package io.github.lopezjavier.appfitness.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import io.github.lopezjavier.appfitness.composeapp.R
import io.github.lopezjavier.appfitness.domain.port.AlertSound

private const val MAX_PLAY_MILLIS = 2000L

/**
 * In-app [AlertSound] for the foreground (a visible activity, so Android 17's background-audio limit does not apply):
 * the bundled "Campana suave" (`rest_chime`), cut after about two seconds. Silent unless the ringer is normal, because
 * the alarm stream would otherwise bypass the mute. Outside the app the notification channel plays the sound
 * (RestAlertNotifier).
 */
class AndroidAlertSound internal constructor(context: Context, private val tone: () -> AlertTone?) : AlertSound {
    constructor(context: Context) : this(context, { chimeTone(context) })

    private val handler = Handler(Looper.getMainLooper())
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)

    override fun play() {
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        runCatching {
            val playing = tone() ?: return
            playing.play()
            handler.postDelayed({ playing.stop() }, MAX_PLAY_MILLIS)
        }
    }
}

private fun chimeTone(context: Context): AlertTone? {
    val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    val player = MediaPlayer.create(context, R.raw.rest_chime, attributes, AudioManager.AUDIO_SESSION_ID_GENERATE)
    return player?.let {
        AlertTone(
            play = {
                it.setOnCompletionListener { done -> done.release() }
                it.start()
            },
            stop = { runCatching { it.release() } },
        )
    }
}

/** A sound that can be started and stopped; a seam over [MediaPlayer] so tests need no real audio. */
internal class AlertTone(val play: () -> Unit, val stop: () -> Unit)
