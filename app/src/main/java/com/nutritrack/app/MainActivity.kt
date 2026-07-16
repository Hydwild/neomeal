package com.nutritrack.app

import android.content.Intent
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.nutritrack.app.databinding.ActivityMainBinding
import com.nutritrack.app.ui.onboarding.OnboardingActivity
import com.nutritrack.app.utils.AchievementToast
import com.nutritrack.app.utils.PrefsManager
import com.nutritrack.app.viewmodel.AchievementsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.nutritrack.app.utils.LocaleHelper.wrap(newBase))
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager

    // Общий для всего приложения — фрагменты используют activityViewModels()
    private val achievementsVm: AchievementsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash Screen API (Android 12+); до super.onCreate
        installSplashScreen()
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)

        // Шрифт интерфейса — применяем тему-вариант до создания UI
        when (prefs.fontFamily) {
            "inter"      -> setTheme(R.style.Theme_NutriTrack_Inter)
            "manrope"    -> setTheme(R.style.Theme_NutriTrack_Manrope)
            "montserrat" -> setTheme(R.style.Theme_NutriTrack_Montserrat)
            // "system" — оставляем базовую тему (системный шрифт)
        }

        // Material You: цвета обоев поверх токенов темы (Android 12+)
        if (android.os.Build.VERSION.SDK_INT >= 31 && prefs.dynamicColors) {
            theme.applyStyle(R.style.ThemeOverlay_NutriTrack_Dynamic, true)
        }

        // Тема до создания UI
        AppCompatDelegate.setDefaultNightMode(
            if (prefs.isDarkTheme) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        // Первый запуск онбординг
        if (prefs.isFirstLaunch) {
            goToOnboarding()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge: контент и фон-градиент рисуются под системными барами,
        // отступы контента задаём вручную через insets ниже.
        // ЕДИНСТВЕННЫЙ листенер на root: системные бары + клавиатура вместе.
        // (Второй setOnApplyWindowInsetsListener на ту же view затирает первый —
        // раньше из-за этого отступ бара от системной навигации не применялся.)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val navBase = (6 * resources.displayMetrics.density).toInt()
        val backBase = (74 * resources.displayMetrics.density).toInt()
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            binding.navHostFragment.setPadding(0, bars.top, 0, 0)
            (binding.bottomNavigation.layoutParams as android.widget.FrameLayout.LayoutParams).bottomMargin = navBase + bars.bottom
            (binding.btnGlobalBack.layoutParams as android.widget.FrameLayout.LayoutParams).bottomMargin = backBase + bars.bottom
            binding.bottomNavigation.requestLayout()
            // Клавиатура открыта — прячем нижний бар (иначе он перекрывает поля ввода)
            imeVisible = insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())
            applyBottomNavVisibility()
            insets
        }
        com.nutritrack.app.utils.BackgroundHelper.syncWithTheme(this)
        com.nutritrack.app.utils.BackgroundHelper.apply(binding.rootBackground)
        com.nutritrack.app.utils.BackgroundHelper.applyScrim(binding.backgroundScrim)
        applyStatusBar()

        // При включённой блокировке содержимое не попадает в скриншоты
        // и миниатюру «недавних» (вес, фото прогресса — чувствительные данные)
        if (prefs.appLockEnabled) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        // Сама блокировка — в onStart (срабатывает и при возврате из фона)

        setupNavigation()
        observeAchievementUnlocks()

        // Доп. проверка: если профилей нет — форсим онбординг
        lifecycleScope.launch(Dispatchers.IO) {
            val profileCount = (application as NutriApp).database.profileDao().getProfileCount()
            if (profileCount == 0) {
                prefs.isFirstLaunch = true
                withContext(Dispatchers.Main) { goToOnboarding() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        maybeLock()
    }

    override fun onStop() {
        super.onStop()
        // Обновляем метку при уходе в фон (если разблокированы) — таймаут
        // отсчитывает время В ФОНЕ, а не с момента разблокировки
        if (prefs.appLockEnabled && ::binding.isInitialized &&
            binding.root.visibility == android.view.View.VISIBLE && lastUnlockMs > 0L) {
            lastUnlockMs = System.currentTimeMillis()
        }
    }

 /**
 * Блокировка при запуске И при возврате из фона (раньше — раз на процесс).
 * Быстрые переключения приложений (< 1 мин после разблокировки) не дёргают
 * биометрию заново.
 */
    private fun maybeLock() {
        if (!prefs.appLockEnabled) return
        if (!::binding.isInitialized) return   // ветка ухода на онбординг
        if (lockPromptShowing) return
        if (System.currentTimeMillis() - lastUnlockMs <= LOCK_TIMEOUT_MS) return
        binding.root.visibility = android.view.View.INVISIBLE
        lockPromptShowing = true
        showAppLock()
    }

    override fun onResume() {
        super.onResume()
        com.nutritrack.app.utils.BackgroundHelper.apply(binding.rootBackground)
        com.nutritrack.app.utils.BackgroundHelper.applyScrim(binding.backgroundScrim)
        applyStatusBar()
    }

 /** Прозрачный или цветной статус-бар + контраст иконок под яркость фона. */
    @Suppress("DEPRECATION") // statusBarColor/navigationBarColor нужны для API < 35
    private fun applyStatusBar() {
        // Статус-бар и навбар прозрачные (контент рисуется под ними, виден фон-градиент)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        val lum = com.nutritrack.app.utils.BackgroundHelper.backgroundLuminance(this)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lum > 0.6f
            isAppearanceLightNavigationBars = lum > 0.6f
        }
    }

    /** Прозрачность таб-бара: 0..100. Публичный — для живого превью из настроек. */
    fun applyBottomBarAlpha(alpha0to100: Int) {
        binding.bottomNavigation.setBarAlpha(alpha0to100)
    }

    // Навигация

    private fun setupNavigation() {
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        // Разделы кастомного бара
        binding.bottomNavigation.setTabs(listOf(
            com.nutritrack.app.ui.custom.GlassBottomNav.Tab(R.id.dashboardFragment, R.drawable.ic_home, getString(R.string.nav_home)),
            com.nutritrack.app.ui.custom.GlassBottomNav.Tab(R.id.mealPlanFragment, R.drawable.ic_meal_plan, getString(R.string.nav_plan)),
            com.nutritrack.app.ui.custom.GlassBottomNav.Tab(R.id.analyticsFragment, R.drawable.ic_chart, getString(R.string.nav_analytics)),
            com.nutritrack.app.ui.custom.GlassBottomNav.Tab(R.id.profileFragment, R.drawable.ic_profile, getString(R.string.nav_profile)),
            com.nutritrack.app.ui.custom.GlassBottomNav.Tab(R.id.moreFragment, R.drawable.ic_more, getString(R.string.nav_more))
        ))
        binding.bottomNavigation.setBarAlpha(prefs.bottomBarAlpha)

        // Тап по разделу переход (как в setupWithNavController)
        binding.bottomNavigation.onTabSelected = { destId ->
            navController.navigate(destId, null,
                androidx.navigation.NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setRestoreState(true)
                    .setPopUpTo(navController.graph.startDestinationId, false, true)
                    .build()
            )
        }

        // Смена экрана подсветка нужного раздела
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNavigation.selectById(destination.id)
            // Экраны, где таб-бар ОСТАЁТСЯ (все 5 вкладок нижней навигации)
            val barScreen = when (destination.id) {
                R.id.dashboardFragment,
                R.id.mealPlanFragment,
                R.id.analyticsFragment,
                R.id.profileFragment,
                R.id.moreFragment -> true
                else -> false
            }
            // На экранах без таб-бара показываем кнопку «Назад»
            barAllowedByScreen = barScreen
            hiddenByScroll = false
            applyBottomNavVisibility()
            binding.btnGlobalBack.visibility = if (barScreen) View.GONE else View.VISIBLE
        }
        binding.btnGlobalBack.setOnClickListener {
            if (!navController.popBackStack()) onBackPressedDispatcher.onBackPressed()
        }

        // Скрытие бара при клавиатуре — в общем insets-листенере в onCreate
        maybeShowWhatsNew(navController)
    }

 /** Показывает «Что нового» один раз после обновления (не на первом запуске). */
    private fun maybeShowWhatsNew(navController: androidx.navigation.NavController) {
        val prefs = com.nutritrack.app.utils.PrefsManager(this)
        val latest = com.nutritrack.app.utils.WhatsNewData.latestVersion()
        val seen = prefs.lastSeenChangelog
        when {
            seen.isEmpty() -> {
                // Первый запуск приложения — не показываем, просто фиксируем версию
                prefs.lastSeenChangelog = latest
            }
            seen != latest -> {
                // Было обновление — покажем экран после отрисовки
                binding.root.post { navController.navigate(R.id.whatsNewFragment) }
            }
        }
    }

    private var barAllowedByScreen = true
    private var imeVisible = false
    private var hiddenByScroll = false

    private fun applyBottomNavVisibility() {
        val visible = barAllowedByScreen && !imeVisible && !hiddenByScroll
        binding.bottomNavigation.visibility = if (visible) View.VISIBLE else View.GONE
        // Кнопка «Назад» видна на второстепенных экранах (где нет таб-бара),
        // но прячется при скролле/клавиатуре.
        val backVisible = !barAllowedByScreen && !imeVisible && !hiddenByScroll
        binding.btnGlobalBack.alpha = if (backVisible) 1f else 0f
    }

 /** Вызывается фрагментами при скролле: скрыть бар вниз / показать вверх. */
    fun onContentScroll(dy: Int) {
        val newHidden = when {
            dy > 6 -> true        // скролл вниз — прячем
            dy < -6 -> false      // скролл вверх — показываем
            else -> hiddenByScroll
        }
        if (newHidden != hiddenByScroll) {
            hiddenByScroll = newHidden
            applyBottomNavVisibility()
        }
    }

    // Уведомления о новых достижениях

    private fun observeAchievementUnlocks() {
        achievementsVm.newlyUnlocked.observe(this) { list ->
            if (list.isNullOrEmpty()) return@observe
            // Все достижения — всплывающими тостами сверху, по очереди (надёжная очередь)
            AchievementToast.enqueueAll(this, list.map {
                AchievementToast.Item(
                    emoji = it.def.icon,
                    title = com.nutritrack.app.utils.DataLabel.tr(this, it.def.title),
                    description = com.nutritrack.app.utils.DataLabel.tr(this, it.def.description),
                    rarity = it.def.rarity
                )
            })
            achievementsVm.clearNewlyUnlocked()
        }
    }

    // Онбординг

    private fun goToOnboarding() {
        startActivity(Intent(this, OnboardingActivity::class.java))
        finish()
    }

    //  Блокировка входа

    private var lockPromptShowing = false

    private fun showAppLock() {
        val executor = androidx.core.content.ContextCompat.getMainExecutor(this)
        val prompt = androidx.biometric.BiometricPrompt(this, executor,
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                    lastUnlockMs = System.currentTimeMillis()
                    lockPromptShowing = false
                    binding.root.visibility = android.view.View.VISIBLE
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    lockPromptShowing = false
                    finish()
                }
            })
        val info = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_lock_title))
            .setSubtitle(getString(R.string.app_lock_subtitle))
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }

    companion object {
        // Момент последней успешной разблокировки (на процесс). 0 = не разблокировано.
        private var lastUnlockMs = 0L
        // Возврат из фона в течение минуты не требует повторной биометрии
        private const val LOCK_TIMEOUT_MS = 60_000L
    }
}
