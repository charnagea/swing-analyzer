package dev.swinganalyzer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.swinganalyzer.core.AnalysisResult
import dev.swinganalyzer.core.CameraView
import dev.swinganalyzer.core.Handedness
import dev.swinganalyzer.core.Knowledge
import dev.swinganalyzer.core.Note
import dev.swinganalyzer.core.ShotType
import dev.swinganalyzer.core.SwingNotFound
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.Executors

sealed interface Screen {
    data object Home : Screen
    data class Setup(val uri: Uri, val info: ClipInfo) : Screen
    data class Analyzing(val step: String, val progress: Float) : Screen
    data class Result(val analysis: AnalysisResult, val savedNote: Note?) : Screen
    data class NoteDetail(val note: Note) : Screen
    data object Settings : Screen
    data class Error(val message: String) : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val knowledge: Knowledge = Knowledge.loadBundled()
    val storage = Storage(app, knowledge.clubs.map { it.id })
    private val analyzer = VideoAnalyzer(app)

    // One dedicated thread for analysis: MediaPipe's GPU delegate must stay on the thread that created it.
    private val analysisThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var job: Job? = null
    @Volatile private var cancelled = false

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _settings = MutableStateFlow(storage.loadSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _notes = MutableStateFlow(storage.notes())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    init {
        viewModelScope.launch(analysisThread) { storage.cleanUp() }
    }

    fun go(s: Screen) { _screen.value = s }

    fun onVideoPicked(uri: Uri) {
        viewModelScope.launch {
            val info = withContext(analysisThread) { runCatching { analyzer.readInfo(uri) } }
            info.onSuccess { _screen.value = Screen.Setup(uri, it) }
                .onFailure { _screen.value = Screen.Error(it.message ?: "Couldn't read this video.") }
        }
    }

    fun analyze(uri: Uri, info: ClipInfo, clubId: String, shot: ShotType, view: CameraView, handedness: Handedness, slowMotion: Double) {
        val s = _settings.value
        updateSettings(s.copy(lastClubId = clubId, view = view, handedness = handedness))
        cancelled = false
        _screen.value = Screen.Analyzing("Checking the clip", 0f)
        job = viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val result = withContext(analysisThread) {
                runCatching {
                    val opts = VideoAnalyzer.Options(clubId, shot, view, handedness, slowMotion, s.heightCm)
                    analyzer.analyze(uri, info, opts, knowledge, id, storage.analysisDir(id),
                        progress = { step, p -> _screen.value = Screen.Analyzing(step, p) },
                        isCancelled = { cancelled })
                        .also { storage.saveAnalysis(it) }
                }
            }
            result.onSuccess { _screen.value = Screen.Result(it, null) }
                .onFailure { e ->
                    _screen.value = when (e) {
                        is AnalysisCancelled -> Screen.Home
                        is SwingNotFound -> Screen.Error("${e.message} Make sure the whole swing is in the clip and you picked the right shot type.")
                        is IllegalArgumentException -> Screen.Error(e.message ?: "The analysis couldn't run on this clip.")
                        else -> Screen.Error("The analysis failed: ${e.message ?: e.javaClass.simpleName}")
                    }
                }
        }
    }

    fun cancel() {
        cancelled = true
    }

    fun saveNote(a: AnalysisResult, comment: String, reference: Boolean) {
        val note = Note.fromAnalysis(UUID.randomUUID().toString(), System.currentTimeMillis(), a, comment.trim(), false)
        storage.saveNote(note)
        if (reference) storage.setReference(note, true)
        refreshNotes()
        _screen.value = Screen.Result(a, storage.notes().first { it.id == note.id })
    }

    fun updateComment(n: Note, comment: String) {
        storage.saveNote(n.copy(comment = comment.trim()))
        refreshNotes()
    }

    fun setReference(n: Note, on: Boolean) {
        storage.setReference(n, on)
        refreshNotes()
        _screen.value = Screen.NoteDetail(_notes.value.first { it.id == n.id })
    }

    fun deleteNote(n: Note) {
        storage.deleteNote(n)
        refreshNotes()
        _screen.value = Screen.Home
    }

    fun updateSettings(s: Settings) {
        storage.saveSettings(s)
        _settings.value = s
    }

    /** Text for exporting an analysis that hasn't been saved as a note yet. */
    fun previewNote(a: AnalysisResult, comment: String) =
        Note.fromAnalysis("preview", System.currentTimeMillis(), a, comment.trim(), false)

    private fun refreshNotes() { _notes.value = storage.notes() }

    override fun onCleared() {
        cancelled = true
        analysisThread.close()
    }
}
