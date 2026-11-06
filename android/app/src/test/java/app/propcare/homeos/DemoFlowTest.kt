package app.propcare.homeos

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.propcare.homeos.data.Repository
import app.propcare.homeos.ui.demo.DemoApp
import app.propcare.homeos.ui.theme.PropCareTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercises the actual composables, view models and Room together, without a server. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class DemoFlowTest {
    @get:Rule val ui = createComposeRule()
    private fun waitFor(text: String) {
        try { ui.waitUntil(25_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
        catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
            val errors = org.robolectric.shadows.ShadowLog.getLogsForTag("PropCareCompletion").joinToString("\n") { "${it.msg}: ${it.throwable?.stackTraceToString()}" }
            throw AssertionError("Waiting for '$text'\n$errors\n${ui.onRoot(useUnmergedTree = true).printToString()}", failure)
        }
    }
    private fun tap(text: String, scroll: Boolean = false) {
        waitFor(text)
        if (scroll) ui.onNodeWithText(text).performScrollTo()
        ui.onNodeWithText(text).performClick()
    }
    @Test fun sampleReportMatchingCompletionAndWarrantyWorkTogether() {
        val app = RuntimeEnvironment.getApplication()
        val repo = Repository(app)
        runBlocking(Dispatchers.IO) { repo.db.clearAllTables(); repo.seedSample() }
        val vm = AppViewModel(app)
        ui.setContent { PropCareTheme { DemoApp(vm, remember { SnackbarHostState() }) } }
        waitFor("Your home.")
        tap("Report a repair", scroll = true)
        tap("Try the sample tap photo", scroll = true)
        waitFor("Sample photo and assessment. Take or choose a photo for real on-device analysis.")
        ui.waitUntil(25_000) { ui.onAllNodesWithText("Check this issue").fetchSemanticsNodes().any { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Text) } }
        tap("Check this issue")
        waitFor("A possible tap seal issue")
        tap("Find suitable help")
        tap("View technician")
        waitFor("Jamie Taylor")
        tap("Prepare repair brief")
        tap("Save repair brief")
        waitFor("Your repair")
        ui.onNodeWithContentDescription("Back").performClick()
        waitFor("Your home.")
        tap("Resident")
        tap("Technician")
        tap("A possible tap seal issue", scroll = true)
        tap("Complete repair", scroll = true)
        waitFor("Repair finished?")
        tap("Mark completed")
        waitFor("Repair protection.")
        ui.onNodeWithText("90").assertExists()
        tap("Report a problem again")
        waitFor("Show us the issue.")
        val persisted = runBlocking(Dispatchers.IO) { repo.dao.jobsNow().first { it.title == "A possible tap seal issue" } }
        assertEquals("done", persisted.status)
        assertEquals("jamie", persisted.technicianId)
        assertEquals(90, persisted.warrantyDays)
        assertNotNull(persisted.completedAt)
    }
}
