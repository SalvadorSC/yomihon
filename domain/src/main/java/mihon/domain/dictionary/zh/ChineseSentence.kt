package mihon.domain.dictionary.zh

data class ChineseSegment(
    val text: String,
    val pinyin: String,
)

data class ChineseSentence(
    val text: String,
    val words: List<ChineseSegment>,
) {
    val pinyin: String
        get() = words.map { it.pinyin }.filter { it.isNotBlank() }.joinToString(" ")
}

/** Header shown above the word list in the Chinese OCR popup. */
data class ChineseSentenceGuide(
    val text: String,
    val pinyin: String,
    val translation: String?,
)

fun ChineseSentence.toGuide(translation: String?): ChineseSentenceGuide {
    return ChineseSentenceGuide(
        text = text,
        pinyin = pinyin,
        translation = translation,
    )
}

/** Drop whitespace inside a bubble. Newlines from OCR must not split a word. */
fun normalizeChineseBubbleText(raw: String): String {
    return buildString(raw.length) {
        raw.forEach { char ->
            if (!char.isWhitespace()) append(char)
        }
    }
}
