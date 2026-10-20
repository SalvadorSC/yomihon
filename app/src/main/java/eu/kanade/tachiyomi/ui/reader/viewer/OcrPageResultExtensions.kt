package eu.kanade.tachiyomi.ui.reader.viewer

import mihon.domain.ocr.model.OcrBoundingBox
import mihon.domain.ocr.model.OcrPageResult
import mihon.domain.ocr.model.OcrRegion

fun OcrPageResult.findRegionAt(
    sourceX: Float,
    sourceY: Float,
): OcrRegion? {
    if (imageWidth <= 0 || imageHeight <= 0) return null
    return findRegionAtNormalized(
        normalizedX = sourceX / imageWidth,
        normalizedY = sourceY / imageHeight,
    )
}

/**
 * Picks the smallest region that contains the point. A small pad around each box
 * catches taps that land on the bubble edge. [normalizedX] and [normalizedY] are
 * fractions of the image the user is looking at.
 */
fun OcrPageResult.findRegionAtNormalized(
    normalizedX: Float,
    normalizedY: Float,
): OcrRegion? {
    return regions
        .filter { region ->
            region.boundingBox.containsNormalized(normalizedX, normalizedY)
        }
        .minByOrNull { region ->
            val box = region.boundingBox
            box.width * box.height
        }
}

private fun OcrBoundingBox.containsNormalized(
    normalizedX: Float,
    normalizedY: Float,
): Boolean {
    if (!isValid()) return false
    val pad = HIT_PAD_RATIO * minOf(width, height)
    return normalizedX in (left - pad)..(right + pad) &&
        normalizedY in (top - pad)..(bottom + pad)
}

private const val HIT_PAD_RATIO = 0.15f
