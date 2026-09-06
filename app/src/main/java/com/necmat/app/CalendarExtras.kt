package com.necmat.app

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// ------------------------------------------------------------------ Etapa 4: rafinări (v1.27)

/** Statistica unei luni: câte programări, câte finalizate, câte anulate, câte active. */
data class MonthStats(val total: Int, val done: Int, val cancelled: Int, val active: Int)

fun monthStats(list: List<Appointment>, month: YearMonth): MonthStats {
    val inMonth = list.filter { YearMonth.from(toLocalDate(it.start)) == month }
    return MonthStats(
        total = inMonth.size,
        done = inMonth.count { it.isDone },
        cancelled = inMonth.count { it.isCancelled },
        active = inMonth.count { it.isActive }
    )
}

/** Filtrare pentru Agendă: text (client, titlu, adresă, notițe), tipuri, doar active. */
fun filterAppointments(
    list: List<Appointment>,
    query: String = "",
    types: Set<AppointmentType> = emptySet(),
    onlyActive: Boolean = false
): List<Appointment> {
    val q = normalizeName(query)
    return list.filter { a ->
        (types.isEmpty() || a.type in types) &&
            (!onlyActive || a.isActive) &&
            (q.isEmpty() || listOf(a.clientName, a.title, a.address, a.notes).any { normalizeName(it).contains(q) })
    }
}

/** Un rând din cronologia clientului: o lucrare sau o programare. */
data class TimelineItem(val time: Long, val work: Work? = null, val appointment: Appointment? = null)

/** Lucrările și programările unui client, cele mai recente primele. */
fun clientTimeline(works: List<Work>, appointments: List<Appointment>, client: Client): List<TimelineItem> {
    val phone = normalizePhone(client.phone)
    val appts = appointments.filter { a ->
        a.clientId == client.id || (a.clientId == null && phone.isNotEmpty() && normalizePhone(a.phone) == phone)
    }
    return (worksOfClient(works, client).map { TimelineItem(it.date, work = it) } +
        appts.map { TimelineItem(it.start, appointment = it) })
        .sortedByDescending { it.time }
}

// ------------------------------------------------------------------ export iCalendar (.ics)

private val icsUtc: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
private val icsDate: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

private fun icsEscape(s: String): String =
    s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

/** Fișier iCalendar cu programările active (importabil în Google Calendar, Outlook etc.). */
fun icsFor(list: List<Appointment>, stamp: Long = System.currentTimeMillis()): String {
    val sb = StringBuilder()
    sb.append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//NecMat//RO\r\nCALSCALE:GREGORIAN\r\n")
    val now = Instant.ofEpochMilli(stamp).atOffset(ZoneOffset.UTC).format(icsUtc)
    list.filter { it.isActive }.sortedBy { it.start }.forEach { a ->
        sb.append("BEGIN:VEVENT\r\n")
        sb.append("UID:necmat-").append(a.id).append("@necmat.app\r\n")
        sb.append("DTSTAMP:").append(now).append("\r\n")
        if (a.allDay) {
            val d = toLocalDate(a.start)
            sb.append("DTSTART;VALUE=DATE:").append(d.format(icsDate)).append("\r\n")
            sb.append("DTEND;VALUE=DATE:").append(d.plusDays(1).format(icsDate)).append("\r\n")
        } else {
            sb.append("DTSTART:").append(Instant.ofEpochMilli(a.start).atOffset(ZoneOffset.UTC).format(icsUtc)).append("\r\n")
            sb.append("DTEND:").append(Instant.ofEpochMilli(a.end).atOffset(ZoneOffset.UTC).format(icsUtc)).append("\r\n")
        }
        val summary = listOf(a.type.label, a.clientName, a.title).filter { it.isNotBlank() }.joinToString(" — ")
        sb.append("SUMMARY:").append(icsEscape(summary)).append("\r\n")
        if (a.address.isNotBlank()) sb.append("LOCATION:").append(icsEscape(a.address)).append("\r\n")
        val desc = listOf(a.notes, if (a.phone.isNotBlank()) "Tel.: ${a.phone}" else "").filter { it.isNotBlank() }.joinToString("\n")
        if (desc.isNotBlank()) sb.append("DESCRIPTION:").append(icsEscape(desc)).append("\r\n")
        sb.append("STATUS:").append(if (a.status == AppointmentStatus.CONFIRMAT) "CONFIRMED" else "TENTATIVE").append("\r\n")
        sb.append("END:VEVENT\r\n")
    }
    sb.append("END:VCALENDAR\r\n")
    return sb.toString()
}

/** Numele fișierului pentru exportul unei săptămâni: „Program 07.09-13.09.2026”. */
fun weekFileStem(days: List<LocalDate>): String {
    val a = days.first()
    val b = days.last()
    fun dm(d: LocalDate) = "${d.dayOfMonth.toString().padStart(2, '0')}.${d.monthValue.toString().padStart(2, '0')}"
    return "Program ${dm(a)}-${dm(b)}.${b.year}"
}

// ------------------------------------------------------------------ culori configurabile pe tip

object TypePalette {
    /** Paleta din care se aleg culorile tipurilor (index stabil, salvat în setări). */
    val colors: List<Long> = listOf(
        0xFF183E6E, // albastru NecMat
        0xFF2E7D32, // verde
        0xFFEF6C00, // portocaliu
        0xFF6A1B9A, // mov
        0xFFC62828, // roșu
        0xFF00838F, // turcoaz
        0xFF5D4037, // maro
        0xFF546E7A  // gri-albastru
    )

    val defaults: Map<AppointmentType, Int> = mapOf(
        AppointmentType.VIZITA to 0,
        AppointmentType.OFERTA to 3,
        AppointmentType.EXECUTIE to 1,
        AppointmentType.REVIZIE to 2,
        AppointmentType.ALTELE to 7
    )

    /** Suprascrierile utilizatorului (tip → index); se populează din setări. */
    val overrides = androidx.compose.runtime.mutableStateMapOf<AppointmentType, Int>()

    fun indexOf(t: AppointmentType): Int = overrides[t] ?: defaults[t] ?: 7

    fun toJson(): String = org.json.JSONObject().apply {
        overrides.forEach { (t, i) -> put(t.name, i) }
    }.toString()

    fun loadJson(json: String?) {
        overrides.clear()
        if (json.isNullOrBlank()) return
        try {
            val o = org.json.JSONObject(json)
            o.keys().forEach { k ->
                AppointmentType.entries.firstOrNull { it.name == k }?.let { t ->
                    val i = o.optInt(k, -1)
                    if (i in colors.indices) overrides[t] = i
                }
            }
        } catch (_: Exception) {
        }
    }
}
