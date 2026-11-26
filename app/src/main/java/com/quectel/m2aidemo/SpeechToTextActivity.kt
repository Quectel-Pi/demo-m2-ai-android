package com.quectel.m2aidemo

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

class SpeechToTextActivity : AppCompatActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val audioLock = Any()
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var status: TextView
    private lateinit var resultText: TextView
    @Volatile private var model: Model? = null
    @Volatile private var isRecording = false
    private var isProcessing = false
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var recordedAudio: ByteArrayOutputStream? = null

    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startRecording()
            } else {
                status.text = getString(R.string.speech_permission_denied)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_speech_to_text)

        startButton = findViewById(R.id.speech_start_button)
        stopButton = findViewById(R.id.speech_stop_button)
        status = findViewById(R.id.speech_status)
        resultText = findViewById(R.id.speech_result_text)

        findViewById<View>(R.id.speech_back_button).setOnClickListener { finish() }
        startButton.setOnClickListener { requestRecording() }
        stopButton.setOnClickListener { stopRecording() }
        startButton.isEnabled = false
        status.text = getString(R.string.speech_model_loading)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.speech_root)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        worker.execute {
            try {
                val modelDirectory = unpackModel()
                val loadedModel = Model(modelDirectory.absolutePath)
                model = loadedModel
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    status.text = getString(R.string.speech_ready)
                    updateButtons()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    status.text = getString(
                        R.string.speech_model_error,
                        error.localizedMessage ?: error.javaClass.simpleName
                    )
                }
            }
        }
    }

    private fun requestRecording() {
        if (model == null) {
            status.text = getString(R.string.speech_model_loading)
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        val minimumBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minimumBufferSize <= 0) {
            status.text = getString(R.string.speech_recording_error)
            return
        }

        val bufferSize = maxOf(minimumBufferSize, READ_BUFFER_SIZE)
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (_: IllegalArgumentException) {
            status.text = getString(R.string.speech_recording_error)
            return
        } catch (_: SecurityException) {
            status.text = getString(R.string.speech_permission_denied)
            return
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            status.text = getString(R.string.speech_recording_error)
            return
        }

        try {
            recorder.startRecording()
        } catch (_: IllegalStateException) {
            recorder.release()
            status.text = getString(R.string.speech_recording_error)
            return
        } catch (_: SecurityException) {
            recorder.release()
            status.text = getString(R.string.speech_permission_denied)
            return
        }

        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            recorder.release()
            status.text = getString(R.string.speech_recording_error)
            return
        }

        audioRecord = recorder
        synchronized(audioLock) {
            recordedAudio = ByteArrayOutputStream()
        }
        isRecording = true
        isProcessing = false
        resultText.text = getString(R.string.speech_result_placeholder)
        status.text = getString(R.string.speech_listening)
        updateButtons()

        recordingThread = Thread({
            val buffer = ByteArray(bufferSize)
            var readFailed = false
            while (isRecording) {
                val count = try {
                    recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                } catch (_: IllegalStateException) {
                    readFailed = true
                    break
                } catch (_: SecurityException) {
                    readFailed = true
                    break
                }
                if (count > 0) {
                    synchronized(audioLock) {
                        recordedAudio?.write(buffer, 0, count)
                    }
                } else if (count < 0 && isRecording) {
                    readFailed = true
                    break
                }
            }
            if (readFailed) {
                runOnUiThread {
                    if (isRecording && !isFinishing && !isDestroyed) {
                        stopRecording(readFailed = true)
                    }
                }
            }
        }, "speech-audio-capture").apply { start() }
    }

    private fun stopRecording(readFailed: Boolean = false) {
        if (!isRecording) return
        isRecording = false
        isProcessing = true
        status.text = getString(R.string.speech_processing)
        updateButtons()

        val recorder = audioRecord
        audioRecord = null
        val captureThread = recordingThread
        recordingThread = null
        var stopFailed = false
        try {
            recorder?.stop()
        } catch (_: IllegalStateException) {
            stopFailed = true
        } catch (_: SecurityException) {
            stopFailed = true
        }

        worker.execute {
            try {
                captureThread?.join()
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                finishWithStatus(R.string.speech_recording_error)
                recorder?.release()
                return@execute
            }
            recorder?.release()

            if (readFailed || stopFailed) {
                finishWithStatus(R.string.speech_recording_error)
                return@execute
            }

            val pcmAudio = synchronized(audioLock) {
                recordedAudio?.toByteArray().also { recordedAudio = null }
            } ?: ByteArray(0)
            if (pcmAudio.isEmpty()) {
                finishWithStatus(R.string.speech_empty_audio)
                return@execute
            }
            transcribe(pcmAudio)
        }
    }

    private fun transcribe(pcmAudio: ByteArray) {
        val loadedModel = model
        if (loadedModel == null) {
            finishWithStatus(R.string.speech_model_loading)
            return
        }

        try {
            val recognizer = Recognizer(loadedModel, SAMPLE_RATE.toFloat())
            val resultJson: String
            try {
                val buffer = ByteArray(READ_BUFFER_SIZE)
                var offset = 0
                while (offset < pcmAudio.size) {
                    val count = minOf(buffer.size, pcmAudio.size - offset)
                    System.arraycopy(pcmAudio, offset, buffer, 0, count)
                    recognizer.acceptWaveForm(buffer, count)
                    offset += count
                }
                resultJson = recognizer.finalResult
            } finally {
                recognizer.close()
            }

            val transcription = JSONObject(resultJson).optString("text").trim()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                isProcessing = false
                updateButtons()
                if (transcription.isBlank()) {
                    status.text = getString(R.string.speech_no_match)
                } else {
                    resultText.text = transcription
                    status.text = getString(R.string.speech_complete)
                }
            }
        } catch (error: Exception) {
            finishWithStatus(
                R.string.speech_recognition_error,
                error.localizedMessage ?: error.javaClass.simpleName
            )
        }
    }

    private fun finishWithStatus(message: Int, detail: String? = null) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            isProcessing = false
            updateButtons()
            status.text = if (detail == null) getString(message) else getString(message, detail)
        }
    }

    private fun updateButtons() {
        startButton.isEnabled = model != null && !isRecording && !isProcessing
        stopButton.isEnabled = isRecording
    }

    private fun unpackModel(): File = synchronized(modelInstallLock) {
        val destination = File(filesDir, MODEL_DIRECTORY)
        if (isModelDirectoryValid(destination)) return@synchronized destination

        val staging = File(filesDir, "$MODEL_DIRECTORY.tmp")
        staging.deleteRecursively()
        if (!staging.mkdirs()) throw IOException("Could not create model directory.")

        try {
            ZipInputStream(assets.open(MODEL_ARCHIVE)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val normalizedName = entry.name.replace('\\', '/')
                    if (normalizedName.startsWith("/") ||
                        normalizedName.split('/').any { it == ".." }
                    ) {
                        throw IOException("Invalid model archive path.")
                    }
                    val rootPrefix = "$MODEL_ARCHIVE_ROOT/"
                    if (normalizedName != MODEL_ARCHIVE_ROOT &&
                        !normalizedName.startsWith(rootPrefix)
                    ) {
                        throw IOException("Unexpected model archive entry.")
                    }
                    val relativePath = normalizedName.removePrefix(rootPrefix)
                    if (relativePath.isNotEmpty()) {
                        val output = File(staging, relativePath)
                        val stagingPath = staging.canonicalPath + File.separator
                        if (!output.canonicalPath.startsWith(stagingPath)) {
                            throw IOException("Invalid model archive path.")
                        }
                        if (entry.isDirectory) {
                            if (!output.isDirectory && !output.mkdirs()) {
                                throw IOException("Could not create model directory.")
                            }
                        } else {
                            output.parentFile?.let { parent ->
                                if (!parent.isDirectory && !parent.mkdirs()) {
                                    throw IOException("Could not create model directory.")
                                }
                            }
                            output.outputStream().use { zip.copyTo(it) }
                        }
                    }
                    zip.closeEntry()
                }
            }

            if (!isModelDirectoryValid(staging)) {
                throw IOException("Offline English model files are incomplete.")
            }
            destination.deleteRecursively()
            if (!staging.renameTo(destination)) {
                throw IOException("Could not install offline English model.")
            }
            return@synchronized destination
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        }
    }

    private fun isModelDirectoryValid(directory: File): Boolean =
        File(directory, "am/final.mdl").isFile &&
            File(directory, "conf/mfcc.conf").isFile &&
            File(directory, "graph/HCLr.fst").isFile &&
            File(directory, "graph/Gr.fst").isFile

    override fun onDestroy() {
        isRecording = false
        val recorder = audioRecord
        audioRecord = null
        try {
            if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
        } catch (_: IllegalStateException) {
            // The recorder may already have stopped while the activity was closing.
        } catch (_: SecurityException) {
            // Microphone access may have been revoked while the activity was closing.
        }
        recorder?.release()
        recordingThread?.interrupt()
        recordingThread = null
        worker.execute {
            model?.close()
            model = null
        }
        worker.shutdown()
        super.onDestroy()
    }

    private companion object {
        val modelInstallLock = Any()
        const val SAMPLE_RATE = 16_000
        const val READ_BUFFER_SIZE = 8_192
        const val MODEL_DIRECTORY = "vosk-model-en-us"
        const val MODEL_ARCHIVE_ROOT = "vosk-model-small-en-us-0.15"
        const val MODEL_ARCHIVE = "vosk-model-small-en-us-0.15.zip"
    }
}
