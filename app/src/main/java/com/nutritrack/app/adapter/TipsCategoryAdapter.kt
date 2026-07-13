package com.nutritrack.app.adapter

import com.nutritrack.app.R

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nutritrack.app.databinding.ItemTipsCategoryBinding
import com.nutritrack.app.utils.TipsCategory

class TipsCategoryAdapter :
    ListAdapter<TipsCategory, TipsCategoryAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemTipsCategoryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: TipsCategory) {
            // Заголовок вида " Поддержание веса" — отделяем эмодзи в кружок
            val parts = item.title.trim().split(" ", limit = 2)
            val firstIsEmoji = parts[0].isNotEmpty() && !parts[0][0].isLetterOrDigit()
            if (firstIsEmoji && parts.size == 2) {
                b.tvCategoryEmoji.text = parts[0]
                b.tvCategoryTitle.text = com.nutritrack.app.utils.DataLabel.tr(b.root.context, parts[1])
            } else {
                b.tvCategoryEmoji.text = item.icon.ifEmpty { "💡" }
                b.tvCategoryTitle.text = com.nutritrack.app.utils.DataLabel.tr(b.root.context, item.title)
            }

            val count = item.tips.size
            b.tvTipsCount.text = "$count " + b.root.context.resources.getQuantityString(R.plurals.tips_count, count)

            // Цветной акцент по позиции (циклично)
            val accents = listOf("#FF9E80", "#69F0AE", "#B388FF", "#40C4FF", "#FFD740", "#FF6E40")
            val color = android.graphics.Color.parseColor(accents[adapterPosition % accents.size])
            b.accentBar.setBackgroundColor(color)
            b.tvTipsCount.setTextColor(color)

            val tipsAdapter = TipsItemAdapter()
            b.rvTips.layoutManager = LinearLayoutManager(b.root.context)
            b.rvTips.adapter = tipsAdapter

            if (item.isExpanded) {
                tipsAdapter.submitList(item.tips)
                b.rvTips.visibility = android.view.View.VISIBLE
                b.ivArrow.rotation = 90f
            } else {
                b.rvTips.visibility = android.view.View.GONE
                b.ivArrow.rotation = 0f
            }

            b.headerRow.setOnClickListener {
                item.isExpanded = !item.isExpanded
                notifyItemChanged(adapterPosition)
            }
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemTipsCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TipsCategory>() {
            override fun areItemsTheSame(a: TipsCategory, b: TipsCategory) = a.title == b.title
            override fun areContentsTheSame(a: TipsCategory, b: TipsCategory) = a.title == b.title && a.isExpanded == b.isExpanded
        }
    }
}