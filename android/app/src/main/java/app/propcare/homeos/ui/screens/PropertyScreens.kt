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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.propcare.homeos.data.JobEntity
import app.propcare.homeos.data.PropertyEntity
import app.propcare.homeos.data.PropertySummary
import app.propcare.homeos.data.RoomEntity
import app.propcare.homeos.domain.CheckStatus
import app.propcare.homeos.domain.Company
import app.propcare.homeos.domain.PropertyType
import app.propcare.homeos.ui.components.CheckStatePill
import app.propcare.homeos.ui.components.EmptyState
import app.propcare.homeos.ui.components.HealthRing
import app.propcare.homeos.ui.components.JobTicketCard
import app.propcare.homeos.ui.components.ListDivider
import app.propcare.homeos.ui.components.PanelCard
import app.propcare.homeos.ui.components.ScreenScaffold
import app.propcare.homeos.ui.components.ScrollBody
import app.propcare.homeos.ui.components.SectionTitle
import app.propcare.homeos.util.Format
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Properties list

@Composable
fun PropertiesScreen(summaries: List<PropertySummary>, onOpen: (Long) -> Unit, onAdd: () -> Unit) {
    ScreenScaffold(
        title = "Properties", topLevel = true,
        actions = { IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Add a property") } },
    ) { padding ->
        ScrollBody(padding) {
            if (summaries.isEmpty()) {
                EmptyState(Icons.Filled.HomeWork, "No properties yet", "Add your home or a rental to track its safety checks and repairs.", actionLabel = "Add a property", onAction = onAdd)
            }
            summaries.forEach { s ->
                Card(
                    onClick = { onOpen(s.property.id) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        HealthRing(s.health.score, s.health.band, size = 64.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.property.name, style = MaterialTheme.typography.titleMedium)
                            Text("${s.property.addressLine}, ${s.property.postcode}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${s.type.label}. ${s.openJobs.size} open jobs.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (summaries.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Add a property") }
            }
        }
    }
}

// Property detail

data class PropertyDetailState(val summary: PropertySummary?, val rooms: List<RoomEntity>, val jobs: List<JobEntity>)

data class PropertyDetailActions(
    val onBack: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onSetDate: (kind: String, date: LocalDate?) -> Unit = { _, _ -> },
    val onBook: (kind: String) -> Unit = {},
    val onAddRoom: (String) -> Unit = {},
    val onRenameRoom: (RoomEntity, String) -> Unit = { _, _ -> },
    val onDeleteRoom: (Long) -> Unit = {},
    val onExport: () -> Unit = {},
    val onOpenJob: (Long) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PropertyDetailScreen(state: PropertyDetailState, actions: PropertyDetailActions) {
    val s = state.summary
    var dateFor by rememberSaveable { mutableStateOf<String?>(null) }
    var roomDialog by rememberSaveable { mutableStateOf<Long?>(null) } // 0 = new room
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(
        title = s?.property?.name ?: "Property", onBack = actions.onBack,
        actions = {
            if (s != null) {
                IconButton(onClick = actions.onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit property") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete property") }
            }
        },
    ) { padding ->
        if (s == null) {
            Column(Modifier.padding(padding)) { EmptyState(Icons.Filled.HomeWork, "Property not found", "It may have been deleted.", actionLabel = "Back", onAction = actions.onBack) }
            return@ScreenScaffold
        }
        ScrollBody(padding) {
            Text(listOf(s.property.addressLine, s.property.town, s.property.postcode).filter { it.isNotBlank() }.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
            Text(s.type.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionTitle("Home Health Score")
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HealthRing(s.health.score, s.health.band)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.health.band, style = MaterialTheme.typography.titleMedium)
                        if (s.health.tips.isEmpty()) Text("Nothing to sort right now.", style = MaterialTheme.typography.bodyMedium)
                        s.health.tips.forEach { Text("• ${it.text} (+${it.points})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
                    }
                }
            }

            SectionTitle(if (s.type == PropertyType.rental) "Landlord safety checks (England)" else "Safety checks")
            s.checks.forEach { c -> CheckCard(c, onDate = { dateFor = c.rule.kind }, onBook = { actions.onBook(c.rule.kind) }) }

            SectionTitle("Rooms")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.rooms.forEach { r -> FilterChip(selected = false, onClick = { roomDialog = r.id }, label = { Text(r.name) }, trailingIcon = { Icon(Icons.Filled.Edit, contentDescription = "Edit ${r.name}") }) }
                FilterChip(selected = false, onClick = { roomDialog = 0L }, label = { Text("Add room") }, leadingIcon = { Icon(Icons.Filled.Add, null) })
            }

            SectionTitle("Repair history")
            if (state.jobs.isEmpty()) Text("No jobs for this property yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.jobs.forEach { j -> JobTicketCard(j, null, onClick = { actions.onOpenJob(j.id) }, modifier = Modifier.padding(bottom = 8.dp)) }

            SectionTitle("Maintenance passport")
            Text("A PDF with this property's checks, repairs and photos, to share with a buyer, tenant or letting agent.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = actions.onExport, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Filled.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text("Export passport PDF") }
        }

        dateFor?.let { kind ->
            val current = s.checks.firstOrNull { it.rule.kind == kind }?.lastDone
            val picker = rememberDatePickerState(
                initialSelectedDateMillis = current?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
                },
            )
            DatePickerDialog(
                onDismissRequest = { dateFor = null },
                confirmButton = {
                    TextButton(onClick = {
                        picker.selectedDateMillis?.let { actions.onSetDate(kind, Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                        dateFor = null
                    }) { Text("Save date") }
                },
                dismissButton = {
                    Row {
                        if (current != null) TextButton(onClick = { actions.onSetDate(kind, null); dateFor = null }) { Text("Clear") }
                        TextButton(onClick = { dateFor = null }) { Text("Cancel") }
                    }
                },
            ) { DatePicker(picker, title = { Text("When was it last done?", Modifier.padding(start = 24.dp, top = 16.dp)) }) }
        }

        roomDialog?.let { id ->
            val room = state.rooms.firstOrNull { it.id == id }
            var name by rememberSaveable(id) { mutableStateOf(room?.name ?: "") }
            AlertDialog(
                onDismissRequest = { roomDialog = null },
                title = { Text(if (room == null) "Add a room" else "Edit room") },
                text = { OutlinedTextField(name, { name = it }, label = { Text("Room name") }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done)) },
                confirmButton = {
                    TextButton(enabled = name.isNotBlank(), onClick = {
                        if (room == null) actions.onAddRoom(name) else actions.onRenameRoom(room, name)
                        roomDialog = null
                    }) { Text("Save room") }
                },
                dismissButton = {
                    Row {
                        if (room != null) TextButton(onClick = { actions.onDeleteRoom(room.id); roomDialog = null }) { Text("Delete room") }
                        TextButton(onClick = { roomDialog = null }) { Text("Cancel") }
                    }
                },
            )
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete ${s.property.name}?") },
                text = { Text("This deletes the property, its checks, rooms, jobs and photos from this phone. It cannot be undone.") },
                confirmButton = { TextButton(onClick = { confirmDelete = false; actions.onDelete() }) { Text("Delete property") } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it") } },
            )
        }
    }
}

@Composable
private fun CheckCard(c: CheckStatus, onDate: () -> Unit, onBook: () -> Unit) {
    PanelCard(Modifier.padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.rule.name, style = MaterialTheme.typography.titleSmall)
                Text(Format.checkState(c), style = MaterialTheme.typography.bodyMedium)
            }
            CheckStatePill(c.state)
        }
        Text(c.rule.rule, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        Company.checkPrices[c.rule.kind]?.let { Text("Example price: ${Format.pounds(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDate, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Filled.Event, null); Spacer(Modifier.width(6.dp)); Text(if (c.lastDone == null) "Add date" else "Change date") }
            FilledTonalButton(onClick = onBook, modifier = Modifier.heightIn(min = 48.dp)) { Text("Book") }
        }
    }
}

// Property edit

@Composable
fun PropertyEditScreen(initial: PropertyEntity?, onBack: () -> Unit, onSave: (PropertyEntity) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var address by rememberSaveable { mutableStateOf(initial?.addressLine ?: "") }
    var town by rememberSaveable { mutableStateOf(initial?.town ?: "") }
    var postcode by rememberSaveable { mutableStateOf(initial?.postcode ?: "") }
    var type by rememberSaveable { mutableStateOf(initial?.type ?: PropertyType.home.name) }
    var tried by rememberSaveable { mutableStateOf(false) }
    val nameError = if (tried && name.isBlank()) "Give it a name, for example My flat" else null
    val addressError = if (tried && address.isBlank()) "Enter the first line of the address" else null
    val postcodeError = if (tried && !Format.isUkPostcode(postcode)) "Enter a UK postcode, for example M1 1AE" else null

    ScreenScaffold(title = if (initial == null) "Add a property" else "Edit property", onBack = onBack) { padding ->
        ScrollBody(padding) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, isError = nameError != null, supportingText = nameError?.let { { Text(it) } }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next))
            OutlinedTextField(address, { address = it }, label = { Text("Address line") }, isError = addressError != null, supportingText = addressError?.let { { Text(it) } }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
            OutlinedTextField(town, { town = it }, label = { Text("Town (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
            OutlinedTextField(postcode, { postcode = it }, label = { Text("Postcode") }, isError = postcodeError != null, supportingText = postcodeError?.let { { Text(it) } }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done))
            SectionTitle("Type")
            PropertyType.entries.forEach { t ->
                FilterChip(selected = type == t.name, onClick = { type = t.name }, label = { Text(t.label) }, modifier = Modifier.padding(end = 8.dp).heightIn(min = 48.dp))
            }
            if (type == PropertyType.rental.name) {
                Text("Rentals get the full set of landlord checks: gas safety, EICR, EPC and alarms.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            ListDivider()
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    tried = true
                    if (name.isNotBlank() && address.isNotBlank() && Format.isUkPostcode(postcode)) {
                        onSave((initial ?: PropertyEntity(name = "", addressLine = "", town = "", postcode = "", type = type)).copy(name = name, addressLine = address, town = town, postcode = postcode, type = type))
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Save property") }
        }
    }
}
