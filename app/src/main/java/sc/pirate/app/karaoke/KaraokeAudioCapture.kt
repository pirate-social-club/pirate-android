package sc.pirate.app.karaoke

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

const val KARAOKE_CAPTURE_CHUNK_BYTES: Int = 3_200

data class KaraokeCapturedChunk(
    val chunkId: Long,
    val captureStartMs: Long,
    val pcm16MonoLittleEndian: ByteArray,
) {
    val captureDurationMs: Long = karaokePcmDurationMs(pcm16MonoLittleEndian.size)
}

interface KaraokeAudioCapture {
    fun start(
        scope: CoroutineScope,
        onChunk: (KaraokeCapturedChunk) -> Unit,
        onFailure: (String) -> Unit,
    )

    fun stop()

    fun resetChunkIds()
}

class AudioRecordKaraokeCapture(
    private val captureClockMs: () -> Long = { System.nanoTime() / 1_000_000L },
) : KaraokeAudioCapture {
    private var recorder: AudioRecord? = null
    private var captureJob: Job? = null
    private var nextChunkId: Long = 1

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun start(
        scope: CoroutineScope,
        onChunk: (KaraokeCapturedChunk) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        if (captureJob != null) return
        val audioRecord = try {
            createAudioRecord()
        } catch (error: Exception) {
            onFailure(error.message ?: "Could not start microphone capture.")
            return
        }
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            onFailure("Microphone capture is unavailable.")
            return
        }

        recorder = audioRecord
        captureJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(KARAOKE_CAPTURE_CHUNK_BYTES)
            try {
                audioRecord.startRecording()
                while (isActive) {
                    val startedAt = captureClockMs()
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    when {
                        read > 0 -> {
                            onChunk(
                                KaraokeCapturedChunk(
                                    chunkId = nextChunkId++,
                                    captureStartMs = startedAt,
                                    pcm16MonoLittleEndian = buffer.copyOf(read),
                                ),
                            )
                        }
                        read == AudioRecord.ERROR_INVALID_OPERATION -> {
                            onFailure("Microphone capture is not initialized.")
                            break
                        }
                        read == AudioRecord.ERROR_BAD_VALUE -> {
                            onFailure("Microphone capture buffer is invalid.")
                            break
                        }
                        read == AudioRecord.ERROR_DEAD_OBJECT -> {
                            onFailure("Microphone capture stopped unexpectedly.")
                            break
                        }
                    }
                }
            } catch (error: SecurityException) {
                onFailure("Microphone permission is required.")
            } catch (error: Exception) {
                onFailure(error.message ?: "Microphone capture failed.")
            } finally {
                stop()
            }
        }
    }

    override fun stop() {
        captureJob?.cancel()
        captureJob = null
        recorder?.let { audioRecord ->
            runCatching {
                if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop()
                }
            }
            audioRecord.release()
        }
        recorder = null
    }

    override fun resetChunkIds() {
        nextChunkId = 1
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(): AudioRecord {
        val minBufferBytes = AudioRecord.getMinBufferSize(
            KARAOKE_AUDIO_SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minBufferBytes > 0) { "Microphone capture does not support 16 kHz PCM." }
        val bufferBytes = maxOf(minBufferBytes, KARAOKE_CAPTURE_CHUNK_BYTES * 2)
        return AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            KARAOKE_AUDIO_SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferBytes,
        )
    }
}
