package com.nutritrack.app.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nutritrack.app.MainActivity
import com.nutritrack.app.NutriApp
import com.nutritrack.app.R
import com.nutritrack.app.data.db.entity.ProfileEntity
import com.nutritrack.app.databinding.ActivityOnboardingBinding
import com.nutritrack.app.utils.PrefsManager
import kotlinx.coroutines.*

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val db by lazy { (application as NutriApp).database }
    private var currentPage = 0
    private val totalPages = 5

    // Собранные данные
    private var userName = ""
    private var userAge = 25
    private var userHeight = 170f
    private var userWeight = 70f
    private var userGender = "male"
    private var userGoal = "maintain"
    private var userActivity = "moderate"
    private var personalDataEntered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        showPage(0)

        binding.btnNext.setOnClickListener {
            when (currentPage) {
                0 -> showPage(1)
                1 -> savePersonalDataAndNext()
                2 -> showPage(3)
                3 -> { saveActivityAndFinish(); showPage(4) }
                4 -> finishOnboarding()
            }
        }
        binding.btnBack.setOnClickListener { handleBack() }
    }

    private fun showPage(page: Int) {
        currentPage = page
        binding.viewPagerContent.removeAllViews()

        updateDots()

        when (page) {
            0 -> showWelcomePage()
            1 -> showPersonalDataPage()
            2 -> showGoalPage()
            3 -> showActivityPage()
            4 -> showResultPage()
        }

        binding.btnBack.visibility = if (page == 0) View.INVISIBLE else View.VISIBLE
        binding.btnNext.text = if (page == totalPages - 1) getString(R.string.ob_start) else getString(R.string.ob_next)
    }

    private fun updateDots() {
        binding.layoutDots.removeAllViews()
        repeat(totalPages) { i ->
            val dot = View(this).apply {
                val size = if (i == currentPage) 12 else 8
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    dpToPx(size), dpToPx(size)
                ).apply { setMargins(dpToPx(4), 0, dpToPx(4), 0) }
                background = if (i == currentPage)
                    getDrawable(R.drawable.dot_active)
                else
                    getDrawable(R.drawable.dot_inactive)
            }
            binding.layoutDots.addView(dot)
        }
    }

    private fun showWelcomePage() {
        val v = layoutInflater.inflate(R.layout.onboard_welcome, binding.viewPagerContent, false)
        binding.viewPagerContent.addView(v)
    }

    private fun showPersonalDataPage() {
        val v = layoutInflater.inflate(R.layout.onboard_personal, binding.viewPagerContent, false)
        binding.viewPagerContent.addView(v)

        // Восстанавливаем ранее введённые данные (чтобы не терялись при возврате назад)
        if (personalDataEntered) {
            v.findViewById<android.widget.EditText>(R.id.etName)?.setText(userName)
            v.findViewById<android.widget.EditText>(R.id.etAge)?.setText(userAge.toString())
            v.findViewById<android.widget.EditText>(R.id.etHeight)?.setText(userHeight.toInt().toString())
            v.findViewById<android.widget.EditText>(R.id.etWeight)?.setText(userWeight.toInt().toString())
            val rg = v.findViewById<android.widget.RadioGroup>(R.id.rgGender)
            rg?.check(if (userGender == "male") R.id.rbMale else R.id.rbFemale)
        }
    }

    private fun savePersonalDataAndNext() {
        val scrollView = binding.viewPagerContent.getChildAt(0) as? android.widget.ScrollView ?: return
        val linearLayout = scrollView.getChildAt(0) as? android.widget.LinearLayout ?: return

        val etName   = linearLayout.findViewById<android.widget.EditText>(R.id.etName)
        val etAge    = linearLayout.findViewById<android.widget.EditText>(R.id.etAge)
        val etHeight = linearLayout.findViewById<android.widget.EditText>(R.id.etHeight)
        val etWeight = linearLayout.findViewById<android.widget.EditText>(R.id.etWeight)
        val rgGender = linearLayout.findViewById<android.widget.RadioGroup>(R.id.rgGender)

        userName   = etName.text.toString().trim().ifEmpty { getString(R.string.ob_user) }
        userAge    = etAge.text.toString().toIntOrNull() ?: 25
        userHeight = etHeight.text.toString().toFloatOrNull() ?: 170f
        userWeight = etWeight.text.toString().toFloatOrNull() ?: 70f
        userGender = if (rgGender.checkedRadioButtonId == R.id.rbMale) "male" else "female"

        if (userAge < 10 || userAge > 120)        { etAge.error    = getString(R.string.ob_age_invalid); return }
        if (userHeight < 100 || userHeight > 250) { etHeight.error = getString(R.string.ob_height_invalid);    return }
        if (userWeight < 30 || userWeight > 300)  { etWeight.error = getString(R.string.ob_weight_invalid);     return }

        personalDataEntered = true
        showPage(2)
    }

    private fun showGoalPage() {
        val v = layoutInflater.inflate(R.layout.onboard_goal, binding.viewPagerContent, false)
        binding.viewPagerContent.addView(v)

        val goalCards = listOf(
            v.findViewById<androidx.cardview.widget.CardView>(R.id.cardMaintain),
            v.findViewById<androidx.cardview.widget.CardView>(R.id.cardLose),
            v.findViewById<androidx.cardview.widget.CardView>(R.id.cardGain),
            v.findViewById<androidx.cardview.widget.CardView>(R.id.cardHealthy)
        )
        val goals = listOf("maintain", "lose", "gain", "healthy")
        var selectedGoal = userGoal

        fun updateCardStyles() {
            goalCards.forEachIndexed { i, card ->
                card.setCardBackgroundColor(
                    if (goals[i] == selectedGoal) getColor(R.color.color_primary)
                    else getColor(R.color.card_bg)
                )
            }
        }
        updateCardStyles()

        goalCards.forEachIndexed { i, card ->
            card.setOnClickListener {
                selectedGoal = goals[i]
                userGoal = selectedGoal
                updateCardStyles()
            }
        }
    }

    private fun showActivityPage() {
        val v = layoutInflater.inflate(R.layout.onboard_activity, binding.viewPagerContent, false)
        binding.viewPagerContent.addView(v)

        val rgActivity = v.findViewById<android.widget.RadioGroup>(R.id.rgActivity)
        when (userActivity) {
            "sedentary"   -> rgActivity.check(R.id.rbSedentary2)
            "light"       -> rgActivity.check(R.id.rbLight2)
            "active"      -> rgActivity.check(R.id.rbActive2)
            "very_active" -> rgActivity.check(R.id.rbVeryActive2)
            else          -> rgActivity.check(R.id.rbModerate2)
        }
    }

    private fun saveActivityAndFinish() {
        val v = binding.viewPagerContent.getChildAt(0) ?: return
        val rgActivity = v.findViewById<android.widget.RadioGroup>(R.id.rgActivity)
        userActivity = when (rgActivity.checkedRadioButtonId) {
            R.id.rbSedentary2  -> "sedentary"
            R.id.rbLight2      -> "light"
            R.id.rbActive2     -> "active"
            R.id.rbVeryActive2 -> "very_active"
            else               -> "moderate"
        }
    }

    private fun handleBack() {
        if (currentPage > 0) showPage(currentPage - 1)
    }

    private fun showResultPage() {
        val v = layoutInflater.inflate(R.layout.onboard_result, binding.viewPagerContent, false)
        binding.viewPagerContent.addView(v)

        val tempProfile = ProfileEntity(
            name = userName, age = userAge, height = userHeight, weight = userWeight,
            gender = userGender, goal = userGoal, activityLevel = userActivity, isActive = true
        )
        val cal = com.nutritrack.app.utils.CalorieCalculator.calculateDailyGoal(tempProfile)
        val water = com.nutritrack.app.utils.CalorieCalculator.calculateWaterGoal(tempProfile)

        v.findViewById<android.widget.TextView>(R.id.tvResultHello)?.text =
            getString(R.string.ob_result_hello, userName)
        v.findViewById<android.widget.TextView>(R.id.tvResultCalories)?.text = "$cal " + getString(R.string.kcal_unit)
        v.findViewById<android.widget.TextView>(R.id.tvResultWater)?.text = "$water " + getString(R.string.ml_unit)
    }

 /**
 * Напоминания о воде включаются сразу после онбординга: каждые 1.5 часа
 * с 09:00 до 21:00. Значения кладём в те же ключи, что читает экран
 * «Напоминания о воде», поэтому тумблер и интервал там показываются
 * выбранными, и пользователь может всё выключить в один тап.
 */
    private fun enableWaterRemindersDefaults() {
        val p = PrefsManager(this)
        p.setBool("water_reminder_enabled", true)
        p.setString("water_interval_min", "90")   // 1.5 часа
        p.setString("water_start_time", "09:00")
        p.setString("water_end_time", "21:00")
    }

    private fun scheduleWaterReminders() {
        val p = PrefsManager(this)
        com.nutritrack.app.utils.WaterReminderScheduler.schedule(
            context = this,
            intervalMin = p.getString("water_interval_min", "90").toIntOrNull() ?: 90,
            startTime = p.getString("water_start_time", "09:00"),
            endTime = p.getString("water_end_time", "21:00")
        )
    }

 /**
 * Разрешение на уведомления (Android 13+). При отказе НЕ оставляем включённый
 * тумблер: иначе в настройках значилось бы «включено», а уведомления бы не
 * приходили. Пользователь сможет включить их позже вручную.
 */
    private val notifPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                scheduleWaterReminders()
            } else {
                PrefsManager(this).setBool("water_reminder_enabled", false)
            }
            goToMain()
        }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun finishOnboarding() {
        lifecycleScope.launch(Dispatchers.IO) {
            // Проверяем, существует ли уже профиль (создан в prepopulate)
            val existingCount = db.profileDao().getProfileCount()

            if (existingCount == 0) {
                // Профиля нет — создаём новый
                db.profileDao().insertProfile(
                    ProfileEntity(
                        name          = userName,
                        age           = userAge,
                        height        = userHeight,
                        weight        = userWeight,
                        gender        = userGender,
                        goal          = userGoal,
                        activityLevel = userActivity,
                        isActive      = true
                    )
                )
            } else {
                // Профиль уже есть — обновляем первый вместо создания дубликата
                val first = db.profileDao().getFirstProfile()
                if (first != null) {
                    db.profileDao().updateProfile(
                        first.copy(
                            name          = userName,
                            age           = userAge,
                            height        = userHeight,
                            weight        = userWeight,
                            gender        = userGender,
                            goal          = userGoal,
                            activityLevel = userActivity,
                            isActive      = true
                        )
                    )
                } else {
                    db.profileDao().insertProfile(
                        ProfileEntity(
                            name          = userName,
                            age           = userAge,
                            height        = userHeight,
                            weight        = userWeight,
                            gender        = userGender,
                            goal          = userGoal,
                            activityLevel = userActivity,
                            isActive      = true
                        )
                    )
                }
            }

            PrefsManager(this@OnboardingActivity).isFirstLaunch = false

            withContext(Dispatchers.Main) {
                enableWaterRemindersDefaults()
                val needsPermission =
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            this@OnboardingActivity,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                if (needsPermission) {
                    // Дальше на главный экран уйдём из колбэка разрешения
                    notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    scheduleWaterReminders()
                    goToMain()
                }
            }
        }
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
}
