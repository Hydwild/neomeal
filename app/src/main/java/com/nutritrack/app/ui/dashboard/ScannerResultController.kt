package com.nutritrack.app.ui.dashboard

import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.nutritrack.app.NutriApp
import com.nutritrack.app.R
import com.nutritrack.app.data.db.entity.DiaryEntryEntity
import com.nutritrack.app.data.db.entity.ProductEntity
import com.nutritrack.app.data.repository.FoodSearchRepository
import com.nutritrack.app.utils.AppLog
import com.nutritrack.app.utils.DateUtils
import com.nutritrack.app.utils.HealthConnectWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Обработка результата сканера штрихкодов: поиск продукта в OpenFoodFacts и
 * диалог добавления найденного в дневник (с сохранением как кастомный продукт
 * и дублированием в Health Connect). Вынесено из DashboardFragment.
 */
class ScannerResultController(
    private val fragment: Fragment,
    private val onProductAdded: () -> Unit,
) {
    private val ctx get() = fragment.requireContext()
    private val scope get() = fragment.viewLifecycleOwner.lifecycleScope
    private fun s(id: Int, vararg a: Any) = ctx.getString(id, *a)

    fun lookupBarcode(barcode: String) {
        scope.launch {
            try {
                val p = FoodSearchRepository.byBarcode(barcode)
                val n = p?.nutriments
                if (p != null && n != null) {
                    showFoundProductDialog(
                        name = p.bestName(),
                        calories = (n.energyKcal100g ?: 0.0).toFloat(),
                        protein = (n.proteins100g ?: 0.0).toFloat(),
                        fat = (n.fat100g ?: 0.0).toFloat(),
                        carbs = (n.carbs100g ?: 0.0).toFloat(),
                        fiber = n.fiber100g?.toFloat(),
                        sugar = n.sugars100g?.toFloat(),
                        startGrams = p.servingGrams() ?: 100
                    )
                } else {
                    Toast.makeText(ctx, s(R.string.ds_not_in_base), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                AppLog.e("barcode lookup failed", e)
                Toast.makeText(ctx, s(R.string.ds_net_error), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showFoundProductDialog(
        name: String, calories: Float, protein: Float, fat: Float, carbs: Float,
        fiber: Float? = null, sugar: Float? = null,
        startGrams: Int = 100
    ) {
        val input = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = s(R.string.ds_grams_hint)
            setText(startGrams.toString())
            setPadding(50, 30, 50, 30)
        }
        AlertDialog.Builder(ctx)
            .setTitle(name)
            .setMessage(s(R.string.ds_per100_fmt, calories.toInt(), protein.toInt(), fat.toInt(), carbs.toInt()))
            .setView(input)
            .setPositiveButton(s(R.string.c_add)) { _, _ ->
                val grams = input.text.toString().toFloatOrNull() ?: 100f
                scope.launch(Dispatchers.IO) {
                    val db = (fragment.requireActivity().application as NutriApp).database
                    val profile = db.profileDao().getActiveProfileSync() ?: return@launch
                    val factor = grams / 100f

                    // Сохраняем в свою базу как кастомный продукт
                    db.productDao().insertProduct(
                        ProductEntity(
                            name = name, calories = calories, protein = protein,
                            fat = fat, carbs = carbs, category = "Сканер", isCustom = true,
                            fiber = fiber, sugar = sugar
                        )
                    )
                    // Пишем в дневник (+ дублируем в Health Connect, если включено)
                    val entry = DiaryEntryEntity(
                        productId = 0, productName = name, grams = grams,
                        calories = calories * factor, protein = protein * factor,
                        fat = fat * factor, carbs = carbs * factor,
                        fiber = fiber?.times(factor), sugar = sugar?.times(factor),
                        date = DateUtils.today(), profileId = profile.id
                    )
                    db.diaryDao().insert(entry)
                    HealthConnectWriter.writeDiaryEntry(ctx.applicationContext, entry)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, s(R.string.ds_added2_fmt, name, grams.toInt()), Toast.LENGTH_SHORT).show()
                        onProductAdded()
                    }
                }
            }
            .setNegativeButton(s(R.string.c_cancel), null)
            .show()
    }
}
