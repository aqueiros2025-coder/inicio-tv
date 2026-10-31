package com.example.tvlauncher

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object QrCode {
    // QR Code preto sobre branco, com margem, para ser lido facilmente pela câmara do telemóvel.
    fun bitmap(text: String, size: Int): Bitmap {
        val hints = HashMap<EncodeHintType, Any>()
        hints[EncodeHintType.MARGIN] = 2
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                pixels[y * w + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
}
