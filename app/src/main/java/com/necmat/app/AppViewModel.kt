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

class AppViewModel(app: Application) : AndroidViewModel(app) {

    var categories by mutableStateOf(Repo.load(app))
        private set

    var works by mutableStateOf(Repo.loadWorks(app))
        private set

    var themeMode by mutableStateOf(loadTheme())
        private set

    private var saveJob: Job? = null

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

    private fun newId() = System.currentTimeMillis() + (0..999).random()

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

    fun deleteMaterial(catId: Long, matId: Long) = update { cats ->
        cats.map { c ->
            if (c.id != catId) c else c.copy(materials = c.materials.filter { it.id != matId })
        }
    }

    fun addCategory(name: String) = update { cats ->
        cats + Category(newId(), name.trim())
    }

    fun renameCategory(catId: Long, name: String) = update { cats ->
        cats.map { c -> if (c.id != catId) c else c.copy(name = name.trim()) }
    }

    fun deleteCategory(catId: Long) = update { cats ->
        cats.filter { it.id != catId }
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

    fun saveWork(name: String, client: String, address: String, phone: String): Boolean {
        val w = snapshot(name, client, address, phone)
        if (w.categories.isEmpty()) return false
        works = listOf(w) + works
        persistWorks()
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
        works = works.filter { it.id != workId }
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
                result = result.mapIndexed { i, c -> if (i == idx) target else c }
            } else {
                result = result + Category(
                    newId(), wc.name,
                    wc.materials.map { Material(newId(), it.name, it.qty, it.price) }
                )
            }
        }
        result
    }

    fun summaryText(): String {
        val sb = StringBuilder("Necesar materiale\n")
        categories.forEach { c ->
            val sel = c.materials.filter { it.qty > 0 }
            if (sel.isNotEmpty()) {
                sb.append("\n").append(c.name).append(":\n")
                sel.forEach { m -> sb.append("  • ").append(m.name).append(" — ").append(m.qty).append(" buc\n") }
            }
        }
        return sb.toString()
    }

    // ---- backup / restaurare ----

    fun backupJson(): String = Repo.backupJson(categories, works)

    /** Înlocuiește toate datele cu cele din backup. Întoarce false dacă fișierul e invalid. */
    fun restoreBackup(text: String): Boolean {
        val parsed = Repo.parseBackup(text) ?: return false
        categories = parsed.first
        works = parsed.second
        viewModelScope.launch(Dispatchers.IO) {
            Repo.save(getApplication(), parsed.first)
            Repo.saveWorks(getApplication(), parsed.second)
        }
        return true
    }

    // ---- actualizări ----

    var updateInfo by mutableStateOf<Updater.UpdateInfo?>(null)
        private set
    var updateBusy by mutableStateOf(false)
        private set

    /** Verificare silențioasă la pornire. */
    fun autoCheckUpdate() {
        viewModelScope.launch { updateInfo = Updater.check() }
    }

    /** Verificare manuală; onDone(true) dacă există versiune nouă. */
    fun checkUpdateNow(onDone: (Boolean) -> Unit) {
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
}
