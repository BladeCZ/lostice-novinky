package cz.lostice.novinky.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Čtyři kategorie obsahu. [file] je název JSON souboru v repozitáři. */
enum class Category(val file: String, val label: String, val shortLabel: String, val notifyTitle: String) {
    HLASENI("hlaseni", "Hlášení městského rozhlasu", "Rozhlas", "Nové hlášení městského rozhlasu"),
    RM("rm", "Usnesení rady města", "Rada", "Nové usnesení rady města"),
    ZM("zm", "Usnesení zastupitelstva", "Zastupitelstvo", "Nové usnesení zastupitelstva"),
    HLAS("hlas", "Hlas Loštic (zpravodaj)", "Zpravodaj", "Nové číslo Hlasu Loštic")
}

data class Resolution(
    val num: String = "",
    val decision: String = "",
    val is_procedural: Boolean = false,
    val summary: String = "",
    val text: String = ""
)

data class TocItem(
    val page: Int = 1,
    val title: String = ""
)

data class Item(
    val id: String = "",
    val title: String = "",
    val date: String? = null,
    val url: String = "",
    val page: String = "",
    val text: String = "",
    val header: String = "",
    val a5_url: String? = null,
    val pages_count: Int = 0,
    val toc: List<TocItem> = emptyList(),
    val resolutions: List<Resolution> = emptyList()
) {
    /** Datum ve formátu 3. 11. 2026 */
    val displayDate: String
        get() {
            val p = date?.split("-") ?: return ""
            if (p.size != 3) return date.orEmpty()
            return "${p[2].toInt()}. ${p[1].toInt()}. ${p[0]}"
        }

    /** Plná URL na převedené A5 PDF */
    val fullA5Url: String?
        get() = if (!a5_url.isNullOrBlank()) "https://bladecz.github.io/lostice-novinky/$a5_url" else null
}

data class Feed(
    @SerializedName("updated") val updated: String = "",
    @SerializedName("items") val items: List<Item> = emptyList()
)

class Repository(context: Context) {
    private val dir = context.applicationContext.filesDir
    private val gson = Gson()

    private fun cacheFile(c: Category) = File(dir, "${c.file}.json")

    /** Načte data z lokální cache (offline režim). */
    suspend fun cached(c: Category): Feed? = withContext(Dispatchers.IO) {
        runCatching {
            val f = cacheFile(c)
            if (f.exists()) gson.fromJson(f.readText(), Feed::class.java) else null
        }.getOrNull()
    }

    /** Stáhne aktuální data z GitHubu a uloží do cache. Vrací null při chybě. */
    suspend fun refresh(c: Category): Feed? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("$BASE_URL${c.file}.json?t=${System.currentTimeMillis() / 60000}")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            try {
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val feed = gson.fromJson(body, Feed::class.java)
                if (feed != null && feed.items.isNotEmpty()) {
                    cacheFile(c).writeText(body)
                    feed
                } else null
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    companion object {
        const val BASE_URL = "https://raw.githubusercontent.com/BladeCZ/lostice-novinky/main/data/"
    }
}
