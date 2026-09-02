package com.necmat.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class V7Test {

    private var idCounter = 7000L
    private fun nextId(): Long = idCounter++

    @Test
    fun `numele pdf contine titlul clientul adresa si data`() {
        val date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            .parse("02.09.2026")!!.time
        val w = Work(
            1, "Necesar materiale", date, emptyList(),
            client = "Familia Ciuvică", address = "Str. Teiului 5", phone = "0722"
        )
        assertEquals(
            "Necesar materiale - Familia Ciuvică - Str. Teiului 5 - 02.09.2026.pdf",
            pdfFileName(w)
        )
    }

    @Test
    fun `numele pdf omite campurile goale si caracterele interzise`() {
        val date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            .parse("02.09.2026")!!.time
        val w = Work(1, "Casa: A/B <test>", date, emptyList())
        val name = pdfFileName(w)
        assertTrue(name.endsWith("02.09.2026.pdf"))
        assertFalse(name.contains("/"))
        assertFalse(name.contains(":"))
        assertFalse(name.contains("<"))
    }

    @Test
    fun `componentele trifazice sunt detectate`() {
        assertTrue(isTriphasicItem("MCB 3P 16A"))
        assertTrue(isTriphasicItem("Separator trifazic (4P)"))
        assertTrue(isTriphasicItem("Diferențial general trifazic (4P)"))
        assertTrue(isTriphasicItem("Busbar trifazic 18 module (pieptene 3P)"))
        assertFalse(isTriphasicItem("MCB 1P+N 16A"))
        assertFalse(isTriphasicItem("Diferențial cu protecție (RCBO) 1P+N 16A"))
        assertFalse(isTriphasicItem("Busbar 13 module (pieptene 1P+N)"))
        assertFalse(isTriphasicItem("Priză simplă"))
    }

    @Test
    fun `migrarea v5 adauga componentele de tablou fara dubluri`() {
        val cats = listOf(
            Category(1, "Tablou electric", listOf(Material(10, "MCB 1P+N 16A", 4))),
            Category(2, "Module")
        )
        val once = Repo.migrateV5(cats) { nextId() }
        val tablou = once.first { it.name == "Tablou electric" }
        listOf(
            "Tablou 2 rânduri (26 module)", "MCB 3P 25A",
            "Diferențial cu protecție (RCBO) 1P+N 16A",
            "Descărcător supratensiune (SPD) Tip 2",
            "Releu protecție tensiune (min/max)", "Contactor modular 25A",
            "Busbar 18 module (pieptene 1P+N)"
        ).forEach { n ->
            assertTrue("lipsește $n", tablou.materials.any { it.name == n })
        }
        // cantitățile existente rămân
        assertEquals(4, tablou.materials.first { it.name == "MCB 1P+N 16A" }.qty)
        // idempotent
        val twice = Repo.migrateV5(once) { nextId() }
        assertEquals(
            once.first { it.name == "Tablou electric" }.materials.size,
            twice.first { it.name == "Tablou electric" }.materials.size
        )
        // alte categorii neatinse
        assertTrue(once.first { it.name == "Module" }.materials.isEmpty())
    }

    @Test
    fun `catalogul implicit contine noile componente de tablou`() {
        val tablou = Repo.defaultCatalog().first { it.name == "Tablou electric" }
        assertTrue(tablou.materials.any { it.name == "Tablou 1 rând (13 module)" })
        assertTrue(tablou.materials.any { it.name == "Busbar 24 module (pieptene 1P+N)" })
        assertTrue(tablou.materials.any { it.name == "Sonerie modulară" })
    }
}
