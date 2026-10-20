package mihon.domain.dictionary.zh

import mihon.domain.dictionary.model.DictionaryTerm

/**
 * Put the reading chosen for the sentence first.
 * 好 in 好的 is hǎo, so that card stays above hào "to be fond of".
 */
fun orderTermsByPreferredReading(
    terms: List<DictionaryTerm>,
    preferredReading: String,
): List<DictionaryTerm> {
    val target = pinyinKey(preferredReading)
    if (target.isEmpty() || terms.size < 2) return terms
    val preferred = ArrayList<DictionaryTerm>(terms.size)
    val others = ArrayList<DictionaryTerm>(terms.size)
    for (term in terms) {
        if (pinyinKey(term.reading) == target) {
            preferred += term
        } else {
            others += term
        }
    }
    if (preferred.isEmpty()) return terms
    preferred += others
    return preferred
}

fun pinyinKey(raw: String): String {
    return raw.lowercase()
        .replace("u:", "ü")
        .replace(" ", "")
        .replace("·", "")
        .replace("'", "")
}
