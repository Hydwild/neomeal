package com.nutritrack.app.adapter

import com.nutritrack.app.R

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nutritrack.app.data.db.entity.ProductEntity
import com.nutritrack.app.databinding.ItemProductPickerBinding

class ProductPickerAdapter(
    private val onSelect: (ProductEntity) -> Unit,
    private val onFavoriteToggle: (ProductEntity) -> Unit = {}
) : ListAdapter<ProductEntity, ProductPickerAdapter.VH>(DIFF) {

    private var selectedPos = -1

    inner class VH(private val b: ItemProductPickerBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: ProductEntity, isSelected: Boolean) {
            b.tvName.text = com.nutritrack.app.utils.ProductNameLabel.of(b.root.context, item.name)
            b.tvCategory.text = com.nutritrack.app.utils.CategoryLabel.of(b.root.context, item.category)
            b.tvCaloriesPer100.text = b.root.context.getString(R.string.pick_kcal_100_fmt, item.calories.toInt())
            b.root.isSelected = isSelected

            // Звёздочка: заполненная = в избранном, пустая = нет
            b.btnFavorite.setImageResource(
                if (item.isFavorite) android.R.drawable.btn_star_big_on
                else android.R.drawable.btn_star_big_off
            )
            b.btnFavorite.setOnClickListener {
                onFavoriteToggle(item)
            }

            b.root.setOnClickListener {
                val prev = selectedPos
                selectedPos = adapterPosition
                notifyItemChanged(prev)
                notifyItemChanged(selectedPos)
                onSelect(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemProductPickerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position), position == selectedPos)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ProductEntity>() {
            override fun areItemsTheSame(a: ProductEntity, b: ProductEntity) = a.id == b.id
            override fun areContentsTheSame(a: ProductEntity, b: ProductEntity) = a == b
        }
    }
}
