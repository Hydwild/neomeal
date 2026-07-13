package com.nutritrack.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nutritrack.app.data.db.entity.DiaryEntryEntity
import com.nutritrack.app.databinding.ItemDiaryBinding

class DiaryAdapter(
    private val onDelete: ((DiaryEntryEntity) -> Unit)? = null,  // nullable, опциональный
    private val onEdit:   ((DiaryEntryEntity) -> Unit)? = null,  // долгий тап — редактирование грамм
    private val onPhoto:  ((DiaryEntryEntity) -> Unit)? = null,  // 📷 фото блюда
    private val onRate:   ((DiaryEntryEntity) -> Unit)? = null   // 😊 оценка самочувствия
) : ListAdapter<DiaryEntryEntity, DiaryAdapter.ViewHolder>(DIFF) {

    // Карта цен: productId цена за 100г (null если цена не задана)
    private var priceMap: Map<Long, Float?> = emptyMap()

    fun setPriceMap(map: Map<Long, Float?>) {
        priceMap = map
        notifyDataSetChanged()
    }

    // Карта составов комбо: productId → "имя:граммы;..." (null если не комбо)
    private var comboMap: Map<Long, String?> = emptyMap()

    fun setComboMap(map: Map<Long, String?>) {
        comboMap = map
        notifyDataSetChanged()
    }

    // Карта предупреждений: entryId текст
    private var warningsMap: Map<Long, String> = emptyMap()

    fun setWarnings(map: Map<Long, String>) {
        warningsMap = map
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemDiaryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DiaryEntryEntity) {
            binding.apply {
                binding.tvName.text = com.nutritrack.app.utils.ProductNameLabel.of(binding.root.context, item.productName)
                val unit = if (isLiquidName(item.productName)) "мл" else "г"

                // Стоимость порции, если у продукта задана цена
                val price = priceMap[item.productId]
                val costStr = if (price != null && price > 0f) {
                    val cost = item.grams / 100f * price
                    "  •  ${cost.toInt()} ₽"
                } else ""

                binding.tvDetails.text = "${item.grams.toInt()}$unit  •  Б:${item.protein.toInt()} " +
                        "Ж:${item.fat.toInt()} У:${item.carbs.toInt()}$costStr"
                binding.tvCalories.text = "${item.calories.toInt()} ккал"

                // Состав комбо (если продукт — комбо)
                val combo = comboMap[item.productId]
                if (!combo.isNullOrBlank()) {
                    val ctx = binding.root.context
                    val parts = combo.split(";").mapNotNull { piece ->
                        val idx = piece.lastIndexOf(":")
                        if (idx <= 0) return@mapNotNull null
                        val nm = piece.substring(0, idx)
                        val gr = piece.substring(idx + 1)
                        val label = com.nutritrack.app.utils.ProductNameLabel.of(ctx, nm)
                        "$label ${gr}г"
                    }
                    if (parts.isNotEmpty()) {
                        binding.tvCombo.text = "🍱 " + parts.joinToString(", ")
                        binding.tvCombo.visibility = View.VISIBLE
                    } else binding.tvCombo.visibility = View.GONE
                } else {
                    binding.tvCombo.visibility = View.GONE
                }

                // Фото блюда: миниатюра, если есть; кнопка — если обработчик передан
                if (item.photoUri != null) {
                    binding.ivMealPhoto.visibility = View.VISIBLE
                    try {
                        binding.ivMealPhoto.setImageURI(android.net.Uri.parse(item.photoUri))
                    } catch (_: Exception) { binding.ivMealPhoto.visibility = View.GONE }
                    binding.ivMealPhoto.setOnClickListener { onPhoto?.invoke(item) }
                } else {
                    binding.ivMealPhoto.visibility = View.GONE
                }
                if (onPhoto != null) {
                    binding.btnPhoto.visibility = View.VISIBLE
                    binding.btnPhoto.setOnClickListener { onPhoto.invoke(item) }
                } else {
                    binding.btnPhoto.visibility = View.GONE
                }

                // Предупреждение под продуктом
                val warning = warningsMap[item.id]
                if (warning != null) {
                    binding.tvWarning.text = "⚠️ $warning"
                    binding.tvWarning.visibility = View.VISIBLE
                } else {
                    binding.tvWarning.visibility = View.GONE
                }

                // Оценка самочувствия
                if (onRate != null) {
                    binding.btnRate.visibility = View.VISIBLE
                    binding.btnRate.setOnClickListener { onRate.invoke(item) }
                } else {
                    binding.btnRate.visibility = View.GONE
                }

                // Показываем кнопку удаления только если передан обработчик
                if (onDelete != null) {
                    binding.btnDelete.visibility = View.VISIBLE
                    binding.btnDelete.setOnClickListener { onDelete.invoke(item) }
                } else {
                    binding.btnDelete.visibility = View.GONE
                }

                // Долгий тап — редактирование (если передан обработчик)
                if (onEdit != null) {
                    binding.root.setOnLongClickListener {
                        onEdit.invoke(item); true
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDiaryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

 /** Напиток ли это (по названию) — для показа "мл" вместо "г". */
    private fun isLiquidName(name: String): Boolean {
        val n = name.lowercase()
        return LIQUID_KEYWORDS.any { n.contains(it) }
    }

    companion object {
        private val LIQUID_KEYWORDS = listOf(
            "сок", "вода", "кофе", "чай", "молоко", "кефир", "морс",
            "компот", "лимонад", "кола", "напиток", "смузи", "коктейль",
            "пиво", "вино", "квас", "какао", "латте", "капучино", "айран",
            "ряженка", "йогурт питьевой", "тан "
        )

        private val DIFF = object : DiffUtil.ItemCallback<DiaryEntryEntity>() {
            override fun areItemsTheSame(oldItem: DiaryEntryEntity, newItem: DiaryEntryEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: DiaryEntryEntity, newItem: DiaryEntryEntity) =
                oldItem == newItem
        }
    }
}