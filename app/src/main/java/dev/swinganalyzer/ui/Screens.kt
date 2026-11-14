@file:OptIn(ExperimentalMaterial3Api::class)

package dev.swinganalyzer.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.swinganalyzer.ClipInfo
import dev.swinganalyzer.Settings
import dev.swinganalyzer.core.AnalysisResult
import dev.swinganalyzer.core.CameraView
import dev.swinganalyzer.core.Club
import dev.swinganalyzer.core.Handedness
import dev.swinganalyzer.core.Knowledge
import dev.swinganalyzer.core.Note
import dev.swinganalyzer.core.ShotType
import dev.swinganalyzer.core.Status
import dev.swinganalyzer.keyFrameFile
import java.io.File
import java.util.Locale

// ---------- shared pieces ----------

@Composable
private fun Page(title: String, onBack: (() -> Unit)? = null, action: (@Composable () -> Unit)? = null, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { if (onBack != null) TextButton(onClick = onBack) { Text("Back") } },
                actions = { action?.invoke() },
            )
        },
        content = content,
    )
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun <T> ChoiceRow(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in options.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (o in row) FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
            }
        }
    }
}

@Composable
private fun KeyFrames(dir: File?, labels: List<String>) {
    if (dir == null || labels.isEmpty()) return
    val images = remember(dir, labels) {
        labels.mapNotNull { l ->
            File(dir, keyFrameFile(l)).takeIf { it.exists() }?.let { f -> BitmapFactory.decodeFile(f.path)?.let { l to it } }
        }
    }
    if (images.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(images) { (label, bmp) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(bmp.asImageBitmap(), contentDescription = label, modifier = Modifier.height(220.dp))
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ---------- home ----------

@Composable
fun HomeScreen(
    notes: List<Note>,
    onPick: () -> Unit,
    onOpenNote: (Note) -> Unit,
    onSettings: () -> Unit,
    onExportAll: (List<Note>) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Page("Swing Analyzer", action = { TextButton(onClick = onSettings) { Text("Settings") } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Analyze") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Notes (${notes.size})") })
            }
            if (tab == 0) AnalyzeTab(onPick) else NotesTab(notes, onOpenNote, onExportAll)
        }
    }
}

@Composable
private fun AnalyzeTab(onPick: () -> Unit) {
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Pick a swing video from your gallery. Everything is analysed on this phone; nothing is uploaded.",
            style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) { Text("Choose swing video") }
        Section("Filming tips")
        listOf(
            "Tripod at hand height, about 3–4 m away, the same spot every time.",
            "Down-the-line: behind you, looking along the target line. Face-on: facing your chest.",
            "Whole body and the club at the top in the frame.",
            "60 fps or more; slow motion (120/240 fps) gives the best tempo readings.",
            "Pick the original clip, not one trimmed in the gallery editor.",
        ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun NotesTab(notes: List<Note>, onOpen: (Note) -> Unit, onExportAll: (List<Note>) -> Unit) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val clubs = notes.map { it.clubName }.distinct()
    val shown = notes.filter { filter == null || it.clubName == filter }
    Column(Modifier.padding(16.dp)) {
        if (notes.isEmpty()) {
            Text("No saved notes yet. Analyse a swing and tap Save note.")
            return@Column
        }
        ChoiceRow(listOf<String?>(null) + clubs, filter, { it ?: "All clubs" }) { filter = it }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { onExportAll(shown) }, modifier = Modifier.fillMaxWidth()) {
            Text("Export ${if (filter == null) "all" else filter} as .txt (${shown.size})")
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { n ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(n) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${n.clubName} · ${n.shot.label} · ${n.view.label}" + if (n.isReference) " · reference" else "",
                            fontWeight = FontWeight.SemiBold)
                        Text(n.dateText(), style = MaterialTheme.typography.labelMedium)
                        Text(n.feedbackText.lineSequence().first(), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        if (n.comment.isNotBlank()) Text("“${n.comment}”", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                }
            }
        }
    }
}

// ---------- setup ----------

@Composable
fun SetupScreen(
    info: ClipInfo,
    settings: Settings,
    knowledge: Knowledge,
    onAnalyze: (clubId: String, shot: ShotType, view: CameraView, hand: Handedness, slowMotion: Double) -> Unit,
    onBack: () -> Unit,
) {
    val bag = knowledge.clubs.filter { it.id in settings.bag }.ifEmpty { knowledge.clubs }
    var club by remember { mutableStateOf(bag.firstOrNull { it.id == settings.lastClubId }) }
    var shot by remember(club) { mutableStateOf(club?.shots?.first()) }
    var view by remember { mutableStateOf(settings.view) }
    var hand by remember { mutableStateOf(settings.handedness) }
    var slow by remember { mutableStateOf(info.suggestedSlowMotion) }

    Page("New analysis", onBack = onBack) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            val capture = info.captureFps?.let { " · recorded at %.0f fps".format(Locale.US, it) } ?: ""
            Text("Clip: %.1f s · %.0f fps · %d×%d%s".format(Locale.US, info.durationS, info.fps, info.width, info.height, capture),
                style = MaterialTheme.typography.bodyMedium)

            Section("Which club did you use?")
            ChoiceRow(bag, club, Club::name) { club = it }

            val shots = club?.shots.orEmpty()
            if (shots.size > 1) {
                Section("Shot")
                ChoiceRow(shots, shot, ShotType::label) { shot = it }
            }

            Section("Camera view")
            ChoiceRow(CameraView.entries, view, CameraView::label) { view = it }
            Section("Golfer")
            ChoiceRow(Handedness.entries, hand, Handedness::label) { hand = it }
            Section("Speed")
            ChoiceRow(listOf(1.0, 2.0, 4.0, 8.0), slow, { if (it == 1.0) "Normal" else "Slow-mo ×%.0f".format(Locale.US, it) }) { slow = it }
            Text("Slow-motion clips play slower than real time; pick the factor so tempo is measured in real seconds.",
                style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onAnalyze(club!!.id, shot ?: ShotType.FULL, view, hand, slow) },
                enabled = club != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Analyze swing") }
        }
    }
}

// ---------- analysing ----------

@Composable
fun AnalyzingScreen(step: String, progress: Float, onCancel: () -> Unit) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    Page("Analysing") { pad ->
        Column(Modifier.padding(pad).padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(step, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

// ---------- result ----------

@Composable
fun ResultScreen(
    analysis: AnalysisResult,
    keyFrameDir: File,
    savedNote: Note?,
    onSave: (comment: String, reference: Boolean) -> Unit,
    onExport: (comment: String) -> Unit,
    onShare: (comment: String) -> Unit,
    onDone: () -> Unit,
) {
    var comment by rememberSaveable { mutableStateOf("") }
    var reference by rememberSaveable { mutableStateOf(false) }
    val f = analysis.feedback
    Page("Your swing", onBack = onDone) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("${analysis.clubName} · ${analysis.shot.label} · ${analysis.view.label}", fontWeight = FontWeight.SemiBold)
            Text(analysis.clipText(), style = MaterialTheme.typography.labelMedium)

            if (analysis.warnings.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(12.dp)) { analysis.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) } }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(f.summary, style = MaterialTheme.typography.titleLarge)

            if (f.working.isNotEmpty()) {
                Section("What's working")
                f.working.forEach { Text("• $it") }
            }
            if (f.workOn.isNotEmpty()) {
                Section("Work on")
                f.workOn.forEachIndexed { i, w ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${i + 1}. ${w.title}", fontWeight = FontWeight.SemiBold)
                            Text("Seen: ${w.seen}", style = MaterialTheme.typography.bodyMedium)
                            Text("Why it matters: ${w.why}", style = MaterialTheme.typography.bodyMedium)
                            if (w.drillName != null) {
                                Spacer(Modifier.height(6.dp))
                                Text("Drill: ${w.drillName}", fontWeight = FontWeight.Medium)
                                w.drillSteps.forEach { Text("– $it", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
            }
            f.bodyNote?.let { Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.bodySmall) }

            f.tempo?.let { Section("Tempo"); Text(it) }

            Section("Key positions")
            KeyFrames(keyFrameDir, analysis.keyFrames.map { it.label })

            Section("Key numbers")
            f.numbers.forEach { r ->
                val mark = when { r.status == Status.FAULT -> " ✗"; r.status == Status.WARN -> " ⚠"; r.unusual -> " (unusual)"; else -> "" }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(r.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(r.value + mark, Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Text(r.expected, Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            Section("Couldn't judge from this clip")
            f.couldntJudge.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            if (savedNote == null) {
                OutlinedTextField(comment, { comment = it }, label = { Text("My comment (optional)") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(reference, { reference = it })
                    Text("Mark as my reference swing for ${analysis.clubName}")
                }
                Button(onClick = { onSave(comment, reference) }, modifier = Modifier.fillMaxWidth()) { Text("Save note") }
            } else {
                Text("Saved to Notes.", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onExport(comment) }, modifier = Modifier.weight(1f)) { Text("Export .txt") }
                OutlinedButton(onClick = { onShare(comment) }, modifier = Modifier.weight(1f)) { Text("Share") }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ---------- note detail ----------

@Composable
fun NoteDetailScreen(
    note: Note,
    keyFrameDir: File?,
    keyFrameLabels: List<String>,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onReference: (Boolean) -> Unit,
    onComment: (String) -> Unit,
    onBack: () -> Unit,
) {
    var confirm by remember { mutableStateOf(false) }
    var comment by remember(note.id) { mutableStateOf(note.comment) }
    Page("${note.clubName} · ${note.dateText()}", onBack = { if (comment != note.comment) onComment(comment); onBack() }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            KeyFrames(keyFrameDir, keyFrameLabels)
            Spacer(Modifier.height(12.dp))
            Text(note.exportText().substringBefore("MY COMMENT").trimEnd(), fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall)
            Section("My comment")
            OutlinedTextField(comment, { comment = it }, modifier = Modifier.fillMaxWidth())
            if (comment != note.comment) TextButton(onClick = { onComment(comment) }) { Text("Save comment") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(note.isReference, { onReference(it) })
                Text("Reference swing for ${note.clubName}")
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("Export .txt") }
                OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) { Text("Share") }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { confirm = true }) { Text("Delete note") }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep") } },
            title = { Text("Delete this note?") },
            text = { Text("Its key frames and numbers are deleted too. Export it first if you want a copy.") },
        )
    }
}

// ---------- settings ----------

@Composable
fun SettingsScreen(settings: Settings, knowledge: Knowledge, onSave: (Settings) -> Unit, onBack: () -> Unit) {
    var height by remember { mutableStateOf("%.0f".format(Locale.US, settings.heightCm)) }
    var hand by remember { mutableStateOf(settings.handedness) }
    var view by remember { mutableStateOf(settings.view) }
    var bag by remember { mutableStateOf(settings.bag) }
    Page("Settings", onBack = onBack) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(height, { height = it.filter(Char::isDigit).take(3) }, label = { Text("Your height (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Text("Used only to show distances in centimetres.", style = MaterialTheme.typography.bodySmall)
            Section("Golfer")
            ChoiceRow(Handedness.entries, hand, Handedness::label) { hand = it }
            Section("Usual camera view")
            ChoiceRow(CameraView.entries, view, CameraView::label) { view = it }
            Section("My bag")
            knowledge.clubs.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(c.id in bag, { on -> bag = if (on) bag + c.id else bag - c.id })
                    Text(c.name)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onSave(settings.copy(heightCm = height.toDoubleOrNull()?.coerceIn(120.0, 220.0) ?: settings.heightCm,
                    handedness = hand, view = view, bag = bag.ifEmpty { knowledge.clubs.map { it.id }.toSet() })) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}

// ---------- error ----------

@Composable
fun ErrorScreen(message: String, onBack: () -> Unit) {
    Page("Couldn't analyse this clip", onBack = onBack) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Text(message)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onBack) { Text("Back") }
        }
    }
}
