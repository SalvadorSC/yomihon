package mihon.data.dictionary.zh

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import mihon.domain.dictionary.zh.ChineseTranslationPolicy
import mihon.domain.dictionary.zh.OfflineChineseTranslator
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit Chinese-to-English. The language model is not in the APK.
 * [translateIfReady] only runs when both models are already stored, and it
 * never calls [downloadModel]. The settings button is what downloads them.
 */
class MlKitOfflineChineseTranslator : OfflineChineseTranslator {
    private val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.CHINESE)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build(),
    )
    private val modelManager = RemoteModelManager.getInstance()
    private val chineseModel = TranslateRemoteModel.Builder(TranslateLanguage.CHINESE).build()
    private val englishModel = TranslateRemoteModel.Builder(TranslateLanguage.ENGLISH).build()

    override suspend fun isModelDownloaded(): Boolean {
        return try {
            modelManager.isModelDownloaded(chineseModel).await() &&
                modelManager.isModelDownloaded(englishModel).await()
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun translateIfReady(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || !isModelDownloaded()) return null
        return try {
            translator.translate(trimmed).await().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun downloadModel(): Result<Unit> {
        if (!ChineseTranslationPolicy.mayContactNetwork(explicitUserDownload = true)) {
            return Result.failure(IllegalStateException("translation download is not allowed"))
        }
        return try {
            translator.downloadModelIfNeeded(
                DownloadConditions.Builder().build(),
            ).awaitUnit()
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value ->
        if (continuation.isActive) continuation.resume(value)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
    addOnCanceledListener {
        continuation.cancel()
    }
}

private suspend fun Task<Void>.awaitUnit() = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener {
        if (continuation.isActive) continuation.resume(Unit)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
    addOnCanceledListener {
        continuation.cancel()
    }
}
