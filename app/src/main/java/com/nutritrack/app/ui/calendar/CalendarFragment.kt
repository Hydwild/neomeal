package com.nutritrack.app.ui.calendar

import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.nutritrack.app.R
import com.nutritrack.app.databinding.FragmentCalendarBinding
import com.nutritrack.app.ui.custom.CalendarDayView
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs


class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val vm: HistoryViewModel by viewModels()

    private val dayViews = mutableListOf<CalendarDayView>()
    private var animatingMonth = false
    private lateinit var swipeDetector: GestureDetector

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyWeekdayHeaders()
        buildGrid()
        setupSwipe()

        binding.btnPrevMonth.setOnClickListener { animateMonth(forward = false) { vm.previousMonth() } }
        binding.btnNextMonth.setOnClickListener { animateMonth(forward = true)  { vm.nextMonth() } }
        binding.btnToday.setOnClickListener {
            val nowMonth = YearMonth.now()
            val cur = vm.currentMonth.value ?: nowMonth
            if (cur != nowMonth) {
                animateMonth(forward = nowMonth.isAfter(cur)) {
                    vm.setMonth(nowMonth)
                    vm.selectDay(vm.today())
                }
            } else {
                vm.selectDay(vm.today())
            }
        }
        binding.btnOpenInDiary.setOnClickListener {
            val date = vm.selectedDay.value ?: vm.today()
            val nav = findNavController()
            // Пишем дату в backstack-запись дашборда (а не в предыдущий экран,
            // т.к. при заходе через "Ещё" предыдущий экран — не дашборд)
            try {
                nav.getBackStackEntry(R.id.dashboardFragment)
                    .savedStateHandle
                    .set("selected_date", date)
            } catch (_: Exception) {
                // если дашборда нет в стеке — пишем в предыдущий как запасной вариант
                nav.previousBackStackEntry?.savedStateHandle?.set("selected_date", date)
            }
            // Возврат на дашборд, схлопывая промежуточные экраны (Ещё/Календарь)
            nav.popBackStack(R.id.dashboardFragment, false)
        }

        observeAll()
    }

 /** Создаём 42 пустых ячейки (6 недель × 7 дней). */

 /** Подписи дней недели по настройке начала недели. */
    private fun applyWeekdayHeaders() {
        val monday = com.nutritrack.app.utils.PrefsManager(requireContext()).weekStartMonday
        val labels = if (monday) listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс")
        else        listOf("Вс","Пн","Вт","Ср","Чт","Пт","Сб")
        val row = binding.rowWeekdays
        for (i in 0 until row.childCount) {
            (row.getChildAt(i) as? android.widget.TextView)?.let { tv ->
                tv.text = labels.getOrNull(i) ?: tv.text
                // выходные — оранжевым
                val isWeekend = labels.getOrNull(i) == "Сб" || labels.getOrNull(i) == "Вс"
                tv.setTextColor(if (isWeekend) android.graphics.Color.parseColor("#FFB74D")
                else android.graphics.Color.WHITE)
            }
        }
    }

    private fun buildGrid() {
        binding.gridDays.removeAllViews()
        dayViews.clear()
        val cellSize = (resources.displayMetrics.widthPixels - dp(32)) / 7
        for (i in 0 until 42) {
            val v = CalendarDayView(requireContext())
            val params = GridLayout.LayoutParams().apply {
                width = cellSize
                height = (cellSize * 1.15f).toInt()
                rowSpec = GridLayout.spec(i / 7)
                columnSpec = GridLayout.spec(i % 7)
            }
            v.layoutParams = params
            v.setOnClickListener {
                val tag = v.getTag(R.id.tvMonth) as? String
                if (tag != null) vm.selectDay(tag)
            }
            binding.gridDays.addView(v)
            dayViews.add(v)
        }
    }

 /** Анимация смены месяца: уезд + смена + въезд. */
    private fun animateMonth(forward: Boolean, action: () -> Unit) {
        if (animatingMonth) { action(); return }
        animatingMonth = true
        val grid = binding.gridDays
        val sign = if (forward) -1 else 1
        val translateOut = grid.width.toFloat() * sign

        grid.animate()
            .translationX(translateOut * 0.3f)
            .alpha(0f)
            .setDuration(160)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                action()                       // меняем месяц (тут же сетка перерендерится по observe)
                grid.translationX = -translateOut * 0.3f
                grid.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(200)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction { animatingMonth = false }
                    .start()
            }
            .start()
    }

 /** Свайп влево/вправо по календарю — переключает месяцы. */
    private fun setupSwipe() {
        swipeDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                // только горизонтальный свайп с минимальным смещением
                if (abs(dx) < abs(dy) || abs(dx) < dp(50)) return false
                if (abs(vx) < 200) return false
                if (dx > 0) animateMonth(false) { vm.previousMonth() }
                else       animateMonth(true)  { vm.nextMonth() }
                return true
            }
        })
        // Вешаем на контейнер сетки — не мешает клику по дню
        binding.gridContainer.setOnTouchListener { _, ev ->
            swipeDetector.onTouchEvent(ev)
            false  // false → событие идёт дальше к дочерним (клик по дню работает)
        }
    }

    private fun observeAll() {
        vm.currentMonth.observe(viewLifecycleOwner) { month ->
            binding.tvMonth.text = formatMonthHeader(month)
            renderGrid()
            renderChart()
        }
        vm.monthStats.observe(viewLifecycleOwner) { renderGrid(); renderChart() }
        vm.selectedDay.observe(viewLifecycleOwner) { renderGrid() }
        vm.selectedDayStats.observe(viewLifecycleOwner) { renderDetails(it) }
        vm.calorieGoal.observe(viewLifecycleOwner) {
            renderGrid()
            renderChart()
            renderDetails(vm.selectedDayStats.value ?: DayStats(vm.today()))
        }
    }

 /** Заполнить сетку днями с учётом месяца, статусов, выбранного дня, сегодня, серий. */
    private fun renderGrid() {
        val month = vm.currentMonth.value ?: YearMonth.now()
        val stats = vm.monthStats.value ?: emptyMap()
        val goal = vm.calorieGoal.value ?: 2000
        val today = LocalDate.now()
        val selected = vm.selectedDay.value

        // Считаем серии: каждый день, для которого вчерашний (или он сам — старт серии) зелёный
        val streakDates = computeStreakDates(stats, goal, month)

        val firstOfMonth = month.atDay(1)
        val mondayStart = com.nutritrack.app.utils.PrefsManager(requireContext()).weekStartMonday
        val offset = if (mondayStart) firstOfMonth.dayOfWeek.value - 1   // Пн=0..Вс=6
        else firstOfMonth.dayOfWeek.value % 7               // Вс=0..Сб=6
        val daysInMonth = month.lengthOfMonth()
        val prevMonthDays = month.minusMonths(1).lengthOfMonth()

        for (i in 0 until 42) {
            val v = dayViews[i]
            val dayNumber: Int
            val date: LocalDate
            val isCurrentMonth: Boolean

            when {
                i < offset -> {
                    dayNumber = prevMonthDays - (offset - i - 1)
                    date = month.minusMonths(1).atDay(dayNumber)
                    isCurrentMonth = false
                }
                i < offset + daysInMonth -> {
                    dayNumber = i - offset + 1
                    date = month.atDay(dayNumber)
                    isCurrentMonth = true
                }
                else -> {
                    dayNumber = i - offset - daysInMonth + 1
                    date = month.plusMonths(1).atDay(dayNumber)
                    isCurrentMonth = false
                }
            }

            val dateStr = date.toString()
            v.dayNumber = dayNumber
            v.isCurrentMonth = isCurrentMonth
            v.isToday = (date == today)
            v.isSelectedDay = (dateStr == selected)
            v.isFuture = date.isAfter(today)
            v.status = stats[dateStr]?.status(goal) ?: DayStats.Status.NONE
            v.progress = stats[dateStr]?.progress(goal) ?: 0f
            v.isInStreak = isCurrentMonth && dateStr in streakDates
            v.setTag(R.id.tvMonth, dateStr)
        }
    }

 /**
 * День в "серии", если он зелёный И вчерашний/завтрашний тоже зелёный
 * (т.е. часть цепочки минимум из 2 подряд зелёных дней).
 */
    private fun computeStreakDates(
        stats: Map<String, DayStats>,
        goal: Int,
        month: YearMonth
    ): Set<String> {
        val result = HashSet<String>()
        // Проходим по всем дням месяца + соседним краям
        var day = month.minusMonths(1).atEndOfMonth()
        val endDay = month.plusMonths(1).atDay(1)
        while (!day.isAfter(endDay)) {
            val ds = day.toString()
            val isGreen = stats[ds]?.status(goal) == DayStats.Status.GREEN
            if (isGreen) {
                val prev = day.minusDays(1).toString()
                val next = day.plusDays(1).toString()
                val prevGreen = stats[prev]?.status(goal) == DayStats.Status.GREEN
                val nextGreen = stats[next]?.status(goal) == DayStats.Status.GREEN
                if (prevGreen || nextGreen) result.add(ds)
            }
            day = day.plusDays(1)
        }
        return result
    }

 /** Максимальная длина серии подряд зелёных дней (учитывая текущий месяц). */
    private fun longestStreakInMonth(
        stats: Map<String, DayStats>,
        goal: Int,
        month: YearMonth
    ): Int {
        var longest = 0
        var current = 0
        var day = month.atDay(1)
        val end = month.atEndOfMonth()
        while (!day.isAfter(end)) {
            val isGreen = stats[day.toString()]?.status(goal) == DayStats.Status.GREEN
            if (isGreen) {
                current++
                if (current > longest) longest = current
            } else current = 0
            day = day.plusDays(1)
        }
        return longest
    }

    private fun renderChart() {
        val month = vm.currentMonth.value ?: return
        val stats = vm.monthStats.value ?: emptyMap()
        val goal = vm.calorieGoal.value ?: 2000

        val data = (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day).toString()
            day to stats[date]
        }
        binding.monthChart.setData(data, goal)

        val streak = longestStreakInMonth(stats, goal, month)
        binding.tvMonthStreak.text = if (streak >= 2) getString(R.string.cal_streak, streak, resources.getQuantityString(R.plurals.days_streak, streak)) else ""
    }


    private fun renderDetails(stats: DayStats) {
        val goal = vm.calorieGoal.value ?: 2000
        val waterGoal = vm.waterGoal.value ?: 2000

        binding.tvSelectedDate.text = humanizeDate(stats.date)

        binding.tvCalories.text = getString(R.string.cal_kcal_goal_fmt, stats.calories.toInt(), goal)
        val pct = if (goal > 0) (stats.calories / goal * 100f).toInt().coerceIn(0, 200) else 0
        binding.pbCalories.progress = pct.coerceAtMost(100)

        binding.tvProtein.text = getString(R.string.cal_g_fmt, stats.protein.toInt())
        binding.tvFat.text     = getString(R.string.cal_g_fmt, stats.fat.toInt())
        binding.tvCarbs.text   = getString(R.string.cal_g_fmt, stats.carbs.toInt())

        binding.tvWater.text = getString(R.string.cal_water_fmt, stats.waterMl, waterGoal)
        binding.tvWeight.text = stats.weightKg?.let { getString(R.string.cal_weight_fmt, "%.1f".format(it)) } ?: ""

        binding.containerEntries.removeAllViews()
        if (stats.entries.isEmpty()) {
            binding.tvEmptyEntries.visibility = View.VISIBLE
            binding.containerEntries.visibility = View.GONE
        } else {
            binding.tvEmptyEntries.visibility = View.GONE
            binding.containerEntries.visibility = View.VISIBLE
            stats.entries.forEach { e ->
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(6), 0, dp(6))
                }
                val liquidKw = listOf("сок","вода","кофе","чай","молоко","кефир","морс","компот",
                    "лимонад","кола","напиток","смузи","коктейль","пиво","вино","квас","какао",
                    "латте","капучино","айран","ряженка")
                val u = if (liquidKw.any { e.productName.lowercase().contains(it) }) "мл" else "г"
                val name = TextView(requireContext()).apply {
                    text = "${e.productName} · ${e.grams.toInt()}$u"
                    setTextColor(resources.getColor(R.color.white, null))
                    textSize = 13f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                val cal = TextView(requireContext()).apply {
                    text = context.getString(R.string.cal_kcal_fmt, e.calories.toInt())
                    setTextColor(android.graphics.Color.parseColor("#FF7043"))
                    textSize = 13f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
                row.addView(name); row.addView(cal)
                binding.containerEntries.addView(row)
            }
        }
    }

    private fun formatMonthHeader(month: YearMonth): String {
        return com.nutritrack.app.utils.DateDisplay.monthYear(month.year, month.monthValue)
    }

    private fun humanizeDate(date: String): String {
        return try {
            val d = LocalDate.parse(date)
            val today = LocalDate.now()
            val dm = com.nutritrack.app.utils.DateDisplay.dayMonth(d)
            when {
                d == today -> getString(R.string.cal_today_date, dm)
                d == today.minusDays(1) -> getString(R.string.cal_yesterday_date, dm)
                else -> com.nutritrack.app.utils.DateDisplay.dayMonthYear(d)
            }
        } catch (_: Exception) { date }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
