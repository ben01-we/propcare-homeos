package app.propcare.homeos

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.propcare.homeos.data.*
import app.propcare.homeos.domain.*
import app.propcare.homeos.ui.demo.*
import app.propcare.homeos.ui.theme.PropCareTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class DemoScreenshotTest {
    private val property = PropertyEntity(1, "Sample rental", "14 Example Street", "Manchester", "M14 5AA", "rental")
    private val job = JobEntity(id = 1, ref = 1001, propertyId = 1, title = "Dripping kitchen tap", description = "The tap keeps dripping even when turned off.", trade = "plumber", urgency = "routine", technicianId = "jamie")
    private val done = job.copy(status = "done", completedAt = System.currentTimeMillis(), warrantyDays = 90)
    private val triage = Triage(issueSummary = "A possible tap seal issue", trade = "plumber", source = "demo", box = listOf(220, 340, 850, 640), customerAdvice = listOf("Catch the drips. Avoid forcing the handle."))
    private fun photo() = BitmapFactory.decodeResource(RuntimeEnvironment.getApplication().resources, R.drawable.demo_tap).asImageBitmap()
    private fun state(step: DemoStep) = DemoReportState(step = step, description = job.description, photo = photo(), propertyId = 1, triage = triage, technicianId = "jamie", sample = true, title = job.title)
    private fun home() = summarise(listOf(property), listOf(
        ComplianceEntity(propertyId = 1, kind = "gas_safety", lastDone = LocalDate.now().minusMonths(12).plusDays(15).toString()),
        ComplianceEntity(propertyId = 1, kind = "eicr", lastDone = LocalDate.now().minusYears(2).toString()),
        ComplianceEntity(propertyId = 1, kind = "epc", lastDone = LocalDate.now().minusYears(2).toString()),
        ComplianceEntity(propertyId = 1, kind = "alarms", lastDone = LocalDate.now().minusMonths(2).toString()),
    ), listOf(job))
    private fun shot(name: String, dark: Boolean = false, scale: Float = 1f, content: @Composable () -> Unit) =
        captureRoboImage("build/outputs/roborazzi/demo-$name.png") {
            PropCareTheme(darkTheme = dark) {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, scale)) { content() }
            }
        }
    @Test fun homeLight() = shot("01-home") { DemoHomeScreen("Benjamin", home(), listOf(job, done.copy(id = 2)), DemoRole.resident, {}, {}, {}, {}, {}, {}, {}) }
    @Test fun homeDark() = shot("01-home-dark", true) { DemoHomeScreen("Benjamin", home(), listOf(job), DemoRole.resident, {}, {}, {}, {}, {}, {}, {}) }
    @Test fun homeLargeType() = shot("01-home-large-type", scale = 1.3f) { DemoHomeScreen("Benjamin", home(), listOf(job), DemoRole.resident, {}, {}, {}, {}, {}, {}, {}) }
    @Test fun report() = shot("02-report") { DemoReportScreen(state(DemoStep.describe), listOf(property), "en-GB", DemoReportActions()) }
    @Test fun assessment() = shot("03-assessment") { DemoReportScreen(state(DemoStep.assessment), listOf(property), "en-GB", DemoReportActions()) }
    @Test fun matching() = shot("04-matching") { DemoReportScreen(state(DemoStep.matching), listOf(property), "en-GB", DemoReportActions()) }
    @Test fun technician() = shot("05-technician") { DemoReportScreen(state(DemoStep.technician), listOf(property), "en-GB", DemoReportActions()) }
    @Test fun brief() = shot("06-brief") { DemoReportScreen(state(DemoStep.brief), listOf(property), "en-GB", DemoReportActions()) }
    @Test fun completion() = shot("07-completion") { DemoCompletionScreen(job, {}, { _, _ -> }, false, null) }
    @Test fun timeline() = shot("08-timeline") { DemoJobScreen(done, property, listOf(JobEventEntity(jobId = 1, status = "done", note = "Seal replaced")), DemoRole.resident, {}, {}, {}, {}, {}, {}) }
    @Test fun warranty() = shot("09-warranty") { DemoWarrantyScreen(done, onBack = {}, onFollowup = {}) }
    @Test fun warrantyDark() = shot("09-warranty-dark", true) { DemoWarrantyScreen(done, onBack = {}, onFollowup = {}) }
    @Test fun warrantyLargeType() = shot("09-warranty-large-type", scale = 1.3f) { DemoWarrantyScreen(done, onBack = {}, onFollowup = {}) }
    @Test fun landlord() = shot("10-landlord") { DemoHomeScreen("Benjamin", home(), listOf(done), DemoRole.landlord, {}, {}, {}, {}, {}, {}, {}) }
    @Test fun board() = shot("11-board") { DemoJobsScreen(listOf(job, done.copy(id = 2)), DemoRole.technician, {}, {}) }
}
