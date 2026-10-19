package mihon.data.ocr.zh

import android.graphics.Bitmap
import androidx.core.graphics.scale
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import mihon.data.ocr.OcrEngine
import mihon.domain.ocr.model.OcrBoundingBox
import mihon.domain.ocr.model.OcrRegion
import mihon.domain.ocr.model.OcrTextOrientation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/**
 * Simplified Chinese OCR backed by the bundled ML Kit Chinese model.
 *
 * The dependency is `com.google.mlkit:text-recognition-chinese`, which links the model
 * into the APK. This is not the Play Services variant that downloads weights on first use.
 */
internal class MlKitChineseOcrEngine : OcrEngine {
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    override suspend fun recognizeText(image: Bitmap): String {
        val (working, recycleWorking) = prepareImage(image)
        return try {
            val result = recognizer.process(InputImage.fromBitmap(working, 0)).awaitTask()
            val lines = result.textBlocks.flatMap { block -> block.toLines() }
            ChineseTextPostprocessor.postprocess(ChineseOcrLayout.assemble(lines))
        } finally {
            if (recycleWorking && !working.isRecycled) {
                working.recycle()
            }
        }
    }

    /**
     * Full-page result used by chapter OCR. ML Kit's own blocks become regions, so this
     * path does not need the Japanese text detector and does not upload the image.
     */
    suspend fun recognizePage(image: Bitmap): List<OcrRegion> {
        val (working, recycleWorking) = prepareImage(image)
        return try {
            val result = recognizer.process(InputImage.fromBitmap(working, 0)).awaitTask()
            val width = working.width.coerceAtLeast(1)
            val height = working.height.coerceAtLeast(1)
            result.textBlocks.mapIndexedNotNull { index, block ->
                val lines = block.toLines()
                val text = ChineseTextPostprocessor.postprocess(ChineseOcrLayout.assemble(lines))
                if (text.isBlank()) {
                    return@mapIndexedNotNull null
                }
                val bounds = block.boundingBox
                    ?: return@mapIndexedNotNull null
                OcrRegion(
                    order = index,
                    text = text,
                    boundingBox = OcrBoundingBox(
                        left = bounds.left / width.toFloat(),
                        top = bounds.top / height.toFloat(),
                        right = bounds.right / width.toFloat(),
                        bottom = bounds.bottom / height.toFloat(),
                    ),
                    textOrientation = if (ChineseOcrLayout.isVertical(lines)) {
                        OcrTextOrientation.Vertical
                    } else {
                        OcrTextOrientation.Horizontal
                    },
                )
            }
        } finally {
            if (recycleWorking && !working.isRecycled) {
                working.recycle()
            }
        }
    }

    override fun close() {
        recognizer.close()
    }

    private fun prepareImage(image: Bitmap): Pair<Bitmap, Boolean> {
        require(!image.isRecycled) { "Input bitmap is recycled" }
        val software = if (image.config == Bitmap.Config.HARDWARE) {
            image.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            image
        }
        val longest = max(software.width, software.height)
        if (longest <= MAX_DIMENSION) {
            return software to (software !== image)
        }
        val scaleFactor = MAX_DIMENSION.toFloat() / longest.toFloat()
        val scaled = software.scale(
            width = (software.width * scaleFactor).toInt().coerceAtLeast(1),
            height = (software.height * scaleFactor).toInt().coerceAtLeast(1),
            filter = true,
        )
        if (software !== image && !software.isRecycled) {
            software.recycle()
        }
        return scaled to true
    }

    private fun Text.TextBlock.toLines(): List<ChineseOcrLine> {
        return lines.map { line ->
            val bounds = line.boundingBox
            ChineseOcrLine(
                text = line.text,
                left = bounds?.left?.toFloat() ?: 0f,
                top = bounds?.top?.toFloat() ?: 0f,
                right = bounds?.right?.toFloat() ?: 0f,
                bottom = bounds?.bottom?.toFloat() ?: 0f,
                angleDegrees = line.angle,
            )
        }
    }

    private companion object {
        const val MAX_DIMENSION = 4096
    }
}

internal suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { completed ->
        if (continuation.isCancelled) return@addOnCompleteListener
        val error = completed.exception
        when {
            error != null -> continuation.resumeWithException(error)
            completed.isCanceled -> continuation.cancel()
            else -> continuation.resume(completed.result)
        }
    }
}
