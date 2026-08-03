package com.nutritrack.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nutritrack.app.databinding.ItemCalendarDayNewBinding
import com.nutritrack.app.viewmodel.DayStatus
import java.text.SimpleDateFormat
import java.util.*

class CalendarWeekAdapter(
    private val onClick: (DayStatus) -> Unit
) : ListAdapter<DayStatus, CalendarWeekAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemCalendarDayNewBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: DayStatus) {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(item.date)
            b.tvDayName.text = SimpleDateFormat("EEE", Locale.getDefault()).format(date ?: Date())
            b.tvDayNum.text = SimpleDateFormat("d", Locale.getDefault()).format(date ?: Date())

            val consumed = item.calories.toInt()
            val goal = item.calorieGoal
            val deficit = goal - consumed

            b.tvConsumed.text = b.root.context.getString(
                com.nutritrack.app.R.string.cal_day_kcal_fmt, consumed)

            // Deficit/surplus display
            when {
                consumed == 0 -> {
                    b.tvDeficit.text = "—"
                    b.tvDeficit.setTextColor(0xFF757575.toInt())
                }
                deficit > 0 -> {
                    b.tvDeficit.text = "−$deficit"
                    b.tvDeficit.setTextColor(0xFF69F0AE.toInt())
                }
                else -> {
                    b.tvDeficit.text = "+${-deficit}"
                    b.tvDeficit.setTextColor(0xFFFF5252.toInt())
                }
            }

            // Progress bar
            val percent = if (goal > 0) (consumed * 100 / goal).coerceAtMost(100) else 0
            b.progressDay.progress = percent

            // Color indicator
            val color = when {
                consumed == 0 -> 0xFF424242.toInt()
                percent >= 90 && item.water >= item.waterGoal * 0.9 -> 0xFF69F0AE.toInt()
                percent in 50..89 -> 0xFFFFD740.toInt()
                percent > 100 -> 0xFFFF5252.toInt()
                else -> 0xFF757575.toInt()
            }
            b.viewIndicator.setBackgroundColor(color)

            // Highlight today
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            b.root.strokeColor = if (item.date == today) 0xFF7C4DFF.toInt() else 0x00000000
            b.root.strokeWidth = if (item.date == today) 2 else 0

            // Future days
            b.root.alpha = if (item.date > today) 0.4f else 1f

            b.root.setOnClickListener { if (item.date <= today) onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCalendarDayNewBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DayStatus>() {
            override fun areItemsTheSame(a: DayStatus, b: DayStatus) = a.date == b.date
            override fun areContentsTheSame(a: DayStatus, b: DayStatus) = a == b
        }
    }
}