package mihon.domain.dictionary.interactor

/**
 * Controls which language parser [SearchDictionaryTerms] uses when searching.
 *
 * Pass one of these values to [SearchDictionaryTerms.search] or
 * [SearchDictionaryTerms.findFirstWordMatch] via the [parserLanguage] parameter.
 * The default, [AUTO], lets the interactor infer the parser from the query's
 * character script automatically.
 */
enum class ParserLanguage {
    /** Detect the script automatically from the query text (default). */
    AUTO,

    /** Force the Japanese pipeline: romaji -> kana conversion and deinflection. */
    JAPANESE,

    /** Force the Korean pipeline: direct character-by-character longest match. */
    KOREAN,

    /** Force the Chinese pipeline: CJK character-by-character longest match. */
    CHINESE,

    /** Force the English pipeline: space-delimited words. */
    ENGLISH,
}

/**
 * Auto treats pure Hanzi as Japanese. While the Chinese OCR engine is selected, that
 * misread would skip the CC-CEDICT longest-match parser, so prefer Chinese instead.
 * Kana or hangul still stays on its own parser. An explicit parser choice is unchanged.
 */
fun ParserLanguage.preferringChineseForHanzi(
    text: String,
    chineseOcrActive: Boolean,
): ParserLanguage {
    if (this != ParserLanguage.AUTO || !chineseOcrActive) {
        return this
    }
    if (!text.containsHanzi() || text.containsJapaneseKana() || text.containsHangul()) {
        return this
    }
    return ParserLanguage.CHINESE
}

private fun String.containsHanzi(): Boolean {
    return any { it.isHanzi() }
}

private fun String.containsJapaneseKana(): Boolean {
    return any { character ->
        character in '\u3040'..'\u30FF' || character in '\uFF66'..'\uFF9D'
    }
}

private fun String.containsHangul(): Boolean {
    return any { character ->
        character in '\u1100'..'\u11FF' || character in '\uAC00'..'\uD7A3'
    }
}

private fun Char.isHanzi(): Boolean {
    return code in 0x3400..0x4DBF || code in 0x4E00..0x9FFF || code in 0xF900..0xFAFF
}
