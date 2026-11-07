package app.propcare.homeos

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.propcare.homeos.data.Repository
import app.propcare.homeos.data.Settings
import app.propcare.homeos.ui.PropCareRoot
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class LocalLoginFlowTest {
    @get:Rule val ui = createComposeRule()
    private fun waitFor(text: String) = ui.waitUntil(25_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    @Test fun createSignOutAndSignInKeepTheSameHome() {
        val app = RuntimeEnvironment.getApplication()
        val repo = Repository(app)
        runBlocking(Dispatchers.IO) { repo.db.clearAllTables() }
        Settings(app).clearAll()
        val vm = AppViewModel(app)
        ui.setContent { PropCareRoot(vm, false, {}) }
        waitFor("Create account")
        ui.onNodeWithText("Full name").performTextInput("Morgan Homeowner")
        ui.onNodeWithText("Email address").performTextInput("morgan@example.test")
        ui.onNodeWithText("Password").performTextInput("test-password-123")
        ui.onNodeWithText("Create account").performScrollTo().performClick()
        waitFor("Your home.")
        assertTrue(Settings(app).signedIn)
        val home = runBlocking(Dispatchers.IO) { repo.dao.propertiesNow().single() }
        assertEquals("My home", home.name)
        ui.runOnIdle { vm.signOutLocal() }
        waitFor("Sign in")
        ui.onNodeWithText("Password").performTextInput("test-password-123")
        ui.onNodeWithText("Sign in").performScrollTo().performClick()
        waitFor("Your home.")
        assertEquals(home, runBlocking(Dispatchers.IO) { repo.dao.propertiesNow().single() })
    }
}
