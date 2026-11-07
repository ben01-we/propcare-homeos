package app.propcare.homeos.ui.demo

import android.content.Context
import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.propcare.homeos.AppViewModel
import app.propcare.homeos.R
import app.propcare.homeos.data.JobDraft
import app.propcare.homeos.domain.DemoDirectory
import app.propcare.homeos.domain.Safety
import app.propcare.homeos.domain.Triage
import app.propcare.homeos.domain.Urgency
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File

enum class DemoStep { describe, assessment, matching, technician, brief, saved }
data class DemoReportState(
    val step: DemoStep = DemoStep.describe, val description: String = "", val photo: ImageBitmap? = null,
    val propertyId: Long? = null, val parentJobId: Long? = null, val triage: Triage? = null,
    val technicianId: String? = null, val loading: Boolean = false, val checking: Boolean = false,
    val saving: Boolean = false, val error: String? = null, val onlineNote: String? = null,
    val sample: Boolean = false, val access: String = "", val title: String = "", val savedId: Long? = null,
    val matchingComplete: Boolean = false,
)

/** Draft survives rotation/process recreation. Sample results are never used for real camera/gallery photos. */
class DemoFlowModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val drafts = ReportDraftStore(app)
    private val restored = drafts.read()
    private var photoPath: String? = restored?.photoPath ?: saved["photo"]
    private var current by mutableStateOf(DemoReportState(
        description = restored?.description ?: saved["description"] ?: "",
        propertyId = restored?.propertyId ?: saved["property"], parentJobId = restored?.parentJobId ?: saved["parent"],
        sample = restored?.sample ?: saved["sample"] ?: false, access = restored?.access ?: saved["access"] ?: "",
        title = restored?.title.orEmpty(), triage = restored?.triage, technicianId = restored?.technicianId,
        matchingComplete = restored?.matchingComplete ?: false,
        step = if (restored?.triage != null) DemoStep.entries.firstOrNull { it.name == restored.step && it != DemoStep.saved } ?: DemoStep.describe else DemoStep.describe,
    ))
    var state: DemoReportState
        get() = current
        private set(value) {
            current = value
            if (value.savedId == null) drafts.write(StoredReport(value.description, photoPath, value.propertyId, value.parentJobId,
                value.triage, value.technicianId, value.sample, value.access, value.title, value.step.name, value.matchingComplete))
        }
    private var checkJob: Job? = null
    private var photoJob: Job? = null

    fun start(property: Long?, parent: Long?, description: String = "") {
        if (state.parentJobId != parent) { photoPath = null; state = DemoReportState() }
        state = state.copy(propertyId = state.propertyId ?: property, parentJobId = state.parentJobId ?: parent,
            description = state.description.ifBlank { description })
        saved["property"] = state.propertyId; saved["parent"] = state.parentJobId; saved["description"] = state.description
    }
    fun description(value: String) { cancelCheck(); state = state.copy(description = value.take(500), triage = null, error = null); saved["description"] = state.description }
    fun access(value: String) { state = state.copy(access = value.take(200)); saved["access"] = state.access }
    fun title(value: String) { state = state.copy(title = value.take(120)) }
    fun property(id: Long) { state = state.copy(propertyId = id); saved["property"] = id }
    fun choose(id: String) { if (DemoDirectory.matching(state.triage?.tradeEnum ?: return).any { it.id == id }) state = state.copy(technicianId = id) }
    fun matched() { state = state.copy(matchingComplete = true) }
    fun next(step: DemoStep) { state = state.copy(step = step, error = null) }
    fun error(message: String) { state = state.copy(error = message, loading = false) }
    private fun cancelCheck() { checkJob?.cancel(); state = state.copy(checking = false) }
    fun back() {
        cancelCheck()
        state = state.copy(step = when (state.step) {
            DemoStep.assessment -> DemoStep.describe
            DemoStep.matching -> DemoStep.assessment
            DemoStep.technician -> DemoStep.matching
            DemoStep.brief -> if (state.technicianId != null) DemoStep.technician else DemoStep.matching
            else -> DemoStep.describe
        }, error = null)
    }
    fun restore(vm: AppViewModel) {
        if (state.photo != null || state.loading || photoPath == null) return
        viewModelScope.launch {
            try { state = state.copy(photo = vm.loadPhoto(Uri.fromFile(File(photoPath!!))).asImageBitmap()) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { photoPath = null; saved["photo"] = null; error("Please choose the photo again.") }
        }
    }
    fun load(vm: AppViewModel, uri: Uri) {
        cancelCheck(); photoJob?.cancel(); state = state.copy(loading = true, error = null, sample = false, triage = null, step = DemoStep.describe, matchingComplete = false)
        saved["sample"] = false
        photoJob = viewModelScope.launch {
            try {
                val bitmap = vm.loadPhoto(uri)
                photoPath = vm.savePhoto(bitmap); saved["photo"] = photoPath
                state = state.copy(photo = bitmap.asImageBitmap(), loading = false)
                check(vm)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error("Could not open that photo. Try another one.") }
        }
    }
    fun sample(vm: AppViewModel, context: Context) {
        cancelCheck(); photoJob?.cancel(); state = state.copy(loading = true, error = null)
        photoJob = viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeResource(context.resources, R.drawable.demo_tap) }
                photoPath = vm.savePhoto(bitmap); saved["photo"] = photoPath; saved["sample"] = true
                state = state.copy(photo = bitmap.asImageBitmap(), loading = false, sample = true, triage = null)
                description("The kitchen tap keeps dripping, even when turned off.")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error("Could not load the sample photo. Take or choose a photo instead.") }
        }
    }
    fun check(vm: AppViewModel) {
        if (state.checking || state.loading) return
        if (state.propertyId == null) { error("Choose a property first."); return }
        if (state.photo == null && state.description.isBlank()) { error("Add a photo or describe the problem."); return }
        val snapshot = state
        val checkedPhotoPath = photoPath
        state = snapshot.copy(checking = true, error = null, matchingComplete = false)
        checkJob = viewModelScope.launch {
            try {
                val started = android.os.SystemClock.elapsedRealtime()
                val result = if (snapshot.sample) {
                    app.propcare.homeos.TriageResult(Safety.enforce(Triage(
                        issueSummary = "A possible tap seal issue", trade = "plumber", urgency = "routine", source = "demo",
                        likelyCauses = listOf("A worn washer or seal", "A loose connection", "A damaged tap cartridge"), confidence = "low",
                        customerAdvice = listOf("Catch the drips. Avoid forcing the handle."),
                        cannotTell = listOf("The condition of the internal seal"), box = listOf(220, 340, 850, 640),
                    ), snapshot.description))
                } else {
                    val bitmap = checkedPhotoPath?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(it) } }
                    vm.triage(bitmap, snapshot.description)
                }
                // A brief, bounded transition lets the captured photo hand off visibly to analysis.
                delay((1600L - (android.os.SystemClock.elapsedRealtime() - started)).coerceAtLeast(0))
                val t = Safety.enforce(result.triage, snapshot.description)
                state = state.copy(checking = false, triage = t, title = t.issueSummary.take(120),
                    technicianId = DemoDirectory.matching(t.tradeEnum).firstOrNull()?.id,
                    onlineNote = result.onlineError, step = DemoStep.assessment)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { state = state.copy(checking = false, error = "The check failed. Try again or describe the issue.") }
        }
    }
    fun save(vm: AppViewModel, onSaved: (Long) -> Unit) {
        if (state.saving) return
        val t = state.triage ?: return
        val pid = state.propertyId ?: return
        state = state.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val safe = Safety.enforce(t, state.description)
                val id = vm.createDemoJob(JobDraft(pid, state.title.ifBlank { safe.issueSummary }, state.description,
                    safe.tradeEnum, if (safe.urgencyEnum == Urgency.emergency) Urgency.emergency else safe.urgencyEnum,
                    triage = safe, accessNotes = state.access, photoPath = photoPath,
                    technicianId = state.technicianId, parentJobId = state.parentJobId))
                state = state.copy(saving = false, savedId = id, step = DemoStep.saved); drafts.clear(); onSaved(id)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { state = state.copy(saving = false, error = "Could not save the repair. Your draft is still here; try again.") }
        }
    }
}
