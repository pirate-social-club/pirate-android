package sc.pirate.app.study

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import sc.pirate.app.R

/**
 * The two study outcome sounds, using the same assets as web.
 *
 * SoundPool rather than MediaPlayer because these fire on a verdict and must be audible
 * immediately; loading a MediaPlayer at that moment costs the exact delay that makes feedback feel
 * detached from the tap. Both clips are loaded up front for the same reason — web preloads them.
 *
 * Playback is deliberately best-effort: a device with audio unavailable still studies fine, so a
 * failure here never surfaces to the viewer.
 */
class StudyFeedbackSounds(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val correctId = pool.load(context, R.raw.study_correct, 1)
    private val incorrectId = pool.load(context, R.raw.study_incorrect, 1)
    private val loaded = mutableSetOf<Int>()

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loaded.add(sampleId)
        }
    }

    /**
     * Plays the outcome sound. Called only with a server verdict — never optimistically, so the
     * viewer never hears "correct" for something the server later rejects.
     */
    fun play(correct: Boolean) {
        val sample = if (correct) correctId else incorrectId
        if (sample !in loaded) return
        pool.play(sample, VOLUME, VOLUME, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }

    private companion object {
        /** Matches web, which plays these at 0.7. */
        const val VOLUME = 0.7f
    }
}

/** Loads the sounds for as long as the study surface is on screen, and releases them after. */
@Composable
fun rememberStudyFeedbackSounds(): StudyFeedbackSounds {
    val context = LocalContext.current
    val sounds = remember { StudyFeedbackSounds(context) }
    DisposableEffect(sounds) {
        onDispose { sounds.release() }
    }
    return sounds
}
