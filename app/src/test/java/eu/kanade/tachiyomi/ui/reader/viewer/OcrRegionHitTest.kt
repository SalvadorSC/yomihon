package eu.kanade.tachiyomi.ui.reader.viewer

import mihon.domain.ocr.model.OcrBoundingBox
import mihon.domain.ocr.model.OcrModel
import mihon.domain.ocr.model.OcrPageResult
import mihon.domain.ocr.model.OcrRegion
import mihon.domain.ocr.model.OcrTextOrientation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OcrRegionHitTest {
    @Test
    fun tapInsideNestedRegionsChoosesTheSmallest() {
        val page = page(
            region("大", 0.1f, 0.1f, 0.9f, 0.9f),
            region("小", 0.4f, 0.4f, 0.6f, 0.6f),
        )

        assertEquals("小", page.findRegionAt(sourceX = 500f, sourceY = 1000f)?.text)
        assertEquals("小", page.findRegionAtNormalized(0.5f, 0.5f)?.text)
    }

    @Test
    fun tapOutsideTextMissesAndANearEdgeStillHits() {
        val page = page(region("气泡", 0.40f, 0.40f, 0.60f, 0.60f))

        assertNull(page.findRegionAtNormalized(0.01f, 0.01f))
        assertEquals("气泡", page.findRegionAtNormalized(0.38f, 0.50f)?.text)
        assertNull(page.findRegionAtNormalized(0.30f, 0.50f))
    }

    private fun page(vararg regions: OcrRegion): OcrPageResult {
        return OcrPageResult(
            chapterId = 1L,
            pageIndex = 0,
            ocrModel = OcrModel.ZH_MLKIT,
            imageWidth = 1000,
            imageHeight = 2000,
            regions = regions.toList(),
        )
    }

    private fun region(
        text: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): OcrRegion {
        return OcrRegion(
            order = 0,
            text = text,
            boundingBox = OcrBoundingBox(left, top, right, bottom),
            textOrientation = OcrTextOrientation.Horizontal,
        )
    }
}
