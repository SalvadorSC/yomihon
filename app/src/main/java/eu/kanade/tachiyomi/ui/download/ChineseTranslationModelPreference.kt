package eu.kanade.tachiyomi.ui.download

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import eu.kanade.presentation.more.settings.widget.InfoWidget
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import kotlinx.coroutines.launch
import mihon.domain.dictionary.zh.OfflineChineseTranslator
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * One-time download of the zh-en model. Tapping a bubble never calls this.
 */
@Composable
fun ChineseTranslationModelPreference() {
    val translator = remember { Injekt.get<OfflineChineseTranslator>() }
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf<Boolean?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(translator) {
        ready = translator.isModelDownloaded()
    }

    val subtitle = when {
        downloading -> stringResource(MR.strings.pref_zh_translation_model_downloading)
        failed -> stringResource(MR.strings.pref_zh_translation_model_failed)
        ready == true -> stringResource(MR.strings.pref_zh_translation_model_ready)
        else -> stringResource(MR.strings.pref_zh_translation_model_missing)
    }

    TextPreferenceWidget(
        title = stringResource(
            if (ready == true) {
                MR.strings.pref_zh_translation_model
            } else {
                MR.strings.pref_zh_translation_model_action
            },
        ),
        subtitle = subtitle,
        onPreferenceClick = {
            if (downloading || ready == true) return@TextPreferenceWidget
            downloading = true
            failed = false
            scope.launch {
                val result = translator.downloadModel()
                downloading = false
                if (result.isSuccess) {
                    ready = translator.isModelDownloaded()
                    failed = ready != true
                } else {
                    failed = true
                }
            }
        },
    )
    InfoWidget(text = stringResource(MR.strings.pref_zh_translation_model_note))
}
