package com.necmat.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {

    @Test
    fun `versiune mai noua este detectata`() {
        assertTrue(Updater.compareVersions("1.2", "1.1") > 0)
        assertTrue(Updater.compareVersions("v1.2", "1.1") > 0)
        assertTrue(Updater.compareVersions("2.0", "1.9") > 0)
        assertTrue(Updater.compareVersions("1.10", "1.9") > 0)
        assertTrue(Updater.compareVersions("1.2.1", "1.2") > 0)
    }

    @Test
    fun `versiuni egale sau mai vechi nu declanseaza update`() {
        assertEquals(0, Updater.compareVersions("1.2", "v1.2"))
        assertEquals(0, Updater.compareVersions("1.2.0", "1.2"))
        assertTrue(Updater.compareVersions("1.1", "1.2") < 0)
        assertTrue(Updater.compareVersions("0.9", "1.0") < 0)
    }

    @Test
    fun `valoarea totala si preturile lucrarii`() {
        val w = Work(
            1, "Test", 0L,
            listOf(
                Category(1, "Module", listOf(
                    Material(10, "Priză simplă", qty = 4, price = 12.5),
                    Material(11, "Cap scară", qty = 2, price = 0.0)
                ))
            )
        )
        assertTrue(w.hasPrices)
        assertEquals(50.0, w.totalValue, 0.001)
    }

    @Test
    fun `backup se serializeaza si se citeste inapoi complet`() {
        val cats = listOf(
            Category(1, "Module", listOf(Material(10, "Priză dublă", 3, 24.99)))
        )
        val works = listOf(
            Work(
                5, "Casa Ciuvică", 123456789L,
                listOf(Category(2, "Doze modulare", listOf(Material(20, "Doză 3 module", 2)))),
                client = "Familia Ciuvică", address = "Str. Exemplu 5", phone = "0722000000"
            )
        )
        val json = Repo.backupJson(cats, works)
        val parsed = Repo.parseBackup(json)!!

        assertEquals(cats, parsed.categories)
        assertEquals(works, parsed.works)
    }

    @Test
    fun `backup invalid este respins`() {
        assertEquals(null, Repo.parseBackup("nu e json"))
        assertEquals(null, Repo.parseBackup("""{"alt":"format"}"""))
    }
}
