package app.propcare.homeos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import app.propcare.homeos.data.ComplianceEntity
import app.propcare.homeos.data.JobEntity
import app.propcare.homeos.data.JobEventEntity
import app.propcare.homeos.data.ProfileEntity
import app.propcare.homeos.data.PropertyEntity
import app.propcare.homeos.data.RoomEntity
import app.propcare.homeos.data.summarise
import app.propcare.homeos.domain.Trade
import app.propcare.homeos.domain.Triage
import app.propcare.homeos.domain.Urgency
import app.propcare.homeos.ui.screens.AccountActions
import app.propcare.homeos.ui.screens.AccountScreen
import app.propcare.homeos.ui.screens.AccountState
import app.propcare.homeos.ui.screens.EmergencyActions
import app.propcare.homeos.ui.screens.EmergencyScreen
import app.propcare.homeos.ui.screens.HomeActions
import app.propcare.homeos.ui.screens.HomeScreen
import app.propcare.homeos.ui.screens.HomeState
import app.propcare.homeos.ui.screens.JobDetailActions
import app.propcare.homeos.ui.screens.JobDetailScreen
import app.propcare.homeos.ui.screens.JobDetailState
import app.propcare.homeos.ui.screens.JobsScreen
import app.propcare.homeos.ui.screens.JobsState
import app.propcare.homeos.ui.screens.OnboardingActions
import app.propcare.homeos.ui.screens.OnboardingScreen
import app.propcare.homeos.ui.screens.PropertiesScreen
import app.propcare.homeos.ui.screens.PropertyDetailActions
import app.propcare.homeos.ui.screens.PropertyDetailScreen
import app.propcare.homeos.ui.screens.PropertyDetailState
import app.propcare.homeos.ui.screens.ReportActions
import app.propcare.homeos.ui.screens.ReportScreen
import app.propcare.homeos.ui.screens.ReportState
import app.propcare.homeos.ui.screens.ReportStep
import app.propcare.homeos.ui.screens.ServicesActions
import app.propcare.homeos.ui.screens.ServicesScreen
import app.propcare.homeos.ui.screens.StaffBoardScreen
import app.propcare.homeos.ui.screens.StaffBoardState
import app.propcare.homeos.ui.theme.PropCareTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/** Renders every screen with sample data. Images go to build/outputs/roborazzi/. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class ScreenshotTest {
    private val today = LocalDate.now()
    private fun daysAgo(d: Long) = today.minusDays(d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val home = PropertyEntity(1, "Flat 2, Chorley Road", "2 Chorley Road", "Bolton", "BL1 4AA", "rental", daysAgo(300))
    private val house = PropertyEntity(2, "Home", "14 Moss Lane", "Wigan", "WN1 2PL", "home", daysAgo(200))
    private val properties = listOf(home, house)
    private val compliance = listOf(
        ComplianceEntity(1, 1, "gas_safety", today.minusMonths(13).toString()),
        ComplianceEntity(2, 1, "eicr", today.minusYears(2).toString()),
        ComplianceEntity(3, 1, "epc", today.minusYears(9).minusMonths(11).plusDays(10).toString()),
        ComplianceEntity(4, 2, "gas_safety", today.minusMonths(3).toString()),
        ComplianceEntity(5, 2, "alarms", today.minusMonths(2).toString()),
    )
    private val triage = Triage(
        issueSummary = "Water dripping from the waste pipe joint under the kitchen sink",
        likelyCauses = listOf("Loose slip nut on the waste trap", "Worn washer in the joint", "Cracked trap"),
        trade = Trade.plumber.name, urgency = Urgency.urgent.name,
        cannotTell = listOf("Whether water has soaked into the cupboard base", "The pipe behind the back panel"),
        customerAdvice = listOf("Put a bowl under the joint.", "Avoid using the sink until it is fixed."),
        confidence = "medium", source = "ai", model = "gemini-2.5-flash-lite", box = listOf(420, 380, 760, 640),
    )
    private val json = Json.encodeToString(Triage.serializer(), triage)
    private val jobs = listOf(
        JobEntity(1, 1001, 1, triage.issueSummary, "Water dripping under the kitchen sink since this morning", Trade.plumber.name, Urgency.urgent.name, "quoted", json, "Key safe by the door, tenant works from home", "Weekday mornings", 14500, null, "", null, null, null, daysAgo(2), daysAgo(1)),
        JobEntity(2, 1002, 1, "Sparking from the hallway socket", "Sparking from the socket in the hallway and a burning smell", Trade.electrician.name, Urgency.emergency.name, "requested", null, "", "", null, null, "", null, null, null, daysAgo(0), daysAgo(0)),
        JobEntity(3, 1003, 2, "Repaint the back bedroom", "Paint peeling on the bedroom wall", Trade.decorator.name, Urgency.routine.name, "done", null, "", "", 32000, null, "", null, null, null, daysAgo(40), daysAgo(20)),
    )
    private val events = listOf(
        JobEventEntity(1, 1, "requested", "", daysAgo(2)),
        JobEventEntity(2, 1, "quoted", "Replace the trap and seal, about 1 hour", daysAgo(1)),
    )
    private val names = properties.associate { it.id to it.name }
    private val summaries = summarise(properties, compliance, jobs, today)

    private fun photo() = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b)
        c.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, 600f, Color.rgb(200, 210, 220), Color.rgb(120, 135, 150), Shader.TileMode.CLAMP) })
        c.drawRect(330f, 0f, 390f, 470f, Paint().apply { color = Color.rgb(176, 183, 189) })
        c.drawOval(335f, 450f, 385f, 560f, Paint().apply { color = Color.argb(190, 80, 150, 210) })
    }.asImageBitmap()

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        captureRoboImage("build/outputs/roborazzi/$name.png") { PropCareTheme(darkTheme = dark) { content() } }

    @Test fun s01_onboarding() = shot("01-onboarding") { OnboardingScreen(notificationsAllowed = false, actions = OnboardingActions()) }

    @Test fun s02_home() = shot("02-home") { HomeScreen(HomeState("Sam", summaries, jobs.filter { it.status != "done" }, names), HomeActions()) }

    @Test fun s03_home_dark() = shot("03-home-dark", dark = true) { HomeScreen(HomeState("Sam", summaries, jobs.filter { it.status != "done" }, names), HomeActions()) }

    @Test fun s04_report_describe() = shot("04-report-describe") {
        ReportScreen(ReportState(step = ReportStep.describe, photo = photo(), description = "Water dripping under the kitchen sink since this morning", properties = properties, propertyId = 1, onlineConfigured = true), ReportActions())
    }

    @Test fun s05_report_result() = shot("05-report-result") {
        ReportScreen(ReportState(step = ReportStep.result, photo = photo(), description = "Water dripping under the kitchen sink", properties = properties, propertyId = 1, triage = triage, onlineConfigured = true, trade = Trade.plumber, urgency = Urgency.urgent), ReportActions())
    }

    @Test fun s06_report_details() = shot("06-report-details") {
        ReportScreen(ReportState(step = ReportStep.details, photo = photo(), properties = properties, propertyId = 1, triage = triage, title = triage.issueSummary, trade = Trade.plumber, urgency = Urgency.urgent, accessNotes = "Key safe by the door", preferredTimes = "Weekday mornings"), ReportActions())
    }

    @Test fun s07_report_saved() = shot("07-report-saved") {
        ReportScreen(ReportState(step = ReportStep.saved, properties = properties, propertyId = 1, triage = triage, title = triage.issueSummary, savedJobRef = "PC-1001"), ReportActions())
    }

    @Test fun s08_emergency() = shot("08-emergency") { EmergencyScreen(EmergencyActions()) }

    @Test fun s09_jobs() = shot("09-jobs") { JobsScreen(JobsState(jobs, names), {}, {}) }

    @Test fun s10_job_detail() = shot("10-job-detail") { JobDetailScreen(JobDetailState(jobs[0], home, events), JobDetailActions()) }

    @Test fun s11_properties() = shot("11-properties") { PropertiesScreen(summaries, {}, {}) }

    @Test fun s12_property_detail() = shot("12-property-detail") {
        PropertyDetailScreen(PropertyDetailState(summaries[0], listOf(RoomEntity(1, 1, "Kitchen"), RoomEntity(2, 1, "Hallway"), RoomEntity(3, 1, "Bathroom")), jobs.filter { it.propertyId == 1L }), PropertyDetailActions())
    }

    @Test fun s13_staff_board() = shot("13-staff-board") { StaffBoardScreen(StaffBoardState(jobs, names), { _, _ -> }, {}) }

    @Test fun s14_account() = shot("14-account") {
        AccountScreen(AccountState(ProfileEntity(1, "Sam Taylor", "07700 900123", "BL1 4AA"), appLock = true, appLockAvailable = true, staffMode = false, hasStaffPin = false, onlineConfigured = true), AccountActions())
    }

    @Test fun s15_services() = shot("15-services") { ServicesScreen(ServicesActions()) }
}
