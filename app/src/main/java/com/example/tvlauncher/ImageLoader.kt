package com.example.tvlauncher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

// Carregador de imagens simples: 3 descarregamentos em simultâneo, cache em memória (8 MB),
// e as imagens são reduzidas ao tamanho do cartão para poupar RAM.
object ImageLoader {

    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val executor = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun load(url: String, reqW: Int, reqH: Int, onDone: (Bitmap?) -> Unit) {
        val hit = cache.get(url)
        if (hit != null) {
            onDone(hit)
            return
        }
        executor.execute {
            val bmp = try {
                download(url, reqW, reqH)
            } catch (e: Exception) {
                null
            }
            if (bmp != null) cache.put(url, bmp)
            main.post { onDone(bmp) }
        }
    }

    private fun download(url: String, reqW: Int, reqH: Int): Bitmap? {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.setRequestProperty("User-Agent", "InicioTV/1.0")
            val bytes = c.inputStream.use { it.readBytes() }
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) {
                sample *= 2
            }
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } finally {
            c.disconnect()
        }
    }
}
