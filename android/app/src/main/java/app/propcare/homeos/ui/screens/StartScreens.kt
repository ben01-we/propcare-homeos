package app.propcare.homeos.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.propcare.homeos.domain.Company
import app.propcare.homeos.ui.components.PanelCard
import app.propcare.homeos.ui.components.ScreenScaffold
import app.propcare.homeos.ui.components.ScrollBody
import app.propcare.homeos.ui.components.SectionTitle
import app.propcare.homeos.ui.theme.PropCareTheme
import app.propcare.homeos.util.Format

// Onboarding

data class OnboardingActions(
    val onSave: (name: String, phone: String, postcode: String) -> Unit = { _, _, _ -> },
    val onLoadSample: () -> Unit = {},
    val onAllowNotifications: () -> Unit = {},
)

/** Local sign-up: a name, phone and postcode stay on this phone. No account, no server. */
@Composable
fun OnboardingScreen(notificationsAllowed: Boolean, actions: OnboardingActions) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var postcode by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    val nameError = if (tried && name.isBlank()) "Enter your name" else null
    val phoneError = if (tried && phone.count { it.isDigit() } < 10) "Enter a UK phone number, for example 07700 900123" else null
    val postcodeError = if (tried && !Format.isUkPostcode(postcode)) "Enter a UK postcode, for example M1 1AE" else null

    ScreenScaffold(title = "Welcome to PropCare") { padding ->
        ScrollBody(padding) {
            Text("Report a repair in a minute, keep your safety checks on track and send jobs straight to PropCare.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text("Your details stay on this phone. There is no account to create.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionTitle("Your details")
            OutlinedTextField(
                name, { name = it }, label = { Text("Full name") }, isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                phone, { phone = it }, label = { Text("Phone") }, isError = phoneError != null,
                supportingText = phoneError?.let { { Text(it) } }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                postcode, { postcode = it }, label = { Text("Postcode") }, isError = postcodeError != null,
                supportingText = postcodeError?.let { { Text(it) } }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    tried = true
                    if (name.isNotBlank() && phone.count { it.isDigit() } >= 10 && Format.isUkPostcode(postcode)) actions.onSave(name, phone, postcode)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Save and continue") }

            SectionTitle("Just looking?")
            PanelCard {
                Text("Load a sample home to see the app full of data: a rental with safety checks and two jobs. It is marked as a sample, and you can delete it any time.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = actions.onLoadSample, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Home, null); Spacer(Modifier.width(8.dp)); Text("Load a sample home")
                }
            }
            if (!notificationsAllowed) {
                SectionTitle("Reminders")
                PanelCard {
                    Text("Allow notifications so we can remind you before a gas safety check or EICR is due.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = actions.onAllowNotifications, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.Notifications, null); Spacer(Modifier.width(8.dp)); Text("Allow reminders")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(Company.conceptLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Lock

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Lock, null, Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("PropCare is locked", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text("Unlock with your fingerprint, face or screen lock.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onUnlock, modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
            ) { Text("Unlock") }
        }
    }
}

// Emergency

data class EmergencyActions(
    val onBack: () -> Unit = {},
    val onCallPropCare: () -> Unit = {},
    val onCallGas: () -> Unit = {},
)

/** Full red screen: one-tap calls first, then plain safe steps. */
@Composable
fun EmergencyScreen(actions: EmergencyActions) {
    val s = PropCareTheme.status
    Surface(Modifier.fillMaxSize(), color = s.emergency, contentColor = s.onEmergency) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.Warning, null)
                Spacer(Modifier.width(8.dp))
                Text("Emergency", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            }
            Spacer(Modifier.height(8.dp))
            Text("If anyone is in danger, call 999.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = actions.onCallGas, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = s.onEmergency, contentColor = s.emergency),
            ) { Icon(Icons.Filled.Call, null); Spacer(Modifier.width(8.dp)); Text("Call the gas emergency line, ${Company.gasEmergency}") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = actions.onCallPropCare, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = s.onEmergency),
                border = BorderStroke(2.dp, s.onEmergency),
            ) { Icon(Icons.Filled.Call, null); Spacer(Modifier.width(8.dp)); Text("Call PropCare, ${Company.phoneDisplay}") }

            SafeSteps("You smell gas", listOf("Do not use switches, flames or phones in the room.", "Open doors and windows.", "Turn off the gas at the meter if you can.", "Leave the building, then call ${Company.gasEmergency}."))
            SafeSteps("Your CO alarm is sounding", listOf("Open doors and windows.", "Turn off fuel-burning appliances.", "Get everyone outside into fresh air.", "Call ${Company.gasEmergency}. Get medical help if anyone feels unwell."))
            SafeSteps("Sparking, burning smell or a shock", listOf("Do not touch the socket or switch.", "Turn off the power at the consumer unit if it is safe to reach.", "If there is fire, get out and call 999."))
            SafeSteps("Flooding or a burst pipe", listOf("Turn off the water at the stopcock.", "If water is near electrics, turn off the power at the consumer unit, if you can reach it from a dry place.", "Move valuables away and call PropCare."))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SafeSteps(title: String, steps: List<String>) {
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
    steps.forEachIndexed { i, step -> Text("${i + 1}. $step", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp)) }
}

// Services

data class ServicesActions(
    val onBack: () -> Unit = {},
    val onCall: () -> Unit = {},
    val onWhatsApp: () -> Unit = {},
    val onEmail: () -> Unit = {},
)

@Composable
fun ServicesScreen(actions: ServicesActions) {
    ScreenScaffold(title = "PropCare services", onBack = actions.onBack) { padding ->
        ScrollBody(padding) {
            Text(Company.name, style = MaterialTheme.typography.titleLarge)
            Text(Company.hours, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Company.quoteResponse, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = actions.onCall, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Filled.Call, null); Spacer(Modifier.width(6.dp)); Text("Call") }
                FilledTonalButton(onClick = actions.onWhatsApp, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.AutoMirrored.Filled.Chat, null); Spacer(Modifier.width(6.dp)); Text("WhatsApp") }
                FilledTonalButton(onClick = actions.onEmail, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Filled.Email, null); Spacer(Modifier.width(6.dp)); Text("Email") }
            }
            SectionTitle("What we do")
            Company.services.forEach { svc ->
                PanelCard(Modifier.padding(bottom = 8.dp)) {
                    Text(svc.name, style = MaterialTheme.typography.titleSmall)
                    Text(svc.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SectionTitle("Areas we cover")
            Text(Company.areas.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
            SectionTitle("Example prices")
            Text(Company.priceGuideNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Company.priceGuide.forEach { (trade, band) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(trade.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${Format.pounds(band.first)} to ${Format.pounds(band.second)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(Company.conceptLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = actions.onBack) { Text("Back") }
        }
    }
}
