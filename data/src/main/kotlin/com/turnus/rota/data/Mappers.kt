package com.turnus.rota.data

import com.turnus.rota.engine.DayNumber
import com.turnus.rota.engine.Overrides
import com.turnus.rota.engine.Pattern
import com.turnus.rota.engine.ShiftDefinition

/**
 * The boundary between stored rows and the engine's immutable domain types.
 *
 * Deliberately small and explicit. At the SQLite level a DayNumber and an
 * epoch-millisecond timestamp are both `INTEGER`, so this is the one layer
 * where keeping them apart is a matter of care rather than of types — worth
 * having in one readable place instead of scattered through the repository.
 */

internal fun PatternEntity.toDomain(): Pattern = Pattern(
    id = id,
    name = name,
    anchor = DayNumber(anchorDay),
    slots = SlotCodec.decode(slots),
)

internal fun Pattern.toEntity(
    isActive: Boolean,
    createdAt: Long,
    updatedAt: Long,
): PatternEntity = PatternEntity(
    id = id,
    name = name,
    anchorDay = anchor.value,
    slots = SlotCodec.encode(slots),
    slotCount = slots.size,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun ShiftTypeEntity.toDefinition(): ShiftDefinition = ShiftDefinition(
    id = id,
    code = code,
    name = name,
    startMinute = startMinute,
    durationMinute = durationMinute,
)

/**
 * Only rows that actually change the shift reach the engine.
 *
 * A note-only row is deliberately dropped here: the day still follows the
 * pattern, so handing it to [Overrides] would pin it. Among the rows that do
 * count, a null `shift_type_id` means the user explicitly took a working day
 * off — which [Overrides] keeps distinct from having no entry at all.
 */
internal fun List<DayOverrideEntity>.toOverrides(): Overrides =
    Overrides(filter { it.overridesShift }.associate { DayNumber(it.day) to it.shiftTypeId })
