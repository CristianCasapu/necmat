package com.necmat.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalTime

// ------------------------------------------------------------------ logică pură (testabilă)

/** Valoarea „folosește reminderul implicit din Setări” pentru [Appointment.reminderMin]. */
const val REMINDER_USE_DEFAULT = -1

/** Momentul reminderului sau null (programare inactivă / fără reminder). */
fun reminderTime(a: Appointment, defaultMin: Int): Long? {
    if (!a.isActive) return null
    val m = if (a.reminderMin == REMINDER_USE_DEFAULT) defaultMin else a.reminderMin
    if (m <= 0) return null
    return a.start - m * 60_000L
}

data class ScheduledReminder(val appointment: Appointment, val at: Long)

/** Reminderele viitoare, cronologic, cel mult [limit] (AlarmManager nu e făcut pentru sute de alarme). */
fun remindersToSchedule(
    list: List<Appointment>, defaultMin: Int, now: Long, limit: Int = 50
): List<ScheduledReminder> = list
    .mapNotNull { a -> reminderTime(a, defaultMin)?.takeIf { it > now }?.let { ScheduledReminder(a, it) } }
    .sortedBy { it.at }
    .take(limit)

/** Titlul notificării: „Vizită — Ion Exemplu”. */
fun reminderTitle(a: Appointment): String =
    listOf(a.type.label, a.clientName).filter { it.isNotBlank() }.joinToString(" — ")

/** Textul notificării: „10:00–11:00 · Str. Exemplu 1 · în 60 min”. */
fun reminderText(a: Appointment, now: Long): String {
    val parts = mutableListOf(a.timeLabel())
    if (a.address.isNotBlank()) parts += a.address
    val minutes = ((a.start - now) / 60_000L).toInt()
    if (!a.allDay && minutes in 1..24 * 60) {
        parts += if (minutes < 60) "în $minutes min" else "în ${minutes / 60} h" + if (minutes % 60 != 0) " ${minutes % 60} min" else ""
    }
    return parts.joinToString(" · ")
}

/** Rezumatul de dimineață sau null dacă ziua nu are programări active. */
fun morningSummaryText(list: List<Appointment>, date: LocalDate): String? {
    val todays = appointmentsOn(list, date).filter { it.isActive }
    if (todays.isEmpty()) return null
    val first = todays.first()
    val where = listOf(first.clientName, first.address).filter { it.isNotBlank() }.joinToString(", ")
    val firstText = (if (first.allDay) "toată ziua" else "prima la ${formatTime(first.start)}") +
        if (where.isNotBlank()) " — $where" else ""
    return if (todays.size == 1) "Azi ai o programare: $firstText"
    else "Azi ai ${todays.size} programări: $firstText"
}

/** Următorul moment de [hour]:00 după [now] (azi dacă n-a trecut, altfel mâine). */
fun nextMorningTrigger(now: Long, hour: Int): Long {
    val today = toLocalDate(now)
    val h = hour.coerceIn(0, 23)
    val t = epochOf(today, LocalTime.of(h, 0))
    return if (t > now) t else epochOf(today.plusDays(1), LocalTime.of(h, 0))
}

// ------------------------------------------------------------------ Android: alarme + notificări

/**
 * Programează reminderele cu AlarmManager (inexact, „allow while idle” — fără
 * permisiunea de alarme exacte) și afișează notificările. Totul e local.
 */
object ReminderScheduler {
    const val CHANNEL_ID = "programari"
    const val ACTION_REMINDER = "com.necmat.app.REMINDER"
    const val ACTION_MORNING = "com.necmat.app.MORNING"
    const val EXTRA_ID = "appointment_id"
    const val EXTRA_OPEN = "open_appointment"
    private const val MORNING_REQUEST = 1
    private const val MORNING_NOTIF = 2

    data class Config(val enabled: Boolean, val defaultMin: Int, val morning: Boolean, val morningHour: Int)

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Citite direct din preferințe, ca receiverele să nu depindă de ViewModel. */
    fun config(context: Context): Config {
        val p = prefs(context)
        return Config(
            enabled = p.getBoolean("rem_enabled", true),
            defaultMin = p.getInt("rem_default", 60),
            morning = p.getBoolean("rem_morning", false),
            morningHour = p.getInt("rem_hour", 7)
        )
    }

    fun needsPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    fun canNotify(context: Context): Boolean =
        !needsPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Programări", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Remindere pentru vizite, oferte și zile de execuție"
                }
            )
        }
    }

    private fun receiverIntent(context: Context, action: String) =
        Intent(context, ReminderReceiver::class.java).setAction(action)

    private fun reminderPi(context: Context, id: Long): PendingIntent = PendingIntent.getBroadcast(
        context, (id % Int.MAX_VALUE).toInt(),
        receiverIntent(context, ACTION_REMINDER).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun morningPi(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, MORNING_REQUEST, receiverIntent(context, ACTION_MORNING),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Reprogramează toate alarmele după starea curentă (apelat la orice schimbare și la pornire). */
    fun sync(context: Context, appointments: List<Appointment>) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val p = prefs(context)
        try {
            (p.getStringSet("sched_ids", emptySet()) ?: emptySet()).forEach { id ->
                id.toLongOrNull()?.let { am.cancel(reminderPi(context, it)) }
            }
            am.cancel(morningPi(context))
            val cfg = config(context)
            if (!cfg.enabled) {
                p.edit().putStringSet("sched_ids", emptySet()).apply()
                return
            }
            val now = System.currentTimeMillis()
            val list = remindersToSchedule(appointments, cfg.defaultMin, now)
            list.forEach { r ->
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at, reminderPi(context, r.appointment.id))
            }
            p.edit().putStringSet("sched_ids", list.map { it.appointment.id.toString() }.toSet()).apply()
            if (cfg.morning) {
                am.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, nextMorningTrigger(now, cfg.morningHour), morningPi(context)
                )
            }
            AppLog.d("Remindere", "Programate ${list.size} remindere" + if (cfg.morning) " + rezumat ${cfg.morningHour}:00" else "")
        } catch (e: Exception) {
            AppLog.e("Remindere", "Nu am putut programa alarmele", e)
        }
    }

    private fun openPi(context: Context, id: Long): PendingIntent = PendingIntent.getActivity(
        context, (id % Int.MAX_VALUE).toInt().coerceAtLeast(0),
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN, id)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun notify(context: Context, notifId: Int, title: String, text: String, open: PendingIntent) {
        ensureChannel(context)
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notifId, n)
        } catch (e: SecurityException) {
            AppLog.w("Remindere", "Notificare respinsă de sistem", e)
        }
    }

    fun showReminder(context: Context, id: Long) {
        val a = AppointmentsRepo.load(context).firstOrNull { it.id == id } ?: return
        if (!a.isActive) return
        notify(context, (id % Int.MAX_VALUE).toInt(), reminderTitle(a), reminderText(a, System.currentTimeMillis()), openPi(context, id))
        AppLog.i("Remindere", "Reminder afișat: ${reminderTitle(a)} ${formatDate(a.start)}")
    }

    fun showMorning(context: Context) {
        val list = AppointmentsRepo.load(context)
        morningSummaryText(list, toLocalDate(System.currentTimeMillis()))?.let { txt ->
            notify(context, MORNING_NOTIF, "Programul de azi", txt, openPi(context, -1L))
        }
        // reprogramează dimineața următoare și reminderele
        sync(context, list)
    }
}

/** Primește alarmele: reminder de programare sau rezumatul de dimineață. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ReminderScheduler.ACTION_REMINDER -> {
                val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1L)
                if (id > 0) ReminderScheduler.showReminder(context, id)
            }
            ReminderScheduler.ACTION_MORNING -> ReminderScheduler.showMorning(context)
        }
    }
}

/** După repornirea telefonului alarmele se pierd — le reprogramăm. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.MY_PACKAGE_REPLACED"
        ) {
            ReminderScheduler.sync(context, AppointmentsRepo.load(context))
        }
    }
}
