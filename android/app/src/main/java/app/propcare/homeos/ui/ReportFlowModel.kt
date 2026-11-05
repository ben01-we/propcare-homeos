package app.propcare.homeos.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.propcare.homeos.AppViewModel
import app.propcare.homeos.data.JobDraft
import app.propcare.homeos.domain.Urgency
import app.propcare.homeos.ui.screens.ReportState
import app.propcare.homeos.ui.screens.ReportStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Holds the report flow while it is open (scoped to the report destination, so it survives
 * rotation). The screen itself stays stateless and only renders [state].
 */
class ReportFlowModel : ViewModel() {
    var state by mutableStateOf(ReportState())
        private set
    var savedJobId by mutableStateOf<Long?>(null)
        private set
    private var bitmap: Bitmap? = null
    /** Where the camera app writes the photo. */
    var captureUri: Uri? = null

    fun update(change: (ReportState) -> ReportState) { state = change(state).copy(error = null) }

    fun start(propertyId: Long?, onlineConfigured: Boolean) {
        if (state.propertyId == null) state = state.copy(propertyId = propertyId, onlineConfigured = onlineConfigured)
    }

    fun loadPhoto(vm: AppViewModel, uri: Uri) {
        state = state.copy(loadingPhoto = true, error = null)
        viewModelScope.launch {
            try {
                setPhoto(vm.loadPhoto(uri))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state = state.copy(loadingPhoto = false, error = "Could not open that photo. Try another one.")
            }
        }
    }

    fun setPhoto(b: Bitmap?) {
        bitmap = b
        state = state.copy(photo = b?.asImageBitmap(), loadingPhoto = false, triage = null)
    }

    fun check(vm: AppViewModel) {
        if (state.checking) return
        state = state.copy(checking = true, error = null)
        viewModelScope.launch {
            try {
                val r = vm.triage(bitmap, state.description)
                val t = r.triage
                state = state.copy(
                    checking = false, triage = t, step = ReportStep.result,
                    onlineNote = r.onlineError?.let { "We used the offline check. $it" },
                    title = state.title.ifBlank { t.issueSummary.take(80) }, trade = t.tradeEnum, urgency = t.urgencyEnum,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state = state.copy(checking = false, error = "The check did not work. Try again, or add a few words about the issue.")
            }
        }
    }

    fun back() {
        state = when (state.step) {
            ReportStep.details -> state.copy(step = ReportStep.result)
            ReportStep.result -> state.copy(step = ReportStep.describe)
            else -> state
        }
    }

    fun save(vm: AppViewModel) {
        val s = state
        val pid = s.propertyId ?: return
        if (s.saving || s.title.isBlank()) return
        state = s.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val path = bitmap?.let { vm.savePhoto(it) }
                // The safety rules win: a flagged emergency stays an emergency.
                val urgency = if (s.triage?.urgencyEnum == Urgency.emergency) Urgency.emergency else s.urgency
                vm.createJob(
                    JobDraft(
                        propertyId = pid, title = s.title, description = s.description, trade = s.trade, urgency = urgency,
                        triage = s.triage, accessNotes = s.accessNotes, preferredTimes = s.preferredTimes, photoPath = path,
                    ),
                ) { id ->
                    savedJobId = id
                    state = state.copy(saving = false, step = ReportStep.saved)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state = state.copy(saving = false, error = "Could not save the photo. Try again.")
            }
        }
    }
}
