package com.tk.carelog.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.io.File
import java.util.Locale

/**
 * 음성 브리핑. Pleos TextToSpeech SDK가 공개되기 전까지 Android 표준 TTS 사용.
 *
 * 실시간 합성(tts.speak)은 CPU가 바쁘면 소리가 끊긴다(특히 에뮬레이터).
 * 그래서 문장 전체를 먼저 파일로 합성한 뒤 MediaPlayer로 재생한다. 같은 문장은 캐시를 다시 쓴다.
 * 에뮬레이터에 TTS 엔진이 없으면 ready=false → 화면 문구만 표시됩니다.
 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val appCtx = context.applicationContext
    private val tts = TextToSpeech(appCtx, this)
    private val main = Handler(Looper.getMainLooper())
    private val dir = File(appCtx.cacheDir, "tts").apply { mkdirs() }
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private var player: MediaPlayer? = null
    /** 지금 재생하려는 문장의 id (늦게 끝난 이전 합성은 무시) */
    private var wanted: String? = null
    /** 엔진 준비 전에 들어온 마지막 요청 */
    private var pending: String? = null
    /** id → 문장 (재생 실패 시 실시간 합성용) */
    private val texts = HashMap<String, String>()

    var ready = false
        private set

    /** 안내 음성 크기 (0~1) */
    var volume = 0.55f

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts.setLanguage(Locale.KOREAN)
            ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            if (ready) {
                tts.setSpeechRate(1.05f)
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String) {}
                    override fun onDone(id: String) {
                        main.post {
                            val part = File(dir, "$id.part")
                            val done = File(dir, "$id.wav")
                            if (part.exists()) part.renameTo(done)
                            if (id == wanted) play(done, texts[id])
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String) {}
                })
                // 엔진을 미리 깨워 둔다 (재생하지 않음)
                tts.synthesizeToFile("준비", Bundle(), File(dir, "warmup.part"), "warmup")
                pending?.let { speak(it) }
                pending = null
            }
        }
        Log.i("CareLog/TTS", "TTS ready=$ready")
    }

    fun speak(text: String) {
        if (!ready) { pending = text; return }
        stopPlayer()
        val id = "u" + Integer.toHexString(text.hashCode())
        wanted = id
        texts[id] = text
        val cached = File(dir, "$id.wav")
        if (cached.exists() && cached.length() > 0) {
            play(cached, text)
        } else {
            tts.stop() // 이전 합성이 남아 있으면 비우고 새 문장부터
            tts.synthesizeToFile(text, Bundle(), File(dir, "$id.part"), id)
        }
    }

    private fun play(file: File, text: String?) {
        stopPlayer()
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(file.path)
                setVolume(volume, volume)
                setOnCompletionListener { mp ->
                    mp.release()
                    if (player === mp) player = null
                }
                prepare()
                start()
            }
        } catch (t: Throwable) {
            Log.w("CareLog/TTS", "재생 실패, 실시간 합성으로 대체", t)
            stopPlayer()
            file.delete()
            if (text != null) {
                val p = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, p, "fallback")
            }
        }
    }

    private fun stopPlayer() {
        player?.let {
            try { it.stop() } catch (_: Throwable) {}
            it.release()
        }
        player = null
    }

    fun stop() {
        pending = null
        wanted = null
        if (ready) tts.stop()
        stopPlayer()
    }
}
