package com.necmat.app

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Jurnal de activitate persistent, configurabil din Setări.
 * Scrie într-un fișier rotativ; nivelurile sub minLevel sunt ignorate.
 */
object AppLog {

    enum class Level(val tagChar: Char) { DEBUG('D'), INFO('I'), WARN('W'), ERROR('E') }

    @Volatile
    var enabled: Boolean = true

    @Volatile
    var minLevel: Level = Level.INFO

    private const val MAX_BYTES = 512 * 1024L
    private const val KEEP_BYTES = 256 * 1024
    private val lock = Any()
    private var file: File? = null
    private var crashHandlerInstalled = false
    private val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(logFile: File) {
        synchronized(lock) { file = logFile }
    }

    fun currentFile(): File? = synchronized(lock) { file }

    fun shouldLog(level: Level): Boolean = enabled && level.ordinal >= minLevel.ordinal

    fun formatLine(time: String, level: Level, tag: String, msg: String): String =
        "$time ${level.tagChar}/$tag: $msg"

    fun log(level: Level, tag: String, msg: String, tr: Throwable? = null) {
        if (!shouldLog(level)) return
        val line = buildString {
            append(formatLine(df.format(Date()), level, tag, msg))
            if (tr != null) append('\n').append(tr.stackTraceToString().trimEnd())
        }
        synchronized(lock) {
            val f = file ?: return
            try {
                f.appendText(line + "\n")
                if (f.length() > MAX_BYTES) rotate(f)
            } catch (_: Exception) {
                // jurnalul nu are voie să strice aplicația
            }
        }
        try {
            android.util.Log.println(
                when (level) {
                    Level.DEBUG -> android.util.Log.DEBUG
                    Level.INFO -> android.util.Log.INFO
                    Level.WARN -> android.util.Log.WARN
                    Level.ERROR -> android.util.Log.ERROR
                },
                "NecMat", "$tag: $msg" + (tr?.let { "\n${it.stackTraceToString()}" } ?: "")
            )
        } catch (_: Throwable) {
            // în testele JVM logcat nu există
        }
    }

    fun d(tag: String, msg: String) = log(Level.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = log(Level.INFO, tag, msg)
    fun w(tag: String, msg: String, tr: Throwable? = null) = log(Level.WARN, tag, msg, tr)
    fun e(tag: String, msg: String, tr: Throwable? = null) = log(Level.ERROR, tag, msg, tr)

    /** Excepțiile neprinse ajung în jurnal înainte de închiderea aplicației. */
    fun installCrashHandler() {
        synchronized(lock) {
            if (crashHandlerInstalled) return
            crashHandlerInstalled = true
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val wasEnabled = enabled
            enabled = true
            log(Level.ERROR, "Crash", "Excepție neprinsă pe firul ${thread.name}", throwable)
            enabled = wasEnabled
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun readTail(maxChars: Int = 20_000): String = synchronized(lock) {
        val f = file ?: return ""
        try {
            if (!f.exists()) "" else f.readText().takeLast(maxChars)
        } catch (e: Exception) {
            ""
        }
    }

    fun clear() = synchronized(lock) {
        try {
            file?.writeText("")
        } catch (_: Exception) {
        }
    }

    private fun rotate(f: File) {
        val bytes = f.readBytes()
        if (bytes.size <= KEEP_BYTES) return
        var keep = bytes.copyOfRange(bytes.size - KEEP_BYTES, bytes.size)
        // tăiem la început de linie completă
        val nl = keep.indexOfFirst { it == '\n'.code.toByte() }
        if (nl in 0 until keep.size - 1) keep = keep.copyOfRange(nl + 1, keep.size)
        f.writeBytes(keep)
    }
}
