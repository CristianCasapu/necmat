package com.necmat.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V5Test {

    private var idCounter = 5000L
    private fun nextId(): Long = idCounter++

    @Test
    fun `catalogul implicit respecta ordinea canonica`() {
        val names = Repo.defaultCatalog().map { it.name }
        assertEquals(Repo.canonicalOrder, names)
    }

    @Test
    fun `migrarea v4 reordoneaza si adauga materialele noi`() {
        val cats = listOf(
            Category(1, "Cabluri și tuburi (m)"),
            Category(2, "Tablou electric", listOf(Material(10, "Diferențial general", 1))),
            Category(3, "Doze aparat îngropate"),
            Category(4, "Categorie proprie"),
            Category(5, "Doze legături")
        )
        val result = Repo.migrateV4(cats) { nextId() }

        // ordine canonică; categoria necunoscută la coadă
        assertEquals(
            listOf(
                "Doze aparat îngropate", "Tablou electric", "Doze legături",
                "Cabluri și tuburi (m)", "Categorie proprie"
            ),
            result.map { it.name }
        )
        val tablou = result.first { it.name == "Tablou electric" }
        assertTrue(tablou.materials.any { it.name == "Releu monitorizare fază" })
        assertTrue(tablou.materials.any { it.name == "Busbar 13 module (pieptene 1P+N)" })
        assertTrue(tablou.materials.any { it.name == "ATS (comutare automată rețea-generator)" })
        // ce exista rămâne neatins
        assertEquals(1, tablou.materials.first { it.name == "Diferențial general" }.qty)
        val doze = result.first { it.name == "Doze legături" }
        assertTrue(doze.materials.any { it.name == "Clemă distribuție simplă (4 intrări)" })
        assertTrue(doze.materials.any { it.name == "Clemă distribuție dublă (8 intrări)" })
    }

    @Test
    fun `migrarea v4 nu dubleaza la rulari repetate`() {
        val once = Repo.migrateV4(Repo.defaultCatalog()) { nextId() }
        val twice = Repo.migrateV4(once) { nextId() }
        assertEquals(
            once.map { it.name to it.materials.size },
            twice.map { it.name to it.materials.size }
        )
    }

    @Test
    fun `replaceWork inlocuieste dupa id pastrand idul`() {
        val a = Work(1, "Casa A", 100L, emptyList())
        val b = Work(2, "Casa B", 200L, emptyList())
        val updated = Work(99, "Casa A renovată", 300L, emptyList())

        val result = replaceWork(listOf(a, b), updated, overwriteId = 1L)
        assertEquals(2, result.size)
        assertEquals(1L, result[0].id)
        assertEquals("Casa A renovată", result[0].name)
        assertTrue(result.contains(b))
    }

    @Test
    fun `replaceWork fara id se comporta ca upsert`() {
        val a = Work(1, "Casa A", 100L, emptyList())
        val result = replaceWork(listOf(a), Work(2, "casa a", 200L, emptyList()), null)
        assertEquals(1, result.size)
        assertEquals(2L, result[0].id)
    }

    @Test
    fun `mergeBrands adauga doar ce lipseste`() {
        val mine = listOf(
            BrandEntry(1, "Noark", "Ex9BN", listOf(BrandGroups.TABLOU)),
            BrandEntry(2, "Marca Mea", "", listOf(BrandGroups.APARATAJ))
        )
        val merged = Repo.mergeBrands(mine, Repo.seedBrands())
        // Noark Ex9BN nu se dublează
        assertEquals(1, merged.count { it.brand == "Noark" && it.series == "Ex9BN" })
        // marca proprie rămâne
        assertTrue(merged.any { it.brand == "Marca Mea" })
        // mărcile smart noi apar
        assertTrue(merged.any { it.brand == "Shelly" })
        assertTrue(merged.any { it.brand == "F&F" })
        // id-uri unice
        assertEquals(merged.size, merged.map { it.id }.distinct().size)
    }

    @Test
    fun `faza categoriei apare in eticheta si in backup`() {
        val c = Category(1, "Tablou electric", emptyList(), "Schneider Electric", "Acti9", "tri")
        assertEquals("Schneider Electric Acti9 · Trifazic", c.brandLabel)
        val parsed = Repo.parseBackup(Repo.backupJson(listOf(c), emptyList()))!!
        assertEquals("tri", parsed.categories[0].phase)
    }
}
