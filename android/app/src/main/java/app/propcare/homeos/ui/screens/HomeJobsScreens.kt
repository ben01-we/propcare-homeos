package app.propcare.homeos.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.propcare.homeos.data.JobEntity
import app.propcare.homeos.data.JobEventEntity
import app.propcare.homeos.data.PropertyEntity
import app.propcare.homeos.data.PropertySummary
import app.propcare.homeos.data.canCancel
import app.propcare.homeos.data.refLabel
import app.propcare.homeos.data.statusEnum
import app.propcare.homeos.data.tradeEnum
import app.propcare.homeos.data.triage
import app.propcare.homeos.data.urgencyEnum
import app.propcare.homeos.domain.CheckState
import app.propcare.homeos.domain.Company
import app.propcare.homeos.domain.Urgency
import app.propcare.homeos.ui.components.CheckStatePill
import app.propcare.homeos.ui.components.EmptyState
import app.propcare.homeos.ui.components.HealthRing
import app.propcare.homeos.ui.components.JobTicketCard
import app.propcare.homeos.ui.components.PanelCard
import app.propcare.homeos.ui.components.PhotoWithBox
import app.propcare.homeos.ui.components.ScreenScaffold
import app.propcare.homeos.ui.components.ScrollBody
import app.propcare.homeos.ui.components.SectionTitle
import app.propcare.homeos.ui.components.StatusTimeline
import app.propcare.homeos.ui.components.TriageCard
import app.propcare.homeos.ui.components.UrgencyPill
import app.propcare.homeos.ui.components.rememberPhoto
import app.propcare.homeos.ui.theme.PropCareTheme
import app.propcare.homeos.util.Format

// Home

data class HomeState(
    val firstName: String,
    val summaries: List<PropertySummary>,
    val openJobs: List<JobEntity>,
    val propertyNames: Map<Long, String>,
)

data class HomeActions(
    val onEmergency: () -> Unit = {},
    val onOpenJob: (Long) -> Unit = {},
    val onOpenProperty: (Long) -> Unit = {},
    val onAddProperty: () -> Unit = {},
    val onBookCheck: (propertyId: Long, kind: String) -> Unit = { _, _ -> },
    val onLoadSample: () -> Unit = {},
    val onServices: () -> Unit = {},
)

@Composable
fun HomeScreen(state: HomeState, actions: HomeActions) {
    ScreenScaffold(title = if (state.firstName.isBlank()) "Home" else "Hello, ${state.firstName}", topLevel = true) { padding ->
        ScrollBody(padding) {
            EmergencyStrip(actions.onEmergency)

            if (state.summaries.isEmpty()) {
                EmptyState(
                    Icons.Filled.HomeWork, "Add your home",
                    "Add a property to track its safety checks and Home Health Score.",
                    actionLabel = "Add a property", onAction = actions.onAddProperty,
                )
                TextButton(onClick = actions.onLoadSample, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Load a sample home instead") }
            }

            state.summaries.forEach { s -> HealthPanel(s, actions) }

            SectionTitle("Open jobs")
            if (state.openJobs.isEmpty()) {
                Text("No open jobs. Use Report issue when something needs fixing.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.openJobs.take(5).forEach { j ->
                JobTicketCard(j, state.propertyNames[j.propertyId], onClick = { actions.onOpenJob(j.id) }, modifier = Modifier.padding(bottom = 8.dp))
            }

            val due = state.summaries.flatMap { s -> s.checks.filter { it.state != CheckState.ok }.map { s to it } }
            if (due.isNotEmpty()) {
                SectionTitle("Safety checks to sort")
                due.forEach { (s, c) ->
                    PanelCard(Modifier.padding(bottom = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.rule.name, style = MaterialTheme.typography.titleSmall)
                                Text("${s.property.name}. ${Format.checkState(c)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            CheckStatePill(c.state)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { actions.onBookCheck(s.property.id, c.rule.kind) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Book check") }
                            TextButton(onClick = { actions.onOpenProperty(s.property.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add a date") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = actions.onServices) { Text("See PropCare services and example prices") }
        }
    }
}

@Composable
private fun EmergencyStrip(onClick: () -> Unit) {
    val s = PropCareTheme.status
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).semantics { role = Role.Button },
        colors = CardDefaults.cardColors(containerColor = s.emergency, contentColor = s.onEmergency),
    ) {
        Row(Modifier.padding(16.dp).heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Gas smell, sparks or flooding?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Open emergency help", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HealthPanel(s: PropertySummary, actions: HomeActions) {
    Card(
        onClick = { actions.onOpenProperty(s.property.id) },
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            HealthRing(s.health.score, s.health.band)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(s.property.name, style = MaterialTheme.typography.titleMedium)
                Text(s.health.band, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                s.health.tips.take(2).forEach { tip ->
                    Text("• ${tip.text} (+${tip.points})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

// Jobs

data class JobsState(val jobs: List<JobEntity>, val propertyNames: Map<Long, String>)

@Composable
fun JobsScreen(state: JobsState, onOpenJob: (Long) -> Unit, onReport: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val shown = state.jobs.filter { if (tab == 0) it.statusEnum.isOpen else !it.statusEnum.isOpen }
        .sortedWith(compareBy<JobEntity>({ if (tab == 0) it.urgencyEnum.ordinal else 0 }).thenByDescending { it.createdAt })
    ScreenScaffold(title = "Jobs", topLevel = true) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Open (${state.jobs.count { it.statusEnum.isOpen }})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Closed") })
            }
            if (shown.isEmpty()) {
                EmptyState(
                    Icons.Filled.Handyman,
                    if (tab == 0) "No open jobs" else "No closed jobs yet",
                    if (tab == 0) "When something needs fixing, take a photo and we will check it for you." else "Finished and cancelled jobs appear here.",
                    actionLabel = if (tab == 0) "Report issue" else null, onAction = onReport,
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(shown, key = { it.id }) { j ->
                        JobTicketCard(j, state.propertyNames[j.propertyId], onClick = { onOpenJob(j.id) }, modifier = Modifier.widthIn(max = 720.dp))
                    }
                }
            }
        }
    }
}

// Job detail

data class JobDetailState(val job: JobEntity?, val property: PropertyEntity?, val events: List<JobEventEntity>)

data class JobDetailActions(
    val onBack: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onWhatsApp: () -> Unit = {},
    val onEmail: () -> Unit = {},
    val onCall: () -> Unit = {},
    val onEmergency: () -> Unit = {},
)

@Composable
fun JobDetailScreen(state: JobDetailState, actions: JobDetailActions) {
    val job = state.job
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(title = job?.refLabel ?: "Job", onBack = actions.onBack) { padding ->
        if (job == null) {
            Column(Modifier.padding(padding)) { EmptyState(Icons.Filled.Handyman, "Job not found", "It may have been deleted.", actionLabel = "Back", onAction = actions.onBack) }
            return@ScreenScaffold
        }
        val photo = rememberPhoto(job.photoPath)
        val after = rememberPhoto(job.afterPhotoPath)
        val triage = job.triage()
        ScrollBody(padding) {
            Text(job.title, style = MaterialTheme.typography.headlineSmall)
            state.property?.let { Text("${it.name}, ${it.postcode}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { UrgencyPill(job.urgencyEnum); Text(job.tradeEnum.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.CenterVertically)) }

            if (job.urgencyEnum == Urgency.emergency && job.statusEnum.isOpen) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = actions.onEmergency, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PropCareTheme.status.emergency, contentColor = PropCareTheme.status.onEmergency),
                ) { Icon(Icons.Filled.Warning, null); Spacer(Modifier.width(8.dp)); Text("Open emergency help") }
            }

            if (job.quotePence != null) {
                SectionTitle("Quote")
                PanelCard {
                    Text(Format.pounds(job.quotePence), style = MaterialTheme.typography.headlineMedium)
                    Text("Quoted by PropCare. Call or message to accept it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (job.visitAt != null) {
                SectionTitle("Visit")
                Text(Format.dateTime(job.visitAt), style = MaterialTheme.typography.titleMedium)
            }

            SectionTitle("Progress")
            StatusTimeline(job.statusEnum, state.events)

            SectionTitle("Send to PropCare")
            Text(
                if (job.sentAt != null) "Sent ${Format.dateTime(job.sentAt)}. You can send it again." else "PropCare has not received this yet. Send it by WhatsApp or email.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = actions.onWhatsApp, modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
                ) { Icon(Icons.AutoMirrored.Filled.Chat, null); Spacer(Modifier.width(6.dp)); Text("Send by WhatsApp") }
                OutlinedButton(onClick = actions.onEmail, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Filled.Email, null); Spacer(Modifier.width(6.dp)); Text("Email") }
            }
            TextButton(onClick = actions.onCall, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Filled.Call, null); Spacer(Modifier.width(6.dp)); Text("Call PropCare, ${Company.phoneDisplay}") }

            if (job.description.isNotBlank()) {
                SectionTitle("Description")
                Text(job.description, style = MaterialTheme.typography.bodyLarge)
            }
            if (job.accessNotes.isNotBlank() || job.preferredTimes.isNotBlank()) {
                SectionTitle("Access")
                if (job.accessNotes.isNotBlank()) Text(job.accessNotes, style = MaterialTheme.typography.bodyMedium)
                if (job.preferredTimes.isNotBlank()) Text("Best times: ${job.preferredTimes}", style = MaterialTheme.typography.bodyMedium)
            }
            if (triage != null) {
                SectionTitle("Photo check")
                TriageCard(triage, photo)
            } else if (photo != null) {
                SectionTitle("Photo")
                PhotoWithBox(photo, null)
            }
            if (after != null) {
                SectionTitle("After the repair")
                PhotoWithBox(after, null)
            }
            if (job.canCancel) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { confirmCancel = true }, modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Icon(Icons.Filled.Cancel, null); Spacer(Modifier.width(6.dp)); Text("Cancel job") }
            }
        }
        if (confirmCancel) {
            AlertDialog(
                onDismissRequest = { confirmCancel = false },
                title = { Text("Cancel ${job.refLabel}?") },
                text = { Text("PropCare will see that you cancelled it. You can report it again later.") },
                confirmButton = { TextButton(onClick = { confirmCancel = false; actions.onCancel() }) { Text("Cancel job") } },
                dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep job") } },
            )
        }
    }
}
