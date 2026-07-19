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

    @Test fun `обед и ужин различаются каждый день`() {
        // avoid=lunch в генераторе должен разводить обед и ужин во все дни
        repeat(5) {
            val plan = MealPlanGenerator.generate("lose")
            plan.days.forEach { day ->
                if (day.lunch != null && day.dinner != null) {
                    assertTrue("Обед==ужин в дне ${day.dayName}", day.lunch!!.id != day.dinner!!.id)
                }
            }
        }
    }

    @Test fun `с целью — структура сохраняется`() {
        val plan = MealPlanGenerator.generate("fit", dailyCalorieTarget = 2000)
        assertEquals(7, plan.days.size)
        plan.days.forEach { day ->
            assertNotNull(day.breakfast)
            assertNotNull(day.lunch)
            assertNotNull(day.dinner)
        }
    }

    @Test fun `высокая цель тянет к самому калорийному блюду слота`() {
        // При огромной норме подбор минимизирует |кал − доля|, т.е. берёт максимум пула
        val pool = RecipeData.forGoalAndMeal("fit", "breakfast")
            .ifEmpty { RecipeData.forGoal("fit") }
        val maxCal = pool.maxOf { it.caloriesPerServing }
        repeat(5) {
            val plan = MealPlanGenerator.generate("fit", dailyCalorieTarget = 100_000)
            assertEquals(maxCal, plan.days.first().breakfast!!.caloriesPerServing)
        }
    }

    @Test fun `завтрак не повторяется, пока в пуле есть свежие блюда`() {
        // Первые N дней (N = размер пула завтраков) не должны повторять рецепт
        val poolSize = RecipeData.forGoalAndMeal("mass", "breakfast")
            .ifEmpty { RecipeData.forGoal("mass") }.size
        repeat(5) {
            val plan = MealPlanGenerator.generate("mass")
            val firstN = plan.days.take(poolSize).mapNotNull { it.breakfast?.id }
            assertEquals("Повтор завтрака в пределах свежего пула", firstN.size, firstN.toSet().size)
        }
    }
}
