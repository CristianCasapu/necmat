package com.necmat.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelV3Test {

    private var idCounter = 1000L
    private fun nextId(): Long = idCounter++

    // ---- filterForPdf ----

    @Test
    fun `dozele modulare sunt scoase din pdf dar accesoriile raman`() {
        val w = Work(
            1, "Test", 0L,
            listOf(
                Category(1, "Doze modulare", listOf(Material(10, "Doză 3 module", 2))),
                Category(2, "Module", listOf(Material(20, "Priză simplă", 1)))
            )
        ).withAutoAccessories().filterForPdf(includeBoxes = false)

        // categoria de doze dispare (rămâne goală după filtrare)
        assertTrue(w.categories.none { it.name == "Doze modulare" })
        // modulele și accesoriile rămân
        assertTrue(w.categories.any { it.name == "Module" })
        val acc = w.categories.first { it.name.contains("calcul automat") }
        assertEquals(2, acc.materials.first { it.name == "Ramă suport 3 module" }.qty)
        assertEquals(5, acc.materials.first { it.name == "Obturator (modul fals)" }.qty)
    }

    @Test
    fun `cu setarea activata dozele raman in pdf`() {
        val w = Work(
            1, "Test", 0L,
            listOf(Category(1, "Doze modulare", listOf(Material(10, "Doză 3 module", 2))))
        ).filterForPdf(includeBoxes = true)
        assertTrue(w.categories.any { it.name == "Doze modulare" })
    }

    @Test
    fun `dozele aparat nu sunt filtrate ca doze modulare`() {
        assertFalse(isModularBox("Doză aparat pentru priză"))
        assertFalse(isModularBox("Priză dublă încastrată"))
        assertTrue(isModularBox("Doză 7 module"))
        assertTrue(isModularBox("doza 2 module"))
    }

    // ---- upsertWork ----

    @Test
    fun `salvarea cu acelasi nume inlocuieste lucrarea veche`() {
        val old = Work(1, "Casa Ciuvică", 100L, emptyList())
        val other = Work(2, "Alt client", 200L, emptyList())
        val updated = Work(3, "casa ciuvică", 300L, emptyList())

        val result = upsertWork(listOf(old, other), updated)
        assertEquals(2, result.size)
        assertEquals(updated, result[0])
        assertTrue(result.contains(other))
        assertFalse(result.contains(old))
    }

    @Test
    fun `nume diferit adauga lucrare noua`() {
        val old = Work(1, "Casa A", 100L, emptyList())
        val result = upsertWork(listOf(old), Work(2, "Casa B", 200L, emptyList()))
        assertEquals(2, result.size)
    }

    // ---- migrateV3 ----

    @Test
    fun `migrarea adauga modulele si aparatajul incastrat`() {
        val cats = listOf(
            Category(1, "Module", listOf(Material(10, "Priză simplă", 0)))
        )
        val result = migrateV3(cats) { nextId() }

        val mod = result.first { it.name == "Module" }
        assertTrue(mod.materials.any { it.name == "Modul TV" })
        assertTrue(mod.materials.any { it.name == "Modul rețea (CAT5/6)" })

        val buried = result.first { it.name == "Aparataj încastrat" }
        assertEquals(6, buried.materials.size)
        assertTrue(buried.materials.any { it.name == "Întrerupător dublu încastrat" })
    }

    @Test
    fun `migrarea nu dubleaza ce exista deja si pastreaza cantitatile`() {
        val cats = listOf(
            Category(1, "Module", listOf(Material(10, "Modul TV", qty = 4))),
            Category(2, "Aparataj încastrat", listOf(Material(20, "Priză simplă încastrată", qty = 2)))
        )
        val result = migrateV3(migrateV3(cats) { nextId() }) { nextId() }

        val mod = result.first { it.name == "Module" }
        assertEquals(1, mod.materials.count { it.name == "Modul TV" })
        assertEquals(4, mod.materials.first { it.name == "Modul TV" }.qty)

        val buried = result.first { it.name == "Aparataj încastrat" }
        assertEquals(1, buried.materials.count { it.name == "Priză simplă încastrată" })
        assertEquals(2, buried.materials.first { it.name == "Priză simplă încastrată" }.qty)
        assertEquals(6, buried.materials.size)
    }

    @Test
    fun `aparatajul incastrat nu intra in calculul de module`() {
        // doar categoria "Module" conteaza la obturatoare
        val w = Work(
            1, "Test", 0L,
            listOf(
                Category(1, "Doze modulare", listOf(Material(10, "Doză 2 module", 1))),
                Category(2, "Aparataj încastrat", listOf(Material(20, "Priză dublă încastrată", 5)))
            )
        ).withAutoAccessories()
        val acc = w.categories.first { it.name.contains("calcul automat") }
        // niciun modul folosit -> toate cele 2 sloturi devin obturatoare
        assertEquals(2, acc.materials.first { it.name == "Obturator (modul fals)" }.qty)
    }
}
