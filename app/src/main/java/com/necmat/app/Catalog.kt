package com.necmat.app

/**
 * Chei stabile (id-uri) pentru categoriile standard și grupurile de afișare (v1.36).
 *
 * Numele unei categorii se poate schimba oricând; cheia nu. Toate migrările,
 * regulile de calcul și gruparea lucrează pe chei, deci o categorie redenumită
 * primește în continuare materialele noi și intră în grupul potrivit.
 */
object CategoryKeys {
    const val DOZE_APARAT = "doze_aparat"
    const val APARATAJ_INCASTRAT = "aparataj_incastrat"
    const val DOZE_MODULARE = "doze_modulare"
    const val MODULE = "module"
    const val ACCESORII = "accesorii_modulare"
    const val APARATAJ_APLICAT = "aparataj_aplicat"
    const val TABLOU = "tablou"
    const val DOZE_LEGATURI = "doze_legaturi"
    const val CABLURI = "cabluri"
    const val ILUMINAT = "iluminat"
    const val PV = "pv"

    /** Numele implicit (canonic) al fiecărei categorii standard, în ordinea canonică. */
    val canonicalName: Map<String, String> = linkedMapOf(
        DOZE_APARAT to "Doze aparat încastrate",
        APARATAJ_INCASTRAT to "Aparataj încastrat",
        DOZE_MODULARE to "Doze modulare",
        MODULE to "Module",
        ACCESORII to "Accesorii doze modulare (calcul automat)",
        APARATAJ_APLICAT to "Aparataj aplicat",
        TABLOU to "Tablou electric",
        DOZE_LEGATURI to "Doze legături",
        CABLURI to "Cabluri și tuburi (m)",
        ILUMINAT to "Corpuri de iluminat (montaj)",
        PV to "Sistem fotovoltaic"
    )

    private val ordered = canonicalName.keys.toList()

    /** Poziția canonică a cheii; cheile necunoscute (goale) trec la coadă. */
    fun rank(key: String): Int = ordered.indexOf(key).let { if (it < 0) Int.MAX_VALUE else it }

    fun nameOf(key: String): String = canonicalName[key] ?: key

    /** Nume istorice (dinainte de redenumirile din migrări) → cheie. */
    private val historical = mapOf(
        "doze aparat ingropate" to DOZE_APARAT,
        "aparataj ingropat" to APARATAJ_INCASTRAT,
        "cabluri si tuburi" to CABLURI,
        "doze de legaturi" to DOZE_LEGATURI
    )

    /** Cheia după numele canonic exact (sau istoric); "" dacă nu e o categorie standard. */
    fun exact(name: String): String {
        val n = normalizeName(name)
        historical[n]?.let { return it }
        return canonicalName.entries.firstOrNull { normalizeName(it.value) == n }?.key ?: ""
    }

    /**
     * Deducere permisivă după cuvinte-cheie — doar pentru grupare, UM și manoperă la
     * categoriile proprii ale utilizatorului. NU se folosește la migrări (ar adăuga
     * materiale standard într-o categorie proprie).
     */
    fun loose(name: String): String {
        val n = normalizeName(name)
        return when {
            n.contains("fotovoltaic") || n.contains("panou") || n.contains("invertor") -> PV
            n.contains("(montaj)") || n.contains("iluminat") -> ILUMINAT
            n.contains("tablou") -> TABLOU
            n.contains("cablu") || n.contains("tub") || n.contains("jgheab") -> CABLURI
            n.contains("legatur") -> DOZE_LEGATURI
            n.contains("accesori") && n.contains("modul") -> ACCESORII
            n.contains("doz") && n.contains("modul") -> DOZE_MODULARE
            n.contains("doz") && n.contains("aparat") -> DOZE_APARAT
            n.contains("modul") -> MODULE
            n.contains("aplicat") || n.contains("aparent") -> APARATAJ_APLICAT
            n.contains("incastrat") || n.contains("aparataj") -> APARATAJ_INCASTRAT
            else -> ""
        }
    }
}

/** Cheia strictă: setată sau dedusă din numele canonic; "" la categoriile proprii. */
val Category.catalogKey: String get() = key.ifBlank { CategoryKeys.exact(name) }

/** Felul categoriei pentru reguli și grupare: cheia strictă sau deducerea permisivă. */
val Category.kind: String get() = catalogKey.ifBlank { CategoryKeys.loose(name) }

/** Unitatea de măsură a categoriei: metri la cabluri / tuburi, altfel bucăți. */
val Category.unit: String
    get() = if (kind == CategoryKeys.CABLURI || name.contains("(m)")) "m" else "buc"

/** Numele de catalog al materialului (cel implicit la creare), folosit în toate regulile. */
val Material.catalogName: String get() = key.ifBlank { name }

/** Cheia de potrivire a unui material (fără majuscule / diacritice), stabilă la redenumire. */
fun Material.matchKey(): String = normalizeName(catalogName)

/** Grupurile de afișare, în ordinea din Materiale / Necesar / PDF. */
enum class CategoryGroup(val label: String, val keys: List<String>, val requiresBrand: Boolean) {
    INCASTRAT("Aparataj clasic încastrat", listOf(CategoryKeys.DOZE_APARAT, CategoryKeys.APARATAJ_INCASTRAT), true),
    MODULAR("Aparataj modular", listOf(CategoryKeys.DOZE_MODULARE, CategoryKeys.MODULE, CategoryKeys.ACCESORII), true),
    APLICAT("Aparataj aplicat", listOf(CategoryKeys.APARATAJ_APLICAT), true),
    TABLOU("Tablou electric", listOf(CategoryKeys.TABLOU), true),
    LEGATURI("Doze de legături", listOf(CategoryKeys.DOZE_LEGATURI), false),
    CABLURI("Cabluri, tuburi și jgheaburi", listOf(CategoryKeys.CABLURI), false),
    ILUMINAT("Corpuri de iluminat", listOf(CategoryKeys.ILUMINAT), false),
    PV("Sistem fotovoltaic", listOf(CategoryKeys.PV), false),
    ALTELE("Alte materiale", emptyList(), false);

    companion object {
        fun of(kind: String): CategoryGroup =
            entries.firstOrNull { kind.isNotBlank() && kind in it.keys } ?: ALTELE
    }
}

val Category.group: CategoryGroup get() = CategoryGroup.of(kind)

/** Marca și modelul sunt obligatorii la aparataj (încastrat / modular / aplicat) și tablou. */
val Category.brandRequired: Boolean
    get() = group.requiresBrand && kind != CategoryKeys.ACCESORII

val Category.brandMissing: Boolean
    get() = brandRequired && (brand.isBlank() || model.isBlank())

/** Categoriile bifate (cu cantități) cărora le lipsește marca sau modelul obligatoriu. */
fun missingBrandCategories(cats: List<Category>): List<Category> =
    cats.filter { c -> c.brandMissing && c.materials.any { it.qty > 0 } }

/**
 * Ordinea de afișare: grup → ordinea canonică în grup → ordinea utilizatorului
 * (categoriile proprii își păstrează ordinea relativă). Stabilă și idempotentă.
 */
fun sortGrouped(cats: List<Category>): List<Category> =
    cats.withIndex()
        .sortedWith(
            compareBy({ it.value.group.ordinal }, { CategoryKeys.rank(it.value.kind) }, { it.index })
        )
        .map { it.value }

/** Un grup afișat: antetul apare doar când grupul are cel puțin două categorii. */
data class CategorySection(val group: CategoryGroup, val categories: List<Category>) {
    val showHeader: Boolean get() = categories.size >= 2
}

fun groupSections(cats: List<Category>): List<CategorySection> =
    sortGrouped(cats).groupBy { it.group }.map { (g, list) -> CategorySection(g, list) }

/** Categoriile vizibile: „Sistem fotovoltaic” doar când setarea e activă. */
fun visibleCategories(cats: List<Category>, showPv: Boolean): List<Category> =
    if (showPv) cats else cats.filter { it.kind != CategoryKeys.PV }

/** Categoria din catalog care corespunde uneia dintr-o lucrare / plan: după cheie, apoi după nume. */
fun findCategoryIndex(cats: List<Category>, key: String, name: String): Int {
    if (key.isNotBlank()) {
        val byKey = cats.indexOfFirst { it.catalogKey == key }
        if (byKey >= 0) return byKey
    }
    return cats.indexOfFirst { it.name.equals(name, ignoreCase = true) }
}

/** Materialul din categorie care corespunde numelui de catalog dat: după cheie, apoi după nume. */
fun findMaterialIndex(cat: Category, catalogName: String, name: String = catalogName): Int {
    val byKey = cat.materials.indexOfFirst { it.catalogName.equals(catalogName, ignoreCase = true) }
    if (byKey >= 0) return byKey
    return cat.materials.indexOfFirst {
        it.name.equals(name, ignoreCase = true) || it.name.equals(catalogName, ignoreCase = true)
    }
}

/**
 * Încarcă o lucrare salvată peste catalog: cantitățile revin la 0, apoi fiecare
 * linie a lucrării își găsește materialul după cheie (apoi nume); categoriile și
 * materialele lipsă se creează. Marca / modelul / faza din lucrare se preiau.
 */
fun mergeWorkIntoCatalog(cats: List<Category>, work: Work, newId: () -> Long): List<Category> {
    var result = cats.map { c -> c.copy(materials = c.materials.map { it.copy(qty = 0) }) }
    work.categories.forEach { wc ->
        val idx = findCategoryIndex(result, wc.catalogKey, wc.name)
        if (idx >= 0) {
            var target = result[idx]
            wc.materials.forEach { wm ->
                val mIdx = findMaterialIndex(target, wm.catalogName, wm.name)
                target = if (mIdx >= 0) {
                    target.copy(materials = target.materials.mapIndexed { i, m ->
                        if (i == mIdx) m.copy(qty = wm.qty) else m
                    })
                } else {
                    target.copy(
                        materials = target.materials +
                            Material(newId(), wm.name, wm.qty, wm.price, key = wm.key)
                    )
                }
            }
            if (wc.brand.isNotBlank() || wc.model.isNotBlank() || wc.phase.isNotBlank()) {
                target = target.copy(brand = wc.brand, model = wc.model, phase = wc.phase)
            }
            result = result.mapIndexed { i, c -> if (i == idx) target else c }
        } else {
            result = result + Category(
                newId(), wc.name,
                wc.materials.map { Material(newId(), it.name, it.qty, it.price, key = it.key) },
                brand = wc.brand, model = wc.model, phase = wc.phase, key = wc.catalogKey
            )
        }
    }
    return sortGrouped(result)
}

/**
 * Aplică planul asistentului peste catalog: cantități (înlocuind sau adunând),
 * mărci pe categorii (după cheie), modul de montaj al cablurilor și faza tabloului.
 * Categoriile lipsă se creează cu numele canonic; materialele lipsă, în categoria lor.
 */
fun applyPlanToCatalog(
    cats: List<Category>,
    plan: WizardPlan,
    kind: String,
    replace: Boolean,
    newId: () -> Long
): List<Category> {
    var result = if (replace)
        cats.map { c -> c.copy(materials = c.materials.map { it.copy(qty = 0) }) }
    else cats
    plan.items.forEach { item ->
        val idx = findCategoryIndex(result, item.category, CategoryKeys.nameOf(item.category))
        if (idx < 0) {
            result = result + Category(
                newId(), CategoryKeys.nameOf(item.category),
                listOf(Material(newId(), item.material, item.qty, key = item.material)),
                key = item.category
            )
        } else {
            val cat = result[idx]
            val mIdx = findMaterialIndex(cat, item.material)
            val updated = if (mIdx >= 0) cat.copy(materials = cat.materials.mapIndexed { i, m ->
                if (i == mIdx) m.copy(qty = m.qty + item.qty) else m
            }) else cat.copy(
                materials = cat.materials + Material(newId(), item.material, item.qty, key = item.material)
            )
            result = result.mapIndexed { i, c -> if (i == idx) updated else c }
        }
    }
    result = result.map { c ->
        var cc = c
        plan.brandFor[c.kind]?.let { (b, m) -> cc = cc.copy(brand = b, model = m) }
        if (plan.cableMode.isNotBlank() && c.kind == CategoryKeys.CABLURI) cc = cc.copy(phase = plan.cableMode)
        if (kind == "electric" && c.kind == CategoryKeys.TABLOU && plan.tablouPhase.isNotBlank())
            cc = cc.copy(phase = plan.tablouPhase)
        cc
    }
    return sortGrouped(result)
}

/** Un element cu preț de manoperă: eticheta afișată și cheia din `LaborConfig.dozaPrices`. */
data class LaborItem(val label: String, val priceKey: String)

/** Cheia de preț de manoperă a unui material: numele de catalog, fără majuscule. */
val Material.laborKey: String get() = catalogName.trim().lowercase()

/** Prețul de manoperă al unui material: după numele de catalog, apoi după numele afișat. */
fun LaborConfig.priceFor(m: Material): Double =
    dozaPrices[m.laborKey] ?: dozaPrices[m.name.trim().lowercase()] ?: 0.0
