package eu.kanade.tachiyomi.ui.reader

/**
 * Geometry for one slice of a box selection. Screen coordinates describe where the
 * slice was on screen. Bitmap size is the decoded crop, which is usually larger.
 */
internal data class SelectionStitchPart(
    val screenLeft: Float,
    val screenTop: Float,
    val screenRight: Float,
    val screenBottom: Float,
    val bitmapWidth: Int,
    val bitmapHeight: Int,
)

internal data class SelectionStitchSlot(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

internal data class SelectionStitchLayout(
    val width: Int,
    val height: Int,
    val slots: List<SelectionStitchSlot>,
)

/**
 * Places selection crops onto one bitmap using their on-screen layout, scaled into
 * source pixels. Webtoon chapters split a tall strip into stacked slices; a box that
 * crosses the cut must be stitched in this space or the halves overlap.
 *
 * [snapScreenGapPx] closes a hairline gap between stacked slices so a character cut
 * by the split is not separated by a blank row. Larger gaps, such as two bubbles,
 * are left alone.
 */
internal fun layoutSelectionStitch(
    parts: List<SelectionStitchPart>,
    snapScreenGapPx: Float = 2f,
): SelectionStitchLayout? {
    if (parts.isEmpty()) return null
    if (parts.any { part ->
            part.bitmapWidth <= 0 ||
                part.bitmapHeight <= 0 ||
                part.screenRight - part.screenLeft <= 0.5f ||
                part.screenBottom - part.screenTop <= 0.5f
        }
    ) {
        return null
    }

    val snapped = snapVerticalGaps(parts, snapScreenGapPx)
    val minLeft = snapped.minOf { it.screenLeft }
    val minTop = snapped.minOf { it.screenTop }
    val scale = snapped.maxOf { part ->
        val screenWidth = part.screenRight - part.screenLeft
        val screenHeight = part.screenBottom - part.screenTop
        maxOf(part.bitmapWidth / screenWidth, part.bitmapHeight / screenHeight)
    }.coerceAtLeast(0.01f)

    val slots = snapped.map { part ->
        SelectionStitchSlot(
            left = (part.screenLeft - minLeft) * scale,
            top = (part.screenTop - minTop) * scale,
            right = (part.screenRight - minLeft) * scale,
            bottom = (part.screenBottom - minTop) * scale,
        )
    }
    return SelectionStitchLayout(
        width = slots.maxOf { it.right }.toInt().coerceAtLeast(1),
        height = slots.maxOf { it.bottom }.toInt().coerceAtLeast(1),
        slots = slots,
    )
}

private fun snapVerticalGaps(
    parts: List<SelectionStitchPart>,
    snapPx: Float,
): List<SelectionStitchPart> {
    if (parts.size < 2 || snapPx <= 0f) return parts

    val tops = FloatArray(parts.size) { parts[it].screenTop }
    val bottoms = FloatArray(parts.size) { parts[it].screenBottom }
    val order = parts.indices.sortedBy { tops[it] }
    for (position in 1 until order.size) {
        val current = order[position]
        var bestPrevious = -1
        var smallestGap = Float.POSITIVE_INFINITY
        for (earlierPosition in 0 until position) {
            val earlier = order[earlierPosition]
            val gap = tops[current] - bottoms[earlier]
            if (gap < -snapPx || !overlapsHorizontally(parts[earlier], parts[current])) continue
            if (gap < smallestGap) {
                smallestGap = gap
                bestPrevious = earlier
            }
        }
        if (bestPrevious >= 0 && smallestGap > 0f && smallestGap <= snapPx) {
            tops[current] -= smallestGap
            bottoms[current] -= smallestGap
        }
    }

    return parts.mapIndexed { index, part ->
        part.copy(
            screenTop = tops[index],
            screenBottom = bottoms[index],
        )
    }
}

private fun overlapsHorizontally(first: SelectionStitchPart, second: SelectionStitchPart): Boolean {
    return minOf(first.screenRight, second.screenRight) - maxOf(first.screenLeft, second.screenLeft) > 0f
}
