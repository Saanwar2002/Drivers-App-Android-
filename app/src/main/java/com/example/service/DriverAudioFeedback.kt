package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class DriverAudioFeedback(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var tts: TextToSpeech? = null
    @Volatile
    private var ttsReady = false

    init {
        val navAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(navAttrs)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { /* transient ducking */ }
                .build()
        }

        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.UK
                tts?.setAudioAttributes(navAttrs)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        abandonNavAudioFocus()
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        abandonNavAudioFocus()
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        abandonNavAudioFocus()
                    }
                })
                ttsReady = true
            }
        }
    }

    private fun requestNavAudioFocus() {
        runCatching {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { am.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        }
    }

    private fun abandonNavAudioFocus() {
        runCatching {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        }
    }

    /**
     * Plays a crisp 3-pulse urgent offer alert tone unless `muteOfferAlerts` is enabled.
     */
    fun playRideOfferChime(muteOfferAlerts: Boolean) {
        if (muteOfferAlerts) return
        scope.launch {
            runCatching {
                val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
                try {
                    repeat(3) {
                        tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 260)
                        delay(380L)
                    }
                } finally {
                    tg.release()
                }
            }
        }
    }

    /**
     * Plays a crisp 2-tone alert when a passenger updates stops, destination, or fare mid-trip.
     */
    fun playRideUpdatedAlert(muteOfferAlerts: Boolean) {
        if (muteOfferAlerts) return
        scope.launch {
            runCatching {
                val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
                try {
                    repeat(2) {
                        tg.startTone(ToneGenerator.TONE_PROP_ACK, 220)
                        delay(280L)
                    }
                } finally {
                    tg.release()
                }
            }
        }
    }

    /**
     * Plays a descending alert tone when a passenger cancels an active ride.
     */
    fun playCancellationAlert(muteOfferAlerts: Boolean) {
        if (muteOfferAlerts) return
        scope.launch {
            runCatching {
                val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
                try {
                    tg.startTone(ToneGenerator.TONE_PROP_NACK, 380)
                    delay(420L)
                } finally {
                    tg.release()
                }
            }
        }
    }

    /**
     * Plays a crisp short notification pip when a passenger sends a live chat message while chat is closed.
     */
    fun playChatMessageChime(muteOfferAlerts: Boolean) {
        if (muteOfferAlerts) return
        scope.launch {
            runCatching {
                val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                try {
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP, 140)
                    delay(180L)
                } finally {
                    tg.release()
                }
            }
        }
    }

    /**
     * Plays a crisp 2-tone ascending confirmation chime when the driver marks Arrived Outside.
     */
    fun playArrivalConfirmChime(muteOfferAlerts: Boolean) {
        if (muteOfferAlerts) return
        scope.launch {
            runCatching {
                val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
                try {
                    tg.startTone(ToneGenerator.TONE_PROP_ACK, 180)
                    delay(210L)
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 220)
                    delay(250L)
                } finally {
                    tg.release()
                }
            }
        }
    }

    /**
     * Speaks concise UK English heads-up driving announcements with transient audio ducking
     * so background music/radio volume dips smoothly during voice prompts.
     */
    fun speakHeadsUp(text: String, muteHeadsUp: Boolean) {
        if (muteHeadsUp || !ttsReady || text.isBlank()) return
        runCatching {
            requestNavAudioFocus()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "anyroller_hud_${System.currentTimeMillis()}")
        }
    }

    /**
     * Reads an incoming passenger chat message aloud hands-free using QUEUE_ADD so
     * rapid back-to-back messages do not cut each other off. Respects `muteHeadsUp`.
     */
    fun speakChatMessage(
        senderName: String,
        text: String?,
        hasImage: Boolean,
        muteHeadsUp: Boolean
    ) {
        if (muteHeadsUp || !ttsReady) return
        val firstName = senderName
            .trim()
            .split(" ")
            .firstOrNull()
            ?.takeIf { it.isNotBlank() && !it.equals("passenger", ignoreCase = true) }
            ?: "passenger"
        val cleanText = text?.trim()?.replace(Regex("\\s+"), " ")?.take(220).orEmpty()
        val spoken = when {
            hasImage && cleanText.isNotBlank() -> "Photo message from $firstName: $cleanText"
            hasImage -> "$firstName sent a photo."
            cleanText.isNotBlank() -> "Message from $firstName: $cleanText"
            else -> return
        }
        runCatching {
            requestNavAudioFocus()
            tts?.speak(spoken, TextToSpeech.QUEUE_ADD, null, "anyroller_chat_${System.currentTimeMillis()}")
        }
    }

    fun release() {
        abandonNavAudioFocus()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ttsReady = false
    }
}
