package com.nutritrack.app

import com.nutritrack.app.utils.DietFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты фильтрации продуктов по диете (matchesProductName / productPassesDiets).
 * Отдельно проверяем баг, который чинили: веган не должен получать печень,
 * но печенье (выпечка) блокироваться не должно.
 */
class DietFilterTest {

    @Test
    fun vegan_rejects_meat() {
        assertFalse(DietFilter.matchesProductName("Куриная грудка", "vegan"))
        assertFalse(DietFilter.matchesProductName("Говядина", "vegan"))
    }

    @Test
    fun vegan_rejects_liver() {
        // именно этот случай чинили — печень как источник B12/железа
        assertFalse(DietFilter.matchesProductName("Печень куриная", "vegan"))
        assertFalse(DietFilter.matchesProductName("Печёнка говяжья", "vegan"))
    }

    @Test
    fun vegan_allows_cookies() {
        // "печенье" не должно ловиться как "печень"
        assertTrue(DietFilter.matchesProductName("Печенье овсяное", "vegan"))
    }

    @Test
    fun vegan_rejects_fish_egg_dairy() {
        assertFalse(DietFilter.matchesProductName("Лосось", "vegan"))
        assertFalse(DietFilter.matchesProductName("Яйцо куриное", "vegan"))
        assertFalse(DietFilter.matchesProductName("Молоко", "vegan"))
    }

    @Test
    fun vegan_allows_plants() {
        assertTrue(DietFilter.matchesProductName("Гречка", "vegan"))
        assertTrue(DietFilter.matchesProductName("Яблоко", "vegan"))
        assertTrue(DietFilter.matchesProductName("Тофу", "vegan"))
    }

    @Test
    fun vegetarian_allows_dairy_and_egg() {
        assertTrue(DietFilter.matchesProductName("Молоко", "vegetarian"))
        assertTrue(DietFilter.matchesProductName("Яйцо куриное", "vegetarian"))
    }

    @Test
    fun vegetarian_rejects_meat_and_fish() {
        assertFalse(DietFilter.matchesProductName("Говядина", "vegetarian"))
        assertFalse(DietFilter.matchesProductName("Лосось", "vegetarian"))
    }

    @Test
    fun no_pork_rejects_pork_only() {
        assertFalse(DietFilter.matchesProductName("Свинина", "no_pork"))
        assertTrue(DietFilter.matchesProductName("Куриная грудка", "no_pork"))
    }

    @Test
    fun none_allows_everything() {
        assertTrue(DietFilter.matchesProductName("Свинина", "none"))
        assertTrue(DietFilter.matchesProductName("Говядина", "none"))
    }

    @Test
    fun passesDiets_empty_allows_all() {
        assertTrue(DietFilter.productPassesDiets("Свинина", null))
        assertTrue(DietFilter.productPassesDiets("Свинина", ""))
    }

    @Test
    fun passesDiets_multiple_must_pass_all() {
        // vegan + no_pork: курица проходит no_pork, но не vegan -> должна отсеяться
        assertFalse(DietFilter.productPassesDiets("Куриная грудка", "vegan;no_pork"))
        // растительное проходит обе
        assertTrue(DietFilter.productPassesDiets("Гречка", "vegan;no_pork"))
    }
}
