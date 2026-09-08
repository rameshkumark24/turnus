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

    private const val TAG = "RotaBackupFile"

    private val FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** Dated, because people keep several and need to tell them apart. */
    fun suggestedName(today: LocalDate = LocalDate.now()): String =
        "turnus-backup-${FILE_DATE.format(today)}.json"

    suspend fun write(context: Context, repository: RotaRepository, target: Uri) =
        withContext(Dispatchers.IO) {
            val text = RotaBackup.encode(repository.snapshot(appVersion(context)))
            // "wt", not "w". Overwriting an existing backup with a shorter one
            // in "w" mode leaves the tail of the old file in place, and the
            // result is a file that looks like a backup and will not parse.
            val stream = context.contentResolver.openOutputStream(target, "wt")
                ?: error("The file could not be opened for writing")
            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
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

    /** The rota as it was before the last restore, or null if there is none. */
    suspend fun undoSnapshot(context: Context): BackupSnapshot? =
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, UNDO_FILE)
            if (!file.exists()) return@withContext null
            (RotaBackup.decode(file.readText()) as? BackupResult.Success)?.snapshot
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
