package com.nutritrack.app

import com.google.gson.Gson
import com.nutritrack.app.utils.BackupManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Файл бэкапа не должен зависеть от обфускации.
 *
 * До 1.4.2 классы BackupData/Envelope не были под -keep, и R8 переименовывал их
 * поля (products → c, avatars → o, salt → c). Gson пишет JSON по именам полей,
 * поэтому в файл попадали буквы: формат оказался завязан на конкретную сборку.
 * Восстановление чужого файла давало сплошные null и падало.
 *
 * Здесь проверяется разбор обоих раскладок. Сохранность самих имён в релизе
 * обеспечивают keep-правила в proguard-rules.pro.
 */
class BackupLegacyFormatTest {

    private val gson = Gson()

    private val legacyJson = """
        {
          "a": 1,
          "b": "2026-08-11 09:30",
          "c": [],
          "d": [
            {
              "id": 1, "productId": 0, "productName": "Овсянка (сухая)",
              "grams": 60.0, "calories": 233.4, "protein": 7.8, "fat": 3.7,
              "carbs": 39.8, "date": "2026-08-11", "mealType": "breakfast",
              "profileId": 1, "timestamp": 1785300540000
            }
          ],
          "e": [], "f": [],
          "g": [
            {
              "id": 1, "name": "Алексей", "age": 30, "height": 180.0,
              "weight": 82.0, "gender": "male", "goal": "lose",
              "targetWeight": 75.0, "activityLevel": "moderate",
              "isActive": true, "allergens": "", "diets": ""
            }
          ],
          "h": [], "i": [], "j": [], "k": [], "l": [], "m": [],
          "n": {"language": "ru"},
          "o": {},
          "p": [], "q": []
        }
    """.trimIndent()

    private fun parse(json: String): BackupManager.BackupData {
        val normalized = BackupManager.normalizeKeys(
            json, BackupManager.legacyBackupKeysForTest(), "diary")
        return gson.fromJson(normalized, BackupManager.BackupData::class.java)
    }

    @Test fun `старый формат с обфусцированными ключами читается`() {
        val data = parse(legacyJson)
        assertEquals(1, data.diary.size)
        assertEquals("Овсянка (сухая)", data.diary[0].productName)
        assertEquals(1, data.profiles.size)
        assertEquals("Алексей", data.profiles[0].name)
        assertEquals("ru", data.settings["language"])
    }

    @Test fun `элементы списков — реальные сущности, а не сырые мапы`() {
        // Именно здесь ломался релиз: без generic-сигнатуры Gson клал в список
        // LinkedTreeMap, и падение случалось уже при вставке в БД.
        val data = parse(legacyJson)
        val entry: Any = data.diary[0]
        assertTrue(
            "В списке оказался ${entry.javaClass.name} вместо DiaryEntryEntity",
            entry is com.nutritrack.app.data.db.entity.DiaryEntryEntity
        )
    }

    @Test fun `новый формат не трогается нормализацией`() {
        val modern = """{"version":1,"diary":[],"profiles":[],"settings":{"language":"en"}}"""
        val out = BackupManager.normalizeKeys(
            modern, BackupManager.legacyBackupKeysForTest(), "diary")
        assertEquals("Файл нового формата не должен переписываться", modern, out)
    }

    @Test fun `конверт шифрования читается в обеих раскладках`() {
        val legacyEnv = """{"a":"neomeal-encrypted","b":1,"c":"c2FsdA==","d":"aXY=","e":"ZGF0YQ=="}"""
        val out = BackupManager.normalizeKeys(
            legacyEnv, BackupManager.legacyEnvelopeKeysForTest(), "salt")
        assertTrue("Ключ salt не восстановлен: $out", out.contains("\"salt\""))
        assertTrue("Ключ iv не восстановлен: $out", out.contains("\"iv\""))
    }

    @Test fun `мусорный файл даёт пустой разбор и импорт обязан отказаться`() {
        val junk = """{"hello":"world"}"""
        val data = parse(junk)
        // У BackupData все поля имеют дефолты, поэтому Kotlin генерирует no-arg
        // конструктор: здесь (без минификации) Gson применяет дефолты и списки
        // выходят ПУСТЫМИ. В release R8 этот конструктор выкидывал как
        // неиспользуемый, Gson переходил на Unsafe — и поля становились null;
        // отсюда и падение на avatars.isEmpty(). Keep-правило сохраняет
        // конструктор, а orEmptySafe в import страхует оба случая.
        assertTrue("Дневник должен быть пуст", data.diary.isEmpty())
        assertTrue("Профили должны быть пусты", data.profiles.isEmpty())
        // Именно это сочетание BackupManager.import считает нераспознанным
        // файлом и выходит ДО удаления данных пользователя.
    }
}
