package app.unreel.data

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/**
 * Per-day usage stats. The guard service and the UI run in the same process,
 * so they share this in-memory buffer; it is flushed to disk every few seconds.
 */
object UsageStore {
    /** Tracking id for total time with the screen on and unlocked. */
    const val SCREEN = "_screen"
    private lateinit var sp: SharedPreferences
    private val pendingSeconds = HashMap<String, Long>()

    fun init(context: Context) {
        if (!::sp.isInitialized) {
            sp = context.applicationContext.getSharedPreferences("usage", Context.MODE_PRIVATE)
        }
    }

    fun today(): String = LocalDate.now().toString()

    @Synchronized
    fun addSeconds(appId: String, seconds: Long) {
        val key = "t|${today()}|$appId"
        pendingSeconds[key] = (pendingSeconds[key] ?: 0L) + seconds
    }

    @Synchronized
    fun seconds(appId: String, date: String = today()): Long {
        val key = "t|$date|$appId"
        return sp.getLong(key, 0L) + (pendingSeconds[key] ?: 0L)
    }

    @Synchronized
    fun flush() {
        if (pendingSeconds.isEmpty()) return
        val editor = sp.edit()
        for ((key, secs) in pendingSeconds) {
            editor.putLong(key, sp.getLong(key, 0L) + secs)
        }
        editor.apply()
        pendingSeconds.clear()
    }

    @Synchronized
    fun addBlock(source: String) {
        val day = today()
        sp.edit()
            .putInt("b|$day", sp.getInt("b|$day", 0) + 1)
            .putInt("b|$day|$source", sp.getInt("b|$day|$source", 0) + 1)
            .apply()
    }

    @Synchronized
    fun blocks(date: String = today()): Int = sp.getInt("b|$date", 0)

    @Synchronized
    fun bonusMinutes(appId: String, date: String = today()): Int = sp.getInt("bonus|$date|$appId", 0)

    @Synchronized
    fun addBonus(appId: String, minutes: Int) {
        val key = "bonus|${today()}|$appId"
        sp.edit().putInt(key, sp.getInt(key, 0) + minutes).apply()
    }

    @Synchronized
    fun addUnlock() {
        val key = "unlocks|${today()}"
        sp.edit().putInt(key, sp.getInt(key, 0) + 1).apply()
    }

    @Synchronized
    fun unlocks(date: String = today()): Int = sp.getInt("unlocks|$date", 0)

    @Synchronized
    fun extensionsUsed(id: String, date: String = today()): Int = sp.getInt("ext|$date|$id", 0)

    @Synchronized
    fun addExtension(id: String) {
        val key = "ext|${today()}|$id"
        sp.edit().putInt(key, sp.getInt(key, 0) + 1).apply()
    }

    /** Wall-clock time (ms) until which a forced break is in effect for [id]. */
    @Synchronized
    fun breakUntil(id: String): Long = sp.getLong("break|$id", 0L)

    @Synchronized
    fun setBreakUntil(id: String, millis: Long) {
        sp.edit().putLong("break|$id", millis).apply()
    }

    @Synchronized
    fun reset() {
        pendingSeconds.clear()
        sp.edit().clear().apply()
    }
}
