package app.propcare.homeos

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.propcare.homeos.ui.PropCareRoot

/**
 * Single activity. Hosts the Compose UI edge to edge and the optional app lock:
 * BiometricPrompt (weak biometric or the device PIN/pattern) on start and after
 * 30 seconds in the background.
 */
class MainActivity : FragmentActivity() {
    private val vm: AppViewModel by viewModels()
    private var locked by mutableStateOf(false)
    private var prompting = false
    private lateinit var prompt: BiometricPrompt

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { prompting = false; locked = false }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                prompting = false
                // If the phone no longer has a screen lock, the app lock cannot work: let the user in.
                if (errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL || errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT) locked = false
            }
        })
        locked = savedInstanceState?.getBoolean(KEY_LOCKED) ?: (vm.appLock.value && AppLock.available(this))
        handleIntent(intent)
        setContent { PropCareRoot(vm = vm, locked = locked, onUnlock = ::unlock) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_PROPERTY_ID, -1L) ?: -1L
        if (id > 0) vm.requestOpenProperty(id)
    }

    override fun onStart() {
        super.onStart()
        val away = lastStoppedAt
        lastStoppedAt = 0L
        if (away > 0L && vm.appLock.value && SystemClock.elapsedRealtime() - away > LOCK_AFTER_MS && AppLock.available(this)) locked = true
    }

    override fun onResume() {
        super.onResume()
        if (locked) unlock()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !prompting) lastStoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LOCKED, locked)
    }

    /** Shows the system unlock prompt. The Lock screen's "Unlock" button calls this again after a cancel. */
    fun unlock() {
        if (!locked || prompting) return
        if (!AppLock.available(this)) { locked = false; return }
        prompting = true
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock PropCare")
                .setSubtitle("Use your fingerprint, face or screen lock")
                .setAllowedAuthenticators(AppLock.AUTHENTICATORS)
                .build(),
        )
    }

    companion object {
        const val EXTRA_PROPERTY_ID = "app.propcare.homeos.PROPERTY_ID"
        private const val KEY_LOCKED = "locked"
        private const val LOCK_AFTER_MS = 30_000L
        /** Process-wide, so a recreated activity still knows how long the app was away. */
        private var lastStoppedAt = 0L
    }
}

object AppLock {
    const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** True when the phone has a fingerprint/face or a screen lock, so the app lock can be turned on. */
    fun available(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
}
