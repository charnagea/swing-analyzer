package dev.swinganalyzer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.swinganalyzer.core.Note
import dev.swinganalyzer.ui.AnalyzingScreen
import dev.swinganalyzer.ui.ErrorScreen
import dev.swinganalyzer.ui.HomeScreen
import dev.swinganalyzer.ui.NoteDetailScreen
import dev.swinganalyzer.ui.ResultScreen
import dev.swinganalyzer.ui.SettingsScreen
import dev.swinganalyzer.ui.SetupScreen
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF2E6B3A), secondary = Color(0xFF4F6354))) {
                App(vm)
            }
        }
    }
}

@Composable
fun App(vm: AppViewModel) {
    val screen by vm.screen.collectAsState()
    val settings by vm.settings.collectAsState()
    val notes by vm.notes.collectAsState()
    val context = LocalContext.current

    // Export: the system "save as" dialog writes the text wherever the user chooses. No permission needed.
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = pendingExport
        if (uri != null && text != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
        pendingExport = null
    }
    fun export(fileName: String, text: String) {
        pendingExport = text
        exportLauncher.launch(fileName)
    }
    fun share(subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Share swing note"))
    }
    fun exportNote(n: Note) = export(n.fileName(), n.exportText())

    // The system photo picker: no storage permission needed.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.onVideoPicked(uri)
    }

    BackHandler(enabled = screen !is Screen.Home) {
        if (screen is Screen.Analyzing) vm.cancel() else vm.go(Screen.Home)
    }

    when (val s = screen) {
        is Screen.Home -> HomeScreen(
            notes = notes,
            onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
            onOpenNote = { vm.go(Screen.NoteDetail(it)) },
            onSettings = { vm.go(Screen.Settings) },
            onExportAll = { list -> export("swing-notes_${LocalDate.now()}.txt", Note.exportAll(list)) },
        )
        is Screen.Setup -> SetupScreen(
            info = s.info,
            settings = settings,
            knowledge = vm.knowledge,
            onAnalyze = { club, shot, view, hand, slow -> vm.analyze(s.uri, s.info, club, shot, view, hand, slow) },
            onBack = { vm.go(Screen.Home) },
        )
        is Screen.Analyzing -> AnalyzingScreen(s.step, s.progress, onCancel = { vm.cancel() })
        is Screen.Result -> ResultScreen(
            analysis = s.analysis,
            keyFrameDir = vm.storage.analysisDir(s.analysis.id),
            savedNote = s.savedNote,
            onSave = { comment, ref -> vm.saveNote(s.analysis, comment, ref) },
            onExport = { comment -> exportNote(s.savedNote ?: vm.previewNote(s.analysis, comment)) },
            onShare = { comment ->
                val n = s.savedNote ?: vm.previewNote(s.analysis, comment)
                share("Swing note: ${n.clubName}", n.exportText())
            },
            onDone = { vm.go(Screen.Home) },
        )
        is Screen.NoteDetail -> NoteDetailScreen(
            note = s.note,
            keyFrameDir = s.note.analysisId?.let { vm.storage.analysisDir(it) },
            keyFrameLabels = remember(s.note.analysisId) {
                s.note.analysisId?.let { vm.storage.loadAnalysis(it)?.keyFrames?.map { k -> k.label } }.orEmpty()
            },
            onExport = { exportNote(s.note) },
            onShare = { share("Swing note: ${s.note.clubName}", s.note.exportText()) },
            onDelete = { vm.deleteNote(s.note) },
            onReference = { on -> vm.setReference(s.note, on) },
            onComment = { c -> vm.updateComment(s.note, c) },
            onBack = { vm.go(Screen.Home) },
        )
        is Screen.Settings -> SettingsScreen(
            settings = settings,
            knowledge = vm.knowledge,
            onSave = { vm.updateSettings(it); vm.go(Screen.Home) },
            onBack = { vm.go(Screen.Home) },
        )
        is Screen.Error -> ErrorScreen(s.message, onBack = { vm.go(Screen.Home) })
    }
}
