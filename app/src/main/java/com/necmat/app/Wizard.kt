package com.necmat.app

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Asistentul pentru un necesar nou (v1.35): din câteva răspunsuri produce o
 * listă de materiale cu cantități, semnalizatoare pentru PDF și linii de
 * manoperă suplimentare. Totul e pur și testabil; UI-ul doar colectează
 * răspunsurile și aplică planul.
 */

enum class ProjectKind(val label: String) { ELECTRIC("Instalație electrică"), PV("Sistem fotovoltaic") }
enum class ElectricType(val label: String) { REZIDENTIAL("Rezidențial"), COMERCIAL("Comercial"), INDUSTRIAL("Industrial") }
enum class Supply(val label: String) { MONO("Monofazic"), TRI("Trifazic") }
enum class Dwelling(val label: String) { CASA("Casă"), APARTAMENT("Apartament") }
enum class ApparatusKind(val label: String) {
    MODULAR("Modular (doze modulare + module)"),
    CLASIC("Clasic încastrat"),
    APLICAT("Aplicat (aparent)")
}
enum class RoofKind(val label: String) { TIGLA("Acoperiș țiglă"), TABLA("Acoperiș tablă"), TERASA("Terasă / sol") }
enum class PvDifficulty(val label: String, val eurPerKw: Double) {
    NORMAL("Normal — 130 €/kW", 130.0),
    MEDIU("Mediu — 140 €/kW", 140.0),
    DIFICIL("Dificil — 150 €/kW", 150.0)
}

/** Răspunsurile pentru o instalație electrică. */
data class ElectricInput(
    val type: ElectricType = ElectricType.REZIDENTIAL,
    val supply: Supply = Supply.MONO,
    val atrKw: Double = 0.0,
    val dwelling: Dwelling = Dwelling.APARTAMENT,
    val bedrooms: Int = 2,
    val bathrooms: Int = 1,
    val kitchens: Int = 1,
    val living: Int = 1,
    val otherRooms: Int = 0,
    val apparatus: ApparatusKind = ApparatusKind.MODULAR,
    val brand: String = "",
    val brandModel: String = "",
    /** Instalație nouă de la zero: se cumpără doze, tuburi, cabluri, tablou → apar în PDF. */
    val newInstall: Boolean = true,
    val boxesMounted: Boolean = false,
    val cablesPulled: Boolean = false,
    val keepPanel: Boolean = false
) {
    val rooms: Int get() = bedrooms + bathrooms + kitchens + living + otherRooms
}

/** Răspunsurile pentru un sistem fotovoltaic. */
data class PvInput(
    val kw: Double = 5.0,
    val panelW: Int = 450,
    val supply: Supply = Supply.MONO,
    val inverterKw: Double = 5.0,
    val batteryKwh: Double = 0.0,
    val roof: RoofKind = RoofKind.TIGLA,
    val difficulty: PvDifficulty = PvDifficulty.NORMAL,
    val eurRate: Double = 5.0
) {
    val panels: Int get() = if (kw <= 0 || panelW <= 0) 0 else ceil(kw * 1000.0 / panelW).toInt()
    val strings: Int get() = if (panels == 0) 0 else ceil(panels / 12.0).toInt()
    /** Manopera în lei: kW × €/kW × curs. */
    val laborLei: Double get() = kw * difficulty.eurPerKw * eurRate
}

data class WizardItem(val category: String, val material: String, val qty: Int)

/** Planul produs de asistent. */
data class WizardPlan(
    val items: List<WizardItem>,
    /** true = dozele, carcasele și tuburile intră în PDF pentru această lucrare. */
    val pdfIncludeBoxes: Boolean?,
    val extraLabor: List<LaborLine>,
    val notes: List<String>,
    /** Marca de setat pe categoriile de aparataj (nume categorie → (marcă, model)). */
    val brandFor: Map<String, Pair<String, String>> = emptyMap(),
    /** Modul de montaj pentru cabluri: "incastrat" / "aparent". */
    val cableMode: String = "",
    /** Faza tabloului: "mono" / "tri" (doar la electric). */
    val tablouPhase: String = ""
) {
    val totalPieces: Int get() = items.sumOf { it.qty }
}

object WizardEstimator {

    const val CAT_DOZE_APARAT = "Doze aparat încastrate"
    const val CAT_APARATAJ_INCASTRAT = "Aparataj încastrat"
    const val CAT_DOZE_MODULARE = "Doze modulare"
    const val CAT_MODULE = "Module"
    const val CAT_APARATAJ_APLICAT = "Aparataj aplicat"
    const val CAT_TABLOU = "Tablou electric"
    const val CAT_LEGATURI = "Doze legături"
    const val CAT_CABLURI = "Cabluri și tuburi (m)"
    const val CAT_ILUMINAT = "Corpuri de iluminat (montaj)"
    const val CAT_PV = "Sistem fotovoltaic"

    private class Acc {
        val items = linkedMapOf<Pair<String, String>, Int>()
        fun add(cat: String, name: String, qty: Int) {
            if (qty <= 0) return
            items.merge(cat to name, qty, Int::plus)
        }
        fun list() = items.map { (k, q) -> WizardItem(k.first, k.second, q) }
    }

    fun estimateElectric(i: ElectricInput): WizardPlan {
        val a = Acc()
        val notes = mutableListOf<String>()
        val rooms = i.rooms.coerceAtLeast(1)
        val sockets = rooms * 2 + i.kitchens * 2       // bucătăria are nevoie de prize în plus
        val switches = rooms
        val commercial = i.type != ElectricType.REZIDENTIAL
        val apparatus = if (commercial && i.apparatus == ApparatusKind.MODULAR) ApparatusKind.APLICAT else i.apparatus
        if (apparatus != i.apparatus) notes += "Spațiile comerciale / industriale pornesc cu aparataj aplicat."

        // ---- aparataj + doze pe încăperi
        when (apparatus) {
            ApparatusKind.MODULAR -> {
                a.add(CAT_MODULE, "Priză 2 module", sockets)
                a.add(CAT_MODULE, "Întrerupător simplu", switches)
                if (!i.boxesMounted) {
                    a.add(CAT_DOZE_MODULARE, "Doză 2 module", sockets + switches)
                }
            }
            ApparatusKind.CLASIC -> {
                a.add(CAT_APARATAJ_INCASTRAT, "Priză simplă încastrată", sockets)
                a.add(CAT_APARATAJ_INCASTRAT, "Întrerupător simplu încastrat", switches)
                if (!i.boxesMounted) {
                    a.add(CAT_DOZE_APARAT, "Doză aparat pentru priză", sockets)
                    a.add(CAT_DOZE_APARAT, "Doză aparat pentru întrerupător", switches)
                }
            }
            ApparatusKind.APLICAT -> {
                a.add(CAT_APARATAJ_APLICAT, "Priză aplicată", sockets)
                a.add(CAT_APARATAJ_APLICAT, "Întrerupător aplicat", switches)
            }
        }
        // câte un punct de lumină pe încăpere (montaj — intră în ofertă, nu în PDF)
        a.add(CAT_ILUMINAT, "Bec sau aplică rotundă", rooms)
        if (i.living > 0) a.add(CAT_ILUMINAT, "Lustră medie", i.living)

        // ---- doze de legături
        if (!i.boxesMounted) {
            a.add(CAT_LEGATURI, "Doză legături mică (până la 5 circuite)", rooms)
            if (i.dwelling == Dwelling.CASA || commercial) a.add(CAT_LEGATURI, "Doză legături medie (până la 15 circuite)", 1)
            a.add(CAT_LEGATURI, "Clemă distribuție simplă (4 intrări)", rooms * 2)
        }

        // ---- cabluri și tuburi
        if (!i.cablesPulled) {
            val perRoomPower = if (commercial) 30 else 20
            val perRoomLight = if (commercial) 15 else 12
            a.add(CAT_CABLURI, "Cablu CYY-F 3x2.5", rooms * perRoomPower + if (i.dwelling == Dwelling.CASA) 30 else 0)
            a.add(CAT_CABLURI, "Cablu CYY-F 3x1.5", rooms * perRoomLight)
            a.add(CAT_CABLURI, "Cablu CYY-F 3x4", i.kitchens * 15)            // cuptor / plită
            when (i.supply) {
                Supply.MONO -> a.add(CAT_CABLURI, "Cablu CYY-F 3x6", 10)     // branșament → tablou
                Supply.TRI -> a.add(CAT_CABLURI, "Cablu CYY-F 5x6", 15)
            }
            if (apparatus == ApparatusKind.APLICAT) {
                a.add(CAT_CABLURI, "Copex metalic Ø20 (aparent)", rooms * perRoomPower)
                a.add(CAT_CABLURI, "Copex metalic Ø16 (aparent)", rooms * perRoomLight)
                if (commercial) a.add(CAT_CABLURI, "Jgheab metalic perforat 100x60 cu capac", rooms * 8)
            } else {
                a.add(CAT_CABLURI, "Tub copex Ø20", rooms * perRoomPower)
                a.add(CAT_CABLURI, "Tub copex Ø16", rooms * perRoomLight)
            }
        }

        // ---- tablou
        if (!i.keepPanel) {
            val socketCircuits = rooms
            val lightCircuits = ceil(rooms / 2.0).toInt()
            val dedicated = i.kitchens * 2 + (if (i.bathrooms > 0) 1 else 0)   // cuptor+plită, boiler/mașină
            val circuits = socketCircuits + lightCircuits + dedicated
            val modules = circuits * 2 + (if (i.supply == Supply.TRI) 8 else 6)
            val carcasa = when {
                modules <= 13 -> "Tablou 1 rând (13 module)"
                modules <= 26 -> "Tablou 2 rânduri (26 module)"
                else -> "Tablou 3 rânduri (39 module)"
            }
            a.add(CAT_TABLOU, carcasa, 1)
            a.add(CAT_TABLOU, "MCB 1P+N 16A", socketCircuits)
            a.add(CAT_TABLOU, "MCB 1P+N 10A", lightCircuits)
            a.add(CAT_TABLOU, "MCB 1P+N 20A", i.kitchens)
            a.add(CAT_TABLOU, "MCB 1P+N 25A", i.kitchens + (if (i.bathrooms > 0) 1 else 0))
            when (i.supply) {
                Supply.MONO -> {
                    a.add(CAT_TABLOU, "Diferențial general", 1)
                    a.add(CAT_TABLOU, "Busbar 13 module (pieptene 1P+N)", ceil(circuits / 6.0).toInt())
                }
                Supply.TRI -> {
                    a.add(CAT_TABLOU, "Separator trifazic (4P)", 1)
                    a.add(CAT_TABLOU, "Diferențial general trifazic (4P)", 1)
                    a.add(CAT_TABLOU, "MCB 3P 32A", 1)
                    a.add(CAT_TABLOU, "Releu protecție tensiune (min/max)", 1)
                    a.add(CAT_TABLOU, "Busbar trifazic (pieptene 3P)", 1)
                }
            }
            a.add(CAT_TABLOU, "Descărcător supratensiune (SPD) Tip 2", 1)
            notes += "Tablou dimensionat pentru $circuits circuite (~$modules module)."
        } else {
            notes += "Tabloul existent se păstrează — nu s-au adăugat componente de tablou."
        }
        if (i.atrKw > 0) notes += "Putere aprobată (ATR): ${"%.1f".format(i.atrKw)} kW, ${i.supply.label.lowercase()}."

        val pdfBoxes: Boolean? = if (i.newInstall) true else null
        if (i.newInstall) notes += "Instalație nouă: dozele, tuburile și carcasa tabloului intră în PDF-ul pentru furnizor."

        val brandFor = if (i.brand.isBlank()) emptyMap() else when (apparatus) {
            ApparatusKind.MODULAR -> mapOf(CAT_MODULE to (i.brand to i.brandModel), CAT_DOZE_MODULARE to (i.brand to i.brandModel))
            ApparatusKind.CLASIC -> mapOf(CAT_APARATAJ_INCASTRAT to (i.brand to i.brandModel))
            ApparatusKind.APLICAT -> mapOf(CAT_APARATAJ_APLICAT to (i.brand to i.brandModel))
        }
        return WizardPlan(
            items = a.list(),
            pdfIncludeBoxes = pdfBoxes,
            extraLabor = emptyList(),
            notes = notes,
            brandFor = brandFor,
            cableMode = if (apparatus == ApparatusKind.APLICAT) "aparent" else "incastrat",
            tablouPhase = if (i.supply == Supply.TRI) "tri" else "mono"
        )
    }

    fun inverterName(supply: Supply, kw: Double): String {
        val sizes = if (supply == Supply.MONO) listOf(3, 5, 6, 8, 10) else listOf(6, 8, 10, 12, 15, 20, 30)
        val size = sizes.firstOrNull { it >= kw - 0.01 } ?: sizes.last()
        return "Invertor hibrid ${if (supply == Supply.MONO) "monofazic" else "trifazic"} $size kW"
    }

    fun estimatePv(i: PvInput): WizardPlan {
        val a = Acc()
        val notes = mutableListOf<String>()
        val panels = i.panels
        if (panels == 0) return WizardPlan(emptyList(), null, emptyList(), listOf("Introdu puterea în kW."))
        a.add(CAT_PV, "Panou fotovoltaic ${i.panelW} W", panels)
        a.add(CAT_PV, inverterName(i.supply, i.inverterKw), 1)
        val batteries = ceil(i.batteryKwh / 5.0).toInt()
        if (batteries > 0) a.add(CAT_PV, "Baterie LiFePO4 5 kWh", batteries)
        // structură
        val structItem = when (i.roof) {
            RoofKind.TIGLA -> "Cârlig țiglă (set/panou)"
            RoofKind.TABLA -> "Suport tablă (set/panou)"
            RoofKind.TERASA -> "Structură balast terasă (set/panou)"
        }
        a.add(CAT_PV, structItem, panels)
        a.add(CAT_PV, "Șină aluminiu 4,2 m", ceil(panels * 1.15).toInt())
        a.add(CAT_PV, "Clemă mijloc", panels * 2)
        a.add(CAT_PV, "Clemă capăt", i.strings * 4 + 4)
        // DC
        a.add(CAT_PV, "Cutie protecții DC (tablou DC)", 1)
        a.add(CAT_PV, "Siguranță DC 1000 V 16 A", i.strings * 2)
        a.add(CAT_PV, "Separator DC 1000 V", 1)
        a.add(CAT_PV, "Descărcător supratensiune DC 1000 V", 1)
        a.add(CAT_PV, "Conector MC4 (pereche)", i.strings * 2 + 2)
        a.add(CAT_CABLURI, "Cablu solar 6 mm²", (panels * 2.5 + 40).roundToInt())
        // AC
        a.add(CAT_PV, "Cutie protecții AC (tablou AC)", 1)
        when (i.supply) {
            Supply.MONO -> {
                a.add(CAT_PV, "MCB 1P+N 32 A invertor", 1)
                a.add(CAT_PV, "Diferențial tip A 40 A 30 mA (2P)", 1)
                a.add(CAT_CABLURI, "Cablu CYY-F 3x6", 20)
            }
            Supply.TRI -> {
                a.add(CAT_PV, "MCB 3P 32 A invertor", 1)
                a.add(CAT_PV, "Diferențial tip A 40 A 30 mA (4P)", 1)
                a.add(CAT_CABLURI, "Cablu CYY-F 5x6", 20)
            }
        }
        a.add(CAT_PV, "Descărcător supratensiune AC Tip 2", 1)
        a.add(CAT_PV, "Contor bidirecțional (smart meter)", 1)
        a.add(CAT_CABLURI, "Cablu împământare 16 mm² (galben-verde)", 15)
        a.add(CAT_PV, "Electrod împământare + piesă de separație", 1)
        if (batteries > 0) a.add(CAT_CABLURI, "Cablu baterie 25 mm²", batteries * 4)

        val labor = LaborLine(
            "Instalare sistem fotovoltaic ${"%.1f".format(i.kw)} kW " +
                "(${i.difficulty.eurPerKw.roundToInt()} €/kW × ${"%.2f".format(i.eurRate)} lei/€)",
            qty = 1, unitPrice = i.laborLei.roundTo2()
        )
        notes += "$panels panouri de ${i.panelW} W în ${i.strings} ${if (i.strings == 1) "string" else "string-uri"}."
        notes += "Manoperă: ${"%.1f".format(i.kw)} kW × ${i.difficulty.eurPerKw.roundToInt()} € × ${"%.2f".format(i.eurRate)} lei = ${"%.0f".format(i.laborLei)} lei."
        return WizardPlan(a.list(), null, listOf(labor), notes, cableMode = "aparent")
    }

    private fun Double.roundTo2(): Double = (this * 100.0).roundToInt() / 100.0
}

/** PDF-ul include dozele / carcasele dacă lucrarea o cere explicit, altfel după setare. */
fun effectiveIncludeBoxes(work: Work, setting: Boolean): Boolean = work.pdfIncludeBoxes ?: setting
