package com.nutritrack.app.utils

import android.content.Context
import android.widget.Toast
import com.nutritrack.app.R
import kotlin.random.Random

/**
 * «Компаньон» — тёплые слова поддержки при действиях пользователя.
 * Фразы берутся из ресурсов (локализуются). Антиспам: не чаще раза в 2 минуты,
 * срабатывает ≈35%, не повторяется подряд.
 */
object CompanionHelper {

    private fun arrayFor(event: String): Int = when (event) {
        "water"  -> R.array.cheer_water
        "food"   -> R.array.cheer_food
        "weight" -> R.array.cheer_weight
        "plan"   -> R.array.cheer_plan
        else     -> 0
    }

    fun maybeCheer(context: Context, event: String) {
        if (Random.nextFloat() > 0.35f) return

        val prefs = PrefsManager(context)
        val now = System.currentTimeMillis()
        val lastShown = prefs.getString("companion_last_time", "0").toLongOrNull() ?: 0L
        if (now - lastShown < 120_000) return

        val arrayRes = arrayFor(event)
        if (arrayRes == 0) return
        val list = context.resources.getStringArray(arrayRes).toList()
        if (list.isEmpty()) return

        val lastPhrase = prefs.getString("companion_last_phrase", "")
        val candidates = list.filter { it != lastPhrase }.ifEmpty { list }
        val phrase = candidates[Random.nextInt(candidates.size)]

        prefs.setString("companion_last_time", now.toString())
        prefs.setString("companion_last_phrase", phrase)
        Toast.makeText(context, phrase, Toast.LENGTH_SHORT).show()
    }
}
