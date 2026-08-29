package com.cascalc.app

import android.content.Intent
import android.speech.RecognizerIntent
import java.util.Locale

/**
 * V6's voice input, using the system speech recogniser.
 *
 * Deliberately uses the recogniser *intent* rather than a bound
 * `SpeechRecognizer` service: the intent shows the system's own listening UI,
 * needs no RECORD_AUDIO permission of our own, and works on devices without
 * on-device recognition. The transcript then goes through
 * [com.cascalc.engine.NaturalLanguageParser] exactly as typed words would.
 */
object VoiceInput {

    fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a calculation")
    }

    /** The best transcript from a recogniser result, or null if there was none. */
    fun transcriptOf(data: Intent?): String? =
        data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
}
