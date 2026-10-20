package mihon.data.ocr.zh

import android.graphics.Bitmap
import mihon.data.ocr.DetOcrEngine
import mihon.domain.ocr.model.OcrBoundingBox

/**
 * Text-region detector for the Chinese engine.
 *
 * Regions come from the same ML Kit Chinese pass as recognition. Closing this
 * wrapper does not close the shared recognizer.
 */
internal class MlKitChineseDetEngine(
    private val engine: MlKitChineseOcrEngine,
) : DetOcrEngine {
    override suspend fun detectTextRegions(image: Bitmap): List<OcrBoundingBox> {
        return engine.recognizePage(image).map { it.boundingBox }
    }

    override fun close() = Unit
}
