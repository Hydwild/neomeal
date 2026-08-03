package com.nutritrack.app.ui.dashboard

import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.nutritrack.app.NutriApp
import com.nutritrack.app.R
import com.nutritrack.app.adapter.DiaryAdapter
import com.nutritrack.app.data.db.entity.DiaryEntryEntity
import com.nutritrack.app.data.db.entity.MealFeedbackEntity
import com.nutritrack.app.databinding.FragmentDashboardBinding
import com.nutritrack.app.utils.ActiveProfile
import com.nutritrack.app.utils.DateUtils
import com.nutritrack.app.utils.NutritionWarnings
import com.nutritrack.app.utils.PrefsManager
import com.nutritrack.app.utils.ProductNameLabel
import com.nutritrack.app.utils.ProductUnits
import com.nutritrack.app.viewmodel.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Диалоги и действия над записями дневника: редактирование грамм, удаление с
 * undo-снекбаром, оценка самочувствия, предупреждения о проблемных продуктах.
 * Вынесено из DashboardFragment. [adapterOf] возвращает адаптер дневника (создаётся
 * в setupDiaryList), [bindingOf] — актуальный binding или null.
 */
class DiaryEntryController(
    private val fragment: Fragment,
    private val vm: DashboardViewModel,
    private val bindingOf: () -> FragmentDashboardBinding?,
    private val adapterOf: () -> DiaryAdapter,
) {
    private val ctx get() = fragment.requireContext()
    private val prefs = PrefsManager(fragment.requireContext())
    private val scope get() = fragment.viewLifecycleOwner.lifecycleScope
    private fun s(id: Int, vararg a: Any) = ctx.getString(id, *a)

    /** Диалог изменения количества грамм у записи дневника. */
    fun showEditEntryDialog(entry: DiaryEntryEntity) {
        val isLiquid = ProductUnits.isLiquidName(entry.productName)
        val unit = s(if (isLiquid) R.string.u_ml else R.string.u_g)
        val input = android.widget.EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(entry.grams.toInt().toString())
            setSelection(text.length)
        }
        val container = android.widget.FrameLayout(ctx).apply {
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
            addView(input)
        }
        AlertDialog.Builder(ctx)
            .setTitle(s(R.string.ds_edit_title_fmt, entry.productName))
            .setMessage(if (isLiquid) s(R.string.ds_how_much_ml) else s(R.string.ds_how_much_g))
            .setView(container)
            .setPositiveButton(s(R.string.c_save)) { _, _ ->
                val newGrams = input.text.toString().toFloatOrNull()
                if (newGrams != null && newGrams > 0f) {
                    vm.updateDiaryEntry(entry, newGrams)
                    Toast.makeText(ctx, "✅ ${entry.productName} — ${newGrams.toInt()}$unit",
                        Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton(s(R.string.c_delete)) { _, _ -> deleteEntryWithUndo(entry) }
            .setNegativeButton(s(R.string.c_cancel), null)
            .show()
    }

    /** Удаление записи дневника со снекбаром «Отменить» (вместо подтверждения). */
    fun deleteEntryWithUndo(entry: DiaryEntryEntity) {
        vm.deleteDiaryEntry(entry)
        val b = bindingOf() ?: return
        val name = ProductNameLabel.of(ctx, entry.productName)
        val bar = Snackbar.make(b.root, s(R.string.sb_deleted_fmt, name), Snackbar.LENGTH_LONG)
            .setAction(R.string.c_undo) { vm.restoreDiaryEntry(entry) }
        fragment.activity?.findViewById<View>(R.id.bottom_navigation)
            ?.takeIf { it.visibility == View.VISIBLE }
            ?.let { bar.anchorView = it }
        bar.show()
    }

    /** Диалог оценки самочувствия после продукта (4 уровня). */
    fun showMealFeedbackDialog(entry: DiaryEntryEntity) {
        val ratings = listOf(
            "great" to s(R.string.feedback_great),
            "ok" to s(R.string.feedback_ok),
            "discomfort" to s(R.string.feedback_discomfort),
            "bad" to s(R.string.feedback_bad)
        )
        val labels = ratings.map { it.second }.toTypedArray()
        val name = ProductNameLabel.of(ctx, entry.productName)
        AlertDialog.Builder(ctx)
            .setTitle(s(R.string.feedback_title_fmt, name))
            .setItems(labels) { _, which -> saveMealFeedback(entry, ratings[which].first) }
            .setNegativeButton(s(R.string.c_cancel), null)
            .show()
    }

    private fun saveMealFeedback(entry: DiaryEntryEntity, rating: String) {
        val app = ctx.applicationContext as NutriApp
        scope.launch {
            withContext(Dispatchers.IO) {
                val profileId = ActiveProfile.id(ctx, app.database)
                app.database.mealFeedbackDao().insert(
                    MealFeedbackEntity(
                        productId = entry.productId,
                        productName = entry.productName,
                        rating = rating,
                        date = DateUtils.today(),
                        profileId = profileId
                    )
                )
            }
            Toast.makeText(ctx, s(R.string.feedback_saved), Toast.LENGTH_SHORT).show()
            vm.todayEntries.value?.let { refreshDiaryWarnings(it) }
        }
    }

    /** Собирает предупреждения: превышение нормы + проблемные продукты. */
    fun refreshDiaryWarnings(entries: List<DiaryEntryEntity>) {
        val base = NutritionWarnings.analyze(ctx, entries).toMutableMap()
        val app = ctx.applicationContext as NutriApp
        scope.launch {
            val problemEntryIds = withContext(Dispatchers.IO) {
                val profileId = ActiveProfile.id(ctx, app.database)
                val dao = app.database.mealFeedbackDao()
                val result = mutableListOf<Long>()
                val byProduct = entries.groupBy { it.productId }
                for ((pid, group) in byProduct) {
                    if (pid <= 0) continue
                    if (dao.negativeCountForProduct(pid, profileId) >= 2) {
                        group.forEach { result.add(it.id) }
                    }
                }
                result
            }
            val warningText = s(R.string.feedback_problem_hint)
            problemEntryIds.forEach { id ->
                val existing = base[id]
                base[id] = if (existing.isNullOrBlank()) warningText else "$existing\n$warningText"
            }
            adapterOf().setWarnings(base)
        }
    }
}
