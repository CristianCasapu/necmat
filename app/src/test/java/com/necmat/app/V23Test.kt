package com.necmat.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.23 — parserul pentru actele de identitate. TOATE datele de aici sunt
 * FICTIVE (nume inventate, CNP-uri generate cu cifra de control).
 */
class V23Test {

    private fun fakeCnp(first12: String) = first12 + cnpControlDigit(first12)

    /** Format electronic (2021+): etichete bilingve, CNP tipărit, fără adresă. */
    private fun newCardLines(cnp: String) = listOf(
        "ROMÂNIA  CARTE DE IDENTITATE",
        "ROMANIA  IDENTITY CARD",
        "Nume /Surname",
        "POPESCU",
        "Prenume / Given names",
        "ION-ANDREI",
        "Sex / Sex   Cetățenie / Nationality   Data nașterii / Date of birth",
        "M   ROU   01 01 1990",
        "CNP / PIN",
        cnp,
        "Nr. document / Document no.   Data expirării / Date of expiry",
        "AB1234567   01 01 2035",
        "Semnătura titularului / Holder's signature"
    )

    /** Linia 2 MRZ (TD2) pentru un CNP dat: doc + ROU + data nașterii + sex + expirare + opțional. */
    private fun mrzLine2(cnp: String, sex: Char = 'F'): String {
        val dob = cnp.substring(1, 7)
        val opt = cnp.substring(0, 1) + cnp.substring(7, 13)   // S + JJNNNC
        return "XX123456<1ROU${dob}5${sex}3101013${opt}6"
    }

    /** Format vechi: CNP tipărit, adresă pe două rânduri, MRZ pe două linii. */
    private fun oldCardLines(cnp: String) = listOf(
        "ROUMANIE  ROMANIA  ROMANIA",
        "CARTE D'IDENTITE   CARTE DE IDENTITATE   IDENTITY CARD",
        "SERIA XX  NR 123456",
        "CNP $cnp",
        "Nume/Nom/Last name",
        "POPESCU",
        "Prenume/Prenom/First name",
        "MARIA-ELENA",
        "Cetățenie/Nationalite/Nationality",
        "Română / ROU",
        "Loc naștere/Lieu de naissance/Place of birth",
        "Jud.DJ Mun.Calafat",
        "Domiciliu/Adresse/Address",
        "Jud.DJ Loc.Exemplu (Mun.Calafat)",
        "Str.Exemplului nr.8",
        "Emisă de/Delivree par/Issued by   Valabilitate/Validite/Validity",
        "SPCLEP Calafat   01.01.24-01.01.2031",
        "IDROUPOPESCU<<MARIA<ELENA<<<<<<<<<<<<",
        mrzLine2(cnp)
    )

    @Test
    fun `cartea electronica - nume prenume cu cratima si CNP, fara adresa`() {
        val cnp = fakeCnp("190010112345")
        val r = IdCardParser.parse(newCardLines(cnp))
        assertEquals("Popescu", r.surname)
        assertEquals("Ion-Andrei", r.givenNames)
        assertEquals("Popescu Ion-Andrei", r.fullName)
        assertEquals(cnp, r.cnp)
        assertTrue(r.cnpSure)
        assertEquals("", r.address)
        // doar tipărit, fără MRZ → de verificat
        assertFalse(r.surnameSure)
    }

    @Test
    fun `cartea veche - adresa curatata si CNP confirmat de MRZ`() {
        val cnp = fakeCnp("290010112345")
        val r = IdCardParser.parse(oldCardLines(cnp))
        assertEquals("Popescu", r.surname)
        assertEquals("Maria-Elena", r.givenNames)          // cratima vine din tipărit
        assertTrue(r.surnameSure)                           // tipărit = MRZ
        assertTrue(r.givenSure)
        assertEquals(cnp, r.cnp)
        assertTrue(r.cnpSure)
        assertEquals("Str. Exemplului nr. 8, Loc. Exemplu (Mun. Calafat), jud. DJ", r.address)
        assertTrue(r.addressSure)
    }

    @Test
    fun `doar MRZ - numele si CNP-ul se reconstruiesc`() {
        val cnp = fakeCnp("290010112345")
        val r = IdCardParser.parse(listOf("IDROUPOPESCU<<MARIA<ELENA<<<<<<<<<<<<", mrzLine2(cnp)))
        assertEquals("Popescu", r.surname)
        assertEquals("Maria Elena", r.givenNames)
        assertTrue(r.surnameSure)
        assertEquals(cnp, r.cnp)
        assertTrue(r.cnpSure)
        assertEquals(cnp, IdCardParser.cnpFromMrz(mrzLine2(cnp)))
    }

    @Test
    fun `confuziile OCR din CNP sunt corectate`() {
        val cnp = fakeCnp("190010112345")
        val confused = cnp.replace('0', 'O').replace('1', 'I').replace('5', 'S')
        val r = IdCardParser.parse(listOf("CNP / PIN", confused))
        assertEquals(cnp, r.cnp)
        assertTrue(r.cnpSure)
        assertEquals("1900", IdCardParser.fixDigits("I9OO"))
        // un CNP cu cifra de control greșită nu e acceptat
        val bad = cnp.dropLast(1) + ((cnp.last() - '0' + 1) % 10)
        assertEquals("", IdCardParser.parse(listOf("CNP", bad)).cnp)
    }

    @Test
    fun `tiparitul gresit este corectat de MRZ si marcat de verificat`() {
        val cnp = fakeCnp("290010112345")
        // literă confundată cu cifră: se corectează și coincide cu MRZ → verificat
        val zero = oldCardLines(cnp).map { if (it == "POPESCU") "P0PESCU" else it }
        val r0 = IdCardParser.parse(zero)
        assertEquals("Popescu", r0.surname)
        assertTrue(r0.surnameSure)
        // conflict real de litere: câștigă MRZ, marcat „de verificat”
        val conflict = oldCardLines(cnp).map { if (it == "POPESCU") "POPESCV" else it }
        val r1 = IdCardParser.parse(conflict)
        assertEquals("Popescu", r1.surname)
        assertFalse(r1.surnameSure)
    }

    @Test
    fun `ordinea blocurilor nu conteaza`() {
        val cnp = fakeCnp("290010112345")
        val l = oldCardLines(cnp)
        // MRZ-ul și blocul CNP ajung primele (OCR-ul poate întoarce blocurile în altă ordine)
        val shuffled = l.takeLast(2) + l.slice(3..3) + l.slice(0..2) + l.slice(4..l.lastIndex - 2)
        val r = IdCardParser.parse(shuffled)
        assertEquals("Popescu", r.surname)
        assertEquals("Maria-Elena", r.givenNames)
        assertEquals(cnp, r.cnp)
        assertEquals("Str. Exemplului nr. 8, Loc. Exemplu (Mun. Calafat), jud. DJ", r.address)
    }

    @Test
    fun `text fara ancore da rezultat gol fara exceptie`() {
        val r = IdCardParser.parse(listOf("Bon fiscal", "Total 123.45 lei", "Multumim!"))
        assertTrue(r.isEmpty)
        assertTrue(IdCardParser.parse(emptyList()).isEmpty)
        assertTrue(IdCardParser.parse(listOf("", "   ")).isEmpty)
    }

    @Test
    fun `curatarea adresei si titlul numelor`() {
        assertEquals(
            "Str. Exemplului nr. 8, Loc. Exemplu (Mun. Calafat), jud. DJ" to true,
            IdCardParser.cleanAddress("Jud.DJ Loc.Exemplu (Mun.Calafat) Str.Exemplului nr.8")
        )
        assertEquals(
            "Str. Lungă nr. 10, bl. A, ap. 3, Mun. Craiova, jud. DJ" to true,
            IdCardParser.cleanAddress("Jud.DJ Mun.Craiova Str.Lungă nr.10, bl.A, ap.3")
        )
        // fără structură cunoscută textul rămâne, dar e marcat „de verificat”
        assertEquals("ceva neclar" to false, IdCardParser.cleanAddress("ceva  neclar"))
        assertEquals("Cristian-Costinel", IdCardParser.titleCase("CRISTIAN-COSTINEL"))
        assertEquals("Adina Georgiana", IdCardParser.titleCase("ADINA GEORGIANA"))
        assertEquals("Ștefan", IdCardParser.titleCase("ȘTEFAN"))
    }
}
