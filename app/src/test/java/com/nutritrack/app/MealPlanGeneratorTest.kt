package com.nutritrack.app

import com.nutritrack.app.data.RecipeData
import com.nutritrack.app.utils.MealPlanGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Генератор недельного меню: структура, диеты, избранные. */
class MealPlanGeneratorTest {

    @Test fun `план — ровно 7 дней, все слоты заполнены`() {
        val plan = MealPlanGenerator.generate("mass")
        assertEquals(7, plan.days.size)
        plan.days.forEach { day ->
            assertNotNull(day.breakfast)
            assertNotNull(day.lunch)
            assertNotNull(day.dinner)
        }
    }

    @Test fun `КБЖУ дня — сумма порций слотов`() {
        val plan = MealPlanGenerator.generate("fit")
        val day = plan.days.first()
        val expected = listOfNotNull(day.breakfast, day.lunch, day.dinner, day.snack)
            .sumOf { it.caloriesPerServing }
        assertEquals(expected, day.totalCalories)
    }

    @Test fun `избранный рецепт попадает в первый день`() {
        // Берём любой завтрак для цели mass и помечаем избранным
        val fav = RecipeData.forGoalAndMeal("mass", "breakfast").first()
        // Генерация с shuffle — прогоняем несколько раз: избранный всегда первым
        repeat(5) {
            val plan = MealPlanGenerator.generate("mass", favorites = setOf(fav.id))
            assertEquals(fav.id, plan.days.first().breakfast?.id)
        }
    }

    @Test fun `веган-фильтр не отдаёт мясные блюда`() {
        val plan = MealPlanGenerator.generate("fit", dietKey = "vegan")
        plan.days.forEach { day ->
            listOfNotNull(day.breakfast, day.lunch, day.dinner, day.snack).forEach { r ->
                assertTrue(
                    "Не веганский рецепт в веган-меню: ${r.name}",
                    com.nutritrack.app.utils.DietFilter.filter(listOf(r), "vegan").isNotEmpty()
                )
            }
        }
    }

    @Test fun `обед и ужин первого дня различаются`() {
        // Сдвиг индексов в генераторе должен разводить обед и ужин
        repeat(5) {
            val plan = MealPlanGenerator.generate("lose")
            val day = plan.days.first()
            if (day.lunch != null && day.dinner != null) {
                assertTrue(day.lunch!!.id != day.dinner!!.id)
            }
        }
    }
}
