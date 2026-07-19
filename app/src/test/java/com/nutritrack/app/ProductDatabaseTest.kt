package com.nutritrack.app

import com.nutritrack.app.data.ProductDatabase
import org.junit.Assert.assertTrue
import org.junit.Test

/** Встроенная база продуктов: непустая и без дублей по нормализованному имени. */
class ProductDatabaseTest {

    private fun norm(s: String): String = s
        .lowercase()
        .replace(Regex("[()]"), " ")
        .replace(Regex("[^a-zа-яё0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    @Test fun `база не пустая — цепочка plus собирает категории`() {
        // Защита от регресса, когда all возвращает listOf() без .plus(...)
        assertTrue("Встроенная база продуктов пуста", ProductDatabase.all.size > 300)
    }

    @Test fun `нет дублей по нормализованному имени`() {
        val groups = ProductDatabase.all.groupBy { norm(it.name) }
        val dupes = groups.filter { it.value.size > 1 }
        assertTrue(
            "Дубли продуктов: " + dupes.keys.joinToString(),
            dupes.isEmpty()
        )
    }
}
