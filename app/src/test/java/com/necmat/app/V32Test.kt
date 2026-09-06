package com.necmat.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.32 — cumularea citirilor din mai multe treceri / cadre (date fictive). */
class V32Test {

    private fun fakeCnp(first12: String) = first12 + cnpControlDigit(first12)

    @Test
    fun `campurile confirmate castiga, indiferent de ordine`() {
        val cnp = fakeCnp("190010112345")
        val a = IdScanResult(surname = "Popescu", givenNames = "Ion", surnameSure = false)
        val b = IdScanResult(cnp = cnp, cnpSure = true, surname = "Popescu", surnameSure = true)
        val m = IdCardParser.merge(a, b)
        assertEquals(cnp, m.cnp)
        assertTrue(m.cnpSure)
        assertEquals("Popescu", m.surname)
        assertTrue(m.surnameSure)
        assertEquals("Ion", m.givenNames)
        assertFalse(m.givenSure)
        // simetric pentru câmpurile confirmate
        assertEquals(m.cnp, IdCardParser.merge(b, a).cnp)
        assertTrue(IdCardParser.merge(b, a).surnameSure)
    }

    @Test
    fun `fara confirmare se pastreaza valoarea mai completa`() {
        val a = IdScanResult(address = "Str. A nr. 8")
        val b = IdScanResult(address = "Str. A nr. 8, Loc. Exemplu, jud. DJ")
        assertEquals(b.address, IdCardParser.merge(a, b).address)
        assertEquals(b.address, IdCardParser.merge(b, a).address)
        assertFalse(IdCardParser.merge(a, b).addressSure)
        // gol + ceva → ceva
        assertEquals("Ion", IdCardParser.merge(IdScanResult(), IdScanResult(givenNames = "Ion")).givenNames)
        assertTrue(IdCardParser.merge(IdScanResult(), IdScanResult()).isEmpty)
    }

    @Test
    fun `un cadru cu MRZ completeaza un cadru cu adresa`() {
        val cnp = fakeCnp("290010112345")
        val dob = cnp.substring(1, 7)
        val opt = cnp.substring(0, 1) + cnp.substring(7, 13)
        val mrz = IdCardParser.parse(listOf("IDROUPOPESCU<<MARIA<ELENA<<<<<<<<<<<<", "XX123456<1ROU${dob}5F3101013${opt}6"))
        val front = IdCardParser.parse(listOf(
            "Domiciliu/Adresse/Address", "Jud.DJ Loc.Exemplu (Mun.Calafat)", "Str.Exemplului nr.8",
            "Emisă de/Delivree par/Issued by"
        ))
        val m = IdCardParser.merge(front, mrz)
        assertEquals("Popescu", m.surname)
        assertEquals("Maria Elena", m.givenNames)
        assertEquals(cnp, m.cnp)
        assertTrue(m.cnpSure)
        assertEquals("Str. Exemplului nr. 8, Loc. Exemplu (Mun. Calafat), jud. DJ", m.address)
        assertTrue(m.addressSure)
    }

    @Test
    fun `ghidajul anunta citirea partiala`() {
        val guide = FrameBox(100f, 300f, 980f, 855f)
        val lines = listOf("ROMÂNIA CARTE DE IDENTITATE", "Nume", "POPESCU", "CNP")
        val box = FrameBox(150f, 350f, 930f, 800f)
        val onlyCnp = IdScanResult(cnp = fakeCnp("190010112345"), cnpSure = true)
        assertTrue(assessFrame(lines, box, guide, onlyCnp).status.contains("caut numele"))
        val onlyName = IdScanResult(surname = "Popescu")
        assertTrue(assessFrame(lines, box, guide, onlyName).status.contains("caut CNP"))
    }
}
