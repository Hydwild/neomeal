package com.nutritrack.app.data.db

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Пароль для шифрования базы (SQLCipher).
 *
 * При первом запуске генерируется случайный 32-байтный ключ и сохраняется в
 * EncryptedSharedPreferences. Само хранилище зашифровано мастер-ключом из
 * Android Keystore (ключ не покидает устройство и аппаратно защищён на
 * поддерживающих устройствах). В коде приложения пароль не хранится.
 */
object DatabaseKey {

    private const val PREFS_NAME = "secure_db_prefs"
    private const val KEY_NAME = "db_passphrase"

    fun getOrCreate(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val existing = prefs.getString(KEY_NAME, null)
        if (existing != null) {
            return android.util.Base64.decode(existing, android.util.Base64.NO_WRAP)
        }

        val random = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_NAME, android.util.Base64.encodeToString(random, android.util.Base64.NO_WRAP))
            .apply()
        return random
    }
}
