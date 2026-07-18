package com.nutritrack.app.utils

import android.content.Context
import com.nutritrack.app.R

/**
 * Журнал изменений для экрана «Что нового».
 * Единый источник: версия + список пунктов (строковые ресурсы для RU/EN).
 * Самая новая версия — первой в списке.
 *
 * Как добавлять новую версию: добавь Release сверху списка versions и
 * заведи строки whatsnew_<v>_title / whatsnew_<v>_item_N в обоих strings.xml.
 */
object WhatsNewData {

    data class Release(val version: String, val titleRes: Int, val itemRes: List<Int>)

    val versions: List<Release> = listOf(
        Release(
            version = "1.4.1",
            titleRes = R.string.whatsnew_141_title,
            itemRes = listOf(
                R.string.whatsnew_141_1,
                R.string.whatsnew_141_2,
                R.string.whatsnew_141_3
            )
        ),
        Release(
            version = "1.4.0",
            titleRes = R.string.whatsnew_140_title,
            itemRes = listOf(
                R.string.whatsnew_140_1,
                R.string.whatsnew_140_7,
                R.string.whatsnew_140_8,
                R.string.whatsnew_140_2,
                R.string.whatsnew_140_3,
                R.string.whatsnew_140_4,
                R.string.whatsnew_140_9,
                R.string.whatsnew_140_10,
                R.string.whatsnew_140_5,
                R.string.whatsnew_140_6
            )
        ),
        Release(
            version = "1.3.2",
            titleRes = R.string.whatsnew_132_title,
            itemRes = listOf(
                R.string.whatsnew_132_1,
                R.string.whatsnew_132_2
            )
        ),
        Release(
            version = "1.3.1",
            titleRes = R.string.whatsnew_131_title,
            itemRes = listOf(
                R.string.whatsnew_131_1,
                R.string.whatsnew_131_2,
                R.string.whatsnew_131_3,
                R.string.whatsnew_131_4
            )
        ),
        Release(
            version = "1.3.0",
            titleRes = R.string.whatsnew_130_title,
            itemRes = listOf(
                R.string.whatsnew_130_1,
                R.string.whatsnew_130_2,
                R.string.whatsnew_130_3,
                R.string.whatsnew_130_4,
                R.string.whatsnew_130_5
            )
        ),
        Release(
            version = "1.2.1",
            titleRes = R.string.whatsnew_121_title,
            itemRes = listOf(R.string.whatsnew_121_1)
        ),
        Release(
            version = "1.2.0",
            titleRes = R.string.whatsnew_120_title,
            itemRes = listOf(
                R.string.whatsnew_120_1,
                R.string.whatsnew_120_2,
                R.string.whatsnew_120_3
            )
        ),
        Release(
            version = "1.1.2",
            titleRes = R.string.whatsnew_112_title,
            itemRes = listOf(
                R.string.whatsnew_112_1,
                R.string.whatsnew_112_2,
                R.string.whatsnew_112_3
            )
        ),
        Release(
            version = "1.1.1",
            titleRes = R.string.whatsnew_111_title,
            itemRes = listOf(
                R.string.whatsnew_111_1,
                R.string.whatsnew_111_2
            )
        ),
        Release(
            version = "1.1.0",
            titleRes = R.string.whatsnew_110_title,
            itemRes = listOf(
                R.string.whatsnew_110_1,
                R.string.whatsnew_110_2,
                R.string.whatsnew_110_3,
                R.string.whatsnew_110_4,
                R.string.whatsnew_110_5,
                R.string.whatsnew_110_6,
                R.string.whatsnew_110_7,
                R.string.whatsnew_110_8
            )
        ),
        Release(
            version = "1.0.0",
            titleRes = R.string.whatsnew_100_title,
            itemRes = listOf(R.string.whatsnew_100_1)
        )
    )

 /** Последняя версия журнала — для пометки «просмотрено». */
    fun latestVersion(): String = versions.firstOrNull()?.version ?: "1.0.0"

    fun lines(ctx: Context, release: Release): List<String> = release.itemRes.map { ctx.getString(it) }
}
