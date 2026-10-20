package mihon.data.dictionary.zh

import mihon.domain.dictionary.zh.ChineseSentenceSegmenter
import mihon.domain.dictionary.zh.parseCedictPrimaryLines
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class BundledChineseLexiconTest {
    private val segmenter = ChineseSentenceSegmenter(
        File("src/main/assets/zh/cedict_primary.tsv").bufferedReader().use { reader ->
            parseCedictPrimaryLines(reader.lineSequence())
        },
    )

    @Test
    fun weatherBubbleSplitsIntoTodayWeatherReallyGood() {
        val sentence = segmenter.segment("今天天气真好")

        assertEquals(listOf("今天", "天气", "真", "好"), sentence.words.map { it.text })
        assertEquals("jīntiān tiānqì zhēn hǎo", sentence.pinyin)
    }

    @Test
    fun okUsesHaoThirdToneAndNeutralDe() {
        val sentence = segmenter.segment("好的")

        assertEquals(listOf("好", "的"), sentence.words.map { it.text })
        assertEquals("hǎo de", sentence.pinyin)
    }

    @Test
    fun bankUsesTheWordReadingRatherThanTheWalkReading() {
        assertEquals("yínháng", segmenter.segment("银行").pinyin)
    }

    @Test
    fun traditionalSpellingsUseTheSimplifiedReading() {
        assertEquals("fánhuá", segmenter.segment("繁華").pinyin)
        assertEquals("hǎiōu", segmenter.segment("海鷗").pinyin)
        assertEquals("niánlíng", segmenter.segment("年齡").pinyin)
        assertEquals("tǐzhòng", segmenter.segment("體重").pinyin)
        assertEquals("guāngtóu", segmenter.segment("光頭").pinyin)
        assertEquals("fēnbié", segmenter.segment("分別").pinyin)
        assertEquals("yōuměi", segmenter.segment("優美").pinyin)
        assertEquals(
            listOf("繁華", "的", "大都會"),
            segmenter.segment("繁華的大都會").words.map { it.text },
        )
    }
}
