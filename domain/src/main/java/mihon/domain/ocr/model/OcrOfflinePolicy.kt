package mihon.domain.ocr.model

/**
 * Decides which OCR engine may run, and which engine may replace it after a failure.
 * The Chinese on-device engine never falls back to a network engine. Strict offline
 * mode blocks Google Lens and OwOCR even when another model is selected.
 */
object OcrOfflinePolicy {
    fun effectiveModel(selected: OcrModel, strictOffline: Boolean): OcrModel {
        if (strictOffline && selected.requiresNetwork) {
            return OcrModel.LEGACY
        }
        return selected
    }

    fun chapterScanRequiresNetwork(model: OcrModel, strictOffline: Boolean): Boolean {
        if (model == OcrModel.ZH_MLKIT || strictOffline) {
            return false
        }
        return true
    }

    /**
     * Local page scans normally redirect to Google Lens when text detection is unavailable.
     * That upload is refused for the Chinese engine and while strict offline is on.
     */
    fun mayRedirectPageScanToNetwork(
        model: OcrModel,
        useFallbackModels: Boolean,
        strictOffline: Boolean,
    ): Boolean {
        if (!useFallbackModels || strictOffline || model == OcrModel.ZH_MLKIT) {
            return false
        }
        return model == OcrModel.LEGACY || model == OcrModel.FAST || model == OcrModel.OWOCR
    }

    /**
     * Returns the engine to try after [primary] fails, or null when the failure must surface.
     * [OcrModel.ZH_MLKIT] never falls back, so a Chinese scan cannot reach Google Lens.
     */
    fun textFallback(
        primary: OcrModel,
        useFallbackModels: Boolean,
        strictOffline: Boolean,
    ): OcrModel? {
        if (!useFallbackModels || primary == OcrModel.ZH_MLKIT) {
            return null
        }

        val candidate = when (primary) {
            OcrModel.GLENS -> OcrModel.FAST
            OcrModel.FAST -> if (strictOffline) OcrModel.LEGACY else OcrModel.GLENS
            OcrModel.LEGACY -> if (strictOffline) OcrModel.FAST else OcrModel.GLENS
            OcrModel.OWOCR -> if (strictOffline) null else OcrModel.GLENS
            OcrModel.ZH_MLKIT -> null
        }
        if (candidate == null || candidate == primary) {
            return null
        }
        if (strictOffline && candidate.requiresNetwork) {
            return null
        }
        return candidate
    }
}
