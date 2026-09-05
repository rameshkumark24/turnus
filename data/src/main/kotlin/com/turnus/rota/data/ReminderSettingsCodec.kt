package com.turnus.rota.data

import com.turnus.rota.engine.ReminderSettings

/**
 * Stores reminder settings as rows in `app_meta`.
 *
 * One key per field rather than one blob, so adding a setting later is an
 * insert rather than a parse change with a compatibility story attached.
 *
 * Decoding never throws. [ReminderSettings] validates its arguments, so a row
 * holding a value out of range — an old build, a hand-edited database, a
 * half-finished write — would otherwise take the process down on the read that
 * happens at startup. A user who has lost a setting can set it again; a user
 * whose app will not open cannot do anything at all.
 */
internal object ReminderSettingsCodec {

    const val ENABLED = "reminder.enabled"
    const val LEAD_MINUTES = "reminder.lead_minutes"
    const val MUTED = "reminder.muted_shift_ids"
    const val ALL_DAY_MINUTE = "reminder.all_day_minute"

    private const val SEPARATOR = ","

    fun decode(rows: Map<String, String>): ReminderSettings {
        val defaults = ReminderSettings()
        return ReminderSettings(
            enabled = rows[ENABLED]?.toBooleanStrictOrNull() ?: defaults.enabled,
            leadMinutes = rows[LEAD_MINUTES]?.toIntOrNull()
                ?.coerceIn(0, ReminderSettings.MAX_LEAD_MINUTES)
                ?: defaults.leadMinutes,
            // Blank entries dropped: a trailing separator, or the empty string
            // that means "nothing muted", must not become an id of "".
            mutedShiftTypeIds = rows[MUTED]
                ?.split(SEPARATOR)
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: defaults.mutedShiftTypeIds,
            allDayNoticeMinute = rows[ALL_DAY_MINUTE]?.toIntOrNull()
                ?.coerceIn(0, 1439)
                ?: defaults.allDayNoticeMinute,
        )
    }

    fun encode(settings: ReminderSettings): List<AppMetaEntity> = listOf(
        AppMetaEntity(ENABLED, settings.enabled.toString()),
        AppMetaEntity(LEAD_MINUTES, settings.leadMinutes.toString()),
        AppMetaEntity(MUTED, settings.mutedShiftTypeIds.sorted().joinToString(SEPARATOR)),
        AppMetaEntity(ALL_DAY_MINUTE, settings.allDayNoticeMinute.toString()),
    )
}
