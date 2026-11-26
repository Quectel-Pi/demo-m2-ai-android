package com.quectel.m2aidemo

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.exifinterface.media.ExifInterface
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var classifier: ImageClassifier? = null
    private var selectedBitmap: Bitmap? = null
    private lateinit var imagePicker: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var preview: ImageView
    private lateinit var placeholder: View
    private lateinit var chooseImageButton: Button
    private lateinit var recognizeButton: Button
    private lateinit var exampleCards: List<View>
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var resultCard: View
    private lateinit var resultName: TextView
    private lateinit var resultScore: TextView
    private lateinit var inferenceTime: TextView
    private lateinit var predictionRows: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.back_button).setOnClickListener { finish() }
        preview = findViewById(R.id.image_preview)
        placeholder = findViewById(R.id.image_placeholder)
        chooseImageButton = findViewById(R.id.choose_image_button)
        recognizeButton = findViewById(R.id.recognize_button)
        exampleCards = listOf(
            findViewById(R.id.example_cat_card),
            findViewById(R.id.example_burger_card),
            findViewById(R.id.example_dog_card),
            findViewById(R.id.example_mug_card)
        )
        progress = findViewById(R.id.loading_progress)
        status = findViewById(R.id.model_status)
        resultCard = findViewById(R.id.result_card)
        resultName = findViewById(R.id.result_name)
        resultScore = findViewById(R.id.result_score)
        inferenceTime = findViewById(R.id.inference_time)
        predictionRows = findViewById(R.id.prediction_rows)

        imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) loadImage(uri)
        }

        chooseImageButton.setOnClickListener {
            imagePicker.launch("image/*")
        }
        findViewById<View>(R.id.example_cat_card).setOnClickListener {
            loadExample(R.drawable.example_cat)
        }
        findViewById<View>(R.id.example_burger_card).setOnClickListener {
            loadExample(R.drawable.example_burger)
        }
        findViewById<View>(R.id.example_dog_card).setOnClickListener {
            loadExample(R.drawable.example_dog)
        }
        findViewById<View>(R.id.example_mug_card).setOnClickListener {
            loadExample(R.drawable.example_mug)
        }
        recognizeButton.setOnClickListener { classifyImage() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun loadImage(uri: android.net.Uri) {
        setInputControlsEnabled(false)
        progress.visibility = View.VISIBLE
        status.text = getString(R.string.loading_image)
        worker.execute {
            val bitmap = runCatching { decodeBitmap(uri) }.getOrNull()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                progress.visibility = View.GONE
                if (bitmap == null) {
                    status.text = getString(R.string.image_load_failed)
                    setInputControlsEnabled(true)
                    return@runOnUiThread
                }
                showSelectedImage(bitmap)
                status.text = getString(R.string.image_ready)
                setInputControlsEnabled(true)
            }
        }
    }

    private fun loadExample(drawableId: Int) {
        setInputControlsEnabled(false)
        progress.visibility = View.VISIBLE
        status.text = getString(R.string.loading_image)
        worker.execute {
            val bitmap = runCatching {
                BitmapFactory.decodeResource(resources, drawableId)
            }.getOrNull()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                progress.visibility = View.GONE
                if (bitmap == null) {
                    status.text = getString(R.string.image_load_failed)
                    setInputControlsEnabled(true)
                    return@runOnUiThread
                }
                showSelectedImage(bitmap)
                classifyImage()
            }
        }
    }

    private fun showSelectedImage(bitmap: Bitmap) {
        val previous = selectedBitmap
        selectedBitmap = bitmap
        preview.setImageBitmap(bitmap)
        preview.visibility = View.VISIBLE
        placeholder.visibility = View.GONE
        resultCard.visibility = View.GONE
        if (previous !== bitmap) previous?.recycle()
    }

    private fun setInputControlsEnabled(enabled: Boolean) {
        chooseImageButton.isEnabled = enabled
        exampleCards.forEach { it.isEnabled = enabled }
        recognizeButton.isEnabled = enabled && selectedBitmap != null
    }

    private fun decodeBitmap(uri: android.net.Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 1200 || bounds.outHeight / sampleSize > 1200) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

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

    private fun classifyImage() {
        val bitmap = selectedBitmap ?: return
        setInputControlsEnabled(false)
        recognizeButton.text = getString(R.string.recognizing)
        progress.visibility = View.VISIBLE
        status.text = getString(R.string.running_on_device)

        worker.execute {
            val result = runCatching {
                val activeClassifier = classifier ?: ImageClassifier(this).also { classifier = it }
                activeClassifier.classify(bitmap)
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                progress.visibility = View.GONE
                setInputControlsEnabled(true)
                recognizeButton.text = getString(R.string.recognize)
                result.onSuccess { inference ->
                    if (inference.predictions.isEmpty()) {
                        status.text = getString(R.string.no_prediction)
                        return@onSuccess
                    }
                    showPredictions(inference.predictions, inference.inferenceTimeMs)
                    status.text = getString(R.string.inference_complete)
                }.onFailure {
                    status.text = getString(R.string.inference_failed)
                    android.widget.Toast.makeText(
                        this,
                        getString(R.string.inference_error, it.localizedMessage ?: it.javaClass.simpleName),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showPredictions(predictions: List<ImageClassifier.Prediction>, inferenceTimeMs: Long) {
        val best = predictions.first()
        resultName.text = best.label
        resultScore.text = getString(R.string.confidence_percent, (best.score * 100).toInt())
        inferenceTime.text = getString(R.string.inference_time, inferenceTimeMs)
        predictionRows.removeAllViews()

        predictions.forEachIndexed { index, prediction ->
            val row = TextView(this).apply {
                text = getString(
                    R.string.prediction_row,
                    index + 1,
                    prediction.label,
                    (prediction.score * 100).toInt()
                )
                setTextColor(getColor(R.color.text_secondary))
                textSize = if (
                    resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                ) 12f else 14f
                setPadding(0, resources.getDimensionPixelSize(R.dimen.prediction_row_vertical_padding), 0, 0)
            }
            predictionRows.addView(row)
        }
        resultCard.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        worker.execute {
            classifier?.close()
            classifier = null
        }
        worker.shutdown()
        super.onDestroy()
    }
}