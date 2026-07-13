package com.nutritrack.app.adapter

import com.nutritrack.app.R

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nutritrack.app.databinding.ItemFastingProtocolBinding
import com.nutritrack.app.utils.FastingProtocol

class FastingProtocolAdapter(
    private val onSelect: (FastingProtocol) -> Unit
) : ListAdapter<FastingProtocol, FastingProtocolAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemFastingProtocolBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: FastingProtocol) {
            b.tvIcon.text = item.icon
            b.tvName.text = com.nutritrack.app.utils.DataLabel.tr(itemView.context, item.name)
            b.tvDescription.text = com.nutritrack.app.utils.DataLabel.tr(itemView.context, item.shortDescription)
            b.tvDifficulty.text = com.nutritrack.app.utils.DataLabel.tr(itemView.context, item.difficulty)
            b.tvDifficulty.setTextColor(
                when (item.difficulty) {
                    "Очень лёгкий" -> 0xFF69F0AE.toInt()
                    "Лёгкий" -> 0xFF69F0AE.toInt()
                    "Средний" -> 0xFFFFD740.toInt()
                    "Сложный" -> 0xFFFF9800.toInt()
                    else -> 0xFFFF5252.toInt()
                }
            )
            if (item.fastingHours > 0) {
                b.tvHours.text = itemView.context.getString(R.string.fp_hours_fmt, item.fastingHours, item.eatingHours)
            } else {
                b.tvHours.text = itemView.context.getString(R.string.fp_52_desc)
            }
            b.root.setOnClickListener { onSelect(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemFastingProtocolBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FastingProtocol>() {
            override fun areItemsTheSame(a: FastingProtocol, b: FastingProtocol) = a.id == b.id
            override fun areContentsTheSame(a: FastingProtocol, b: FastingProtocol) = a == b
        }
    }
}