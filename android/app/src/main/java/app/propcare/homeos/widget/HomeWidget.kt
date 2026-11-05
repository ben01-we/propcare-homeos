package app.propcare.homeos.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.propcare.homeos.MainActivity
import app.propcare.homeos.data.AppDb
import app.propcare.homeos.data.statusEnum
import app.propcare.homeos.data.summarise
import app.propcare.homeos.domain.CheckState
import app.propcare.homeos.util.Format

/** Home-screen widget: open jobs and the next safety check that needs action. Tapping opens the app. */
class HomeWidget : GlanceAppWidget() {

    data class Snapshot(val hasHome: Boolean, val openJobs: Int, val nextCheck: String?)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = load(context)
        provideContent { GlanceTheme { Content(snap) } }
    }

    @Composable
    private fun Content(s: Snapshot) {
        val colors = GlanceTheme.colors
        Column(
            modifier = GlanceModifier.fillMaxSize().background(colors.widgetBackground).cornerRadius(16.dp).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text("PropCare", style = TextStyle(color = colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(6.dp))
            if (!s.hasHome) {
                Text("Add your home in the app to see jobs and checks here.", style = TextStyle(color = colors.onSurface, fontSize = 14.sp))
                return@Column
            }
            Text(
                when (s.openJobs) { 0 -> "No open jobs"; 1 -> "1 open job"; else -> "${s.openJobs} open jobs" },
                style = TextStyle(color = colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(s.nextCheck ?: "All safety checks are up to date", style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp), maxLines = 3)
        }
    }

    companion object {
        suspend fun load(context: Context): Snapshot {
            val dao = AppDb.get(context).dao()
            val properties = dao.propertiesNow()
            if (properties.isEmpty()) return Snapshot(false, 0, null)
            val jobs = dao.jobsNow()
            val summaries = summarise(properties, dao.complianceNow(), jobs)
            // Overdue first, then missing, then the soonest due.
            val order = mapOf(CheckState.overdue to 0, CheckState.missing to 1, CheckState.due_soon to 2)
            val next = summaries.flatMap { s -> s.checks.filter { it.state != CheckState.ok }.map { s.property.name to it } }
                .minWithOrNull(compareBy({ order[it.second.state] ?: 3 }, { it.second.daysLeft ?: 0 }))
            val text = next?.let { (home, c) -> "${c.rule.name}, $home: ${Format.checkState(c).replaceFirstChar { it.lowercase() }}" }
            return Snapshot(true, jobs.count { it.statusEnum.isOpen }, text)
        }

        /** Called after every data change. Never fails the caller. */
        suspend fun refresh(context: Context) {
            runCatching { HomeWidget().updateAll(context) }
        }
    }
}

class HomeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HomeWidget()
}
