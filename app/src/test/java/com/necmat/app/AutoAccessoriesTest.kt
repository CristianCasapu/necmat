package com.necmat.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class AutoAccessoriesTest {

    private fun work(vararg cats: Category) = Work(1L, "Test", 0L, cats.toList())

    private fun Work.accessories(): Category? =
        categories.firstOrNull { it.name.contains("calcul automat") }

    private fun Category?.qtyOf(name: String): Int? =
        this?.materials?.firstOrNull { it.name == name }?.qty

    @Test
    fun `rama suport si ornament pentru fiecare doza, pe marime`() {
        val w = work(
            Category(1, "Doze modulare", listOf(
                Material(10, "Doză 2 module", 3),
                Material(11, "Doză 4 module", 2)
            ))
        ).withAutoAccessories()
        val acc = w.accessories()
        assertNotNull(acc)
        assertEquals(3, acc.qtyOf("Ramă suport 2 module"))
        assertEquals(3, acc.qtyOf("Ramă ornament (mască) 2 module"))
        assertEquals(2, acc.qtyOf("Ramă suport 4 module"))
        assertEquals(2, acc.qtyOf("Ramă ornament (mască) 4 module"))
    }

    @Test
    fun `obturatoare cand modulele nu umplu dozele`() {
        // 2 doze x 3 module = 6 sloturi; 2 cap scara + 1 priza simpla = 3 module -> 3 obturatoare
        val w = work(
            Category(1, "Doze modulare", listOf(Material(10, "Doză 3 module", 2))),
            Category(2, "Module", listOf(
                Material(20, "Cap scară", 2),
                Material(21, "Priză simplă", 1)
            ))
        ).withAutoAccessories()
        assertEquals(3, w.accessories().qtyOf("Obturator (modul fals)"))
    }

    @Test
    fun `priza dubla ocupa 2 module`() {
        // 1 doza x 4 module = 4 sloturi; 1 priza dubla (2) + 1 intrerupator (1) = 3 -> 1 obturator
        val w = work(
            Category(1, "Doze modulare", listOf(Material(10, "Doză 4 module", 1))),
            Category(2, "Module", listOf(
                Material(20, "Priză dublă", 1),
                Material(21, "Întrerupător simplu", 1)
            ))
        ).withAutoAccessories()
        assertEquals(1, w.accessories().qtyOf("Obturator (modul fals)"))
    }

    @Test
    fun `fara obturatoare cand dozele sunt pline sau depasite`() {
        // 1 doza x 2 module; 2 prize duble = 4 module folosite -> fara obturatoare
        val w = work(
            Category(1, "Doze modulare", listOf(Material(10, "Doză 2 module", 1))),
            Category(2, "Module", listOf(Material(20, "Priză dublă", 2)))
        ).withAutoAccessories()
        assertNull(w.accessories().qtyOf("Obturator (modul fals)"))
        // ramele raman
        assertEquals(1, w.accessories().qtyOf("Ramă suport 2 module"))
    }

    @Test
    fun `fara doze modulare nu se adauga nimic`() {
        val w = work(
            Category(1, "Aparataj aplicat", listOf(Material(10, "Priză aplicată", 5))),
            Category(2, "Module", listOf(Material(20, "Cap scară", 2)))
        ).withAutoAccessories()
        assertNull(w.accessories())
    }

    @Test
    fun `dozele aparat incastrate nu conteaza ca doze modulare`() {
        val w = work(
            Category(1, "Doze aparat încastrate", listOf(
                Material(10, "Doză aparat pentru priză", 8)
            ))
        ).withAutoAccessories()
        assertNull(w.accessories())
    }

    @Test
    fun `totalurile includ accesoriile`() {
        val w = work(
            Category(1, "Doze modulare", listOf(Material(10, "Doză 2 module", 1)))
        ).withAutoAccessories()
        // 1 doza + 1 rama suport + 1 rama ornament + 2 obturatoare
        assertEquals(1 + 1 + 1 + 2, w.totalPieces)
    }
}
