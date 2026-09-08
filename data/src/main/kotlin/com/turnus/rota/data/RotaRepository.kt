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

/**
 * Two shifts cannot share a letter.
 *
 * The database enforces this with a unique index, but a raw constraint
 * violation surfaces as "UNIQUE constraint failed: shift_type.code (code 2067)",
 * which is not something to show a shift worker who typed N twice. Caught and
 * named here so the UI can say which shift already has it.
 */
class DuplicateShiftCodeException internal constructor(
    val code: String,
    val usedBy: String,
) : IllegalArgumentException("'$code' is already used by $usedBy")

/** A pattern referenced shift ids that do not exist. */
class UnknownShiftTypeException internal constructor(
    val ids: List<String>,
) : IllegalArgumentException("No shift type for id(s): ${ids.joinToString()}")

/**
 * A backup was not internally consistent, so nothing was written.
 *
 * Carries the reason in words a user can read: restore is the one operation
 * that destroys what they already have, so "could not restore" on its own is
 * not an acceptable thing to tell them.
 */
class BackupRejectedException internal constructor(
    val reason: String,
) : IllegalArgumentException(reason)

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
     * Whether the user has already been offered reminders on the calendar.
     *
     * Deliberately a separate fact from `enabled`. Someone who turned reminders
     * on and later off has answered the question, and must not be asked again;
     * `enabled = false` alone cannot tell that apart from never having been
     * asked. It lives in `app_meta` rather than in [ReminderSettings] because
     * it records what the app has done, not what the user has configured.
     *
     * Being in `app_meta` also gives it the two behaviours it should have:
     * "delete everything" clears it, so a fresh start asks again, and a backup
     * carries it, so restoring on a new phone does not re-ask.
     */
    fun observeReminderPromptSeen(): Flow<Boolean> =
        appMeta.observeAll().map { rows ->
            rows.any { it.key == REMINDER_PROMPT_SEEN && it.value == "true" }
        }

    suspend fun markReminderPromptSeen() {
        appMeta.put(AppMetaEntity(REMINDER_PROMPT_SEEN, "true"))
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
        breakMinutes: Int = 0,
        isWorking: Boolean = true,
        id: String = newId(),
    ): String {
        // Constructing the domain type first borrows its argument validation.
        ShiftDefinition(id, code, name, startMinute, durationMinute, breakMinutes)
        val timestamp = now()
        db.withTransaction {
            requireCodeIsFree(code, exceptId = null)
            shiftTypes.insert(
                ShiftTypeEntity(
                    id = id,
                    code = code,
                    name = name,
                    color = color,
                    startMinute = startMinute,
                    durationMinute = durationMinute,
                    breakMinutes = breakMinutes,
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
     * Edits an existing shift type in place.
     *
     * The id is deliberately preserved. Patterns and overrides reference shifts
     * by id, so replacing the row would orphan every day already assigned to
     * it — someone correcting their start time from 07:00 to 06:00 would find
     * their whole calendar had gone blank.
     *
     * `created_at` is preserved for the same class of reason: it is an audit
     * field, and an edit is not a creation.
     *
     * @throws UnknownShiftTypeException if the shift no longer exists.
     * @throws IllegalArgumentException via [ShiftDefinition] for times outside a
     *   day, or a start without a duration.
     */
    suspend fun updateShiftType(
        id: String,
        code: String,
        name: String,
        color: Int,
        startMinute: Int?,
        durationMinute: Int?,
        breakMinutes: Int = 0,
    ) {
        // Borrows the domain type's validation before touching the database.
        ShiftDefinition(id, code, name, startMinute, durationMinute, breakMinutes)
        val timestamp = now()
        db.withTransaction {
            val existing = shiftTypes.getById(id) ?: throw UnknownShiftTypeException(listOf(id))
            requireCodeIsFree(code, exceptId = id)
            shiftTypes.upsert(
                existing.copy(
                    code = code,
                    name = name,
                    color = color,
                    startMinute = startMinute,
                    durationMinute = durationMinute,
                    breakMinutes = breakMinutes,
                    updatedAt = timestamp,
                ),
            )
        }
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

    /**
     * Checked in Kotlin as well as by the unique index, so the failure arrives
     * as something the UI can put in front of a person. Inside the caller.s
     * transaction, so nothing can claim the code between the check and the write.
     */
    private suspend fun requireCodeIsFree(code: String, exceptId: String?) {
        val clash = shiftTypes.getAll()
            .firstOrNull { it.code.equals(code, ignoreCase = true) && it.id != exceptId }
        if (clash != null) throw DuplicateShiftCodeException(code, clash.name)
    }

    // ----------------------------------------------------------------- shared

    /**
     * Adopts a rota that arrived as a share code.
     *
     * A code carries shift *letters*, not ids, so the letters have to be
     * matched against this phone's own shift types. Matching is by letter and
     * case-insensitive, and the local shift wins: if the sender's N runs 19:00
     * to 07:00 and the receiver's runs 18:00 to 06:00, the receiver keeps their
     * own hours. They are the ones who have to turn up.
     *
     * A letter with no local match is created rather than refused. Someone
     * whose workmate has an "R" they have never used should get their rota, not
     * an error — with the new shift sitting in the shift editor waiting for its
     * real times. Which letters were new is returned so the UI can say so.
     *
     * The active pattern's id is reused when there is one. Changed days,
     * sickness and booked leave hang off that id: adopting a rota under a fresh
     * id would leave every one of them attached to a pattern that is no longer
     * active, and they would vanish from the calendar.
     */
    suspend fun importSharedPattern(
        name: String,
        anchor: DayNumber,
        codes: List<String?>,
    ): SharedPatternImport = db.withTransaction {
        val existing = shiftTypes.getAll()
        val idByCode = existing.associate { it.code.lowercase() to it.id }.toMutableMap()

        val wanted = codes.filterNotNull().distinct()
        val missing = wanted.filter { it.lowercase() !in idByCode }

        val timestamp = now()
        var order = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        missing.forEach { code ->
            val id = newId()
            shiftTypes.insert(
                ShiftTypeEntity(
                    id = id,
                    code = code,
                    // The letter is all that travelled, so the letter is the
                    // name until the user gives it a better one.
                    name = code,
                    color = NEW_SHIFT_COLORS[order % NEW_SHIFT_COLORS.size],
                    // No times. Inventing hours for someone else's shift would
                    // put wrong times in their reminders and their exported
                    // calendar, which is worse than having none.
                    startMinute = null,
                    durationMinute = null,
                    isWorking = true,
                    sortOrder = order++,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                ),
            )
            idByCode[code.lowercase()] = id
        }

        val current = patterns.getActive()
        val pattern = Pattern(
            id = current?.id ?: newId(),
            name = name.ifBlank { "My rota" },
            anchor = anchor,
            slots = codes.map { code -> code?.let { idByCode.getValue(it.lowercase()) } },
        )
        patterns.deactivateAll()
        patterns.upsert(
            pattern.toEntity(
                isActive = true,
                createdAt = current?.createdAt ?: timestamp,
                updatedAt = timestamp,
            ),
        )

        SharedPatternImport(pattern = pattern, createdShiftCodes = missing)
    }

    // ----------------------------------------------------------------- backup

    /**
     * Everything the user owns, read in one transaction.
     *
     * One transaction because a backup assembled from four separate reads
     * could contain a pattern referring to a shift type deleted between the
     * second read and the third — a file that looks fine and cannot be
     * restored, discovered by the user at the worst possible moment.
     */
    suspend fun snapshot(appVersion: String): BackupSnapshot = db.withTransaction {
        BackupSnapshot(
            createdAtMillis = now(),
            appVersion = appVersion,
            shiftTypes = shiftTypes.getAll().map {
                BackupShiftType(
                    id = it.id,
                    code = it.code,
                    name = it.name,
                    color = it.color,
                    startMinute = it.startMinute,
                    durationMinute = it.durationMinute,
                    breakMinutes = it.breakMinutes,
                    isWorking = it.isWorking,
                    sortOrder = it.sortOrder,
                    createdAtMillis = it.createdAt,
                    updatedAtMillis = it.updatedAt,
                )
            },
            patterns = patterns.getAll().map {
                BackupPattern(
                    id = it.id,
                    name = it.name,
                    anchorDay = it.anchorDay,
                    slots = SlotCodec.decode(it.slots),
                    isActive = it.isActive,
                    createdAtMillis = it.createdAt,
                    updatedAtMillis = it.updatedAt,
                )
            },
            changedDays = overrides.getAll().map {
                BackupChangedDay(
                    patternId = it.patternId,
                    day = it.day,
                    shiftTypeId = it.shiftTypeId,
                    overridesShift = it.overridesShift,
                    note = it.note,
                    createdAtMillis = it.createdAt,
                    updatedAtMillis = it.updatedAt,
                )
            },
            settings = appMeta.getAll().associate { it.key to it.value },
        )
    }

    /**
     * Replaces the entire rota with the contents of a backup.
     *
     * Replace, not merge. Merging two rotas has no correct answer — whose
     * anchor wins, what happens to a day changed in both — and a user restoring
     * a backup is asking to go back to a known state, not to negotiate with
     * the one they have.
     *
     * Validated before the first delete and executed in a single transaction,
     * so the failure mode is "nothing happened", never "half your rota".
     *
     * @throws BackupRejectedException if the snapshot is not internally
     *   consistent. Nothing is written in that case.
     */
    suspend fun restore(snapshot: BackupSnapshot) {
        RotaBackup.validate(snapshot)?.let { throw BackupRejectedException(it) }

        val timestamp = now()
        // A row from a backup keeps its original audit timestamps where it has
        // them: they say when the user made the change, and a restore is not a
        // change to their rota. Zero means a file that predates the field.
        fun stamp(value: Long): Long = if (value > 0L) value else timestamp

        db.withTransaction {
            // Overrides first: they hold a RESTRICT foreign key to shift_type,
            // so clearing the shifts before the days that reference them fails.
            overrides.deleteAll()
            patterns.deleteAll()
            shiftTypes.deleteAll()
            appMeta.deleteAll()

            shiftTypes.insertAll(
                snapshot.shiftTypes.map {
                    ShiftTypeEntity(
                        id = it.id,
                        code = it.code,
                        name = it.name,
                        color = it.color,
                        startMinute = it.startMinute,
                        durationMinute = it.durationMinute,
                        breakMinutes = it.breakMinutes,
                        isWorking = it.isWorking,
                        sortOrder = it.sortOrder,
                        createdAt = stamp(it.createdAtMillis),
                        updatedAt = stamp(it.updatedAtMillis),
                    )
                },
            )
            patterns.insertAll(
                snapshot.patterns.map {
                    PatternEntity(
                        id = it.id,
                        name = it.name,
                        anchorDay = it.anchorDay,
                        slots = SlotCodec.encode(it.slots),
                        slotCount = it.slots.size,
                        isActive = it.isActive,
                        createdAt = stamp(it.createdAtMillis),
                        updatedAt = stamp(it.updatedAtMillis),
                    )
                },
            )
            overrides.insertAll(
                snapshot.changedDays.map {
                    DayOverrideEntity(
                        patternId = it.patternId,
                        day = it.day,
                        shiftTypeId = it.shiftTypeId,
                        overridesShift = it.overridesShift,
                        note = it.note,
                        createdAt = stamp(it.createdAtMillis),
                        updatedAt = stamp(it.updatedAtMillis),
                    )
                },
            )
            appMeta.putAll(snapshot.settings.map { AppMetaEntity(it.key, it.value) })
        }
    }

    /**
     * Removes everything the user has and returns the app to its first run.
     *
     * There has to be a way to do this from inside the app. Until now the only
     * route was Android's own "clear storage", which is three levels into
     * system settings and which most people will never find — so someone
     * handing a phone on, or starting a new job, had no way to get their old
     * rota off it.
     *
     * The shift types are reseeded rather than left empty, so what the user
     * gets back is a working first-run app rather than an empty screen that
     * looks broken.
     */
    suspend fun deleteEverything() = db.withTransaction {
        // Overrides first: the RESTRICT foreign key to shift_type means
        // clearing the shifts before the days that reference them fails.
        overrides.deleteAll()
        patterns.deleteAll()
        shiftTypes.deleteAll()
        appMeta.deleteAll()
        seedDefaultsIfEmpty()
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
        /** Not a reminder setting — see [observeReminderPromptSeen]. */
        const val REMINDER_PROMPT_SEEN = "reminder.prompt_seen"

        const val COLOR_DAY = 0xFFE0A33C.toInt()
        const val COLOR_NIGHT = 0xFF3D5A80.toInt()
        const val COLOR_EARLY = 0xFF2A9D8F.toInt()
        const val COLOR_LATE = 0xFF7B5EA7.toInt()

        /**
         * Colours for shifts created by an import.
         *
         * Distinct from the four seeded ones, so a shift that arrived from
         * someone else's rota does not masquerade as a built-in, and cycled by
         * sort order so two new shifts are never the same colour.
         */
        val NEW_SHIFT_COLORS = intArrayOf(
            0xFFB5654A.toInt(),
            0xFF4F7942.toInt(),
            0xFF8C5C8E.toInt(),
            0xFF3F7C88.toInt(),
            0xFF6B7A87.toInt(),
        )
    }
}

/**
 * The outcome of adopting a shared rota.
 *
 * [createdShiftCodes] is empty in the common case — two people on the same site
 * use the same letters — and worth telling the user about when it is not, since
 * the new shifts have no times yet.
 */
data class SharedPatternImport(
    val pattern: Pattern,
    val createdShiftCodes: List<String>,
)

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
