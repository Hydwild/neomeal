package com.nutritrack.app.data.repository

import com.nutritrack.app.data.db.dao.ProfileDao
import com.nutritrack.app.data.db.dao.WeightDao
import com.nutritrack.app.data.db.entity.ProfileEntity
import com.nutritrack.app.data.db.entity.WeightEntryEntity

class ProfileRepository(
    private val profileDao: ProfileDao,
    private val weightDao: WeightDao
) {
    val allProfiles = profileDao.getAllProfiles()
    val activeProfile = profileDao.getActiveProfile()

    suspend fun getActiveSync() = profileDao.getActiveProfileSync()

    suspend fun switchProfile(id: Long) {
        profileDao.deactivateAll()
        profileDao.activateProfile(id)
    }

    suspend fun insert(profile: ProfileEntity) = profileDao.insertProfile(profile)
    suspend fun update(profile: ProfileEntity) = profileDao.updateProfile(profile)
    suspend fun delete(profile: ProfileEntity) = profileDao.deleteProfile(profile)

    fun getWeightHistory(profileId: Long) = weightDao.getAllWeightEntries(profileId)
    suspend fun getWeightHistorySync(profileId: Long) = weightDao.getAllWeightSync(profileId)
    suspend fun getLatestWeight(profileId: Long) = weightDao.getLatestWeight(profileId)
    suspend fun getWeightForDate(date: String, profileId: Long) = weightDao.getWeightForDate(date, profileId)
    suspend fun insertWeight(entry: WeightEntryEntity) = weightDao.insertWeight(entry)
    suspend fun deleteWeight(entry: WeightEntryEntity) = weightDao.deleteWeight(entry)
}