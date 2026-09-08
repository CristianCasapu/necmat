package com.necmat.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.36 — chei stabile pe categorii / materiale, grupuri de afișare, marcă + model
 * obligatorii, client persoană fizică / juridică cu CUI, fotovoltaic ascuns.
 * Toate datele sunt fictive.
 */
class V36Test {

    private var idCounter = 36_000L
    private fun nextId(): Long = idCounter++

    private fun cat(key: String, name: String = CategoryKeys.nameOf(key), vararg items: Pair<String, Int>) =
        Category(nextId(), name, items.map { Material(nextId(), it.first, it.second, key = it.first) }, key = key)

    // ---- chei ----

    @Test
    fun `numele canonice si istorice dau cheia, numele proprii nu`() {
        assertEquals(CategoryKeys.MODULE, CategoryKeys.exact("Module"))
        assertEquals(CategoryKeys.MODULE, CategoryKeys.exact("  module "))
        assertEquals(CategoryKeys.CABLURI, CategoryKeys.exact("Cabluri și tuburi (m)"))
        assertEquals(CategoryKeys.DOZE_APARAT, CategoryKeys.exact("Doze aparat îngropate"))
        assertEquals("", CategoryKeys.exact("Tablou etaj 2"))
        assertEquals("", CategoryKeys.exact("Categorie proprie"))
    }

    @Test
    fun `deducerea permisiva grupeaza categoriile proprii, dar nu le da cheie`() {
        assertEquals(CategoryKeys.TABLOU, CategoryKeys.loose("Tablou etaj 2"))
        assertEquals(CategoryKeys.CABLURI, CategoryKeys.loose("Tuburi PVC"))
        assertEquals(CategoryKeys.ILUMINAT, CategoryKeys.loose("Lămpi (montaj)"))
        assertEquals("", CategoryKeys.loose("Scule"))
        val own = Category(1, "Tablou etaj 2")
        assertEquals("", own.catalogKey)
        assertEquals(CategoryKeys.TABLOU, own.kind)
        assertEquals(CategoryGroup.TABLOU, own.group)
        assertEquals(CategoryGroup.ALTELE, Category(2, "Scule").group)
    }

    @Test
    fun `catalogul implicit are chei pe toate categoriile si materialele`() {
        val cats = Repo.defaultCatalog()
        assertTrue(cats.all { it.key.isNotBlank() })
        assertTrue(cats.all { c -> c.materials.all { it.key == it.name } })
        assertEquals(CategoryKeys.canonicalName.keys - CategoryKeys.ACCESORII, cats.map { it.key }.toSet())
    }

    @Test
    fun `assignCatalogKeys pune cheile pe listele vechi si lasa materialele proprii fara cheie`() {
        val old = listOf(
            Category(1, "Module", listOf(Material(10, "Priză simplă", 2), Material(11, "Modul special al meu", 1))),
            Category(2, "Categorie proprie", listOf(Material(20, "Ceva", 1)))
        )
        val keyed = assignCatalogKeys(old)
        assertEquals(CategoryKeys.MODULE, keyed[0].key)
        assertEquals("Priză simplă", keyed[0].materials[0].key)
        assertEquals("", keyed[0].materials[1].key)
        assertEquals("", keyed[1].key)
        assertEquals("", keyed[1].materials[0].key)
        // idempotent
        assertEquals(keyed, assignCatalogKeys(keyed))
    }

    // ---- redenumire fără efect asupra actualizărilor ----

    @Test
    fun `categoria redenumita primeste in continuare materialele noi din migrari`() {
        val renamed = listOf(
            Category(1, "Aparataj modular Gewiss", listOf(Material(10, "Priză simplă", 1, key = "Priză simplă")), key = CategoryKeys.MODULE),
            Category(2, "Tablou Schneider", emptyList(), key = CategoryKeys.TABLOU)
        )
        val out = Repo.applyAllMigrations(renamed) { nextId() }
        // nu apar categorii duplicate „Module” / „Tablou electric”
        assertEquals(1, out.count { it.kind == CategoryKeys.MODULE })
        assertEquals(1, out.count { it.kind == CategoryKeys.TABLOU })
        assertEquals("Aparataj modular Gewiss", out.first { it.key == CategoryKeys.MODULE }.name)
        assertTrue(out.first { it.key == CategoryKeys.MODULE }.materials.any { it.key == "Modul TV" })
        assertTrue(out.first { it.key == CategoryKeys.TABLOU }.materials.any { it.key == "MCB 3P 16A" })
    }

    @Test
    fun `materialul redenumit nu e re-adaugat de migrari si ramane recunoscut`() {
        val cats = listOf(
            Category(
                1, "Module",
                listOf(Material(10, "TV (modul Gewiss)", 1, key = "Modul TV")),
                key = CategoryKeys.MODULE
            ),
            Category(
                2, "Doze modulare",
                listOf(Material(20, "Doză 3M", 2, key = "Doză 3 module")),
                key = CategoryKeys.DOZE_MODULARE
            )
        )
        val out = Repo.applyAllMigrations(cats) { nextId() }
        val module = out.first { it.key == CategoryKeys.MODULE }
        assertEquals(1, module.materials.count { it.catalogName == "Modul TV" })
        assertEquals("TV (modul Gewiss)", module.materials.first { it.key == "Modul TV" }.name)
        // regulile pe nume folosesc numele de catalog: doza redenumită rămâne doză modulară
        val w = Work(1, "t", 0L, out).withAutoAccessories()
        val acc = w.categories.first { it.kind == CategoryKeys.ACCESORII }
        assertEquals(2, acc.materials.first { it.name == "Ramă suport 3 module" }.qty)
        assertEquals(5, acc.materials.first { it.name == "Obturator (modul fals)" }.qty)
        assertEquals(6, accessorySummary(out)!!.slots)
    }

    @Test
    fun `manopera foloseste numele de catalog al materialului redenumit`() {
        val w = Work(
            1, "t", 0L,
            listOf(cat(CategoryKeys.DOZE_MODULARE, "Doze Gewiss", "Doză 2 module" to 3).let { c ->
                c.copy(materials = c.materials.map { it.copy(name = "Doză 2M") })
            })
        )
        val q = laborQuote(w, Repo.defaultLaborConfig())
        assertEquals(90.0, q.lines.first { it.name == "Montaj aparataj modular" }.value, 0.01)
    }

    @Test
    fun `lucrarea salvata se reincarca dupa cheie chiar daca s-a redenumit categoria`() {
        val catalog = listOf(
            Category(1, "Aparataj modular", listOf(Material(10, "Priză 2 module", 0, key = "Priză 2 module")), key = CategoryKeys.MODULE),
            Category(2, "Doze modulare", listOf(Material(20, "Doză 2 module", 0, key = "Doză 2 module")), key = CategoryKeys.DOZE_MODULARE)
        )
        val work = Work(
            5, "L", 0L,
            listOf(
                Category(7, "Module", listOf(Material(70, "Priză 2 module", 4, key = "Priză 2 module")), key = CategoryKeys.MODULE, brand = "Gewiss", model = "Chorus"),
                Category(8, "A mea", listOf(Material(80, "Chestie", 1)))
            )
        )
        val merged = mergeWorkIntoCatalog(catalog, work) { nextId() }
        val module = merged.first { it.key == CategoryKeys.MODULE }
        assertEquals("Aparataj modular", module.name)
        assertEquals(4, module.materials.first().qty)
        assertEquals("Gewiss", module.brand)
        assertTrue(merged.any { it.name == "A mea" && it.materials.first().qty == 1 })
        // ordinea grupată: dozele înaintea aparatajului
        assertTrue(merged.indexOfFirst { it.key == CategoryKeys.DOZE_MODULARE } < merged.indexOfFirst { it.key == CategoryKeys.MODULE })
    }

    @Test
    fun `planul asistentului se aplica pe chei si creeaza categoriile lipsa cu numele canonic`() {
        val catalog = listOf(
            Category(1, "Modulele mele", listOf(Material(10, "Priză 2 module", 1, key = "Priză 2 module")), key = CategoryKeys.MODULE)
        )
        val plan = WizardEstimator.estimateElectric(ElectricInput(bedrooms = 1, bathrooms = 0, kitchens = 0, living = 0, brand = "Gewiss", brandModel = "Chorus"))
        val out = applyPlanToCatalog(catalog, plan, "electric", replace = true) { nextId() }
        val module = out.first { it.key == CategoryKeys.MODULE }
        assertEquals("Modulele mele", module.name)
        assertEquals(2, module.materials.first { it.key == "Priză 2 module" }.qty)   // înlocuit, nu adunat
        assertEquals("Gewiss", module.brand)
        assertEquals("Tablou electric", out.first { it.key == CategoryKeys.TABLOU }.name)
        assertEquals("mono", out.first { it.key == CategoryKeys.TABLOU }.phase)
        assertEquals(1, out.count { it.kind == CategoryKeys.MODULE })
    }

    // ---- grupuri ----

    @Test
    fun `sortGrouped pune dozele inaintea aparatajului si categoriile proprii la grupul lor`() {
        val cats = listOf(
            cat(CategoryKeys.MODULE),
            Category(nextId(), "Scule"),
            cat(CategoryKeys.TABLOU),
            Category(nextId(), "Tablou etaj 2"),
            cat(CategoryKeys.DOZE_MODULARE),
            cat(CategoryKeys.APARATAJ_INCASTRAT),
            cat(CategoryKeys.DOZE_APARAT)
        )
        val sorted = sortGrouped(cats).map { it.name }
        assertEquals(
            listOf(
                "Doze aparat încastrate", "Aparataj încastrat", "Doze modulare", "Module",
                "Tablou electric", "Tablou etaj 2", "Scule"
            ),
            sorted
        )
        assertEquals(sortGrouped(cats), sortGrouped(sortGrouped(cats)))
    }

    @Test
    fun `antetul de grup apare doar la grupurile cu cel putin doua categorii`() {
        val sections = groupSections(listOf(cat(CategoryKeys.TABLOU), cat(CategoryKeys.MODULE), cat(CategoryKeys.DOZE_MODULARE)))
        assertEquals(listOf(CategoryGroup.MODULAR, CategoryGroup.TABLOU), sections.map { it.group })
        assertTrue(sections[0].showHeader)
        assertFalse(sections[1].showHeader)
    }

    @Test
    fun `accesoriile calculate intra in grupul modular, dupa module`() {
        val w = Work(
            1, "t", 0L,
            listOf(
                cat(CategoryKeys.TABLOU, items = arrayOf("MCB 1P+N 16A" to 1)),
                cat(CategoryKeys.DOZE_MODULARE, items = arrayOf("Doză 3 module" to 1)),
                cat(CategoryKeys.MODULE, items = arrayOf("Priză simplă" to 1))
            )
        ).withAutoAccessories()
        assertEquals(
            listOf(CategoryKeys.DOZE_MODULARE, CategoryKeys.MODULE, CategoryKeys.ACCESORII, CategoryKeys.TABLOU),
            w.categories.map { it.kind }
        )
    }

    @Test
    fun `catalogul implicit respecta ordinea grupata`() {
        val cats = Repo.defaultCatalog()
        assertEquals(cats, sortGrouped(cats))
    }

    // ---- marcă + model obligatorii ----

    @Test
    fun `marca si modelul sunt obligatorii doar la aparataj si tablou`() {
        assertTrue(cat(CategoryKeys.MODULE).brandRequired)
        assertTrue(cat(CategoryKeys.DOZE_APARAT).brandRequired)
        assertTrue(cat(CategoryKeys.APARATAJ_APLICAT).brandRequired)
        assertTrue(cat(CategoryKeys.TABLOU).brandRequired)
        assertFalse(cat(CategoryKeys.CABLURI).brandRequired)
        assertFalse(cat(CategoryKeys.DOZE_LEGATURI).brandRequired)
        assertFalse(cat(CategoryKeys.ILUMINAT).brandRequired)
        assertFalse(cat(CategoryKeys.PV).brandRequired)
        assertFalse(Category(1, "Scule").brandRequired)
    }

    @Test
    fun `lipsesc din necesar doar categoriile bifate fara marca sau fara model`() {
        val cats = listOf(
            cat(CategoryKeys.MODULE, items = arrayOf("Priză simplă" to 2)),                                // fără marcă
            cat(CategoryKeys.TABLOU, items = arrayOf("MCB 1P+N 16A" to 1)).copy(brand = "Schneider"),       // fără model
            cat(CategoryKeys.DOZE_MODULARE, items = arrayOf("Doză 2 module" to 1)).copy(brand = "Gewiss", model = "Chorus"),
            cat(CategoryKeys.APARATAJ_APLICAT, items = arrayOf("Priză aplicată" to 0)),                     // nebifată
            cat(CategoryKeys.CABLURI, items = arrayOf("Cablu CYY-F 3x2.5" to 50))                          // opțional
        )
        assertEquals(listOf("Module", "Tablou electric"), missingBrandCategories(cats).map { it.name })
    }

    // ---- fotovoltaic ascuns ----

    @Test
    fun `fara setare, categoria fotovoltaic nu e vizibila`() {
        val cats = Repo.defaultCatalog()
        assertTrue(visibleCategories(cats, showPv = false).none { it.kind == CategoryKeys.PV })
        assertTrue(visibleCategories(cats, showPv = true).any { it.kind == CategoryKeys.PV })
        assertEquals(cats.size - 1, visibleCategories(cats, showPv = false).size)
    }

    // ---- materiale existente la client ----

    @Test
    fun `materialul clientului se scade dupa cheie chiar daca linia a fost redenumita`() {
        val w = Work(
            1, "t", 0L,
            listOf(cat(CategoryKeys.MODULE, items = arrayOf("Priză simplă" to 5)).let { c ->
                c.copy(materials = c.materials.map { it.copy(name = "Priză simplă albă") })
            }),
            owned = listOf(OwnedMaterial("Priză simplă", 2, "Module", key = "Priză simplă", catKey = CategoryKeys.MODULE))
        ).subtractOwned()
        assertEquals(3, w.categories.first().materials.first().qty)
        assertEquals(2, w.owned.first().qty)
        assertEquals("buc", w.owned.first().unit)
        assertEquals("m", OwnedMaterial("Cablu", 1, "Cabluri", catKey = CategoryKeys.CABLURI).unit)
    }

    // ---- persistență ----

    @Test
    fun `cheile, cui-ul si tipul clientului trec prin JSON si backup`() {
        val cats = listOf(cat(CategoryKeys.MODULE, "Modulele mele", "Priză simplă" to 1))
        val work = Work(
            9, "L", 1L, cats, client = "Firma SRL", cui = "RO12345678",
            owned = listOf(OwnedMaterial("Priză simplă", 1, "Modulele mele", key = "Priză simplă", catKey = CategoryKeys.MODULE))
        )
        val client = Client(3, "Firma SRL", kind = Client.KIND_PJ, cui = "RO12345678")
        val json = Repo.backupJson(cats, listOf(work), clients = listOf(client))
        val b = Repo.parseBackup(json)!!
        assertEquals(CategoryKeys.MODULE, b.categories[0].key)
        assertEquals("Priză simplă", b.categories[0].materials[0].key)
        assertEquals("RO12345678", b.works[0].cui)
        assertEquals("Priză simplă", b.works[0].owned[0].key)
        assertEquals(CategoryKeys.MODULE, b.works[0].owned[0].catKey)
        assertEquals(Client.KIND_PJ, b.clients[0].kind)
        assertEquals("RO12345678", b.clients[0].cui)
        assertTrue(b.clients[0].isCompany)
        // backup vechi, fără câmpurile noi → valori implicite
        val old = ClientsRepo.fromJson(JSONObject().put("id", 4).put("name", "Ion"))
        assertEquals(Client.KIND_PF, old.kind)
        assertEquals("", old.cui)
        assertFalse(old.isCompany)
    }

    // ---- CUI ----

    private fun fakeCui(digits: String) = digits + cuiControlDigit(digits)

    @Test
    fun `validarea CUI - cifra de control, prefix RO, lungime`() {
        val valid = fakeCui("1234567")
        assertTrue(isValidCui(valid))
        assertTrue(isValidCui("RO$valid"))
        assertTrue(isValidCui("ro $valid"))
        assertTrue(isValidCui(""))                       // opțional
        val bad = valid.dropLast(1) + ((valid.last() - '0' + 1) % 10)
        assertFalse(isValidCui(bad))
        assertFalse(isValidCui("1"))
        assertFalse(isValidCui("12345678901"))
        assertFalse(isValidCui("RO12A45"))
        assertEquals("RO$valid", normalizeCui("ro $valid"))
    }

    @Test
    fun `eticheta tipului de client`() {
        assertEquals("Persoană fizică", Client(1, "Ion").kindLabel)
        assertEquals("Persoană juridică", Client(1, "Firma SRL", kind = Client.KIND_PJ).kindLabel)
    }
}
