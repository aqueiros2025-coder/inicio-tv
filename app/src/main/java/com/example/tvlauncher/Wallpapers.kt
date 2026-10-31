package com.example.tvlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class GradientPreset(
    val id: String,
    val name: String,
    val animated: Boolean,
    val colors: IntArray,
    val waves: Boolean = false
)

data class WallpaperFile(
    val id: String,
    val displayName: String,
    val file: File,
    val isVideo: Boolean
)

object Wallpapers {
    const val DEFAULT_ID = "builtin:ondas"

    // Fundos incluídos na app (não precisam de descarregar nada).
    val BUILTINS: List<GradientPreset> = listOf(
        GradientPreset(
            "builtin:ondas", "Ondas azuis", true,
            intArrayOf(0xFF020B24.toInt(), 0xFF0A2A6E.toInt(), 0xFF1458B8.toInt()),
            waves = true
        ),
        GradientPreset(
            "builtin:ondas_fixo", "Ondas azuis", false,
            intArrayOf(0xFF020B24.toInt(), 0xFF0A2A6E.toInt(), 0xFF1458B8.toInt()),
            waves = true
        ),
        GradientPreset(
            "builtin:noite", "Noite", false,
            intArrayOf(0xFF0B1020.toInt(), 0xFF1B1740.toInt(), 0xFF3A1C5C.toInt())
        ),
        GradientPreset(
            "builtin:oceano", "Oceano", false,
            intArrayOf(0xFF021B2B.toInt(), 0xFF05456B.toInt(), 0xFF0B7A8F.toInt())
        ),
        GradientPreset(
            "builtin:por_do_sol", "Pôr do sol", false,
            intArrayOf(0xFF1A0F2E.toInt(), 0xFF6B1F4B.toInt(), 0xFFE0613B.toInt())
        ),
        GradientPreset(
            "builtin:aurora", "Aurora", true,
            intArrayOf(0xFF0B1020.toInt(), 0xFF0F4C5C.toInt(), 0xFF3A1C5C.toInt(), 0xFF0B1020.toInt())
        ),
        GradientPreset(
            "builtin:brasas", "Brasas", true,
            intArrayOf(0xFF1A0505.toInt(), 0xFF7A1F0B.toInt(), 0xFF2B0B3A.toInt(), 0xFF1A0505.toInt())
        )
    )

    fun preset(id: String): GradientPreset? = BUILTINS.firstOrNull { it.id == id }
}

// Fundos guardados no armazenamento privado da app: imagens (fixos) e vídeos (animados).
object WallpaperFiles {
    const val MAX_BYTES = 300L * 1024L * 1024L

    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp")
    private val VIDEO_EXT = setOf("mp4", "webm")

    fun isAllowedExt(ext: String): Boolean = ext in IMAGE_EXT || ext in VIDEO_EXT

    fun dir(context: Context): File {
        val d = File(context.filesDir, "wallpapers")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun list(context: Context): List<WallpaperFile> {
        val files = dir(context).listFiles() ?: return emptyList()
        return files
            .filter { it.isFile && isAllowedExt(it.extension.lowercase()) }
            .sortedBy { it.name }
            .map { toEntry(it) }
    }

    fun toEntry(f: File): WallpaperFile {
        val ext = f.extension.lowercase()
        val base = f.nameWithoutExtension
        val shown = base.substringAfter('_', base).replace('_', ' ')
        return WallpaperFile("file:" + f.name, shown, f, ext in VIDEO_EXT)
    }

    fun find(context: Context, id: String): WallpaperFile? {
        if (!id.startsWith("file:")) return null
        return list(context).firstOrNull { it.id == id }
    }

    fun newFile(context: Context, base: String, ext: String): File {
        val clean = base
            .map { if (it.isLetterOrDigit() || it == '-') it else '_' }
            .joinToString("")
            .trim('_')
            .take(40)
            .ifEmpty { "fundo" }
        return File(dir(context), System.currentTimeMillis().toString() + "_" + clean + "." + ext)
    }

    // Abre o endereço a seguir redirecionamentos (incluindo de http para https).
    private fun openFollowing(address: String): HttpURLConnection {
        var current = address
        var redirects = 0
        while (true) {
            val url = URL(current)
            if (url.protocol != "http" && url.protocol != "https") {
                throw IllegalArgumentException("endereço inválido")
            }
            val c = url.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.setRequestProperty("User-Agent", "InicioTV/1.0")
            val code = c.responseCode
            if (code in 300..399) {
                val location = c.getHeaderField("Location")
                c.disconnect()
                redirects++
                if (location == null || redirects > 5) {
                    throw IllegalStateException("demasiados redirecionamentos")
                }
                current = URL(url, location).toString()
                continue
            }
            if (code != 200) {
                c.disconnect()
                throw IllegalStateException("o servidor respondeu " + code)
            }
            return c
        }
    }

    fun download(context: Context, address: String, baseName: String?): File {
        val c = openFollowing(address)
        try {
            val type = (c.contentType ?: "").substringBefore(';').trim().lowercase()
            var ext = when (type) {
                "image/jpeg", "image/jpg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                "video/mp4" -> "mp4"
                "video/webm" -> "webm"
                else -> ""
            }
            val path = URL(address).path ?: ""
            if (ext.isEmpty()) {
                val fromUrl = path.substringAfterLast('.', "").lowercase()
                if (isAllowedExt(fromUrl)) ext = fromUrl
            }
            if (ext.isEmpty()) {
                throw IllegalArgumentException("formato não suportado (usa JPG, PNG, WEBP, MP4 ou WEBM)")
            }
            if (c.contentLengthLong > MAX_BYTES) {
                throw IllegalStateException("ficheiro demasiado grande")
            }

            val base = baseName
                ?: path.substringAfterLast('/').substringBeforeLast('.').ifEmpty { URL(address).host }
            val dest = newFile(context, base, ext)
            val tmp = File(dest.parentFile, dest.name + ".part")
            try {
                var total = 0L
                c.inputStream.use { input ->
                    FileOutputStream(tmp).use { out ->
                        val buf = ByteArray(65536)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_BYTES) throw IllegalStateException("ficheiro demasiado grande")
                            out.write(buf, 0, n)
                        }
                    }
                }
                if (!tmp.renameTo(dest)) throw IllegalStateException("não foi possível guardar o ficheiro")
            } catch (e: Exception) {
                tmp.delete()
                throw e
            }
            return dest
        } finally {
            c.disconnect()
        }
    }

    // Lê a imagem já reduzida ao tamanho do ecrã, para não gastar RAM à box.
    fun decodeSampled(file: File, reqW: Int, reqH: Int): Bitmap? {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) {
            sample *= 2
        }
        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        return try {
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}
