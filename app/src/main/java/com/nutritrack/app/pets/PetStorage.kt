package com.nutritrack.app.pets

import android.content.Context
import java.io.File

/**
 * Управляет распакованными паками питомцев в filesDir/pets/<id>/.
 * Структура после распаковки zip:
 *   filesDir/pets/<id>/idle/frame_00.png ... frame_04.png
 *   filesDir/pets/<id>/blink/...
 *   filesDir/pets/<id>/manifest.json
 *   filesDir/pets/<id>/.ready   (маркер успешной установки)
 */
class PetStorage(context: Context) {

    private val root = File(context.filesDir, "pets")

    fun petDir(petId: String): File = File(root, petId)

    private fun readyMarker(petId: String): File = File(petDir(petId), ".ready")

    /** Питомец полностью скачан и распакован. */
    fun isInstalled(petId: String): Boolean = readyMarker(petId).exists()

    fun markInstalled(petId: String) {
        petDir(petId).mkdirs()
        readyMarker(petId).writeText("ok")
    }

    /** Папка одного действия питомца. */
    fun actionDir(petId: String, action: String): File = File(petDir(petId), action)

    /**
     * Упорядоченный список файлов кадров для действия.
     * Пустой список — действие отсутствует (анимацию для него показывать нельзя).
     */
    fun frames(petId: String, action: String): List<File> {
        val dir = actionDir(petId, action)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.name.startsWith("frame_") && f.name.endsWith(".png") }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    fun hasAction(petId: String, action: String): Boolean = frames(petId, action).isNotEmpty()

    /** Полностью удалить пак питомца (например, для повторной загрузки). */
    fun delete(petId: String) {
        petDir(petId).deleteRecursively()
    }

    /** Занимаемый размер пака в байтах (для экрана управления). */
    fun sizeBytes(petId: String): Long {
        val dir = petDir(petId)
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
