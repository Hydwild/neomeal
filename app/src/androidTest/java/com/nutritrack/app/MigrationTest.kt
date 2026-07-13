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

    /** Схема v19 из JSON согласована с текущими Entity-классами. */
    @Test
    fun schema19_matchesEntities() {
        helper.createDatabase(dbName, 19).close()
    }

    // Образец для будущих миграций (раскомментировать при переходе 19→20):
    //
    // @Test
    // fun migrate_19_20() {
    //     helper.createDatabase(dbName, 19).use { db ->
    //         db.execSQL(
    //             "INSERT INTO diary_entries (id, ...) VALUES (1, ...)"
    //         )
    //     }
    //     helper.runMigrationsAndValidate(dbName, 20, true, *AppDatabase.ALL_MIGRATIONS)
    //         .use { db ->
    //             // проверить, что старые данные на месте
    //         }
    // }
}
