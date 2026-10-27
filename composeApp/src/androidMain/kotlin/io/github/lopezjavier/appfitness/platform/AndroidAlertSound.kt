package io.github.lopezjavier.appfitness.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import io.github.lopezjavier.appfitness.domain.port.AlertSound

private const val MAX_PLAY_MILLIS = 2000L

/**
 * [AlertSound] with the system alarm tone (notification tone when there is no alarm one), cut after about two
 * seconds. Silent unless the ringer is normal, because the alarm stream would otherwise bypass the mute.
 */
class AndroidAlertSound internal constructor(context: Context, private val toneOf: (type: Int) -> AlertTone?) :
    AlertSound {
    constructor(context: Context) : this(context, { type -> systemTone(context, type) })

    private val handler = Handler(Looper.getMainLooper())
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)

    override fun play() {
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        runCatching {
            val tone = toneOf(RingtoneManager.TYPE_ALARM) ?: toneOf(RingtoneManager.TYPE_NOTIFICATION)
            tone ?: return
            tone.play()
            handler.postDelayed({ tone.stop() }, MAX_PLAY_MILLIS)
        }
    }
}

/** A sound that can be started and stopped; a seam over [Ringtone] so tests need no real audio. */
internal class AlertTone(val play: () -> Unit, val stop: () -> Unit)

private fun systemTone(context: Context, type: Int): AlertTone? {
    val uri = RingtoneManager.getDefaultUri(type)
    val ringtone: Ringtone = uri?.let { RingtoneManager.getRingtone(context, it) } ?: return null
    ringtone.audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    return AlertTone(play = { ringtone.play() }, stop = { ringtone.stop() })
}
