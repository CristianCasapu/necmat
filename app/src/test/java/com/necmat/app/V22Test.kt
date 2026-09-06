package com.necmat.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.22 — clienții ca entitate (Etapa 0). Toate datele sunt fictive. */
class V22Test {

    private var idCounter = 22_000L
    private fun nextId(): Long = idCounter++

    /** CNP fictiv valid: primele 12 cifre + cifra de control calculată. */
    private fun fakeCnp(first12: String) = first12 + cnpControlDigit(first12)

    @Test
    fun `telefonul se normalizeaza la cifre cu prefixul national`() {
        assertEquals("0722123456", normalizePhone("0722 123 456"))
        assertEquals("0722123456", normalizePhone("+40 722 123 456"))
        assertEquals("0722123456", normalizePhone("0040722123456"))
        assertEquals("0722123456", normalizePhone("0722-123-456"))
        assertEquals("", normalizePhone(""))
        assertEquals("40722123456", phoneForWhatsApp("0722 123 456"))
    }

    @Test
    fun `validarea CNP - cifra de control, lungime, data imposibila`() {
        val valid = fakeCnp("190010112345")
        assertTrue(isValidCnp(valid))
        // ultima cifră schimbată -> invalid
        val bad = valid.dropLast(1) + ((valid.last() - '0' + 1) % 10)
        assertFalse(isValidCnp(bad))
        assertFalse(isValidCnp("12345"))
        assertFalse(isValidCnp("abcdefghijklm"))
        assertFalse(isValidCnp(fakeCnp("190130112345")))  // luna 13
        assertFalse(isValidCnp(fakeCnp("190013212345")))  // ziua 32
        assertFalse(isValidCnp(fakeCnp("090010112345")))  // sex 0
        assertTrue(isValidCnp(fakeCnp("285123116187")))
    }

    @Test
    fun `validarea de forma a emailului`() {
        assertTrue(isValidEmail(""))
        assertTrue(isValidEmail("ion.popescu@example.com"))
        assertTrue(isValidEmail("a+b@sub.example.ro"))
        assertFalse(isValidEmail("fara-arond"))
        assertFalse(isValidEmail("x@y"))
        assertFalse(isValidEmail("x@@y.com"))
    }

    @Test
    fun `CNP-ul mascat arata doar ultimele 4 cifre`() {
        assertEquals("•••••••••1234", maskCnp("1990101011234"))
        assertEquals("", maskCnp("12"))
    }

    @Test
    fun `extragerea clientilor din lucrari face dedupe pe telefon`() {
        val works = listOf(
            Work(1, "Casa A", 100L, emptyList(), client = "Ion Exemplu", phone = "0722 111 222", address = "Str. A 1"),
            Work(2, "Casa A etaj", 200L, emptyList(), client = "ion exemplu", phone = "+40722111222", address = "Str. A 1"),
            Work(3, "Bloc B", 300L, emptyList(), client = "Maria Exemplu", phone = "", address = "Str. B 2"),
            Work(4, "Bloc B revizie", 400L, emptyList(), client = "MARIA EXEMPLU", phone = "", address = "str. b 2"),
            Work(5, "Fără client", 500L, emptyList())
        )
        val (clients, linked) = clientsFromWorks(works, emptyList(), ::nextId, now = 999L)
        assertEquals(2, clients.size)
        assertEquals("Ion Exemplu", clients[0].name)
        assertEquals("Maria Exemplu", clients[1].name)
        // lucrările cu același client primesc același id
        assertEquals(linked[0].clientId, linked[1].clientId)
        assertEquals(linked[2].clientId, linked[3].clientId)
        assertNull(linked[4].clientId)
        // idempotent
        val (again, _) = clientsFromWorks(linked, clients, ::nextId)
        assertEquals(clients, again)
    }

    @Test
    fun `duplicatul se gaseste dupa telefon sau nume plus adresa`() {
        val list = listOf(
            Client(1, "Ion Exemplu", phone = "0722111222", address = "Str. A 1"),
            Client(2, "Maria Exemplu", phone = "", address = "Str. B 2")
        )
        assertEquals(1L, findDuplicateClient(Client(0, "Altcineva", phone = "+40 722 111 222"), list)?.id)
        assertEquals(2L, findDuplicateClient(Client(0, "maria exemplu", address = "STR. B 2"), list)?.id)
        assertNull(findDuplicateClient(Client(0, "Maria Exemplu", address = "Altă adresă"), list))
        // clientul însuși nu e propriul duplicat
        assertNull(findDuplicateClient(list[0], list))
    }

    @Test
    fun `sugestiile cauta dupa nume si telefon`() {
        val list = listOf(
            Client(1, "Ion Exemplu", phone = "0722111222"),
            Client(2, "Maria Exemplu", phone = "0733444555"),
            Client(3, "Vasile Altul", phone = "")
        )
        assertEquals(listOf(1L, 2L), suggestClients("exem", list).map { it.id })
        assertEquals(listOf(2L), suggestClients("0733", list).map { it.id })
        assertTrue(suggestClients("i", list).isEmpty())   // prea scurt
        assertEquals(listOf(3L), suggestClients("altul", list).map { it.id })
    }

    @Test
    fun `stergerea clientului lasa lucrarile intacte fara legatura`() {
        val works = listOf(
            Work(1, "A", 0L, emptyList(), client = "Ion", clientId = 7L),
            Work(2, "B", 0L, emptyList(), client = "Maria", clientId = 8L)
        )
        val out = unlinkClient(works, 7L)
        assertNull(out[0].clientId)
        assertEquals("Ion", out[0].client)
        assertEquals(8L, out[1].clientId)
    }

    @Test
    fun `lucrarile unui client se gasesc prin id sau telefon`() {
        val c = Client(7, "Ion", phone = "0722111222")
        val works = listOf(
            Work(1, "A", 0L, emptyList(), clientId = 7L),
            Work(2, "B", 0L, emptyList(), phone = "+40722111222"),
            Work(3, "C", 0L, emptyList(), phone = "0733000000")
        )
        assertEquals(listOf(1L, 2L), worksOfClient(works, c).map { it.id })
    }

    @Test
    fun `json dus-intors client si lucrare cu clientId`() {
        val c = Client(5, "Ion Exemplu", "0722", "Str. A", "ion@example.com", fakeCnp("190010112345"), "notă", 10L, 20L)
        assertEquals(c, ClientsRepo.fromJson(ClientsRepo.toJson(c)))
        val minimal = ClientsRepo.fromJson(org.json.JSONObject().put("id", 9).put("name", "X"))
        assertEquals("", minimal.email)
        assertEquals("", minimal.cnp)

        val w = Work(1, "Casa", 0L, emptyList(), clientId = 5L)
        val parsed = Repo.parseBackup(Repo.backupJson(emptyList(), listOf(w), clients = listOf(c)))!!
        assertEquals(5L, parsed.works[0].clientId)
        assertEquals(listOf(c), parsed.clients)
    }

    @Test
    fun `backup v3 fara clienti se restaureaza cu lista goala`() {
        val json = Repo.backupJson(emptyList(), listOf(Work(1, "Casa", 0L, emptyList(), client = "Ion", phone = "0722")))
        val parsed = Repo.parseBackup(json)!!
        assertTrue(parsed.clients.isEmpty())
        assertNull(parsed.works[0].clientId)
        // după migrare, clientul apare
        val (clients, _) = clientsFromWorks(parsed.works, emptyList(), ::nextId)
        assertEquals(1, clients.size)
    }

    @Test
    fun `lucrarea nu transporta niciodata CNP sau email`() {
        val w = Work(1, "Casa", 0L, emptyList(), client = "Ion", clientId = 5L)
        val json = Repo.backupJson(emptyList(), listOf(w))
        val workJson = org.json.JSONObject(json).getJSONArray("works").getJSONObject(0)
        assertFalse(workJson.has("cnp"))
        assertFalse(workJson.has("email"))
        assertNotNull(workJson.opt("clientId"))
    }

    @Test
    fun `localitatea se ia din ultimul segment al adresei`() {
        assertEquals("Craiova", Client(1, "X", address = "Str. A nr. 1, Craiova").locality)
        assertEquals("Str. A", Client(1, "X", address = "Str. A").locality)
        assertEquals("", Client(1, "X").locality)
    }
}
