package com.nutritrack.app

import com.nutritrack.app.utils.BackupManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Шифрование резервной копии: AES-GCM + PBKDF2, раунд-трип и неверный пароль. */
class BackupCryptoTest {

    private val sample = """{"version":1,"diary":[{"name":"Гречка","grams":150}]}"""

    @Test fun `encrypt → decrypt возвращает исходный JSON`() {
        val encrypted = BackupManager.encrypt(sample, "correct horse battery")
        val decrypted = BackupManager.decrypt(encrypted, "correct horse battery")
        assertEquals(sample, decrypted)
    }

    @Test fun `шифротекст не содержит исходных данных`() {
        val encrypted = BackupManager.encrypt(sample, "pass123")
        assertTrue(!encrypted.contains("Гречка"))
        assertTrue(encrypted.contains("neomeal-encrypted"))
    }

    @Test fun `одинаковый вход — разный шифротекст (случайные соль и IV)`() {
        val a = BackupManager.encrypt(sample, "pass123")
        val b = BackupManager.encrypt(sample, "pass123")
        assertTrue(a != b)
    }

    @Test(expected = BackupManager.WrongPasswordException::class)
    fun `неверный пароль — WrongPasswordException`() {
        val encrypted = BackupManager.encrypt(sample, "правильный")
        BackupManager.decrypt(encrypted, "неправильный")
    }
}
