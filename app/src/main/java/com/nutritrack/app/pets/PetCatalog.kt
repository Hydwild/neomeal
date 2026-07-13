package com.nutritrack.app.pets

/**
 * Единый каталог питомцев и ссылок на их паки.
 * Чтобы поменять источник загрузки — правится только BASE_URL (и при желании имена файлов).
 */
object PetCatalog {

    /** Базовый адрес релиза на GitHub. Имя файла пака = "<id>.zip". */
    const val BASE_URL = "https://github.com/Hydwild/reanki-pets/releases/download/v1/"

    /** Действия, общие для всех питомцев (по 5 кадров каждое). */
    val ACTIONS = listOf("idle", "blink", "drink", "happy", "jump")

    const val FRAMES_PER_ACTION = 5

    /**
     * Питомец каталога.
     * @param id      технический идентификатор (= имя папки и zip-файла)
     * @param nameRes ключ строки с отображаемым именем (RU/EN/KO)
     * @param emoji   эмодзи для списка выбора
     */
    data class Pet(
        val id: String,
        val nameResName: String,
        val emoji: String
    ) {
        val zipUrl: String get() = BASE_URL + id + ".zip"
    }

    /** Все доступные питомцы. */
    val ALL = listOf(
        Pet("panda",   "pet_name_panda",   "🐼"),
        Pet("fox",     "pet_name_fox",     "🦊"),
        Pet("cat",     "pet_name_cat",     "🐱"),
        Pet("rabbit",  "pet_name_rabbit",  "🐰"),
        Pet("penguin", "pet_name_penguin", "🐧"),
        Pet("otter",   "pet_name_otter",   "🦦")
    )

    fun byId(id: String): Pet? = ALL.firstOrNull { it.id == id }

    /** Питомец по умолчанию. */
    const val DEFAULT_ID = "panda"
}
