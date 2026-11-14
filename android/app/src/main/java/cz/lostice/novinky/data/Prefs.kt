package cz.lostice.novinky.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

enum class ThemeMode {
    SYSTEM, LIGHT, DARK, OLED, CUSTOM
}

/** Ukládané nastavení: velikost textu, rychlost čtení, upozornění, témata, barvy a přečtené položky. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("novinky", Context.MODE_PRIVATE)

    var fontScale: Float
        get() = sp.getFloat("fontScale", 1.0f)
        set(v) = sp.edit().putFloat("fontScale", v.coerceIn(MIN_FONT, MAX_FONT)).apply()

    var speechRate: Float
        get() = sp.getFloat("speechRate", 1.0f)
        set(v) = sp.edit().putFloat("speechRate", v.coerceIn(MIN_RATE, MAX_RATE)).apply()

    /** Zda otevírat PDF ve vestavěném prohlížeči (true) nebo v externím internetovém prohlížeči (false) */
    var useInternalPdfViewer: Boolean
        get() = sp.getBoolean("useInternalPdfViewer", true)
        set(v) = sp.edit().putBoolean("useInternalPdfViewer", v).apply()

    /** Vybrané téma aplikace */
    var themeMode: ThemeMode
        get() {
            val name = sp.getString("themeMode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
            return runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.SYSTEM)
        }
        set(v) = sp.edit().putString("themeMode", v.name).apply()

    /** Vlastní barva pozadí (pro ThemeMode.CUSTOM) */
    var customBgColor: Int
        get() = sp.getInt("customBgColor", Color(0xFFF1F5F9).toArgb())
        set(v) = sp.edit().putInt("customBgColor", v).apply()

    /** Vlastní barva hlavičky (pro ThemeMode.CUSTOM) */
    var customHeaderColor: Int
        get() = sp.getInt("customHeaderColor", Color(0xFF0D3B66).toArgb())
        set(v) = sp.edit().putInt("customHeaderColor", v).apply()

    /** Vlastní barva zvýraznění nového/nepřečteného příspěvku (pro ThemeMode.CUSTOM) */
    var customUnreadColor: Int
        get() = sp.getInt("customUnreadColor", Color(0xFFFEFCE8).toArgb())
        set(v) = sp.edit().putInt("customUnreadColor", v).apply()

    /** Vlastní barva odznáčku nového příspěvku / kolečka s číslem (pro ThemeMode.CUSTOM) */
    var customBadgeColor: Int
        get() = sp.getInt("customBadgeColor", Color(0xFFFACC15).toArgb())
        set(v) = sp.edit().putInt("customBadgeColor", v).apply()

    fun notifyEnabled(c: Category) = sp.getBoolean("notify_${c.file}", false)
    fun setNotifyEnabled(c: Category, on: Boolean) = sp.edit().putBoolean("notify_${c.file}", on).apply()

    fun seen(c: Category): Set<String>? = sp.getStringSet("seen_${c.file}", null)?.toSet()
    fun setSeen(c: Category, ids: Set<String>) = sp.edit().putStringSet("seen_${c.file}", ids).apply()

    // --- Přečtené položky pro uživatelské rozhraní ---

    fun isFirstRun(): Boolean = sp.getBoolean("first_run_completed", false)
    fun setFirstRunCompleted() = sp.edit().putBoolean("first_run_completed", true).apply()

    fun getReadIds(): Set<String> = sp.getStringSet("read_ids", emptySet())?.toSet() ?: emptySet()

    fun isRead(id: String): Boolean = id in getReadIds()

    fun markRead(id: String) {
        val current = (sp.getStringSet("read_ids", emptySet()) ?: emptySet()).toMutableSet()
        if (current.add(id)) {
            sp.edit().putStringSet("read_ids", current).apply()
        }
    }

    fun markAllRead(ids: Collection<String>) {
        val current = (sp.getStringSet("read_ids", emptySet()) ?: emptySet()).toMutableSet()
        current.addAll(ids)
        sp.edit().putStringSet("read_ids", current).apply()
    }

    companion object {
        const val MIN_FONT = 0.7f
        const val MAX_FONT = 2.5f
        const val STEP_FONT = 0.1f
        const val MIN_RATE = 0.5f
        const val MAX_RATE = 2.0f
        const val STEP_RATE = 0.1f
    }
}
