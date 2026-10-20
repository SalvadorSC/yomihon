package eu.kanade.tachiyomi.ui.reader.viewer

import eu.kanade.tachiyomi.data.ocr.decodeImageDecoderBitmap
import eu.kanade.tachiyomi.util.ocr.toOcrImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.domain.ocr.interactor.ScanPageOcr
import mihon.domain.ocr.model.OcrModel
import mihon.domain.ocr.model.OcrOfflinePolicy
import mihon.domain.ocr.model.OcrPageResult
import mihon.domain.ocr.repository.OcrRepository
import mihon.domain.ocr.service.OcrPreferences
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import java.io.InputStream

/**
 * Detects Chinese text on a reader page after it is ready.
 *
 * One scan runs at a time so a fast webtoon fling does not decode every strip at once.
 * Pages still waiting are cancelled when the holder is recycled. The scan that already
 * holds the permit finishes and is cached, but its result is applied only if this holder
 * is still showing that page.
 */
internal class ReaderChinesePageScanner(
    private val scope: CoroutineScope,
    private val ocrPreferences: OcrPreferences,
    private val ocrRepository: OcrRepository,
    private val scanPageOcr: ScanPageOcr,
) {
    private var job: Job? = null
    private var generation = 0

    fun cancel() {
        generation++
        job?.cancel()
        job = null
    }

    fun onPageDisplayed(
        chapterId: Long?,
        pageIndex: Int,
        isAnimated: Boolean,
        openStream: () -> InputStream?,
        onResult: (OcrPageResult?) -> Unit,
    ) {
        cancel()
        val token = generation
        if (chapterId == null) {
            onResult(null)
            return
        }

        job = scope.launchIO {
            val cached = try {
                ocrRepository.getCachedPage(chapterId, pageIndex)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.WARN, e) {
                    "Chinese page cache read failed chapterId=$chapterId pageIndex=$pageIndex"
                }
                null
            }
            if (token != generation) return@launchIO

            val effective = OcrOfflinePolicy.effectiveModel(
                selected = ocrPreferences.ocrModel().get(),
                strictOffline = ocrPreferences.strictOffline().get(),
            )
            if (!OcrOfflinePolicy.shouldDetectPageInReader(effective, cached?.ocrModel)) {
                deliver(token, cached, onResult)
                return@launchIO
            }
            if (isAnimated) {
                deliver(token, null, onResult)
                return@launchIO
            }

            deliver(token, null, onResult)
            scanGate.acquire()
            try {
                if (token != generation) return@launchIO
                ensureActive()
                if (currentEffectiveModel() != OcrModel.ZH_MLKIT) return@launchIO

                val bitmap = try {
                    openStream()?.use { decodeImageDecoderBitmap(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    logcat(LogPriority.WARN, e) {
                        "Chinese page decode failed chapterId=$chapterId pageIndex=$pageIndex"
                    }
                    null
                }
                if (bitmap == null) return@launchIO
                try {
                    if (token != generation) return@launchIO
                    val image = bitmap.toOcrImage()
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                    val result = withContext(NonCancellable) {
                        scanPageOcr.await(
                            chapterId = chapterId,
                            pageIndex = pageIndex,
                            image = image,
                        )
                    }
                    if (result.ocrModel == OcrModel.ZH_MLKIT) {
                        deliver(token, result, onResult)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    logcat(LogPriority.WARN, e) {
                        "Chinese page detection failed chapterId=$chapterId pageIndex=$pageIndex"
                    }
                } finally {
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            } finally {
                scanGate.release()
            }
        }
    }

    private fun currentEffectiveModel(): OcrModel {
        return OcrOfflinePolicy.effectiveModel(
            selected = ocrPreferences.ocrModel().get(),
            strictOffline = ocrPreferences.strictOffline().get(),
        )
    }

    private suspend fun deliver(
        token: Int,
        result: OcrPageResult?,
        onResult: (OcrPageResult?) -> Unit,
    ) {
        withUIContext {
            if (token == generation) {
                onResult(result)
            }
        }
    }

    private companion object {
        val scanGate = Semaphore(1)
    }
}
