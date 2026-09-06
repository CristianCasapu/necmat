package com.necmat.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/** v1.27 — rafinări calendar/clienți (Etapa 4). */
class V27Test {

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

    private val day: LocalDate = LocalDate.of(2026, 9, 7)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = epochOf(d, LocalTime.of(h, m))

    @Test
    fun `statistica lunii`() {
        val list = listOf(
            Appointment(1, "", at(day, 9)),
            Appointment(2, "", at(day.plusDays(3), 9), status = AppointmentStatus.FINALIZAT),
            Appointment(3, "", at(day.plusDays(5), 9), status = AppointmentStatus.ANULAT),
            Appointment(4, "", at(LocalDate.of(2026, 10, 1), 9))
        )
        val s = monthStats(list, YearMonth.of(2026, 9))
        assertEquals(MonthStats(total = 3, done = 1, cancelled = 1, active = 1), s)
        assertEquals(MonthStats(1, 0, 0, 1), monthStats(list, YearMonth.of(2026, 10)))
    }

    @Test
    fun `filtrarea agendei dupa text, tip si stare`() {
        val list = listOf(
            Appointment(1, "Tablou", at(day, 9), type = AppointmentType.EXECUTIE, clientName = "Ion Exemplu", address = "Craiova"),
            Appointment(2, "", at(day, 11), type = AppointmentType.VIZITA, clientName = "Maria", notes = "aduce cheia"),
            Appointment(3, "", at(day, 13), type = AppointmentType.VIZITA, clientName = "Vasile", status = AppointmentStatus.ANULAT)
        )
        assertEquals(listOf(1L), filterAppointments(list, query = "craiova").map { it.id })
        assertEquals(listOf(2L), filterAppointments(list, query = "CHEIA").map { it.id })
        assertEquals(listOf(2L, 3L), filterAppointments(list, types = setOf(AppointmentType.VIZITA)).map { it.id })
        assertEquals(listOf(1L, 2L), filterAppointments(list, onlyActive = true).map { it.id })
        assertEquals(3, filterAppointments(list).size)
    }

    @Test
    fun `cronologia clientului combina lucrarile si programarile, recente primele`() {
        val c = Client(7, "Ion", phone = "0722111222")
        val works = listOf(Work(1, "Casa", at(day, 12), emptyList(), clientId = 7L))
        val appts = listOf(
            Appointment(10, "", at(day.plusDays(1), 9), clientId = 7L),
            Appointment(11, "", at(day.minusDays(1), 9), phone = "+40722111222"),   // legată prin telefon
            Appointment(12, "", at(day, 9), clientId = 8L)                            // alt client
        )
        val t = clientTimeline(works, appts, c)
        assertEquals(listOf(10L, null, 11L), t.map { it.appointment?.id })
        assertEquals(1L, t[1].work?.id)
    }

    @Test
    fun `exportul ics are evenimente cu ore UTC si zile intregi`() {
        val list = listOf(
            Appointment(1, "Tablou", at(day, 10), 90, clientName = "Ion, Exemplu", address = "Str. A; nr 1"),
            Appointment(2, "", at(day, 0), allDay = true, type = AppointmentType.EXECUTIE),
            Appointment(3, "", at(day, 15), status = AppointmentStatus.ANULAT)
        )
        val ics = icsFor(list, stamp = 0L)
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(ics.trimEnd().endsWith("END:VCALENDAR"))
        assertEquals(2, Regex("BEGIN:VEVENT").findAll(ics).count())          // cea anulată lipsește
        // 10:00 la București (UTC+3 în septembrie) = 07:00Z; 90 min → 08:30Z
        assertTrue(ics.contains("DTSTART:20260907T070000Z"))
        assertTrue(ics.contains("DTEND:20260907T083000Z"))
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20260907"))
        assertTrue(ics.contains("DTEND;VALUE=DATE:20260908"))
        // caracterele speciale sunt escapate
        assertTrue(ics.contains("SUMMARY:Vizită — Ion\\, Exemplu — Tablou"))
        assertTrue(ics.contains("LOCATION:Str. A\\; nr 1"))
        assertTrue(ics.contains("UID:necmat-1@necmat.app"))
        assertFalse(ics.contains("\n\n"))
    }

    @Test
    fun `numele fisierului saptamanii si paleta de culori`() {
        assertEquals("Program 07.09-13.09.2026", weekFileStem(weekOf(day)))
        assertEquals("Program 28.12-03.01.2027", weekFileStem(weekOf(LocalDate.of(2026, 12, 31))))
        TypePalette.loadJson(null)
        assertEquals(0, TypePalette.indexOf(AppointmentType.VIZITA))
        TypePalette.loadJson("""{"VIZITA":4,"OFERTA":99,"XYZ":1}""")
        assertEquals(4, TypePalette.indexOf(AppointmentType.VIZITA))
        assertEquals(3, TypePalette.indexOf(AppointmentType.OFERTA))   // index invalid → implicit
        assertTrue(TypePalette.toJson().contains("\"VIZITA\":4"))
        TypePalette.loadJson(null)
    }
}
