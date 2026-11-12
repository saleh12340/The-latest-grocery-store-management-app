package com.saleh.alazzi.grocery.printing

import android.graphics.Bitmap

/**
 * ESC/POS GS v 0 raster encoder. Each returned byte array is an independent
 * image band so Bluetooth writes stay bounded on low-memory phones.
 */
object EscPosRasterEncoder {
    fun encode(bitmap: Bitmap, maxRowsPerBand: Int = 256, threshold: Int = 160): List<ByteArray> {
        require(!bitmap.isRecycled) { "Bitmap has been recycled" }
        require(bitmap.width > 0 && bitmap.height > 0)
        require(maxRowsPerBand in 1..2048)
        val widthBytes = (bitmap.width + 7) / 8
        val bands = ArrayList<ByteArray>()
        var top = 0

        while (top < bitmap.height) {
            val rows = minOf(maxRowsPerBand, bitmap.height - top)
            val payload = ByteArray(widthBytes * rows)
            for (row in 0 until rows) {
                for (column in 0 until bitmap.width) {
                    val color = bitmap.getPixel(column, top + row)
                    val red = (color shr 16) and 0xFF
                    val green = (color shr 8) and 0xFF
                    val blue = color and 0xFF
                    val luminance = (red * 299 + green * 587 + blue * 114) / 1000
                    if (luminance < threshold) {
                        val index = row * widthBytes + column / 8
                        payload[index] = (payload[index].toInt() or (0x80 shr (column % 8))).toByte()
                    }
                }
            }

            val header = byteArrayOf(
                0x1D, 0x76, 0x30, 0x00,
                (widthBytes and 0xFF).toByte(),
                ((widthBytes shr 8) and 0xFF).toByte(),
                (rows and 0xFF).toByte(),
                ((rows shr 8) and 0xFF).toByte()
            )
            bands += header + payload
            top += rows
        }
        return bands
    }
}
