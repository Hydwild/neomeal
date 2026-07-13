package com.nutritrack.app.utils

import android.content.Context
import com.nutritrack.app.R

/**
 * Вопросы-ответы для экрана FAQ. Тексты — строковые ресурсы (RU/EN).
 * Чтобы добавить вопрос: заведи строки faq_q_N / faq_a_N в обоих strings.xml
 * и добавь пару сюда.
 */
object FaqData {

    data class Item(val questionRes: Int, val answerRes: Int)

    val items: List<Item> = listOf(
        Item(R.string.faq_q_1, R.string.faq_a_1),
        Item(R.string.faq_q_2, R.string.faq_a_2),
        Item(R.string.faq_q_3, R.string.faq_a_3),
        Item(R.string.faq_q_4, R.string.faq_a_4),
        Item(R.string.faq_q_5, R.string.faq_a_5),
        Item(R.string.faq_q_6, R.string.faq_a_6),
        Item(R.string.faq_q_7, R.string.faq_a_7),
        Item(R.string.faq_q_8, R.string.faq_a_8),
        Item(R.string.faq_q_9, R.string.faq_a_9),
        Item(R.string.faq_q_10, R.string.faq_a_10)
    )

    fun question(ctx: Context, item: Item): String = ctx.getString(item.questionRes)
    fun answer(ctx: Context, item: Item): String = ctx.getString(item.answerRes)
}
