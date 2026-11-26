package com.quectel.m2aidemo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import org.tensorflow.lite.support.metadata.MetadataExtractor
import java.io.Closeable
import java.nio.ByteBuffer

internal class ImageClassifier(context: Context) : Closeable {
    private val labels: List<String>
    private val model: CompiledModel
    private val inputWidth: Int
    private val inputHeight: Int

    init {
        val modelBytes = context.assets.open(MODEL_FILE).use { it.readBytes() }
        val metadata = MetadataExtractor(ByteBuffer.wrap(modelBytes))
        check(metadata.hasMetadata()) { "The image model does not contain label metadata." }
        val inputShape = metadata.getInputTensorShape(0)
        check(inputShape.size == 4 && inputShape[3] == CHANNELS) {
            "Expected a single NHWC RGB image input, got ${inputShape.contentToString()}."
        }
        inputHeight = inputShape[1]
        inputWidth = inputShape[2]
        check(inputWidth > 0 && inputHeight > 0) { "The model input shape is invalid." }
        labels = metadata.getAssociatedFile(LABEL_FILE).bufferedReader().useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
        }
        check(labels.isNotEmpty()) { "The image model label list is empty." }
        model = CompiledModel.create(
            context.assets,
            MODEL_FILE,
            CompiledModel.Options(Accelerator.CPU),
            null
        )
    }

    fun classify(bitmap: Bitmap): InferenceResult {
        val inputBitmap = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        val pixels = IntArray(inputWidth * inputHeight)
        inputBitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        val input = FloatArray(pixels.size * CHANNELS)
        pixels.forEachIndexed { index, pixel ->
            val offset = index * CHANNELS
            input[offset] = (Color.red(pixel) - PIXEL_MEAN) / PIXEL_STDDEV
            input[offset + 1] = (Color.green(pixel) - PIXEL_MEAN) / PIXEL_STDDEV
            input[offset + 2] = (Color.blue(pixel) - PIXEL_MEAN) / PIXEL_STDDEV
        }
        if (inputBitmap !== bitmap) inputBitmap.recycle()

        val inputBuffers = model.createInputBuffers()
        val outputBuffers = model.createOutputBuffers()
        var inferenceTimeMs = 0L
        val scores = try {
            inputBuffers[0].writeFloat(input)
            val startedAt = SystemClock.elapsedRealtime()
            model.run(inputBuffers, outputBuffers)
            inferenceTimeMs = SystemClock.elapsedRealtime() - startedAt
            outputBuffers[0].readFloat()
        } finally {
            inputBuffers.forEach { it.close() }
            outputBuffers.forEach { it.close() }
        }

        val scoreOffset = when (scores.size) {
            labels.size -> 0
            labels.size + 1 -> 1
            else -> error(
                "The model returned ${scores.size} scores for ${labels.size} labels."
            )
        }
        val predictions = labels.indices
            .map { index -> labels[index] to scores[index + scoreOffset] }
            .sortedByDescending { it.second }
            .take(RESULT_COUNT)
            .map { (label, score) -> Prediction(label.replace('_', ' '), score) }
        return InferenceResult(predictions, inferenceTimeMs)
    }

    override fun close() {
        model.close()
    }

    data class Prediction(val label: String, val score: Float)

    data class InferenceResult(val predictions: List<Prediction>, val inferenceTimeMs: Long)

    private companion object {
        const val MODEL_FILE = "efficientnet_lite2.tflite"
        const val LABEL_FILE = "labels_without_background.txt"
        const val CHANNELS = 3
        const val PIXEL_MEAN = 127.5f
        const val PIXEL_STDDEV = 127.5f
        const val RESULT_COUNT = 3
    }
}
