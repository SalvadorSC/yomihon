package mihon.data.dictionary.zh

import android.content.Context
import mihon.domain.dictionary.zh.ChineseLexicon
import mihon.domain.dictionary.zh.MapChineseLexicon
import mihon.domain.dictionary.zh.parseCedictPrimaryLines

/**
 * Loads the bundled CC-CEDICT primary-reading list. The file is CC-BY-SA 4.0
 * data shipped beside the Apache-2.0 code; it is not a network download.
 */
class AssetChineseLexicon(
    context: Context,
) : ChineseLexicon {
    private val appContext = context.applicationContext
    private val lock = Any()
    private var loaded: MapChineseLexicon? = null

    init {
        Thread({ lexicon() }, "cedict-lexicon").apply { isDaemon = true }.start()
    }

    override val maxWordLength: Int
        get() = lexicon().maxWordLength

    override fun readingOf(word: String): String? = lexicon().readingOf(word)

    private fun lexicon(): MapChineseLexicon {
        loaded?.let { return it }
        synchronized(lock) {
            loaded?.let { return it }
            val parsed = appContext.assets.open(ASSET).bufferedReader().use { reader ->
                parseCedictPrimaryLines(reader.lineSequence())
            }
            loaded = parsed
            return parsed
        }
    }

    private companion object {
        const val ASSET = "zh/cedict_primary.tsv"
    }
}
