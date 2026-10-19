package eu.kanade.tachiyomi.ui.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.abs

class ReaderSelectionStitchTest {
    @Test
    fun adjacentWebtoonSlicesAbutInSourcePixels() {
        val layout = layoutSelectionStitch(
            listOf(
                SelectionStitchPart(
                    screenLeft = 0f,
                    screenTop = 100f,
                    screenRight = 100f,
                    screenBottom = 150f,
                    bitmapWidth = 200,
                    bitmapHeight = 100,
                ),
                SelectionStitchPart(
                    screenLeft = 0f,
                    screenTop = 150f,
                    screenRight = 100f,
                    screenBottom = 180f,
                    bitmapWidth = 200,
                    bitmapHeight = 60,
                ),
            ),
        )!!

        assertEquals(200, layout.width)
        assertEquals(160, layout.height)
        assertEquals(0f, layout.slots[0].top)
        assertEquals(100f, layout.slots[0].bottom)
        assertEquals(100f, layout.slots[1].top)
        assertEquals(160f, layout.slots[1].bottom)
    }

    @Test
    fun hairlineSliceGapIsClosedAndARealGapIsKept() {
        val closed = layoutSelectionStitch(
            listOf(
                part(top = 0f, bottom = 50f, bitmapHeight = 100),
                part(top = 51f, bottom = 80f, bitmapHeight = 60),
            ),
        )!!
        assertEquals(closed.slots[0].bottom, closed.slots[1].top, 0.01f)

        val separated = layoutSelectionStitch(
            listOf(
                part(top = 0f, bottom = 50f, bitmapHeight = 100),
                part(top = 70f, bottom = 100f, bitmapHeight = 60),
            ),
        )!!
        val gap = separated.slots[1].top - separated.slots[0].bottom
        assertEquals(40f, gap, 0.01f)
    }

    @Test
    fun sideBySidePagesStaySideBySide() {
        val layout = layoutSelectionStitch(
            listOf(
                SelectionStitchPart(0f, 0f, 100f, 200f, 100, 200),
                SelectionStitchPart(100f, 0f, 200f, 200f, 100, 200),
            ),
        )!!

        assertEquals(0f, layout.slots[0].left)
        assertEquals(100f, layout.slots[1].left)
        assertEquals(200, layout.width)
        assertEquals(200, layout.height)
        assertEquals(0f, abs(layout.slots[0].right - layout.slots[1].left))
    }

    private fun part(top: Float, bottom: Float, bitmapHeight: Int): SelectionStitchPart {
        return SelectionStitchPart(
            screenLeft = 0f,
            screenTop = top,
            screenRight = 100f,
            screenBottom = bottom,
            bitmapWidth = 200,
            bitmapHeight = bitmapHeight,
        )
    }
}
