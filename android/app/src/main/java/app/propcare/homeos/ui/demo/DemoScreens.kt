@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.propcare.homeos.ui.demo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.propcare.homeos.R
import app.propcare.homeos.data.*
import app.propcare.homeos.domain.*
import app.propcare.homeos.ui.components.StatusTimeline
import app.propcare.homeos.ui.components.rememberPhoto
import app.propcare.homeos.ui.theme.DemoColors
import app.propcare.homeos.util.Format
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class DemoRole(val label: String) { resident("Resident"), landlord("Landlord"), technician("Technician") }
val demoDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)
fun JobEntity.coverage(): RepairCoverage? = if (statusEnum == JobStatus.done && completedAt != null && warrantyDays != null && warrantyDays in 1..730) RepairCoverage(completedAt, warrantyDays) else null

@Composable fun DemoWelcome(onSample: () -> Unit, busy: Boolean) {
    DemoPage("Welcome", brand = true) {
        Spacer(Modifier.height(16.dp))
        DemoTitle("Home. Handled.", "Report a repair. Follow the work. Know what’s covered.")
        Image(painterResource(R.drawable.demo_house), "Illustrative sample house", Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Crop)
        PrimaryAction(if (busy) "Loading sample home…" else "Explore the demo", onSample, enabled = !busy)
        DemoNote("Loads a clearly labelled sample home. No account, server or API key needed.")
        DemoNote(Company.conceptLabel)
    }
}

@Composable fun DemoHomeScreen(name: String, summaries: List<PropertySummary>, jobs: List<JobEntity>, role: DemoRole,
    onRole: (DemoRole) -> Unit, onReport: () -> Unit, onJob: (Long) -> Unit, onProperty: (Long) -> Unit,
    onEmergency: () -> Unit, onPassport: (Long) -> Unit, onJobs: () -> Unit) {
    val summary = summaries.firstOrNull()
    var menu by remember { mutableStateOf(false) }
    DemoPage("Home", topLevel = true, brand = true) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DemoNote("Morning, ${name.substringBefore(' ')}")
                Text(if (role == DemoRole.landlord) "Your properties." else if (role == DemoRole.technician) "Your work." else "Your home.", style = MaterialTheme.typography.headlineLarge)
            }
            Box {
                FilledTonalButton(onClick = { menu = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(role.label); Icon(Icons.Filled.ExpandMore, null, Modifier.size(18.dp)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DemoRole.entries.forEach { item -> DropdownMenuItem(text = { Text(item.label) }, onClick = { menu = false; onRole(item) }) }
                }
            }
        }
        if (summary != null) {
            Box(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(24.dp)).clickable { onProperty(summary.property.id) }) {
                Image(painterResource(R.drawable.demo_house), "Sample house artwork", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(DemoColors.photoScrim.copy(alpha = .12f), DemoColors.photoScrim.copy(alpha = .94f)))))
                Text(summary.property.name, Modifier.align(Alignment.TopStart).padding(18.dp), color = DemoColors.photoText, style = MaterialTheme.typography.titleSmall)
                Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                    Text("HOME HEALTH", color = DemoColors.photoText, style = MaterialTheme.typography.labelSmall)
                    Text("${summary.health.score}/100", style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"), color = DemoColors.photoText)
                    Text(summary.health.tips.firstOrNull()?.text ?: "View checks and repair history", style = MaterialTheme.typography.bodySmall, color = DemoColors.photoText)
                }
            }
            PrimaryAction(if (role == DemoRole.technician) "Open job board" else "Report a repair", if (role == DemoRole.technician) onJobs else onReport, icon = if (role == DemoRole.technician) Icons.Filled.Handyman else Icons.Filled.PhotoCamera)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(Triple(Icons.Filled.VerifiedUser, "Checks", { onProperty(summary.property.id) }),
                    Triple(Icons.Filled.Handyman, "Repairs", onJobs), Triple(Icons.Filled.Description, "Passport", { onPassport(summary.property.id) })).forEach { (icon, title, action) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FilledTonalIconButton(onClick = action, modifier = Modifier.size(52.dp)) { Icon(icon, title) }
                        Text(title, style = MaterialTheme.typography.labelMedium)
                    }
            }
        } else PrimaryAction("Report a repair", onReport)
        Text(if (role == DemoRole.technician) "Your jobs" else "Your repairs", style = MaterialTheme.typography.titleMedium)
        jobs.take(3).forEach { job ->
            InfoRow(Icons.Filled.Handyman, job.title, "${job.refLabel} · ${job.statusEnum.label}", { onJob(job.id) })
        }
        if (jobs.isEmpty()) DemoNote("No repairs yet. Report an issue to get started.")
        OutlinedButton(onClick = onEmergency, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Filled.Warning, null); Spacer(Modifier.width(8.dp)); Text("Emergency help") }
        DemoNote("Demonstration data · technician profiles are examples.")
    }
}

data class DemoReportActions(
    val back: () -> Unit = {}, val close: () -> Unit = {}, val camera: () -> Unit = {}, val gallery: () -> Unit = {}, val sample: () -> Unit = {},
    val description: (String) -> Unit = {}, val property: (Long) -> Unit = {}, val voice: () -> Unit = {},
    val language: (String) -> Unit = {}, val check: () -> Unit = {}, val next: (DemoStep) -> Unit = {}, val choose: (String) -> Unit = {},
    val access: (String) -> Unit = {}, val title: (String) -> Unit = {}, val save: () -> Unit = {}, val emergency: () -> Unit = {},
)

@Composable fun DemoReportScreen(state: DemoReportState, properties: List<PropertyEntity>, language: String, actions: DemoReportActions) {
    val triage = state.triage
    val emergency = triage?.urgencyEnum == Urgency.emergency
    val label = when (state.step) {
        DemoStep.describe -> if (state.checking) "Checking your report…" else "Check this issue"
        DemoStep.assessment -> if (emergency) "Open emergency help" else "Find suitable help"
        DemoStep.matching -> if (state.technicianId != null) "View technician" else "Continue to repair brief"
        DemoStep.technician -> "Prepare repair brief"
        DemoStep.brief -> if (state.saving) "Saving repair…" else "Save repair brief"
        DemoStep.saved -> "Repair saved"
    }
    val nextAction = {
        when (state.step) {
            DemoStep.describe -> actions.check()
            DemoStep.assessment -> if (emergency) actions.emergency() else actions.next(DemoStep.matching)
            DemoStep.matching -> actions.next(if (state.technicianId != null) DemoStep.technician else DemoStep.brief)
            DemoStep.technician -> actions.next(DemoStep.brief)
            DemoStep.brief -> actions.save()
            DemoStep.saved -> actions.close()
        }
    }
    DemoPage(when (state.step) {
        DemoStep.describe -> "Report a repair"; DemoStep.assessment -> "Your assessment"; DemoStep.matching -> "Finding help"
        DemoStep.technician -> "Suggested technician"; else -> "Repair brief"
    }, back = if (state.step == DemoStep.describe) actions.close else actions.back,
        footer = { ActionFooter(if (state.sample) "Sample AI assessment · not a live diagnosis" else "Photos are checked after capture. A technician confirms.") {
            PrimaryAction(label, nextAction, enabled = !state.loading && !state.checking && !state.saving &&
                (state.step != DemoStep.describe || (properties.isNotEmpty() && (state.photo != null || state.description.isNotBlank()))))
        } }) {
        ErrorLine(state.error)
        if (state.loading || state.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
        AnimatedContent(targetState = state.step, label = "Report step") { step ->
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (step) {
                    DemoStep.describe -> {
                        DemoTitle("Show us the issue.")
                        if (properties.isEmpty()) DemoNote("Load the sample home from Home before reporting a repair.")
                        if (properties.size == 1) DemoNote(properties.first().name) else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            properties.forEach { p -> FilterChip(selected = state.propertyId == p.id, onClick = { actions.property(p.id) }, label = { Text(p.name) }, modifier = Modifier.heightIn(min = 48.dp)) }
                        }
                        DemoPhoto(state.photo)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = actions.camera, enabled = !state.loading) { Icon(Icons.Filled.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text("Take photo") }
                            OutlinedButton(onClick = actions.gallery, enabled = !state.loading) { Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Choose photo") }
                        }
                        OutlinedTextField(state.description, actions.description, label = { Text("What’s happening?") }, modifier = Modifier.fillMaxWidth(), minLines = 3,
                            supportingText = { Text("${state.description.length}/500") })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("en-GB" to "English", "hi-IN" to "Hindi", "ta-IN" to "Tamil").forEach { (tag, name) ->
                                FilterChip(selected = language == tag, onClick = { actions.language(tag) }, label = { Text(name) }, modifier = Modifier.heightIn(min = 48.dp))
                            }
                        }
                        FilledTonalButton(onClick = actions.voice) { Icon(Icons.Filled.Mic, null); Spacer(Modifier.width(8.dp)); Text("Speak instead") }
                        TextButton(onClick = actions.sample, enabled = !state.loading) { Text("Try the sample tap photo") }
                        if (state.sample) DemoNote("Sample photo and assessment. Take or choose a photo for real on-device analysis.")
                        else DemoNote("No server needed: on-device labels and your description guide the offline check.")
                    }
                    DemoStep.assessment -> if (triage != null) {
                        DemoTitle("A clearer next step.")
                        Text("${triage.tradeEnum.label} · ${triage.urgencyEnum.label}", style = MaterialTheme.typography.titleSmall,
                            color = if (emergency) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        DemoPhoto(state.photo, triage)
                        Text(triage.issueSummary, style = MaterialTheme.typography.titleLarge)
                        DemoNote(when (triage.source) { "ai" -> "AI first look · a technician confirms"; "demo" -> "Sample assessment and example highlight"; else -> "On-device/offline check · not a diagnosis" })
                        if (!triage.isPropertyIssue) DemoNote("This may not show a property issue. Retake the photo or add more detail.")
                        if (emergency) DemoPanel { Text("Safety first", color = MaterialTheme.colorScheme.error); triage.customerAdvice.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) } }
                        else triage.customerAdvice.firstOrNull()?.let { DemoPanel { Text("For now", style = MaterialTheme.typography.titleSmall); Text(it) } }
                        var expanded by rememberSaveable { mutableStateOf(false) }
                        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide assessment details" else "See assessment details") }
                        if (expanded) { (triage.likelyCauses + triage.cannotTell).forEach { DemoNote(it) }; if (triage.labels.isNotEmpty()) DemoNote("Image labels: ${triage.labels.joinToString()}") }
                        state.onlineNote?.let { DemoNote("Online check unavailable; offline check used.") }
                        if (emergency) TextButton(onClick = { actions.next(DemoStep.brief) }) { Text("Save emergency report") }
                    }
                    DemoStep.matching -> {
                        DemoTitle("The right trade.\nThe next step.")
                        Image(painterResource(R.drawable.brand_logo), "Prop Care logo", Modifier.size(128.dp).align(Alignment.CenterHorizontally))
                        DemoPanel { Text("${triage?.tradeEnum?.label ?: "General maintenance"} identified", style = MaterialTheme.typography.titleMedium) }
                        val people = DemoDirectory.matching(triage?.tradeEnum ?: Trade.general)
                        if (people.isEmpty()) DemoNote("No matching sample technician. Send the brief to PropCare to arrange the right trade.")
                        people.forEach { tech ->
                            FilterChip(selected = state.technicianId == tech.id, onClick = { actions.choose(tech.id) }, label = { Text("${tech.name} · ${tech.trade.label}") }, modifier = Modifier.heightIn(min = 56.dp).fillMaxWidth())
                        }
                        DemoNote("Illustrative directory. No availability, credentials or assignment confirmed.")
                    }
                    DemoStep.technician -> DemoDirectory.find(state.technicianId)?.let { TechnicianBody(it) }
                    DemoStep.brief, DemoStep.saved -> {
                        DemoTitle("Ready to send.")
                        DemoPhoto(state.photo)
                        OutlinedTextField(state.title, actions.title, label = { Text("Repair title") }, modifier = Modifier.fillMaxWidth())
                        DemoPanel {
                            Text(triage?.tradeEnum?.label.orEmpty() + " · " + triage?.urgencyEnum?.label.orEmpty(), style = MaterialTheme.typography.titleSmall)
                            Text(state.description, style = MaterialTheme.typography.bodyMedium)
                            DemoNote(properties.firstOrNull { it.id == state.propertyId }?.name ?: "Sample rental")
                            DemoDirectory.find(state.technicianId)?.let { DemoNote("Suggested expert: ${it.name} (demo, not assigned)") }
                            if (state.parentJobId != null) DemoNote("Follow-up linked to the original repair. Its warranty stays unchanged.")
                        }
                        OutlinedTextField(state.access, actions.access, label = { Text("Access and preferred times") }, modifier = Modifier.fillMaxWidth())
                        DemoNote("After saving, share the brief through WhatsApp or email. You confirm sending in that app.")
                    }
                }
            }
        }
    }
}

@Composable fun TechnicianBody(technician: DemoTechnician) {
    if (technician.id == "jamie") Image(painterResource(R.drawable.demo_technician), "Stock photo illustrating a technician", Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(22.dp)), contentScale = ContentScale.Crop)
    else Box(Modifier.fillMaxWidth().height(160.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) { Text(technician.initials, style = MaterialTheme.typography.displayMedium) }
    DemoTitle(technician.name, technician.trade.label)
    Text(technician.specialties, style = MaterialTheme.typography.bodyMedium)
    InfoRow(Icons.Filled.VerifiedUser, "Matches this trade", "Example profile, not verified or assigned")
    InfoRow(Icons.Filled.Event, "Arrange a visit", "PropCare confirms availability")
    DemoNote("Demonstration profile and stock imagery. No real technician has been contacted.")
}

@Composable fun DemoJobsScreen(jobs: List<JobEntity>, role: DemoRole, onJob: (Long) -> Unit, onReport: () -> Unit) {
    DemoPage("Repairs", topLevel = true) {
        DemoTitle(if (role == DemoRole.technician) "Today’s work." else "Your repairs.")
        PrimaryAction("Report a repair", onReport, icon = Icons.Filled.PhotoCamera)
        val ordered = if (role == DemoRole.technician) jobs.sortedWith(compareBy<JobEntity> { if (it.statusEnum.isOpen) 0 else 1 }.thenBy { it.urgencyEnum.ordinal }) else jobs
        ordered.forEach { job ->
            InfoRow(Icons.Filled.Handyman, job.title, "${job.refLabel} · ${job.statusEnum.label}" +
                (job.coverage()?.let { " · ${it.remaining()} warranty days left" } ?: ""), { onJob(job.id) })
        }
        if (jobs.isEmpty()) DemoNote("No repairs yet. Start with a photo or a few words.")
        if (role == DemoRole.technician) DemoNote("Demo job board. Changes are stored locally on this device.")
    }
}

@Composable fun DemoJobScreen(job: JobEntity, property: PropertyEntity?, events: List<JobEventEntity>, role: DemoRole,
    onBack: () -> Unit, onShare: (Boolean) -> Unit, onComplete: () -> Unit, onWarranty: () -> Unit, onStatus: (JobStatus) -> Unit, onFollowup: () -> Unit) {
    DemoPage("Your repair", back = onBack, footer = { ActionFooter { PrimaryAction("Send via WhatsApp", { onShare(true) }, icon = Icons.Filled.Share) } }) {
        DemoTitle(job.title, "${job.refLabel} · ${property?.name.orEmpty()}")
        Text(job.statusEnum.label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
        val photo = rememberPhoto(job.photoPath)
        DemoPhoto(photo, job.triage())
        DemoPanel {
            Text(job.description)
            if (job.accessNotes.isNotBlank()) DemoNote("Access: ${job.accessNotes}")
            DemoDirectory.find(job.technicianId)?.let { DemoNote("Suggested technician: ${it.name} · demo, not an assignment") }
            if (job.parentJobId != null) DemoNote("Follow-up to repair #${job.parentJobId}")
        }
        StatusTimeline(job.statusEnum, events)
        job.coverage()?.let { coverage -> InfoRow(Icons.Filled.VerifiedUser, if (coverage.active()) "Warranty active" else "Warranty ended", "${coverage.remaining()} days remaining · Example period", onWarranty) }
        OutlinedButton(onClick = { onShare(false) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Send by email") }
        if (role == DemoRole.technician) {
            Text("Demo staff controls", style = MaterialTheme.typography.titleMedium)
            if (job.statusEnum != JobStatus.done && job.statusEnum != JobStatus.cancelled) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(JobStatus.quoted, JobStatus.booked, JobStatus.in_progress).forEach { status ->
                        FilterChip(selected = status == job.statusEnum, onClick = { onStatus(status) }, label = { Text(status.label) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                PrimaryAction("Complete repair", onComplete, icon = Icons.Filled.Check)
            }
        } else if (job.statusEnum.isOpen) DemoNote("Use the Technician demo role to update the job and demonstrate completion.")
        if (job.statusEnum == JobStatus.done) TextButton(onClick = onFollowup) { Text("Report a problem again") }
        DemoNote("Sharing opens another app; it does not confirm delivery or a booking.")
    }
}

@Composable fun DemoCompletionScreen(job: JobEntity, onBack: () -> Unit, onComplete: (Int, String) -> Unit, busy: Boolean, error: String?) {
    var period by rememberSaveable(job.id) { mutableStateOf("90") }
    var note by rememberSaveable(job.id) { mutableStateOf("Repair completed and checked.") }
    val days = period.toIntOrNull()
    DemoPage("Complete repair", back = onBack, footer = { ActionFooter("Example warranty period. PropCare confirms actual terms.") {
        PrimaryAction(if (busy) "Saving completion…" else "Mark completed", { if (days != null) onComplete(days, note) }, enabled = !busy && days != null && days in 1..730, icon = Icons.Filled.Check)
    } }) {
        DemoTitle("Repair finished?", "Record the work, then start the warranty.")
        InfoRow(Icons.Filled.Handyman, job.title, job.refLabel)
        OutlinedTextField(note, { note = it.take(500) }, label = { Text("Work completed") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        DemoNote("Completion date: ${LocalDate.now().format(demoDate)}")
        Text("Warranty period", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 90, 180).forEach { n -> FilterChip(selected = period == "$n", onClick = { period = "$n" }, label = { Text("$n days") }, modifier = Modifier.heightIn(min = 48.dp)) }
        }
        OutlinedTextField(period, { period = it.filter(Char::isDigit).take(3) }, label = { Text("Days (1–730)") }, isError = days == null || days !in 1..730,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        job.completedAt?.let { DemoNote("This repair already has an original completion date. Its warranty will not restart.") }
            ?: DemoNote("The countdown begins only when you mark this repair completed.")
        ErrorLine(error)
    }
}

@Composable fun DemoWarrantyScreen(job: JobEntity, today: LocalDate = LocalDate.now(), onBack: () -> Unit, onFollowup: () -> Unit) {
    val coverage = job.coverage()
    DemoPage("Repair warranty", back = onBack, footer = { ActionFooter("Example coverage period. PropCare confirms terms.") {
        PrimaryAction(if (coverage?.active(today) == true) "Report a problem again" else "Request another repair", onFollowup, icon = Icons.Filled.Replay)
    } }) {
        val status = if (coverage == null) "No active warranty" else if (coverage.active(today)) "Active coverage" else "Warranty ended"
        InfoRow(Icons.Filled.VerifiedUser, status, job.refLabel)
        DemoTitle("Repair protection.")
        if (coverage != null) {
            val remaining = coverage.remaining(today)
            val target = remaining.toFloat() / coverage.days
            val progress by animateFloatAsState(target, label = "Warranty coverage")
            val track = MaterialTheme.colorScheme.surfaceContainerHighest
            val color = MaterialTheme.colorScheme.tertiary
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(200.dp).semantics { contentDescription = "$remaining days of example coverage remaining" }) {
                    val stroke = 7.dp.toPx()
                    drawCircle(track, style = Stroke(stroke)); drawArc(color, -90f, progress * 360f, false, style = Stroke(stroke))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$remaining", style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"))
                    DemoNote("days remaining")
                }
            }
            Text("Coverage ends ${coverage.expires().format(demoDate)}", style = MaterialTheme.typography.titleMedium)
            DemoPanel {
                DemoNote("Completed ${coverage.start().format(demoDate)}")
                Text("${coverage.days}-day example period", style = MaterialTheme.typography.titleSmall)
            }
        } else DemoNote("Warranty begins after completion with a selected period. Older repairs may have no recorded coverage.")
        InfoRow(Icons.Filled.Handyman, job.title, "Original repair · ${job.refLabel}")
        var terms by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { terms = !terms }) { Text(if (terms) "Hide warranty terms" else "View warranty terms") }
        if (terms) DemoNote("This is an example demonstration period, not PropCare’s published policy. Coverage and exclusions require confirmation. Reopening or reporting a follow-up does not reset the original start date.")
    }
}
