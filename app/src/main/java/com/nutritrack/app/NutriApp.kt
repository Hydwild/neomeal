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
        // Crashlytics: сбор отчётов о сбоях ТОЛЬКО при явном согласии пользователя
        // (по умолчанию выключен; запрос согласия — при первом запуске в MainActivity).
        runCatching {
            val fc = com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
            val consent = com.nutritrack.app.utils.PrefsManager(this).crashReportsConsent
            fc.isCrashlyticsCollectionEnabled = consent
            if (consent) fc.setCustomKey("locale", com.nutritrack.app.utils.PrefsManager(this).language)
        }
        val p = com.nutritrack.app.utils.PrefsManager(this)
        com.nutritrack.app.utils.CalorieCalculator.adjustment = p.calorieAdjustment
        com.nutritrack.app.utils.CalorieCalculator.manualCalories = p.manualCalorieGoal
        com.nutritrack.app.utils.CalorieCalculator.manualWater = p.manualWaterGoal
        com.nutritrack.app.utils.CalorieCalculator.proteinPct = p.macroProtein
        com.nutritrack.app.utils.CalorieCalculator.fatPct = p.macroFat
        com.nutritrack.app.utils.CalorieCalculator.carbsPct = p.macroCarbs
        com.nutritrack.app.utils.UserRecipes.load(this)
        // Приводим будильники приёмов пищи в соответствие с ползунками пользователя.
        // Снимает «фантомные» напоминания, которые старые версии ставили сами
        // (reminder_breakfast/lunch/dinner были включены по умолчанию и не
        // выключались ни из одного экрана).
        runCatching { com.nutritrack.app.utils.ReminderScheduler.syncMealReminders(this) }
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
                    // Полностью пересоздаём встроенные продукты.
                    // enrich обязателен — иначе продукты теряют витамины/минералы
                    // (первичное заполнение в AppDatabase.prepopulate тоже с enrich)
                    db.productDao().deleteNonCustomProducts()
                    ProductDatabase.all.forEach {
                        db.productDao().insertProduct(
                            com.nutritrack.app.data.MicronutrientData.enrich(it)
                        )
                    }
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
