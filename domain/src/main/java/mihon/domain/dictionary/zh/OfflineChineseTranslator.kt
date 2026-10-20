package mihon.domain.dictionary.zh

/**
 * Chinese-to-English translation that is allowed to touch the network only
 * from an explicit download. Reading a page calls [translateIfReady], which
 * must not fetch a model.
 */
interface OfflineChineseTranslator {
    suspend fun isModelDownloaded(): Boolean

    suspend fun translateIfReady(text: String): String?

    suspend fun downloadModel(): Result<Unit>

    object Unavailable : OfflineChineseTranslator {
        override suspend fun isModelDownloaded(): Boolean = false

        override suspend fun translateIfReady(text: String): String? = null

        override suspend fun downloadModel(): Result<Unit> {
            return Result.failure(IllegalStateException("Chinese translation is unavailable"))
        }
    }
}

object ChineseTranslationPolicy {
    /** A popup translation is shown only when the model is already on the device. */
    fun forPopup(modelOnDevice: Boolean, translated: String?): String? {
        if (!modelOnDevice) return null
        return translated?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Read time passes false. The settings button is the only caller that passes true. */
    fun mayContactNetwork(explicitUserDownload: Boolean): Boolean = explicitUserDownload
}
