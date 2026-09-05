package com.turnus.rota.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities.
 *
 * Two different units of time live here and must never be confused:
 *
 * - `anchor_day` and `day` are **DayNumber** — civil days since 1970-01-01,
 *   no zone, no clock.
 * - `created_at` and `updated_at` are **epoch milliseconds**.
 *
 * The engine's `DayNumber` value class keeps them apart in Kotlin. At the
 * SQLite boundary both are `INTEGER`, so the mapping layer is the only place
 * the distinction is enforced by convention rather than by the compiler —
 * which is why the mappers are deliberately small and explicit.
 *
 * Note also what is NOT here: there is no table of generated shifts. The
 * database stores the pattern and its exceptions; every calendar cell is
 * recomputed on read.
 */

@Entity(
    tableName = "shift_type",
    indices = [
        Index(value = ["code"], unique = true),
        Index(value = ["sort_order"]),
    ],
)
data class ShiftTypeEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    /** Packed ARGB. */
    val color: Int,
    /** Minutes from midnight, 0..1439. Null together with [durationMinute] for an all-day marker. */
    @ColumnInfo(name = "start_minute") val startMinute: Int?,
    /** 1..1440. May push past midnight — that is how night shifts are expressed. */
    @ColumnInfo(name = "duration_minute") val durationMinute: Int?,
    @ColumnInfo(name = "is_working") val isWorking: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "pattern")
data class PatternEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** DayNumber of slot 0. */
    @ColumnInfo(name = "anchor_day") val anchorDay: Long,
    /** Encoded by [SlotCodec]. */
    val slots: String,
    /**
     * Denormalised length of [slots].
     *
     * Kept so the cycle length is queryable without decoding, and so a
     * corrupted slots string is detectable. The repository is responsible for
     * keeping the two in step — SQLite CHECK constraints cannot be expressed
     * through Room annotations, so this invariant lives in Kotlin and in tests.
     */
    @ColumnInfo(name = "slot_count") val slotCount: Int,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * A sparse exception layered over a pattern: a swap, sickness, leave, overtime.
 *
 * The primary key is composite even though v1 supports a single active pattern.
 * Multiple patterns — one per partner — is the likeliest v2 feature, and adding
 * `pattern_id` to the key now costs nothing while adding it later would be a
 * migration.
 *
 * Three states have to stay distinguishable, which takes two columns:
 *
 * - no row — the day follows the pattern
 * - row, `overrides_shift = 1`, `shift_type_id` set — the shift was changed
 * - row, `overrides_shift = 1`, `shift_type_id` null — explicitly taken off
 * - row, `overrides_shift = 0` — a note only; the shift still follows the pattern
 *
 * Without the flag, attaching a note would have to pin the day's shift, and a
 * later `shiftActivePatternBy` — the one-field fix for a misaligned rota that
 * this whole design exists to make possible — would leave every annotated day
 * stranded on its old shift.
 */
@Entity(
    tableName = "day_override",
    primaryKeys = ["pattern_id", "day"],
    foreignKeys = [
        ForeignKey(
            entity = PatternEntity::class,
            parentColumns = ["id"],
            childColumns = ["pattern_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ShiftTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["shift_type_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["shift_type_id"])],
)
data class DayOverrideEntity(
    @ColumnInfo(name = "pattern_id") val patternId: String,
    /** DayNumber. */
    val day: Long,
    @ColumnInfo(name = "shift_type_id") val shiftTypeId: String?,
    /** False when this row carries only a note and the shift still follows the pattern. */
    @ColumnInfo(name = "overrides_shift", defaultValue = "1") val overridesShift: Boolean,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
