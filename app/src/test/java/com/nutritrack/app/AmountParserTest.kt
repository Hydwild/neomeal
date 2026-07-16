package com.nutritrack.app

import com.nutritrack.app.utils.AmountParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Разбор количеств ингредиентов рецептов (список покупок). */
class AmountParserTest {

    @Test fun `граммы`() {
        assertEquals(200f to "г", AmountParser.parse("200 г"))
    }

    @Test fun `миллилитры`() {
        assertEquals(300f to "мл", AmountParser.parse("300 мл"))
    }

    @Test fun `штуки`() {
        assertEquals(2f to "шт", AmountParser.parse("2 шт"))
    }

    @Test fun `ложки`() {
        assertEquals(1f to "ст.л.", AmountParser.parse("1 ст.л."))
        assertEquals(1f to "ч.л.", AmountParser.parse("1 ч.л."))
    }

    @Test fun `число без единицы — штуки`() {
        assertEquals(3f to "шт", AmountParser.parse("3"))
    }

    @Test fun `дробное с запятой`() {
        assertEquals(1.5f to "шт", AmountParser.parse("1,5 шт"))
    }

    @Test fun `без числа — null`() {
        assertNull(AmountParser.parse("по вкусу"))
        assertNull(AmountParser.parse("щепотка"))
    }

    @Test fun `пробелы по краям`() {
        assertEquals(50f to "г", AmountParser.parse("  50 г  "))
    }
}
