package app.propcare.homeos.data

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Small settings store. The staff PIN is never stored: only a salted PBKDF2 hash.
 * The app's backup is disabled, so none of this leaves the phone.
 */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("propcare_settings", Context.MODE_PRIVATE)

    var appLock: Boolean
        get() = prefs.getBoolean("app_lock", false)
        set(v) = prefs.edit().putBoolean("app_lock", v).apply()

    var staffMode: Boolean
        get() = prefs.getBoolean("staff_mode", false)
        set(v) = prefs.edit().putBoolean("staff_mode", v).apply()

    var notifiedKeys: Set<String>
        get() = prefs.getStringSet("notified", emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet("notified", v).apply()

    val hasStaffPin: Boolean get() = prefs.contains("pin_hash")

    private var failedAttempts: Int
        get() = prefs.getInt("pin_fail", 0)
        set(v) = prefs.edit().putInt("pin_fail", v).apply()
    private var lockedUntil: Long
        get() = prefs.getLong("pin_locked_until", 0)
        set(v) = prefs.edit().putLong("pin_locked_until", v).apply()

    fun setStaffPin(pin: String) {
        require(pin.length in 4..8 && pin.all { it.isDigit() })
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString("pin_salt", salt.toHex())
            .putString("pin_hash", hash(pin, salt).toHex())
            .apply()
        failedAttempts = 0
    }

    sealed interface PinResult { data object Ok : PinResult; data object Wrong : PinResult; data class Locked(val seconds: Long) : PinResult }

    /** Checks a PIN with a growing lockout after 5 wrong attempts. */
    fun checkStaffPin(pin: String, now: Long = System.currentTimeMillis()): PinResult {
        if (now < lockedUntil) return PinResult.Locked((lockedUntil - now) / 1000 + 1)
        val salt = prefs.getString("pin_salt", null)?.fromHex() ?: return PinResult.Wrong
        val expected = prefs.getString("pin_hash", null)?.fromHex() ?: return PinResult.Wrong
        val ok = MessageDigest.isEqual(hash(pin, salt), expected)
        if (ok) { failedAttempts = 0; return PinResult.Ok }
        failedAttempts += 1
        if (failedAttempts >= 5) {
            val seconds = 30L shl (failedAttempts - 5).coerceAtMost(6)
            lockedUntil = now + seconds * 1000
            return PinResult.Locked(seconds)
        }
        return PinResult.Wrong
    }

    fun clearAll() = prefs.edit().clear().apply()

    private fun hash(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)).encoded

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.fromHex() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
