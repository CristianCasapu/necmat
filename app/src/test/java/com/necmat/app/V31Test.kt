package com.necmat.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.31 — ghidajul scanării cu camera (evaluarea unui cadru), date fictive. */
class V31Test {

    private fun fakeCnp(first12: String) = first12 + cnpControlDigit(first12)
    private val guide = FrameBox(100f, 300f, 980f, 855f)   // chenar ~ 880 × 555
    private val idLines = listOf("ROMÂNIA CARTE DE IDENTITATE", "Nume /Surname", "POPESCU", "Prenume / Given names", "ION")

    @Test
    fun `fara text sau prea putin - indreapta camera`() {
        val a = assessFrame(emptyList(), null, guide, IdScanResult())
        assertEquals(0, a.level)
        assertTrue(a.status.contains("Îndreaptă"))
        assertFalse(a.looksLikeId)
        assertEquals(0, assessFrame(listOf("abc", "def"), FrameBox(200f, 400f, 600f, 500f), guide, IdScanResult()).level)
    }

    @Test
    fun `document gresit - text mult dar fara indicii de act romanesc`() {
        val lines = listOf("Bon fiscal", "Total 123.45 lei", "Multumim", "Cod 12345")
        val a = assessFrame(lines, FrameBox(200f, 400f, 800f, 700f), guide, IdCardParser.parse(lines))
        assertEquals(0, a.level)
        assertTrue(a.status.contains("Nu pare"))
        assertFalse(a.looksLikeId)
    }

    @Test
    fun `prea departe - apropie, iese din chenar - departeaza`() {
        val far = assessFrame(idLines, FrameBox(400f, 500f, 700f, 640f), guide, IdCardParser.parse(idLines))
        assertEquals(1, far.level)
        assertTrue(far.status.contains("Apropie"))
        assertTrue(far.looksLikeId)
        val close = assessFrame(idLines, FrameBox(20f, 250f, 1060f, 900f), guide, IdCardParser.parse(idLines))
        assertEquals(1, close.level)
        assertTrue(close.status.contains("Depărtează"))
    }

    @Test
    fun `incadrat dar inca necitit - tine nemiscat, CNP invalid - reflexii`() {
        val ok = assessFrame(idLines, FrameBox(150f, 350f, 930f, 800f), guide, IdCardParser.parse(idLines))
        assertEquals(1, ok.level)
        assertTrue(ok.status.contains("nemișcat"))
        val badCnp = IdScanResult(surname = "Popescu", cnp = "1234567890123", cnpSure = false)
        val b = assessFrame(idLines + "CNP 1234567890123", FrameBox(150f, 350f, 930f, 800f), guide, badCnp)
        assertTrue(b.status.contains("greșit"))
    }

    @Test
    fun `citit complet - verde, indiferent de incadrare`() {
        val cnp = fakeCnp("190010112345")
        val r = IdCardParser.parse(idLines + listOf("CNP / PIN", cnp))
        assertTrue(r.cnpSure)
        val a = assessFrame(idLines + cnp, FrameBox(400f, 500f, 700f, 640f), guide, r)
        assertEquals(2, a.level)
        assertTrue(a.status.startsWith("Citit"))
        assertTrue(a.looksLikeId)
    }

    @Test
    fun `reuniunea casetelor`() {
        val u = FrameBox(10f, 10f, 20f, 20f).union(FrameBox(5f, 15f, 30f, 18f))
        assertEquals(FrameBox(5f, 10f, 30f, 20f), u)
        assertEquals(25f, u.width, 0.001f)
        assertEquals(10f, u.height, 0.001f)
    }
}
