package app.propcare.homeos

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.propcare.homeos.data.JobDraft
import app.propcare.homeos.data.JobEntity
import app.propcare.homeos.data.JobEventEntity
import app.propcare.homeos.data.ProfileEntity
import app.propcare.homeos.data.PropertyEntity
import app.propcare.homeos.data.PropertySummary
import app.propcare.homeos.data.Repository
import app.propcare.homeos.data.RoomEntity
import app.propcare.homeos.data.Settings
import app.propcare.homeos.data.StaffUpdate
import app.propcare.homeos.data.refLabel
import app.propcare.homeos.data.summarise
import app.propcare.homeos.domain.JobStatus
import app.propcare.homeos.domain.Safety
import app.propcare.homeos.domain.Triage
import app.propcare.homeos.net.TriageApi
import app.propcare.homeos.util.Images
import app.propcare.homeos.util.OfflineLabeler
import app.propcare.homeos.util.PassportPdf
import app.propcare.homeos.util.Share
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Result of a photo/text check. [onlineError] says why the online check was skipped, if it was tried. */
data class TriageResult(val triage: Triage, val onlineError: String? = null)

/**
 * App state for every screen, as StateFlows straight from Room, plus every action the UI needs.
 * Screens stay stateless: they read these flows and call these functions.
 * Messages for snackbars arrive on [messages].
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val dao = repo.dao
    private val settings: Settings = repo.settings
    private fun <T> Flow<T>.state(initial: T): StateFlow<T> = stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    /** False until the first database read completes (show a loading state until then). */
    val ready: StateFlow<Boolean> = dao.profile().map { true }.state(false)
    val profile: StateFlow<ProfileEntity?> = dao.profile().state(null)
    val properties: StateFlow<List<PropertyEntity>> = dao.properties().state(emptyList())
    val jobs: StateFlow<List<JobEntity>> = dao.jobs().state(emptyList())
    /** Timeline events grouped by job id, oldest first. */
    val events: StateFlow<Map<Long, List<JobEventEntity>>> = dao.events().map { list -> list.groupBy { it.jobId } }.state(emptyMap())
    /** Rooms grouped by property id. */
    val rooms: StateFlow<Map<Long, List<RoomEntity>>> = dao.allRooms().map { list -> list.groupBy { it.propertyId } }.state(emptyMap())
    /** Each property with its checks, health score and open jobs. */
    val summaries: StateFlow<List<PropertySummary>> =
        combine(dao.properties(), dao.compliance(), dao.jobs()) { p, c, j -> summarise(p, c, j, LocalDate.now()) }.state(emptyList())

    private val _appLock = MutableStateFlow(settings.appLock)
    val appLock: StateFlow<Boolean> = _appLock.asStateFlow()
    private val _staffMode = MutableStateFlow(settings.staffMode)
    val staffMode: StateFlow<Boolean> = _staffMode.asStateFlow()
    private val _hasStaffPin = MutableStateFlow(settings.hasStaffPin)
    val hasStaffPin: StateFlow<Boolean> = _hasStaffPin.asStateFlow()
    /** True when the build has an HTTPS API base, so the AI photo check can run online. */
    val onlineCheckConfigured: Boolean get() = TriageApi.configured

    /** A property to open, set when the user taps a reminder notification. The UI clears it with [consumeOpenProperty]. */
    private val _openProperty = MutableStateFlow<Long?>(null)
    val openProperty: StateFlow<Long?> = _openProperty.asStateFlow()
    fun requestOpenProperty(id: Long) { _openProperty.value = id }
    fun consumeOpenProperty() { _openProperty.value = null }

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()
    private fun say(text: String) { _messages.trySend(text) }

    private fun launchSafely(errorText: String, block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) { say(errorText) }
    }

    fun job(id: Long): JobEntity? = jobs.value.firstOrNull { it.id == id }
    fun summary(propertyId: Long): PropertySummary? = summaries.value.firstOrNull { it.property.id == propertyId }

    // Profile and properties

    fun saveProfile(name: String, phone: String, postcode: String) = launchSafely("Could not save your details") {
        repo.saveProfile(name, phone, postcode); say("Details saved")
    }

    fun saveProperty(p: PropertyEntity, onSaved: (Long) -> Unit = {}) = launchSafely("Could not save the property") {
        val id = repo.saveProperty(p); say("Property saved"); onSaved(id)
    }

    fun deleteProperty(id: Long) = launchSafely("Could not delete the property") { repo.deleteProperty(id); say("Property deleted") }
    fun addRoom(propertyId: Long, name: String) = launchSafely("Could not add the room") { repo.addRoom(propertyId, name) }
    fun renameRoom(room: RoomEntity, name: String) = launchSafely("Could not rename the room") { repo.renameRoom(room, name) }
    fun deleteRoom(id: Long) = launchSafely("Could not delete the room") { repo.deleteRoom(id) }
    fun setCheckDate(propertyId: Long, kind: String, lastDone: LocalDate?) = launchSafely("Could not save the date") { repo.setCheckDate(propertyId, kind, lastDone) }
    fun seedSample() = launchSafely("Could not load the sample home") { repo.seedSample(); say("Sample home loaded") }

    // Jobs

    fun createJob(d: JobDraft, onCreated: (Long) -> Unit = {}) = launchSafely("Could not save the job") {
        val id = repo.createJob(d); onCreated(id)
    }

    fun bookCheck(propertyId: Long, kind: String, onCreated: (Long) -> Unit = {}) = launchSafely("Could not create the booking") {
        val id = repo.bookCheck(propertyId, kind); say("Booking request created"); onCreated(id)
    }

    fun cancelJob(jobId: Long, reason: String = "") = launchSafely("Could not cancel the job") {
        say(if (repo.cancelJob(jobId, reason)) "Job cancelled" else "This job is booked, so call PropCare to cancel it")
    }

    fun setStatus(jobId: Long, status: JobStatus, note: String = "") = launchSafely("Could not update the job") { repo.setStatus(jobId, status, note) }
    fun staffUpdate(jobId: Long, u: StaffUpdate) = launchSafely("Could not update the job") { repo.staffUpdate(jobId, u); say("Job updated") }

    /** Opens WhatsApp or email with the job sheet and photo, then marks the job as sent. */
    fun sendJob(context: Context, jobId: Long, channel: Share.Channel) = launchSafely("Could not send the job") {
        val job = dao.job(jobId) ?: return@launchSafely
        val property = properties.value.firstOrNull { it.id == job.propertyId }
        val text = Share.jobSheet(job, property, profile.value)
        val ok = Share.send(context, channel, "Job request ${job.refLabel}: ${job.title}", text, Share.photoUri(context, job.photoPath))
        if (ok) repo.markSent(jobId) else say(if (channel == Share.Channel.whatsapp) "No app found to send this" else "No email app found")
    }

    fun setAfterPhoto(jobId: Long, bitmap: Bitmap) = launchSafely("Could not save the photo") {
        repo.setAfterPhoto(jobId, Images.saveForJob(getApplication(), bitmap))
    }

    /** Builds the maintenance passport PDF and opens the share sheet. */
    fun exportPassport(context: Context, propertyId: Long) = launchSafely("Could not create the passport PDF") {
        val s = summary(propertyId) ?: return@launchSafely
        val uri = PassportPdf.create(getApplication(), s, dao.jobsFor(propertyId))
        if (!Share.shareFile(context, uri, "application/pdf", "Maintenance passport: ${s.property.name}")) say("No app found to share the PDF")
    }

    // Report flow helpers (called from a coroutine in the UI)

    suspend fun loadPhoto(uri: Uri): Bitmap = Images.decodeScaled(getApplication(), uri, 1600)
    suspend fun savePhoto(bitmap: Bitmap): String = Images.saveForJob(getApplication(), bitmap)
    fun clearCaptures() = viewModelScope.launch(Dispatchers.IO) { Images.deleteCaptures(getApplication()) }

    /**
     * The photo check: online AI first when configured, otherwise (or on failure) on-device labels
     * plus keyword rules. The safety rules are always applied, and the description always wins.
     */
    suspend fun triage(bitmap: Bitmap?, description: String): TriageResult {
        if (bitmap == null) return TriageResult(Safety.textOnly(description))
        var error: String? = null
        if (TriageApi.configured) {
            try {
                val jpeg = withContext(Dispatchers.Default) { Images.jpeg(Images.scale(bitmap, 768), 80) }
                val ai = TriageApi.triage(jpeg, description)
                return TriageResult(Safety.enforce(ai.copy(source = "ai"), description))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = (e as? TriageApi.TriageException)?.message ?: "No connection, so this is an offline check."
            }
        }
        val labels = OfflineLabeler.labels(bitmap)
        return TriageResult(Safety.offlineTriage(description, labels), error)
    }

    // Account and security

    fun setAppLock(on: Boolean) { settings.appLock = on; _appLock.value = on }

    fun setStaffPin(pin: String): Boolean {
        if (pin.length !in 4..8 || !pin.all { it.isDigit() }) return false
        settings.setStaffPin(pin); _hasStaffPin.value = true
        return true
    }

    /** PBKDF2 is deliberately slow, so this runs off the main thread. */
    suspend fun checkStaffPin(pin: String): Settings.PinResult {
        val r = withContext(Dispatchers.Default) { settings.checkStaffPin(pin) }
        if (r == Settings.PinResult.Ok) { settings.staffMode = true; _staffMode.value = true }
        return r
    }

    fun leaveStaffMode() { settings.staffMode = false; _staffMode.value = false }

    fun deleteAllData(onDone: () -> Unit = {}) = launchSafely("Could not delete your data") {
        repo.deleteAll()
        _appLock.value = false; _staffMode.value = false; _hasStaffPin.value = false
        say("All data deleted")
        onDone()
    }
}
