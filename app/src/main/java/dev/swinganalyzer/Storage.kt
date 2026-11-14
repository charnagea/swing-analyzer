package dev.swinganalyzer

import android.content.Context
import dev.swinganalyzer.core.AnalysisResult
import dev.swinganalyzer.core.CameraView
import dev.swinganalyzer.core.Handedness
import dev.swinganalyzer.core.Note
import java.io.File

data class Settings(
    val heightCm: Double,
    val handedness: Handedness,
    val view: CameraView,
    val bag: Set<String>,
    val lastClubId: String?,
)

/**
 * Everything lives in the app's private storage: analyses (numbers + key-frame JPEGs, never video)
 * and notes as small JSON files, settings in shared preferences.
 */
class Storage(private val context: Context, private val allClubIds: List<String>) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val analysesDir = File(context.filesDir, "analyses").apply { mkdirs() }
    private val notesDir = File(context.filesDir, "notes").apply { mkdirs() }

    fun loadSettings() = Settings(
        heightCm = prefs.getFloat("heightCm", 177f).toDouble(),
        handedness = Handedness.fromId(prefs.getString("handedness", Handedness.RIGHT.id)!!),
        view = CameraView.fromId(prefs.getString("view", CameraView.DOWN_THE_LINE.id)!!),
        bag = prefs.getStringSet("bag", null)?.toSet() ?: allClubIds.toSet(),
        lastClubId = prefs.getString("lastClub", null),
    )

    fun saveSettings(s: Settings) {
        prefs.edit()
            .putFloat("heightCm", s.heightCm.toFloat())
            .putString("handedness", s.handedness.id)
            .putString("view", s.view.id)
            .putStringSet("bag", s.bag)
            .putString("lastClub", s.lastClubId)
            .apply()
    }

    fun analysisDir(id: String) = File(analysesDir, id)

    fun saveAnalysis(a: AnalysisResult) {
        val dir = analysisDir(a.id).apply { mkdirs() }
        File(dir, "analysis.json").writeText(a.toJson())
    }

    fun loadAnalysis(id: String): AnalysisResult? = runCatching {
        AnalysisResult.fromJson(File(analysisDir(id), "analysis.json").readText())
    }.getOrNull()

    fun saveNote(n: Note) = File(notesDir, "${n.id}.json").writeText(n.toJson())

    fun deleteNote(n: Note) {
        File(notesDir, "${n.id}.json").delete()
        n.analysisId?.let { id -> if (notes().none { it.analysisId == id }) analysisDir(id).deleteRecursively() }
    }

    fun notes(): List<Note> = notesDir.listFiles { f -> f.extension == "json" }.orEmpty()
        .mapNotNull { runCatching { Note.fromJson(it.readText()) }.getOrNull() }
        .sortedByDescending { it.createdAt }

    /** Only one reference swing per club. */
    fun setReference(note: Note, on: Boolean) {
        if (on) notes().filter { it.clubId == note.clubId && it.isReference && it.id != note.id }
            .forEach { saveNote(it.copy(isReference = false)) }
        saveNote(note.copy(isReference = on))
    }

    /** Removes analyses that were never saved as a note, after a week. */
    fun cleanUp(maxAgeMs: Long = 7L * 24 * 3600 * 1000) {
        val keep = notes().mapNotNull { it.analysisId }.toSet()
        val now = System.currentTimeMillis()
        analysesDir.listFiles().orEmpty().filter { it.isDirectory && it.name !in keep && now - it.lastModified() > maxAgeMs }
            .forEach { it.deleteRecursively() }
    }
}
