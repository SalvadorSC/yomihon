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
}
