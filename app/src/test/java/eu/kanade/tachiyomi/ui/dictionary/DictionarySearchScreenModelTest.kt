package eu.kanade.tachiyomi.ui.dictionary

import eu.kanade.domain.dictionary.DictionaryPreferences
import eu.kanade.presentation.dictionary.components.DictionaryCardAudioState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mihon.domain.ankidroid.interactor.AddDictionaryCard
import mihon.domain.ankidroid.interactor.FindExistingAnkiNotes
import mihon.domain.ankidroid.repository.AnkiDroidRepository
import mihon.domain.dictionary.audio.DictionaryAudio
import mihon.domain.dictionary.audio.DictionaryAudioPlayer
import mihon.domain.dictionary.audio.DictionaryAudioRepository
import mihon.domain.dictionary.audio.DictionaryAudioResult
import mihon.domain.dictionary.interactor.DictionaryInteractor
import mihon.domain.dictionary.interactor.ParserLanguage
import mihon.domain.dictionary.interactor.SearchDictionaryTerms
import mihon.domain.dictionary.model.Dictionary
import mihon.domain.dictionary.model.DictionaryTerm
import mihon.domain.dictionary.model.DictionaryTermCard
import mihon.domain.dictionary.zh.ChineseSentenceSegmenter
import mihon.domain.dictionary.zh.MapChineseLexicon
import mihon.domain.dictionary.zh.OfflineChineseTranslator
import mihon.domain.ocr.model.OcrModel
import mihon.domain.ocr.service.OcrPreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.ankidroid.service.AnkiDroidPreferences
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DictionarySearchScreenModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fetchAndPlayAudio exposes loading then ready and reuses cache`() = runTest(dispatcher) {
        val repository = mockk<DictionaryAudioRepository>()
        val player = mockk<DictionaryAudioPlayer>(relaxed = true)
        val gate = CompletableDeferred<Unit>()
        val audio = createAudioFile()
        coEvery { repository.fetchAudio(any(), any()) } coAnswers {
            gate.await()
            DictionaryAudioResult.Success(audio)
        }

        val model = createModel(
            dictionaryAudioRepository = repository,
            dictionaryAudioPlayer = player,
        )
        val terms = listOf(sampleTerm())

        model.fetchAndPlayAudio(terms)
        runCurrent()

        assertEquals(
            DictionaryCardAudioState.Loading,
            model.state.value.audioStates["日本語|にほんご"],
        )

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            DictionaryCardAudioState.Ready,
            model.state.value.audioStates["日本語|にほんご"],
        )
        coVerify(exactly = 1) { repository.fetchAudio("日本語", "にほんご") }
        every { player.play(any()) } returns Unit

        model.fetchAndPlayAudio(terms)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.fetchAudio("日本語", "にほんご") }
        io.mockk.verify(exactly = 2) { player.play(any()) }
    }

    @Test
    fun `addGroupToAnki fetches audio when prefill enabled`() = runTest(dispatcher) {
        val repository = mockk<DictionaryAudioRepository>()
        val player = mockk<DictionaryAudioPlayer>(relaxed = true)
        val ankiRepo = mockk<AnkiDroidRepository>()
        var capturedCard: DictionaryTermCard? = null
        val audio = createAudioFile()
        coEvery { repository.fetchAudio(any(), any()) } returns DictionaryAudioResult.Success(audio)
        coEvery { ankiRepo.findExistingNotes(any()) } returns emptySet()
        coEvery { ankiRepo.addCard(any()) } coAnswers {
            capturedCard = firstArg()
            AnkiDroidRepository.Result.Added
        }

        val model = createModel(
            ankiRepository = ankiRepo,
            dictionaryAudioRepository = repository,
            dictionaryAudioPlayer = player,
        )

        model.addGroupToAnki(listOf(sampleTerm()))
        advanceUntilIdle()

        assertEquals(audio.file.absolutePath, capturedCard?.audio)
        coVerify(exactly = 1) { repository.fetchAudio("日本語", "にほんご") }
    }

    @Test
    fun `addGroupToAnki skips audio when prefill disabled`() = runTest(dispatcher) {
        val repository = mockk<DictionaryAudioRepository>(relaxed = true)
        val player = mockk<DictionaryAudioPlayer>(relaxed = true)
        val ankiRepo = mockk<AnkiDroidRepository>()
        var capturedCard: DictionaryTermCard? = null
        coEvery { ankiRepo.findExistingNotes(any()) } returns emptySet()
        coEvery { ankiRepo.addCard(any()) } coAnswers {
            capturedCard = firstArg()
            AnkiDroidRepository.Result.Added
        }

        val prefsStore = InMemoryPreferenceStore().apply {
            getBoolean("pref_anki_dictionary_audio_prefill", true).set(false)
        }
        val model = createModel(
            ankiRepository = ankiRepo,
            dictionaryAudioRepository = repository,
            dictionaryAudioPlayer = player,
            ankiPreferences = AnkiDroidPreferences(prefsStore),
        )

        model.addGroupToAnki(listOf(sampleTerm()))
        advanceUntilIdle()

        assertEquals("", capturedCard?.audio)
    }

    @Test
    fun `addGroupToAnki still exports when audio fetch fails`() = runTest(dispatcher) {
        val repository = mockk<DictionaryAudioRepository>()
        val player = mockk<DictionaryAudioPlayer>(relaxed = true)
        val ankiRepo = mockk<AnkiDroidRepository>()
        var capturedCard: DictionaryTermCard? = null
        coEvery { repository.fetchAudio(any(), any()) } returns DictionaryAudioResult.NotFound
        coEvery { ankiRepo.findExistingNotes(any()) } returns emptySet()
        coEvery { ankiRepo.addCard(any()) } coAnswers {
            capturedCard = firstArg()
            AnkiDroidRepository.Result.Added
        }

        val model = createModel(
            ankiRepository = ankiRepo,
            dictionaryAudioRepository = repository,
            dictionaryAudioPlayer = player,
        )

        model.addGroupToAnki(listOf(sampleTerm()))
        advanceUntilIdle()

        assertEquals("", capturedCard?.audio)
        assertTrue(model.state.value.existingTermExpressions.contains("日本語"))
    }

    @Test
    fun `auto parser prefers Chinese for hanzi while the Chinese engine is selected`() = runTest(dispatcher) {
        val search = mockk<SearchDictionaryTerms>()
        coEvery { search.findFirstWordMatch(any(), any(), any()) } returns SearchDictionaryTerms.FirstWordMatch(
            word = "你好",
            sourceOffset = 0,
            sourceLength = 2,
            isDictionaryMatch = true,
        )
        coEvery { search.search(any(), any(), any()) } returns emptyList()
        coEvery { search.getTermMeta(any(), any()) } returns emptyMap()

        val dictionaryInteractor = mockk<DictionaryInteractor>()
        coEvery { dictionaryInteractor.getAllDictionaries() } returns listOf(
            Dictionary(
                id = 7L,
                title = "CC-CEDICT",
                revision = "1",
                version = 1,
                sourceLanguage = null,
            ),
        )
        val preferenceStore = SettablePreferenceStore()
        OcrPreferences(preferenceStore).ocrModel().set(OcrModel.ZH_MLKIT)
        val model = createModel(
            searchDictionaryTerms = search,
            dictionaryInteractor = dictionaryInteractor,
            ocrPreferences = OcrPreferences(preferenceStore),
        )
        advanceUntilIdle()

        model.search("你好世界")
        advanceUntilIdle()

        coVerify {
            search.findFirstWordMatch("你好世界", listOf(7L), ParserLanguage.CHINESE)
            search.search("你好", listOf(7L), ParserLanguage.CHINESE)
        }
    }

    @Test
    fun `auto parser stays automatic for kana and for the Japanese engines`() = runTest(dispatcher) {
        val search = mockk<SearchDictionaryTerms>()
        coEvery { search.findFirstWordMatch(any(), any(), any()) } returns SearchDictionaryTerms.FirstWordMatch(
            word = "食",
            sourceOffset = 0,
            sourceLength = 1,
        )
        coEvery { search.search(any(), any(), any()) } returns emptyList()
        coEvery { search.getTermMeta(any(), any()) } returns emptyMap()
        val dictionaryInteractor = mockk<DictionaryInteractor>()
        coEvery { dictionaryInteractor.getAllDictionaries() } returns listOf(
            Dictionary(id = 7L, title = "CC-CEDICT", revision = "1", version = 1),
        )
        val chineseEngine = SettablePreferenceStore().also {
            OcrPreferences(it).ocrModel().set(OcrModel.ZH_MLKIT)
        }
        val model = createModel(
            searchDictionaryTerms = search,
            dictionaryInteractor = dictionaryInteractor,
            ocrPreferences = OcrPreferences(chineseEngine),
        )
        advanceUntilIdle()

        model.search("食べる")
        advanceUntilIdle()
        coVerify { search.findFirstWordMatch("食べる", listOf(7L), ParserLanguage.AUTO) }

        val japaneseEngine = SettablePreferenceStore()
        val japaneseModel = createModel(
            searchDictionaryTerms = search,
            dictionaryInteractor = dictionaryInteractor,
            ocrPreferences = OcrPreferences(japaneseEngine),
        )
        advanceUntilIdle()
        japaneseModel.search("你好")
        advanceUntilIdle()
        coVerify { search.findFirstWordMatch("你好", listOf(7L), ParserLanguage.AUTO) }
    }

    private fun createModel(
        ankiRepository: AnkiDroidRepository = mockk {
            coEvery { findExistingNotes(any()) } returns emptySet()
            coEvery { addCard(any()) } returns AnkiDroidRepository.Result.Added
        },
        dictionaryAudioRepository: DictionaryAudioRepository = mockk(relaxed = true),
        dictionaryAudioPlayer: DictionaryAudioPlayer = mockk(relaxed = true),
        ankiPreferences: AnkiDroidPreferences = AnkiDroidPreferences(InMemoryPreferenceStore()),
        searchDictionaryTerms: SearchDictionaryTerms = mockk(relaxed = true),
        dictionaryInteractor: DictionaryInteractor = mockk<DictionaryInteractor>().also {
            coEvery { it.getAllDictionaries() } returns emptyList()
        },
        ocrPreferences: OcrPreferences = OcrPreferences(InMemoryPreferenceStore()),
        chineseSegmenter: ChineseSentenceSegmenter = ChineseSentenceSegmenter(MapChineseLexicon(emptyMap())),
        offlineChineseTranslator: OfflineChineseTranslator = OfflineChineseTranslator.Unavailable,
    ): DictionarySearchScreenModel {
        return DictionarySearchScreenModel(
            searchDictionaryTerms = searchDictionaryTerms,
            dictionaryInteractor = dictionaryInteractor,
            addDictionaryCard = AddDictionaryCard(ankiRepository),
            findExistingAnkiNotes = FindExistingAnkiNotes(ankiRepository),
            dictionaryPreferences = DictionaryPreferences(InMemoryPreferenceStore()),
            ankiDroidPreferences = ankiPreferences,
            dictionaryAudioRepository = dictionaryAudioRepository,
            dictionaryAudioPlayer = dictionaryAudioPlayer,
            ocrPreferences = ocrPreferences,
            chineseSegmenter = chineseSegmenter,
            offlineChineseTranslator = offlineChineseTranslator,
        )
    }

    @Test
    fun `chinese sentence search lists words in order and hides translation without a model`() = runTest(dispatcher) {
        val search = mockk<SearchDictionaryTerms>()
        coEvery { search.search(any(), any(), any()) } coAnswers {
            val word = firstArg<String>()
            if (word == "好") {
                listOf(
                    sampleTerm(expression = "好", reading = "hào"),
                    sampleTerm(expression = "好", reading = "hǎo"),
                )
            } else {
                listOf(sampleTerm(expression = word, reading = "x"))
            }
        }
        coEvery { search.getTermMeta(any(), any()) } returns emptyMap()
        val dictionaryInteractor = mockk<DictionaryInteractor>()
        coEvery { dictionaryInteractor.getAllDictionaries() } returns listOf(
            Dictionary(id = 7L, title = "CC-CEDICT", revision = "1", version = 1),
        )
        val model = createModel(
            searchDictionaryTerms = search,
            dictionaryInteractor = dictionaryInteractor,
            chineseSegmenter = ChineseSentenceSegmenter(
                MapChineseLexicon(
                    mapOf(
                        "今天" to "jīntiān",
                        "天气" to "tiānqì",
                        "天天" to "tiāntiān",
                        "真" to "zhēn",
                        "好" to "hǎo",
                    ),
                ),
            ),
        )
        advanceUntilIdle()

        model.searchChineseSentence("今天天气真好")
        advanceUntilIdle()

        val results = model.state.value.results
        assertEquals(listOf("今天", "天气", "真", "好", "好"), results?.items?.map { it.expression })
        assertEquals(listOf("hǎo", "hào"), results?.items?.filter { it.expression == "好" }?.map { it.reading })
        assertEquals("jīntiān tiānqì zhēn hǎo", results?.sentence?.pinyin)
        assertEquals(null, results?.sentence?.translation)
        coVerify(exactly = 0) { search.findFirstWordMatch(any(), any(), any()) }
    }

    @Test
    fun `chinese sentence search shows a translation when the model is already downloaded`() = runTest(dispatcher) {
        val search = mockk<SearchDictionaryTerms>(relaxed = true)
        coEvery { search.search(any(), any(), any()) } returns emptyList()
        coEvery { search.getTermMeta(any(), any()) } returns emptyMap()
        val dictionaryInteractor = mockk<DictionaryInteractor>()
        coEvery { dictionaryInteractor.getAllDictionaries() } returns listOf(
            Dictionary(id = 7L, title = "CC-CEDICT", revision = "1", version = 1),
        )
        val translator = object : OfflineChineseTranslator {
            override suspend fun isModelDownloaded(): Boolean = true

            override suspend fun translateIfReady(text: String): String = "The weather is nice today."

            override suspend fun downloadModel(): Result<Unit> = Result.success(Unit)
        }
        val model = createModel(
            searchDictionaryTerms = search,
            dictionaryInteractor = dictionaryInteractor,
            chineseSegmenter = ChineseSentenceSegmenter(MapChineseLexicon(mapOf("好" to "hǎo"))),
            offlineChineseTranslator = translator,
        )
        advanceUntilIdle()

        model.searchChineseSentence("好")
        advanceUntilIdle()

        assertEquals("The weather is nice today.", model.state.value.results?.sentence?.translation)
        assertEquals("hǎo", model.state.value.results?.sentence?.pinyin)
    }

    private fun sampleTerm(
        expression: String = "日本語",
        reading: String = "にほんご",
    ): DictionaryTerm {
        return DictionaryTerm(
            id = 1L,
            dictionaryId = 1L,
            expression = expression,
            reading = reading,
            definitionTags = null,
            rules = null,
            score = 0,
            glossary = emptyList(),
        )
    }

    private class SettablePreferenceStore : PreferenceStore by InMemoryPreferenceStore() {
        private val stored = mutableMapOf<String, String>()

        override fun <T> getObjectFromString(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> {
            return object : Preference<T> {
                override fun key(): String = key

                override fun get(): T {
                    return stored[key]?.let(deserializer) ?: defaultValue
                }

                override fun set(value: T) {
                    stored[key] = serializer(value)
                }

                override fun isSet(): Boolean = key in stored

                override fun delete() {
                    stored.remove(key)
                }

                override fun defaultValue(): T = defaultValue

                override fun changes(): Flow<T> = flow { emit(get()) }

                override fun stateIn(scope: CoroutineScope) = MutableStateFlow(get())
            }
        }
    }

    private fun createAudioFile(): DictionaryAudio {
        val file = File.createTempFile("dictionary-audio", ".mp3").apply {
            writeText("audio")
            deleteOnExit()
        }
        return DictionaryAudio(
            file = file,
            mediaType = "audio/mpeg",
            source = mihon.domain.dictionary.audio.DictionaryAudioSource.JPOD101,
        )
    }
}
