package mihon.data.ocr.zh

import mihon.data.ocr.TextPostprocessor
import mihon.domain.ocr.model.OcrTextOrientation
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

    @Test
    fun nearbyLinesMergeIntoOneBubbleAndDistantBubblesStaySplit() {
        val regions = ChineseBubbleGrouper.group(
            blocks = listOf(
                block("你好世界", left = 100f, top = 100f, right = 300f, bottom = 140f),
                block("今天天气", left = 110f, top = 150f, right = 280f, bottom = 190f),
                block("不", left = 700f, top = 800f, right = 900f, bottom = 860f),
            ),
            imageWidth = 1000,
            imageHeight = 1000,
        )
        assertEquals(listOf("你好世界今天天气", "不"), regions.map { it.text })
        assertEquals(OcrTextOrientation.Horizontal, regions[0].textOrientation)
        assertTrue(regions[0].boundingBox.top < regions[1].boundingBox.top)
        assertTrue(regions[0].boundingBox.bottom > 0.18f)
        assertTrue(regions[0].boundingBox.bottom < 0.22f)
    }

    @Test
    fun verticalBubblesAreOrderedRightToLeft() {
        val regions = ChineseBubbleGrouper.group(
            blocks = listOf(
                block("左", left = 0f, top = 0f, right = 20f, bottom = 80f, angleDegrees = 90f),
                block("右", left = 80f, top = 0f, right = 100f, bottom = 80f, angleDegrees = 90f),
            ),
            imageWidth = 200,
            imageHeight = 200,
        )
        assertEquals(listOf("右", "左"), regions.map { it.text })
        assertEquals(OcrTextOrientation.Vertical, regions[0].textOrientation)
        assertTrue(regions[0].boundingBox.left > regions[1].boundingBox.left)
    }

    @Test
    fun dropsManhuarenAndDm5WatermarksFromTheTestPages() {
        val regions = ChineseBubbleGrouper.group(
            blocks = listOf(
                block("妖灵师和武者都有五个等级", left = 218f, top = 31f, right = 364f, bottom = 216f),
                block("烈焰妖狐", left = 56f, top = 425f, right = 183f, bottom = 457f),
                block("manhuaren.com", left = 658f, top = 1100f, right = 790f, bottom = 1118f),
                block("manhuarencom", left = 650f, top = 1096f, right = 792f, bottom = 1120f),
                block("DM5", left = 710f, top = 8f, right = 778f, bottom = 36f),
                block("dm5.com", left = 16f, top = 1088f, right = 140f, bottom = 1116f),
            ),
            imageWidth = 800,
            imageHeight = 1131,
        )

        assertEquals(
            listOf("妖灵师和武者都有五个等级", "烈焰妖狐"),
            regions.map { it.text },
        )
        assertEquals(listOf(0, 1), regions.map { it.order })
    }

    @Test
    fun dropsOtherSiteCreditsAndCornerLatin() {
        val regions = ChineseBubbleGrouper.group(
            blocks = listOf(
                block("圣祖山脉之外的世界", left = 40f, top = 180f, right = 520f, bottom = 240f),
                block("www.kuaikanmanhua.com", left = 180f, top = 400f, right = 620f, bottom = 448f),
                block("www.", left = 640f, top = 16f, right = 760f, bottom = 48f),
                block("example.net", left = 24f, top = 980f, right = 200f, bottom = 1016f),
                block("example.cn", left = 280f, top = 980f, right = 440f, bottom = 1016f),
                block("mantuarencom", left = 658f, top = 1096f, right = 791f, bottom = 1120f),
            ),
            imageWidth = 800,
            imageHeight = 1131,
        )

        assertEquals(listOf("圣祖山脉之外的世界"), regions.map { it.text })
    }

    @Test
    fun keepsChineseBubblesThatContainOccasionalLatin() {
        val regions = ChineseBubbleGrouper.group(
            blocks = listOf(
                block("Hello 世界", left = 100f, top = 100f, right = 360f, bottom = 160f),
                block("A1，你好！", left = 100f, top = 220f, right = 360f, bottom = 280f),
                block("OK，出发", left = 120f, top = 340f, right = 320f, bottom = 400f),
                block("BOSS", left = 330f, top = 480f, right = 450f, bottom = 540f),
                block("今天去 www.kuaikanmanhua.com 看", left = 40f, top = 700f, right = 740f, bottom = 760f),
                block("DM5很强", left = 160f, top = 860f, right = 360f, bottom = 920f),
            ),
            imageWidth = 800,
            imageHeight = 1131,
        )

        assertEquals(
            listOf(
                "Hello 世界",
                "A1，你好！",
                "OK，出发",
                "BOSS",
                "今天去 www.kuaikanmanhua.com 看",
                "DM5很强",
            ),
            regions.map { it.text },
        )
    }

    @Test
    fun cornerCreditDoesNotChangeAVerticalColumn() {
        val regions = ChineseBubbleGrouper.group(
            blocks = listOf(
                block(
                    "繁華環璇的大都會",
                    left = 635f,
                    top = 250f,
                    right = 675f,
                    bottom = 820f,
                    angleDegrees = 90f,
                ),
                block("manhuaren.com", left = 658f, top = 1100f, right = 790f, bottom = 1118f),
                block("DM5", left = 20f, top = 12f, right = 90f, bottom = 40f),
            ),
            imageWidth = 800,
            imageHeight = 1131,
        )

        assertEquals(listOf("繁華環璇的大都會"), regions.map { it.text })
        assertEquals(OcrTextOrientation.Vertical, regions[0].textOrientation)
    }

    private fun block(
        text: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        angleDegrees: Float = 0f,
    ): ChineseTextBlock {
        return ChineseTextBlock(
            lines = listOf(line(text, left, top, right, bottom, angleDegrees)),
            left = left,
            top = top,
            right = right,
            bottom = bottom,
        )
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
