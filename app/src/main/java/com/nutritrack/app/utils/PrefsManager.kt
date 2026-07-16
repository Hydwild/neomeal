package com.nutritrack.app.utils

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nutritrack_prefs", Context.MODE_PRIVATE)

    // Тема
    var isDarkTheme: Boolean
        get() = prefs.getBoolean("dark_theme", false)
        set(v) = prefs.edit().putBoolean("dark_theme", v).apply()

    var hapticEnabled: Boolean
        get() = prefs.getBoolean("haptic_enabled", true)
        set(v) = prefs.edit().putBoolean("haptic_enabled", v).apply()

    var confettiEnabled: Boolean
        get() = prefs.getBoolean("confetti_enabled", true)
        set(v) = prefs.edit().putBoolean("confetti_enabled", v).apply()

    var vacationMode: Boolean
        get() = prefs.getBoolean("vacation_mode", false)
        set(v) = prefs.edit().putBoolean("vacation_mode", v).apply()

    var vacationStart: String
        get() = prefs.getString("vacation_start", "") ?: ""
        set(v) = prefs.edit().putString("vacation_start", v).apply()

    var frozenDates: Set<String>
        get() = prefs.getStringSet("frozen_dates", emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet("frozen_dates", v).apply()

    var lastFreezeDate: String
        get() = prefs.getString("last_freeze_date", "") ?: ""
        set(v) = prefs.edit().putString("last_freeze_date", v).apply()

    // Акцентный цвет ("purple" по умолчанию)
    var accentColor: String
        get() = prefs.getString("accent_color", "purple") ?: "purple"
        set(v) = prefs.edit().putString("accent_color", v).apply()

    // Фоновое оформление: ключ пресета / "custom_gradient" / "custom_image"
    var backgroundKey: String
        get() = prefs.getString("background_key", "default") ?: "default"
        set(v) = prefs.edit().putString("background_key", v).apply()

    // Пользователь вручную выбрал фон тема больше не перезаписывает его автоматически
    var backgroundManual: Boolean
        get() = prefs.getBoolean("background_manual", false)
        set(v) = prefs.edit().putBoolean("background_manual", v).apply()

    // Прозрачность таб-бара: 0 = прозрачный, 100 = плотный. Дефолт 72 —
    // полупрозрачное «стекло» (просьба пользователя, было 100)
    var bottomBarAlpha: Int
        get() = prefs.getInt("bottom_bar_alpha", 72)
        set(v) = prefs.edit().putInt("bottom_bar_alpha", v.coerceIn(0, 100)).apply()

    // Пользовательский градиент: HEX-цвета через запятую ("#FF5733,#33C1FF")
    var customGradient: String
        get() = prefs.getString("custom_gradient", "#667eea,#f093fb") ?: "#667eea,#f093fb"
        set(v) = prefs.edit().putString("custom_gradient", v).apply()

    // Путь к пользовательскому фоновому изображению (во внутренней памяти)
    var customImagePath: String
        get() = prefs.getString("custom_image_path", "") ?: ""
        set(v) = prefs.edit().putString("custom_image_path", v).apply()

    var appLockEnabled: Boolean
        get() = prefs.getBoolean("app_lock_enabled", false)
        set(v) = prefs.edit().putBoolean("app_lock_enabled", v).apply()

    // Начало недели (true = понедельник)
    var weekStartMonday: Boolean
        get() = prefs.getBoolean("week_start_monday", true)
        set(v) = prefs.edit().putBoolean("week_start_monday", v).apply()

    // Единицы измерения ("metric" / "imperial")
    var units: String
        get() = prefs.getString("units", "metric") ?: "metric"
        set(v) = prefs.edit().putString("units", v).apply()

    var smartCoachEnabled: Boolean
        get() = prefs.getBoolean("smart_coach_enabled", false)
        set(v) = prefs.edit().putBoolean("smart_coach_enabled", v).apply()

    var calorieAdjustment: Int
        get() = prefs.getInt("calorie_adjustment", 0)
        set(v) = prefs.edit().putInt("calorie_adjustment", v).apply()

    var lastCoachCheck: String
        get() = prefs.getString("last_coach_check", "") ?: ""
        set(v) = prefs.edit().putString("last_coach_check", v).apply()

    // Шрифт интерфейса
    // Значения: "system", "inter", "manrope", "montserrat"
    var fontFamily: String
        get() = prefs.getString("font_family", "manrope") ?: "manrope"
        set(v) = prefs.edit().putString("font_family", v).apply()

    // Язык
    var language: String
        get() = prefs.getString("language", "auto") ?: "auto"
        set(v) = prefs.edit().putString("language", v).apply()

    // Активный профиль
    var activeProfileId: Long
        get() = prefs.getLong("active_profile_id", 1L)
        set(v) = prefs.edit().putLong("active_profile_id", v).apply()

    // Первый запуск
    var isFirstLaunch: Boolean
        get() = prefs.getBoolean("is_first_launch", true)
        set(v) = prefs.edit().putBoolean("is_first_launch", v).apply()

    // Режим отображения калорий
    var isCircularCalorieMode: Boolean
        get() = prefs.getBoolean("circular_calorie_mode", true)
        set(v) = prefs.edit().putBoolean("circular_calorie_mode", v).apply()

    // Режим отображения воды (true = живой с кольцом, false = классический)
    var isLiveWaterMode: Boolean
        get() = prefs.getBoolean("live_water_mode", false)
        set(v) = prefs.edit().putBoolean("live_water_mode", v).apply()

    // Умные напоминания о воде (по типичному времени питья)
    var smartWaterReminder: Boolean
        get() = prefs.getBoolean("smart_water_reminder", false)
        set(v) = prefs.edit().putBoolean("smart_water_reminder", v).apply()

    // Геймификация: выбранная рамка аватара и компаньон
    var selectedFrame: String
        get() = prefs.getString("selected_frame", "none") ?: "none"
        set(v) = prefs.edit().putString("selected_frame", v).apply()

    var selectedCompanion: String
        get() = prefs.getString("selected_companion", "drop") ?: "drop"
        set(v) = prefs.edit().putString("selected_companion", v).apply()

 /** Последний показанный пользователю уровень — для тоста о повышении. */
    var lastSeenLevel: Int
        get() = prefs.getInt("last_seen_level", 1)
        set(v) = prefs.edit().putInt("last_seen_level", v).apply()

 /** Показывать ли цифру уровня в углу аватара. */
    var showLevelOnAvatar: Boolean
        get() = prefs.getBoolean("show_level_on_avatar", true)
        set(v) = prefs.edit().putBoolean("show_level_on_avatar", v).apply()

 /** Выбранная валюта ("auto" = по языку, иначе код валюты). */
    var currency: String
        get() = prefs.getString("currency", "auto") ?: "auto"
        set(v) = prefs.edit().putString("currency", v).apply()

 /** Последняя версия журнала изменений, которую видел пользователь. */
    var lastSeenChangelog: String
        get() = prefs.getString("last_seen_changelog", "") ?: ""
        set(v) = prefs.edit().putString("last_seen_changelog", v).apply()

    // Напоминания (стандартные)
    var breakfastEnabled: Boolean
        get() = prefs.getBoolean("reminder_breakfast", true)
        set(v) = prefs.edit().putBoolean("reminder_breakfast", v).apply()

    var breakfastTime: String
        get() = prefs.getString("reminder_breakfast_time", "08:00") ?: "08:00"
        set(v) = prefs.edit().putString("reminder_breakfast_time", v).apply()

    var lunchEnabled: Boolean
        get() = prefs.getBoolean("reminder_lunch", true)
        set(v) = prefs.edit().putBoolean("reminder_lunch", v).apply()

    var lunchTime: String
        get() = prefs.getString("reminder_lunch_time", "13:00") ?: "13:00"
        set(v) = prefs.edit().putString("reminder_lunch_time", v).apply()

    var dinnerEnabled: Boolean
        get() = prefs.getBoolean("reminder_dinner", true)
        set(v) = prefs.edit().putBoolean("reminder_dinner", v).apply()

    var dinnerTime: String
        get() = prefs.getString("reminder_dinner_time", "19:00") ?: "19:00"
        set(v) = prefs.edit().putString("reminder_dinner_time", v).apply()

    // Версия встроенной базы продуктов
    var productDbVersion: Int
        get() = prefs.getInt("product_db_version", 0)
        set(v) = prefs.edit().putInt("product_db_version", v).apply()
    var snackEnabled: Boolean
        get() = prefs.getBoolean("reminder_snack", false)
        set(v) = prefs.edit().putBoolean("reminder_snack", v).apply()


    var snackTime: String
        get() = prefs.getString("reminder_snack_time", "16:00") ?: "16:00"
        set(v) = prefs.edit().putString("reminder_snack_time", v).apply()

    // Выбранный питомец (id из PetCatalog, по умолчанию panda)
    var selectedPet: String
        get() = prefs.getString("selected_pet", "panda") ?: "panda"
        set(v) = prefs.edit().putString("selected_pet", v).apply()

    // Питомцы включены пользователем (показывать у воды / в виджете)
    var petsEnabled: Boolean
        get() = prefs.getBoolean("pets_enabled", true)
        set(v) = prefs.edit().putBoolean("pets_enabled", v).apply()

    // Запись питания/воды/веса в Health Connect (по умолчанию выключена)
    var hcWriteEnabled: Boolean
        get() = prefs.getBoolean("hc_write_enabled", false)
        set(v) = prefs.edit().putBoolean("hc_write_enabled", v).apply()

    // Material You: поверхности/акцент из цветов обоев (Android 12+)
    var dynamicColors: Boolean
        get() = prefs.getBoolean("dynamic_colors", false)
        set(v) = prefs.edit().putBoolean("dynamic_colors", v).apply()

    // Универсальные геттеры / сеттеры
    // Используются для динамических ключей (напр. уведомления плана питания)

    fun getBool(key: String, default: Boolean = false): Boolean =
        prefs.getBoolean(key, default)

    fun setBool(key: String, value: Boolean) =
        prefs.edit().putBoolean(key, value).apply()

    fun getString(key: String, default: String = ""): String =
        prefs.getString(key, default) ?: default

    fun setString(key: String, value: String) =
        prefs.edit().putString(key, value).apply()
}
