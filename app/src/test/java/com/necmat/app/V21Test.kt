package com.necmat.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.21 — materiale existente la client, scăzute DUPĂ calculul accesoriilor. */
class V21Test {

    private fun work(vararg cats: Category, owned: List<OwnedMaterial> = emptyList()) =
        Work(1L, "Test", 0L, cats.toList(), owned = owned)

    private fun Work.qtyOf(name: String): Int? =
        categories.flatMap { it.materials }.firstOrNull { it.name == name }?.qty

    private val doze = Category(1, "Doze modulare", listOf(Material(10, "Doză 4 module", 1)))
    private val module = Category(
        2, "Module",
        listOf(Material(20, "Priză simplă", 2), Material(21, "Întrerupător simplu", 2))
    )

    @Test
    fun `exemplul de aur - modulele clientului nu devin obturatoare`() {
        val w = work(doze, module, owned = listOf(OwnedMaterial("Priză simplă", 2)))
            .shoppingList(autoAccessories = true, includeBoxes = false)
        assertNull(w.qtyOf("Priză simplă"))                 // 2 − 2 = 0 → linia dispare
        assertEquals(2, w.qtyOf("Întrerupător simplu"))
        assertEquals(1, w.qtyOf("Ramă suport 4 module"))
        assertEquals(1, w.qtyOf("Ramă ornament (mască) 4 module"))
        assertNull(w.qtyOf("Obturator (modul fals)"))        // sloturile sunt ocupate
        assertNull(w.qtyOf("Doză 4 module"))                 // doza e montată, nu apare
    }

    @Test
    fun `scaderea inainte de accesorii ar produce obturatoare false`() {
        // documentează de ce ordinea din shoppingList e obligatorie
        val gresit = work(doze, module, owned = listOf(OwnedMaterial("Priză simplă", 2)))
            .subtractOwned().withAutoAccessories()
        assertEquals(2, gresit.qtyOf("Obturator (modul fals)"))
    }

    @Test
    fun `rama existenta la client scade fara sa schimbe obturatoarele`() {
        val treiModule = Category(2, "Module", listOf(Material(20, "Priză simplă", 3)))
        val w = work(doze, treiModule, owned = listOf(OwnedMaterial("Ramă suport 4 module", 1)))
            .shoppingList(autoAccessories = true, includeBoxes = false)
        assertNull(w.qtyOf("Ramă suport 4 module"))
        assertEquals(1, w.qtyOf("Ramă ornament (mască) 4 module"))
        assertEquals(1, w.qtyOf("Obturator (modul fals)"))
        assertEquals(3, w.qtyOf("Priză simplă"))
    }

    @Test
    fun `clientul are mai mult decat necesarul`() {
        val w = work(module, owned = listOf(OwnedMaterial("Priză simplă", 6))).subtractOwned()
        assertNull(w.qtyOf("Priză simplă"))
        assertTrue(w.categories.flatMap { it.materials }.all { it.qty > 0 })
        // în rezultat rămâne doar cantitatea efectiv scăzută
        assertEquals(listOf(OwnedMaterial("Priză simplă", 2)), w.owned)
    }

    @Test
    fun `material inexistent in necesar este ignorat`() {
        val w = work(module, owned = listOf(OwnedMaterial("Cablu 3x2.5", 50))).subtractOwned()
        assertEquals(2, w.qtyOf("Priză simplă"))
        assertTrue(w.owned.isEmpty())
    }

    @Test
    fun `potrivirea ignora majusculele si diacriticele`() {
        val tablou = Category(3, "Tablou electric", listOf(Material(30, "Siguranță automată 16A", 4)))
        val w = work(tablou, owned = listOf(OwnedMaterial("siguranta  AUTOMATA 16a", 3))).subtractOwned()
        assertEquals(1, w.qtyOf("Siguranță automată 16A"))
        // ş/ţ cu sedilă (tastaturi vechi) = ș/ț cu virgulă
        assertEquals("siguranta automata 16a", normalizeName("Siguranţă  Automată 16A"))
    }

    @Test
    fun `oferta de manopera nu depinde de materialele clientului`() {
        val cfg = Repo.defaultLaborConfig()
        val fara = laborQuote(work(doze, module), cfg)
        val cu = laborQuote(work(doze, module, owned = listOf(OwnedMaterial("Priză simplă", 2))), cfg)
        assertEquals(fara.lines, cu.lines)
        assertEquals(fara.total, cu.total, 0.001)
    }

    @Test
    fun `json dus-intors si lucrari vechi fara cheia owned`() {
        val owned = listOf(OwnedMaterial("Priză simplă", 2, "Module"))
        val back = Repo.parseBackup(
            Repo.backupJson(listOf(module), listOf(work(module, owned = owned)))
        )
        assertNotNull(back)
        assertEquals(owned, back!!.works[0].owned)
        assertTrue(Repo.ownedFromJson(null).isEmpty())
        assertEquals(owned, Repo.ownedFromJson(Repo.ownedToJson(owned)))
        // backup v3 (fără cheia owned) → listă goală
        val old = Repo.parseBackup(Repo.backupJson(listOf(module), listOf(work(module))))
        assertTrue(old!!.works[0].owned.isEmpty())
    }

    @Test
    fun `fara listare, materialele clientului sunt scazute dar nu apar`() {
        val w = work(module, owned = listOf(OwnedMaterial("Priză simplă", 1)))
            .shoppingList(autoAccessories = true, includeBoxes = false, listOwned = false)
        assertEquals(1, w.qtyOf("Priză simplă"))
        assertTrue(w.owned.isEmpty())
    }

    @Test
    fun `doua intrari cu acelasi nume se cumuleaza`() {
        val w = work(
            module,
            owned = listOf(OwnedMaterial("Priză simplă", 1), OwnedMaterial("PRIZĂ SIMPLĂ", 1))
        ).subtractOwned()
        assertNull(w.qtyOf("Priză simplă"))
        assertEquals(2, w.owned.sumOf { it.qty })
    }
}
