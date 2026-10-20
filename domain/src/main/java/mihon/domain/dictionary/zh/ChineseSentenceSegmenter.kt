package mihon.domain.dictionary.zh

/**
 * Forward maximum match against a CC-CEDICT headword list.
 *
 * Matching starts at the beginning of the bubble, so 今天天气真好 becomes
 * 今天 / 天气 / 真 / 好. Starting on the second character would prefer 天天,
 * which is the tap-offset bug this replaces.
 */
class ChineseSentenceSegmenter(
    private val lexicon: ChineseLexicon,
) {
    fun segment(raw: String): ChineseSentence {
        val text = normalizeChineseBubbleText(raw)
        if (text.isEmpty()) return ChineseSentence(text, emptyList())

        val words = mutableListOf<ChineseSegment>()
        var index = 0
        while (index < text.length) {
            val current = text[index]
            if (current.isCjkIdeograph()) {
                val match = longestMatch(text, index)
                if (match == null) {
                    words += ChineseSegment(current.toString(), "")
                    index += 1
                } else {
                    words += match
                    index += match.text.length
                }
                continue
            }

            if (current.isLetterOrDigit()) {
                val start = index
                index += 1
                while (index < text.length && text[index].isLetterOrDigit() && !text[index].isCjkIdeograph()) {
                    index += 1
                }
                val token = text.substring(start, index)
                words += ChineseSegment(token, token)
                continue
            }

            index += 1
        }
        return ChineseSentence(text, words)
    }

    private fun longestMatch(text: String, start: Int): ChineseSegment? {
        val limit = minOf(lexicon.maxWordLength, text.length - start)
        for (length in limit downTo 1) {
            val slice = text.substring(start, start + length)
            if (slice.any { !it.isCjkIdeograph() }) continue
            val reading = lexicon.readingOf(slice) ?: continue
            return ChineseSegment(slice, reading)
        }
        return null
    }
}

internal fun Char.isCjkIdeograph(): Boolean {
    return this in '\u3400'..'\u4DBF' || this in '\u4E00'..'\u9FFF'
}
