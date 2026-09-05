package com.turnus.rota.data

import androidx.room.withTransaction
import com.turnus.rota.engine.DayNumber
import com.turnus.rota.engine.Outlook
import com.turnus.rota.engine.Overrides
import com.turnus.rota.engine.Pattern
import com.turnus.rota.engine.ReminderSettings
import com.turnus.rota.engine.ResolvedDay
import com.turnus.rota.engine.ShiftCode
import com.turnus.rota.engine.ShiftDefinition
import com.turnus.rota.engine.ShiftEngine
import kotlinx.coroutines.flow.Flow

import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

/** A shift type cannot be removed while a pattern or an override still uses it. */
class ShiftTypeInUseException internal constructor(
    val shiftTypeId: String,
    val usedByPatterns: List<String>,
    val usedByOverrideCount: Int,
) : IllegalStateException(
    buildString {
        append("Shift type '").append(shiftTypeId).append("' is still in use")
        if (usedByPatterns.isNotEmpty()) append(" by pattern(s): ").append(usedByPatterns.joinToString())
        if (usedByOverrideCount > 0) append(" and by ").append(usedByOverrideCount).append(" override(s)")
    },
)

/** A pattern referenced shift ids that do not exist. */
class UnknownShiftTypeException internal constructor(
    val ids: List<String>,
) : IllegalArgumentException("No shift type for id(s): ${ids.joinToString()}")

/**
 * A shift type as the UI needs it: the engine's [ShiftDefinition] plus the
 * presentation fields.
 *
 * Colour deliberately does not live in `:engine` — the engine is pure rota
 * logic and must stay free of anything that only means something on a screen.
 */
data class ShiftStyle(
    val id: String,
    val code: String,
    val name: String,
    val color: Int,
    val isWorking: Boolean,
)

/** A stored exception, as the day editor needs it. */
data class DayOverrideDetail(
    val day: DayNumber,
    val shiftTypeId: String?,
    /** False when the row carries only a note and the shift follows the pattern. */
    val overridesShift: Boolean,
    val note: String?,
)

/**
 * The only way the app touches stored rota data.
 *
 * Three invariants live here rather than in the schema, because SQLite CHECK
 * constraints cannot be expressed through Room's annotations:
 *
 * 1. `slot_count` always equals the decoded slot list's length.
 * 2. Every shift id referenced by a pattern or an override exists.
 * 3. At most one pattern is active.
 *
 * Each is enforced on write, inside the same transaction as the write itself.
 * Nothing else in the app may write these tables directly.
 *
 * All three are covered by instrumentation tests in `androidTest`, against real
 * SQLite rather than a simulation of it — transactions, foreign keys and unique
 * indexes are the things being checked, so testing them against an
 * approximation would prove nothing.
 */
class RotaRepository(
    private val db: TurnusDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    private val shiftTypes = db.shiftTypeDao()
    private val patterns = db.patternDao()
    private val overrides = db.dayOverrideDao()
    private val appMeta = db.appMetaDao()

    // ------------------------------------------------------------------ reads

    fun observeShiftTypes(): Flow<List<ShiftDefinition>> =
        shiftTypes.observeAll().map { rows -> rows.map { it.toDefinition() } }

    fun observeActivePattern(): Flow<Pattern?> =
        patterns.observeActive().map { it?.toDomain() }

    /** Shift types with their colours, keyed by id, for rendering the grid. */
    fun observeShiftStyles(): Flow<Map<String, ShiftStyle>> =
        shiftTypes.observeAll().map { rows ->
            rows.associate {
                it.id to ShiftStyle(it.id, it.code, it.name, it.color, it.isWorking)
            }
        }

    /**
     * The resolved calendar for an inclusive range.
     *
     * Recomputed from the pattern and its exceptions on every emission — no
     * generated shift is ever stored, which is what makes correcting a
     * misaligned rota a one-field edit instead of a data migration.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeCalendar(from: DayNumber, to: DayNumber): Flow<List<ResolvedDay>> =
        patterns.observeActive().flatMapLatest { patternRow ->
            if (patternRow == null) {
                flowOf(emptyList())
            } else {
                overrides.observeRange(patternRow.id, from.value, to.value).map { rows ->
                    ShiftEngine.resolveRange(patternRow.toDomain(), rows.toOverrides(), from, to)
                }
            }
        }

    fun observeOverrides(patternId: String): Flow<Overrides> =
        overrides.observeAllFor(patternId).map { it.toOverrides() }

    /**
     * The run [day] sits in and the one after it — "you are on nights until
     * Sunday, then off for four".
     *
     * Loads exactly the window the scan can reach rather than every override
     * ever recorded, because the answer is bounded by [horizonDays] in both
     * directions and nothing outside that window can change it.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeOutlook(day: DayNumber, horizonDays: Int = 366): Flow<Outlook.Summary?> =
        patterns.observeActive().flatMapLatest { patternRow ->
            if (patternRow == null) {
                flowOf(null)
            } else {
                overrides.observeRange(
                    patternRow.id,
                    (day - horizonDays.toLong()).value,
                    (day + horizonDays.toLong()).value,
                ).map { rows ->
                    Outlook.summarise(patternRow.toDomain(), rows.toOverrides(), day, horizonDays)
                }
            }
        }

    // -------------------------------------------------------------- reminders

    fun observeReminderSettings(): Flow<ReminderSettings> =
        appMeta.observeAll().map { rows -> ReminderSettingsCodec.decode(rows.associate { it.key to it.value }) }

    suspend fun reminderSettings(): ReminderSettings =
        ReminderSettingsCodec.decode(appMeta.getAll().associate { it.key to it.value })

    suspend fun saveReminderSettings(settings: ReminderSettings) = db.withTransaction {
        ReminderSettingsCodec.encode(settings).forEach { appMeta.put(it) }
    }

    /**
     * Everything the scheduler needs, read once and consistently.
     *
     * Read as four separate calls the rota could change between them, and the
     * alarms would then be set from a pattern that no longer matches the shift
     * definitions they were timed against.
     */
    suspend fun reminderInputs(from: DayNumber, days: Int): ReminderInputs? = db.withTransaction {
        val pattern = patterns.getActive()?.toDomain() ?: return@withTransaction null
        ReminderInputs(
            pattern = pattern,
            overrides = overrides.getRange(pattern.id, from.value, (from + days.toLong()).value)
                .toOverrides(),
            definitions = shiftTypes.getAll()
                .filter { it.isWorking }
                .associate { it.id to it.toDefinition() },
            settings = ReminderSettingsCodec.decode(appMeta.getAll().associate { it.key to it.value }),
        )
    }

    suspend fun activePattern(): Pattern? = patterns.getActive()?.toDomain()

    suspend fun shiftDefinitions(): Map<String, ShiftDefinition> =
        shiftTypes.getAll().associate { it.id to it.toDefinition() }

    /** One-shot read, in display order, for flows that do not need to observe. */
    suspend fun shiftStyles(): List<ShiftStyle> =
        shiftTypes.getAll().map { ShiftStyle(it.id, it.code, it.name, it.color, it.isWorking) }

    /**
     * The stored exception for one day, or null when the day simply follows the
     * pattern. Null here and a row whose shift is null are different answers —
     * the second means the user deliberately took a working day off.
     */
    suspend fun overrideFor(day: DayNumber): DayOverrideDetail? {
        val pattern = patterns.getActive() ?: return null
        return overrides.get(pattern.id, day.value)?.let {
            DayOverrideDetail(
                day = DayNumber(it.day),
                shiftTypeId = it.shiftTypeId,
                overridesShift = it.overridesShift,
                note = it.note,
            )
        }
    }

    // ----------------------------------------------------------------- writes

    /**
     * Saves [pattern] and makes it the only active one.
     *
     * @throws UnknownShiftTypeException if any slot names a shift type that
     *   does not exist. Failing here beats writing a pattern that renders
     *   blank cells the user cannot explain.
     */
    suspend fun saveActivePattern(pattern: Pattern) {
        val timestamp = now()
        // The check and the write share a transaction. Validating outside it
        // would let a shift type be deleted in between, leaving the pattern
        // holding a dangling id that no foreign key can catch — slots is a
        // delimited text column, so SQLite cannot police it.
        db.withTransaction {
            val known = shiftTypes.getAll().map { it.id }.toSet()
            val missing = pattern.slots.filterNotNull().distinct().filterNot { it in known }
            if (missing.isNotEmpty()) throw UnknownShiftTypeException(missing)

            val existing = patterns.getById(pattern.id)
            patterns.deactivateAll()
            patterns.upsert(
                pattern.toEntity(
                    isActive = true,
                    createdAt = existing?.createdAt ?: timestamp,
                    updatedAt = timestamp,
                ),
            )
        }
    }

    /** Corrects a rota that is running a day or two out. One field, no migration. */
    suspend fun shiftActivePatternBy(days: Long) {
        val current = patterns.getActive() ?: return
        val timestamp = now()
        patterns.upsert(
            current.copy(anchorDay = current.anchorDay + days, updatedAt = timestamp),
        )
    }

    /**
     * Records an exception for one day. A null [shiftTypeId] means explicitly
     * off, which is not the same as clearing the override.
     */
    suspend fun setOverride(day: DayNumber, shiftTypeId: String?, note: String? = null) {
        val timestamp = now()
        db.withTransaction {
            val pattern = patterns.getActive() ?: return@withTransaction
            if (shiftTypeId != null && shiftTypes.getById(shiftTypeId) == null) {
                throw UnknownShiftTypeException(listOf(shiftTypeId))
            }
            val existing = overrides.get(pattern.id, day.value)
            overrides.upsert(
                DayOverrideEntity(
                    patternId = pattern.id,
                    day = day.value,
                    shiftTypeId = shiftTypeId,
                    overridesShift = true,
                    note = note,
                    createdAt = existing?.createdAt ?: timestamp,
                    updatedAt = timestamp,
                ),
            )
        }
    }

    /**
     * Attaches a note without touching which shift the day is.
     *
     * A note must never pin the day: someone who annotates a fortnight and then
     * corrects a rota that is running a day out would otherwise find every
     * annotated day stranded on its old shift. If the day already has a real
     * shift override, that is preserved; if not, the row is marked note-only
     * and the engine ignores it.
     *
     * A blank note on a day with no shift override deletes the row rather than
     * storing an exception that carries no information.
     */
    suspend fun setNote(day: DayNumber, note: String?) {
        val timestamp = now()
        db.withTransaction {
            val pattern = patterns.getActive() ?: return@withTransaction
            val existing = overrides.get(pattern.id, day.value)
            val text = note?.takeIf { it.isNotBlank() }

            if (text == null && existing?.overridesShift != true) {
                overrides.delete(pattern.id, day.value)
                return@withTransaction
            }

            overrides.upsert(
                DayOverrideEntity(
                    patternId = pattern.id,
                    day = day.value,
                    shiftTypeId = existing?.shiftTypeId.takeIf { existing?.overridesShift == true },
                    overridesShift = existing?.overridesShift ?: false,
                    note = text,
                    createdAt = existing?.createdAt ?: timestamp,
                    updatedAt = timestamp,
                ),
            )
        }
    }

    /** Removes the exception, so the day falls back to the generated pattern. */
    suspend fun clearOverride(day: DayNumber) {
        val pattern = patterns.getActive() ?: return
        overrides.delete(pattern.id, day.value)
    }

    suspend fun createShiftType(
        code: String,
        name: String,
        color: Int,
        startMinute: Int? = null,
        durationMinute: Int? = null,
        isWorking: Boolean = true,
        id: String = newId(),
    ): String {
        // Constructing the domain type first borrows its argument validation.
        ShiftDefinition(id, code, name, startMinute, durationMinute)
        val timestamp = now()
        db.withTransaction {
            shiftTypes.insert(
                ShiftTypeEntity(
                    id = id,
                    code = code,
                    name = name,
                    color = color,
                    startMinute = startMinute,
                    durationMinute = durationMinute,
                    isWorking = isWorking,
                    // MAX + 1, not count(): after any deletion a count would
                    // reuse an index already held by a later row, and every
                    // "ORDER BY sort_order" query would then be ambiguous.
                    sortOrder = (shiftTypes.maxSortOrder() ?: -1) + 1,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                ),
            )
        }
        return id
    }

    /**
     * @throws ShiftTypeInUseException listing exactly what still references it,
     *   so the UI can tell the user which pattern or how many days to fix
     *   rather than just refusing.
     */
    suspend fun deleteShiftType(id: String) {
        // Check and delete share a transaction, so a pattern cannot start
        // referencing this shift between the two.
        db.withTransaction {
            val row = shiftTypes.getById(id) ?: return@withTransaction

            // SQLite cannot enforce a foreign key into a delimited text column,
            // so the pattern check happens here. Patterns are few; decoding
            // them all is cheaper than any clever query and cannot go subtly
            // wrong.
            val usedBy = patterns.getAll()
                .filter { id in SlotCodec.decode(it.slots) }
                .map { it.name }
            val overrideCount = overrides.countUsing(id)

            if (usedBy.isNotEmpty() || overrideCount > 0) {
                throw ShiftTypeInUseException(id, usedBy, overrideCount)
            }
            shiftTypes.delete(row)
        }
    }

    // ------------------------------------------------------------------ setup

    /**
     * Installs the four default shift types on first run.
     *
     * The ids are deliberately the engine's [ShiftCode] constants rather than
     * generated UUIDs: the built-in presets reference those ids, so a preset
     * chosen during setup resolves without any translation step.
     */
    suspend fun seedDefaultsIfEmpty() = db.withTransaction {
        // Transactional check-then-insert, and IGNORE on the insert. Startup
        // calls this from more than one place; without both, two callers can
        // each see an empty table and the loser's INSERT hits the unique code
        // index and takes the process down on first launch.
        if (shiftTypes.count() > 0) return@withTransaction
        val timestamp = now()
        var order = 0
        fun row(id: String, code: String, name: String, color: Int, start: Int, hours: Int) =
            ShiftTypeEntity(
                id = id,
                code = code,
                name = name,
                color = color,
                startMinute = start,
                durationMinute = hours * 60,
                isWorking = true,
                sortOrder = order++,
                createdAt = timestamp,
                updatedAt = timestamp,
            )

        shiftTypes.insertAllIgnoring(
            listOf(
                row(ShiftCode.DAY, "D", "Day", COLOR_DAY, 7 * 60, 12),
                row(ShiftCode.NIGHT, "N", "Night", COLOR_NIGHT, 19 * 60, 12),
                row(ShiftCode.EARLY, "E", "Early", COLOR_EARLY, 6 * 60, 8),
                row(ShiftCode.LATE, "L", "Late", COLOR_LATE, 14 * 60, 8),
            ),
        )
    }

    private companion object {
        const val COLOR_DAY = 0xFFE0A33C.toInt()
        const val COLOR_NIGHT = 0xFF3D5A80.toInt()
        const val COLOR_EARLY = 0xFF2A9D8F.toInt()
        const val COLOR_LATE = 0xFF7B5EA7.toInt()
    }
}

/**
 * A consistent snapshot of everything a reminder schedule depends on.
 *
 * Bundled rather than fetched piecemeal so the alarms can never be built from a
 * pattern and a set of shift definitions that were read either side of an edit.
 */
data class ReminderInputs(
    val pattern: Pattern,
    val overrides: Overrides,
    val definitions: Map<String, ShiftDefinition>,
    val settings: ReminderSettings,
)
