package com.nutritrack.app

import android.app.Application
import com.nutritrack.app.data.ProductDatabase
import com.nutritrack.app.data.db.AppDatabase
import com.nutritrack.app.utils.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NutriApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    // Скоуп уровня приложения для фоновой работы, не привязанной к экрану
    // (живёт весь процесс; SupervisorJob — падение одной задачи не роняет остальные)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(com.nutritrack.app.utils.LocaleHelper.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        // Нативные библиотеки SQLCipher — до первого обращения к БД
        net.sqlcipher.database.SQLiteDatabase.loadLibs(this)
        com.nutritrack.app.utils.AppLog.init(this)
        // Crashlytics: помечаем версию встроенной базы и собираем краши
        runCatching {
            val fc = com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
            fc.isCrashlyticsCollectionEnabled = true
            fc.setCustomKey("locale", com.nutritrack.app.utils.PrefsManager(this).language)
        }
        com.nutritrack.app.utils.CalorieCalculator.adjustment =
            com.nutritrack.app.utils.PrefsManager(this).calorieAdjustment
        checkAndRepopulateProducts()
    }

    private fun checkAndRepopulateProducts() {
        appScope.launch {
            try {
                val db    = database
                val prefs = PrefsManager(this@NutriApp)
                val count = db.productDao().getProductCount()

                // Версия встроенной базы — увеличивай при изменении списка продуктов
                val dbVersion = 6

                if (count < 50 || prefs.productDbVersion < dbVersion) {
                    // Полностью пересоздаём встроенные продукты
                    db.productDao().deleteNonCustomProducts()
                    ProductDatabase.all.forEach { db.productDao().insertProduct(it) }
                    prefs.productDbVersion = dbVersion
                }

                // ВСЕГДА чистим дубликаты при старте (на случай старых данных)
                db.productDao().removeDuplicates()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
