package cz.lostice.novinky.ui

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Předčítání textu pomocí systémového TTS (česky), rychlost lze měnit za běhu. */
class Reader(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: (() -> Unit)? = null

    private var chunks: List<String> = emptyList()
    private var current = 0
    private var rate = 1.0f

    var playing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            main.post {
                if (status == TextToSpeech.SUCCESS) {
                    val res = tts?.setLanguage(Locale("cs", "CZ")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                    if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                        error = "Český hlas není v telefonu nainstalován (Nastavení → Převod textu na řeč)."
                    }
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {
                            id?.removePrefix("c")?.toIntOrNull()?.let { main.post { current = it } }
                        }

                        override fun onDone(id: String?) {
                            if (id?.removePrefix("c")?.toIntOrNull() == chunks.lastIndex) main.post { playing = false }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(id: String?) {
                            main.post { playing = false }
                        }
                    })
                    ready = true
                    pending?.invoke()
                    pending = null
                } else {
                    error = "Předčítání není na tomto zařízení dostupné."
                }
            }
        }
    }

    fun play(text: String, speechRate: Float) {
        rate = speechRate
        chunks = split(text)
        current = 0
        start(0)
    }

    private fun start(from: Int) {
        val action = {
            val t = tts
            if (t != null && chunks.isNotEmpty()) {
                t.stop()
                t.setSpeechRate(rate)
                playing = true
                for (i in from..chunks.lastIndex) {
                    t.speak(chunks[i], TextToSpeech.QUEUE_ADD, Bundle(), "c$i")
                }
            }
        }
        if (ready) action() else {
            playing = true
            pending = action
        }
    }

    /** Změna rychlosti – při čtení se pokračuje od aktuálního odstavce. */
    fun setRate(r: Float) {
        rate = r
        if (playing) start(current) else tts?.setSpeechRate(r)
    }

    fun stop() {
        pending = null
        tts?.stop()
        playing = false
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }

    /** Rozdělí text na kousky (limit TTS engine ~4000 znaků) po větách. */
    private fun split(text: String, max: Int = 1500): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        fun flush() {
            if (sb.isNotBlank()) out.add(sb.toString().trim())
            sb.clear()
        }
        for (para in text.split("\n")) {
            for (sentence in para.split(Regex("(?<=[.!?;:])\\s+"))) {
                var s = sentence
                while (s.length > max) {
                    flush()
                    val cut = s.lastIndexOf(' ', max).let { if (it <= 0) max else it }
                    out.add(s.substring(0, cut).trim())
                    s = s.substring(cut)
                }
                if (sb.length + s.length + 1 > max) flush()
                sb.append(s).append(' ')
            }
            flush() // odstavec = přirozená pauza
        }
        return out.filter { it.isNotBlank() }
    }
}
