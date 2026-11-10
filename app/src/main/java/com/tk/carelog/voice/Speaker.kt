package com.tk.carelog.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * 음성 브리핑. Pleos TextToSpeech SDK가 공개되기 전까지 Android 표준 TTS 사용.
 * 에뮬레이터에 TTS 엔진이 없으면 ready=false → 화면 문구만 표시됩니다.
 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    var ready = false
        private set

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts.setLanguage(Locale.KOREAN)
            ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        }
        Log.i("CareLog/TTS", "TTS ready=$ready")
    }

    fun speak(text: String) {
        if (!ready) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "carelog")
    }

    fun stop() { if (ready) tts.stop() }
}
