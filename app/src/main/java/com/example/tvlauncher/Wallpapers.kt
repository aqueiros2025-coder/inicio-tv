package com.example.tvlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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

class PortraitVideoException : Exception("o vídeo tem de ser horizontal (16:9)")

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

data class CatalogItem(
    val name: String,
    val urls: List<String>,
    val video: Boolean
)

// Fundos selecionados para descarregar. Cada item tem um ou mais endereços: a app tenta o primeiro
// e, se falhar (por exemplo, resolução inexistente), passa ao seguinte.
//  - Imagens: Wikimedia Commons (imagens em destaque da NASA, ESA e ESO), pedidas já a 1920 px.
//  - Vídeos: Mixkit (licença gratuita Mixkit), em ciclo, com tentativa de 1080p, depois 720p e 360p.
object Catalog {

    private fun commons(file: String): List<String> {
        val encoded = URLEncoder.encode(file, "UTF-8").replace("+", "%20")
        return listOf("https://commons.wikimedia.org/wiki/Special:FilePath/" + encoded + "?width=1920")
    }

    private fun mixkit(id: Int): List<String> {
        val base = "https://assets.mixkit.co/videos/" + id + "/" + id
        return listOf(base + "-1080.mp4", base + "-720.mp4", base + "-360.mp4")
    }

    val IMAGES: List<CatalogItem> = listOf(
        CatalogItem("Nebulosa de Carina", commons("Carina Nebula.jpg"), false),
        CatalogItem("Nebulosas Coração e Alma", commons("Heart and Soul nebulae.jpg"), false),
        CatalogItem("Nebulosa do Caranguejo", commons("Crab Nebula.jpg"), false),
        CatalogItem("Grande Nuvem de Magalhães", commons("Large Magellanic Cloud.jpg"), false),
        CatalogItem("Nebulosa da Lagoa", commons("Lagoon Nebula.jpg"), false),
        CatalogItem("Galáxias das Antenas", commons("Antennae galaxies xl.jpg"), false),
        CatalogItem("Via Láctea em infravermelho", commons("Milky Way IR Spitzer.jpg"), false),
        CatalogItem("Terra à noite nos EUA", commons("City Lights of the United States 2012.jpg"), false),
        CatalogItem("Saturno com auroras", commons("Saturn with auroras.jpg"), false),
        CatalogItem("Lagos glaciares do Butão", commons("Glacial lakes, Bhutan.jpg"), false),
        CatalogItem("Ilha do Fogo em Cabo Verde", commons("Fogo, Cape Verde Islands.jpg"), false),
        CatalogItem("Campo Ultra Profundo do Hubble", commons("Hubble ultra deep field.jpg"), false)
    )

    val VIDEOS: List<CatalogItem> = listOf(
        CatalogItem("Túnel azul em loop", mixkit(19583), true),
        CatalogItem("Túnel futurista em loop", mixkit(30340), true),
        CatalogItem("Mundo abstrato no espaço em loop", mixkit(30595), true),
        CatalogItem("Túnel alienígena com luz em loop", mixkit(19594), true),
        CatalogItem("Floresta de inverno com neve em loop", mixkit(26958), true),
        CatalogItem("Árvores de outono em loop", mixkit(19647), true)
    )
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

    private fun clean(base: String): String {
        return base
            .map { if (it.isLetterOrDigit() || it == '-') it else '_' }
            .joinToString("")
            .trim('_')
            .take(40)
            .ifEmpty { "fundo" }
    }

    // Nome tal como aparece na lista depois de guardado (serve para saber se já foi descarregado).
    fun displayFor(base: String): String = clean(base).replace('_', ' ')

    fun newFile(context: Context, base: String, ext: String): File {
        return File(dir(context), System.currentTimeMillis().toString() + "_" + clean(base) + "." + ext)
    }

    // Os vídeos de fundo são esticados ao ecrã, por isso só aceitamos vídeos horizontais.
    fun isLandscapeVideo(file: File): Boolean {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                ?: return true
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                ?: return true
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val shownW = if (rot == 90 || rot == 270) h else w
            val shownH = if (rot == 90 || rot == 270) w else h
            return shownW >= shownH
        } catch (e: Exception) {
            return true
        } finally {
            try {
                r.release()
            } catch (e: Exception) {
                // nada a libertar
            }
        }
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
            c.setRequestProperty("User-Agent", "InicioTV/1.0 (launcher Android TV)")
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

    // Tenta cada endereço pela ordem até um funcionar.
    fun downloadFirst(context: Context, urls: List<String>, baseName: String?): File {
        var last: Exception? = null
        for (u in urls) {
            try {
                return download(context, u, baseName)
            } catch (e: PortraitVideoException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("sem endereços")
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
            if (ext in VIDEO_EXT && !isLandscapeVideo(dest)) {
                dest.delete()
                throw PortraitVideoException()
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
