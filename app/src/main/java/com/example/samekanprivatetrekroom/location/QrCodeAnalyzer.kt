package com.example.samekanprivatetrekroom.location

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer

class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true
        )
        setHints(hints)
    }

    override fun analyze(image: ImageProxy) {
        // Strip stride padding from Y (Luminance) plane
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val width = image.width
        val height = image.height

        val data = ByteArray(width * height)
        val rowBuffer = ByteArray(rowStride)

        buffer.rewind()
        for (y in 0 until height) {
            val length = Math.min(rowStride, buffer.remaining())
            buffer.get(rowBuffer, 0, length)
            System.arraycopy(rowBuffer, 0, data, y * width, width)
        }

        val source = PlanarYUVLuminanceSource(
            data, width, height, 0, 0, width, height, false
        )
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

        try {
            val result = reader.decode(binaryBitmap)
            onQrCodeScanned(result.text)
        } catch (e: NotFoundException) {
            // No barcode found in this frame, continue
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            image.close()
        }
    }
}
