package com.nutritrack.app

import com.nutritrack.app.data.db.entity.ProductEntity
import com.nutritrack.app.utils.ProductUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Определение «жидких» продуктов и шаги порций. */
class ProductUnitsTest {

    private fun product(name: String, category: String = "Другое") = ProductEntity(
        name = name, calories = 100f, protein = 1f, fat = 1f, carbs = 1f, category = category
    )

    @Test fun `напитки по названию`() {
        assertTrue(ProductUnits.isLiquidName("Сок яблочный"))
        assertTrue(ProductUnits.isLiquidName("Кофе с молоком"))
        assertTrue(ProductUnits.isLiquidName("Кефир 1%"))
    }

    @Test fun `не напитки`() {
        assertFalse(ProductUnits.isLiquidName("Гречка"))
        assertFalse(ProductUnits.isLiquidName("Куриная грудка"))
    }

    @Test fun `категория Напитки — жидкость независимо от названия`() {
        assertTrue(ProductUnits.isLiquid(product("Эспрессо-тоник", category = "Напитки")))
    }

    @Test fun `единица измерения`() {
        assertEquals("мл", ProductUnits.unit(product("Вода минеральная")))
        assertEquals("г", ProductUnits.unit(product("Овсяные хлопья")))
    }

    @Test fun `стартовая порция — 250 мл для напитков, 100 г для еды`() {
        assertEquals(250, ProductUnits.defaultAmount(product("Чай зелёный")))
        assertEquals(100, ProductUnits.defaultAmount(product("Рис")))
    }

    @Test fun `шаг порции растёт с количеством`() {
        val solid = product("Рис")
        assertTrue(ProductUnits.step(solid, 50) < ProductUnits.step(solid, 200))
    }
}
