package com.nutritrack.app.utils

import android.content.Context
import com.nutritrack.app.NutriApp
import com.nutritrack.app.data.db.AppDatabase

/**
 * Единственная точка доступа к активному профилю.
 *
 * Источник правды — флаг `isActive` в таблице `profiles`: именно его меняет
 * переключение профиля, и именно его читают список дневника, добавление еды и
 * виджеты.
 *
 * В prefs лежит только КЭШ последнего известного id — для мест, где нельзя
 * выполнить suspend-запрос (синхронная отрисовка UI). Кэш пишется исключительно
 * отсюда и освежается при старте приложения и после каждого переключения, так
 * что расхождение самоустраняется на следующем запуске.
 *
 * Раньше кэш (`prefs.activeProfileId`) не записывался вообще и навсегда
 * оставался равным 1: у пользователя со вторым профилем дневник работал с
 * одним профилем, а аналитика, достижения и экспорт — с другим.
 */
object ActiveProfile {

    private fun db(context: Context): AppDatabase =
        (context.applicationContext as NutriApp).database

    /** Активный профиль из БД. Попутно освежает кэш. Предпочтительный способ. */
    suspend fun id(context: Context, database: AppDatabase? = null): Long {
        val d = database ?: db(context)
        val fromDb = d.profileDao().getActiveProfileSync()?.id
        val prefs = PrefsManager(context)
        return if (fromDb != null) {
            if (prefs.activeProfileIdCache != fromDb) prefs.activeProfileIdCache = fromDb
            fromDb
        } else {
            // Профилей ещё нет (онбординг не пройден) — отдаём кэш
            prefs.activeProfileIdCache
        }
    }

    /**
     * Кэш активного профиля без обращения к БД.
     * Может отставать, если профиль переключили в этом же запуске до синхронизации,
     * поэтому использовать только там, где suspend невозможен.
     */
    fun cachedId(context: Context): Long = PrefsManager(context).activeProfileIdCache

    /** Привести кэш в соответствие с БД: старт приложения и после переключения профиля. */
    suspend fun sync(context: Context, database: AppDatabase? = null): Long =
        id(context, database)
}
