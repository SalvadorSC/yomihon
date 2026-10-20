package mihon.data.ocr.zh

import mihon.domain.ocr.model.OcrBoundingBox

/**
 * Site credits that manhua hosts stamp on a page.
 *
 * These are not speech bubbles. A short Latin credit (a domain, DM5, or other
 * small Latin text in a corner) is dropped. A Chinese bubble that also contains
 * a few Latin letters is kept.
 */
internal object ChineseWatermark {
    fun isSiteCredit(text: String, box: OcrBoundingBox): Boolean {
        if (text.isBlank()) return false
        if (hanziCount(text) >= MIN_HANZI_TO_KEEP) return false

        val compact = compactLatin(text)
        if (compact.isEmpty()) return false
        if (isKnownCredit(text, compact)) return true
        return isSmallLatinNearEdge(compact, box)
    }

    private fun isKnownCredit(text: String, compact: String): Boolean {
        if (compact.length > MAX_CREDIT_LENGTH) return false
        if (KNOWN_HOSTS.any { host -> host in compact }) return true
        if (isDm5Credit(compact)) return true
        return DOTTED_URL.containsMatchIn(text) || WWW.containsMatchIn(text)
    }

    private fun isDm5Credit(compact: String): Boolean {
        return compact == "dm5" ||
            compact == "dm5com" ||
            compact == "dm5net" ||
            compact == "dm5cn"
    }

    /**
     * OCR often smears a corner credit into Latin that no longer contains the
     * host name, such as manhuaren.com read as mantuarencom. A short Latin-only
     * box sitting on a page edge is the same kind of stamp.
     */
    private fun isSmallLatinNearEdge(compact: String, box: OcrBoundingBox): Boolean {
        if (compact.length < MIN_EDGE_LATIN || compact.length > MAX_CREDIT_LENGTH) return false
        if (compact.none { it.isLetter() }) return false
        val width = box.width
        val height = box.height
        val small = height <= MAX_EDGE_HEIGHT && width <= MAX_EDGE_WIDTH
        if (!small) return false
        return box.left <= EDGE_BAND ||
            box.right >= 1f - EDGE_BAND ||
            box.top <= EDGE_BAND ||
            box.bottom >= 1f - EDGE_BAND
    }

    private fun hanziCount(text: String): Int {
        return text.count { character ->
            val code = character.code
            code in 0x3400..0x4DBF || code in 0x4E00..0x9FFF || code in 0xF900..0xFAFF
        }
    }

    private fun compactLatin(text: String): String {
        val builder = StringBuilder(text.length)
        for (character in text) {
            val folded = when (character) {
                in 'A'..'Z' -> character.lowercaseChar()
                in 'a'..'z' -> character
                in '0'..'9' -> character
                in '\uFF21'..'\uFF3A' -> (character.code - 0xFF21 + 'a'.code).toChar()
                in '\uFF41'..'\uFF5A' -> (character.code - 0xFF41 + 'a'.code).toChar()
                in '\uFF10'..'\uFF19' -> (character.code - 0xFF10 + '0'.code).toChar()
                else -> continue
            }
            builder.append(folded)
        }
        return builder.toString()
    }

    private val KNOWN_HOSTS = listOf(
        "manhuaren",
        "kuaikanmanhua",
    )

    private val DOTTED_URL = Regex(
        """(?i)(?:^|[^a-z0-9])[a-z0-9][a-z0-9-]{0,40}\.(?:com|net|cn)(?![a-z0-9])""",
    )

    private val WWW = Regex("""(?i)(?:^|[^a-z0-9])www\.""")

    private const val MIN_HANZI_TO_KEEP = 2
    private const val MAX_CREDIT_LENGTH = 40
    private const val MIN_EDGE_LATIN = 4
    private const val MAX_EDGE_HEIGHT = 0.08f
    private const val MAX_EDGE_WIDTH = 0.5f
    private const val EDGE_BAND = 0.15f
}
