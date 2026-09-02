package com.necmat.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V9Test {

    // ---- redenumire îngropat -> încastrat ----

    @Test
    fun `redenumirea schimba categoriile si materialele pastrand datele`() {
        val cats = listOf(
            Category(
                1, "Aparataj îngropat",
                listOf(Material(10, "Priză simplă îngropată", qty = 3, price = 12.5)),
                brand = "Schneider Electric", model = "Asfora"
            ),
            Category(2, "Doze aparat îngropate", listOf(Material(20, "Doză aparat pentru priză", 8)))
        )
        val renamed = renameTermCategories(cats)
        assertEquals("Aparataj încastrat", renamed[0].name)
        assertEquals("Priză simplă încastrată", renamed[0].materials[0].name)
        assertEquals(3, renamed[0].materials[0].qty)
        assertEquals(12.5, renamed[0].materials[0].price, 0.001)
        assertEquals("Schneider Electric", renamed[0].brand)
        assertEquals("Doze aparat încastrate", renamed[1].name)
    }

    @Test
    fun `redenumirea se aplica si lucrarilor salvate`() {
        val works = listOf(
            Work(
                1, "Casa X", 0L,
                listOf(Category(1, "Aparataj îngropat", listOf(Material(10, "Priză TV îngropată", 2))))
            )
        )
        val renamed = renameTermWorks(works)
        assertEquals("Aparataj încastrat", renamed[0].categories[0].name)
        assertEquals("Priză TV încastrată", renamed[0].categories[0].materials[0].name)
        assertEquals("Casa X", renamed[0].name)
    }

    @Test
    fun `catalogul implicit nu mai contine ingropat`() {
        Repo.defaultCatalog().forEach { c ->
            assertFalse(c.name.contains("îngropat"))
            c.materials.forEach { assertFalse(it.name.contains("îngropat")) }
        }
    }

    // ---- sumar accesorii ----

    @Test
    fun `sumarul de accesorii calculeaza sloturi module si obturatoare`() {
        val cats = listOf(
            Category(1, "Doze modulare", listOf(
                Material(10, "Doză 3 module", 2),
                Material(11, "Doză 4 module", 1)
            )),
            Category(2, "Module", listOf(
                Material(20, "Priză dublă", 2),   // 4 module
                Material(21, "Cap scară", 3)      // 3 module
            ))
        )
        val s = accessorySummary(cats)!!
        assertEquals(3, s.boxes)
        assertEquals(10, s.slots)
        assertEquals(7, s.usedModules)
        assertEquals(3, s.obturatoare)
        assertFalse(s.overflow)
    }

    @Test
    fun `sumarul semnaleaza depasirea sloturilor`() {
        val cats = listOf(
            Category(1, "Doze modulare", listOf(Material(10, "Doză 2 module", 1))),
            Category(2, "Module", listOf(Material(20, "Priză dublă", 2)))  // 4 > 2
        )
        val s = accessorySummary(cats)!!
        assertTrue(s.overflow)
        assertEquals(0, s.obturatoare)
    }

    @Test
    fun `fara doze modulare nu exista sumar`() {
        assertNull(accessorySummary(listOf(Category(1, "Module", listOf(Material(10, "Cap scară", 5))))))
    }

    // ---- șabloane ----

    @Test
    fun `starea de sablon se pastreaza in backup`() {
        val works = listOf(Work(1, "Apartament 2 camere", 0L, emptyList(), isTemplate = true))
        val parsed = Repo.parseBackup(Repo.backupJson(emptyList(), works))!!
        assertTrue(parsed.works[0].isTemplate)
    }
}
