package com.necmat.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val installerName: String = "",
    val installerPhone: String = "",
    val installerCompany: String = "",
    val includeBoxesInPdf: Boolean = false,
    val autoAccessories: Boolean = true,
    val clearAfterSave: Boolean = true,
    val autoUpdateCheck: Boolean = true,
    val detailExpensesInOffer: Boolean = true,
    val materialPrices: Boolean = false
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    var categories by mutableStateOf(Repo.load(app))
        private set

    var works by mutableStateOf(Repo.loadWorks(app))
        private set

    var brands by mutableStateOf(Repo.loadBrands(app))
        private set

    var labor by mutableStateOf(Repo.loadLabor(app))
        private set

    var themeMode by mutableStateOf(loadTheme())
        private set

    var settings by mutableStateOf(loadSettings())
        private set

    private var saveJob: Job? = null

    init {
        // migrare v3: module TV/rețea + aparataj încastrat pentru instalările existente
        if (!prefs().getBoolean("migr_v3", false)) {
            categories = migrateV3(categories) { newId() }
            viewModelScope.launch(Dispatchers.IO) {
                Repo.save(getApplication(), categories)
            }
            prefs().edit().putBoolean("migr_v3", true).apply()
        }
        // migrare v4: materiale tablou/doze legături + ordinea canonică + mărci noi
        if (!prefs().getBoolean("migr_v4", false)) {
            categories = Repo.migrateV4(categories) { newId() }
            brands = Repo.mergeBrands(brands, Repo.seedBrands())
            viewModelScope.launch(Dispatchers.IO) {
                Repo.save(getApplication(), categories)
                Repo.saveBrands(getApplication(), brands)
            }
            prefs().edit().putBoolean("migr_v4", true).apply()
        }
        // migrare v5: componentele de tablou din v1.7
        if (!prefs().getBoolean("migr_v5", false)) {
            categories = Repo.migrateV5(categories) { newId() }
            viewModelScope.launch(Dispatchers.IO) {
                Repo.save(getApplication(), categories)
            }
            prefs().edit().putBoolean("migr_v5", true).apply()
        }
        // migrare v6: „îngropat" devine „încastrat" peste tot
        if (!prefs().getBoolean("migr_v6", false)) {
            categories = renameTermCategories(categories)
            works = renameTermWorks(works)
            viewModelScope.launch(Dispatchers.IO) {
                Repo.save(getApplication(), categories)
                Repo.saveWorks(getApplication(), works)
            }
            prefs().edit().putBoolean("migr_v6", true).apply()
        }
        // migrare v7: priză dublă (2 module) + despărțire CAT5 / CAT6
        if (!prefs().getBoolean("migr_v7", false)) {
            categories = Repo.migrateV7(categories) { newId() }
            viewModelScope.launch(Dispatchers.IO) {
                Repo.save(getApplication(), categories)
            }
            prefs().edit().putBoolean("migr_v7", true).apply()
        }
        // migrare v8: corpurile de iluminat (montaj)
        if (!prefs().getBoolean("migr_v8", false)) {
            categories = Repo.migrateV8(categories) { newId() }
            viewModelScope.launch(Dispatchers.IO) {
                Repo.save(getApplication(), categories)
            }
            prefs().edit().putBoolean("migr_v8", true).apply()
        }
        // migrare v9: dozele de legături pe trepte de circuite
        if (!prefs().getBoolean("migr_v9", false)) {
            categories = Repo.migrateV9(categories) { newId() }
            viewModelScope.launch(Dispatchers.IO) {
                Repo.save(getApplication(), categories)
            }
            prefs().edit().putBoolean("migr_v9", true).apply()
        }
        // migrare v10: Priză 2 module
        if (!prefs().getBoolean("migr_v10", false)) {
            categories = Repo.migrateV10(categories)
            viewModelScope.launch(Dispatchers.IO) {
                Repo.save(getApplication(), categories)
            }
            prefs().edit().putBoolean("migr_v10", true).apply()
        }
        // auto-reparare: la fiecare versiune nouă a aplicației, completează
        // materialele și mărcile lipsă (ex. listă restaurată dintr-un backup vechi)
        if (prefs().getInt("last_vc", 0) != BuildConfig.VERSION_CODE) {
            categories = Repo.applyAllMigrations(categories) { newId() }
            brands = Repo.mergeBrands(brands, Repo.seedBrands())
            viewModelScope.launch(Dispatchers.IO) {
                Repo.save(getApplication(), categories)
                Repo.saveBrands(getApplication(), brands)
            }
            prefs().edit().putInt("last_vc", BuildConfig.VERSION_CODE).apply()
        }
        // reparare id-uri duplicate (generatorul vechi putea produce coliziuni)
        val deduped = Repo.fixDuplicateIds(categories) { newId() }
        if (deduped != categories) {
            categories = deduped
            viewModelScope.launch(Dispatchers.IO) {
                Repo.save(getApplication(), categories)
            }
        }
        if (works.map { it.id }.toSet().size != works.size) {
            val seen = mutableSetOf<Long>()
            works = works.map { w -> if (seen.add(w.id)) w else w.copy(id = newId()) }
            persistWorks()
        }
    }

    /** Completează manual materialele lipsă; întoarce câte a adăugat. */
    fun repairCatalog(): Int {
        val before = categories.sumOf { it.materials.size }
        categories = Repo.applyAllMigrations(categories) { newId() }
        persist()
        return categories.sumOf { it.materials.size } - before
    }

    // ---- anulare (undo) pentru ștergeri ----

    private var undoState: Pair<List<Category>, List<Work>>? = null

    private fun rememberUndo() {
        undoState = categories to works
    }

    /** Restaurează starea dinaintea ultimei ștergeri. */
    fun undoDelete(): Boolean {
        val s = undoState ?: return false
        categories = s.first
        works = s.second
        undoState = null
        persist()
        persistWorks()
        return true
    }

    private fun persist() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300)
            Repo.save(getApplication(), categories)
        }
    }

    private fun update(transform: (List<Category>) -> List<Category>) {
        categories = transform(categories)
        persist()
    }

    // id-uri strict crescătoare: nu se pot genera duplicate nici când
    // migrările adaugă zeci de materiale în aceeași milisecundă
    private var lastGeneratedId = 0L
    private fun newId(): Long {
        val id = maxOf(System.currentTimeMillis(), lastGeneratedId + 1)
        lastGeneratedId = id
        return id
    }

    fun changeQty(catId: Long, matId: Long, delta: Int) = update { cats ->
        cats.map { c ->
            if (c.id != catId) c else c.copy(materials = c.materials.map { m ->
                if (m.id != matId) m else m.copy(qty = (m.qty + delta).coerceIn(0, 9999))
            })
        }
    }

    fun setQty(catId: Long, matId: Long, qty: Int) = update { cats ->
        cats.map { c ->
            if (c.id != catId) c else c.copy(materials = c.materials.map { m ->
                if (m.id != matId) m else m.copy(qty = qty.coerceIn(0, 9999))
            })
        }
    }

    fun addMaterial(catId: Long, name: String) = update { cats ->
        cats.map { c ->
            if (c.id != catId) c
            else c.copy(materials = c.materials + Material(newId(), name.trim()))
        }
    }

    fun updateMaterial(catId: Long, matId: Long, name: String, price: Double) = update { cats ->
        cats.map { c ->
            if (c.id != catId) c else c.copy(materials = c.materials.map { m ->
                if (m.id != matId) m
                else m.copy(name = name.trim(), price = price.coerceAtLeast(0.0))
            })
        }
    }

    fun deleteMaterial(catId: Long, matId: Long) {
        rememberUndo()
        update { cats ->
            cats.map { c ->
                if (c.id != catId) c else c.copy(materials = c.materials.filter { it.id != matId })
            }
        }
    }

    fun addCategory(name: String) = update { cats ->
        cats + Category(newId(), name.trim())
    }

    fun renameCategory(catId: Long, name: String) = update { cats ->
        cats.map { c -> if (c.id != catId) c else c.copy(name = name.trim()) }
    }

    fun deleteCategory(catId: Long) {
        rememberUndo()
        update { cats -> cats.filter { it.id != catId } }
    }

    /** Scoate din lucrare toate materialele unei categorii (cantități la 0). */
    fun zeroCategory(catId: Long) = update { cats ->
        cats.map { c ->
            if (c.id != catId) c
            else c.copy(materials = c.materials.map { it.copy(qty = 0) })
        }
    }

    /** Mută o categorie mai sus (delta = -1) sau mai jos (delta = +1). */
    fun moveCategory(catId: Long, delta: Int) = update { cats ->
        val idx = cats.indexOfFirst { it.id == catId }
        val newIdx = idx + delta
        if (idx < 0 || newIdx < 0 || newIdx >= cats.size) cats
        else cats.toMutableList().apply {
            val c = removeAt(idx)
            add(newIdx, c)
        }
    }

    /** Mută o categorie de la un index la altul (drag & drop). */
    fun moveCategoryTo(from: Int, to: Int) = update { cats ->
        if (from !in cats.indices || to !in cats.indices || from == to) cats
        else cats.toMutableList().apply {
            val c = removeAt(from)
            add(to, c)
        }
    }

    // ---- starea de pliere a categoriilor (persistentă) ----

    var collapsedIds by mutableStateOf(loadCollapsed())
        private set

    private fun loadCollapsed(): Set<Long> =
        (prefs().getStringSet("collapsed_cats", emptySet()) ?: emptySet())
            .mapNotNull { it.toLongOrNull() }.toSet()

    private fun persistCollapsed() {
        prefs().edit()
            .putStringSet("collapsed_cats", collapsedIds.map { it.toString() }.toSet())
            .apply()
    }

    fun toggleCollapsed(catId: Long) {
        collapsedIds = if (catId in collapsedIds) collapsedIds - catId else collapsedIds + catId
        persistCollapsed()
    }

    fun setAllCollapsed(collapsed: Boolean) {
        collapsedIds = if (collapsed) categories.map { it.id }.toSet() else emptySet()
        persistCollapsed()
    }

    /** Aplică o ordine nouă a categoriilor (după id-uri). */
    fun setCategoryOrder(ids: List<Long>) = update { cats ->
        val byId = cats.associateBy { it.id }
        val ordered = ids.mapNotNull { byId[it] }
        ordered + cats.filter { c -> ids.none { it == c.id } }
    }

    fun resetQuantities() = update { cats ->
        cats.map { c -> c.copy(materials = c.materials.map { it.copy(qty = 0) }) }
    }

    fun restoreDefaults() = update { Repo.defaultCatalog() }

    // ---- lucrări salvate ----

    private fun persistWorks() {
        val snapshot = works
        viewModelScope.launch(Dispatchers.IO) {
            Repo.saveWorks(getApplication(), snapshot)
        }
    }

    /** Instantaneu al necesarului curent (doar materialele cu cantitate > 0). */
    fun snapshot(
        name: String,
        client: String = "",
        address: String = "",
        phone: String = ""
    ): Work = Work(
        id = newId(),
        name = name.trim(),
        date = System.currentTimeMillis(),
        categories = categories
            .map { c -> c.copy(materials = c.materials.filter { it.qty > 0 }) }
            .filter { it.materials.isNotEmpty() },
        client = client.trim(),
        address = address.trim(),
        phone = phone.trim()
    )

    /**
     * Salvează lucrarea. Cu overwriteId, înlocuiește complet lucrarea respectivă;
     * altfel, una existentă cu același nume este înlocuită.
     */
    fun saveWork(
        name: String,
        client: String,
        address: String,
        phone: String,
        overwriteId: Long? = null
    ): Boolean {
        val w = snapshot(name, client, address, phone)
        if (w.categories.isEmpty()) return false
        works = replaceWork(works, w, overwriteId)
        persistWorks()
        if (settings.clearAfterSave) resetQuantities()
        return true
    }

    fun duplicateWork(work: Work) {
        works = listOf(
            work.copy(
                id = newId(),
                name = work.name + " (copie)",
                date = System.currentTimeMillis()
            )
        ) + works
        persistWorks()
    }

    fun deleteWork(workId: Long) {
        rememberUndo()
        works = works.filter { it.id != workId }
        persistWorks()
    }

    /** Marchează / demarchează o lucrare ca șablon. */
    fun toggleTemplate(workId: Long) {
        works = works.map { w ->
            if (w.id != workId) w else w.copy(isTemplate = !w.isTemplate)
        }
        persistWorks()
    }

    /** Încarcă o lucrare salvată înapoi în editor (setează cantitățile). */
    fun loadWork(work: Work) = update { cats ->
        var result = cats.map { c -> c.copy(materials = c.materials.map { it.copy(qty = 0) }) }
        work.categories.forEach { wc ->
            val idx = result.indexOfFirst { it.name.equals(wc.name, ignoreCase = true) }
            if (idx >= 0) {
                var target = result[idx]
                wc.materials.forEach { wm ->
                    val mIdx = target.materials.indexOfFirst {
                        it.name.equals(wm.name, ignoreCase = true)
                    }
                    target = if (mIdx >= 0) {
                        target.copy(materials = target.materials.mapIndexed { i, m ->
                            if (i == mIdx) m.copy(qty = wm.qty) else m
                        })
                    } else {
                        target.copy(
                            materials = target.materials +
                                Material(newId(), wm.name, wm.qty, wm.price)
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
                    wc.materials.map { Material(newId(), it.name, it.qty, it.price) },
                    brand = wc.brand, model = wc.model, phase = wc.phase
                )
            }
        }
        result
    }

    /** Text pentru Copiază/Trimite — același conținut ca PDF-ul de materiale. */
    fun summaryText(): String {
        val prepared = preparePdfWork(snapshot("Necesar"))
        val sb = StringBuilder("Necesar materiale\n")
        prepared.categories.forEach { c ->
            if (c.materials.isEmpty()) return@forEach
            sb.append("\n").append(c.name)
            if (c.brandLabel.isNotEmpty()) sb.append(" — ").append(c.brandLabel)
            sb.append(":\n")
            val um = if (c.name.contains("(m)")) "m" else "buc"
            c.materials.forEach { m ->
                sb.append("  • ").append(m.name).append(" — ").append(m.qty)
                    .append(" ").append(um).append("\n")
            }
        }
        return sb.toString()
    }

    // ---- mărci și modele ----

    private fun persistBrands() {
        val snapshot = brands
        viewModelScope.launch(Dispatchers.IO) {
            Repo.saveBrands(getApplication(), snapshot)
        }
    }

    fun upsertBrand(entry: BrandEntry) {
        val cleaned = entry.copy(brand = entry.brand.trim(), series = entry.series.trim())
        brands = brands.filter { it.id != cleaned.id } + cleaned
        persistBrands()
    }

    fun deleteBrand(brandId: Long) {
        brands = brands.filter { it.id != brandId }
        persistBrands()
    }

    fun restoreBrandDefaults() {
        brands = Repo.seedBrands()
        persistBrands()
    }

    /** Setează marca, modelul și faza unei categorii. */
    fun setCategoryBrand(catId: Long, brand: String, model: String, phase: String = "") =
        update { cats ->
            cats.map { c ->
                if (c.id != catId) c
                else c.copy(brand = brand.trim(), model = model.trim(), phase = phase)
            }
        }

    // ---- manoperă ----

    fun saveLabor(cfg: LaborConfig) {
        labor = cfg
        viewModelScope.launch(Dispatchers.IO) {
            Repo.saveLabor(getApplication(), cfg)
        }
    }

    /** Dozele + corpurile de iluminat din catalog (pentru prețurile de manoperă). */
    fun laborItems(): List<String> =
        categories.flatMap { c ->
            c.materials.map { it.name }.filter { n ->
                isMontajCategory(c.name) || (isDozaItem(n) && !isTablouCarcasa(n))
            }
        }.distinctBy { it.trim().lowercase() }

    // ---- backup / restaurare ----

    private fun settingsToJson(): org.json.JSONObject = org.json.JSONObject()
        .put("installerName", settings.installerName)
        .put("installerPhone", settings.installerPhone)
        .put("installerCompany", settings.installerCompany)
        .put("includeBoxesInPdf", settings.includeBoxesInPdf)
        .put("autoAccessories", settings.autoAccessories)
        .put("clearAfterSave", settings.clearAfterSave)
        .put("detailExpensesInOffer", settings.detailExpensesInOffer)
        .put("materialPrices", settings.materialPrices)

    fun backupJson(): String =
        Repo.backupJson(categories, works, brands, labor, settingsToJson())

    /** Înlocuiește toate datele cu cele din backup. Întoarce false dacă fișierul e invalid. */
    fun restoreBackup(text: String): Boolean {
        val parsed = Repo.parseBackup(text) ?: return false
        // backupurile vechi primesc automat materialele adăugate între timp
        categories = Repo.applyAllMigrations(parsed.categories) { newId() }
        works = parsed.works
        if (parsed.brands.isNotEmpty()) brands = parsed.brands
        parsed.labor?.let { labor = Repo.mergeLaborDefaults(it) }
        parsed.settings?.let { s ->
            saveSettings(
                settings.copy(
                    installerName = s.optString("installerName", settings.installerName),
                    installerPhone = s.optString("installerPhone", settings.installerPhone),
                    installerCompany = s.optString("installerCompany", settings.installerCompany),
                    includeBoxesInPdf = s.optBoolean("includeBoxesInPdf", settings.includeBoxesInPdf),
                    autoAccessories = s.optBoolean("autoAccessories", settings.autoAccessories),
                    clearAfterSave = s.optBoolean("clearAfterSave", settings.clearAfterSave),
                    detailExpensesInOffer = s.optBoolean("detailExpensesInOffer", settings.detailExpensesInOffer),
                    materialPrices = s.optBoolean("materialPrices", settings.materialPrices)
                )
            )
        }
        val cats = categories
        val lab = labor
        viewModelScope.launch(Dispatchers.IO) {
            Repo.save(getApplication(), cats)
            Repo.saveWorks(getApplication(), parsed.works)
            if (parsed.brands.isNotEmpty()) Repo.saveBrands(getApplication(), parsed.brands)
            if (parsed.labor != null) Repo.saveLabor(getApplication(), lab)
        }
        return true
    }

    // ---- actualizări ----

    var updateInfo by mutableStateOf<Updater.UpdateInfo?>(null)
        private set
    var updateBusy by mutableStateOf(false)
        private set

    /** Varianta din Play Store nu are self-update — Google Play face actualizările. */
    val selfUpdateAvailable = BuildConfig.FLAVOR == "github"

    /** Verificare silențioasă la pornire (dacă e activată din setări). */
    fun autoCheckUpdate() {
        if (!selfUpdateAvailable || !settings.autoUpdateCheck) return
        viewModelScope.launch { updateInfo = Updater.check() }
    }

    /** Verificare manuală; onDone(true) dacă există versiune nouă. */
    fun checkUpdateNow(onDone: (Boolean) -> Unit) {
        if (!selfUpdateAvailable) {
            onDone(false)
            return
        }
        viewModelScope.launch {
            val info = Updater.check()
            updateInfo = info
            onDone(info != null)
        }
    }

    fun dismissUpdate() {
        updateInfo = null
    }

    fun installUpdate(onFailed: () -> Unit) {
        val info = updateInfo ?: return
        updateBusy = true
        viewModelScope.launch {
            val ok = Updater.downloadAndInstall(getApplication(), info)
            updateBusy = false
            if (!ok) onFailed() else updateInfo = null
        }
    }

    // theme
    private fun prefs() = getApplication<Application>()
        .getSharedPreferences("settings", Application.MODE_PRIVATE)

    private fun loadTheme(): ThemeMode = try {
        ThemeMode.valueOf(prefs().getString("theme", "SYSTEM") ?: "SYSTEM")
    } catch (e: Exception) { ThemeMode.SYSTEM }

    fun setTheme(mode: ThemeMode) {
        themeMode = mode
        prefs().edit().putString("theme", mode.name).apply()
    }

    // ---- setări ----

    private fun loadSettings(): AppSettings {
        val p = prefs()
        return AppSettings(
            installerName = p.getString("inst_name", "") ?: "",
            installerPhone = p.getString("inst_phone", "") ?: "",
            installerCompany = p.getString("inst_company", "") ?: "",
            includeBoxesInPdf = p.getBoolean("pdf_boxes", false),
            autoAccessories = p.getBoolean("auto_acc", true),
            clearAfterSave = p.getBoolean("clear_after_save", true),
            autoUpdateCheck = p.getBoolean("auto_update", true),
            detailExpensesInOffer = p.getBoolean("offer_detail", true),
            materialPrices = p.getBoolean("mat_prices", false)
        )
    }

    fun saveSettings(s: AppSettings) {
        settings = s
        prefs().edit()
            .putString("inst_name", s.installerName)
            .putString("inst_phone", s.installerPhone)
            .putString("inst_company", s.installerCompany)
            .putBoolean("pdf_boxes", s.includeBoxesInPdf)
            .putBoolean("auto_acc", s.autoAccessories)
            .putBoolean("clear_after_save", s.clearAfterSave)
            .putBoolean("auto_update", s.autoUpdateCheck)
            .putBoolean("offer_detail", s.detailExpensesInOffer)
            .putBoolean("mat_prices", s.materialPrices)
            .apply()
    }

    /** Lucrarea pregătită pentru PDF, conform setărilor. */
    fun preparePdfWork(work: Work): Work {
        val withAcc = if (settings.autoAccessories) work.withAutoAccessories() else work
        val filtered = withAcc.filterForPdf(settings.includeBoxesInPdf)
        // prețurile de achiziție apar doar dacă sunt activate din Setări
        return if (settings.materialPrices) filtered
        else filtered.copy(categories = filtered.categories.map { c ->
            c.copy(materials = c.materials.map { it.copy(price = 0.0) })
        })
    }
}
