package com.turnus.rota.data

import androidx.room.withTransaction
import com.turnus.rota.engine.DayNumber
import com.turnus.rota.engine.Overrides
import com.turnus.rota.engine.Pattern
import com.turnus.rota.engine.ResolvedDay
import com.turnus.rota.engine.ShiftCode
import com.turnus.rota.engine.ShiftDefinition
import com.turnus.rota.engine.ShiftEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
 * The only way the app touches stored rota data.
 *
 * Three invariants live here rather than in the schema, because SQLite CHECK
 * constraints cannot be expressed through Room's annotations:
 *
 * 1. `slot_count` always equals the decoded slot list's length.
 * 2. Every shift id referenced by a pattern or an override exists.
 * 3. At most one pattern is active.
 *
 * Each is enforced on write and covered by tests. Nothing else in the app may
 * write these tables directly.
 */
class RotaRepository(
    private val db: TurnusDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    private val shiftTypes = db.shiftTypeDao()
    private val patterns = db.patternDao()
    private val overrides = db.dayOverrideDao()

    // ------------------------------------------------------------------ reads

    fun observeShiftTypes(): Flow<List<ShiftDefinition>> =
        shiftTypes.observeAll().map { rows -> rows.map { it.toDefinition() } }

    fun observeActivePattern(): Flow<Pattern?> =
        patterns.observeActive().map { it?.toDomain() }

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

    suspend fun activePattern(): Pattern? = patterns.getActive()?.toDomain()

    suspend fun shiftDefinitions(): Map<String, ShiftDefinition> =
        shiftTypes.getAll().associate { it.id to it.toDefinition() }

    // ----------------------------------------------------------------- writes

    /**
     * Saves [pattern] and makes it the only active one.
     *
     * @throws UnknownShiftTypeException if any slot names a shift type that
     *   does not exist. Failing here beats writing a pattern that renders
     *   blank cells the user cannot explain.
     */
    suspend fun saveActivePattern(pattern: Pattern) {
        val known = shiftTypes.getAll().map { it.id }.toSet()
        val missing = pattern.slots.filterNotNull().distinct().filterNot { it in known }
        if (missing.isNotEmpty()) throw UnknownShiftTypeException(missing)

        val timestamp = now()
        db.withTransaction {
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
        val pattern = patterns.getActive() ?: return
        if (shiftTypeId != null && shiftTypes.getById(shiftTypeId) == null) {
            throw UnknownShiftTypeException(listOf(shiftTypeId))
        }
        val timestamp = now()
        overrides.upsert(
            DayOverrideEntity(
                patternId = pattern.id,
                day = day.value,
                shiftTypeId = shiftTypeId,
                note = note,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
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
        shiftTypes.insert(
            ShiftTypeEntity(
                id = id,
                code = code,
                name = name,
                color = color,
                startMinute = startMinute,
                durationMinute = durationMinute,
                isWorking = isWorking,
                sortOrder = shiftTypes.count(),
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
        return id
    }

    /**
     * @throws ShiftTypeInUseException listing exactly what still references it,
     *   so the UI can tell the user which pattern or how many days to fix
     *   rather than just refusing.
     */
    suspend fun deleteShiftType(id: String) {
        val row = shiftTypes.getById(id) ?: return

        // SQLite cannot enforce a foreign key into a delimited text column, so
        // the pattern check happens here. Patterns are few; decoding them all
        // is cheaper than any clever query and cannot go subtly wrong.
        val usedBy = patterns.getAll()
            .filter { id in SlotCodec.decode(it.slots) }
            .map { it.name }
        val overrideCount = overrides.countUsing(id)

        if (usedBy.isNotEmpty() || overrideCount > 0) {
            throw ShiftTypeInUseException(id, usedBy, overrideCount)
        }
        shiftTypes.delete(row)
    }

    // ------------------------------------------------------------------ setup

    /**
     * Installs the four default shift types on first run.
     *
     * The ids are deliberately the engine's [ShiftCode] constants rather than
     * generated UUIDs: the built-in presets reference those ids, so a preset
     * chosen during setup resolves without any translation step.
     */
    suspend fun seedDefaultsIfEmpty() {
        if (shiftTypes.count() > 0) return
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

        shiftTypes.insertAll(
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
