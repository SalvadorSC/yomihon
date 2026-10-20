package mihon.domain.dictionary.zh

/**
 * Simplified-Chinese headwords mapped to one reading each.
 * The bundled file keeps the first CC-CEDICT reading, which is the common one
 * (好 → hǎo, 的 → de).
 */
interface ChineseLexicon {
    fun readingOf(word: String): String?

    val maxWordLength: Int
}

class MapChineseLexicon(
    private val readings: Map<String, String>,
) : ChineseLexicon {
    override val maxWordLength: Int = readings.keys.maxOfOrNull { it.length } ?: 1

    override fun readingOf(word: String): String? = readings[word]
}

fun parseCedictPrimaryLines(lines: Sequence<String>): MapChineseLexicon {
    val readings = LinkedHashMap<String, String>()
    for (line in lines) {
        if (line.isBlank() || line.startsWith("#")) continue
        val tab = line.indexOf('\t')
        if (tab <= 0 || tab == line.lastIndex) continue
        val word = line.substring(0, tab)
        val pinyin = line.substring(tab + 1).trim()
        if (word.isEmpty() || pinyin.isEmpty() || word in readings) continue
        readings[word] = pinyin
    }
    return MapChineseLexicon(readings)
}
