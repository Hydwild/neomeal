package com.nutritrack.app.adapter

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nutritrack.app.R
import com.nutritrack.app.viewmodel.AchievementDisplayItem

class AchievementsAdapter(
    private val onClick: (AchievementDisplayItem) -> Unit
) : ListAdapter<AchievementDisplayItem, AchievementsAdapter.VH>(DIFF) {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvIcon:      TextView     = itemView.findViewById(R.id.tvIcon)
        private val tvTitle:     TextView     = itemView.findViewById(R.id.tvTitle)
        private val tvLocked:    TextView     = itemView.findViewById(R.id.tvLocked)
        private val bgRarity:    LinearLayout = itemView.findViewById(R.id.bgRarity)
        private val progressBar: ProgressBar  = itemView.findViewById(R.id.progressBar)

        fun bind(item: AchievementDisplayItem) {
            tvIcon.text  = item.def.icon
            tvTitle.text = com.nutritrack.app.utils.DataLabel.tr(itemView.context, item.def.title)

            // Градиентный фон + цветная рамка по редкости
            bgRarity.background = rarityBackground(item.def.rarity, item.isUnlocked)

            if (item.isUnlocked) {
                itemView.alpha = 1f
                tvIcon.alpha   = 1f
                tvTitle.alpha  = 1f
                tvLocked.visibility    = View.GONE
                progressBar.visibility = View.GONE
            } else {
                itemView.alpha = 1f       // прозрачность даём не всей карточке, а содержимому
                tvIcon.alpha   = 0.4f
                tvTitle.alpha  = 0.6f
                tvLocked.visibility = View.VISIBLE

                if (item.def.maxProgress > 1) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.max        = item.def.maxProgress
                    progressBar.progress   = item.progress
                } else {
                    progressBar.visibility = View.GONE
                }
            }

            itemView.setOnClickListener { onClick(item) }
        }

 /**
 * Градиент (по диагонали) + рамка в цвет редкости.
 * Разблокированные — яркие, закрытые — тусклый серый.
 */
        private fun rarityBackground(rarity: String, unlocked: Boolean): GradientDrawable {
            val corner = dp(14f)

            if (!unlocked) {
                // Закрытое достижение — приглушённый серый фон + серая рамка
                return GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xFF1F1A33.toInt(), 0xFF14101F.toInt())
                ).apply {
                    cornerRadius = corner
                    setStroke(dpInt(1), 0xFF3D3358.toInt())
                }
            }

            // Цвета: (градиент начало, градиент конец, рамка)
            val (gradStart, gradEnd, border) = when (rarity) {
                "legendary" -> Triple(0xFF4A3708.toInt(), 0xFF1A1206.toInt(), 0xFFFFB300.toInt())
                "epic"      -> Triple(0xFF3A2150.toInt(), 0xFF160C1F.toInt(), 0xFFAB47BC.toInt())
                "rare"      -> Triple(0xFF132C4D.toInt(), 0xFF0A1220.toInt(), 0xFF42A5F5.toInt())
                else        -> Triple(0xFF143322.toInt(), 0xFF0A140E.toInt(), 0xFF66BB6A.toInt())
            }

            return GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(gradStart, gradEnd)
            ).apply {
                cornerRadius = corner
                setStroke(dpInt(2), border)
            }
        }

        private fun dp(v: Float): Float =
            v * itemView.context.resources.displayMetrics.density

        private fun dpInt(v: Int): Int =
            (v * itemView.context.resources.displayMetrics.density).toInt()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_achievement, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AchievementDisplayItem>() {
            override fun areItemsTheSame(a: AchievementDisplayItem, b: AchievementDisplayItem) =
                a.def.id == b.def.id
            override fun areContentsTheSame(a: AchievementDisplayItem, b: AchievementDisplayItem) =
                a.isUnlocked == b.isUnlocked && a.progress == b.progress
        }
    }
}
