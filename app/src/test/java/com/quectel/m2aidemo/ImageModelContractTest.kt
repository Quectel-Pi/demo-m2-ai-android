package com.quectel.m2aidemo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.tensorflow.lite.support.metadata.MetadataExtractor
import java.io.File
import java.nio.ByteBuffer

class ImageModelContractTest {
    @Test
    fun modelInputAndLabelsMatchImageClassifier() {
        val modelFile = File("src/main/assets/efficientnet_lite2.tflite")
        val modelBytes = modelFile.readBytes()
        val metadata = MetadataExtractor(ByteBuffer.wrap(modelBytes))
        val inputShape = metadata.getInputTensorShape(0)
        val outputShape = metadata.getOutputTensorShape(0)
        val labelCount = metadata.getAssociatedFile("labels_without_background.txt")
            .bufferedReader(Charsets.UTF_8)
            .useLines { lines -> lines.count() }

        assertEquals(4, inputShape.size)
        assertEquals(3, inputShape.last())
        assertEquals(labelCount, outputShape.last())
        assertEquals(0, metadata.getInputTensorType(0).toInt())
        assertEquals(0, metadata.getOutputTensorType(0).toInt())
    }
}
