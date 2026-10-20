package mihon.domain.ocr.model

import mihon.domain.dictionary.interactor.ParserLanguage
import mihon.domain.dictionary.interactor.preferringChineseForHanzi
import mihon.domain.ocr.service.OcrPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class OcrOfflinePolicyTest {
    @Test
    fun chineseEngineNeverReachesGoogleLens() {
        assertNull(
            OcrOfflinePolicy.textFallback(
                primary = OcrModel.ZH_MLKIT,
                useFallbackModels = true,
                strictOffline = false,
            ),
        )
        assertFalse(
            OcrOfflinePolicy.mayRedirectPageScanToNetwork(
                model = OcrModel.ZH_MLKIT,
                useFallbackModels = true,
                strictOffline = false,
            ),
        )
        assertFalse(
            OcrOfflinePolicy.chapterScanRequiresNetwork(
                model = OcrModel.ZH_MLKIT,
                strictOffline = false,
            ),
        )
    }

    @Test
    fun strictOfflineBlocksNetworkEnginesAndKeepsJapaneseLocalFallback() {
        assertEquals(OcrModel.LEGACY, OcrOfflinePolicy.effectiveModel(OcrModel.GLENS, strictOffline = true))
        assertEquals(OcrModel.LEGACY, OcrOfflinePolicy.effectiveModel(OcrModel.OWOCR, strictOffline = true))
        assertEquals(OcrModel.ZH_MLKIT, OcrOfflinePolicy.effectiveModel(OcrModel.ZH_MLKIT, strictOffline = true))
        assertEquals(
            OcrModel.FAST,
            OcrOfflinePolicy.textFallback(
                primary = OcrModel.LEGACY,
                useFallbackModels = true,
                strictOffline = true,
            ),
        )
        assertFalse(
            OcrOfflinePolicy.mayRedirectPageScanToNetwork(
                model = OcrModel.LEGACY,
                useFallbackModels = true,
                strictOffline = true,
            ),
        )
        assertFalse(
            OcrOfflinePolicy.mayRedirectPageScanToNetwork(
                model = OcrModel.OWOCR,
                useFallbackModels = true,
                strictOffline = true,
            ),
        )
        assertFalse(OcrOfflinePolicy.chapterScanRequiresNetwork(OcrModel.LEGACY, strictOffline = true))
        assertFalse(OcrOfflinePolicy.chapterScanRequiresNetwork(OcrModel.GLENS, strictOffline = true))
    }

    @Test
    fun japaneseFallbackIsUnchangedWhenStrictOfflineIsOff() {
        assertEquals(OcrModel.GLENS, OcrOfflinePolicy.effectiveModel(OcrModel.GLENS, strictOffline = false))
        assertEquals(
            OcrModel.GLENS,
            OcrOfflinePolicy.textFallback(
                primary = OcrModel.LEGACY,
                useFallbackModels = true,
                strictOffline = false,
            ),
        )
        assertEquals(
            OcrModel.FAST,
            OcrOfflinePolicy.textFallback(
                primary = OcrModel.GLENS,
                useFallbackModels = true,
                strictOffline = false,
            ),
        )
        assertNull(
            OcrOfflinePolicy.textFallback(
                primary = OcrModel.LEGACY,
                useFallbackModels = false,
                strictOffline = false,
            ),
        )
        assertTrue(
            OcrOfflinePolicy.mayRedirectPageScanToNetwork(
                model = OcrModel.FAST,
                useFallbackModels = true,
                strictOffline = false,
            ),
        )
        assertTrue(OcrOfflinePolicy.chapterScanRequiresNetwork(OcrModel.LEGACY, strictOffline = false))
        assertTrue(OcrOfflinePolicy.chapterScanRequiresNetwork(OcrModel.GLENS, strictOffline = false))
    }

    @Test
    fun strictOfflineDefaultsOn() {
        val preferences = OcrPreferences(InMemoryPreferenceStore())
        assertTrue(preferences.strictOffline().get())
        assertEquals(OcrModel.LEGACY, preferences.ocrModel().get())
    }

    @Test
    fun autoPrefersChineseOnlyForHanziWhileChineseEngineIsActive() {
        assertEquals(
            ParserLanguage.CHINESE,
            ParserLanguage.AUTO.preferringChineseForHanzi("你好世界", chineseOcrActive = true),
        )
        assertEquals(
            ParserLanguage.CHINESE,
            ParserLanguage.AUTO.preferringChineseForHanzi("你 好！", chineseOcrActive = true),
        )
        assertEquals(
            ParserLanguage.AUTO,
            ParserLanguage.AUTO.preferringChineseForHanzi("食べる", chineseOcrActive = true),
        )
        assertEquals(
            ParserLanguage.AUTO,
            ParserLanguage.AUTO.preferringChineseForHanzi("こんにちは", chineseOcrActive = true),
        )
        assertEquals(
            ParserLanguage.AUTO,
            ParserLanguage.AUTO.preferringChineseForHanzi("안녕", chineseOcrActive = true),
        )
        assertEquals(
            ParserLanguage.AUTO,
            ParserLanguage.AUTO.preferringChineseForHanzi("你好", chineseOcrActive = false),
        )
        assertEquals(
            ParserLanguage.JAPANESE,
            ParserLanguage.JAPANESE.preferringChineseForHanzi("你好", chineseOcrActive = true),
        )
    }
}
