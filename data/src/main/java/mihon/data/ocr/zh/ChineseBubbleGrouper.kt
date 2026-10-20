package mihon.data.ocr.zh

import mihon.domain.ocr.model.OcrBoundingBox
import mihon.domain.ocr.model.OcrRegion
import mihon.domain.ocr.model.OcrTextOrientation
import kotlin.math.max
import kotlin.math.min

/**
 * One ML Kit text block in the pixel space of the bitmap that was scanned.
 */
internal data class ChineseTextBlock(
    val lines: List<ChineseOcrLine>,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float
        get() = (right - left).coerceAtLeast(0f)

    val height: Float
        get() = (bottom - top).coerceAtLeast(0f)
}

/**
 * Merges nearby Chinese text blocks into bubbles.
 *
 * ML Kit often returns one block per line. Stacked lines of the same bubble, and
 * fragments sitting side by side, are joined. Blocks that are far apart stay separate.
 * Site credits and other corner watermarks are dropped, so they are not outlined.
 * The merged text is ordered for manhua: horizontal bubbles top-to-bottom then
 * left-to-right, and a page that is mostly vertical columns right-to-left.
 */
internal object ChineseBubbleGrouper {
    fun group(
        blocks: List<ChineseTextBlock>,
        imageWidth: Int,
        imageHeight: Int,
    ): List<OcrRegion> {
        if (imageWidth <= 0 || imageHeight <= 0) return emptyList()
        val usable = blocks.filter { block ->
            block.width > 0f &&
                block.height > 0f &&
                block.lines.any { it.text.isNotBlank() }
        }
        if (usable.isEmpty()) return emptyList()

        val unionFind = UnionFind(usable.size)
        for (left in usable.indices) {
            for (right in left + 1 until usable.size) {
                if (shouldMerge(usable[left], usable[right])) {
                    unionFind.union(left, right)
                }
            }
        }

        val groups = usable.indices
            .groupBy { unionFind.find(it) }
            .values
            .map { indexes -> merge(indexes.map { usable[it] }) }

        val bubbles = groups.mapNotNull { bubble ->
            val text = ChineseTextPostprocessor.postprocess(ChineseOcrLayout.assemble(bubble.lines))
            if (text.isBlank()) return@mapNotNull null
            val box = OcrBoundingBox(
                left = (bubble.left / imageWidth).coerceIn(0f, 1f),
                top = (bubble.top / imageHeight).coerceIn(0f, 1f),
                right = (bubble.right / imageWidth).coerceIn(0f, 1f),
                bottom = (bubble.bottom / imageHeight).coerceIn(0f, 1f),
            )
            if (!box.isValid() || ChineseWatermark.isSiteCredit(text, box)) return@mapNotNull null
            PreparedBubble(bubble, text, box)
        }
        if (bubbles.isEmpty()) return emptyList()

        val verticalPage = bubbles.count { ChineseOcrLayout.isVertical(it.source.lines) } * 2 >= bubbles.size
        val ordered = if (verticalPage) {
            bubbles.sortedWith(
                compareByDescending<PreparedBubble> { (it.source.left + it.source.right) / 2f }
                    .thenBy { it.source.top },
            )
        } else {
            bubbles.sortedWith(
                compareBy<PreparedBubble> { it.source.top }
                    .thenBy { it.source.left },
            )
        }

        return ordered.mapIndexed { index, bubble ->
            OcrRegion(
                order = index,
                text = bubble.text,
                boundingBox = bubble.box,
                textOrientation = if (ChineseOcrLayout.isVertical(bubble.source.lines)) {
                    OcrTextOrientation.Vertical
                } else {
                    OcrTextOrientation.Horizontal
                },
            )
        }
    }

    private fun shouldMerge(first: ChineseTextBlock, second: ChineseTextBlock): Boolean {
        val minWidth = min(first.width, second.width).coerceAtLeast(1f)
        val minHeight = min(first.height, second.height).coerceAtLeast(1f)
        val horizontalOverlap = min(first.right, second.right) - max(first.left, second.left)
        val verticalOverlap = min(first.bottom, second.bottom) - max(first.top, second.top)
        if (horizontalOverlap > 0f && verticalOverlap > 0f) return true

        val verticalGap = max(0f, -verticalOverlap)
        val horizontalGap = max(0f, -horizontalOverlap)
        val stacked = verticalGap <= minHeight * VERTICAL_GAP_RATIO &&
            horizontalOverlap > minWidth * MIN_OVERLAP_RATIO
        val sideBySide = horizontalGap <= minWidth * HORIZONTAL_GAP_RATIO &&
            verticalOverlap > minHeight * MIN_OVERLAP_RATIO
        return stacked || sideBySide
    }

    private fun merge(blocks: List<ChineseTextBlock>): MergedBubble {
        return MergedBubble(
            lines = blocks.flatMap { it.lines },
            left = blocks.minOf { it.left },
            top = blocks.minOf { it.top },
            right = blocks.maxOf { it.right },
            bottom = blocks.maxOf { it.bottom },
        )
    }

    private data class MergedBubble(
        val lines: List<ChineseOcrLine>,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    )

    private data class PreparedBubble(
        val source: MergedBubble,
        val text: String,
        val box: OcrBoundingBox,
    )

    private class UnionFind(size: Int) {
        private val parent = IntArray(size) { it }

        fun find(index: Int): Int {
            var current = index
            while (parent[current] != current) {
                parent[current] = parent[parent[current]]
                current = parent[current]
            }
            return current
        }

        fun union(left: Int, right: Int) {
            val leftRoot = find(left)
            val rightRoot = find(right)
            if (leftRoot != rightRoot) {
                parent[rightRoot] = leftRoot
            }
        }
    }

    private const val VERTICAL_GAP_RATIO = 0.45f
    private const val HORIZONTAL_GAP_RATIO = 0.35f
    private const val MIN_OVERLAP_RATIO = 0.35f
}
