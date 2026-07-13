package com.nutritrack.app.utils

import android.content.Context
import android.provider.Settings
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac

/**
 * Защищённое хранилище Premium-статуса.
 *
 * Статус — не boolean, а токен покупки + HMAC-подпись на ключе из Android Keystore.
 * Ключ генерируется внутри защищённого хранилища и НЕ извлекается (даже с root):
 * - вписать "true" в prefs бесполезно — подпись не сойдётся;
 * - перенести файл с другого устройства нельзя — в подпись вшит ANDROID_ID и пакет;
 * - подделать подпись без ключа нельзя.
 *
 * Предел защиты (честно): динамический хук процесса (Frida/Xposed на root)
 * обходит любые клиентские проверки. Настоящая валидация — по purchase token
 * через RuStore Billing при включении монетизации (activate() уже принимает token).
 */
object PremiumStore {

    private const val KEY_ALIAS = "neomeal_premium_hmac"
    private const val PREFS = "premium_store"
    private const val K_TOKEN = "t"
    private const val K_SIG = "s"

    private fun mac(context: Context): Mac {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: KeyGenerator.getInstance(
                android.security.keystore.KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore"
            ).apply {
                init(
                    android.security.keystore.KeyGenParameterSpec.Builder(
                        KEY_ALIAS, android.security.keystore.KeyProperties.PURPOSE_SIGN
                    ).build()
                )
            }.generateKey()
        return Mac.getInstance("HmacSHA256").apply { init(key) }
    }

    private fun payload(context: Context, token: String): ByteArray {
        @Suppress("HardwareIds")
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        return "$token|$deviceId|${context.packageName}".toByteArray()
    }

    private fun sign(context: Context, token: String): String =
        android.util.Base64.encodeToString(mac(context).doFinal(payload(context, token)), android.util.Base64.NO_WRAP)

    /** Активировать Premium. [purchaseToken] — токен покупки из биллинга. */
    fun activate(context: Context, purchaseToken: String) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(K_TOKEN, purchaseToken)
                .putString(K_SIG, sign(context, purchaseToken))
                .apply()
        }
    }

    fun deactivate(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** Premium активен и запись не подделана/не перенесена с другого устройства. */
    fun isActive(context: Context): Boolean = runCatching {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = p.getString(K_TOKEN, null) ?: return false
        val sig = p.getString(K_SIG, null) ?: return false
        // сравнение постоянной длины — защита от timing-атак не критична, но бесплатна
        java.security.MessageDigest.isEqual(
            android.util.Base64.decode(sig, android.util.Base64.NO_WRAP),
            mac(context).doFinal(payload(context, token))
        )
    }.getOrDefault(false)

    // ---- Каркас проверки целостности подписи APK (анти-перепаковка) ----

    /**
     * SHA-256 подписи приложения. После сборки release вызови из отладочного
     * места и зашей значение в EXPECTED_SIGNATURE перед включением ENFORCEMENT.
     */
    fun currentSignatureSha256(context: Context): String = runCatching {
        val pm = context.packageManager
        val sig = if (android.os.Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNATURES)
                .signatures?.firstOrNull()
        } ?: return ""
        val md = java.security.MessageDigest.getInstance("SHA-256")
        android.util.Base64.encodeToString(md.digest(sig.toByteArray()), android.util.Base64.NO_WRAP)
    }.getOrDefault("")

    /** Эталон подписи release-сборки. Пустой = проверка выключена. */
    private const val EXPECTED_SIGNATURE = ""

    /** true, если подпись APK совпадает с эталоном (или эталон не задан). */
    fun signatureOk(context: Context): Boolean =
        EXPECTED_SIGNATURE.isEmpty() || currentSignatureSha256(context) == EXPECTED_SIGNATURE
}
