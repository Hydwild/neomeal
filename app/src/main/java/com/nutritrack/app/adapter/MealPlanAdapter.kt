package com.nutritrack.app.adapter

import com.nutritrack.app.R

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nutritrack.app.data.db.entity.MealItemEntity
import com.nutritrack.app.databinding.ItemMealItemBinding

class MealItemsAdapter(
    private val onLongPress: ((MealItemEntity) -> Unit)? = null
) : ListAdapter<MealItemEntity, MealItemsAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemMealItemBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: MealItemEntity) {
            b.tvItemName.text = b.root.context.getString(R.string.mp_item_fmt, com.nutritrack.app.utils.ProductNameLabel.of(b.root.context, item.productName), item.grams.toInt())
            b.tvItemCal.text  = "${item.calories.toInt()} ккал"
            b.tvItemMacros.text =
                "Б:${item.protein.toInt()} Ж:${item.fat.toInt()} У:${item.carbs.toInt()}"

            // Долгое нажатие удалить
            b.root.setOnLongClickListener {
                onLongPress?.invoke(item)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemMealItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MealItemEntity>() {
            override fun areItemsTheSame(a: MealItemEntity, b: MealItemEntity) = a.id == b.id
            override fun areContentsTheSame(a: MealItemEntity, b: MealItemEntity) = a == b
        }
    }
}
