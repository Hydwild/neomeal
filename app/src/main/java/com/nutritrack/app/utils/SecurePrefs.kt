package com.nutritrack.app.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Защищённое key-value хранилище приложения (Android Keystore).
 * Для чувствительных значений: ПИН-код (соль+хеш), пароль авто-бэкапа.
 * Не в бэкапе, не в обычных prefs.
 */
object SecurePrefs {

    private const val FILE = "secure_app_prefs"

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context.applicationContext, FILE, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getString(context: Context, key: String): String? =
        try { prefs(context).getString(key, null) } catch (e: Exception) {
            AppLog.e("secure prefs read failed", e); null
        }

    fun setString(context: Context, key: String, value: String?) {
        try {
            prefs(context).edit().apply {
                if (value == null) remove(key) else putString(key, value)
            }.apply()
        } catch (e: Exception) { AppLog.e("secure prefs write failed", e) }
    }
}
