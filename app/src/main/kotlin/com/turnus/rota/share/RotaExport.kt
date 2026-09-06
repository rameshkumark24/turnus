package com.turnus.rota.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.turnus.rota.data.RotaRepository
import com.turnus.rota.engine.DayNumber
import com.turnus.rota.engine.IcsWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Turns the rota into a calendar file other apps can read.
 *
 * The point is that a rota is not only the wearer's problem: a partner wants it
 * in the family calendar, and a manager wants it in an email. Neither of those
 * is going to install this app.
 *
 * The file is written to the cache directory and handed over as a `content://`
 * URI through a [FileProvider]. Nothing is uploaded — the share sheet passes a
 * grant to whichever app the user picks, and cache is the right home for a file
 * whose whole life is the few seconds between tapping export and the receiving
 * app copying it.
 */
object RotaExport {

    /**
     * A year is the useful horizon: long enough to be worth importing, short
     * enough that a calendar app is not asked to swallow thousands of events.
     */
    private const val MONTHS = 12

    private val FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** Thrown when there is nothing to export, so the caller can say why. */
    class NothingToExport : IllegalStateException("No rota has been set up yet")

    /**
     * Writes the next year of shifts and returns a URI to share.
     *
     * Runs off the main thread: this resolves 365 days and formats a few
     * hundred events, which is quick but not free.
     */
    suspend fun writeIcs(context: Context, repository: RotaRepository): Uri =
        withContext(Dispatchers.IO) {
            val from = DayNumber.today()
            val to = DayNumber.from(from.toLocalDate().plusMonths(MONTHS.toLong()))

            // One consistent read, so the events cannot be built from a pattern
            // and shift times that were fetched either side of an edit.
            val inputs = repository.reminderInputs(from, days = (to.value - from.value).toInt())
                ?: throw NothingToExport()

            // Every shift the range can name has to be defined, or IcsWriter
            // refuses — it will not write a calendar with holes in it. Off-duty
            // shift types are excluded from reminderInputs, so they are added
            // back here rather than allowed to fail the export.
            val shifts = repository.shiftDefinitions()

            val ics = IcsWriter.write(
                pattern = inputs.pattern,
                overrides = inputs.overrides,
                shifts = shifts,
                from = from,
                to = to,
                stamp = Instant.now(),
                calendarName = inputs.pattern.name,
            )

            val directory = File(context.cacheDir, "export").apply { mkdirs() }
            // Overwritten rather than accumulated: one file per app, not one per
            // export sitting in the cache until Android decides to clear it.
            val file = File(directory, "turnus-${from.toLocalDate().format(FILE_DATE)}.ics")
            file.writeText(ics)

            FileProvider.getUriForFile(context, "${context.packageName}.export", file)
        }

    /** The share sheet, with the grant the receiving app needs to read the file. */
    fun shareIntent(uri: Uri, patternName: String): Intent =
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/calendar"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, patternName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Share your rota",
        )
}
