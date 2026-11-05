package app.propcare.homeos.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.propcare.homeos.data.JobEntity
import app.propcare.homeos.data.ProfileEntity
import app.propcare.homeos.data.Settings
import app.propcare.homeos.data.StaffUpdate
import app.propcare.homeos.data.refLabel
import app.propcare.homeos.data.statusEnum
import app.propcare.homeos.data.urgencyEnum
import app.propcare.homeos.domain.Company
import app.propcare.homeos.domain.JobStatus
import app.propcare.homeos.ui.components.EmptyState
import app.propcare.homeos.ui.components.JobTicketCard
import app.propcare.homeos.ui.components.ListDivider
import app.propcare.homeos.ui.components.PanelCard
import app.propcare.homeos.ui.components.ScreenScaffold
import app.propcare.homeos.ui.components.ScrollBody
import app.propcare.homeos.ui.components.SectionTitle
import app.propcare.homeos.util.Format
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

// Account

data class AccountState(
    val profile: ProfileEntity?,
    val appLock: Boolean,
    val appLockAvailable: Boolean,
    val staffMode: Boolean,
    val hasStaffPin: Boolean,
    val onlineConfigured: Boolean,
)

data class AccountActions(
    val onSaveProfile: (String, String, String) -> Unit = { _, _, _ -> },
    val onAppLock: (Boolean) -> Unit = {},
    val onSetPin: (String) -> Boolean = { true },
    val onEnterPin: suspend (String) -> Settings.PinResult = { Settings.PinResult.Wrong },
    val onLeaveStaff: () -> Unit = {},
    val onDeleteAll: () -> Unit = {},
    val onServices: () -> Unit = {},
)

@Composable
fun AccountScreen(state: AccountState, actions: AccountActions) {
    var name by rememberSaveable(state.profile) { mutableStateOf(state.profile?.fullName ?: "") }
    var phone by rememberSaveable(state.profile) { mutableStateOf(state.profile?.phone ?: "") }
    var postcode by rememberSaveable(state.profile) { mutableStateOf(state.profile?.postcode ?: "") }
    var pinDialog by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val postcodeError = if (postcode.isNotBlank() && !Format.isUkPostcode(postcode)) "Enter a UK postcode, for example M1 1AE" else null
    val changed = state.profile != null && (name != state.profile.fullName || phone != state.profile.phone || postcode != state.profile.postcode)

    ScreenScaffold(title = "Account", topLevel = true) { padding ->
        ScrollBody(padding) {
            SectionTitle("Your details")
            OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next))
            OutlinedTextField(postcode, { postcode = it }, label = { Text("Postcode") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                isError = postcodeError != null, supportingText = postcodeError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done))
            if (changed) {
                Button(
                    onClick = { actions.onSaveProfile(name, phone, postcode) },
                    enabled = name.isNotBlank() && phone.isNotBlank() && postcodeError == null && postcode.isNotBlank(),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Save details") }
            }

            SectionTitle("Security")
            ListItem(
                headlineContent = { Text("App lock") },
                supportingContent = {
                    Text(if (state.appLockAvailable) "Ask for your fingerprint, face or screen lock when the app opens" else "Set a screen lock on your phone to use this")
                },
                trailingContent = { Switch(checked = state.appLock, onCheckedChange = actions.onAppLock, enabled = state.appLockAvailable) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
            ListDivider()
            ListItem(
                headlineContent = { Text("Staff mode") },
                supportingContent = { Text(if (state.staffMode) "On. The Board tab shows every job." else "For PropCare staff. Needs a PIN.") },
                leadingContent = { Icon(Icons.Filled.Badge, null) },
                trailingContent = {
                    if (state.staffMode) TextButton(onClick = actions.onLeaveStaff) { Text("Turn off") }
                    else TextButton(onClick = { pinDialog = true }) { Text(if (state.hasStaffPin) "Enter PIN" else "Set PIN") }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )

            SectionTitle("AI photo check")
            Text(
                if (state.onlineConfigured) "Photos are checked by Google Gemini through PropCare's server, then the safety rules on this phone. The photo is not stored on the server."
                else "Not set up in this build. Photos are checked on this phone, offline.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("About")
            TextButton(onClick = actions.onServices, modifier = Modifier.heightIn(min = 48.dp)) { Text("PropCare services and example prices") }
            Text(Company.conceptLabel, style = MaterialTheme.typography.bodyMedium)
            Text("All your data stays on this phone. Nothing is backed up to the cloud.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionTitle("Delete data")
            OutlinedButton(
                onClick = { confirmDelete = true }, modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Icon(Icons.Filled.DeleteForever, null); Spacer(Modifier.width(6.dp)); Text("Delete all my data") }
        }
    }

    if (pinDialog) PinDialog(state.hasStaffPin, actions, onDismiss = { pinDialog = false })
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all data?") },
            text = { Text("This deletes your details, properties, jobs, photos and settings from this phone. It cannot be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; actions.onDeleteAll() }) { Text("Delete everything") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep my data") } },
        )
    }
}

@Composable
private fun PinDialog(hasPin: Boolean, actions: AccountActions, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasPin) "Enter staff PIN" else "Set a staff PIN") },
        text = {
            Column {
                if (!hasPin) Text("Choose 4 to 8 digits. Only a scrambled copy is kept on this phone.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    pin, { v -> pin = v.filter { it.isDigit() }.take(8); error = null }, label = { Text("PIN") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), isError = error != null, supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = pin.length >= 4 && !busy, onClick = {
                if (!hasPin) {
                    // Once set, hasPin flips to true and the dialog asks for the PIN to turn staff mode on.
                    if (actions.onSetPin(pin)) { pin = ""; error = null } else error = "Use 4 to 8 digits"
                } else {
                    busy = true
                    scope.launch {
                        when (val r = actions.onEnterPin(pin)) {
                            Settings.PinResult.Ok -> onDismiss()
                            Settings.PinResult.Wrong -> error = "Wrong PIN"
                            is Settings.PinResult.Locked -> error = "Too many tries. Try again in ${r.seconds} seconds."
                        }
                        busy = false; pin = ""
                    }
                }
            }) { Text(if (hasPin) "Unlock staff mode" else "Set PIN") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// Staff board

data class StaffBoardState(val jobs: List<JobEntity>, val propertyNames: Map<Long, String>)

/** All open jobs, most urgent first. Tap one to set status, quote, visit, a message and notes. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StaffBoardScreen(state: StaffBoardState, onUpdate: (Long, StaffUpdate) -> Unit, onOpenJob: (Long) -> Unit) {
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    val open = state.jobs.filter { it.statusEnum.isOpen }.sortedWith(compareBy<JobEntity>({ it.urgencyEnum.ordinal }).thenBy { it.createdAt })
    ScreenScaffold(title = "Job board", topLevel = true) { padding ->
        ScrollBody(padding) {
            Text("Staff view: ${open.size} open jobs, most urgent first.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (open.isEmpty()) EmptyState(Icons.Filled.Handyman, "No open jobs", "New customer reports appear here.")
            open.forEach { j ->
                JobTicketCard(j, state.propertyNames[j.propertyId], onClick = { editing = j.id }, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
    }

    val job = editing?.let { id -> state.jobs.firstOrNull { it.id == id } }
    if (job != null) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { editing = null }, sheetState = sheet) {
            StaffEditor(job, onCancel = { editing = null }, onSave = { u -> onUpdate(job.id, u); editing = null }, onOpenJob = { editing = null; onOpenJob(job.id) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StaffEditor(job: JobEntity, onCancel: () -> Unit, onSave: (StaffUpdate) -> Unit, onOpenJob: () -> Unit) {
    var status by rememberSaveable(job.id) { mutableStateOf(job.statusEnum) }
    var quote by rememberSaveable(job.id) { mutableStateOf(job.quotePence?.let { if (it % 100 == 0) "${it / 100}" else "%.2f".format(it / 100.0) } ?: "") }
    var visitAt by rememberSaveable(job.id) { mutableStateOf(job.visitAt) }
    var message by rememberSaveable(job.id) { mutableStateOf("") }
    var notes by rememberSaveable(job.id) { mutableStateOf(job.staffNotes) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf<LocalDate?>(null) }
    val quotePence = quote.trim().takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()?.let { (it * 100.toBigDecimal()).toInt() }
    val quoteError = if (quote.isNotBlank() && (quotePence == null || quotePence < 0)) "Enter an amount in pounds, for example 95" else null

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
        Text("${job.refLabel}: ${job.title}", style = MaterialTheme.typography.titleLarge)
        if (job.description.isNotBlank()) Text(job.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onOpenJob, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open full job") }

        SectionTitle("Status")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (JobStatus.flow + JobStatus.cancelled).forEach { s -> FilterChip(selected = status == s, onClick = { status = s }, label = { Text(s.label) }, modifier = Modifier.heightIn(min = 48.dp)) }
        }
        SectionTitle("Quote")
        OutlinedTextField(quote, { quote = it }, label = { Text("Quote in £") }, prefix = { Text("£") }, singleLine = true, isError = quoteError != null,
            supportingText = quoteError?.let { { Text(it) } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        SectionTitle("Visit")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(visitAt?.let { Format.dateTime(it) } ?: "Not booked", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = { pickDate = true }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Filled.Event, null); Spacer(Modifier.width(6.dp)); Text("Set visit") }
        }
        SectionTitle("Message to the customer")
        OutlinedTextField(message, { message = it.take(300) }, label = { Text("Shown on their timeline (optional)") }, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        SectionTitle("Internal notes")
        OutlinedTextField(notes, { notes = it.take(1000) }, label = { Text("Staff only") }, minLines = 2, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onSave(StaffUpdate(status = status, quotePence = quotePence, visitAt = visitAt, message = message, staffNotes = notes)) },
                enabled = quoteError == null, modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Save changes") }
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }
    }

    if (pickDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = visitAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() })
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { pickTime = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("Next: time") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(picker) }
    }
    pickTime?.let { day ->
        val time = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = null },
            title = { Text("Visit time") },
            text = { TimePicker(time) },
            confirmButton = {
                TextButton(onClick = {
                    visitAt = day.atTime(time.hour, time.minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    pickTime = null
                }) { Text("Set visit") }
            },
            dismissButton = { TextButton(onClick = { pickTime = null }) { Text("Cancel") } },
        )
    }
}
