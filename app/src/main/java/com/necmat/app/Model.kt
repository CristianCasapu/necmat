package com.necmat.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Material(
    val id: Long,
    val name: String,
    val qty: Int = 0,
    val price: Double = 0.0
)

data class Category(
    val id: Long,
    val name: String,
    val materials: List<Material> = emptyList()
)

data class Work(
    val id: Long,
    val name: String,
    val date: Long,
    val categories: List<Category>,
    val client: String = "",
    val address: String = "",
    val phone: String = ""
) {
    val totalTypes get() = categories.sumOf { it.materials.size }
    val totalPieces get() = categories.sumOf { c -> c.materials.sumOf { it.qty } }
    val totalValue get() = categories.sumOf { c -> c.materials.sumOf { it.qty * it.price } }
    val hasPrices get() = categories.any { c -> c.materials.any { it.price > 0.0 } }
}

/** Detectează o doză modulară după nume (ex: "Doză 3 module"). */
private val MODULE_BOX_REGEX = Regex("(\\d+)\\s*module", RegexOption.IGNORE_CASE)
fun isModularBox(name: String): Boolean =
    name.contains("doz", ignoreCase = true) && MODULE_BOX_REGEX.containsMatchIn(name)

/**
 * Pregătește lucrarea pentru PDF: dacă includeBoxes = false, dozele modulare
 * sunt eliminate din listă (rămân doar pentru calculele de accesorii).
 */
fun Work.filterForPdf(includeBoxes: Boolean): Work {
    if (includeBoxes) return this
    return copy(
        categories = categories
            .map { c -> c.copy(materials = c.materials.filterNot { isModularBox(it.name) }) }
            .filter { it.materials.isNotEmpty() }
    )
}

/** Adaugă lucrarea în listă; o lucrare existentă cu același nume este înlocuită. */
fun upsertWork(works: List<Work>, w: Work): List<Work> =
    listOf(w) + works.filterNot { it.name.trim().equals(w.name.trim(), ignoreCase = true) }

/**
 * Migrare v3: adaugă materialele noi (module TV/rețea, aparataj îngropat)
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

    // categoria "Aparataj îngropat"
    val buriedItems = listOf(
        "Priză simplă îngropată", "Priză dublă îngropată",
        "Întrerupător simplu îngropat", "Întrerupător dublu îngropat",
        "Priză rețea (CAT5/6) îngropată", "Priză TV îngropată"
    )
    val burIdx = result.indexOfFirst {
        it.name.trim().equals("aparataj îngropat", ignoreCase = true)
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
        result + Category(newId(), "Aparataj îngropat", buriedItems.map { Material(newId(), it) })
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

    return copy(
        categories = categories +
            Category(-999L, "Accesorii doze modulare (calcul automat)", acc)
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
            }
        )
    }

    private fun workToJson(w: Work): JSONObject {
        val cats = JSONArray()
        w.categories.forEach { cats.put(catToJson(it)) }
        return JSONObject().put("id", w.id).put("name", w.name)
            .put("date", w.date).put("categories", cats)
            .put("client", w.client).put("address", w.address).put("phone", w.phone)
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
            phone = w.optString("phone", "")
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

    // ---- backup / restaurare ----

    fun backupJson(categories: List<Category>, works: List<Work>): String {
        val cats = JSONArray()
        categories.forEach { cats.put(catToJson(it)) }
        val ws = JSONArray()
        works.forEach { ws.put(workToJson(it)) }
        return JSONObject()
            .put("app", "NecMat").put("version", 1)
            .put("categories", cats).put("works", ws)
            .toString(2)
    }

    /** Returnează (categorii, lucrări) sau null dacă fișierul nu e un backup valid. */
    fun parseBackup(text: String): Pair<List<Category>, List<Work>>? = try {
        val o = JSONObject(text)
        if (o.optString("app") != "NecMat") null
        else {
            val cats = o.getJSONArray("categories")
            val ws = o.getJSONArray("works")
            Pair(
                (0 until cats.length()).map { catFromJson(cats.getJSONObject(it)) },
                (0 until ws.length()).map { workFromJson(ws.getJSONObject(it)) }
            )
        }
    } catch (e: Exception) {
        null
    }

    private var nextId = 1L
    private fun nid() = nextId++
    private fun cat(name: String, vararg items: String) =
        Category(nid(), name, items.map { Material(nid(), it) })

    fun defaultCatalog(): List<Category> {
        nextId = 1L
        return listOf(
            cat(
                "Doze modulare",
                "Doză 2 module", "Doză 3 module", "Doză 4 module",
                "Doză 5 module", "Doză 7 module"
            ),
            cat(
                "Doze aparat îngropate",
                "Doză aparat pentru priză", "Doză aparat pentru întrerupător"
            ),
            cat(
                "Doze legături",
                "Doză simplă", "Doză dublă", "Doză îngropată"
            ),
            cat(
                "Aparataj aplicat",
                "Priză aplicată", "Întrerupător aplicat"
            ),
            cat(
                "Module",
                "Întrerupător simplu", "Cap scară", "Cap cruce",
                "Priză simplă", "Priză dublă", "Modul TV", "Modul rețea (CAT5/6)"
            ),
            cat(
                "Aparataj îngropat",
                "Priză simplă îngropată", "Priză dublă îngropată",
                "Întrerupător simplu îngropat", "Întrerupător dublu îngropat",
                "Priză rețea (CAT5/6) îngropată", "Priză TV îngropată"
            ),
            cat(
                "Tablou electric",
                "Separator cu fuzibili", "Pastilă fuzibil 25A", "Pastilă fuzibil 32A",
                "Pastilă fuzibil 40A", "Diferențial general",
                "MCB 1P+N 6A", "MCB 1P+N 10A", "MCB 1P+N 16A",
                "MCB 1P+N 20A", "MCB 1P+N 25A", "MCB 1P+N 32A"
            ),
            cat(
                "Cabluri și tuburi (m)",
                "Cablu CYY-F 3x1.5", "Cablu CYY-F 3x2.5", "Tub copex Ø16", "Tub copex Ø20"
            )
        )
    }
}
