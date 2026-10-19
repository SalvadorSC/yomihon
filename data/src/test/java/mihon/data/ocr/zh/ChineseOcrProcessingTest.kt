package mihon.data.ocr.zh

import mihon.data.ocr.TextPostprocessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChineseOcrProcessingTest {
    @Test
    fun joinsLinesWithoutSpacesAndDropsSpacesBetweenHanzi() {
        assertEquals("你好吗", ChineseTextPostprocessor.postprocess("你 好\n吗"))
        assertEquals("你好世界", ChineseTextPostprocessor.postprocess("你好\r\n世界"))
    }

    @Test
    fun keepsAsciiLettersAndDigitsAndMapsChinesePunctuation() {
        assertEquals("A1，你好！", ChineseTextPostprocessor.postprocess("A1,你好!"))
        assertEquals("Hello 世界", ChineseTextPostprocessor.postprocess("Hello 世界"))
        assertEquals("什么……", ChineseTextPostprocessor.postprocess("什么..."))
        assertEquals("123你好", ChineseTextPostprocessor.postprocess("123你好"))
    }

    @Test
    fun japanesePostprocessorStillConvertsHalfWidth() {
        val processed = TextPostprocessor().postprocess("123漢字")
        assertEquals("１２３漢字", processed)
        assertFalse(processed.contains('1'))
    }

    @Test
    fun horizontalLinesReadTopToBottomWithoutSpaces() {
        val text = ChineseOcrLayout.assemble(
            listOf(
                line("你好", left = 0f, top = 0f, right = 80f, bottom = 20f),
                line("世界", left = 0f, top = 24f, right = 80f, bottom = 44f),
            ),
        )
        assertEquals("你好世界", text)
        assertFalse(ChineseOcrLayout.isVertical(listOf(line("你好世界", 0f, 0f, 120f, 24f))))
    }

    @Test
    fun verticalColumnsReadTopToBottomAndRightToLeft() {
        val lines = listOf(
            line("左", left = 0f, top = 0f, right = 20f, bottom = 20f, angleDegrees = 90f),
            line("边", left = 0f, top = 24f, right = 20f, bottom = 44f, angleDegrees = 90f),
            line("右", left = 40f, top = 0f, right = 60f, bottom = 20f, angleDegrees = -90f),
            line("边", left = 40f, top = 24f, right = 60f, bottom = 44f, angleDegrees = 90f),
        )
        assertTrue(ChineseOcrLayout.isVertical(lines))
        assertEquals("右边左边", ChineseOcrLayout.assemble(lines))
    }

    @Test
    fun stackedSingleCharactersAreTreatedAsAVerticalColumn() {
        val lines = listOf(
            line("你", left = 10f, top = 0f, right = 30f, bottom = 20f),
            line("好", left = 10f, top = 22f, right = 30f, bottom = 42f),
            line("吗", left = 10f, top = 44f, right = 30f, bottom = 64f),
        )
        assertTrue(ChineseOcrLayout.isVertical(lines))
        assertEquals("你好吗", ChineseOcrLayout.assemble(lines))
    }

    private fun line(
        text: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        angleDegrees: Float = 0f,
    ): ChineseOcrLine {
        return ChineseOcrLine(
            text = text,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            angleDegrees = angleDegrees,
        )
    }
}
