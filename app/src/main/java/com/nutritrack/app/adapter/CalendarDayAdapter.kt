package com.nutritrack.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nutritrack.app.databinding.ItemCalendarDayBinding
import com.nutritrack.app.viewmodel.DayStatus
import java.text.SimpleDateFormat
import java.util.*

class CalendarDayAdapter : ListAdapter<DayStatus, CalendarDayAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemCalendarDayBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: DayStatus) {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(item.date)
            val dayName = SimpleDateFormat("EEE", Locale.getDefault()).format(date ?: Date())
            val dayNum = SimpleDateFormat("d", Locale.getDefault()).format(date ?: Date())

            b.tvDayName.text = dayName
            b.tvDayNum.text = dayNum
            b.tvCalories.text = "${item.calories.toInt()}\nккал"

            val calPercent = (item.calories / item.calorieGoal).coerceIn(0f, 1f)
            val waterPercent = (item.water.toFloat() / item.waterGoal).coerceIn(0f, 1f)

            val color = when {
                calPercent >= 0.9f && waterPercent >= 0.9f -> 0xFF69F0AE.toInt() // green
                calPercent >= 0.5f -> 0xFFFFD740.toInt()                          // yellow
                else -> 0xFF757575.toInt()                                        // grey
            }
            b.viewIndicator.setBackgroundColor(color)

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            b.root.alpha = if (item.date > today) 0.4f else 1f
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCalendarDayBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DayStatus>() {
            override fun areItemsTheSame(a: DayStatus, b: DayStatus) = a.date == b.date
            override fun areContentsTheSame(a: DayStatus, b: DayStatus) = a == b
        }
    }
}