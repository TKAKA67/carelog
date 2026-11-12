package com.tk.carelog.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
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

    /** 엔진 준비 전에 들어온 마지막 요청 */
    private var pending: String? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts.setLanguage(Locale.KOREAN)
            ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            if (ready) {
                tts.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                tts.setSpeechRate(1.05f)
                // 첫 발화 때 엔진이 늦게 깨어나 끊기는 것을 막기 위해 무음으로 한 번 합성해 둔다
                val silent = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0f) }
                tts.speak("준비", TextToSpeech.QUEUE_FLUSH, silent, "warmup")
                pending?.let { speak(it) }
                pending = null
            }
        }
        Log.i("CareLog/TTS", "TTS ready=$ready")
    }

    fun speak(text: String) {
        if (!ready) { pending = text; return }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "carelog-${System.nanoTime()}")
    }

    fun stop() {
        pending = null
        if (ready) tts.stop()
    }
}
