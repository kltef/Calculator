package com.cascalc.app.ar

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer

/**
 * Supplies the hand-landmark model, fetching it once and caching it.
 *
 * The model is 7.5 MB. Bundling it pushed the download over the size that can
 * reasonably be shipped, and ML Kit already fetches its text model on demand,
 * so hand tracking follows the same pattern. Everything after this first fetch
 * runs on-device: no frame, landmark or equation ever leaves the phone.
 *
 * Hand tracking is an enhancement, never a requirement — if the fetch fails,
 * AR mode still works by tapping.
 */
class HandModel(private val context: Context) {

    sealed interface State {
        data object Missing : State
        data object Downloading : State
        data class Ready(val buffer: ByteBuffer) : State
        data class Failed(val reason: String) : State
    }

    private val file: File get() = File(context.filesDir, FILE_NAME)

    fun isPresent(): Boolean = file.exists() && file.length() > MIN_PLAUSIBLE_BYTES

    /**
     * Loads the model, downloading it if needed. Call off the main thread.
     *
     * MediaPipe requires a *direct* buffer; a heap buffer is rejected at the
     * JNI boundary.
     */
    fun load(): State {
        if (!isPresent()) {
            val downloaded = download()
            if (downloaded != null) return State.Failed(downloaded)
        }
        return try {
            val bytes = file.readBytes()
            val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
                put(bytes)
                rewind()
            }
            State.Ready(buffer)
        } catch (e: Exception) {
            State.Failed(e.message ?: "Couldn't read the hand model")
        }
    }

    /** @return null on success, or a message describing the failure */
    private fun download(): String? {
        // Download to a temporary file and rename, so an interrupted download
        // cannot leave a truncated model that then fails to load forever.
        val temporary = File(context.filesDir, "$FILE_NAME.part")
        return try {
            val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
            }
            connection.inputStream.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            if (temporary.length() < MIN_PLAUSIBLE_BYTES) {
                temporary.delete()
                return "Hand model download was incomplete"
            }
            temporary.renameTo(file)
            null
        } catch (e: Exception) {
            temporary.delete()
            "Couldn't download the hand model: ${e.message}"
        }
    }

    private companion object {
        const val FILE_NAME = "hand_landmarker.task"
        const val MODEL_URL =
            "https://storage.googleapis.com/mediapipe-models/hand_landmarker/" +
                "hand_landmarker/float16/1/hand_landmarker.task"
        const val TIMEOUT_MILLIS = 30_000
        const val MIN_PLAUSIBLE_BYTES = 1_000_000L
    }
}
