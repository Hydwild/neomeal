package com.nutritrack.app.utils

import android.content.Context
import java.security.SecureRandom

/**
 * ПИН-код как способ блокировки входа (альтернатива биометрии —
 * например, когда телефоном пользуются несколько человек).
 *
 * Хранится соль + PBKDF2-хеш в EncryptedSharedPreferences (Keystore);
 * сам ПИН нигде не сохраняется.
 */
object PinLock {

    private const val K_SALT = "pin_salt"
    private const val K_HASH = "pin_hash"
    private const val ITERATIONS = 120_000

    fun isSet(context: Context): Boolean =
        SecurePrefs.getString(context, K_HASH) != null

    fun set(context: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        SecurePrefs.setString(context, K_SALT, b64(salt))
        SecurePrefs.setString(context, K_HASH, b64(hash(pin, salt)))
    }

    fun clear(context: Context) {
        SecurePrefs.setString(context, K_SALT, null)
        SecurePrefs.setString(context, K_HASH, null)
    }

    fun check(context: Context, pin: String): Boolean {
        val salt = SecurePrefs.getString(context, K_SALT) ?: return false
        val stored = SecurePrefs.getString(context, K_HASH) ?: return false
        return b64(hash(pin, unb64(salt))) == stored
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        return javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
    }

    private fun b64(b: ByteArray) = java.util.Base64.getEncoder().encodeToString(b)
    private fun unb64(s: String) = java.util.Base64.getDecoder().decode(s)
}
