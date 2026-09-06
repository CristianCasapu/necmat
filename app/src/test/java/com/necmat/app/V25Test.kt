package com.necmat.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/** v1.25 — grila lunară și săptămânală (Etapa 2). */
class V25Test {

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

    @Test
    fun `grila pentru februarie an bisect`() {
        val g = monthGrid(YearMonth.of(2028, 2))
        assertEquals(6, g.size)
        assertTrue(g.all { it.size == 7 })
        // 1 februarie 2028 e marți → grila începe luni 31 ianuarie
        assertEquals(LocalDate.of(2028, 1, 31), g[0][0])
        assertEquals(DayOfWeek.MONDAY, g[0][0].dayOfWeek)
        assertEquals(LocalDate.of(2028, 2, 1), g[0][1])
        // 29 februarie există și e în grilă
        assertTrue(g.flatten().contains(LocalDate.of(2028, 2, 29)))
        // ultima celulă continuă în martie
        assertEquals(LocalDate.of(2028, 3, 12), g[5][6])
    }

    @Test
    fun `luna care incepe duminica are 6 zile din luna trecuta pe primul rand`() {
        // 1 martie 2026 e duminică
        val g = monthGrid(YearMonth.of(2026, 3))
        assertEquals(LocalDate.of(2026, 2, 23), g[0][0])
        assertEquals(LocalDate.of(2026, 3, 1), g[0][6])
        assertEquals(6, g[0].count { it.monthValue == 2 })
    }

    @Test
    fun `trecerea decembrie - ianuarie si saptamana`() {
        val g = monthGrid(YearMonth.of(2026, 12))
        assertTrue(g.flatten().any { it.year == 2027 && it.monthValue == 1 })
        // săptămâna care conține joi 31 decembrie 2026: luni 28.12 → duminică 03.01.2027
        val w = weekOf(LocalDate.of(2026, 12, 31))
        assertEquals(LocalDate.of(2026, 12, 28), w.first())
        assertEquals(LocalDate.of(2027, 1, 3), w.last())
        assertEquals(7, w.size)
        // duminica aparține săptămânii care începe luni
        assertEquals(LocalDate.of(2026, 9, 7), weekOf(LocalDate.of(2026, 9, 13)).first())
    }

    @Test
    fun `rezumatul zilelor numara programarile active inclusiv toata ziua`() {
        val d = LocalDate.of(2026, 9, 7)
        fun at(h: Int) = epochOf(d, LocalTime.of(h, 0))
        val list = listOf(
            Appointment(1, "", at(9), 60, type = AppointmentType.VIZITA),
            Appointment(2, "", at(11), 120, type = AppointmentType.EXECUTIE),
            Appointment(3, "", at(0), allDay = true, type = AppointmentType.EXECUTIE),
            Appointment(4, "", at(15), 60, status = AppointmentStatus.ANULAT),
            Appointment(5, "", epochOf(d.plusDays(1), LocalTime.of(9, 0)), 60)
        )
        val s = daySummaries(list, weekOf(d))
        val today = s[d]!!
        assertEquals(3, today.count)                      // cea anulată nu se numără
        assertEquals(AppointmentType.EXECUTIE, today.dominant)
        assertEquals(60 + 120 + 8 * 60, today.busyMinutes)
        assertTrue(today.isFull)
        assertEquals(1, s[d.plusDays(1)]!!.count)
        assertFalse(s[d.plusDays(1)]!!.isFull)
        assertNull(s[d.plusDays(2)])
    }

    @Test
    fun `vizualizarea se citeste tolerant`() {
        assertEquals(CalendarView.MONTH, CalendarView.parse("MONTH"))
        assertEquals(CalendarView.AGENDA, CalendarView.parse(null))
        assertEquals(CalendarView.AGENDA, CalendarView.parse("altceva"))
    }
}
