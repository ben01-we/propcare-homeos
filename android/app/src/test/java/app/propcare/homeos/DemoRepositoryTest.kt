package app.propcare.homeos

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.propcare.homeos.data.*
import app.propcare.homeos.domain.*
import app.propcare.homeos.ui.demo.coverage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DemoRepositoryTest {
    @Test fun jobCompletionWarrantyAndFollowupPersistWithoutReset() = runBlocking(Dispatchers.IO) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = Repository(app)
        repo.db.clearAllTables()
        repo.seedSample(); repo.seedSample()
        assertEquals(1, repo.dao.propertiesNow().size)
        val pid = repo.dao.propertiesNow().first().id
        val id = repo.createJob(JobDraft(pid, "Tap", "Dripping tap", Trade.plumber, Urgency.routine, technicianId = "jamie"))
        assertNull(repo.dao.job(id)!!.coverage())
        repo.staffUpdate(id, StaffUpdate(status = JobStatus.done, warrantyDays = 90, message = "Seal replaced"))
        val completed = repo.dao.job(id)!!
        assertNotNull(completed.completedAt)
        assertEquals(90, completed.warrantyDays)
        assertEquals("jamie", Repository(app).dao.job(id)!!.technicianId)
        assertNotNull(completed.coverage())
        repo.staffUpdate(id, StaffUpdate(status = JobStatus.in_progress))
        assertNull(repo.dao.job(id)!!.coverage())
        repo.staffUpdate(id, StaffUpdate(status = JobStatus.done, warrantyDays = 180))
        assertEquals(completed.completedAt, repo.dao.job(id)!!.completedAt)
        assertEquals(90, repo.dao.job(id)!!.warrantyDays)
        val followup = repo.createJob(JobDraft(pid, "Follow-up", "Still dripping", Trade.plumber, Urgency.routine, parentJobId = id))
        assertEquals(id, repo.dao.job(followup)!!.parentJobId)
        assertEquals(completed.completedAt, repo.dao.job(id)!!.completedAt)
        assertTrue(repo.dao.eventsFor(id).any { it.status == "done" && it.note.contains("90 days") })
        val cancelled = repo.createJob(JobDraft(pid, "Cancelled", "Test", Trade.general, Urgency.routine))
        repo.cancelJob(cancelled)
        try { repo.staffUpdate(cancelled, StaffUpdate(status = JobStatus.done, warrantyDays = 90)); fail("Cancelled job must not start coverage") }
        catch (_: IllegalArgumentException) { }
        assertNull(repo.dao.job(cancelled)!!.coverage())
    }
}
