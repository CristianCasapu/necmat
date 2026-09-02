package com.necmat.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Material(
    val id: Long,
    val name: String,
    val qty: Int = 0,
    val price: Double = 0.0
)

data class Category(
    val id: Long,
    val name: String,
    val materials: List<Material> = emptyList(),
    val brand: String = "",
    val model: String = "",
    val phase: String = ""   // "", "mono" sau "tri" (pentru tablou)
) {
    /** "Gewiss Chorus" sau "" dacă nu e setată marca. */
    val brandLabel: String
        get() {
            val b = listOf(brand, model).filter { it.isNotBlank() }.joinToString(" ")
            val p = phaseLabel
            return listOf(b, p).filter { it.isNotBlank() }.joinToString(" · ")
        }

    val phaseLabel get() = when (phase) {
        "mono" -> "Monofazic"
        "tri" -> "Trifazic"
        else -> ""
    }
}

/** O marcă + serie/model, cu grupurile de materiale cărora li se aplică. */
data class BrandEntry(
    val id: Long,
    val brand: String,
    val series: String = "",
    val groups: List<String> = emptyList()
) {
    val label get() = listOf(brand, series).filter { it.isNotBlank() }.joinToString(" ")
}

/** Grupurile de aplicabilitate pentru mărci. */
object BrandGroups {
    const val MODULAR = "modular"
    const val APARATAJ = "aparataj"
    const val TABLOU = "tablou"
    const val INSTALATIE = "instalatie"
    const val SMART = "smart"

    val all = listOf(MODULAR, APARATAJ, TABLOU, INSTALATIE, SMART)

    fun label(group: String): String = when (group) {
        MODULAR -> "Aparataj modular"
        APARATAJ -> "Aparataj clasic"
        TABLOU -> "Tablou electric"
        INSTALATIE -> "Doze / tuburi / instalație"
        SMART -> "Aparataj smart"
        else -> group
    }

    /** Deduce grupul unei categorii după nume. */
    fun infer(categoryName: String): String {
        val n = categoryName.lowercase()
        return when {
            n.contains("tablou") -> TABLOU
            n.contains("modul") -> MODULAR
            n.contains("aparat") -> APARATAJ
            n.contains("doz") || n.contains("cablu") || n.contains("tub") -> INSTALATIE
            else -> APARATAJ
        }
    }
}

data class Work(
    val id: Long,
    val name: String,
    val date: Long,
    val categories: List<Category>,
    val client: String = "",
    val address: String = "",
    val phone: String = "",
    val isTemplate: Boolean = false
) {
    val totalTypes get() = categories.sumOf { it.materials.size }
    val totalPieces get() = categories.sumOf { c -> c.materials.sumOf { it.qty } }
    val totalValue get() = categories.sumOf { c -> c.materials.sumOf { it.qty * it.price } }
    val hasPrices get() = categories.any { c -> c.materials.any { it.price > 0.0 } }
}

/** Migrare v6: „îngropat" devine „încastrat" în toate denumirile. */
private fun renameTerm(text: String): String = text
    .replace("îngropat", "încastrat").replace("Îngropat", "Încastrat")

fun renameTermCategories(cats: List<Category>): List<Category> = cats.map { c ->
    c.copy(
        name = renameTerm(c.name),
        materials = c.materials.map { it.copy(name = renameTerm(it.name)) }
    )
}

fun renameTermWorks(works: List<Work>): List<Work> = works.map { w ->
    w.copy(categories = renameTermCategories(w.categories))
}

/** Rezumatul calculului de accesorii pentru dozele modulare. */
data class AccessorySummary(
    val boxes: Int,          // număr de doze modulare
    val slots: Int,          // sloturi totale
    val usedModules: Int,    // module folosite (priza dublă = 2)
    val obturatoare: Int     // sloturi rămase libere (0 dacă depășite)
) {
    val overflow get() = usedModules > slots
}

/** null dacă nu există doze modulare selectate. */
fun accessorySummary(categories: List<Category>): AccessorySummary? {
    var boxes = 0
    var slots = 0
    categories.forEach { c ->
        c.materials.forEach { m ->
            if (m.qty > 0 && isModularBox(m.name)) {
                val n = Regex("(\\d+)\\s*module", RegexOption.IGNORE_CASE)
                    .find(m.name)?.groupValues?.get(1)?.toIntOrNull()
                if (n != null && n > 0) {
                    boxes += m.qty
                    slots += n * m.qty
                }
            }
        }
    }
    if (boxes == 0) return null
    var used = 0
    categories.forEach { c ->
        if (c.name.trim().equals("module", ignoreCase = true)) {
            c.materials.forEach { m ->
                val width = if (m.name.contains("dubl", ignoreCase = true)) 2 else 1
                used += width * m.qty
            }
        }
    }
    return AccessorySummary(boxes, slots, used, (slots - used).coerceAtLeast(0))
}

/** Componentă trifazică după nume (3P, 4P, "trifazic"). */
fun isTriphasicItem(name: String): Boolean {
    val n = name.lowercase()
    return n.contains("trifazic") || n.contains("3p") || n.contains("4p")
}

/** Numele fișierului PDF: titlu + client + adresă + data creării lucrării. */
fun pdfFileName(work: Work): String {
    val df = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val parts = listOf(work.name, work.client, work.address, df.format(Date(work.date)))
        .filter { it.isNotBlank() }
    val raw = parts.joinToString(" - ")
        .replace(Regex("[^\\p{L}\\p{N} _.,()+-]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    return (raw.take(120).ifEmpty { "necesar" }) + ".pdf"
}

/** Detectează o doză modulară după nume (ex: "Doză 3 module"). */
private val MODULE_BOX_REGEX = Regex("(\\d+)\\s*module", RegexOption.IGNORE_CASE)
fun isModularBox(name: String): Boolean =
    name.contains("doz", ignoreCase = true) && MODULE_BOX_REGEX.containsMatchIn(name)

/** Orice doză (modulară, aparat, legături) — montată deja la instalare. */
fun isDozaItem(name: String): Boolean = name.contains("doz", ignoreCase = true)

/** Carcasa tabloului (ex: "Tablou 2 rânduri (26 module)") — montată deja. */
fun isTablouCarcasa(name: String): Boolean =
    name.trim().lowercase().startsWith("tablou ")

/**
 * Pregătește lucrarea pentru PDF. Cu includeBoxes = false (implicit), dozele
 * de orice fel și carcasele de tablou NU apar în PDF: sunt deja montate și
 * servesc doar la calcule. Componentele tabloului (MCB, diferențiale,
 * busbar-uri etc.), clemele și accesoriile calculate rămân în PDF.
 */
fun Work.filterForPdf(includeBoxes: Boolean): Work {
    if (includeBoxes) return this
    return copy(
        categories = categories
            .map { c ->
                c.copy(materials = c.materials.filterNot {
                    isDozaItem(it.name) || isTablouCarcasa(it.name)
                })
            }
            .filter { it.materials.isNotEmpty() }
    )
}

/** Adaugă lucrarea în listă; o lucrare existentă cu același nume este înlocuită. */
fun upsertWork(works: List<Work>, w: Work): List<Work> =
    listOf(w) + works.filterNot { it.name.trim().equals(w.name.trim(), ignoreCase = true) }

/**
 * Înlocuiește complet lucrarea cu id-ul dat (nume + toate detaliile).
 * Dacă overwriteId nu există în listă, lucrarea e adăugată normal (după nume).
 */
fun replaceWork(works: List<Work>, w: Work, overwriteId: Long?): List<Work> {
    if (overwriteId == null || works.none { it.id == overwriteId }) return upsertWork(works, w)
    return listOf(w.copy(id = overwriteId)) +
        works.filterNot { it.id == overwriteId || it.name.trim().equals(w.name.trim(), ignoreCase = true) }
}

/**
 * Migrare v3: adaugă materialele noi (module TV/rețea, aparataj încastrat)
 * la cataloagele existente, fără a dubla ce există deja.
 */
fun migrateV3(cats: List<Category>, newId: () -> Long): List<Category> {
    var result = cats

    // module noi în categoria "Module"
    val newModules = listOf("Modul TV", "Modul rețea (CAT5/6)")
    val modIdx = result.indexOfFirst { it.name.trim().equals("module", ignoreCase = true) }
    if (modIdx >= 0) {
        var mod = result[modIdx]
        newModules.forEach { name ->
            if (mod.materials.none { it.name.equals(name, ignoreCase = true) }) {
                mod = mod.copy(materials = mod.materials + Material(newId(), name))
            }
        }
        result = result.mapIndexed { i, c -> if (i == modIdx) mod else c }
    }

    // categoria "Aparataj încastrat"
    val buriedItems = listOf(
        "Priză simplă încastrată", "Priză dublă încastrată",
        "Întrerupător simplu încastrat", "Întrerupător dublu încastrat",
        "Priză rețea (CAT5/6) încastrată", "Priză TV încastrată"
    )
    val burIdx = result.indexOfFirst {
        it.name.trim().equals("aparataj încastrat", ignoreCase = true)
    }
    result = if (burIdx >= 0) {
        var bur = result[burIdx]
        buriedItems.forEach { name ->
            if (bur.materials.none { it.name.equals(name, ignoreCase = true) }) {
                bur = bur.copy(materials = bur.materials + Material(newId(), name))
            }
        }
        result.mapIndexed { i, c -> if (i == burIdx) bur else c }
    } else {
        result + Category(newId(), "Aparataj încastrat", buriedItems.map { Material(newId(), it) })
    }
    return result
}

/**
 * Adaugă automat accesoriile pentru dozele modulare:
 *  - câte o ramă suport + o ramă ornament (mască) pentru fiecare doză modulară, pe mărimea ei;
 *  - obturatoare = sloturile totale din doze − modulele folosite (priza dublă ocupă 2 module).
 */
fun Work.withAutoAccessories(): Work {
    // doze modulare: N module -> număr de doze
    val boxes = sortedMapOf<Int, Int>()
    categories.forEach { c ->
        c.materials.forEach { m ->
            if (m.qty > 0 && isModularBox(m.name)) {
                MODULE_BOX_REGEX.find(m.name)?.let { match ->
                    val n = match.groupValues[1].toIntOrNull()
                    if (n != null && n > 0) boxes[n] = (boxes[n] ?: 0) + m.qty
                }
            }
        }
    }
    if (boxes.isEmpty()) return this

    // module folosite (doar categoria "Module"); "dublă" ocupă 2 sloturi
    var usedModules = 0
    categories.forEach { c ->
        if (c.name.trim().equals("module", ignoreCase = true)) {
            c.materials.forEach { m ->
                val width = if (m.name.contains("dubl", ignoreCase = true)) 2 else 1
                usedModules += width * m.qty
            }
        }
    }

    val acc = mutableListOf<Material>()
    var accId = -1000L
    boxes.forEach { (n, cnt) ->
        acc += Material(accId--, "Ramă suport $n module", cnt)
        acc += Material(accId--, "Ramă ornament (mască) $n module", cnt)
    }
    val slots = boxes.entries.sumOf { it.key * it.value }
    val obturatoare = slots - usedModules
    if (obturatoare > 0) acc += Material(accId--, "Obturator (modul fals)", obturatoare)

    // accesoriile moștenesc marca dozelor modulare (același sistem)
    val boxCat = categories.firstOrNull { c ->
        c.materials.any { it.qty > 0 && isModularBox(it.name) }
    }

    return copy(
        categories = categories +
            Category(
                -999L, "Accesorii doze modulare (calcul automat)", acc,
                brand = boxCat?.brand ?: "", model = boxCat?.model ?: ""
            )
    )
}

object Repo {
    private const val FILE_NAME = "necmat.json"
    private const val WORKS_FILE = "necmat_works.json"

    private fun catToJson(c: Category): JSONObject {
        val mats = JSONArray()
        c.materials.forEach { m ->
            mats.put(
                JSONObject().put("id", m.id).put("name", m.name)
                    .put("qty", m.qty).put("price", m.price)
            )
        }
        return JSONObject().put("id", c.id).put("name", c.name).put("materials", mats)
            .put("brand", c.brand).put("model", c.model).put("phase", c.phase)
    }

    private fun catFromJson(c: JSONObject): Category {
        val mats = c.getJSONArray("materials")
        return Category(
            id = c.getLong("id"),
            name = c.getString("name"),
            materials = (0 until mats.length()).map { j ->
                val m = mats.getJSONObject(j)
                Material(
                    m.getLong("id"), m.getString("name"),
                    m.getInt("qty"), m.optDouble("price", 0.0)
                )
            },
            brand = c.optString("brand", ""),
            model = c.optString("model", ""),
            phase = c.optString("phase", "")
        )
    }

    private fun workToJson(w: Work): JSONObject {
        val cats = JSONArray()
        w.categories.forEach { cats.put(catToJson(it)) }
        return JSONObject().put("id", w.id).put("name", w.name)
            .put("date", w.date).put("categories", cats)
            .put("client", w.client).put("address", w.address).put("phone", w.phone)
            .put("template", w.isTemplate)
    }

    private fun workFromJson(w: JSONObject): Work {
        val cats = w.getJSONArray("categories")
        return Work(
            id = w.getLong("id"),
            name = w.getString("name"),
            date = w.getLong("date"),
            categories = (0 until cats.length()).map { j -> catFromJson(cats.getJSONObject(j)) },
            client = w.optString("client", ""),
            address = w.optString("address", ""),
            phone = w.optString("phone", ""),
            isTemplate = w.optBoolean("template", false)
        )
    }

    fun load(context: Context): List<Category> {
        val f = File(context.filesDir, FILE_NAME)
        if (!f.exists()) return defaultCatalog()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i -> catFromJson(arr.getJSONObject(i)) }
        } catch (e: Exception) {
            defaultCatalog()
        }
    }

    fun save(context: Context, categories: List<Category>) {
        val arr = JSONArray()
        categories.forEach { arr.put(catToJson(it)) }
        File(context.filesDir, FILE_NAME).writeText(arr.toString())
    }

    fun loadWorks(context: Context): List<Work> {
        val f = File(context.filesDir, WORKS_FILE)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i -> workFromJson(arr.getJSONObject(i)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveWorks(context: Context, works: List<Work>) {
        val arr = JSONArray()
        works.forEach { arr.put(workToJson(it)) }
        File(context.filesDir, WORKS_FILE).writeText(arr.toString())
    }

    // ---- mărci și modele ----

    private const val BRANDS_FILE = "necmat_brands.json"

    private fun brandToJson(b: BrandEntry): JSONObject {
        val groups = JSONArray()
        b.groups.forEach { groups.put(it) }
        return JSONObject().put("id", b.id).put("brand", b.brand)
            .put("series", b.series).put("groups", groups)
    }

    private fun brandFromJson(o: JSONObject): BrandEntry {
        val groups = o.optJSONArray("groups") ?: JSONArray()
        return BrandEntry(
            id = o.getLong("id"),
            brand = o.getString("brand"),
            series = o.optString("series", ""),
            groups = (0 until groups.length()).map { groups.getString(it) }
        )
    }

    fun loadBrands(context: Context): List<BrandEntry> {
        val f = File(context.filesDir, BRANDS_FILE)
        if (!f.exists()) return seedBrands()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { brandFromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            seedBrands()
        }
    }

    fun saveBrands(context: Context, brands: List<BrandEntry>) {
        val arr = JSONArray()
        brands.forEach { arr.put(brandToJson(it)) }
        File(context.filesDir, BRANDS_FILE).writeText(arr.toString())
    }

    /** Mărci uzuale pe piața din România, pe grupuri de materiale. */
    fun seedBrands(): List<BrandEntry> {
        fun be(id: Long, brand: String, series: String, vararg g: String) =
            BrandEntry(id, brand, series, g.toList())
        val m = BrandGroups.MODULAR
        val a = BrandGroups.APARATAJ
        val t = BrandGroups.TABLOU
        val i = BrandGroups.INSTALATIE
        return listOf(
            // aparataj modular (doze modulare, module, rame)
            be(1, "Gewiss", "Chorus", m, a),
            be(2, "Gewiss", "System", m),
            be(3, "bTicino", "Living Light", m),
            be(4, "bTicino", "Matix", m),
            be(5, "Vimar", "Plana", m),
            be(6, "Vimar", "Eikon", m),
            be(7, "Schneider Electric", "Unica System+", m),
            // aparataj clasic (încastrat / aplicat)
            be(10, "Schneider Electric", "Asfora", a),
            be(11, "Schneider Electric", "Sedna Design", a),
            be(12, "Legrand", "Valena Life", a),
            be(13, "Legrand", "Niloe", a),
            be(14, "Viko", "Karre", a),
            be(15, "Viko", "Meridian", a),
            be(16, "Panasonic", "Arkedia Slim", a),
            be(17, "ABB", "Basic55", a),
            be(18, "Hager", "Lumina", a),
            be(19, "Makel", "Manolya", a),
            be(20, "Mono Electric", "Despina", a),
            be(21, "Schneider Electric", "Mureva Styl", a),
            be(22, "Legrand", "Plexo", a),
            // tablou electric
            be(30, "Schneider Electric", "Easy9", t),
            be(31, "Schneider Electric", "Acti9", t),
            be(32, "ABB", "SH200", t),
            be(33, "Eaton", "PL6", t),
            be(34, "Hager", "MCN", t),
            be(35, "Legrand", "RX3", t),
            be(36, "Legrand", "TX3", t),
            be(37, "ETI", "ETIMAT 10", t),
            be(38, "Noark", "Ex9BN", t),
            be(39, "Siemens", "5SL", t),
            be(40, "Comtec", "", t),
            be(41, "Elmark", "", t),
            // doze / tuburi / instalație
            be(50, "Kopos", "", i),
            be(51, "Gewiss", "GW", i),
            be(52, "Elettrocanali", "", i),
            be(53, "Courbi", "", i),
            be(54, "Schrack", "", t, i),
            be(55, "Wago", "", i),
            // aparataj și tablou smart / automatizări
            be(60, "Shelly", "", BrandGroups.SMART, t),
            be(61, "Sonoff", "", BrandGroups.SMART, t),
            be(62, "Livolo", "", BrandGroups.SMART, a),
            be(63, "Legrand", "Valena Life with Netatmo", BrandGroups.SMART, a),
            be(64, "bTicino", "Living Now with Netatmo", BrandGroups.SMART, m),
            be(65, "Schneider Electric", "Wiser", BrandGroups.SMART),
            be(66, "Tuya", "", BrandGroups.SMART),
            be(67, "F&F", "", t),
            be(68, "Finder", "", t),
            be(69, "Chint", "", t)
        )
    }

    /** Adaugă în lista existentă intrările din seed care lipsesc (după marcă + serie). */
    fun mergeBrands(existing: List<BrandEntry>, additions: List<BrandEntry>): List<BrandEntry> {
        val known = existing.map { it.brand.lowercase() to it.series.lowercase() }.toSet()
        val maxId = (existing.maxOfOrNull { it.id } ?: 0L)
        var nextId = maxId + 1
        val missing = additions
            .filter { (it.brand.lowercase() to it.series.lowercase()) !in known }
            .map { it.copy(id = nextId++) }
        return existing + missing
    }

    // ---- backup / restaurare ----

    fun backupJson(
        categories: List<Category>,
        works: List<Work>,
        brands: List<BrandEntry> = emptyList()
    ): String {
        val cats = JSONArray()
        categories.forEach { cats.put(catToJson(it)) }
        val ws = JSONArray()
        works.forEach { ws.put(workToJson(it)) }
        val bs = JSONArray()
        brands.forEach { bs.put(brandToJson(it)) }
        return JSONObject()
            .put("app", "NecMat").put("version", 2)
            .put("categories", cats).put("works", ws).put("brands", bs)
            .toString(2)
    }

    data class Backup(
        val categories: List<Category>,
        val works: List<Work>,
        val brands: List<BrandEntry>
    )

    /** Returnează datele din backup sau null dacă fișierul nu e valid. */
    fun parseBackup(text: String): Backup? = try {
        val o = JSONObject(text)
        if (o.optString("app") != "NecMat") null
        else {
            val cats = o.getJSONArray("categories")
            val ws = o.getJSONArray("works")
            val bs = o.optJSONArray("brands") ?: JSONArray()
            Backup(
                categories = (0 until cats.length()).map { catFromJson(cats.getJSONObject(it)) },
                works = (0 until ws.length()).map { workFromJson(ws.getJSONObject(it)) },
                brands = (0 until bs.length()).map { brandFromJson(bs.getJSONObject(it)) }
            )
        }
    } catch (e: Exception) {
        null
    }

    private var nextId = 1L
    private fun nid() = nextId++
    private fun cat(name: String, vararg items: String) =
        Category(nid(), name, items.map { Material(nid(), it) })

    /** Ordinea canonică de afișare a categoriilor. */
    val canonicalOrder = listOf(
        "Doze aparat încastrate",
        "Aparataj încastrat",
        "Doze modulare",
        "Module",
        "Aparataj aplicat",
        "Tablou electric",
        "Doze legături",
        "Cabluri și tuburi (m)"
    )

    private val tablouExtras = listOf(
        "Separator trifazic (4P)", "Diferențial general trifazic (4P)",
        "Releu monitorizare fază", "ATS (comutare automată rețea-generator)",
        "Busbar 13 module (pieptene 1P+N)", "Busbar trifazic (pieptene 3P)",
        "Bloc distribuție (clemă repartiție)"
    )

    /** Componente de tablou adăugate în v1.7 (rezidențial, piața RO). */
    private val tablouExtras2 = listOf(
        "Tablou 1 rând (13 module)", "Tablou 2 rânduri (26 module)",
        "Tablou 3 rânduri (39 module)",
        "MCB 3P 16A", "MCB 3P 20A", "MCB 3P 25A", "MCB 3P 32A",
        "Diferențial cu protecție (RCBO) 1P+N 10A",
        "Diferențial cu protecție (RCBO) 1P+N 16A",
        "Diferențial cu protecție (RCBO) 1P+N 20A",
        "Diferențial cu protecție (RCBO) 1P+N 25A",
        "Descărcător supratensiune (SPD) Tip 1+2",
        "Descărcător supratensiune (SPD) Tip 2",
        "Releu protecție tensiune (min/max)",
        "Contactor modular 25A",
        "Sonerie modulară", "Transformator sonerie",
        "Programator orar modular", "Lampă semnalizare modulară",
        "Priză modulară pe șină DIN",
        "Busbar 18 module (pieptene 1P+N)", "Busbar 24 module (pieptene 1P+N)",
        "Busbar trifazic 18 module (pieptene 3P)"
    )

    private val dozeLegaturiExtras = listOf(
        "Clemă distribuție simplă (4 intrări)", "Clemă distribuție dublă (8 intrări)"
    )

    fun defaultCatalog(): List<Category> {
        nextId = 1L
        return listOf(
            cat(
                "Doze aparat încastrate",
                "Doză aparat pentru priză", "Doză aparat pentru întrerupător"
            ),
            cat(
                "Aparataj încastrat",
                "Priză simplă încastrată", "Priză dublă încastrată",
                "Întrerupător simplu încastrat", "Întrerupător dublu încastrat",
                "Priză rețea (CAT5/6) încastrată", "Priză TV încastrată"
            ),
            cat(
                "Doze modulare",
                "Doză 2 module", "Doză 3 module", "Doză 4 module",
                "Doză 5 module", "Doză 7 module"
            ),
            cat(
                "Module",
                "Întrerupător simplu", "Cap scară", "Cap cruce",
                "Priză simplă", "Priză dublă", "Modul TV", "Modul rețea (CAT5/6)"
            ),
            cat(
                "Aparataj aplicat",
                "Priză aplicată", "Întrerupător aplicat"
            ),
            cat(
                "Tablou electric",
                *(listOf(
                    "Separator cu fuzibili", "Pastilă fuzibil 25A", "Pastilă fuzibil 32A",
                    "Pastilă fuzibil 40A", "Diferențial general",
                    "MCB 1P+N 6A", "MCB 1P+N 10A", "MCB 1P+N 16A",
                    "MCB 1P+N 20A", "MCB 1P+N 25A", "MCB 1P+N 32A"
                ) + tablouExtras + tablouExtras2).toTypedArray()
            ),
            cat(
                "Doze legături",
                *(listOf("Doză simplă", "Doză dublă", "Doză încastrată") +
                    dozeLegaturiExtras).toTypedArray()
            ),
            cat(
                "Cabluri și tuburi (m)",
                "Cablu CYY-F 3x1.5", "Cablu CYY-F 3x2.5", "Tub copex Ø16", "Tub copex Ø20"
            )
        )
    }

    /** Ordonează categoriile după ordinea canonică; cele necunoscute rămân la coadă. */
    fun sortCanonical(cats: List<Category>): List<Category> {
        val rank = canonicalOrder.mapIndexed { i, n -> n.lowercase() to i }.toMap()
        return cats.sortedBy { rank[it.name.trim().lowercase()] ?: Int.MAX_VALUE }
    }

    /** Migrare v4: materiale noi pentru tablou și doze legături + ordinea canonică. */
    fun migrateV4(cats: List<Category>, newId: () -> Long): List<Category> {
        fun addMissing(c: Category, items: List<String>): Category {
            var out = c
            items.forEach { name ->
                if (out.materials.none { it.name.equals(name, ignoreCase = true) }) {
                    out = out.copy(materials = out.materials + Material(newId(), name))
                }
            }
            return out
        }
        val augmented = cats.map { c ->
            when (c.name.trim().lowercase()) {
                "tablou electric" -> addMissing(c, tablouExtras)
                "doze legături" -> addMissing(c, dozeLegaturiExtras)
                else -> c
            }
        }
        return sortCanonical(augmented)
    }

    /** Migrare v5: componentele de tablou din v1.7. */
    fun migrateV5(cats: List<Category>, newId: () -> Long): List<Category> =
        cats.map { c ->
            if (!c.name.trim().equals("tablou electric", ignoreCase = true)) c
            else {
                var out = c
                tablouExtras2.forEach { name ->
                    if (out.materials.none { it.name.equals(name, ignoreCase = true) }) {
                        out = out.copy(materials = out.materials + Material(newId(), name))
                    }
                }
                out
            }
        }
}
