package app.propcare.homeos.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.propcare.homeos.data.PropertyEntity
import app.propcare.homeos.domain.Company
import app.propcare.homeos.domain.Trade
import app.propcare.homeos.domain.Triage
import app.propcare.homeos.domain.Urgency
import app.propcare.homeos.ui.components.EmptyState
import app.propcare.homeos.ui.components.ErrorState
import app.propcare.homeos.ui.components.OfflineBanner
import app.propcare.homeos.ui.components.PanelCard
import app.propcare.homeos.ui.components.ScreenScaffold
import app.propcare.homeos.ui.components.ScrollBody
import app.propcare.homeos.ui.components.SectionTitle
import app.propcare.homeos.ui.components.TriageCard
import app.propcare.homeos.ui.theme.PropCareTheme
import app.propcare.homeos.util.Format

enum class ReportStep { describe, result, details, saved }

/** Voice languages offered for the spoken description. */
enum class VoiceLanguage(val tag: String, val label: String) { en("en-GB", "English (UK)"), hi("hi-IN", "Hindi"), ta("ta-IN", "Tamil") }

data class ReportState(
    val step: ReportStep = ReportStep.describe,
    val photo: ImageBitmap? = null,
    val description: String = "",
    val voiceLanguage: VoiceLanguage = VoiceLanguage.en,
    val properties: List<PropertyEntity> = emptyList(),
    val propertyId: Long? = null,
    val checking: Boolean = false,
    val loadingPhoto: Boolean = false,
    val triage: Triage? = null,
    val onlineNote: String? = null,
    val onlineConfigured: Boolean = false,
    val title: String = "",
    val trade: Trade = Trade.general,
    val urgency: Urgency = Urgency.routine,
    val accessNotes: String = "",
    val preferredTimes: String = "",
    val saving: Boolean = false,
    val savedJobRef: String? = null,
    val error: String? = null,
)

data class ReportActions(
    val onClose: () -> Unit = {},
    val onBackStep: () -> Unit = {},
    val onTakePhoto: () -> Unit = {},
    val onPickPhoto: () -> Unit = {},
    val onRemovePhoto: () -> Unit = {},
    val onVoice: () -> Unit = {},
    val onVoiceLanguage: (VoiceLanguage) -> Unit = {},
    val onDescription: (String) -> Unit = {},
    val onProperty: (Long) -> Unit = {},
    val onAddProperty: () -> Unit = {},
    val onCheck: () -> Unit = {},
    val onContinue: () -> Unit = {},
    val onTitle: (String) -> Unit = {},
    val onTrade: (Trade) -> Unit = {},
    val onUrgency: (Urgency) -> Unit = {},
    val onAccess: (String) -> Unit = {},
    val onTimes: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onEmergency: () -> Unit = {},
    val onWhatsApp: () -> Unit = {},
    val onEmail: () -> Unit = {},
    val onViewJob: () -> Unit = {},
)

/** Report an issue: photo and words, the check, the job details, then send to PropCare. */
@Composable
fun ReportScreen(state: ReportState, actions: ReportActions) {
    // System Back walks back through the steps before leaving the flow.
    BackHandler(enabled = state.step == ReportStep.result || state.step == ReportStep.details) { actions.onBackStep() }
    val stepNo = when (state.step) { ReportStep.describe -> 1; ReportStep.result -> 2; ReportStep.details -> 3; ReportStep.saved -> 4 }
    ScreenScaffold(
        title = if (state.step == ReportStep.saved) "Job saved" else "Report issue: step $stepNo of 3",
        onBack = if (state.step == ReportStep.result || state.step == ReportStep.details) actions.onBackStep else null,
        actions = { IconButton(onClick = actions.onClose) { Icon(Icons.Filled.Close, contentDescription = "Close report") } },
    ) { padding ->
        if (state.properties.isEmpty()) {
            Column(Modifier.padding(padding)) {
                EmptyState(Icons.Filled.HomeWork, "Add a property first", "We need to know where the issue is.", actionLabel = "Add a property", onAction = actions.onAddProperty)
            }
            return@ScreenScaffold
        }
        ScrollBody(padding) {
            when (state.step) {
                ReportStep.describe -> DescribeStep(state, actions)
                ReportStep.result -> ResultStep(state, actions)
                ReportStep.details -> DetailsStep(state, actions)
                ReportStep.saved -> SavedStep(state, actions)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DescribeStep(state: ReportState, actions: ReportActions) {
    if (!state.onlineConfigured) OfflineBanner("The AI photo check is not set up in this build, so you will get an offline check. Everything else works.")
    SectionTitle("Photo (optional)")
    when {
        state.loadingPhoto -> LinearProgressIndicator(Modifier.fillMaxWidth())
        state.photo != null -> {
            Image(state.photo, "Your photo", Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            TextButton(onClick = actions.onRemovePhoto, modifier = Modifier.heightIn(min = 48.dp)) { Text("Remove photo") }
        }
        else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = actions.onTakePhoto, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Filled.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Take photo") }
            OutlinedButton(onClick = actions.onPickPhoto, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Choose photo") }
        }
    }

    SectionTitle("What is wrong?")
    OutlinedTextField(
        state.description, { actions.onDescription(it.take(500)) },
        label = { Text("Describe the issue") }, placeholder = { Text("For example: water dripping from the kitchen ceiling") },
        supportingText = { Text("${state.description.length}/500") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        VoiceLanguage.entries.forEach { l -> FilterChip(selected = state.voiceLanguage == l, onClick = { actions.onVoiceLanguage(l) }, label = { Text(l.label) }, modifier = Modifier.heightIn(min = 48.dp)) }
    }
    OutlinedButton(onClick = actions.onVoice, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Filled.Mic, null); Spacer(Modifier.width(6.dp)); Text("Speak instead") }

    SectionTitle("Where?")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.properties.forEach { p -> FilterChip(selected = state.propertyId == p.id, onClick = { actions.onProperty(p.id) }, label = { Text(p.name) }, modifier = Modifier.heightIn(min = 48.dp)) }
    }

    state.error?.let { Spacer(Modifier.height(8.dp)); ErrorState(it) }
    Spacer(Modifier.height(16.dp))
    val canCheck = (state.photo != null || state.description.isNotBlank()) && state.propertyId != null && !state.checking
    Button(
        onClick = actions.onCheck, enabled = canCheck, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
    ) {
        if (state.checking) { CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Checking") }
        else Text(if (state.photo != null) "Check photo" else "Check issue")
    }
    if (!canCheck && !state.checking) {
        Text("Add a photo or a description, and choose the property.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ResultStep(state: ReportState, actions: ReportActions) {
    val t = state.triage ?: return
    if (t.urgencyEnum == Urgency.emergency) {
        val s = PropCareTheme.status
        Button(
            onClick = actions.onEmergency, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = s.emergency, contentColor = s.onEmergency),
        ) { Icon(Icons.Filled.Warning, null); Spacer(Modifier.width(8.dp)); Text("This could be dangerous. Open emergency help") }
        Spacer(Modifier.height(12.dp))
    }
    TriageCard(t, state.photo, note = state.onlineNote)
    Format.priceBand(t.tradeEnum)?.let { band ->
        SectionTitle("Price guide")
        PanelCard {
            Text(band, style = MaterialTheme.typography.titleMedium)
            Text(Company.priceGuideNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(16.dp))
    Button(onClick = actions.onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Continue to job details") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsStep(state: ReportState, actions: ReportActions) {
    OutlinedTextField(state.title, { actions.onTitle(it.take(120)) }, label = { Text("Job title") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        isError = state.title.isBlank(), supportingText = { if (state.title.isBlank()) Text("Give the job a short title") },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next))
    SectionTitle("Urgency")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Urgency.entries.forEach { u -> FilterChip(selected = state.urgency == u, onClick = { actions.onUrgency(u) }, label = { Text(u.label) }, modifier = Modifier.heightIn(min = 48.dp)) }
    }
    if (state.triage?.urgencyEnum == Urgency.emergency && state.urgency != Urgency.emergency) {
        Text("The safety rules flagged this as an emergency. PropCare will treat it as one.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    SectionTitle("Trade")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Trade.entries.forEach { tr -> FilterChip(selected = state.trade == tr, onClick = { actions.onTrade(tr) }, label = { Text(tr.label) }, modifier = Modifier.heightIn(min = 48.dp)) }
    }
    SectionTitle("Access and times")
    OutlinedTextField(state.accessNotes, { actions.onAccess(it.take(200)) }, label = { Text("Access notes (optional)") }, placeholder = { Text("Key with neighbour, dog in the garden") }, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next))
    OutlinedTextField(state.preferredTimes, { actions.onTimes(it.take(120)) }, label = { Text("Best times (optional)") }, placeholder = { Text("Weekday mornings") }, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done))
    state.error?.let { Spacer(Modifier.height(8.dp)); ErrorState(it) }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = actions.onSave, enabled = state.title.isNotBlank() && !state.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
    ) { Text(if (state.saving) "Saving" else "Save job") }
}

@Composable
private fun SavedStep(state: ReportState, actions: ReportActions) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(56.dp).width(56.dp))
        Spacer(Modifier.height(8.dp))
        Text("Job ${state.savedJobRef ?: ""} saved", style = MaterialTheme.typography.headlineSmall)
        Text("Now send it to PropCare. It goes as a job sheet with your photo.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = actions.onWhatsApp, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
    ) { Icon(Icons.AutoMirrored.Filled.Chat, null); Spacer(Modifier.width(6.dp)); Text("Send by WhatsApp") }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = actions.onEmail, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Filled.Email, null); Spacer(Modifier.width(6.dp)); Text("Send by email") }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = actions.onViewJob, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("View job") }
}
