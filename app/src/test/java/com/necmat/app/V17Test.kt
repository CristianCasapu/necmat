package com.necmat.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V17Test {

    private var idCounter = 17_000L
    private fun nextId(): Long = idCounter++

    @Test
    fun `idurile duplicate sunt reasignate pastrand datele`() {
        val cats = listOf(
            Category(1, "A", listOf(Material(5, "M1", 2), Material(5, "M2", 3))),
            Category(1, "B", listOf(Material(6, "M3", 4)))
        )
        val fixed = Repo.fixDuplicateIds(cats) { nextId() }
        val allIds = fixed.map { it.id } + fixed.flatMap { c -> c.materials.map { it.id } }
        assertEquals(allIds.size, allIds.toSet().size)
        // primele apariții își păstrează id-ul, datele rămân intacte
        assertEquals(1L, fixed[0].id)
        assertEquals(5L, fixed[0].materials[0].id)
        assertEquals(3, fixed[0].materials[1].qty)
        assertEquals("M2", fixed[0].materials[1].name)
        assertEquals(4, fixed[1].materials[0].qty)
    }

    @Test
    fun `fara duplicate lista ramane identica`() {
        val cats = Repo.defaultCatalog()
        assertEquals(cats, Repo.fixDuplicateIds(cats) { nextId() })
    }

    @Test
    fun `auto-repararea nu schimba ordinea aleasa de utilizator`() {
        // utilizatorul a mutat Cablurile pe primul loc
        val custom = listOf(
            Repo.defaultCatalog().first { it.name == "Cabluri și tuburi (m)" },
            Repo.defaultCatalog().first { it.name == "Module" },
            Repo.defaultCatalog().first { it.name == "Tablou electric" }
        )
        val repaired = Repo.applyAllMigrations(custom) { nextId() }
        assertEquals(
            listOf("Cabluri și tuburi (m)", "Module", "Tablou electric"),
            repaired.map { it.name }.take(3)
        )
        // materialele lipsă tot se adaugă (categoriile absente apar la coadă)
        assertTrue(repaired.any { it.name == "Corpuri de iluminat (montaj)" })
    }

    @Test
    fun `suprascrierea unui sablon pastreaza statutul de sablon`() {
        val template = Work(1, "Apartament 2 camere", 100L, emptyList(), isTemplate = true)
        val update = Work(9, "Apartament 2 camere", 200L, emptyList())

        // după id
        val byId = replaceWork(listOf(template), update, overwriteId = 1L)
        assertTrue(byId[0].isTemplate)
        assertEquals(1L, byId[0].id)

        // după nume
        val byName = upsertWork(listOf(template), update)
        assertTrue(byName[0].isTemplate)
    }

    @Test
    fun `backupul include manopera si datele instalatorului`() {
        val labor = LaborConfig(
            dozaPrices = mapOf("doză 3 module" to 42.0),
            rowPrices = mapOf(13 to 333.0),
            travel = 60.0, helperPerDay = 275.0
        )
        val settings = org.json.JSONObject()
            .put("installerName", "Cristian").put("installerPhone", "0722")
        val json = Repo.backupJson(emptyList(), emptyList(), emptyList(), labor, settings)
        val parsed = Repo.parseBackup(json)!!

        assertEquals(42.0, parsed.labor!!.dozaPrices["doză 3 module"]!!, 0.001)
        assertEquals(333.0, parsed.labor!!.rowPrices[13]!!, 0.001)
        assertEquals(60.0, parsed.labor!!.travel, 0.001)
        assertEquals(275.0, parsed.labor!!.helperPerDay, 0.001)
        assertEquals("Cristian", parsed.settings!!.optString("installerName"))
    }

    @Test
    fun `backupurile vechi fara manopera raman valide`() {
        val json = Repo.backupJson(emptyList(), emptyList())
        val parsed = Repo.parseBackup(json)!!
        assertEquals(null, parsed.labor)
        assertEquals(null, parsed.settings)
    }
}
