package com.nutritrack.app

import com.google.gson.Gson
import com.nutritrack.app.utils.MealTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Шаблоны приёмов пищи хранятся JSON-ом в prefs (и в бэкапе).
 *
 * Релизные сборки до 1.4.4 писали этот JSON с буквенными ключами: классы
 * Template/Item не были под keep, и R8 переименовывал поля. Такие записи
 * должны читаться после фикса, иначе последний сохранённый пользователем
 * шаблон пропадёт.
 */
class MealTemplatesParseTest {

    // Ровно то, что писала релизная сборка: раскладка снята с mapping.txt
    private val legacyJson = """
        [{"a":"Мой завтрак","b":[
          {"a":0,"b":"Овсянка (сухая)","c":60.0,"d":233.4,"e":7.8,"f":3.7,"g":39.8,"h":5.5,"i":9.1},
          {"a":0,"b":"Творог 2%","c":150.0,"d":154.5,"e":27.0,"f":3.0,"g":2.7}
        ]}]
    """.trimIndent()

    @Test fun `старая буквенная раскладка читается`() {
        val list = MealTemplates.parse(legacyJson)
        assertEquals(1, list.size)
        assertEquals("Мой завтрак", list[0].name)
        assertEquals(2, list[0].items.size)
        assertEquals("Овсянка (сухая)", list[0].items[0].productName)
        assertEquals(233.4f, list[0].items[0].calories, 0.01f)
        assertEquals(9.1f, list[0].items[0].sugar!!, 0.01f)
    }

    @Test fun `элементы — настоящие Item, а не сырые мапы`() {
        // В релизе без generic-сигнатуры Gson клал в список LinkedTreeMap,
        // и падение случалось при обращении к полю.
        val item: Any = MealTemplates.parse(legacyJson)[0].items[0]
        assertTrue("В списке ${item.javaClass.name}", item is MealTemplates.Item)
        assertEquals(387, MealTemplates.parse(legacyJson)[0].totalCalories)
    }

    @Test fun `нормальная раскладка — круговой проход`() {
        val tpl = MealTemplates.Template(
            "Перекус",
            listOf(MealTemplates.Item(5L, "Яблоко", 150f, 78f, 0.6f, 0.6f, 15f, fiber = 3f))
        )
        val json = Gson().toJson(listOf(tpl))
        val back = MealTemplates.parse(json)
        assertEquals(listOf(tpl), back)
    }

    @Test fun `смешанный список — старый и новый шаблоны вместе`() {
        // После фикса первое сохранение допишет новый шаблон к старому
        val modern = Gson().toJson(MealTemplates.Template("Ужин", emptyList()))
        val mixed = legacyJson.trim().removeSuffix("]") + "," + modern + "]"
        val names = MealTemplates.parse(mixed).map { it.name }
        assertEquals(listOf("Мой завтрак", "Ужин"), names)
    }

    @Test fun `пустая строка — пустой список`() {
        assertTrue(MealTemplates.parse("").isEmpty())
    }
}
