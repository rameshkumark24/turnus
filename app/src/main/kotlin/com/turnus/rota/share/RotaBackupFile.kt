package com.turnus.rota.share

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.turnus.rota.data.BackupResult
import com.turnus.rota.data.BackupSnapshot
import com.turnus.rota.data.RotaBackup
import com.turnus.rota.data.RotaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Reads and writes the backup file the user chooses, through the system file
 * picker.
 *
 * The picker is doing real work here, not just looking friendly. It grants
 * access to exactly one file the user selected, so the app needs no storage
 * permission at all — nothing to ask for, nothing to justify on the store
 * listing, and no way for the app to go looking through anything else. For an
 * app whose main claim is that it keeps to itself, that matters more than the
 * convenience of a fixed folder.
 */
object RotaBackupFile {

    /**
     * JSON, but the picker is opened without a filter.
     *
     * Providers disagree about what to call a `.json` file — some report
     * `text/plain`, some `application/octet-stream`, cloud providers sometimes
     * report nothing useful at all — and a strict filter leaves a user staring
     * at their own backup greyed out. Anything they pick that is not a backup
     * is caught by [RotaBackup.decode] and named as such.
     */
    const val MIME: String = "application/json"

    /**
     * A backup of a decade of shifts is tens of kilobytes. This is a limit on
     * mistakes and on hostile files, not on real ones: without it, picking a
     * video would try to read it into memory as text.
     */
    private const val MAX_BYTES = 4 * 1024 * 1024

    private const val UNDO_FILE = "rota-before-restore.json"

    /**
     * How long the reverse gear stays available.
     *
     * The snapshot is a second, complete copy of the rota with the notes in it,
     * and until now it lived until the user wiped the app — which most people
     * never do. That made it a permanent plaintext copy of data the user may
     * since have deleted: delete a note about a hospital appointment and it is
     * gone from the calendar and still sitting in this file.
     *
     * Deleting it immediately is the wrong correction, because the whole point
     * is that restoring the wrong file is a mistake you discover *later* — when
     * you next look at your calendar and it is not yours. A month covers that
     * discovery comfortably and still bounds how long the copy exists, which is
     * the trade the feature actually needs.
     */
    private val UNDO_LIFETIME = Duration.ofDays(30)

    private const val TAG = "RotaBackupFile"

    private val FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** Dated, because people keep several and need to tell them apart. */
    fun suggestedName(today: LocalDate = LocalDate.now()): String =
        "turnus-backup-${FILE_DATE.format(today)}.json"

    /**
     * Writes the backup the user asked for, and says something useful when it
     * fails.
     *
     * The caller turns a thrown message straight into a snackbar, so whatever is
     * thrown here is what the user reads. Left alone, a full disk surfaces as
     * `ENOSPC (No space left on device)` — accurate, and no help at all to
     * someone who has just been told their rota did not save. The two failures
     * worth naming are the ones a person can act on: no room, and a folder that
     * will not take the file.
     *
     * The rota itself is never at risk here. Nothing is deleted before or by
     * this, so a failed save leaves the app exactly as it was and the user can
     * try somewhere else.
     */
    suspend fun write(context: Context, repository: RotaRepository, target: Uri) =
        withContext(Dispatchers.IO) {
            val text = RotaBackup.encode(repository.snapshot(appVersion(context)))
            // "wt", not "w". Overwriting an existing backup with a shorter one
            // in "w" mode leaves the tail of the old file in place, and the
            // result is a file that looks like a backup and will not parse.
            val stream = context.contentResolver.openOutputStream(target, "wt")
                ?: throw IOException("That folder would not accept the file. Try another one.")
            try {
                stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            } catch (failure: IOException) {
                Log.i(TAG, "backup write failed", failure)
                throw IOException(explain(failure), failure)
            }
        }

    /**
     * Turns a write failure into a sentence.
     *
     * Matched on the message rather than the exception type on purpose: a full
     * disk arrives as a plain `IOException` whose message carries the `ENOSPC`,
     * because it comes up through a `ParcelFileDescriptor` from another app's
     * provider rather than from a local `FileOutputStream`. There is no
     * dedicated type to catch, so the string is what there is. Anything not
     * recognised keeps its own message, which is better than a wrong guess.
     */
    private fun explain(failure: IOException): String {
        val detail = failure.message.orEmpty()
        return when {
            detail.contains("ENOSPC", ignoreCase = true) ||
                detail.contains("No space left", ignoreCase = true) ->
                "There is not enough space to save the backup. Free some up, or " +
                    "choose a different folder."

            detail.contains("EROFS", ignoreCase = true) ||
                detail.contains("EACCES", ignoreCase = true) ||
                detail.contains("Permission denied", ignoreCase = true) ->
                "That folder would not accept the file. Try another one."

            else -> "The backup could not be saved. ${detail.ifBlank { "Try another folder." }}"
        }
    }

    /**
     * Never throws for bad content — a picked file is whatever the user picked.
     *
     * Failing to *open* the file and failing to *understand* it are reported
     * differently, and the difference is not pedantry. Cloud folders hand the
     * picker a placeholder for a file that is not on the phone yet; opening it
     * triggers a download that can fail, and every one of those failures used
     * to arrive here as "that file is not a Turnus backup" — said about the
     * user's only copy of their rota. Size is the one exception that stays
     * [BackupResult.NotABackup]: a file this large was read successfully and is
     * a video, not a backup.
     */
    suspend fun read(context: Context, source: Uri): BackupResult =
        withContext(Dispatchers.IO) {
            val text = try {
                context.contentResolver.openInputStream(source)?.use { stream ->
                    // Read by hand rather than readBytes(): a picked file can
                    // be a video, and readBytes() would pull all of it into
                    // memory before anyone could object. readNBytes is not
                    // available below API 33, so the loop is the portable way.
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(8 * 1024)
                    while (true) {
                        val read = stream.read(chunk)
                        if (read < 0) break
                        buffer.write(chunk, 0, read)
                        if (buffer.size() > MAX_BYTES) return@withContext BackupResult.NotABackup
                    }
                    buffer.toString(Charsets.UTF_8.name())
                } ?: return@withContext BackupResult.Unreadable
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                // Deliberately broad. Between the picker and the bytes sits a
                // provider from another app — a cloud client, a file manager,
                // a vendor gallery — and it can fail in ways this app cannot
                // enumerate: a download that never completes, a grant that
                // expired while the picker result waited through process
                // death, or its own crash surfacing as any RuntimeException.
                // None of those are worth taking the app down for, and none of
                // them are evidence about the file's contents.
                Log.i(TAG, "could not read the picked file", failure)
                return@withContext BackupResult.Unreadable
            }
            RotaBackup.decode(text)
        }

    /**
     * Writes the current rota to private storage so a restore can be undone.
     *
     * Restore replaces everything, and the file picker shows names, not
     * contents — so picking the wrong file is a mistake anyone can make and
     * nobody can reverse. This is the reverse gear. It lives in the app's own
     * storage, is overwritten by the next restore, and never leaves the device.
     */
    suspend fun keepUndoSnapshot(context: Context, repository: RotaRepository) =
        withContext(Dispatchers.IO) {
            val text = RotaBackup.encode(repository.snapshot(appVersion(context)))
            File(context.filesDir, UNDO_FILE).writeText(text)
        }

    /**
     * Removes the undo snapshot.
     *
     * Necessary for "delete everything" to mean it. The snapshot is a complete
     * copy of the rota, notes included, and leaving it behind would turn the
     * one feature that promises to remove the user's data into the one that
     * quietly keeps a copy of it.
     */
    suspend fun clearUndoSnapshot(context: Context) = withContext(Dispatchers.IO) {
        File(context.filesDir, UNDO_FILE).delete()
        Unit
    }

    /**
     * The rota as it was before the last restore, or null if there is none.
     *
     * An expired snapshot is deleted here rather than merely ignored. Returning
     * null while leaving the file on disk would hide the copy instead of
     * removing it, which is the opposite of the point.
     */
    suspend fun undoSnapshot(context: Context): BackupSnapshot? =
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, UNDO_FILE)
            if (!file.exists()) return@withContext null
            if (hasExpired(file)) {
                file.delete()
                return@withContext null
            }
            (RotaBackup.decode(file.readText()) as? BackupResult.Success)?.snapshot
        }

    /**
     * Drops the snapshot once it is past its life, wherever the app happens to
     * start.
     *
     * [undoSnapshot] expires it on read, but only the settings screen reads it —
     * so someone who restores once and never opens settings again would keep the
     * copy indefinitely, which is precisely the person this is for. Called at
     * startup so the file goes whether or not anyone looks for it.
     */
    suspend fun expireUndoSnapshot(context: Context) = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, UNDO_FILE)
        if (file.exists() && hasExpired(file)) file.delete()
        Unit
    }

    /**
     * Age by the file's own timestamp, and a clock moved backwards does not
     * extend it: an unreadable or future-dated timestamp counts as expired,
     * because the failure that keeps the copy is worse than the one that drops
     * it. The user has lost an undo they had almost certainly stopped needing;
     * they have not lost their rota, which is in the database either way.
     */
    private fun hasExpired(file: File): Boolean {
        val written = file.lastModified()
        if (written <= 0L) return true
        val age = System.currentTimeMillis() - written
        return age < 0L || age > UNDO_LIFETIME.toMillis()
    }

    /**
     * Recorded in the file so a future release can tell which build wrote it.
     *
     * Version *name* and code: the name is what a user quotes in a support
     * message, the code is what a compatibility check would actually compare.
     */
    private fun appVersion(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
    }.getOrElse { "unknown" }
}
