package com.necmat.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaborTest {

    @Test
    fun `etajele tabloului se deduc din carcasa`() {
        assertEquals(1 to 13, tablouRows("Tablou 1 rând (13 module)"))
        assertEquals(2 to 13, tablouRows("Tablou 2 rânduri (26 module)"))
        assertEquals(3 to 13, tablouRows("Tablou 3 rânduri (39 module)"))
        assertEquals(2 to 18, tablouRows("Tablou 2 rânduri (36 module)"))
        assertEquals(1 to 24, tablouRows("Tablou 1 rând (24 module)"))
        assertNull(tablouRows("MCB 1P+N 16A"))
        assertNull(tablouRows("Doză 3 module"))
    }

    @Test
    fun `oferta calculeaza dozele si etajele de tablou`() {
        val cfg = LaborConfig(
            dozaPrices = mapOf(
                "doză 3 module" to 25.0,
                "doză aparat pentru priză" to 15.0
            ),
            rowPrices = mapOf(13 to 100.0, 18 to 140.0),
            travel = 50.0, food = 30.0, consumables = 20.0
        )
        val work = Work(
            1, "Test", 0L,
            listOf(
                Category(1, "Doze modulare", listOf(Material(10, "Doză 3 module", 4))),
                Category(2, "Doze aparat încastrate", listOf(
                    Material(20, "Doză aparat pentru priză", 6)
                )),
                Category(3, "Tablou electric", listOf(
                    Material(30, "Tablou 2 rânduri (26 module)", 1),
                    Material(31, "MCB 1P+N 16A", 8)
                ))
            )
        )
        val q = laborQuote(work, cfg)

        // 4 × 25 + 6 × 15 + 2 etaje × 100 — comasate pe grupuri
        assertEquals(3, q.lines.size)
        assertEquals(100.0, q.lines.first { it.name == "Montaj aparataj modular" }.value, 0.001)
        assertEquals(90.0, q.lines.first { it.name == "Montaj aparataj" }.value, 0.001)
        val tablou = q.lines.first { it.name.startsWith("Echipare tablou") }
        assertEquals(2, tablou.qty)
        assertEquals(200.0, tablou.value, 0.001)
        assertEquals(390.0, q.laborTotal, 0.001)
        // + deplasare 50 + mâncare 30 + consumabile 20
        assertEquals(490.0, q.total, 0.001)
    }

    @Test
    fun `etajul fara pret exact foloseste cea mai apropiata marime`() {
        val cfg = LaborConfig(rowPrices = mapOf(13 to 100.0, 24 to 180.0))
        val work = Work(
            1, "Test", 0L,
            listOf(Category(1, "Tablou electric", listOf(
                Material(10, "Tablou 2 rânduri (36 module)", 1)  // etaj de 18
            )))
        )
        val q = laborQuote(work, cfg)
        // 18 e mai aproape de 13 decât de 24 la egalitate? |18-13|=5, |18-24|=6 -> 13
        assertEquals(100.0, q.lines.first().unitPrice, 0.001)
    }

    @Test
    fun `preturile implicite sunt cele cerute`() {
        val cfg = Repo.defaultLaborConfig()
        assertEquals(300.0, cfg.rowPrices[13]!!, 0.001)
        assertEquals(30.0, cfg.dozaPrices["doză 3 module"]!!, 0.001)
        // doza de 4 module este doză mare
        assertEquals(50.0, cfg.dozaPrices["doză 4 module"]!!, 0.001)
        assertEquals(50.0, cfg.dozaPrices["doză 5 module"]!!, 0.001)
        assertEquals(50.0, cfg.dozaPrices["doză 7 module"]!!, 0.001)
        assertEquals(30.0, cfg.dozaPrices["bec sau aplică rotundă"]!!, 0.001)
        assertEquals(50.0, cfg.dozaPrices["lustră mică / aplică pătrată-dreptunghiulară"]!!, 0.001)
        assertEquals(100.0, cfg.dozaPrices["lustră medie"]!!, 0.001)
        assertEquals(150.0, cfg.dozaPrices["lustră mare"]!!, 0.001)
        assertEquals(250.0, cfg.helperPerDay, 0.001)
        // doze de legături pe trepte de circuite
        assertEquals(50.0, cfg.dozaPrices["doză legături mică (până la 5 circuite)"]!!, 0.001)
        assertEquals(100.0, cfg.dozaPrices["doză legături medie (până la 15 circuite)"]!!, 0.001)
        assertEquals(150.0, cfg.dozaPrices["doză legături mare (până la 20 circuite)"]!!, 0.001)
    }

    @Test
    fun `dozele de legaturi pe trepte intra in oferta`() {
        val cfg = Repo.defaultLaborConfig()
        val work = Work(
            1, "Test", 0L,
            listOf(
                Category(1, "Doze legături", listOf(
                    Material(10, "Doză legături mică (până la 5 circuite)", 2),
                    Material(11, "Doză legături mare (până la 20 circuite)", 1)
                ))
            )
        )
        val q = laborQuote(work, cfg)
        // 2 × 50 + 1 × 150
        assertEquals(250.0, q.laborTotal, 0.001)
    }

    @Test
    fun `migrarea v9 adauga treptele o singura data`() {
        var id = 200L
        val cats = listOf(Category(1, "Doze legături", listOf(Material(10, "Doză simplă", 1))))
        val once = Repo.migrateV9(cats) { id++ }
        val twice = Repo.migrateV9(once) { id++ }
        val doze = twice.first { it.name == "Doze legături" }
        assertEquals(4, doze.materials.size)
        assertTrue(doze.materials.any { it.name == "Doză legături medie (până la 15 circuite)" })
        assertEquals(1, doze.materials.first { it.name == "Doză simplă" }.qty)
    }

    @Test
    fun `corpurile de iluminat si ajutorul intra in oferta`() {
        val cfg = Repo.defaultLaborConfig()
        val work = Work(
            1, "Test", 0L,
            listOf(
                Category(1, "Corpuri de iluminat (montaj)", listOf(
                    Material(10, "Lustră medie", 2),
                    Material(11, "Aplică pe perete", 3)
                ))
            )
        )
        val q = laborQuote(work, cfg).copy(days = 3, helperPerDay = 250.0)
        // 2 × 100 + 3 × 30 = 290
        assertEquals(290.0, q.laborTotal, 0.001)
        // + 3 zile × 250 ajutor
        assertEquals(750.0, q.helperCost, 0.001)
        assertEquals(1040.0, q.total, 0.001)
    }

    @Test
    fun `corpurile de iluminat nu apar in pdf-ul de materiale`() {
        val w = Work(
            1, "Test", 0L,
            listOf(
                Category(1, "Corpuri de iluminat (montaj)", listOf(Material(10, "Lustră mare", 1))),
                Category(2, "Module", listOf(Material(20, "Priză simplă", 2)))
            )
        )
        // excluse indiferent de setarea includeBoxes
        assertTrue(w.filterForPdf(false).categories.none { isMontajCategory(it.name) })
        assertTrue(w.filterForPdf(true).categories.none { isMontajCategory(it.name) })
        assertTrue(w.filterForPdf(false).categories.any { it.name == "Module" })
    }

    @Test
    fun `migrarea v8 adauga corpurile de iluminat o singura data`() {
        var id = 100L
        val once = Repo.migrateV8(listOf(Category(1, "Module"))) { id++ }
        assertTrue(once.any { it.name == "Corpuri de iluminat (montaj)" })
        val twice = Repo.migrateV8(once) { id++ }
        assertEquals(
            once.first { isMontajCategory(it.name) }.materials.size,
            twice.first { isMontajCategory(it.name) }.materials.size
        )
        assertEquals(5, twice.first { isMontajCategory(it.name) }.materials.size)
    }

    @Test
    fun `liniile se comaseaza pe grupuri cu pret unitar doar cand e uniform`() {
        val cfg = Repo.defaultLaborConfig()
        val work = Work(
            1, "Test", 0L,
            listOf(
                Category(1, "Doze modulare", listOf(
                    Material(10, "Doză 2 module", 3),   // 30 lei
                    Material(11, "Doză 5 module", 1)    // 50 lei -> prețuri mixte
                )),
                Category(2, "Doze legături", listOf(
                    Material(20, "Doză legături mică (până la 5 circuite)", 2)  // 50 lei uniform
                ))
            )
        )
        val q = laborQuote(work, cfg)

        val modular = q.lines.first { it.name == "Montaj aparataj modular" }
        assertEquals(4, modular.qty)
        assertEquals(140.0, modular.value, 0.001)   // 3×30 + 1×50
        assertEquals(0.0, modular.unitPrice, 0.001) // mixt -> "-" în PDF

        val legaturi = q.lines.first { it.name == "Montaj doze legături" }
        assertEquals(2, legaturi.qty)
        assertEquals(50.0, legaturi.unitPrice, 0.001) // uniform -> P.U. afișat
        assertEquals(100.0, legaturi.value, 0.001)
    }

    @Test
    fun `configurarile vechi primesc preturile noi fara a pierde ce a setat utilizatorul`() {
        // fișier salvat înainte de prețurile pentru iluminat/legături/tablou,
        // cu un preț personalizat de utilizator
        val old = LaborConfig(
            dozaPrices = mapOf("doză 3 module" to 35.0),
            rowPrices = emptyMap()
        )
        val merged = Repo.mergeLaborDefaults(old)
        // prețul utilizatorului rămâne
        assertEquals(35.0, merged.dozaPrices["doză 3 module"]!!, 0.001)
        // prețurile absente vin din implicite
        assertEquals(100.0, merged.dozaPrices["lustră medie"]!!, 0.001)
        assertEquals(100.0, merged.dozaPrices["doză legături medie (până la 15 circuite)"]!!, 0.001)
        assertEquals(300.0, merged.rowPrices[13]!!, 0.001)
    }

    @Test
    fun `dozele fara pret nu genereaza linii`() {
        val work = Work(
            1, "Test", 0L,
            listOf(Category(1, "Doze modulare", listOf(Material(10, "Doză 3 module", 4))))
        )
        val q = laborQuote(work, LaborConfig(travel = 50.0))
        assertTrue(q.lines.isEmpty())
        assertEquals(50.0, q.total, 0.001)
    }
}
