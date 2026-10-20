package mihon.domain.ocr.model

/**
 * Represents the available OCR models.
 */
enum class OcrModel {
    /**
     * Legacy and slower model, supports GPU/CPU.
     */
    LEGACY,

    /**
     * Faster model designed for ARM CPU.
     */
    FAST,

    /**
     * On-device Simplified Chinese recognition. The model is bundled in the APK
     * and does not download weights or upload images.
     */
    ZH_MLKIT,

    /**
     * Online Google Lens OCR model.
     */
    GLENS,

    /**
     * Self-hosted OwOCR model.
     */
    OWOCR,
}

/** Google Lens and self-hosted OwOCR both send the image off the device. */
val OcrModel.requiresNetwork: Boolean
    get() = this == OcrModel.GLENS || this == OcrModel.OWOCR

/** Pure-Hanzi dictionary lookup should prefer the Chinese parser while this engine is active. */
val OcrModel.prefersChineseScript: Boolean
    get() = this == OcrModel.ZH_MLKIT
