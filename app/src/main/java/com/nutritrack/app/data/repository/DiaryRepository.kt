package com.nutritrack.app.data.repository

import com.nutritrack.app.data.db.dao.DiaryDao
import com.nutritrack.app.data.db.entity.DiaryEntryEntity

class DiaryRepository(private val dao: DiaryDao) {
    fun getEntriesForDate(date: String, profileId: Long) =
        dao.getEntriesForDate(date, profileId)

    suspend fun getEntriesForDateSync(date: String, profileId: Long) =
        dao.getEntriesForDateSync(date, profileId)

    suspend fun getEntriesForRange(start: String, end: String, profileId: Long) =
        dao.getEntriesForRange(start, end, profileId)

    suspend fun insert(entry: DiaryEntryEntity) = dao.insertEntry(entry)
    suspend fun update(entry: DiaryEntryEntity) = dao.updateEntry(entry)
    suspend fun delete(entry: DiaryEntryEntity) = dao.deleteEntry(entry)
    suspend fun deleteAll() = dao.deleteAll()
}