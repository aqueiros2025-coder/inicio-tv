package com.example.tvlauncher

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

data class WeatherDay(val label: String, val max: Int, val min: Int, val desc: String)

data class WeatherData(
    val place: String,
    val nowTemp: Int,
    val nowDesc: String,
    val unit: String,
    val days: List<WeatherDay>
)

// Dados do Open-Meteo (https://open-meteo.com), gratuito e sem chave de API.
object Weather {

    private val WEEKDAYS = arrayOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")

    fun fetch(city: String, fahrenheit: Boolean): WeatherData {
        val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=" +
            URLEncoder.encode(city, "UTF-8") + "&count=1&language=pt&format=json"
        val results = JSONObject(httpGet(geoUrl)).optJSONArray("results")
            ?: throw IllegalStateException("cidade não encontrada")
        val first = results.getJSONObject(0)
        val lat = first.getDouble("latitude")
        val lon = first.getDouble("longitude")
        val place = first.optString("name", city)

        val unitParam = if (fahrenheit) "fahrenheit" else "celsius"
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
            "&forecast_days=4&timezone=auto&temperature_unit=$unitParam"
        val root = JSONObject(httpGet(url))

        val current = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        val dates = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val maxs = daily.getJSONArray("temperature_2m_max")
        val mins = daily.getJSONArray("temperature_2m_min")

        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val days = ArrayList<WeatherDay>()
        for (i in 0 until dates.length()) {
            val label = if (i == 0) {
                "Hoje"
            } else {
                val cal = Calendar.getInstance()
                cal.time = parser.parse(dates.getString(i))!!
                WEEKDAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]
            }
            days.add(
                WeatherDay(
                    label,
                    maxs.getDouble(i).roundToInt(),
                    mins.getDouble(i).roundToInt(),
                    describe(codes.getInt(i))
                )
            )
        }

        return WeatherData(
            place,
            current.getDouble("temperature_2m").roundToInt(),
            describe(current.getInt("weather_code")),
            if (fahrenheit) "°F" else "°C",
            days
        )
    }

    private fun httpGet(address: String): String {
        val conn = URL(address).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun describe(code: Int): String = when (code) {
        0 -> "Céu limpo"
        1 -> "Pouco nublado"
        2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45, 48 -> "Nevoeiro"
        51, 53, 55 -> "Chuvisco"
        56, 57 -> "Chuvisco gelado"
        61 -> "Chuva fraca"
        63 -> "Chuva"
        65 -> "Chuva forte"
        66, 67 -> "Chuva gelada"
        71, 73, 75 -> "Neve"
        77 -> "Grãos de neve"
        80, 81, 82 -> "Aguaceiros"
        85, 86 -> "Aguaceiros de neve"
        95 -> "Trovoada"
        96, 99 -> "Trovoada com granizo"
        else -> "Tempo variável"
    }
}
