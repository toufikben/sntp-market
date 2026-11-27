package dz.sntp.market.util

/**
 * Text folding for the shop search.
 *
 * The directory is Arabic and the phones are Arabic keyboards, where the same
 * shop name has several legitimate spellings. A trader typing «شلبي» got nothing
 * because the seed spells it «شلبي» with a different final letter form, and
 * «السعيد» missed «السعيد» typed with a bare alef. `contains` compares code
 * points, so every one of those variants was a dead end in a search box on a
 * screen that holds seven names.
 *
 * Folded, not stemmed: this only removes distinctions the script makes
 * optionally — hamza carriers, tatweel, diacritics, the two alef/ya/ta-marbuta
 * forms — and never changes the letters that carry meaning.
 */
object ArabicText {

    /**
     * Normalised form used for matching only; never displayed.
     *
     *  · أ إ آ ٱ → ا   (hamza carriers are written inconsistently)
     *  · ى → ي        (alef maqsura vs. ya)
     *  · ة → ه        (ta marbuta vs. ha)
     *  · ؤ → و, ئ → ي (hamza on a seat)
     *  · removes tatweel (ـ) and the harakat block
     *  · lowercases Latin, so "SK 13" matches "sk 13"
     *  · collapses whitespace, so a double space is not a failed search
     */
    fun fold(raw: String): String {
        val out = StringBuilder(raw.length)
        for (ch in raw) {
            val mapped = when (ch) {
                'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                'ى' -> 'ي'
                'ة' -> 'ه'
                'ؤ' -> 'و'
                'ئ' -> 'ي'
                '\u0640' -> null                       // tatweel
                in '\u064B'..'\u0652' -> null          // harakat
                in '\u0653'..'\u0655' -> null          // maddah, hamza above/below
                else -> ch
            }
            if (mapped != null) out.append(mapped)
        }
        return out.toString().lowercase().replace(WHITESPACE, " ").trim()
    }

    /** True when [query] appears in [text] once both are folded. */
    fun matches(text: String, query: String): Boolean {
        val q = fold(query)
        if (q.isEmpty()) return true
        return fold(text).contains(q)
    }

    private val WHITESPACE = Regex("\\s+")
}
