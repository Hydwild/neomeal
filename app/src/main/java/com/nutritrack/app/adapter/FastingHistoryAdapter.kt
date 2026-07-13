package com.nutritrack.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nutritrack.app.data.db.entity.FastingSessionEntity
import com.nutritrack.app.databinding.ItemFastingHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class FastingHistoryAdapter :
    ListAdapter<FastingSessionEntity, FastingHistoryAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemFastingHistoryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: FastingSessionEntity) {
            val sdf = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
            b.tvDate.text = sdf.format(Date(item.startTime))
            b.tvProtocol.text = item.protocolName
            b.tvDuration.text = if (item.durationMinutes >= 60) {
                val h = item.durationMinutes / 60
                val m = item.durationMinutes % 60
                "${h}ч ${m}мин"
            } else "${item.durationMinutes} мин"

            b.tvStatus.text = if (item.isCompleted) "✅ Выполнено" else "❌ Прервано"
            b.tvStatus.setTextColor(
                if (item.isCompleted) 0xFF69F0AE.toInt() else 0xFFFF5252.toInt()
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemFastingHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FastingSessionEntity>() {
            override fun areItemsTheSame(a: FastingSessionEntity, b: FastingSessionEntity) = a.id == b.id
            override fun areContentsTheSame(a: FastingSessionEntity, b: FastingSessionEntity) = a == b
        }
    }
}