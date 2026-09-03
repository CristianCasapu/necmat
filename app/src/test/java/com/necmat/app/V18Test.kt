package com.necmat.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class V18Test {

    @Test
    fun `adresa se compune din strada numar si localitate`() {
        val json = """{"address":{"road":"Strada Teiului","house_number":"5",
            "city":"Craiova","country":"România"}}"""
        assertEquals("Strada Teiului 5, Craiova", LocationHelper.addressFromNominatim(json))
    }

    @Test
    fun `fara numar de casa ramane doar strada si localitatea`() {
        val json = """{"address":{"road":"Strada Unirii","village":"Maglavit"}}"""
        assertEquals("Strada Unirii, Maglavit", LocationHelper.addressFromNominatim(json))
    }

    @Test
    fun `fara strada se foloseste doar localitatea`() {
        val json = """{"address":{"town":"Calafat"}}"""
        assertEquals("Calafat", LocationHelper.addressFromNominatim(json))
    }

    @Test
    fun `raspunsurile invalide dau null`() {
        assertNull(LocationHelper.addressFromNominatim("nu e json"))
        assertNull(LocationHelper.addressFromNominatim("""{"error":"Unable to geocode"}"""))
        assertNull(LocationHelper.addressFromNominatim("""{"address":{}}"""))
    }
}
