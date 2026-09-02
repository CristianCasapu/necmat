package com.necmat.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V8Test {

    private fun work() = Work(
        1, "Test", 0L,
        listOf(
            Category(1, "Doze aparat îngropate", listOf(Material(10, "Doză aparat pentru priză", 8))),
            Category(2, "Doze modulare", listOf(Material(20, "Doză 3 module", 2))),
            Category(3, "Module", listOf(Material(30, "Priză simplă", 5))),
            Category(4, "Tablou electric", listOf(
                Material(40, "MCB 1P+N 16A", 6),
                Material(41, "Diferențial general", 1)
            )),
            Category(5, "Doze legături", listOf(
                Material(50, "Doză îngropată", 4),
                Material(51, "Clemă distribuție simplă (4 intrări)", 12)
            ))
        )
    )

    @Test
    fun `pdf exclude dozele si tabloul dar pastreaza restul`() {
        val filtered = work().withAutoAccessories().filterForPdf(includeBoxes = false)
        val names = filtered.categories.map { it.name }

        // tabloul si categoriile ramase doar cu doze dispar
        assertFalse(names.contains("Tablou electric"))
        assertFalse(names.contains("Doze aparat îngropate"))
        assertFalse(names.contains("Doze modulare"))

        // modulele raman
        assertTrue(names.contains("Module"))
        // clemele raman chiar daca sunt in categoria Doze legături
        val legaturi = filtered.categories.first { it.name == "Doze legături" }
        assertEquals(
            listOf("Clemă distribuție simplă (4 intrări)"),
            legaturi.materials.map { it.name }
        )
        // accesoriile calculate raman (rame + obturatoare)
        val acc = filtered.categories.first { it.name.contains("calcul automat") }
        assertTrue(acc.materials.any { it.name == "Ramă suport 3 module" })
    }

    @Test
    fun `cu setarea activata totul ramane in pdf`() {
        val filtered = work().filterForPdf(includeBoxes = true)
        assertEquals(work().categories, filtered.categories)
    }

    @Test
    fun `detectarea dozelor dupa nume`() {
        assertTrue(isDozaItem("Doză aparat pentru priză"))
        assertTrue(isDozaItem("Doză 7 module"))
        assertTrue(isDozaItem("Doză îngropată"))
        assertFalse(isDozaItem("Clemă distribuție simplă (4 intrări)"))
        assertFalse(isDozaItem("Ramă suport 3 module"))
        assertFalse(isDozaItem("Obturator (modul fals)"))
        assertFalse(isDozaItem("MCB 1P+N 16A"))
    }
}
