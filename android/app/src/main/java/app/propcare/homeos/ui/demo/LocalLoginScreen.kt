package app.propcare.homeos.ui.demo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import app.propcare.homeos.R

@Composable fun LocalLoginScreen(hasAccount: Boolean, initialEmail: String = "", busy: Boolean = false,
    error: String? = null, onSubmit: (String, String, String) -> Unit = { _, _, _ -> }) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    // Password stays in memory only; never put credentials into saved UI state.
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    DemoPage("Welcome", brand = true) {
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.brand_logo), "PropCare", Modifier.size(94.dp))
        }
        DemoTitle(if (hasAccount) "Welcome home." else "Your home.\nYour account.",
            if (hasAccount) "Sign in to pick up where you left off." else "Create your login to keep repairs and home details together.")
        if (!hasAccount) OutlinedTextField(name, { name = it.take(80) }, label = { Text("Full name") },
            singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        OutlinedTextField(email, { email = it.take(254) }, label = { Text("Email address") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it.take(128) }, label = { Text("Password") }, singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !busy,
            trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (visible) "Hide password" else "Show password") } },
            supportingText = { if (!hasAccount) Text("At least 8 characters") }, modifier = Modifier.fillMaxWidth())
        ErrorLine(error)
        PrimaryAction(if (busy) "Please wait…" else if (hasAccount) "Sign in" else "Create account",
            { onSubmit(name, email, password) }, enabled = !busy && email.isNotBlank() && password.length >= 8 && (hasAccount || name.trim().length >= 2), icon = Icons.Filled.LockOpen)
        DemoNote("Saved on this device. Your login and repair history stay here; there is no cloud account or password recovery.")
        Spacer(Modifier.height(12.dp))
    }
}
