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
    val materialPrices: Boolean = false,
    /** Secțiunea „Materiale existente la client” din pagina Necesar. */
    val showOwnedSection: Boolean = true,
    /** Listează în PDF / text materialele puse la dispoziție de client. */
    val ownedInPdf: Boolean = true,
    /** Tab-ul „Clienți” din bara de jos. */
    val showClientsPage: Boolean = true,
    /** Păstrează CNP-ul în fișa clientului (doar local; niciodată în PDF). */
    val storeCnp: Boolean = true
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

    /** Materialele pe care clientul le are deja, pentru necesarul din editor. */
    var owned by mutableStateOf(Repo.loadOwned(app))
        private set

    /** Clienții — entitate separată; e-mailul și CNP-ul rămân doar aici. */
    var clients by mutableStateOf(ClientsRepo.load(app))
        private set

    /** Client pre-completat pentru următorul formular de lucrare („Lucrare nouă pentru acest client”). */
    var prefillClient by mutableStateOf<Client?>(null)
        private set

    fun prefillNextWork(c: Client?) {
        prefillClient = c
    }

    var themeMode by mutableStateOf(loadTheme())
        private set

    var settings by mutableStateOf(loadSettings())
        private set

    private var saveJob: Job? = null

    var logEnabled by mutableStateOf(true)
        private set
    var logLevel by mutableStateOf(AppLog.Level.INFO)
        private set

    fun setLogging(enabled: Boolean, level: AppLog.Level) {
        logEnabled = enabled
        logLevel = level
        AppLog.enabled = enabled
        AppLog.minLevel = level
        prefs().edit()
            .putBoolean("log_enabled", enabled)
            .putString("log_level", level.name)
            .apply()
        AppLog.i("Setari", "Logging: enabled=$enabled, nivel=$level")
    }

    init {
        // jurnalul pornește primul, ca migrările să fie înregistrate
        AppLog.init(java.io.File(app.filesDir, "necmat_log.txt"))
        logEnabled = prefs().getBoolean("log_enabled", true)
        logLevel = runCatching {
            AppLog.Level.valueOf(prefs().getString("log_level", "INFO") ?: "INFO")
        }.getOrDefault(AppLog.Level.INFO)
        AppLog.enabled = logEnabled
        AppLog.minLevel = logLevel
        AppLog.installCrashHandler()
        AppLog.i(
            "App",
            "Pornire NecMat v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), " +
                "Android ${android.os.Build.VERSION.RELEASE}, " +
                "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        )
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
            AppLog.i("Migrari", "Auto-reparare rulată pentru versiunea ${BuildConfig.VERSION_CODE}")
        }
        // v1.22: clienții devin entitate — se extrag o singură dată din lucrările existente
        if (!prefs().getBoolean("migr_clients", false)) {
            val (cl, ws) = clientsFromWorks(works, clients, newId = { newId() })
            clients = cl
            works = ws
            persistClients()
            persistWorks()
            prefs().edit().putBoolean("migr_clients", true).apply()
            AppLog.i("Clienti", "Clienți extrași din lucrări: ${cl.size}")
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

    private var undoClients: List<Client>? = null

    private fun rememberUndo() {
        undoState = categories to works
        undoClients = clients
    }

    /** Restaurează starea dinaintea ultimei ștergeri. */
    fun undoDelete(): Boolean {
        val s = undoState ?: return false
        AppLog.i("Materiale", "Ștergere anulată (undo)")
        categories = s.first
        works = s.second
        undoState = null
        undoClients?.let { clients = it; persistClients() }
        undoClients = null
        persist()
        persistWorks()
        return true
    }

    // ---- clienți ----

    private fun persistClients() {
        val snapshot = clients
        viewModelScope.launch(Dispatchers.IO) {
            ClientsRepo.save(getApplication(), snapshot)
        }
    }

    /** Creează (id 0) sau actualizează un client; întoarce varianta salvată. */
    fun upsertClient(c: Client): Client {
        val now = System.currentTimeMillis()
        val clean = c.copy(
            name = c.name.trim(), phone = c.phone.trim(), address = c.address.trim(),
            email = c.email.trim(), cnp = if (settings.storeCnp) c.cnp.trim() else "",
            notes = c.notes.trim(), updatedAt = now
        )
        val exists = clients.any { it.id == clean.id }
        val saved = if (exists) clean
        else clean.copy(id = if (clean.id == 0L) newId() else clean.id, createdAt = now)
        clients = if (exists) clients.map { if (it.id == saved.id) saved else it } else clients + saved
        persistClients()
        AppLog.i("Clienti", if (exists) "Client actualizat: ${saved.name}" else "Client nou: ${saved.name}")
        return saved
    }

    /** Șterge clientul; lucrările lui rămân (doar legătura dispare). Se poate anula. */
    fun deleteClient(clientId: Long) {
        rememberUndo()
        AppLog.i("Clienti", "Client șters: ${clients.firstOrNull { it.id == clientId }?.name}")
        clients = clients.filter { it.id != clientId }
        works = unlinkClient(works, clientId)
        persistClients()
        persistWorks()
    }

    /**
     * Id-ul clientului pentru o lucrare: cel ales în formular, altfel un client
     * existent cu același telefon / nume + adresă, altfel unul nou din datele lucrării.
     */
    private fun resolveClientId(
        client: String, phone: String, address: String, clientId: Long?, cnp: String = ""
    ): Long? {
        val existingId = when {
            clientId != null && clients.any { it.id == clientId } -> clientId
            client.isBlank() && phone.isBlank() -> return null
            else -> findDuplicateClient(Client(0L, client.trim(), phone.trim(), address.trim()), clients)?.id
        }
        if (existingId != null) {
            // CNP-ul citit de pe act completează fișa existentă, dacă era goală
            val existing = clients.first { it.id == existingId }
            if (cnp.isNotBlank() && settings.storeCnp && existing.cnp.isBlank() && isValidCnp(cnp)) {
                upsertClient(existing.copy(cnp = cnp))
            }
            return existingId
        }
        val probe = Client(0L, client.trim(), phone.trim(), address.trim(), cnp = cnp.trim())
        return upsertClient(probe.copy(name = probe.name.ifBlank { "Client fără nume" })).id
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
        trimOwned()
    }

    // ---- materiale existente la client ----

    var ownedExpanded by mutableStateOf(prefs().getBoolean("owned_open", false))
        private set

    fun toggleOwnedExpanded() {
        ownedExpanded = !ownedExpanded
        prefs().edit().putBoolean("owned_open", ownedExpanded).apply()
    }

    private fun persistOwned() {
        val snapshot = owned
        viewModelScope.launch(Dispatchers.IO) {
            Repo.saveOwned(getApplication(), snapshot)
        }
    }

    /**
     * Liniile care ar intra în lista de cumpărături înainte de scăderea
     * materialelor clientului (necesar + accesorii automate, filtrate ca pentru PDF).
     */
    fun purchasableLines(): List<Category> {
        val base = Work(
            0L, "", 0L,
            categories.map { c -> c.copy(materials = c.materials.filter { it.qty > 0 }) }
                .filter { it.materials.isNotEmpty() }
        )
        return base.shoppingList(settings.autoAccessories, settings.includeBoxesInPdf).categories
    }

    private fun purchasableQty(): Map<String, Int> {
        val m = mutableMapOf<String, Int>()
        purchasableLines().forEach { c ->
            c.materials.forEach { mat -> m.merge(normalizeName(mat.name), mat.qty, Int::plus) }
        }
        return m
    }

    /** Setează câte bucăți are clientul; 0 scoate linia. Plafonat la necesar. */
    fun setOwned(name: String, qty: Int, category: String = "") {
        val key = normalizeName(name)
        val max = purchasableQty()[key] ?: 0
        val q = qty.coerceIn(0, max)
        val rest = owned.filter { normalizeName(it.name) != key }
        owned = if (q <= 0) rest else rest + OwnedMaterial(name.trim(), q, category)
        AppLog.i("Client", "Material existent la client: $name = $q")
        persistOwned()
    }

    fun removeOwned(name: String) = setOwned(name, 0)

    fun clearOwned() {
        if (owned.isEmpty()) return
        owned = emptyList()
        persistOwned()
    }

    /** Clientul nu poate „avea” mai mult decât e în lista de cumpărături. */
    private fun trimOwned() {
        if (owned.isEmpty()) return
        val avail = purchasableQty()
        val trimmed = owned.mapNotNull { o ->
            val max = avail[normalizeName(o.name)] ?: 0
            if (max <= 0) null else o.copy(qty = o.qty.coerceAtMost(max))
        }
        if (trimmed != owned) {
            owned = trimmed
            persistOwned()
        }
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
        AppLog.i("Materiale", "Material adăugat în $catId: $name")
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
        AppLog.i("Materiale", "Material șters: cat=$catId, mat=$matId")
        update { cats ->
            cats.map { c ->
                if (c.id != catId) c else c.copy(materials = c.materials.filter { it.id != matId })
            }
        }
    }

    fun addCategory(name: String) = update { cats ->
        AppLog.i("Materiale", "Categorie adăugată: $name")
        cats + Category(newId(), name.trim())
    }

    fun renameCategory(catId: Long, name: String) = update { cats ->
        cats.map { c -> if (c.id != catId) c else c.copy(name = name.trim()) }
    }

    fun deleteCategory(catId: Long) {
        rememberUndo()
        AppLog.i("Materiale", "Categorie ștearsă: $catId")
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

    fun resetQuantities() {
        clearOwned()
        update { cats ->
            cats.map { c -> c.copy(materials = c.materials.map { it.copy(qty = 0) }) }
        }
    }

    fun restoreDefaults() = update {
        AppLog.w("Materiale", "Lista implicită restaurată — catalogul utilizatorului a fost înlocuit")
        Repo.defaultCatalog()
    }

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
        phone: String = "",
        clientId: Long? = null
    ): Work = Work(
        id = newId(),
        name = name.trim(),
        date = System.currentTimeMillis(),
        categories = categories
            .map { c -> c.copy(materials = c.materials.filter { it.qty > 0 }) }
            .filter { it.materials.isNotEmpty() },
        client = client.trim(),
        address = address.trim(),
        phone = phone.trim(),
        owned = owned,
        clientId = clientId
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
        overwriteId: Long? = null,
        clientId: Long? = null,
        cnp: String = ""
    ): Boolean {
        val cid = resolveClientId(client, phone, address, clientId, cnp)
        val w = snapshot(name, client, address, phone, cid)
        if (w.categories.isEmpty()) return false
        works = replaceWork(works, w, overwriteId)
        persistWorks()
        AppLog.i("Lucrari", if (overwriteId != null) "Lucrare actualizată: ${w.name}" else "Lucrare salvată: ${w.name}")
        if (settings.clearAfterSave) resetQuantities()
        return true
    }

    fun duplicateWork(work: Work) {
        AppLog.i("Lucrari", "Lucrare duplicată: ${work.name}")
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
        AppLog.i("Lucrari", "Lucrare ștearsă: ${works.firstOrNull { it.id == workId }?.name}")
        works = works.filter { it.id != workId }
        persistWorks()
    }

    /** Marchează / demarchează o lucrare ca șablon. */
    fun toggleTemplate(workId: Long) {
        AppLog.i("Lucrari", "Șablon comutat pentru lucrarea $workId")
        works = works.map { w ->
            if (w.id != workId) w else w.copy(isTemplate = !w.isTemplate)
        }
        persistWorks()
    }

    /** Încarcă o lucrare salvată înapoi în editor (cantități + materialele clientului). */
    fun loadWork(work: Work) {
        loadWorkCategories(work)
        owned = work.owned
        persistOwned()
        trimOwned()
    }

    private fun loadWorkCategories(work: Work) = update { cats ->
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
        if (prepared.owned.isNotEmpty()) {
            sb.append("\nMateriale existente la client (nu sunt în listă):\n")
            prepared.owned.forEach { o ->
                val um = if (o.category.contains("(m)")) "m" else "buc"
                sb.append("  • ").append(o.name).append(" — ").append(o.qty)
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
        .put("showOwnedSection", settings.showOwnedSection)
        .put("ownedInPdf", settings.ownedInPdf)
        .put("showClientsPage", settings.showClientsPage)
        .put("storeCnp", settings.storeCnp)

    fun backupJson(): String =
        Repo.backupJson(categories, works, brands, labor, settingsToJson(), clients)

    /** Înlocuiește toate datele cu cele din backup. Întoarce false dacă fișierul e invalid. */
    fun restoreBackup(text: String): Boolean {
        val parsed = Repo.parseBackup(text) ?: return false
        // backupurile vechi primesc automat materialele adăugate între timp
        categories = Repo.applyAllMigrations(parsed.categories) { newId() }
        // backup v3 fără clienți: îi refacem din lucrări; v4: îi completăm dacă lipsesc
        val (restoredClients, restoredWorks) = clientsFromWorks(parsed.works, parsed.clients, newId = { newId() })
        clients = restoredClients
        works = restoredWorks
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
                    materialPrices = s.optBoolean("materialPrices", settings.materialPrices),
                    showOwnedSection = s.optBoolean("showOwnedSection", settings.showOwnedSection),
                    ownedInPdf = s.optBoolean("ownedInPdf", settings.ownedInPdf),
                    showClientsPage = s.optBoolean("showClientsPage", settings.showClientsPage),
                    storeCnp = s.optBoolean("storeCnp", settings.storeCnp)
                )
            )
        }
        trimOwned()
        val cats = categories
        val lab = labor
        viewModelScope.launch(Dispatchers.IO) {
            Repo.save(getApplication(), cats)
            Repo.saveWorks(getApplication(), restoredWorks)
            ClientsRepo.save(getApplication(), restoredClients)
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
            materialPrices = p.getBoolean("mat_prices", false),
            showOwnedSection = p.getBoolean("owned_section", true),
            ownedInPdf = p.getBoolean("owned_pdf", true),
            showClientsPage = p.getBoolean("clients_page", true),
            storeCnp = p.getBoolean("store_cnp", true)
        )
    }

    fun saveSettings(s: AppSettings) {
        val recheckOwned = s.autoAccessories != settings.autoAccessories ||
            s.includeBoxesInPdf != settings.includeBoxesInPdf
        // CNP-ul dezactivat = șters imediat din toate fișele (nu doar ascuns)
        if (!s.storeCnp && settings.storeCnp && clients.any { it.cnp.isNotBlank() }) {
            clients = clients.map { it.copy(cnp = "") }
            persistClients()
            AppLog.i("Clienti", "CNP-urile au fost șterse din fișele clienților (setare dezactivată)")
        }
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
            .putBoolean("owned_section", s.showOwnedSection)
            .putBoolean("owned_pdf", s.ownedInPdf)
            .putBoolean("clients_page", s.showClientsPage)
            .putBoolean("store_cnp", s.storeCnp)
            .apply()
        if (recheckOwned) trimOwned()
    }

    /**
     * Lucrarea pregătită pentru PDF, conform setărilor:
     * accesorii automate → − materiale existente la client → filtrare.
     */
    fun preparePdfWork(work: Work): Work {
        val filtered = work.shoppingList(
            settings.autoAccessories, settings.includeBoxesInPdf, settings.ownedInPdf
        )
        // prețurile de achiziție apar doar dacă sunt activate din Setări
        return if (settings.materialPrices) filtered
        else filtered.copy(categories = filtered.categories.map { c ->
            c.copy(materials = c.materials.map { it.copy(price = 0.0) })
        })
    }
}
