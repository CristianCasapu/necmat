package com.necmat.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** Tipul programării — determină culoarea și acțiunile sugerate. */
enum class AppointmentType(val label: String) {
    VIZITA("Vizită"),
    OFERTA("Ofertă"),
    EXECUTIE("Execuție"),
    REVIZIE("Revizie"),
    ALTELE("Altele");

    companion object {
        fun parse(s: String?): AppointmentType = entries.firstOrNull { it.name == s } ?: ALTELE
    }
}

enum class AppointmentStatus(val label: String) {
    PROGRAMAT("Programat"),
    CONFIRMAT("Confirmat"),
    FINALIZAT("Finalizat"),
    ANULAT("Anulat");

    companion object {
        fun parse(s: String?): AppointmentStatus = entries.firstOrNull { it.name == s } ?: PROGRAMAT
    }
}

/**
 * O programare la client (vizită, ofertă, execuție, revizie). `start` e în
 * milisecunde de la epocă; ziua se calculează în fusul orar al telefonului.
 */
data class Appointment(
    val id: Long,
    val title: String,
    val start: Long,
    val durationMin: Int = 60,
    val allDay: Boolean = false,
    val type: AppointmentType = AppointmentType.VIZITA,
    val status: AppointmentStatus = AppointmentStatus.PROGRAMAT,
    val clientId: Long? = null,
    val clientName: String = "",
    val address: String = "",
    val phone: String = "",
    val workId: Long? = null,
    val notes: String = "",
    val createdAt: Long = 0L
) {
    val end: Long get() = if (allDay) startOfDay(start) + DAY_MS else start + durationMin * 60_000L
    val isActive: Boolean get() = status == AppointmentStatus.PROGRAMAT || status == AppointmentStatus.CONFIRMAT
    val isDone: Boolean get() = status == AppointmentStatus.FINALIZAT
    val isCancelled: Boolean get() = status == AppointmentStatus.ANULAT
}

const val DAY_MS = 24 * 60 * 60 * 1000L

var appZone: ZoneId = ZoneId.systemDefault()

fun toLocalDate(epochMs: Long): LocalDate =
    Instant.ofEpochMilli(epochMs).atZone(appZone).toLocalDate()

fun toLocalDateTime(epochMs: Long): LocalDateTime =
    Instant.ofEpochMilli(epochMs).atZone(appZone).toLocalDateTime()

fun startOfDay(epochMs: Long): Long =
    toLocalDate(epochMs).atStartOfDay(appZone).toInstant().toEpochMilli()

fun epochOf(date: LocalDate, time: LocalTime): Long =
    date.atTime(time).atZone(appZone).toInstant().toEpochMilli()

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

fun formatTime(epochMs: Long): String = toLocalDateTime(epochMs).format(timeFmt)
fun formatDate(epochMs: Long): String = toLocalDate(epochMs).format(dateFmt)

private val roDays = listOf("Luni", "Marți", "Miercuri", "Joi", "Vineri", "Sâmbătă", "Duminică")
private val roMonths = listOf(
    "ianuarie", "februarie", "martie", "aprilie", "mai", "iunie",
    "iulie", "august", "septembrie", "octombrie", "noiembrie", "decembrie"
)

/** „Luni, 8 septembrie” (fără an) sau cu an dacă e alt an decât cel curent. */
fun formatDayLong(date: LocalDate, today: LocalDate): String {
    val base = "${roDays[date.dayOfWeek.value - 1]}, ${date.dayOfMonth} ${roMonths[date.monthValue - 1]}"
    return if (date.year == today.year) base else "$base ${date.year}"
}

fun monthLabel(monthValue: Int): String = roMonths[monthValue - 1]

/** Intervalul programării ca text: „10:00–11:30” sau „toată ziua”. */
fun Appointment.timeLabel(): String =
    if (allDay) "toată ziua" else "${formatTime(start)}–${formatTime(end)}"

/** Două programări se suprapun dacă intervalele lor se intersectează (adiacente = nu). */
fun overlaps(a: Appointment, b: Appointment): Boolean =
    a.start < b.end && b.start < a.end

/** Programările active care se suprapun cu cea dată (fără ea însăși și fără cele anulate). */
fun conflictsOf(appt: Appointment, list: List<Appointment>): List<Appointment> =
    list.filter { it.id != appt.id && !it.isCancelled && overlaps(appt, it) }

/** Grupele agendei, în ordinea de afișare. */
enum class AgendaBucket(val label: String) {
    OVERDUE("Trecute, nefinalizate"),
    TODAY("Azi"),
    TOMORROW("Mâine"),
    THIS_WEEK("Săptămâna aceasta"),
    LATER("Mai târziu"),
    DONE("Finalizate / anulate")
}

/** Grupează programările pentru vizualizarea „Agendă”, sortate cronologic. */
fun groupForAgenda(list: List<Appointment>, now: Long): Map<AgendaBucket, List<Appointment>> {
    val today = toLocalDate(now)
    val tomorrow = today.plusDays(1)
    val endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val out = linkedMapOf<AgendaBucket, MutableList<Appointment>>()
    list.sortedBy { it.start }.forEach { a ->
        val day = toLocalDate(a.start)
        val bucket = when {
            !a.isActive -> AgendaBucket.DONE
            day.isBefore(today) -> AgendaBucket.OVERDUE
            day == today -> AgendaBucket.TODAY
            day == tomorrow -> AgendaBucket.TOMORROW
            !day.isAfter(endOfWeek) -> AgendaBucket.THIS_WEEK
            else -> AgendaBucket.LATER
        }
        out.getOrPut(bucket) { mutableListOf() } += a
    }
    // finalizatele/anulatele — cele mai recente primele
    out[AgendaBucket.DONE]?.sortByDescending { it.start }
    return AgendaBucket.entries.filter { it in out }.associateWith { out[it]!!.toList() }
}

/** Programările dintr-o zi (după data locală de început), sortate. */
fun appointmentsOn(list: List<Appointment>, date: LocalDate): List<Appointment> =
    list.filter { toLocalDate(it.start) == date }.sortedWith(compareBy({ !it.allDay }, { it.start }))

/**
 * Următoarea programare activă: prima care începe după momentul dat; dacă nu
 * există, cea în desfășurare (ex. o zi întreagă de execuție).
 */
fun nextUpcoming(list: List<Appointment>, now: Long): Appointment? {
    val active = list.filter { it.isActive && it.end > now }
    return active.filter { it.start > now }.minByOrNull { it.start }
        ?: active.minByOrNull { it.start }
}

const val DEFAULT_CONFIRM_TEMPLATE =
    "Bună ziua{nume}, vă confirm {tip} în data de {data}, ora {ora}. Cu stimă, {instalator}"

/**
 * Mesajul de confirmare trimis clientului. Înlocuiește {nume} (cu virgulă și
 * spațiu dacă există), {tip}, {data}, {ora}, {adresa}, {instalator}.
 */
fun confirmationMessage(template: String, a: Appointment, installer: String): String {
    val tip = when (a.type) {
        AppointmentType.VIZITA -> "vizita"
        AppointmentType.OFERTA -> "întâlnirea pentru ofertă"
        AppointmentType.EXECUTIE -> "începerea lucrării"
        AppointmentType.REVIZIE -> "revizia"
        AppointmentType.ALTELE -> "programarea"
    }
    return template
        .replace("{nume}", if (a.clientName.isBlank()) "" else ", ${a.clientName}")
        .replace("{tip}", tip)
        .replace("{data}", formatDate(a.start))
        .replace("{ora}", if (a.allDay) "—" else formatTime(a.start))
        .replace("{adresa}", a.address)
        .replace("{instalator}", installer)
        .replace(Regex(" +"), " ")
        .replace(" ,", ",")
        .trim()
}

// ------------------------------------------------------------------ persistență

object AppointmentsRepo {
    private const val FILE = "necmat_appointments.json"

    fun toJson(a: Appointment): JSONObject = JSONObject()
        .put("id", a.id).put("title", a.title).put("start", a.start)
        .put("durationMin", a.durationMin).put("allDay", a.allDay)
        .put("type", a.type.name).put("status", a.status.name)
        .put("clientName", a.clientName).put("address", a.address).put("phone", a.phone)
        .put("notes", a.notes).put("createdAt", a.createdAt)
        .apply {
            a.clientId?.let { put("clientId", it) }
            a.workId?.let { put("workId", it) }
        }

    fun fromJson(o: JSONObject): Appointment = Appointment(
        id = o.getLong("id"),
        title = o.optString("title", ""),
        start = o.getLong("start"),
        durationMin = o.optInt("durationMin", 60),
        allDay = o.optBoolean("allDay", false),
        type = AppointmentType.parse(o.optString("type")),
        status = AppointmentStatus.parse(o.optString("status")),
        clientId = if (o.has("clientId") && !o.isNull("clientId")) o.getLong("clientId") else null,
        clientName = o.optString("clientName", ""),
        address = o.optString("address", ""),
        phone = o.optString("phone", ""),
        workId = if (o.has("workId") && !o.isNull("workId")) o.getLong("workId") else null,
        notes = o.optString("notes", ""),
        createdAt = o.optLong("createdAt", 0L)
    )

    fun listToJson(list: List<Appointment>): JSONArray {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        return arr
    }

    fun listFromJson(arr: JSONArray?): List<Appointment> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { runCatching { fromJson(it) }.getOrNull() }
        }
    }

    fun load(context: Context): List<Appointment> {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return emptyList()
        return try {
            listFromJson(JSONArray(f.readText()))
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, list: List<Appointment>) {
        File(context.filesDir, FILE).writeText(listToJson(list).toString())
    }
}
