package com.necmat.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V10Test {

    private var idCounter = 10_000L
    private fun nextId(): Long = idCounter++

    @Test
    fun `latimea in module se citeste din nume`() {
        assertEquals(2, moduleWidth("Priză dublă (2 module)"))
        assertEquals(2, moduleWidth("Priză dublă"))          // regula veche rămâne
        assertEquals(3, moduleWidth("Aparat special (3 module)"))
        assertEquals(1, moduleWidth("Priză simplă"))
        assertEquals(1, moduleWidth("Modul TV"))
        assertEquals(1, moduleWidth("Modul rețea CAT6"))
        assertEquals(1, moduleWidth("Cap scară"))
    }

    @Test
    fun `priza dubla 2 module ocupa 2 sloturi in calcul`() {
        val cats = listOf(
            Category(1, "Doze modulare", listOf(Material(10, "Doză 4 module", 1))),
            Category(2, "Module", listOf(
                Material(20, "Priză dublă (2 module)", 1),
                Material(21, "Modul rețea CAT6", 1)
            ))
        )
        val s = accessorySummary(cats)!!
        assertEquals(3, s.usedModules)
        assertEquals(1, s.obturatoare)
    }

    @Test
    fun `migrarea v7 redenumeste si desparte CAT5 de CAT6`() {
        val cats = listOf(
            Category(1, "Module", listOf(
                Material(10, "Priză dublă", 3),
                Material(11, "Modul rețea (CAT5/6)", 2)
            )),
            Category(2, "Aparataj încastrat", listOf(
                Material(20, "Priză rețea (CAT5/6) încastrată", 4)
            ))
        )
        val result = Repo.migrateV7(cats) { nextId() }

        val mod = result.first { it.name == "Module" }
        // priza dublă primește numele explicit, cantitatea rămâne
        assertEquals(3, mod.materials.first { it.name == "Priză dublă (2 module)" }.qty)
        assertFalse(mod.materials.any { it.name == "Priză dublă" })
        // cantitatea combinată rămâne pe CAT6; CAT5 apare cu 0
        assertEquals(2, mod.materials.first { it.name == "Modul rețea CAT6" }.qty)
        assertEquals(0, mod.materials.first { it.name == "Modul rețea CAT5" }.qty)

        val buried = result.first { it.name == "Aparataj încastrat" }
        assertEquals(4, buried.materials.first { it.name == "Priză rețea CAT6 încastrată" }.qty)
        assertEquals(0, buried.materials.first { it.name == "Priză rețea CAT5 încastrată" }.qty)
    }

    @Test
    fun `migrarea v7 este idempotenta`() {
        val once = Repo.migrateV7(Repo.defaultCatalog()) { nextId() }
        val twice = Repo.migrateV7(once) { nextId() }
        assertEquals(
            once.map { it.name to it.materials.map { m -> m.name } },
            twice.map { it.name to it.materials.map { m -> m.name } }
        )
    }

    @Test
    fun `catalogul implicit are CAT5 si CAT6 separate si priza dubla explicita`() {
        val mod = Repo.defaultCatalog().first { it.name == "Module" }
        assertTrue(mod.materials.any { it.name == "Modul rețea CAT5" })
        assertTrue(mod.materials.any { it.name == "Modul rețea CAT6" })
        assertTrue(mod.materials.any { it.name == "Priză dublă (2 module)" })
        assertFalse(mod.materials.any { it.name == "Modul rețea (CAT5/6)" })
        val buried = Repo.defaultCatalog().first { it.name == "Aparataj încastrat" }
        assertTrue(buried.materials.any { it.name == "Priză rețea CAT5 încastrată" })
        assertTrue(buried.materials.any { it.name == "Priză rețea CAT6 încastrată" })
    }
}
