package com.turnus.rota.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The rota as a file the user owns.
 *
 * This app has no accounts and no server, which is the point of it — and the
 * price of that is that a new phone, a factory reset or a mis-tap on "clear
 * data" takes the rota with it. Android's automatic backup covers some of
 * those, but the user cannot see it, cannot check it and cannot restore it on
 * demand. This they can: a file, in their own storage, that they can send to
 * themselves.
 *
 * The format is JSON rather than a copy of the database file, for three
 * reasons. It survives a schema change, which a raw `.db` does not. It can be
 * read by a person who wants to know what is in it — a claim about privacy is
 * worth more when it can be checked. And it cannot carry anything the schema
 * happens to grow later: every field here is written deliberately.
 *
 * ### Unit discipline
 *
 * Two units of time appear in the same file, so both are named for what they
 * are. `anchorDay` and `day` are DayNumbers — civil days since 1970-01-01, no
 * zone and no clock. Anything ending `Millis` is an epoch timestamp. Nothing
 * is called just "date".
 *
 * ### Compatibility
 *
 * [VERSION] is the file format, not the app version. A file from a newer app
 * is reported as such rather than parsed on a best-effort basis: a partially
 * understood rota is worse than a refused one, because the user would not know
 * which days were wrong.
 */
object RotaBackup {

    /** Identifies the file before anything else in it is trusted. */
    const val FORMAT: String = "turnus.rota.backup"

    const val VERSION: Int = 1

    fun encode(snapshot: BackupSnapshot): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("createdAtMillis", snapshot.createdAtMillis)
        root.put("appVersion", snapshot.appVersion)

        root.put(
            "shiftTypes",
            JSONArray().apply {
                snapshot.shiftTypes.forEach { shift ->
                    put(
                        JSONObject().apply {
                            put("id", shift.id)
                            put("code", shift.code)
                            put("name", shift.name)
                            put("color", shift.color)
                            // Omitted rather than null when the shift has no
                            // clock times: absent and "explicitly nothing" mean
                            // the same thing here, and one of them reads better.
                            shift.startMinute?.let { put("startMinute", it) }
                            shift.durationMinute?.let { put("durationMinute", it) }
                            // Omitted when zero, which is the overwhelming
                            // majority, and read back as zero when absent — so
                            // a file written before breaks existed still
                            // restores correctly.
                            if (shift.breakMinutes > 0) put("breakMinutes", shift.breakMinutes)
                            put("isWorking", shift.isWorking)
                            put("sortOrder", shift.sortOrder)
                            put("createdAtMillis", shift.createdAtMillis)
                            put("updatedAtMillis", shift.updatedAtMillis)
                        },
                    )
                }
            },
        )

        root.put(
            "patterns",
            JSONArray().apply {
                snapshot.patterns.forEach { pattern ->
                    put(
                        JSONObject().apply {
                            put("id", pattern.id)
                            put("name", pattern.name)
                            put("anchorDay", pattern.anchorDay)
                            // An array of ids and nulls, not SlotCodec's packed
                            // string: the file must not depend on an internal
                            // encoding that is free to change.
                            put(
                                "slots",
                                JSONArray().apply {
                                    pattern.slots.forEach { put(it ?: JSONObject.NULL) }
                                },
                            )
                            put("isActive", pattern.isActive)
                            put("createdAtMillis", pattern.createdAtMillis)
                            put("updatedAtMillis", pattern.updatedAtMillis)
                        },
                    )
                }
            },
        )

        root.put(
            "changedDays",
            JSONArray().apply {
                snapshot.changedDays.forEach { day ->
                    put(
                        JSONObject().apply {
                            put("patternId", day.patternId)
                            put("day", day.day)
                            day.shiftTypeId?.let { put("shiftTypeId", it) }
                            put("overridesShift", day.overridesShift)
                            day.note?.let { put("note", it) }
                            put("createdAtMillis", day.createdAtMillis)
                            put("updatedAtMillis", day.updatedAtMillis)
                        },
                    )
                }
            },
        )

        root.put("settings", JSONObject(snapshot.settings.toMap()))

        // Indented: someone will open this in a text editor to check what the
        // privacy policy claims, and one long line of JSON answers them badly.
        return root.toString(2)
    }

    /**
     * Never throws. The input is a file the user picked, which means it may be
     * a photo, a truncated download or a backup from a future release.
     */
    fun decode(text: String): BackupResult {
        val root = try {
            JSONObject(text)
        } catch (_: JSONException) {
            return BackupResult.NotABackup
        }

        if (root.optString("format") != FORMAT) return BackupResult.NotABackup

        val version = root.optInt("version", 0)
        if (version > VERSION) return BackupResult.TooNew(version)
        if (version < 1) return BackupResult.Damaged("it has no version number")

        return try {
            val snapshot = BackupSnapshot(
                createdAtMillis = root.optLong("createdAtMillis", 0L),
                appVersion = root.optString("appVersion"),
                shiftTypes = root.objects("shiftTypes").map { it.toShiftType() },
                patterns = root.objects("patterns").map { it.toPattern() },
                changedDays = root.objects("changedDays").map { it.toChangedDay() },
                settings = root.optJSONObject("settings").toStringMap(),
            )
            validate(snapshot)?.let { return BackupResult.Damaged(it) }
            BackupResult.Success(snapshot)
        } catch (failure: JSONException) {
            BackupResult.Damaged(failure.message ?: "it could not be read")
        } catch (failure: IllegalArgumentException) {
            BackupResult.Damaged(failure.message ?: "it could not be read")
        }
    }

    /**
     * The referential checks the file format itself cannot express.
     *
     * Run before anything is written, because a restore replaces the user's
     * whole rota: a file that failed half way through would leave them with
     * neither their old rota nor the one in the file.
     *
     * @return a description of the first problem, phrased for a person, or
     *   null if the snapshot is internally consistent.
     */
    internal fun validate(snapshot: BackupSnapshot): String? {
        if (snapshot.shiftTypes.isEmpty()) return "it contains no shifts"

        val shiftIds = snapshot.shiftTypes.map { it.id }.toSet()
        if (shiftIds.size != snapshot.shiftTypes.size) return "two shifts share an id"

        val codes = snapshot.shiftTypes.map { it.code.lowercase() }
        if (codes.distinct().size != codes.size) return "two shifts share a letter"

        // A pattern's slots are stored as one delimited column, so an id that
        // contains the delimiter or equals the day-off sentinel cannot be
        // written back. See SlotCodec. Real ids are UUIDs and never look like
        // this; a hand-edited file can.
        if (snapshot.shiftTypes.any { it.id.contains(",") || it.id == "-" }) {
            return "a shift has an id this app cannot store"
        }

        // A file with shifts but no rota would restore an app into its own
        // setup wizard. Whatever the user meant by picking it, that is not it.
        if (snapshot.patterns.isEmpty()) return "it contains no rota"

        val patternIds = snapshot.patterns.map { it.id }.toSet()
        if (patternIds.size != snapshot.patterns.size) return "two rotas share an id"
        if (snapshot.patterns.count { it.isActive } > 1) return "more than one rota is active"

        snapshot.patterns.forEach { pattern ->
            if (pattern.slots.isEmpty()) return "a rota in the file has no days in its cycle"
            val unknown = pattern.slots.filterNotNull().distinct().filterNot { it in shiftIds }
            if (unknown.isNotEmpty()) return "a rota in the file uses a shift the file does not define"
        }

        snapshot.changedDays.forEach { day ->
            if (day.patternId !in patternIds) {
                return "a changed day belongs to a rota the file does not contain"
            }
            val shiftTypeId = day.shiftTypeId
            if (shiftTypeId != null && shiftTypeId !in shiftIds) {
                return "a changed day uses a shift the file does not define"
            }
        }

        return null
    }

    // -------------------------------------------------------------- json glue

    private fun JSONObject.objects(name: String): List<JSONObject> {
        val array = optJSONArray(name) ?: return emptyList()
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        return keys().asSequence().associateWith { getString(it) }
    }

    private fun JSONObject.requiredString(name: String): String {
        val value = getString(name)
        require(value.isNotBlank()) { "a record is missing its $name" }
        return value
    }

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (has(name) && !isNull(name)) getInt(name) else null

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (has(name) && !isNull(name)) getString(name) else null

    private fun JSONObject.toShiftType(): BackupShiftType {
        val start = optIntOrNull("startMinute")
        val duration = optIntOrNull("durationMinute")
        // The pairing ShiftDefinition enforces. A shift with a start and no
        // length would produce an event with no end, and the calendar export
        // would refuse it later, somewhere the user could not connect back to
        // this file.
        require((start == null) == (duration == null)) {
            "a shift has a start time but no length"
        }
        require(start == null || start in 0..1439) { "a shift starts outside the day" }
        require(duration == null || duration in 1..1440) { "a shift is longer than a day" }
        return BackupShiftType(
            id = requiredString("id"),
            code = requiredString("code"),
            name = requiredString("name"),
            color = getInt("color"),
            startMinute = start,
            durationMinute = duration,
            breakMinutes = optInt("breakMinutes", 0),
            isWorking = optBoolean("isWorking", true),
            sortOrder = optInt("sortOrder", 0),
            createdAtMillis = optLong("createdAtMillis", 0L),
            updatedAtMillis = optLong("updatedAtMillis", 0L),
        )
    }

    private fun JSONObject.toPattern(): BackupPattern {
        val slots = getJSONArray("slots")
        return BackupPattern(
            id = requiredString("id"),
            name = optString("name"),
            anchorDay = getLong("anchorDay"),
            slots = (0 until slots.length()).map { index ->
                if (slots.isNull(index)) null else slots.getString(index)
            },
            isActive = optBoolean("isActive", false),
            createdAtMillis = optLong("createdAtMillis", 0L),
            updatedAtMillis = optLong("updatedAtMillis", 0L),
        )
    }

    private fun JSONObject.toChangedDay(): BackupChangedDay = BackupChangedDay(
        patternId = requiredString("patternId"),
        day = getLong("day"),
        shiftTypeId = optStringOrNull("shiftTypeId"),
        overridesShift = optBoolean("overridesShift", true),
        note = optStringOrNull("note"),
        createdAtMillis = optLong("createdAtMillis", 0L),
        updatedAtMillis = optLong("updatedAtMillis", 0L),
    )
}

/** Everything in the database that belongs to the user, and nothing else. */
data class BackupSnapshot(
    val createdAtMillis: Long,
    val appVersion: String,
    val shiftTypes: List<BackupShiftType>,
    val patterns: List<BackupPattern>,
    val changedDays: List<BackupChangedDay>,
    /** The `app_meta` rows — reminder preferences today, more later. */
    val settings: Map<String, String>,
) {
    /**
     * What to show someone before replacing their rota with this.
     *
     * A restore cannot be undone from the user's point of view, and a file
     * picker shows file names, which are the least reliable thing about a file.
     * Reading back "Nights and Days, 8-day cycle, saved 3 March" is the only
     * way they can tell they picked the right one.
     */
    fun summary(): BackupSummary {
        val active = patterns.firstOrNull { it.isActive } ?: patterns.firstOrNull()
        return BackupSummary(
            createdAtMillis = createdAtMillis,
            patternName = active?.name?.takeIf { it.isNotBlank() },
            cycleLength = active?.slots?.size ?: 0,
            shiftTypeCount = shiftTypes.size,
            changedDayCount = changedDays.count { it.patternId == active?.id },
        )
    }
}

data class BackupShiftType(
    val id: String,
    val code: String,
    val name: String,
    val color: Int,
    val startMinute: Int?,
    val durationMinute: Int?,
    val breakMinutes: Int,
    val isWorking: Boolean,
    val sortOrder: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

data class BackupPattern(
    val id: String,
    val name: String,
    /** DayNumber of slot 0. */
    val anchorDay: Long,
    val slots: List<String?>,
    val isActive: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

data class BackupChangedDay(
    val patternId: String,
    /** DayNumber. */
    val day: Long,
    val shiftTypeId: String?,
    val overridesShift: Boolean,
    val note: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

data class BackupSummary(
    val createdAtMillis: Long,
    val patternName: String?,
    val cycleLength: Int,
    val shiftTypeCount: Int,
    val changedDayCount: Int,
)

sealed interface BackupResult {

    data class Success(val snapshot: BackupSnapshot) : BackupResult

    /** Not a Turnus backup at all — a photo, a document, the wrong file. */
    data object NotABackup : BackupResult

    /**
     * The file could not be read, so nothing is known about what is in it.
     *
     * Separate from [NotABackup] because the difference matters to the person
     * standing in front of it. A backup kept in Google Drive or OneDrive is a
     * placeholder until something opens it, and picking one that has not
     * finished downloading fails at the stream, not at the parser — telling
     * that user "this is not a Turnus backup" is both wrong and alarming,
     * because the file they are looking at is their entire rota. The honest
     * answer is that it could not be opened, and that waiting may fix it.
     */
    data object Unreadable : BackupResult

    /** A backup from a newer release. Say so; do not guess at the parts we know. */
    data class TooNew(val version: Int) : BackupResult

    /** A Turnus backup that has been truncated, edited or corrupted. */
    data class Damaged(val reason: String) : BackupResult
}
