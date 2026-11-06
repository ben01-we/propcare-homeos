package app.propcare.homeos.data

import android.content.Context
import androidx.room.withTransaction
import app.propcare.homeos.domain.CheckStatus
import app.propcare.homeos.domain.Compliance
import app.propcare.homeos.domain.HealthScore
import app.propcare.homeos.domain.Health
import app.propcare.homeos.domain.JobStatus
import app.propcare.homeos.domain.PropertyType
import app.propcare.homeos.domain.Safety
import app.propcare.homeos.domain.Trade
import app.propcare.homeos.domain.Triage
import app.propcare.homeos.domain.Urgency
import app.propcare.homeos.util.Format
import app.propcare.homeos.util.Images
import app.propcare.homeos.widget.HomeWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/** A property with its safety checks, Home Health Score and open jobs, derived from the database. */
data class PropertySummary(
    val property: PropertyEntity,
    val checks: List<CheckStatus>,
    val health: Health,
    val openJobs: List<JobEntity>,
) {
    val type: PropertyType get() = PropertyType.of(property.type)
}

/** Everything the report flow collects before a job is saved. */
data class JobDraft(
    val propertyId: Long,
    val title: String,
    val description: String,
    val trade: Trade,
    val urgency: Urgency,
    val triage: Triage? = null,
    val accessNotes: String = "",
    val preferredTimes: String = "",
    val photoPath: String? = null,
    val technicianId: String? = null,
    val parentJobId: Long? = null,
)

/** Staff board edit. Null fields are left unchanged; [message] is shown to the customer on the timeline. */
data class StaffUpdate(
    val status: JobStatus? = null,
    val quotePence: Int? = null,
    val visitAt: Long? = null,
    val message: String = "",
    val staffNotes: String? = null,
    val warrantyDays: Int? = null,
)

/** Codec for the triage stored on each job. */
val jsonCodec = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun JobEntity.triage(): Triage? = triageJson?.let { runCatching { jsonCodec.decodeFromString(Triage.serializer(), it) }.getOrNull() }
val JobEntity.statusEnum: JobStatus get() = JobStatus.of(status)
val JobEntity.urgencyEnum: Urgency get() = Urgency.of(urgency)
val JobEntity.tradeEnum: Trade get() = Trade.of(trade)
val JobEntity.refLabel: String get() = Format.jobRef(ref)
/** Customers can cancel until the job is booked. */
val JobEntity.canCancel: Boolean get() = statusEnum == JobStatus.requested || statusEnum == JobStatus.quoted

fun summarise(
    properties: List<PropertyEntity>,
    compliance: List<ComplianceEntity>,
    jobs: List<JobEntity>,
    today: LocalDate = LocalDate.now(),
): List<PropertySummary> = properties.map { p ->
    val type = PropertyType.of(p.type)
    val dates = compliance.filter { it.propertyId == p.id }.associate { it.kind to it.lastDone }
    val checks = Compliance.rulesFor(type).map { Compliance.status(it, dates[it.kind], today) }
    val open = jobs.filter { it.propertyId == p.id && it.statusEnum.isOpen }
    val health = HealthScore.compute(type, checks, open.map { HealthScore.OpenIssue(it.id, it.title, it.urgencyEnum) })
    PropertySummary(p, checks, health, open)
}

/**
 * The one place that writes data. Every write refreshes the home-screen widget.
 * Jobs and their timeline events are always written together in one transaction.
 */
class Repository(private val context: Context) {
    val db = AppDb.get(context)
    val dao = db.dao()
    val settings = Settings(context)

    private suspend fun changed() = HomeWidget.refresh(context)

    suspend fun saveProfile(name: String, phone: String, postcode: String) {
        dao.saveProfile(ProfileEntity(fullName = name.trim(), phone = phone.trim(), postcode = Format.normalisePostcode(postcode)))
    }

    suspend fun saveProperty(p: PropertyEntity): Long {
        val clean = p.copy(name = p.name.trim(), addressLine = p.addressLine.trim(), town = p.town.trim(), postcode = Format.normalisePostcode(p.postcode))
        val id = if (clean.id == 0L) {
            db.withTransaction {
                val newId = dao.insertProperty(clean)
                defaultRooms.forEach { dao.insertRoom(RoomEntity(propertyId = newId, name = it)) }
                newId
            }
        } else {
            dao.updateProperty(clean); clean.id
        }
        changed()
        return id
    }

    suspend fun deleteProperty(id: Long) {
        val photos = dao.jobsFor(id).flatMap { listOfNotNull(it.photoPath, it.afterPhotoPath) }
        dao.deleteProperty(id)
        withContext(Dispatchers.IO) { photos.forEach { deletePhoto(it) } }
        changed()
    }

    suspend fun addRoom(propertyId: Long, name: String) { if (name.isNotBlank()) dao.insertRoom(RoomEntity(propertyId = propertyId, name = name.trim())) }
    suspend fun renameRoom(room: RoomEntity, name: String) { if (name.isNotBlank()) dao.updateRoom(room.copy(name = name.trim())) }
    suspend fun deleteRoom(id: Long) = dao.deleteRoom(id)

    /** Records when a check was last done (ISO yyyy-MM-dd), or clears it with null. */
    suspend fun setCheckDate(propertyId: Long, kind: String, lastDone: LocalDate?) {
        val existing = dao.complianceFor(propertyId, kind)
        dao.upsertCompliance(ComplianceEntity(id = existing?.id ?: 0, propertyId = propertyId, kind = kind, lastDone = lastDone?.toString()))
        changed()
    }

    suspend fun createJob(d: JobDraft, now: Long = System.currentTimeMillis()): Long {
        val id = db.withTransaction {
            val jobId = dao.insertJob(
                JobEntity(
                    ref = dao.nextRef(), propertyId = d.propertyId, title = d.title.trim().take(120), description = d.description.trim().take(500),
                    trade = d.trade.name, urgency = d.urgency.name, status = JobStatus.requested.name,
                    triageJson = d.triage?.let { jsonCodec.encodeToString(Triage.serializer(), it) },
                    accessNotes = d.accessNotes.trim(), preferredTimes = d.preferredTimes.trim(), photoPath = d.photoPath,
                    createdAt = now, updatedAt = now,
                    technicianId = d.technicianId, parentJobId = d.parentJobId,
                ),
            )
            dao.insertEvent(JobEventEntity(jobId = jobId, status = JobStatus.requested.name, note = "Request created", createdAt = now))
            jobId
        }
        changed()
        return id
    }

    /** "Book" on a safety check: a job for the right trade, with the example check price in the note. */
    suspend fun bookCheck(propertyId: Long, kind: String): Long {
        val rule = Compliance.rule(kind)
        return createJob(JobDraft(propertyId = propertyId, title = "Book: ${rule.name}", description = rule.rule, trade = rule.trade, urgency = Urgency.routine))
    }

    suspend fun cancelJob(jobId: Long, reason: String = ""): Boolean {
        val ok = db.withTransaction {
            val job = dao.job(jobId) ?: return@withTransaction false
            if (!job.canCancel) return@withTransaction false
            val now = System.currentTimeMillis()
            dao.updateJob(job.copy(status = JobStatus.cancelled.name, updatedAt = now))
            dao.insertEvent(JobEventEntity(jobId = jobId, status = JobStatus.cancelled.name, note = reason.ifBlank { "Cancelled by the customer" }, createdAt = now))
            true
        }
        if (ok) changed()
        return ok
    }

    suspend fun setStatus(jobId: Long, status: JobStatus, note: String = "") = staffUpdate(jobId, StaffUpdate(status = status, message = note))

    /** Staff board changes. Each customer-visible change adds a timeline event. */
    suspend fun staffUpdate(jobId: Long, u: StaffUpdate) {
        db.withTransaction {
            val job = dao.job(jobId) ?: return@withTransaction
            val now = System.currentTimeMillis()
            var updated = job.copy(updatedAt = now)
            val notes = mutableListOf<String>()
            if (u.quotePence != null && u.quotePence != job.quotePence) {
                updated = updated.copy(quotePence = u.quotePence)
                notes += "Quote: ${Format.pounds(u.quotePence)}"
            }
            if (u.visitAt != null && u.visitAt != job.visitAt) {
                updated = updated.copy(visitAt = u.visitAt)
                notes += "Visit: ${Format.dateTime(u.visitAt)}"
            }
            if (u.staffNotes != null) updated = updated.copy(staffNotes = u.staffNotes.trim())
            if (u.message.isNotBlank()) notes += u.message.trim()
            val newStatus = u.status ?: job.statusEnum
            require(!(job.statusEnum == JobStatus.cancelled && newStatus == JobStatus.done)) { "A cancelled repair cannot be completed" }
            // Snapshot once at the first completion; subsequent edits/reopening never reset coverage.
            if (newStatus == JobStatus.done && job.completedAt == null) {
                val period = u.warrantyDays ?: 90
                require(period in 1..730) { "Choose a warranty period from 1 to 730 days" }
                updated = updated.copy(completedAt = now, warrantyDays = period)
                notes += "Completed. Example warranty: $period days"
            }
            if (newStatus != job.statusEnum) {
                updated = updated.copy(status = newStatus.name)
                dao.insertEvent(JobEventEntity(jobId = jobId, status = newStatus.name, note = notes.joinToString(". ").ifBlank { newStatus.label }, createdAt = now))
            } else if (notes.isNotEmpty()) {
                dao.insertEvent(JobEventEntity(jobId = jobId, status = job.status, note = notes.joinToString(". "), createdAt = now))
            }
            dao.updateJob(updated)
        }
        changed()
    }

    suspend fun markSent(jobId: Long) {
        val job = dao.job(jobId) ?: return
        dao.updateJob(job.copy(sentAt = System.currentTimeMillis()))
    }

    suspend fun setAfterPhoto(jobId: Long, path: String) {
        val job = dao.job(jobId) ?: return
        job.afterPhotoPath?.let { deletePhoto(it) }
        dao.updateJob(job.copy(afterPhotoPath = path, updatedAt = System.currentTimeMillis()))
    }

    /** Deletes the database, every photo, shared exports and all settings (including the staff PIN). */
    suspend fun deleteAll() {
        withContext(Dispatchers.IO) {
            db.clearAllTables()
            Images.photosDir(context).deleteRecursively()
            File(context.cacheDir, "shared").deleteRecursively()
            settings.clearAll()
        }
        changed()
    }

    /**
     * "Load a sample home": a rental with mixed check states (one due soon, one overdue,
     * one OK, one missing) and two jobs, so judges see a full app at once. Clearly named as a sample.
     */
    suspend fun seedSample(today: LocalDate = LocalDate.now()) {
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        db.withTransaction {
            if (dao.propertiesNow().any { it.name == "Sample rental" }) return@withTransaction
            if (dao.profileNow() == null) dao.saveProfile(ProfileEntity(fullName = "Sample customer", phone = "07700 900123", postcode = "M1 1AE"))
            val pid = dao.insertProperty(PropertyEntity(name = "Sample rental", addressLine = "14 Example Street", town = "Manchester", postcode = "M14 5AA", type = PropertyType.rental.name, createdAt = now))
            listOf("Kitchen", "Bathroom", "Living room", "Bedroom 1", "Bedroom 2").forEach { dao.insertRoom(RoomEntity(propertyId = pid, name = it)) }
            dao.upsertCompliance(ComplianceEntity(propertyId = pid, kind = "gas_safety", lastDone = today.minusMonths(12).plusDays(15).toString())) // due soon
            dao.upsertCompliance(ComplianceEntity(propertyId = pid, kind = "eicr", lastDone = today.minusYears(5).minusDays(20).toString())) // overdue
            dao.upsertCompliance(ComplianceEntity(propertyId = pid, kind = "epc", lastDone = today.minusYears(3).toString())) // ok
            // Smoke and CO alarms: no date recorded (missing)

            val tapText = "The kitchen tap drips all the time, even when turned off hard."
            val tap = Safety.offlineTriage(tapText, emptyList())
            val tapAt = now - 3 * day
            val tapId = dao.insertJob(
                JobEntity(
                    ref = dao.nextRef(), propertyId = pid, title = "Dripping kitchen tap", description = tapText, trade = tap.trade, urgency = Urgency.routine.name,
                    status = JobStatus.quoted.name, triageJson = jsonCodec.encodeToString(Triage.serializer(), tap), accessNotes = "Tenant in after 4pm",
                    preferredTimes = "Weekday evenings", quotePence = 9500, createdAt = tapAt, updatedAt = now - 2 * day, sentAt = tapAt,
                ),
            )
            dao.insertEvent(JobEventEntity(jobId = tapId, status = JobStatus.requested.name, note = "Request created", createdAt = tapAt))
            dao.insertEvent(JobEventEntity(jobId = tapId, status = JobStatus.quoted.name, note = "Quote: £95 (sample)", createdAt = now - 2 * day))

            val mouldText = "Black mould spots on the bathroom ceiling above the shower."
            val mould = Safety.offlineTriage(mouldText, emptyList())
            val mouldAt = now - day
            val mouldId = dao.insertJob(
                JobEntity(
                    ref = dao.nextRef(), propertyId = pid, title = "Mould on bathroom ceiling", description = mouldText, trade = mould.trade, urgency = mould.urgency,
                    status = JobStatus.requested.name, triageJson = jsonCodec.encodeToString(Triage.serializer(), mould), createdAt = mouldAt, updatedAt = mouldAt,
                ),
            )
            dao.insertEvent(JobEventEntity(jobId = mouldId, status = JobStatus.requested.name, note = "Request created", createdAt = mouldAt))
            val completedId = dao.insertJob(JobEntity(
                ref = dao.nextRef(), propertyId = pid, title = "Bathroom seal repaired", description = "Sample completed repair with example coverage.",
                trade = Trade.plumber.name, urgency = Urgency.routine.name, status = JobStatus.done.name,
                technicianId = "jamie", completedAt = now - 7 * day, warrantyDays = 90,
                createdAt = now - 9 * day, updatedAt = now - 7 * day,
            ))
            dao.insertEvent(JobEventEntity(jobId = completedId, status = JobStatus.requested.name, note = "Sample repair requested", createdAt = now - 9 * day))
            dao.insertEvent(JobEventEntity(jobId = completedId, status = JobStatus.done.name, note = "Sample repair completed. Example warranty: 90 days", createdAt = now - 7 * day))
        }
        changed()
    }

    private fun deletePhoto(path: String) {
        val f = File(path)
        // Only ever delete inside our own photos folder.
        if (f.canonicalPath.startsWith(Images.photosDir(context).canonicalPath)) f.delete()
    }

    companion object {
        val defaultRooms = listOf("Kitchen", "Bathroom", "Living room", "Bedroom")
    }
}
