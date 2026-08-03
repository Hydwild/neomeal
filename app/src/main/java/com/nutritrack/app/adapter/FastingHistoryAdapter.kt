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
            val ctx = b.root.context
            b.tvDuration.text = if (item.durationMinutes >= 60) {
                val h = item.durationMinutes / 60
                val m = item.durationMinutes % 60
                ctx.getString(com.nutritrack.app.R.string.fh_dur_hm_fmt, h, m)
            } else ctx.getString(com.nutritrack.app.R.string.fh_dur_m_fmt, item.durationMinutes)

            b.tvStatus.text = ctx.getString(
                if (item.isCompleted) com.nutritrack.app.R.string.fasting_status_done
                else com.nutritrack.app.R.string.fasting_status_broken)
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