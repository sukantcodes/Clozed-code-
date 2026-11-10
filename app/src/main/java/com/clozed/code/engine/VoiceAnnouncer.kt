package com.clozed.code.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Optional spoken alerts ("Your task is completed, sir."). */
class VoiceAnnouncer(context: Context) {
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) ready = true
    }

    fun say(text: String) {
        if (!ready) return
        runCatching {
            tts.language = Locale.getDefault()
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "clozed-announce")
        }
    }

    fun shutdown() = runCatching { tts.shutdown() }.let { }
}
