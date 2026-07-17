package com.nutritrack.app.adapter

import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nutritrack.app.R
import com.nutritrack.app.data.db.entity.ProductEntity
import com.nutritrack.app.databinding.ItemProductBinding
import com.nutritrack.app.utils.ProductTipsHelper

class ProductsAdapter(
    private val onEdit:       (ProductEntity) -> Unit,
    private val onDelete:     (ProductEntity) -> Unit,
    private val onAddToDiary: (ProductEntity) -> Unit
) : ListAdapter<ProductEntity, ProductsAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemProductBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(item: ProductEntity) {

            // Текст
            b.tvName.text     = com.nutritrack.app.utils.ProductNameLabel.of(b.root.context, item.name)
            b.tvCategory.text = com.nutritrack.app.utils.CategoryLabel.of(b.root.context, item.category)
            b.tvCalories.text = b.root.context.getString(R.string.pa_kcal_fmt, item.calories.toInt())
            b.tvMacros.text   =
                b.root.context.getString(R.string.pa_macro_fmt, item.protein.toInt(), item.fat.toInt(), item.carbs.toInt())

            // Фото
            if (!item.photoUri.isNullOrEmpty()) {
                Glide.with(b.root.context)
                    .load(Uri.parse(item.photoUri))
                    .placeholder(R.drawable.ic_food_placeholder)
                    .error(R.drawable.ic_food_placeholder)
                    .circleCrop()
                    .into(b.ivPhoto)
            } else {
                Glide.with(b.root.context).clear(b.ivPhoto)
                b.ivPhoto.setImageResource(R.drawable.ic_food_placeholder)
            }

            // Автосовет
            val tip = com.nutritrack.app.utils.TipLabel.of(b.root.context, item.autoTip ?: ProductTipsHelper.getAutoTip(item.name, item.category))
            if (!tip.isNullOrEmpty()) {
                b.tvAutoTip.text       = tip
                b.tvAutoTip.visibility = View.VISIBLE
            } else {
                b.tvAutoTip.visibility = View.GONE
            }

            // Ручная пометка
            if (!item.manualNote.isNullOrEmpty()) {
                b.tvManualNote.text       = b.root.context.getString(R.string.pa_note_fmt, item.manualNote)
                b.tvManualNote.visibility = View.VISIBLE
            } else {
                b.tvManualNote.visibility = View.GONE
            }

            // Цена
            if (item.price != null && item.price > 0f) {
                b.tvPrice.text       = b.root.context.getString(R.string.pa_price_fmt, item.price.toInt())
                b.tvPrice.visibility = View.VISIBLE
            } else {
                b.tvPrice.visibility = View.GONE
            }

            // Кнопки: добавить + редактировать (БЕЗ УДАЛЕНИЯ)
            b.btnAdd.setOnClickListener  { onAddToDiary(item) }
            b.btnEdit.setOnClickListener { onEdit(item) }

            // Долгое нажатие контекстное меню
            b.root.setOnLongClickListener {
                showContextMenu(b.root.context, item)
                true
            }
        }

        // Контекстное меню
        private fun showContextMenu(ctx: Context, item: ProductEntity) {
            val options = buildList {
                add(ctx.getString(R.string.pa_note_add))
                // Удаление доступно только для добавленных вручную продуктов
                if (item.isCustom) add(ctx.getString(R.string.pa_delete_product))
            }

            AlertDialog.Builder(ctx)
                .setTitle(com.nutritrack.app.utils.ProductNameLabel.of(b.root.context, item.name))
                .setItems(options.toTypedArray()) { _, idx ->
                    when (idx) {
                        0 -> showNoteDialog(ctx, item)
                        1 -> confirmDelete(ctx, item)
                    }
                }
                .setNegativeButton(ctx.getString(R.string.c_cancel), null)
                .show()
        }

        // Пометка
        private fun showNoteDialog(ctx: Context, item: ProductEntity) {
            val input = EditText(ctx).apply {
                setText(item.manualNote ?: "")
                hint = ctx.getString(R.string.pa_note_hint)
                setPadding(48, 24, 48, 24)
            }
            AlertDialog.Builder(ctx)
                .setTitle(b.root.context.getString(R.string.pad_note_title_fmt, com.nutritrack.app.utils.ProductNameLabel.of(b.root.context, item.name)))
                .setView(input)
                .setPositiveButton(ctx.getString(R.string.c_save)) { _, _ ->
                    onEdit(item.copy(manualNote = input.text.toString().trim().ifEmpty { null }))
                }
                .setNeutralButton(ctx.getString(R.string.pa_note_delete)) { _, _ ->
                    onEdit(item.copy(manualNote = null))
                }
                .setNegativeButton(ctx.getString(R.string.c_cancel), null)
                .show()
        }

        // Подтверждение удаления
        private fun confirmDelete(ctx: Context, item: ProductEntity) {
            AlertDialog.Builder(ctx)
                .setTitle(b.root.context.getString(R.string.pad_delete_fmt, com.nutritrack.app.utils.ProductNameLabel.of(b.root.context, item.name)))
                .setMessage(ctx.getString(R.string.pa_delete_msg))
                .setPositiveButton(ctx.getString(R.string.c_delete)) { _, _ -> onDelete(item) }
                .setNegativeButton(ctx.getString(R.string.c_cancel), null)
                .show()
        }
    }

    // ListAdapter boilerplate

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ProductEntity>() {
            override fun areItemsTheSame(a: ProductEntity, b: ProductEntity) = a.id == b.id
            override fun areContentsTheSame(a: ProductEntity, b: ProductEntity) = a == b
        }
    }
}
