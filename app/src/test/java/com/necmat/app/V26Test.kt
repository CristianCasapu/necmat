package com.necmat.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** v1.26 — remindere locale (Etapa 3): logica pură, fără Android. */
class V26Test {

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
    private fun at(h: Int, m: Int = 0) = epochOf(day, LocalTime.of(h, m))

    @Test
    fun `momentul reminderului - implicit, personalizat, fara, inactiv`() {
        val a = Appointment(1, "", at(10))
        assertEquals(at(9), reminderTime(a, 60))
        assertEquals(at(9, 45), reminderTime(a.copy(reminderMin = 15), 60))
        assertNull(reminderTime(a.copy(reminderMin = 0), 60))
        assertNull(reminderTime(a, 0))
        assertNull(reminderTime(a.copy(status = AppointmentStatus.ANULAT), 60))
        assertNull(reminderTime(a.copy(status = AppointmentStatus.FINALIZAT), 60))
        assertEquals(at(9), reminderTime(a.copy(status = AppointmentStatus.CONFIRMAT), 60))
    }

    @Test
    fun `se programeaza doar reminderele viitoare, cronologic si limitat`() {
        val now = at(9, 30)
        val list = listOf(
            Appointment(1, "", at(10)),          // reminder 9:00 → trecut
            Appointment(2, "", at(12)),          // 11:00
            Appointment(3, "", at(11)),          // 10:00
            Appointment(4, "", at(15), status = AppointmentStatus.ANULAT)
        )
        val s = remindersToSchedule(list, 60, now)
        assertEquals(listOf(3L, 2L), s.map { it.appointment.id })
        assertEquals(at(10), s[0].at)
        assertEquals(listOf(3L), remindersToSchedule(list, 60, now, limit = 1).map { it.appointment.id })
    }

    @Test
    fun `textele notificarii`() {
        val a = Appointment(1, "", at(10), 60, clientName = "Ion Exemplu", address = "Str. A 1", type = AppointmentType.VIZITA)
        assertEquals("Vizită — Ion Exemplu", reminderTitle(a))
        assertEquals("10:00–11:00 · Str. A 1 · în 1 h", reminderText(a, at(9)))
        assertEquals("10:00–11:00 · Str. A 1 · în 45 min", reminderText(a, at(9, 15)))
        assertEquals("10:00–11:00 · Str. A 1 · în 1 h 30 min", reminderText(a, at(8, 30)))
        assertEquals("Execuție", reminderTitle(Appointment(2, "", at(8), type = AppointmentType.EXECUTIE)))
        // toată ziua: fără „în N min”
        assertEquals("toată ziua", reminderText(Appointment(3, "", at(0), allDay = true), at(0)))
    }

    @Test
    fun `rezumatul de dimineata`() {
        assertNull(morningSummaryText(emptyList(), day))
        val one = listOf(Appointment(1, "", at(10), clientName = "Ion", address = "Str. A"))
        assertEquals("Azi ai o programare: prima la 10:00 — Ion, Str. A", morningSummaryText(one, day))
        val many = one + Appointment(2, "", at(14), clientName = "Maria") +
            Appointment(3, "", at(8), status = AppointmentStatus.ANULAT) +
            Appointment(4, "", epochOf(day.plusDays(1), LocalTime.of(9, 0)))
        assertEquals("Azi ai 2 programări: prima la 10:00 — Ion, Str. A", morningSummaryText(many, day))
        val allDay = listOf(Appointment(5, "", at(0), allDay = true, clientName = "Vasile"))
        assertEquals("Azi ai o programare: toată ziua — Vasile", morningSummaryText(allDay, day))
    }

    @Test
    fun `urmatorul rezumat de dimineata este azi sau maine`() {
        assertEquals(at(7), nextMorningTrigger(at(6, 59), 7))
        assertEquals(epochOf(day.plusDays(1), LocalTime.of(7, 0)), nextMorningTrigger(at(7), 7))
        assertEquals(epochOf(day.plusDays(1), LocalTime.of(7, 0)), nextMorningTrigger(at(19), 7))
        assertEquals(at(23), nextMorningTrigger(at(22), 99))   // ora se limitează la 23
    }

    @Test
    fun `reminderul per programare trece prin json si lipsa cheii inseamna implicit`() {
        val a = Appointment(1, "", at(10), reminderMin = 15)
        assertEquals(15, AppointmentsRepo.fromJson(AppointmentsRepo.toJson(a)).reminderMin)
        val old = AppointmentsRepo.fromJson(org.json.JSONObject().put("id", 1).put("start", at(10)))
        assertEquals(REMINDER_USE_DEFAULT, old.reminderMin)
        assertTrue(reminderTime(old, 30) == at(9, 30))
    }
}
