package sc.pirate.app.study

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import sc.pirate.app.ui.ButtonVariant
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.FormTone
import sc.pirate.app.ui.PirateButton
import java.io.File

private const val MAX_RECORDING_MS = 30_000

@Composable
internal fun StudyAudioRecorder(
    enabled: Boolean,
    transcribing: Boolean,
    onRecordingReady: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var recording by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun finishRecording(submit: Boolean) {
        val active = recorder
        recorder = null
        recording = false
        try {
            active?.stop()
            if (submit) outputFile?.let(onRecordingReady)
        } catch (_: RuntimeException) {
            outputFile?.delete()
            error = "Recording was too short. Hold the button a little longer."
        } finally {
            active?.release()
            if (!submit) outputFile?.delete()
            outputFile = null
        }
    }

    fun startRecording() {
        error = null
        val file = File.createTempFile("study-", ".m4a", context.cacheDir)
        val created = createRecorder(context)
        try {
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            created.setAudioEncodingBitRate(96_000)
            created.setAudioSamplingRate(44_100)
            created.setMaxDuration(MAX_RECORDING_MS)
            created.setOutputFile(file.absolutePath)
            created.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    finishRecording(submit = true)
                }
            }
            created.prepare()
            created.start()
            recorder = created
            outputFile = file
            recording = true
        } catch (e: Exception) {
            created.release()
            file.delete()
            error = "Could not start recording. Check microphone access and try again."
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startRecording() else error = "Microphone access is needed to record your answer."
    }

    DisposableEffect(Unit) {
        onDispose { finishRecording(submit = false) }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PirateButton(
            text = when {
                transcribing -> "Transcribing…"
                recording -> "Stop and transcribe"
                else -> "Record your answer"
            },
            onClick = {
                if (recording) {
                    finishRecording(submit = true)
                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startRecording()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            enabled = enabled && !transcribing,
            variant = ButtonVariant.Outline,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { FormNote(message = it, tone = FormTone.Error) }
    }
}

@Suppress("DEPRECATION")
private fun createRecorder(context: Context): MediaRecorder =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
