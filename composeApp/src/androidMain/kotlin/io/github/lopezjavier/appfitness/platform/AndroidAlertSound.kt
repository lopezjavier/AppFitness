package io.github.lopezjavier.appfitness.platform

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import io.github.lopezjavier.appfitness.domain.port.AlertSound

private const val TONE_VOLUME = 80
private const val TONE_MILLIS = 400
private const val RELEASE_MARGIN_MILLIS = 100

/** [AlertSound] as a short notification beep; silent when the ringer is on vibrate or mute. */
class AndroidAlertSound(context: Context) : AlertSound {
    private val handler = Handler(Looper.getMainLooper())
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)

    override fun play() {
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, TONE_MILLIS)
            handler.postDelayed({ tone.release() }, (TONE_MILLIS + RELEASE_MARGIN_MILLIS).toLong())
        }
    }
}
