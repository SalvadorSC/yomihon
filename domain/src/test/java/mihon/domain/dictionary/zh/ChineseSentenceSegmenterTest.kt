package mihon.domain.dictionary.zh

import mihon.domain.dictionary.model.DictionaryTerm
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChineseSentenceSegmenterTest {
    private val segmenter = ChineseSentenceSegmenter(
        MapChineseLexicon(
            mapOf(
                "今天" to "jīntiān",
                "天气" to "tiānqì",
                "天天" to "tiāntiān",
                "真" to "zhēn",
                "好" to "hǎo",
                "的" to "de",
                "你好" to "nǐhǎo",
            ),
        ),
    )

    @Test
    fun wholeBubbleAvoidsTheCrossBoundaryEverydayMatch() {
        val sentence = segmenter.segment("今天天气\n真好")

        assertEquals(listOf("今天", "天气", "真", "好"), sentence.words.map { it.text })
        assertEquals("jīntiān tiānqì zhēn hǎo", sentence.pinyin)
        assertEquals("今天天气真好", sentence.text)
        assertFalse(sentence.words.any { it.text == "天天" })
    }

    @Test
    fun haoDeUsesTheGoodReadingRatherThanFondOf() {
        val sentence = segmenter.segment("好的")

        assertEquals(listOf("好", "的"), sentence.words.map { it.text })
        assertEquals("hǎo de", sentence.pinyin)
        assertEquals("hǎo", sentence.words.first().pinyin)
    }

    @Test
    fun punctuationStaysInTheSentenceAndOutOfThePinyin() {
        val sentence = segmenter.segment("你好！")

        assertEquals("你好！", sentence.text)
        assertEquals(listOf("你好"), sentence.words.map { it.text })
        assertEquals("nǐhǎo", sentence.pinyin)
    }

    @Test
    fun preferredReadingIsListedBeforeTheOtherPronunciation() {
        val ordered = orderTermsByPreferredReading(
            listOf(term("好", "hào"), term("好", "hǎo"), term("好", "hào")),
            "hǎo",
        )

        assertEquals(listOf("hǎo", "hào", "hào"), ordered.map { it.reading })
    }

    @Test
    fun translationStaysHiddenUntilTheModelIsOnTheDevice() {
        assertNull(ChineseTranslationPolicy.forPopup(modelOnDevice = false, translated = "today"))
        assertNull(ChineseTranslationPolicy.forPopup(modelOnDevice = true, translated = "  "))
        assertEquals("today", ChineseTranslationPolicy.forPopup(modelOnDevice = true, translated = " today "))
        assertFalse(ChineseTranslationPolicy.mayContactNetwork(explicitUserDownload = false))
        assertTrue(ChineseTranslationPolicy.mayContactNetwork(explicitUserDownload = true))
    }

    private fun term(expression: String, reading: String): DictionaryTerm {
        return DictionaryTerm(
            dictionaryId = 1L,
            expression = expression,
            reading = reading,
            definitionTags = null,
            rules = null,
            score = 0,
            glossary = emptyList(),
        )
    }
}
