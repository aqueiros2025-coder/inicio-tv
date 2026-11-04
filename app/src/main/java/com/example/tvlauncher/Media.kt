package com.example.tvlauncher

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Collections
import java.util.Random

data class MediaItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String
)

// Sugestões de filmes e séries vindas do catálogo público do Cinemeta (o mesmo que o Stremio usa),
// sem chave de API. A lista é baralhada de forma estável durante o dia, para haver variedade.
object MediaSource {

    private const val BASE = "https://v3-cinemeta.strem.io/catalog/"

    // type: "movie" ou "series"
    fun fetch(type: String): List<MediaItem> {
        val conn = URL(BASE + type + "/top.json").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "InicioTV/1.0")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val metas = JSONObject(text).getJSONArray("metas")
            val all = ArrayList<MediaItem>()
            for (i in 0 until metas.length()) {
                val m = metas.getJSONObject(i)
                val id = m.optString("id")
                val name = m.optString("name")
                val poster = m.optString("poster")
                if (id.startsWith("tt") && name.isNotEmpty() && poster.startsWith("http")) {
                    all.add(MediaItem(id, type, name, poster))
                }
            }
            val cal = Calendar.getInstance()
            val seed = cal.get(Calendar.YEAR) * 1000L + cal.get(Calendar.DAY_OF_YEAR) + type.hashCode()
            Collections.shuffle(all, Random(seed))
            return all.take(20)
        } finally {
            conn.disconnect()
        }
    }

    fun toJson(items: List<MediaItem>): String {
        val arr = JSONArray()
        for (it in items) {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("type", it.type)
            o.put("name", it.name)
            o.put("poster", it.poster)
            arr.put(o)
        }
        return arr.toString()
    }

    fun fromJson(text: String?): List<MediaItem> {
        if (text.isNullOrEmpty()) return emptyList()
        return try {
            val arr = JSONArray(text)
            val list = ArrayList<MediaItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    MediaItem(
                        o.getString("id"),
                        o.getString("type"),
                        o.getString("name"),
                        o.getString("poster")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
