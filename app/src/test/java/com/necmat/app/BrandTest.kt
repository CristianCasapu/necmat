package com.necmat.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandTest {

    @Test
    fun `grupul categoriei este dedus corect din nume`() {
        assertEquals(BrandGroups.TABLOU, BrandGroups.infer("Tablou electric"))
        assertEquals(BrandGroups.MODULAR, BrandGroups.infer("Doze modulare"))
        assertEquals(BrandGroups.MODULAR, BrandGroups.infer("Module"))
        assertEquals(BrandGroups.APARATAJ, BrandGroups.infer("Doze aparat încastrate"))
        assertEquals(BrandGroups.APARATAJ, BrandGroups.infer("Aparataj încastrat"))
        assertEquals(BrandGroups.APARATAJ, BrandGroups.infer("Aparataj aplicat"))
        assertEquals(BrandGroups.INSTALATIE, BrandGroups.infer("Doze legături"))
        assertEquals(BrandGroups.INSTALATIE, BrandGroups.infer("Cabluri și tuburi (m)"))
    }

    @Test
    fun `accesoriile mostenesc marca dozelor modulare`() {
        val w = Work(
            1, "Test", 0L,
            listOf(
                Category(
                    1, "Doze modulare",
                    listOf(Material(10, "Doză 3 module", 2)),
                    brand = "Gewiss", model = "Chorus"
                )
            )
        ).withAutoAccessories()
        val acc = w.categories.first { it.name.contains("calcul automat") }
        assertEquals("Gewiss", acc.brand)
        assertEquals("Chorus", acc.model)
        assertEquals("Gewiss Chorus", acc.brandLabel)
    }

    @Test
    fun `seedul are iduri unice si grupuri valide`() {
        val seed = Repo.seedBrands()
        assertEquals(seed.size, seed.map { it.id }.distinct().size)
        assertTrue(seed.all { it.groups.isNotEmpty() })
        assertTrue(seed.all { e -> e.groups.all { it in BrandGroups.all } })
        // mărcile cerute explicit de utilizator există
        listOf("Noark", "ETI", "Schneider Electric", "Hager").forEach { b ->
            assertTrue("lipsește $b", seed.any { it.brand == b })
        }
    }

    @Test
    fun `backupul pastreaza marcile si marca pe categorie`() {
        val cats = listOf(
            Category(1, "Module", listOf(Material(10, "Priză simplă", 1)), "bTicino", "Matix")
        )
        val brands = listOf(BrandEntry(99, "Noark", "Ex9BN", listOf(BrandGroups.TABLOU)))
        val json = Repo.backupJson(cats, emptyList(), brands)
        val parsed = Repo.parseBackup(json)!!
        assertEquals(cats, parsed.categories)
        assertEquals(brands, parsed.brands)
    }

    @Test
    fun `backup vechi fara marci este acceptat`() {
        val json = Repo.backupJson(
            listOf(Category(1, "Module", emptyList())),
            emptyList()
        )
        val parsed = Repo.parseBackup(json)
        assertTrue(parsed != null && parsed.brands.isEmpty())
    }
}
