package com.turnus.rota.data

/**
 * Encodes a pattern's slot cycle for storage in one TEXT column.
 *
 * A slot is a shift-type id, or null for a day off. Ids are generated UUIDs, so
 * they never contain a comma — which makes a comma-delimited list safe, and
 * leaves the stored value legible in any database browser. That readability is
 * worth more than it sounds when you are debugging someone's exported backup.
 *
 * `-` marks a day off. A UUID is never a bare hyphen, so the sentinel cannot
 * collide with a real id.
 *
 * A JSON array was the other candidate and was rejected: it would pull in a
 * serialization dependency and a compiler plugin to store a flat list of
 * strings, and it reads worse in a DB browser.
 */
internal object SlotCodec {

    private const val OFF = "-"
    private const val SEPARATOR = ","

    fun encode(slots: List<String?>): String {
        require(slots.isNotEmpty()) { "a pattern needs at least one slot" }
        require(slots.none { it != null && (it.contains(SEPARATOR) || it == OFF) }) {
            "shift ids must not contain '$SEPARATOR' or equal '$OFF'"
        }
        return slots.joinToString(SEPARATOR) { it ?: OFF }
    }

    /**
     * @throws IllegalArgumentException on an empty string, so a corrupted row
     *   fails loudly at the boundary rather than producing a pattern that
     *   throws later from somewhere less obvious.
     */
    fun decode(encoded: String): List<String?> {
        require(encoded.isNotEmpty()) { "encoded slots must not be empty" }
        return encoded.split(SEPARATOR).map { if (it == OFF) null else it }
    }
}
