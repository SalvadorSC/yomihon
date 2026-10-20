package mihon.data.ocr.zh

/**
 * Cleans OCR text for Simplified Chinese dictionary lookup.
 *
 * Japanese post-processing turns ASCII into full-width forms and keeps line breaks.
 * Chinese has no word spaces, so line breaks are joined directly, spaces between
 * hanzi are removed, and ASCII letters and digits are left as-is.
 */
internal object ChineseTextPostprocessor {
    fun postprocess(text: String): String {
        if (text.isEmpty()) return text

        val lines = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map(::postprocessLine)
            .filter(String::isNotEmpty)

        return lines.joinToString(separator = "")
    }

    private fun postprocessLine(text: String): String {
        if (text.isEmpty()) return text

        val builder = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val character = text[index]
            if (character.isWhitespace()) {
                val next = nextNonWhitespace(text, index + 1)
                val previous = builder.lastOrNull()
                if (previous != null && next != null && shouldKeepSpace(previous, next)) {
                    if (builder.isEmpty() || builder.last() != ' ') {
                        builder.append(' ')
                    }
                }
                index = skipWhitespace(text, index)
                continue
            }

            if (character.isEllipsisDot()) {
                var nextIndex = index + 1
                var dotCount = 1
                while (nextIndex < text.length && text[nextIndex].isEllipsisDot()) {
                    dotCount++
                    nextIndex++
                }
                if (dotCount >= 2) {
                    builder.append('…').append('…')
                    index = nextIndex
                    continue
                }
            }

            builder.append(
                when (character) {
                    ',' -> '，'
                    '?', '？' -> '？'
                    '!', '！' -> '！'
                    else -> character
                },
            )
            index++
        }
        return builder.toString()
    }

    private fun shouldKeepSpace(previous: Char, next: Char): Boolean {
        return !previous.isHanziOrChinesePunctuation() || !next.isHanziOrChinesePunctuation()
    }

    private fun nextNonWhitespace(text: String, start: Int): Char? {
        var index = start
        while (index < text.length) {
            if (!text[index].isWhitespace()) return text[index]
            index++
        }
        return null
    }

    private fun skipWhitespace(text: String, start: Int): Int {
        var index = start
        while (index < text.length && text[index].isWhitespace()) {
            index++
        }
        return index
    }

    private fun Char.isEllipsisDot(): Boolean {
        return this == '.' || this == '．' || this == '…'
    }

    private fun Char.isHanziOrChinesePunctuation(): Boolean {
        if (isHanzi()) return true
        return this in CHINESE_PUNCTUATION || this == ',' || this == '?' || this == '!'
    }

    private fun Char.isHanzi(): Boolean {
        return code in 0x3400..0x4DBF || code in 0x4E00..0x9FFF || code in 0xF900..0xFAFF
    }

    private val CHINESE_PUNCTUATION = setOf(
        '，', '。', '！', '？', '、', '；', '：',
        '（', '）', '【', '】', '《', '》', '「', '」', '『', '』',
        '…', '—', '～', '·',
    )
}
