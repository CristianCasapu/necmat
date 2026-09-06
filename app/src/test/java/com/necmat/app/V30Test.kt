package com.necmat.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.30 — manoperă pe toate elementele, cabluri pe metru după modul de montaj, catalog v11. */
class V30Test {

    private var id = 30_000L
    private fun nextId() = id++

    @Test
    fun `cablurile se taxeaza pe metru dupa modul categoriei`() {
        val cfg = Repo.defaultLaborConfig()
        val incastrat = Category(1, "Cabluri și tuburi (m)", listOf(
            Material(10, "Cablu CYY-F 3x2.5", 40),
            Material(11, "Conductor FY 2.5 mm²", 20)
        ))
        val q1 = laborQuote(Work(1, "T", 0L, listOf(incastrat)), cfg)
        val l1 = q1.lines.single()
        assertEquals("Montaj cablu încastrat", l1.name)
        assertEquals(60, l1.qty)
        assertEquals(5.0, l1.unitPrice, 0.001)
        assertEquals(300.0, l1.value, 0.001)

        val aparent = incastrat.copy(phase = "aparent")
        val l2 = laborQuote(Work(1, "T", 0L, listOf(aparent)), cfg).lines.single()
        assertEquals("Montaj cablu aparent", l2.name)
        assertEquals(180.0, l2.value, 0.001)   // 60 m × 3 lei
    }

    @Test
    fun `tuburile si jgheaburile au pret propriu, nu pe modul cablului`() {
        val cfg = Repo.defaultLaborConfig().copy(
            dozaPrices = Repo.defaultLaborConfig().dozaPrices +
                ("jgheab metalic perforat 100x60 cu capac" to 12.0) + ("tub copex ø16" to 2.0)
        )
        val cat = Category(1, "Cabluri și tuburi (m)", listOf(
            Material(10, "Jgheab metalic perforat 100x60 cu capac", 10),
            Material(11, "Tub copex Ø16", 30),
            Material(12, "Pat de cablu 100 mm", 5)          // fără preț → nu apare
        ))
        val q = laborQuote(Work(1, "T", 0L, listOf(cat)), cfg)
        val l = q.lines.single()
        assertEquals("Montaj tuburi, canale și jgheaburi", l.name)
        assertEquals(40, l.qty)
        assertEquals(10 * 12.0 + 30 * 2.0, l.value, 0.001)
        assertEquals(0.0, l.unitPrice, 0.001)   // prețuri mixte → „–” în PDF
    }

    @Test
    fun `componentele de tablou si aparatajul incastrat sau aplicat intra in oferta`() {
        val cfg = LaborConfig(dozaPrices = mapOf(
            "mcb 1p+n 16a" to 15.0, "priză aplicată" to 25.0, "priză simplă încastrată" to 20.0
        ))
        val work = Work(1, "T", 0L, listOf(
            Category(1, "Tablou electric", listOf(Material(10, "MCB 1P+N 16A", 8), Material(11, "Tablou 2 rânduri (26 module)", 1))),
            Category(2, "Aparataj aplicat", listOf(Material(20, "Priză aplicată", 4))),
            Category(3, "Aparataj încastrat", listOf(Material(30, "Priză simplă încastrată", 6))),
            Category(4, "Module", listOf(Material(40, "Priză simplă", 5)))   // modulele nu au manoperă proprie
        ))
        val q = laborQuote(work, cfg)
        assertEquals(120.0, q.lines.first { it.name == "Echipare tablou — componente" }.value, 0.001)
        assertEquals(100.0, q.lines.first { it.name == "Montaj aparataj aplicat" }.value, 0.001)
        assertEquals(120.0, q.lines.first { it.name == "Montaj aparataj încastrat" }.value, 0.001)
        assertTrue(q.lines.none { it.name.contains("Module") })
        assertEquals(340.0, q.laborTotal, 0.001)
    }

    @Test
    fun `detectarea cablurilor si a categoriei`() {
        assertTrue(isCableItem("Cablu CYY-F 5x16"))
        assertTrue(isCableItem("Conductor FY 4 mm²"))
        assertTrue(isCableItem("cablu NYY-J 3x2.5"))
        assertFalse(isCableItem("Canal cablu PVC 25x16"))
        assertFalse(isCableItem("Pat de cablu 100 mm"))
        assertFalse(isCableItem("Tub copex Ø16"))
        assertFalse(isCableItem("Jgheab metalic perforat 100x60 cu capac"))
        assertTrue(isCableCategory("Cabluri și tuburi (m)"))
        assertFalse(isCableCategory("Tablou electric"))
        assertEquals("incastrat", cableModeOf(Category(1, "Cabluri și tuburi (m)")))
        assertEquals("aparent", cableModeOf(Category(1, "Cabluri și tuburi (m)", phase = "aparent")))
        assertEquals("Aparent", Category(1, "X", phase = "aparent").phaseLabel)
    }

    @Test
    fun `preturile pe metru sunt implicite, se pastreaza in json si la merge`() {
        val d = Repo.defaultLaborConfig()
        assertEquals(5.0, d.cablePerMeter["incastrat"]!!, 0.001)
        assertEquals(3.0, d.cablePerMeter["aparent"]!!, 0.001)
        val custom = LaborConfig(cablePerMeter = mapOf("aparent" to 4.0))
        val merged = Repo.mergeLaborDefaults(custom)
        assertEquals(4.0, merged.cablePerMeter["aparent"]!!, 0.001)
        assertEquals(5.0, merged.cablePerMeter["incastrat"]!!, 0.001)
        val back = Repo.laborFromJson(Repo.laborToJson(merged))
        assertEquals(merged.cablePerMeter, back.cablePerMeter)
        // configurări vechi fără „cable” → implicite după merge
        val old = Repo.laborFromJson(org.json.JSONObject().put("doza", org.json.JSONObject()))
        assertTrue(old.cablePerMeter.isEmpty())
        assertEquals(5.0, Repo.mergeLaborDefaults(old).cablePerMeter["incastrat"]!!, 0.001)
    }

    @Test
    fun `migrarea v11 adauga cablurile o singura data si e in catalogul implicit`() {
        val cats = listOf(Category(1, "Cabluri și tuburi (m)", listOf(Material(10, "Cablu CYY-F 3x1.5", 12))))
        val once = Repo.migrateV11(cats) { nextId() }
        val twice = Repo.migrateV11(once) { nextId() }
        val c = twice.single()
        assertEquals(1 + Repo.cableExtrasV11.size, c.materials.size)
        assertEquals(12, c.materials.first { it.name == "Cablu CYY-F 3x1.5" }.qty)
        assertTrue(c.materials.any { it.name == "Cablu NYY-J 5x16" })
        assertTrue(c.materials.any { it.name == "Jgheab metalic perforat 200x60 cu capac" })
        val def = Repo.defaultCatalog().first { it.name == "Cabluri și tuburi (m)" }
        assertTrue(def.materials.any { it.name == "Conductor FY 25 mm²" })
        // auto-repararea include v11
        val repaired = Repo.applyAllMigrations(cats) { nextId() }
        assertTrue(repaired.first { isCableCategory(it.name) }.materials.any { it.name == "Copex metalic Ø20 (aparent)" })
        // alte categorii nu sunt atinse
        assertNull(Repo.migrateV11(listOf(Category(2, "Module"))) { nextId() }.first().materials.firstOrNull())
    }
}
