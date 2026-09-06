package com.necmat.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.35 — asistentul pentru necesar nou (electric / fotovoltaic). */
class V35Test {

    private fun WizardPlan.qty(material: String): Int =
        items.filter { it.material.equals(material, ignoreCase = true) }.sumOf { it.qty }

    private fun WizardPlan.cats() = items.map { it.category }.toSet()

    // ---- electric ----

    @Test
    fun `apartament 2 dormitoare - 2 prize, 1 intrerupator, 1 bec pe incapere`() {
        val i = ElectricInput(bedrooms = 2, bathrooms = 1, kitchens = 1, living = 1)   // 5 încăperi
        val p = WizardEstimator.estimateElectric(i)
        assertEquals(5 * 2 + 2, p.qty("Priză 2 module"))           // +2 în bucătărie
        assertEquals(5, p.qty("Întrerupător simplu"))
        assertEquals(5, p.qty("Bec sau aplică rotundă"))
        assertEquals(1, p.qty("Lustră medie"))
        // modular: câte o doză de 2 module pentru fiecare aparat
        assertEquals(12 + 5, p.qty("Doză 2 module"))
        assertEquals("mono", p.tablouPhase)
        assertEquals("incastrat", p.cableMode)
        assertEquals(true, p.pdfIncludeBoxes)
    }

    @Test
    fun `aparataj clasic - doze aparat separate priza si intrerupator`() {
        val p = WizardEstimator.estimateElectric(ElectricInput(apparatus = ApparatusKind.CLASIC))
        assertTrue(p.qty("Priză simplă încastrată") > 0)
        assertEquals(p.qty("Priză simplă încastrată"), p.qty("Doză aparat pentru priză"))
        assertEquals(p.qty("Întrerupător simplu încastrat"), p.qty("Doză aparat pentru întrerupător"))
        assertEquals(0, p.qty("Doză 2 module"))
    }

    @Test
    fun `instalatie existenta cu doze, cabluri si tablou pastrate - nu se adauga`() {
        val i = ElectricInput(newInstall = false, boxesMounted = true, cablesPulled = true, keepPanel = true)
        val p = WizardEstimator.estimateElectric(i)
        assertFalse(WizardEstimator.CAT_DOZE_MODULARE in p.cats())
        assertFalse(WizardEstimator.CAT_LEGATURI in p.cats())
        assertFalse(WizardEstimator.CAT_CABLURI in p.cats())
        assertFalse(WizardEstimator.CAT_TABLOU in p.cats())
        assertTrue(WizardEstimator.CAT_MODULE in p.cats())
        assertNull(p.pdfIncludeBoxes)
        assertTrue(p.notes.any { it.contains("păstrează") })
    }

    @Test
    fun `trifazic aduce componente 4P si 3P si cablu 5x6`() {
        val p = WizardEstimator.estimateElectric(ElectricInput(supply = Supply.TRI, dwelling = Dwelling.CASA))
        assertEquals(1, p.qty("Separator trifazic (4P)"))
        assertEquals(1, p.qty("Diferențial general trifazic (4P)"))
        assertEquals(1, p.qty("MCB 3P 32A"))
        assertEquals(0, p.qty("Diferențial general"))
        assertEquals(15, p.qty("Cablu CYY-F 5x6"))
        assertEquals(0, p.qty("Cablu CYY-F 3x6"))
        assertEquals("tri", p.tablouPhase)
    }

    @Test
    fun `comercial cu modular trece pe aplicat, copex metalic si jgheab`() {
        val p = WizardEstimator.estimateElectric(
            ElectricInput(type = ElectricType.COMERCIAL, apparatus = ApparatusKind.MODULAR, otherRooms = 3)
        )
        assertTrue(WizardEstimator.CAT_APARATAJ_APLICAT in p.cats())
        assertFalse(WizardEstimator.CAT_MODULE in p.cats())
        assertTrue(p.qty("Copex metalic Ø20 (aparent)") > 0)
        assertTrue(p.qty("Jgheab metalic perforat 100x60 cu capac") > 0)
        assertEquals("aparent", p.cableMode)
        assertTrue(p.notes.any { it.contains("aplicat") })
    }

    @Test
    fun `carcasa tabloului creste cu numarul de circuite`() {
        val small = WizardEstimator.estimateElectric(ElectricInput(bedrooms = 1, bathrooms = 1, kitchens = 1, living = 0))
        val big = WizardEstimator.estimateElectric(ElectricInput(bedrooms = 5, bathrooms = 3, kitchens = 2, living = 2, otherRooms = 4))
        assertEquals(1, small.qty("Tablou 2 rânduri (26 module)"))
        assertEquals(1, big.qty("Tablou 3 rânduri (39 module)"))
        assertEquals(1, small.qty("Descărcător supratensiune (SPD) Tip 2"))
    }

    @Test
    fun `marca aleasa ajunge pe categoriile de aparataj`() {
        val p = WizardEstimator.estimateElectric(ElectricInput(brand = "Schneider", brandModel = "Sedna"))
        assertEquals("Schneider" to "Sedna", p.brandFor[WizardEstimator.CAT_MODULE])
        assertEquals("Schneider" to "Sedna", p.brandFor[WizardEstimator.CAT_DOZE_MODULARE])
        val clasic = WizardEstimator.estimateElectric(ElectricInput(brand = "Gewiss", apparatus = ApparatusKind.CLASIC))
        assertTrue(WizardEstimator.CAT_APARATAJ_INCASTRAT in clasic.brandFor)
        assertFalse(WizardEstimator.CAT_MODULE in clasic.brandFor)
    }

    // ---- fotovoltaic ----

    @Test
    fun `numar panouri, string-uri si baterii`() {
        val i = PvInput(kw = 6.0, panelW = 450, inverterKw = 6.0, batteryKwh = 10.0)
        assertEquals(14, i.panels)            // 6000 / 450 = 13.3 → 14
        assertEquals(2, i.strings)
        val p = WizardEstimator.estimatePv(i)
        assertEquals(14, p.qty("Panou fotovoltaic 450 W"))
        assertEquals(2, p.qty("Baterie LiFePO4 5 kWh"))
        assertEquals(14, p.qty("Cârlig țiglă (set/panou)"))
        assertEquals(4, p.qty("Siguranță DC 1000 V 16 A"))
        assertEquals(1, p.qty("Cutie protecții DC (tablou DC)"))
        assertEquals(1, p.qty("Cutie protecții AC (tablou AC)"))
        assertEquals(1, p.qty("Invertor hibrid monofazic 6 kW"))
        assertEquals(1, p.qty("MCB 1P+N 32 A invertor"))
        assertTrue(p.qty("Cablu solar 6 mm²") > 0)
        assertEquals("aparent", p.cableMode)
    }

    @Test
    fun `manopera PV - kW x euro x curs, pe trepte de dificultate`() {
        val base = PvInput(kw = 10.0, eurRate = 5.0)
        assertEquals(6500.0, base.copy(difficulty = PvDifficulty.NORMAL).laborLei, 0.001)
        assertEquals(7000.0, base.copy(difficulty = PvDifficulty.MEDIU).laborLei, 0.001)
        assertEquals(7500.0, base.copy(difficulty = PvDifficulty.DIFICIL).laborLei, 0.001)
        val p = WizardEstimator.estimatePv(base.copy(difficulty = PvDifficulty.DIFICIL))
        assertEquals(1, p.extraLabor.size)
        assertEquals(7500.0, p.extraLabor.first().value, 0.001)
        assertTrue(p.extraLabor.first().name.contains("150 €/kW"))
    }

    @Test
    fun `PV trifazic - invertor si protectii trifazice, fara baterii`() {
        val p = WizardEstimator.estimatePv(PvInput(kw = 10.0, supply = Supply.TRI, inverterKw = 10.0, roof = RoofKind.TABLA))
        assertEquals(1, p.qty("Invertor hibrid trifazic 10 kW"))
        assertEquals(1, p.qty("MCB 3P 32 A invertor"))
        assertEquals(1, p.qty("Diferențial tip A 40 A 30 mA (4P)"))
        assertEquals(0, p.qty("Baterie LiFePO4 5 kWh"))
        assertEquals(0, p.qty("Cablu baterie 25 mm²"))
        assertTrue(p.qty("Suport tablă (set/panou)") > 0)
        assertEquals(0, p.qty("Cârlig țiglă (set/panou)"))
    }

    @Test
    fun `PV fara putere - plan gol cu indicatie`() {
        val p = WizardEstimator.estimatePv(PvInput(kw = 0.0))
        assertTrue(p.items.isEmpty())
        assertTrue(p.notes.isNotEmpty())
    }

    @Test
    fun `invertorul se rotunjeste in sus la treapta urmatoare`() {
        assertEquals("Invertor hibrid monofazic 5 kW", WizardEstimator.inverterName(Supply.MONO, 4.2))
        assertEquals("Invertor hibrid monofazic 10 kW", WizardEstimator.inverterName(Supply.MONO, 25.0))
        assertEquals("Invertor hibrid trifazic 12 kW", WizardEstimator.inverterName(Supply.TRI, 11.0))
    }

    // ---- model / persistență ----

    @Test
    fun `campurile noi ale lucrarii supravietuiesc JSON-ului`() {
        val w = Work(
            7L, "PV Popescu", 1L,
            categories = listOf(Category(1L, "Sistem fotovoltaic", listOf(Material(2L, "Panou fotovoltaic 450 W", 12)))),
            pdfIncludeBoxes = true,
            extraLabor = listOf(LaborLine("Instalare PV 5 kW", 1, 3250.0)),
            kind = "pv"
        )
        val json = Repo.backupJson(listOf(), listOf(w), listOf(), null, JSONObject())
        val back = Repo.parseBackup(json)!!.works.first()
        assertEquals(true, back.pdfIncludeBoxes)
        assertEquals("pv", back.kind)
        assertEquals(1, back.extraLabor.size)
        assertEquals(3250.0, back.extraLabor.first().value, 0.001)
        // o lucrare veche, fără câmpuri → valori implicite
        val old = Repo.parseBackup(Repo.backupJson(listOf(), listOf(w.copy(pdfIncludeBoxes = null, extraLabor = emptyList(), kind = "")), listOf(), null, JSONObject()))!!.works.first()
        assertNull(old.pdfIncludeBoxes)
        assertTrue(old.extraLabor.isEmpty())
    }

    @Test
    fun `oferta de manopera include liniile fixe ale lucrarii`() {
        val w = Work(
            1L, "PV", 1L, categories = emptyList(),
            extraLabor = listOf(LaborLine("Instalare PV 5 kW", 1, 3250.0))
        )
        val q = laborQuote(w, Repo.defaultLaborConfig())
        assertEquals(3250.0, q.laborTotal, 0.001)
        assertEquals("Instalare PV 5 kW", q.lines.last().name)
    }

    @Test
    fun `regula per lucrare pentru doze in PDF bate setarea`() {
        val w = Work(1L, "x", 1L, emptyList())
        assertFalse(effectiveIncludeBoxes(w, false))
        assertTrue(effectiveIncludeBoxes(w, true))
        assertTrue(effectiveIncludeBoxes(w.copy(pdfIncludeBoxes = true), false))
        assertFalse(effectiveIncludeBoxes(w.copy(pdfIncludeBoxes = false), true))
    }

    @Test
    fun `cutiile PV nu sunt tratate drept carcase de tablou`() {
        assertFalse(isTablouCarcasa("Cutie protecții DC (tablou DC)"))
        assertFalse(isDozaItem("Cutie protecții AC (tablou AC)"))
        assertTrue(isCableItem("Cablu solar 6 mm²"))
    }

    @Test
    fun `migrarea v12 adauga categoria PV si cablurile solare, idempotent`() {
        var id = 1000L
        val old = Repo.defaultCatalog().filterNot { it.name == "Sistem fotovoltaic" }
            .map { c -> if (isCableCategory(c.name)) c.copy(materials = c.materials.filterNot { it.name.startsWith("Cablu solar") }) else c }
        val once = Repo.applyAllMigrations(old) { id++ }
        val pv = once.first { it.name == "Sistem fotovoltaic" }
        assertTrue(pv.materials.any { it.name == "Invertor hibrid monofazic 5 kW" })
        assertTrue(once.first { isCableCategory(it.name) }.materials.any { it.name == "Cablu solar 6 mm²" })
        val twice = Repo.applyAllMigrations(once) { id++ }
        assertEquals(once.sumOf { it.materials.size }, twice.sumOf { it.materials.size })
        assertEquals(1, twice.count { it.name == "Sistem fotovoltaic" })
    }
}
