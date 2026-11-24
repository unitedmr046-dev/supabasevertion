package com.iyonzkasir.hardware

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

object AppSound {
    fun playClick() = playTone(ToneGenerator.TONE_PROP_BEEP, 55, 65)

    fun playError() = playTone(ToneGenerator.TONE_PROP_NACK, 200, 95)

    fun playSuccess() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 95)
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { tg.startTone(ToneGenerator.TONE_PROP_BEEP, 110) }
                Handler(Looper.getMainLooper()).postDelayed({ runCatching { tg.release() } }, 240)
            }, 140)
        } catch (_: Exception) {}
    }

    private fun playTone(tone: Int, durationMs: Int, volume: Int) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, volume)
            tg.startTone(tone, durationMs)
            Handler(Looper.getMainLooper()).postDelayed({ runCatching { tg.release() } }, durationMs + 120L)
        } catch (_: Exception) {}
    }
}
