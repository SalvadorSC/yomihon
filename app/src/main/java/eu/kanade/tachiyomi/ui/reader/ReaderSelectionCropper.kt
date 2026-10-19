package eu.kanade.tachiyomi.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.data.ocr.OcrPageInput
import eu.kanade.tachiyomi.data.ocr.OcrPageSourceResolver
import eu.kanade.tachiyomi.data.ocr.ResolvedOcrPages
import eu.kanade.tachiyomi.data.ocr.openCroppedBitmap
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderSelectionCapture
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

internal class ReaderSelectionCropper(
    private val ocrPageSourceResolver: OcrPageSourceResolver,
) {
    suspend fun cropSelectionBitmap(
        manga: Manga,
        captures: List<ReaderSelectionCapture>,
    ): Bitmap? {
        val resolvedPagesByChapter = mutableMapOf<Chapter, ResolvedOcrPages>()
        try {
            if (captures.size == 1) {
                return cropPageBitmap(manga, captures.first(), resolvedPagesByChapter)
            }

            logcat(LogPriority.DEBUG) {
                "Selection crop stitching ${captures.size} page segments"
            }

            val parts = captures.mapNotNull { capture ->
                cropPageBitmap(manga, capture, resolvedPagesByChapter)?.let { bitmap ->
                    CroppedSelectionPart(
                        bitmap = bitmap,
                        screenRect = capture.screenRect,
                    )
                }
            }

            if (parts.isEmpty()) {
                logcat(LogPriority.WARN) {
                    "Selection crop unavailable for all ${captures.size} page segments"
                }
                return null
            }

            try {
                val layout = layoutSelectionStitch(
                    parts.map { part ->
                        SelectionStitchPart(
                            screenLeft = part.screenRect.left,
                            screenTop = part.screenRect.top,
                            screenRight = part.screenRect.right,
                            screenBottom = part.screenRect.bottom,
                            bitmapWidth = part.bitmap.width,
                            bitmapHeight = part.bitmap.height,
                        )
                    },
                ) ?: return null
                val mergedBitmap = createBitmap(layout.width, layout.height)
                val canvas = Canvas(mergedBitmap)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                parts.forEachIndexed { index, part ->
                    val slot = layout.slots[index]
                    canvas.drawBitmap(
                        part.bitmap,
                        null,
                        RectF(slot.left, slot.top, slot.right, slot.bottom),
                        paint,
                    )
                }

                logcat(LogPriority.DEBUG) {
                    "Selection crop stitched bitmap size=${mergedBitmap.width}x${mergedBitmap.height}"
                }
                return mergedBitmap
            } finally {
                parts.forEach { part ->
                    if (!part.bitmap.isRecycled) {
                        part.bitmap.recycle()
                    }
                }
            }
        } finally {
            resolvedPagesByChapter.values.forEach(ResolvedOcrPages::close)
        }
    }

    private suspend fun cropPageBitmap(
        manga: Manga,
        capture: ReaderSelectionCapture,
        resolvedPagesByChapter: MutableMap<Chapter, ResolvedOcrPages>,
    ): Bitmap? {
        val page = capture.page
        val sourceRect = capture.sourceRect
        val chapter = page.chapter.chapter.toDomainChapter()
            ?: throw IllegalStateException("Chapter unavailable")

        capture.decodeBitmap()?.let { bitmap ->
            logcat(LogPriority.DEBUG) {
                "Selection crop page=${page.index} via loaded source size=${bitmap.width}x${bitmap.height}"
            }
            return bitmap
        }

        val pageInput = getPageInput(
            manga = manga,
            chapter = chapter,
            pageIndex = page.index,
            resolvedPagesByChapter = resolvedPagesByChapter,
        )

        pageInput.openCroppedBitmap(sourceRect)?.let { bitmap ->
            logcat(LogPriority.DEBUG) {
                "Selection crop page=${page.index} via OCR source size=${bitmap.width}x${bitmap.height}"
            }
            return bitmap
        }

        logcat(LogPriority.WARN) {
            "Selection crop page=${page.index} unavailable"
        }
        return null
    }

    private suspend fun getPageInput(
        manga: Manga,
        chapter: Chapter,
        pageIndex: Int,
        resolvedPagesByChapter: MutableMap<Chapter, ResolvedOcrPages>,
    ): OcrPageInput {
        val resolvedPages = resolvedPagesByChapter[chapter]
            ?: ocrPageSourceResolver.resolve(manga, chapter).also { resolvedPagesByChapter[chapter] = it }
        return resolvedPages.getPageInput(pageIndex)
            ?: throw IllegalStateException("Page unavailable for crop")
    }
}

private data class CroppedSelectionPart(
    val bitmap: Bitmap,
    val screenRect: RectF,
)
