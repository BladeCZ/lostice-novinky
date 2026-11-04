package cz.lostice.novinky.data

import android.content.Context

/** Ukládané nastavení: velikost textu, rychlost čtení, upozornění a již viděná ID. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("novinky", Context.MODE_PRIVATE)

    var fontScale: Float
        get() = sp.getFloat("fontScale", 1.0f)
        set(v) = sp.edit().putFloat("fontScale", v.coerceIn(MIN_FONT, MAX_FONT)).apply()

    var speechRate: Float
        get() = sp.getFloat("speechRate", 1.0f)
        set(v) = sp.edit().putFloat("speechRate", v.coerceIn(MIN_RATE, MAX_RATE)).apply()

    fun notifyEnabled(c: Category) = sp.getBoolean("notify_${c.file}", false)
    fun setNotifyEnabled(c: Category, on: Boolean) = sp.edit().putBoolean("notify_${c.file}", on).apply()

    /** Množina již známých ID, null = první spuštění (nic se nenotifikuje). */
    fun seen(c: Category): Set<String>? = sp.getStringSet("seen_${c.file}", null)?.toSet()
    fun setSeen(c: Category, ids: Set<String>) = sp.edit().putStringSet("seen_${c.file}", ids).apply()

    companion object {
        const val MIN_FONT = 0.7f
        const val MAX_FONT = 2.5f
        const val STEP_FONT = 0.1f
        const val MIN_RATE = 0.5f
        const val MAX_RATE = 2.0f
        const val STEP_RATE = 0.1f
    }
}
