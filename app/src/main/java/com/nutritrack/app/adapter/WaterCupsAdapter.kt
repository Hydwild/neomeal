package com.nutritrack.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nutritrack.app.R
import com.nutritrack.app.data.db.entity.WaterEntryEntity
import com.nutritrack.app.ui.custom.MiniGlassView

class WaterCupsAdapter(
    private val onLongPress: (WaterEntryEntity) -> Unit
) : ListAdapter<WaterEntryEntity, WaterCupsAdapter.VH>(DIFF) {

    // id стаканов, которые уже были анимированы — чтобы не наполнять заново при каждом обновлении
    private val animatedIds = HashSet<Long>()

    inner class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val glass: MiniGlassView = view.findViewById(R.id.glassCup)
        val tvAmount: TextView = view.findViewById(R.id.tvAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_water_cup, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.tvAmount.text = "${item.amountMl}мл"

        // Уровень наполнения стакана зависит от объёма (250мл = полный)
        val level = (item.amountMl / 250f).coerceIn(0.25f, 1f)

        if (animatedIds.contains(item.id)) {
            // Уже показывали — рисуем сразу наполненным без анимации
            holder.glass.fill(level)
        } else {
            // Новый стакан — анимируем наполнение
            animatedIds.add(item.id)
            holder.glass.post { holder.glass.fill(level) }
        }

        holder.itemView.setOnLongClickListener {
            onLongPress(item)
            true
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<WaterEntryEntity>() {
            override fun areItemsTheSame(a: WaterEntryEntity, b: WaterEntryEntity) = a.id == b.id
            override fun areContentsTheSame(a: WaterEntryEntity, b: WaterEntryEntity) = a == b
        }
    }
}
