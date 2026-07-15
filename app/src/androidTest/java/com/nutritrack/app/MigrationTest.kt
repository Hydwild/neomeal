package com.nutritrack.app

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nutritrack.app.data.db.AppDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Тесты миграций Room.
 *
 * Схемы экспортируются в app/schemas начиная с версии 19 —
 * более ранние версии выпускались без exportSchema, поэтому
 * проверяемая цепочка начинается с 19.
 *
 * При каждом повышении версии БД:
 *  1. Собрать проект — в app/schemas появится новый JSON, закоммитить его.
 *  2. Добавить сюда тест migrate_19_20 (по образцу ниже), который
 *     создаёт БД старой версии, наполняет её данными через SQL
 *     и прогоняет новую миграцию с валидацией схемы.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    /** Старая схема v19 создаётся из JSON (санити-чек тестовой инфраструктуры). */
    @Test
    fun schema19_creates() {
        helper.createDatabase(dbName, 19).close()
    }

    /** 19→20: колонки fiber/sugar добавляются, старые данные не теряются. */
    @Test
    fun migrate_19_20() {
        helper.createDatabase(dbName, 19).use { db ->
            db.execSQL(
                "INSERT INTO diary_entries " +
                    "(productId, productName, grams, calories, protein, fat, carbs, " +
                    " date, mealType, profileId, timestamp) " +
                    "VALUES (1, 'Гречка', 150, 165, 6.2, 1.7, 32.1, " +
                    " '2026-07-15', 'lunch', 1, 1752571200000)"
            )
        }
        helper.runMigrationsAndValidate(dbName, 20, true, *AppDatabase.ALL_MIGRATIONS)
            .use { db ->
                db.query("SELECT productName, grams, fiber, sugar FROM diary_entries").use { c ->
                    org.junit.Assert.assertTrue(c.moveToFirst())
                    org.junit.Assert.assertEquals("Гречка", c.getString(0))
                    org.junit.Assert.assertEquals(150f, c.getFloat(1), 0.01f)
                    // новые колонки существуют и пусты
                    org.junit.Assert.assertTrue(c.isNull(2))
                    org.junit.Assert.assertTrue(c.isNull(3))
                }
            }
    }
}
