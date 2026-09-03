package com.necmat.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class V19Test {

    private lateinit var logFile: File

    @Before
    fun setup() {
        logFile = File.createTempFile("necmat_log_test", ".txt")
        AppLog.init(logFile)
        AppLog.enabled = true
        AppLog.minLevel = AppLog.Level.INFO
        AppLog.clear()
    }

    @After
    fun teardown() {
        logFile.delete()
        AppLog.enabled = true
        AppLog.minLevel = AppLog.Level.INFO
    }

    @Test
    fun `nivelurile sub minim sunt filtrate`() {
        AppLog.minLevel = AppLog.Level.WARN
        AppLog.d("T", "debug")
        AppLog.i("T", "info")
        AppLog.w("T", "warn")
        AppLog.e("T", "error")
        val content = logFile.readText()
        assertFalse(content.contains("debug"))
        assertFalse(content.contains("info"))
        assertTrue(content.contains("W/T: warn"))
        assertTrue(content.contains("E/T: error"))
    }

    @Test
    fun `jurnalul dezactivat nu scrie nimic`() {
        AppLog.enabled = false
        AppLog.e("T", "eroare gravă")
        assertEquals("", logFile.readText())
        assertFalse(AppLog.shouldLog(AppLog.Level.ERROR))
    }

    @Test
    fun `erorile includ stack trace`() {
        AppLog.e("Pdf", "a crăpat", RuntimeException("mesaj de test"))
        val content = logFile.readText()
        assertTrue(content.contains("E/Pdf: a crăpat"))
        assertTrue(content.contains("RuntimeException"))
        assertTrue(content.contains("mesaj de test"))
    }

    @Test
    fun `ordinea nivelurilor este debug info warn error`() {
        assertTrue(AppLog.Level.DEBUG.ordinal < AppLog.Level.INFO.ordinal)
        assertTrue(AppLog.Level.INFO.ordinal < AppLog.Level.WARN.ordinal)
        assertTrue(AppLog.Level.WARN.ordinal < AppLog.Level.ERROR.ordinal)
    }

    @Test
    fun `formatul liniei este stabil`() {
        assertEquals(
            "2026-09-02 10:00:00.000 I/Lucrari: Lucrare salvată",
            AppLog.formatLine("2026-09-02 10:00:00.000", AppLog.Level.INFO, "Lucrari", "Lucrare salvată")
        )
    }

    @Test
    fun `readTail si clear functioneaza`() {
        AppLog.i("T", "primul")
        AppLog.i("T", "al doilea")
        val tail = AppLog.readTail()
        assertTrue(tail.contains("primul"))
        assertTrue(tail.contains("al doilea"))
        AppLog.clear()
        assertEquals("", AppLog.readTail())
    }

    @Test
    fun `fisierul mare este rotit pastrand coada`() {
        val big = "x".repeat(200)
        repeat(4000) { i -> AppLog.i("T", "linia $i $big") }
        assertTrue(logFile.length() <= 512 * 1024)
        val content = logFile.readText()
        // coada (ultimele linii) există, începutul a fost tăiat
        assertTrue(content.contains("linia 3999"))
        assertFalse(content.contains("linia 0 $big"))
        // fișierul începe cu o linie completă (nu cu o linie ruptă)
        assertTrue(content.startsWith("20"))
    }
}
