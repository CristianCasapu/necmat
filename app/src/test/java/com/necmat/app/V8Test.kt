package com.necmat.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V8Test {

    private fun work() = Work(
        1, "Test", 0L,
        listOf(
            Category(1, "Doze aparat încastrate", listOf(Material(10, "Doză aparat pentru priză", 8))),
            Category(2, "Doze modulare", listOf(Material(20, "Doză 3 module", 2))),
            Category(3, "Module", listOf(Material(30, "Priză simplă", 5))),
            Category(4, "Tablou electric", listOf(
                Material(40, "MCB 1P+N 16A", 6),
                Material(41, "Diferențial general", 1),
                Material(42, "Tablou 2 rânduri (26 module)", 1)
            )),
            Category(5, "Doze legături", listOf(
                Material(50, "Doză încastrată", 4),
                Material(51, "Clemă distribuție simplă (4 intrări)", 12)
            ))
        )
    )

    @Test
    fun `pdf exclude dozele si carcasele de tablou dar pastreaza restul`() {
        val filtered = work().withAutoAccessories().filterForPdf(includeBoxes = false)
        val names = filtered.categories.map { it.name }

        // categoriile ramase doar cu doze dispar
        assertFalse(names.contains("Doze aparat încastrate"))
        assertFalse(names.contains("Doze modulare"))

        // componentele tabloului RAMAN in PDF (bug reparat in v1.9);
        // doar carcasa tabloului dispare
        val tablou = filtered.categories.first { it.name == "Tablou electric" }
        assertTrue(tablou.materials.any { it.name == "MCB 1P+N 16A" })
        assertTrue(tablou.materials.any { it.name == "Diferențial general" })
        assertFalse(tablou.materials.any { it.name == "Tablou 2 rânduri (26 module)" })

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
        assertTrue(isDozaItem("Doză încastrată"))
        assertFalse(isDozaItem("Clemă distribuție simplă (4 intrări)"))
        assertFalse(isDozaItem("Ramă suport 3 module"))
        assertFalse(isDozaItem("Obturator (modul fals)"))
        assertFalse(isDozaItem("MCB 1P+N 16A"))
    }

    @Test
    fun `detectarea carcaselor de tablou dupa nume`() {
        assertTrue(isTablouCarcasa("Tablou 1 rând (13 module)"))
        assertTrue(isTablouCarcasa("Tablou 3 rânduri (39 module)"))
        assertFalse(isTablouCarcasa("MCB 1P+N 16A"))
        assertFalse(isTablouCarcasa("Busbar 13 module (pieptene 1P+N)"))
        assertFalse(isTablouCarcasa("Releu monitorizare fază"))
    }
}
