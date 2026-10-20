package mihon.data.ocr.zh

/**
 * One recognized line in the coordinate space of the bitmap that was scanned.
 */
internal data class ChineseOcrLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val angleDegrees: Float = 0f,
) {
    val width: Float
        get() = (right - left).coerceAtLeast(0f)

    val height: Float
        get() = (bottom - top).coerceAtLeast(0f)

    val centerX: Float
        get() = (left + right) / 2f

    val centerY: Float
        get() = (top + bottom) / 2f
}

/**
 * Turns ML Kit lines into a string a Chinese dictionary can look up.
 *
 * Horizontal text is read left to right, top to bottom. Vertical text, including a
 * column of single characters, is read top to bottom and the columns right to left.
 * Lines are concatenated with no spaces; [ChineseTextPostprocessor] does the rest.
 */
internal object ChineseOcrLayout {
    fun assemble(lines: List<ChineseOcrLine>): String {
        val expanded = expandNewlines(lines)
        if (expanded.isEmpty()) return ""
        return if (isVertical(expanded)) {
            assembleVertical(expanded)
        } else {
            assembleHorizontal(expanded)
        }
    }

    fun isVertical(lines: List<ChineseOcrLine>): Boolean {
        if (lines.isEmpty()) return false
        if (lines.count { isVerticalAngle(it.angleDegrees) } * 2 >= lines.size) return true
        if (lines.count { it.height > it.width * 1.2f && it.text.length <= 4 } * 2 >= lines.size) {
            return true
        }
        if (lines.size < 2) return false

        val widths = lines.map { it.width.coerceAtLeast(1f) }.sorted()
        val heights = lines.map { it.height.coerceAtLeast(1f) }.sorted()
        val medianWidth = widths[widths.size / 2]
        val medianHeight = heights[heights.size / 2]
        val columns = countClusters(lines.map { it.centerX }, medianWidth * 0.75f)
        val rows = countClusters(lines.map { it.centerY }, medianHeight * 0.75f)
        val mostlyShort = lines.count { it.text.length <= 2 } * 2 >= lines.size
        return mostlyShort && columns < rows
    }

    private fun expandNewlines(lines: List<ChineseOcrLine>): List<ChineseOcrLine> {
        return lines.flatMap { line ->
            val parts = line.text.split('\n')
            if (parts.size <= 1) {
                val trimmed = line.text.trim()
                if (trimmed.isEmpty()) emptyList() else listOf(line.copy(text = trimmed))
            } else {
                val step = line.height / parts.size
                parts.mapIndexedNotNull { index, part ->
                    val trimmed = part.trim()
                    if (trimmed.isEmpty()) {
                        null
                    } else {
                        line.copy(
                            text = trimmed,
                            top = line.top + step * index,
                            bottom = line.top + step * (index + 1),
                        )
                    }
                }
            }
        }
    }

    private fun assembleHorizontal(lines: List<ChineseOcrLine>): String {
        val remaining = lines.sortedBy { it.centerY }.toMutableList()
        val rows = mutableListOf<List<ChineseOcrLine>>()
        while (remaining.isNotEmpty()) {
            val seed = remaining.removeAt(0)
            val row = mutableListOf(seed)
            val iterator = remaining.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                if (overlapsVertically(seed, candidate)) {
                    row += candidate
                    iterator.remove()
                }
            }
            rows += row.sortedBy { it.left }
        }
        return rows.joinToString(separator = "") { row ->
            row.joinToString(separator = "") { it.text }
        }
    }

    private fun assembleVertical(lines: List<ChineseOcrLine>): String {
        val columns = mutableListOf<MutableList<ChineseOcrLine>>()
        for (line in lines.sortedByDescending { it.centerX }) {
            val column = columns.find { members -> members.any { sameColumn(it, line) } }
            if (column == null) {
                columns += mutableListOf(line)
            } else {
                column += line
            }
        }
        return columns.joinToString(separator = "") { column ->
            column.sortedBy { it.top }.joinToString(separator = "") { it.text }
        }
    }

    private fun overlapsVertically(first: ChineseOcrLine, second: ChineseOcrLine): Boolean {
        val overlap = minOf(first.bottom, second.bottom) - maxOf(first.top, second.top)
        val minHeight = minOf(first.height, second.height).coerceAtLeast(1f)
        return overlap > minHeight * 0.4f
    }

    private fun sameColumn(first: ChineseOcrLine, second: ChineseOcrLine): Boolean {
        val width = ((first.width + second.width) / 2f).coerceAtLeast(1f)
        return kotlin.math.abs(first.centerX - second.centerX) <= width * 0.8f
    }

    private fun isVerticalAngle(angleDegrees: Float): Boolean {
        val normalized = kotlin.math.abs(angleDegrees % 180f)
        val fromHorizontal = if (normalized > 90f) 180f - normalized else normalized
        return fromHorizontal >= 60f
    }

    private fun countClusters(values: List<Float>, threshold: Float): Int {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        var clusters = 1
        var previous = sorted.first()
        for (value in sorted.drop(1)) {
            if (value - previous > threshold) {
                clusters++
            }
            previous = value
        }
        return clusters
    }
}
