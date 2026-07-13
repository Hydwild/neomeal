package com.nutritrack.app.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.nutritrack.app.data.db.entity.DiaryEntryEntity

@Dao
interface DiaryDao {

    // Используется DiaryRepository

    @Query("SELECT * FROM diary_entries WHERE date = :date AND profileId = :profileId ORDER BY id DESC")
    fun getEntriesForDate(date: String, profileId: Long): LiveData<List<DiaryEntryEntity>>


    // Кол-во записей, добавленных поздно (час >= 22) — для "Поздний ужин"
    @Query("""
    SELECT COUNT(*) FROM diary_entries
    WHERE profileId = :profileId
      AND timestamp > 0
      AND CAST(strftime('%H', datetime(timestamp/1000, 'unixepoch', 'localtime')) AS INTEGER) >= 22
""")
    suspend fun countNightEntries(profileId: Long): Int

    // Кол-во записей, добавленных рано (час < 7) — для "Ранняя птичка"
    @Query("""
    SELECT COUNT(*) FROM diary_entries
    WHERE profileId = :profileId
      AND timestamp > 0
      AND CAST(strftime('%H', datetime(timestamp/1000, 'unixepoch', 'localtime')) AS INTEGER) < 7
""")
    suspend fun countEarlyEntries(profileId: Long): Int

    @Query("SELECT * FROM diary_entries WHERE date = :date AND profileId = :profileId ORDER BY id DESC")
    suspend fun getEntriesForDateSync(date: String, profileId: Long): List<DiaryEntryEntity>

    // Суммарные калории за всё время
    @Query("SELECT COALESCE(SUM(calories), 0) FROM diary_entries WHERE profileId = :profileId")
    suspend fun getTotalCaloriesAllTime(profileId: Long): Float

    // Суммарный белок за всё время
    @Query("SELECT COALESCE(SUM(protein), 0) FROM diary_entries WHERE profileId = :profileId")
    suspend fun getTotalProteinAllTime(profileId: Long): Float

    // Суммарные углеводы за всё время
    @Query("SELECT COALESCE(SUM(carbs), 0) FROM diary_entries WHERE profileId = :profileId")
    suspend fun getTotalCarbsAllTime(profileId: Long): Float

    // Кол-во записей всего (есть ли вообще хоть одна)
    @Query("SELECT COUNT(*) FROM diary_entries WHERE profileId = :profileId")
    suspend fun getTotalEntriesCount(profileId: Long): Int


    @Query("SELECT * FROM diary_entries WHERE date BETWEEN :start AND :end AND profileId = :profileId ORDER BY date ASC")
    suspend fun getEntriesForRange(start: String, end: String, profileId: Long): List<DiaryEntryEntity>

    // insert(entry) insertEntry
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: DiaryEntryEntity)

    @androidx.room.Update
    suspend fun updateEntry(entry: DiaryEntryEntity)

    // delete(entry) deleteEntry
    @Delete
    suspend fun deleteEntry(entry: DiaryEntryEntity)

    @Query("DELETE FROM diary_entries")
    suspend fun deleteAll()

    // Используется AchievementsViewModel

    // Количество разных дней с записями — для достижений-серий
    @Query("SELECT COUNT(DISTINCT date) FROM diary_entries WHERE profileId = :profileId")
    suspend fun getDistinctDaysCount(profileId: Long): Int

    // Используется ReminderReceiver (waterDao-стиль insert)

    // Синоним insert для подтверждения приёма пищи из шторки
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DiaryEntryEntity)

    // Бюджет (если используется)

    @Query("""
        SELECT COALESCE(SUM(d.grams / 100.0 * p.price), 0)
        FROM diary_entries d
        JOIN products p ON d.productId = p.id
        WHERE d.date BETWEEN :start AND :end
          AND d.profileId = :profileId
          AND p.price IS NOT NULL
    """)
    suspend fun getTotalSpentInRange(start: String, end: String, profileId: Long): Float

 /** Топ продуктов по тратам за период. */
    @Query("""
        SELECT d.productName AS name,
               SUM(d.grams / 100.0 * p.price) AS spent,
               SUM(d.grams) AS totalGrams
        FROM diary_entries d
        JOIN products p ON d.productId = p.id
        WHERE d.date BETWEEN :start AND :end
          AND d.profileId = :profileId
          AND p.price IS NOT NULL
        GROUP BY d.productName
        ORDER BY spent DESC
        LIMIT 5
    """)
    suspend fun getTopSpendingInRange(start: String, end: String, profileId: Long): List<SpendingRow>

    @Query("SELECT * FROM diary_entries")
    suspend fun dumpAll(): List<DiaryEntryEntity>

    // Быстрый ввод

 /**
 * Недавние продукты: последняя запись по каждому productId, по убыванию времени.
 * Возвращает саму запись (с граммами прошлого раза) — для добавления в 1 тап.
 */
    @Query("""
        SELECT d.* FROM diary_entries d
        INNER JOIN (
            SELECT productId, MAX(id) AS maxId
            FROM diary_entries
            WHERE profileId = :profileId
            GROUP BY productId
        ) last ON d.id = last.maxId
        ORDER BY d.id DESC
        LIMIT :limit
    """)
    suspend fun getRecentEntries(profileId: Long, limit: Int = 8): List<DiaryEntryEntity>

 /**
 * Частые продукты: по количеству добавлений за всё время.
 * Возвращает последнюю запись каждого продукта (с типичными граммами).
 */
    @Query("""
        SELECT d.* FROM diary_entries d
        INNER JOIN (
            SELECT productId, COUNT(*) AS cnt, MAX(id) AS maxId
            FROM diary_entries
            WHERE profileId = :profileId
            GROUP BY productId
            HAVING cnt >= 2
        ) freq ON d.id = freq.maxId
        ORDER BY freq.cnt DESC
        LIMIT :limit
    """)
    suspend fun getFrequentEntries(profileId: Long, limit: Int = 8): List<DiaryEntryEntity>

 /** Записи конкретного приёма пищи за дату (для «повторить вчера»). */
    @Query("SELECT * FROM diary_entries WHERE date = :date AND profileId = :profileId")
    suspend fun getEntriesForDateAndProfile(date: String, profileId: Long): List<DiaryEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<DiaryEntryEntity>)
}

data class SpendingRow(
    val name: String,
    val spent: Float,
    val totalGrams: Float
)
