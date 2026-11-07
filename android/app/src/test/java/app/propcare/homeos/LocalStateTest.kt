package app.propcare.homeos

import android.app.Application
import android.content.Context
import app.propcare.homeos.data.Settings
import app.propcare.homeos.domain.*
import app.propcare.homeos.ui.demo.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocalStateTest {
    private val app get() = RuntimeEnvironment.getApplication<Application>()
    @Before fun clean() { Settings(app).clearAll(); ReportDraftStore(app).clear() }

    @Test fun accountSessionAndRolePersistWithoutPlaintextPassword() {
        val settings = Settings(app)
        settings.createLocalAccount("Owner@example.test", "a-test-password")
        settings.selectedRole = "landlord"
        val fresh = Settings(app)
        assertTrue(fresh.signedIn)
        assertEquals("landlord", fresh.selectedRole)
        assertEquals("owner@example.test", fresh.localEmail)
        assertFalse(app.getSharedPreferences("propcare_settings", Context.MODE_PRIVATE).all.values.contains("a-test-password"))
        fresh.signedIn = false
        assertFalse(Settings(app).signedIn)
        assertEquals(Settings.PinResult.Wrong, fresh.signIn("wrong@example.test", "a-test-password"))
        assertEquals(Settings.PinResult.Wrong, fresh.signIn("owner@example.test", "wrong-password"))
        assertEquals(Settings.PinResult.Ok, fresh.signIn("OWNER@example.test", "a-test-password"))
        assertTrue(Settings(app).signedIn)
    }

    @Test fun failedLoginLockoutSurvivesRecreation() {
        Settings(app).createLocalAccount("owner@example.test", "a-test-password")
        Settings(app).signedIn = false
        repeat(4) { assertEquals(Settings.PinResult.Wrong, Settings(app).signIn("owner@example.test", "incorrect", 1000)) }
        assertTrue(Settings(app).signIn("owner@example.test", "incorrect", 1000) is Settings.PinResult.Locked)
        assertTrue(Settings(app).signIn("owner@example.test", "a-test-password", 2000) is Settings.PinResult.Locked)
        assertFalse(Settings(app).signedIn)
        assertEquals(Settings.PinResult.Ok, Settings(app).signIn("owner@example.test", "a-test-password", 32000))
    }

    @Test fun reportRestoresAssessmentSelectionAndPhotoAfterRecreation() {
        val report = StoredReport(description = "Tap dripping", photoPath = "/private/photo.jpg", propertyId = 8,
            triage = Safety.offlineTriage("Tap dripping", emptyList()), technicianId = "jamie", access = "After 5",
            title = "Kitchen tap", step = DemoStep.technician.name, matchingComplete = true)
        ReportDraftStore(app).write(report)
        val restored = DemoFlowModel(app, androidx.lifecycle.SavedStateHandle()).state
        assertEquals(DemoStep.technician, restored.step)
        assertEquals(report.triage, restored.triage)
        assertEquals("jamie", restored.technicianId)
        assertTrue(restored.matchingComplete)
        assertEquals(report, ReportDraftStore(app).read())
        ReportDraftStore(app).clear()
        assertNull(ReportDraftStore(app).read())
    }

    @Test fun descriptionOutranksBroadImageLabelsAndUnknownImagesDoNotInventFaults() {
        assertEquals(Trade.heating, Safety.offlineTriage("No hot water from boiler", listOf("Flooring", "Sink")).tradeEnum)
        assertEquals(Trade.roofer, Safety.offlineTriage("Roof leaking after rain", listOf("Sink")).tradeEnum)
        assertEquals(Trade.damp, Safety.offlineTriage("Black mould on wall", listOf("Tile")).tradeEnum)
        val unknown = Safety.offlineTriage("", listOf("Room", "Furniture"))
        assertEquals(Trade.general, unknown.tradeEnum)
        assertTrue(unknown.likelyCauses.isEmpty())
        assertEquals(Urgency.emergency, Safety.offlineTriage("Water dripping into socket", listOf("Faucet")).urgencyEnum)
    }
}
