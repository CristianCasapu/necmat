package com.necmat.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** v1.24 — programări și agenda (Etapa 1). Fus orar fix ca testele să nu depindă de mașină. */
class V24Test {

    private lateinit var savedZone: ZoneId

    @Before
    fun setup() {
        savedZone = appZone
        appZone = ZoneId.of("Europe/Bucharest")
    }

    @After
    fun teardown() {
        appZone = savedZone
    }

    private fun at(date: LocalDate, h: Int, m: Int = 0) = epochOf(date, LocalTime.of(h, m))
    private val monday: LocalDate = LocalDate.of(2026, 9, 7)   // luni
    private fun appt(id: Long, start: Long, dur: Int = 60, status: AppointmentStatus = AppointmentStatus.PROGRAMAT, allDay: Boolean = false) =
        Appointment(id, "Test $id", start, dur, allDay = allDay, status = status)

    @Test
    fun `suprapunerile - aceeasi ora, adiacente, toata ziua`() {
        val a = appt(1, at(monday, 10))            // 10:00–11:00
        val b = appt(2, at(monday, 10, 30))        // 10:30–11:30 → se suprapune
        val c = appt(3, at(monday, 11))            // 11:00–12:00 → adiacent, nu se suprapune
        val d = appt(4, at(monday, 0), allDay = true)
        assertTrue(overlaps(a, b))
        assertFalse(overlaps(a, c))
        assertTrue(overlaps(a, d))                 // toată ziua acoperă orice interval din zi
        assertEquals(listOf(2L), conflictsOf(a, listOf(a, b, c)).map { it.id })
        // cele anulate nu intră în conflict
        val cancelled = b.copy(status = AppointmentStatus.ANULAT)
        assertTrue(conflictsOf(a, listOf(a, cancelled, c)).isEmpty())
    }

    @Test
    fun `gruparea agendei - azi, maine, saptamana, mai tarziu, trecute, finalizate`() {
        val now = at(monday, 9)
        val list = listOf(
            appt(1, at(monday, 14)),                          // azi
            appt(2, at(monday.plusDays(1), 8)),               // mâine
            appt(3, at(monday.plusDays(6), 8)),               // duminică → săptămâna aceasta
            appt(4, at(monday.plusDays(7), 8)),               // luni viitoare → mai târziu
            appt(5, at(monday.minusDays(1), 8)),              // ieri, neterminată → trecute
            appt(6, at(monday.minusDays(2), 8), status = AppointmentStatus.FINALIZAT),
            appt(7, at(monday, 7))                            // azi dimineață, deja trecută ca oră → tot azi
        )
        val g = groupForAgenda(list, now)
        assertEquals(listOf(5L), g[AgendaBucket.OVERDUE]!!.map { it.id })
        assertEquals(listOf(7L, 1L), g[AgendaBucket.TODAY]!!.map { it.id })
        assertEquals(listOf(2L), g[AgendaBucket.TOMORROW]!!.map { it.id })
        assertEquals(listOf(3L), g[AgendaBucket.THIS_WEEK]!!.map { it.id })
        assertEquals(listOf(4L), g[AgendaBucket.LATER]!!.map { it.id })
        assertEquals(listOf(6L), g[AgendaBucket.DONE]!!.map { it.id })
        // ordinea grupelor e cea de afișare
        assertEquals(
            listOf(AgendaBucket.OVERDUE, AgendaBucket.TODAY, AgendaBucket.TOMORROW,
                AgendaBucket.THIS_WEEK, AgendaBucket.LATER, AgendaBucket.DONE),
            g.keys.toList()
        )
    }

    @Test
    fun `gruparea la miezul noptii si la schimbarea lunii`() {
        val lastDay = LocalDate.of(2026, 9, 30)
        val now = at(lastDay, 23, 59)
        val g = groupForAgenda(listOf(appt(1, at(LocalDate.of(2026, 10, 1), 0, 30))), now)
        assertEquals(listOf(1L), g[AgendaBucket.TOMORROW]!!.map { it.id })
        assertEquals(LocalDate.of(2026, 10, 1), toLocalDate(at(LocalDate.of(2026, 10, 1), 0, 30)))
    }

    @Test
    fun `programarile unei zile si urmatoarea programare`() {
        val list = listOf(
            appt(1, at(monday, 15)),
            appt(2, at(monday, 9)),
            appt(3, at(monday, 0), allDay = true),
            appt(4, at(monday.plusDays(1), 9))
        )
        assertEquals(listOf(3L, 2L, 1L), appointmentsOn(list, monday).map { it.id })   // toată ziua prima
        assertEquals(1L, nextUpcoming(list, at(monday, 10, 30))?.id)
        assertEquals(4L, nextUpcoming(list, at(monday, 16, 30))?.id)
        assertNull(nextUpcoming(list, at(monday.plusDays(2), 0)))
    }

    @Test
    fun `mesajul de confirmare inlocuieste variabilele`() {
        val a = Appointment(1, "Vizită", at(monday, 10), 60, clientName = "Ion Exemplu", address = "Str. A 1")
        val msg = confirmationMessage(DEFAULT_CONFIRM_TEMPLATE, a, "Electro Exemplu")
        assertEquals(
            "Bună ziua, Ion Exemplu, vă confirm vizita în data de 07.09.2026, ora 10:00. Cu stimă, Electro Exemplu",
            msg
        )
        // fără nume, virgula dispare; toată ziua → ora „—”
        val b = a.copy(clientName = "", allDay = true, type = AppointmentType.EXECUTIE)
        assertEquals(
            "Bună ziua, vă confirm începerea lucrării în data de 07.09.2026, ora —. Cu stimă, Electro Exemplu",
            confirmationMessage(DEFAULT_CONFIRM_TEMPLATE, b, "Electro Exemplu")
        )
        assertEquals("Str. A 1", confirmationMessage("{adresa}", a, ""))
    }

    @Test
    fun `etichetele de timp si zi`() {
        val a = appt(1, at(monday, 10, 15), 90)
        assertEquals("10:15–11:45", a.timeLabel())
        assertEquals("toată ziua", a.copy(allDay = true).timeLabel())
        assertEquals("Luni, 7 septembrie", formatDayLong(monday, monday))
        assertEquals("Luni, 7 septembrie 2026", formatDayLong(monday, monday.plusYears(1)))
        assertEquals("07.09.2026", formatDate(at(monday, 10)))
    }

    @Test
    fun `json dus-intors si compatibilitate cu chei lipsa`() {
        val a = Appointment(
            5, "Vizită", at(monday, 10), 45, false, AppointmentType.OFERTA, AppointmentStatus.CONFIRMAT,
            clientId = 7L, clientName = "Ion", address = "Str. A", phone = "0722", workId = 9L,
            notes = "aduce cheia", createdAt = 1L
        )
        assertEquals(a, AppointmentsRepo.fromJson(AppointmentsRepo.toJson(a)))
        val minimal = AppointmentsRepo.fromJson(org.json.JSONObject().put("id", 1).put("start", 0L))
        assertNull(minimal.clientId)
        assertEquals(AppointmentType.ALTELE, minimal.type)
        assertEquals(AppointmentStatus.PROGRAMAT, minimal.status)
        assertEquals(60, minimal.durationMin)
        assertTrue(AppointmentsRepo.listFromJson(null).isEmpty())
        // tipuri necunoscute (versiuni viitoare) nu strică citirea
        val weird = AppointmentsRepo.fromJson(org.json.JSONObject().put("id", 2).put("start", 0L).put("type", "XYZ"))
        assertEquals(AppointmentType.ALTELE, weird.type)
    }

    @Test
    fun `backupul include programarile`() {
        val a = Appointment(5, "Vizită", at(monday, 10), clientName = "Ion")
        val json = Repo.backupJson(emptyList(), emptyList(), appointments = listOf(a))
        val parsed = Repo.parseBackup(json)!!
        assertEquals(listOf(a), parsed.appointments)
        // backup v3/v4 fără programări → listă goală
        assertTrue(Repo.parseBackup(Repo.backupJson(emptyList(), emptyList()))!!.appointments.isEmpty())
    }
}
