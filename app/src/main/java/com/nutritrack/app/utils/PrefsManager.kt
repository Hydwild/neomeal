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

    // Согласие на отправку отчётов о сбоях (Crashlytics). По умолчанию ВЫКЛ —
    // сбор не идёт, пока пользователь не согласится.
    var crashReportsConsent: Boolean
        get() = prefs.getBoolean("crash_reports_consent", false)
        set(v) = prefs.edit().putBoolean("crash_reports_consent", v).apply()

    // Показывали ли уже запрос согласия на отчёты о сбоях (чтобы не спрашивать повторно).
    var crashConsentAsked: Boolean
        get() = prefs.getBoolean("crash_consent_asked", false)
        set(v) = prefs.edit().putBoolean("crash_consent_asked", v).apply()

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
    // КЭШ id активного профиля. Источник правды — флаг isActive в БД.
    // Читать и писать только через ActiveProfile: прямое обращение отсюда
    // приводило к тому, что часть экранов работала с чужим профилем.
    var activeProfileIdCache: Long
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

    // Напоминания о приёмах пищи.
    // ЕДИНСТВЕННЫЙ источник правды — ключи meal_notif_<slot>, их ставит ползунок
    // в «Плане питания». По умолчанию ВЫКЛ: без явного включения пользователем
    // никаких уведомлений о еде быть не должно.
    // Время дублируется в meal_notif_time_<slot>, чтобы восстановить будильники
    // после перезагрузки, не читая БД в BootReceiver.
    // (Раньше здесь жили отдельные reminder_breakfast/lunch/dinner с default=true,
    // которые не выключались ни из одного экрана — источник фантомных уведомлений.)

    fun mealNotifEnabled(slot: String): Boolean = getBool("meal_notif_$slot", false)

    fun mealNotifTime(slot: String, default: String): String =
        getString("meal_notif_time_$slot", default).ifBlank { default }

    fun setMealNotif(slot: String, enabled: Boolean, time: String? = null) {
        setBool("meal_notif_$slot", enabled)
        if (enabled && !time.isNullOrBlank()) setString("meal_notif_time_$slot", time)
    }

    // Версия встроенной базы продуктов
    var productDbVersion: Int
        get() = prefs.getInt("product_db_version", 0)
        set(v) = prefs.edit().putInt("product_db_version", v).apply()

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

    // Избранные рецепты конструктора (id из RecipeData)
    var favoriteRecipes: Set<String>
        get() = prefs.getStringSet("favorite_recipes", emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet("favorite_recipes", v).apply()

    // Режим темы: "auto" (за системой) | "light" | "dark".
    // Мигрирует со старого тумблера isDarkTheme.
    var themeMode: String
        get() = prefs.getString("theme_mode", null)
            ?: if (isDarkTheme) "dark" else "light"
        set(v) = prefs.edit().putString("theme_mode", v).apply()

    // Объёмы кнопок воды на главном, мл (ровно 4)
    var waterButtons: List<Int>
        get() = (prefs.getString("water_buttons", null) ?: "100,200,300,500")
            .split(",").mapNotNull { it.trim().toIntOrNull() }
            .let { if (it.size == 4) it else listOf(100, 200, 300, 500) }
        set(v) = prefs.edit().putString("water_buttons",
            v.take(4).joinToString(",")).apply()

    // Стартовая вкладка: home | plan | stats | profile | more
    var startTab: String
        get() = prefs.getString("start_tab", "home") ?: "home"
        set(v) = prefs.edit().putString("start_tab", v).apply()

    // Ручные цели (0 = автоматический расчёт по профилю)
    var manualCalorieGoal: Int
        get() = prefs.getInt("manual_calorie_goal", 0)
        set(v) = prefs.edit().putInt("manual_calorie_goal", v.coerceIn(0, 20000)).apply()

    var manualWaterGoal: Int
        get() = prefs.getInt("manual_water_goal", 0)
        set(v) = prefs.edit().putInt("manual_water_goal", v.coerceIn(0, 20000)).apply()

    // Активные калории (Health Connect/ручной ввод) увеличивают дневную норму
    var activityBoost: Boolean
        get() = prefs.getBoolean("activity_boost", false)
        set(v) = prefs.edit().putBoolean("activity_boost", v).apply()

    // Авто-бэкап: включён + tree-URI папки (SAF). Пароль — в SecurePrefs.
    var autoBackupEnabled: Boolean
        get() = prefs.getBoolean("auto_backup_enabled", false)
        set(v) = prefs.edit().putBoolean("auto_backup_enabled", v).apply()

    var autoBackupUri: String
        get() = prefs.getString("auto_backup_uri", "") ?: ""
        set(v) = prefs.edit().putString("auto_backup_uri", v).apply()

    // Иконка приложения: classic | dark | purple
    var appIcon: String
        get() = prefs.getString("app_icon", "classic") ?: "classic"
        set(v) = prefs.edit().putString("app_icon", v).apply()

    // Сплит БЖУ в процентах (сумма = 100)
    var macroProtein: Int
        get() = prefs.getInt("macro_protein", 30)
        set(v) = prefs.edit().putInt("macro_protein", v).apply()
    var macroFat: Int
        get() = prefs.getInt("macro_fat", 30)
        set(v) = prefs.edit().putInt("macro_fat", v).apply()
    var macroCarbs: Int
        get() = prefs.getInt("macro_carbs", 40)
        set(v) = prefs.edit().putInt("macro_carbs", v).apply()

    // Скрытые пункты раздела «Ещё» (ключи строк)
    var hiddenMoreItems: Set<String>
        get() = prefs.getStringSet("hidden_more_items", emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet("hidden_more_items", v).apply()

    // Скрытые карточки главного экрана: water | fasting | suggestions | streak
    var hiddenDashboardCards: Set<String>
        get() = prefs.getStringSet("hidden_dash_cards", emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet("hidden_dash_cards", v).apply()

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
