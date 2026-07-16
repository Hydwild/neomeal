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
            val ctx = b.root.context
            // Имя: сначала словарь продуктов, затем словарь рецептов
            // (блюда из конструктора хранятся под русским именем рецепта)
            val viaProducts = com.nutritrack.app.utils.ProductNameLabel.of(ctx, item.productName)
            val name = if (viaProducts != item.productName) viaProducts
                       else com.nutritrack.app.utils.RecipeLabel.tr(ctx, item.productName)
            b.tvItemName.text = ctx.getString(R.string.mp_item_fmt, name, item.grams.toInt())
            b.tvItemCal.text  = ctx.getString(R.string.cal_kcal_fmt, item.calories.toInt())
            b.tvItemMacros.text = ctx.getString(R.string.mp_macros_fmt,
                item.protein.toInt(), item.fat.toInt(), item.carbs.toInt())

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
