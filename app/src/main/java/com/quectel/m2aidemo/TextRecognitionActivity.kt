package com.quectel.m2aidemo

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

class TextRecognitionActivity : AppCompatActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var latinRecognizer: TextRecognizer
    private lateinit var chineseRecognizer: TextRecognizer
    private lateinit var imagePicker: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var preview: ImageView
    private lateinit var placeholder: View
    private lateinit var chooseImageButton: Button
    private lateinit var recognizeButton: Button
    private lateinit var sampleCards: List<View>
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var resultCard: View
    private lateinit var resultText: TextView
    private var selectedBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_text_recognition)

        latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        chineseRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

        findViewById<View>(R.id.ocr_back_button).setOnClickListener { finish() }
        preview = findViewById(R.id.ocr_image_preview)
        placeholder = findViewById(R.id.ocr_image_placeholder)
        chooseImageButton = findViewById(R.id.ocr_choose_image_button)
        recognizeButton = findViewById(R.id.ocr_recognize_button)
        progress = findViewById(R.id.ocr_loading_progress)
        status = findViewById(R.id.ocr_status)
        resultCard = findViewById(R.id.ocr_result_card)
        resultText = findViewById(R.id.ocr_result_text)

        configureExamples()
        imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) loadImage(uri)
        }
        chooseImageButton.setOnClickListener { imagePicker.launch("image/*") }
        recognizeButton.setOnClickListener { recognizeText() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.ocr_root)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun configureExamples() {
        val examples = listOf(
            SampleSpec(
                R.id.ocr_receipt_card,
                R.id.ocr_receipt_image,
                OcrSampleImages.Kind.RECEIPT
            ),
            SampleSpec(
                R.id.ocr_menu_card,
                R.id.ocr_menu_image,
                OcrSampleImages.Kind.MENU
            ),
            SampleSpec(
                R.id.ocr_sign_card,
                R.id.ocr_sign_image,
                OcrSampleImages.Kind.SIGN
            )
        )
        sampleCards = examples.map { example ->
            val bitmap = OcrSampleImages.create(example.kind)
            findViewById<ImageView>(example.imageId).setImageBitmap(bitmap)
            findViewById<View>(example.cardId).apply {
                setOnClickListener {
                    showSelectedImage(bitmap)
                    recognizeText()
                }
            }
        }
    }

    private fun loadImage(uri: Uri) {
        setInputControlsEnabled(false)
        progress.visibility = View.VISIBLE
        status.text = getString(R.string.loading_image)
        worker.execute {
            val outcome = try {
                Result.success(decodeBitmap(uri))
            } catch (error: Exception) {
                Result.failure(error)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                progress.visibility = View.GONE
                outcome.fold(
                    onSuccess = { bitmap ->
                        showSelectedImage(bitmap)
                        status.text = getString(R.string.image_ready)
                        setInputControlsEnabled(true)
                    },
                    onFailure = {
                        status.text = getString(R.string.image_load_failed)
                        setInputControlsEnabled(true)
                        Toast.makeText(this, getString(R.string.image_load_failed), Toast.LENGTH_LONG)
                            .show()
                    }
                )
            }
        }
    }

    private fun showSelectedImage(bitmap: Bitmap) {
        selectedBitmap = bitmap
        preview.setImageBitmap(bitmap)
        preview.visibility = View.VISIBLE
        placeholder.visibility = View.GONE
        resultCard.visibility = View.GONE
        recognizeButton.isEnabled = true
    }

    private fun recognizeText() {
        val bitmap = selectedBitmap ?: return
        setInputControlsEnabled(false)
        recognizeButton.text = getString(R.string.recognizing)
        progress.visibility = View.VISIBLE
        status.text = getString(R.string.ocr_reading)
        resultCard.visibility = View.GONE

        val latinTask: Task<Text>
        val chineseTask: Task<Text>
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            latinTask = latinRecognizer.process(image)
            chineseTask = chineseRecognizer.process(image)
        } catch (error: Exception) {
            showRecognitionFailure(error)
            return
        }

        Tasks.whenAllComplete(latinTask, chineseTask).addOnCompleteListener(this) {
            if (isFinishing || isDestroyed) return@addOnCompleteListener
            progress.visibility = View.GONE
            setInputControlsEnabled(true)
            recognizeButton.text = getString(R.string.recognize)

            val latinText = if (latinTask.isSuccessful) latinTask.result?.text.orEmpty() else ""
            val chineseText = if (chineseTask.isSuccessful) chineseTask.result?.text.orEmpty() else ""
            val recognizedText = when {
                chineseText.any { it in '\u4e00'..'\u9fff' } -> chineseText
                latinText.isNotBlank() -> latinText
                else -> chineseText
            }.trim()

            if (recognizedText.isBlank()) {
                if (!latinTask.isSuccessful && !chineseTask.isSuccessful) {
                    showRecognitionFailure(latinTask.exception ?: chineseTask.exception)
                } else {
                    status.text = getString(R.string.ocr_no_text)
                }
                return@addOnCompleteListener
            }

            resultText.text = recognizedText
            resultCard.visibility = View.VISIBLE
            status.text = getString(R.string.ocr_complete)
        }
    }

    private fun showRecognitionFailure(error: Throwable?) {
        progress.visibility = View.GONE
        setInputControlsEnabled(true)
        recognizeButton.text = getString(R.string.recognize)
        status.text = getString(R.string.ocr_failed)
        Toast.makeText(
            this,
            getString(R.string.ocr_error, error?.localizedMessage ?: error?.javaClass?.simpleName.orEmpty()),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun setInputControlsEnabled(enabled: Boolean) {
        chooseImageButton.isEnabled = enabled
        sampleCards.forEach { it.isEnabled = enabled }
        recognizeButton.isEnabled = enabled && selectedBitmap != null
    }

    private fun decodeBitmap(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image dimensions." }

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 1600 || bounds.outHeight / sampleSize > 1600) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("Unable to decode selected image.")

        val exifOrientation = contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
        val orientation = when (exifOrientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> 0 to true
            ExifInterface.ORIENTATION_ROTATE_180 -> 180 to false
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180 to true
            ExifInterface.ORIENTATION_TRANSPOSE -> 90 to true
            ExifInterface.ORIENTATION_ROTATE_90 -> 90 to false
            ExifInterface.ORIENTATION_TRANSVERSE -> 270 to true
            ExifInterface.ORIENTATION_ROTATE_270 -> 270 to false
            else -> 0 to false
        }
        if (orientation.first == 0 && !orientation.second) return bitmap

        val transform = Matrix().apply {
            postRotate(orientation.first.toFloat())
            if (orientation.second) postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }

    override fun onDestroy() {
        latinRecognizer.close()
        chineseRecognizer.close()
        worker.shutdown()
        super.onDestroy()
    }

    private data class SampleSpec(
        val cardId: Int,
        val imageId: Int,
        val kind: OcrSampleImages.Kind
    )
}
