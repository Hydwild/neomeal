package com.nutritrack.app.data.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nutritrack.app.data.ProductDatabase
import com.nutritrack.app.data.db.dao.*
import com.nutritrack.app.data.db.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ProductEntity::class,
        DiaryEntryEntity::class,
        MealPlanEntity::class,
        MealItemEntity::class,
        WaterEntryEntity::class,
        WeightEntryEntity::class,
        ProfileEntity::class,
        AchievementEntity::class,
        FitnessDataEntity::class,
        FastingSessionEntity::class,
        DailyGoalEntity::class,
        MeasurementEntity::class,
        ProgressPhotoEntity::class,
        com.nutritrack.app.data.db.entity.MealFeedbackEntity::class
    ],
    version = 19,
    // Схемы пишутся в app/schemas (см. room.schemaLocation в build.gradle) —
    // без них невозможно тестировать миграции. Коммитить вместе с кодом.
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun diaryDao(): DiaryDao
    abstract fun mealPlanDao(): MealPlanDao
    abstract fun waterDao(): WaterDao
    abstract fun weightDao(): WeightDao
    abstract fun profileDao(): ProfileDao
    abstract fun achievementDao(): AchievementDao
    abstract fun fitnessDao(): FitnessDao
    abstract fun fastingDao(): FastingDao
    abstract fun dailyGoalDao(): DailyGoalDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun progressPhotoDao(): ProgressPhotoDao
    abstract fun mealFeedbackDao(): com.nutritrack.app.data.db.dao.MealFeedbackDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDb(context).also { INSTANCE = it }
            }

        private fun buildDb(context: Context): AppDatabase {
            // Ключ шифрования из защищённого хранилища
            val passphrase = DatabaseKey.getOrCreate(context.applicationContext)
            val factory = net.sqlcipher.database.SupportFactory(passphrase)

            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "nutritrack_db"
            )
                .openHelperFactory(factory)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        INSTANCE?.let { database ->
                            CoroutineScope(Dispatchers.IO).launch {
                                prepopulate(database)
                            }
                        }
                    }
                })
                .addMigrations(*ALL_MIGRATIONS)
                .build()
        }

        // Миграции

        private val M_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN price REAL")
                db.execSQL("ALTER TABLE products ADD COLUMN autoTip TEXT")
                db.execSQL("ALTER TABLE products ADD COLUMN manualNote TEXT")
            }
        }

        private val M_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS achievements (
                        id TEXT NOT NULL,
                        profileId INTEGER NOT NULL DEFAULT 1,
                        unlockedAt TEXT,
                        isUnlocked INTEGER NOT NULL DEFAULT 0,
                        progress INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(id)
                    )
                """.trimIndent())
            }
        }

        private val M_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS fitness_data (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        profileId INTEGER NOT NULL DEFAULT 1,
                        steps INTEGER NOT NULL DEFAULT 0,
                        caloriesBurned REAL NOT NULL DEFAULT 0,
                        heartRateAvg INTEGER NOT NULL DEFAULT 0,
                        heartRateMax INTEGER NOT NULL DEFAULT 0,
                        heartRateMin INTEGER NOT NULL DEFAULT 0,
                        activeMinutes INTEGER NOT NULL DEFAULT 0,
                        distanceMeters REAL NOT NULL DEFAULT 0,
                        source TEXT NOT NULL DEFAULT 'manual'
                    )
                """.trimIndent())
            }
        }

        private val M_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS fasting_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        profileId INTEGER NOT NULL DEFAULT 1,
                        startTime INTEGER NOT NULL,
                        endTime INTEGER,
                        targetHours INTEGER NOT NULL,
                        protocolName TEXT NOT NULL,
                        isCompleted INTEGER NOT NULL DEFAULT 0,
                        durationMinutes INTEGER NOT NULL DEFAULT 0,
                        notes TEXT
                    )
                """.trimIndent())
            }
        }

        private val M_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE meal_plans ADD COLUMN mealKey TEXT")
            }
        }

        private val M_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cursor = db.query("PRAGMA table_info(diary_entries)")
                var hasColumn = false
                cursor.use {
                    val nameIndex = it.getColumnIndex("name")
                    while (it.moveToNext()) {
                        if (it.getString(nameIndex) == "mealType") { hasColumn = true; break }
                    }
                }
                if (!hasColumn) {
                    db.execSQL("ALTER TABLE diary_entries ADD COLUMN mealType TEXT NOT NULL DEFAULT 'other'")
                }
            }
        }

        // Серии достижений — таблица daily_goals
        private val M_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS daily_goals (
                        profileId INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        calorieGoalMet INTEGER NOT NULL,
                        waterGoalMet INTEGER NOT NULL,
                        PRIMARY KEY(profileId, date)
                    )
                """.trimIndent())
            }
        }

        // Время записи — колонка timestamp в diary_entries (для night_owl / early_bird)
        private val M_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cursor = db.query("PRAGMA table_info(diary_entries)")
                var has = false
                cursor.use {
                    val nameIdx = it.getColumnIndex("name")
                    while (it.moveToNext()) {
                        if (it.getString(nameIdx) == "timestamp") { has = true; break }
                    }
                }
                if (!has) {
                    db.execSQL("ALTER TABLE diary_entries ADD COLUMN timestamp INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        // Избранные продукты — колонка isFavorite в products
        private val M_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cursor = db.query("PRAGMA table_info(products)")
                var has = false
                cursor.use {
                    val nameIdx = it.getColumnIndex("name")
                    while (it.moveToNext()) {
                        if (it.getString(nameIdx) == "isFavorite") { has = true; break }
                    }
                }
                if (!has) {
                    db.execSQL("ALTER TABLE products ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        private val M_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS measurements (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        bodyPart TEXT NOT NULL,
                        valueCm REAL NOT NULL,
                        date TEXT NOT NULL,
                        profileId INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        private val M_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS progress_photos (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        photoUri TEXT NOT NULL,
                        date TEXT NOT NULL,
                        note TEXT NOT NULL,
                        profileId INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        // Заполнение базы при первом запуске

        private suspend fun prepopulate(db: AppDatabase) {
            ProductDatabase.all.forEach { product ->
                db.productDao().insertProduct(
                    com.nutritrack.app.data.MicronutrientData.enrich(product)
                )
            }
        }

        private val M_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Фото блюда у записи дневника
                db.execSQL("ALTER TABLE diary_entries ADD COLUMN photoUri TEXT")
            }
        }

        private val M_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Целевой вес в профиле (0 = не задан)
                db.execSQL("ALTER TABLE profiles ADD COLUMN targetWeight REAL NOT NULL DEFAULT 0")
            }
        }

        private val M_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Оценки самочувствия после приёма пищи
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS meal_feedback (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "productId INTEGER NOT NULL, " +
                    "productName TEXT NOT NULL, " +
                    "rating TEXT NOT NULL, " +
                    "date TEXT NOT NULL, " +
                    "profileId INTEGER NOT NULL, " +
                    "timestamp INTEGER NOT NULL)"
                )
            }
        }

        private val M_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Аллергены и диеты в профиле, аллергены в продукте
                db.execSQL("ALTER TABLE profiles ADD COLUMN allergens TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE profiles ADD COLUMN diets TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products ADD COLUMN allergens TEXT NOT NULL DEFAULT ''")
            }
        }

        private val M_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Микронутриенты на 100 г (USDA FoodData Central). nullable.
                for (col in listOf(
                    "vitA", "vitC", "vitD", "vitE", "vitK", "vitB6", "vitB12",
                    "folate", "calcium", "iron", "magnesium", "zinc",
                    "potassium", "sodium", "selenium"
                )) {
                    db.execSQL("ALTER TABLE products ADD COLUMN $col REAL")
                }
            }
        }

        private val M_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Состав комбо (имя:граммы;...). nullable, только добавление колонки.
                db.execSQL("ALTER TABLE products ADD COLUMN comboItems TEXT")
            }
        }

        private val M_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Индексы (profileId, date) для ускорения выборок дневника/воды/веса/замеров/фитнеса.
                // Имена — строго Room-дефолтные: index_<table>_profileId_date.
                for (t in listOf("diary_entries", "water_entries", "weight_entries", "measurements", "fitness_data")) {
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_${t}_profileId_date` ON `$t` (`profileId`, `date`)")
                }
            }
        }

        // Полная цепочка. Общая для приложения и тестов миграций (MigrationTest).
        val ALL_MIGRATIONS = arrayOf(
            M_1_2, M_2_3, M_3_4, M_4_5, M_5_6, M_6_7, M_7_8, M_8_9, M_9_10,
            M_10_11, M_11_12, M_12_13, M_13_14, M_14_15, M_15_16, M_16_17,
            M_17_18, M_18_19
        )
    }
}
