package com.nutritrack.app.utils

import android.content.Context
import com.nutritrack.app.R

/** Перевод названий частей тела и групп. Логика/БД — по русскому ключу. */
object BodyPartLabel {
    private val map = mapOf(
        "neck" to R.string.bp_neck,
        "shoulders" to R.string.bp_shoulders,
        "chest" to R.string.bp_chest,
        "back" to R.string.bp_back,
        "waist" to R.string.bp_waist,
        "belly" to R.string.bp_belly,
        "hips" to R.string.bp_hips,
        "biceps_l" to R.string.bp_biceps_l,
        "biceps_r" to R.string.bp_biceps_r,
        "forearm_l" to R.string.bp_forearm_l,
        "forearm_r" to R.string.bp_forearm_r,
        "wrist_l" to R.string.bp_wrist_l,
        "wrist_r" to R.string.bp_wrist_r,
        "thigh_l" to R.string.bp_thigh_l,
        "thigh_r" to R.string.bp_thigh_r,
        "calf_l" to R.string.bp_calf_l,
        "calf_r" to R.string.bp_calf_r,    )
    private val groups = mapOf(
        "Корпус" to R.string.bpg_torso,
        "Руки" to R.string.bpg_arms,
        "Ноги" to R.string.bpg_legs,
    )
    fun of(context: Context, key: String): String =
        map[key]?.let { context.getString(it) } ?: key
    fun group(context: Context, ru: String): String =
        groups[ru]?.let { context.getString(it) } ?: ru
}
