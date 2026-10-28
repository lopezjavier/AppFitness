package io.github.lopezjavier.appfitness.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import io.github.lopezjavier.appfitness.composeapp.R
import io.github.lopezjavier.appfitness.domain.model.RestSound
import io.github.lopezjavier.appfitness.domain.port.AlertSound
import io.github.lopezjavier.appfitness.domain.port.RestAlertPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val MAX_PLAY_MILLIS = 2000L

/**
 * In-app [AlertSound] for the foreground (a visible activity, so Android 17's background-audio limit does not apply):
 * the chosen [RestSound], cut after about two seconds. The system candidate is the alarm tone (notification tone when
 * there is no alarm one); the others are the bundled raw assets. Silent unless the ringer is normal, because the alarm
 * stream would otherwise bypass the mute. Outside the app the notification channel plays the sound (RestAlertNotifier).
 */
class AndroidAlertSound internal constructor(
    context: Context,
    private val currentSound: () -> RestSound,
    private val assetOf: (RestSound) -> AlertTone?,
    private val toneOf: (type: Int) -> AlertTone?,
) : AlertSound {
    constructor(context: Context, preferences: RestAlertPreferences) : this(
        context,
        cachedSound(preferences),
        { sound -> assetTone(context, sound) },
        { type -> systemTone(context, type) },
    )

    /** System tone only, for callers that never choose a candidate. */
    internal constructor(context: Context, toneOf: (type: Int) -> AlertTone?) :
        this(context, { RestSound.Default }, { null }, toneOf)

    private val handler = Handler(Looper.getMainLooper())
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)

    override fun preview(sound: RestSound) = start(sound)

    override fun play() = start(currentSound())

    private fun start(sound: RestSound) {
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        runCatching {
            val tone = tonePlaying(sound)
            tone ?: return
            tone.play()
            handler.postDelayed({ tone.stop() }, MAX_PLAY_MILLIS)
        }
    }

    private fun tonePlaying(sound: RestSound): AlertTone? = if (sound == RestSound.SYSTEM) {
        toneOf(RingtoneManager.TYPE_ALARM) ?: toneOf(RingtoneManager.TYPE_NOTIFICATION)
    } else {
        assetOf(sound)
    }
}

private fun cachedSound(preferences: RestAlertPreferences): () -> RestSound {
    var latest = RestSound.Default
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { preferences.sound.collect { latest = it } }
    return { latest }
}

private fun assetTone(context: Context, sound: RestSound): AlertTone? {
    val resId = when (sound) {
        RestSound.CHIME -> R.raw.rest_chime
        RestSound.DOUBLE_BEEP -> R.raw.rest_double_beep
        RestSound.SYSTEM -> null
    }
    val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    val player = resId?.let { MediaPlayer.create(context, it, attributes, AudioManager.AUDIO_SESSION_ID_GENERATE) }
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
