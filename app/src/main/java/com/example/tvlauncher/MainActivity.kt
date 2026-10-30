package com.example.tvlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : Activity() {

    companion object {
        // Apps em destaque na primeira utilização (nome, ou parte do nome, pela ordem de apresentação).
        // Depois de escolheres apps em Opções, a tua escolha passa a ter prioridade.
        private val FEATURED_DEFAULT = listOf("nuvio", "arvio", "wuplay", "playtorrio", "stremio")

        // Apps escondidas por defeito da linha "Todas as apps".
        private val HIDDEN_DEFAULT = setOf(
            "com.google.android.videos",
            "com.google.android.music",
            "com.google.android.youtube.tvkids"
        )

        private const val KEY_FEATURED = "featured"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_CITY = "city"
        private const val KEY_FAHRENHEIT = "fahrenheit"
        private const val KEY_SHOW_WEATHER = "show_weather"
        private const val DEFAULT_CITY = "Lisboa"
        private const val WEATHER_OK_MS = 30 * 60 * 1000L
        private const val WEATHER_RETRY_MS = 5 * 60 * 1000L
    }

    private lateinit var heroTitle: TextView
    private lateinit var weatherNow: TextView
    private lateinit var forecastRow: View
    private lateinit var forecastViews: List<TextView>
    private lateinit var rvFeatured: RecyclerView
    private lateinit var rvAll: RecyclerView
    private lateinit var featuredAdapter: AppAdapter
    private lateinit var allAdapter: AppAdapter

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("inicio_tv", Context.MODE_PRIVATE)
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var allEntries: List<AppEntry> = emptyList()
    private var loadId = 0
    private var weatherId = 0
    private var focusedOnce = false
    private val weatherTick = Runnable { refreshWeather() }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        heroTitle = findViewById(R.id.heroTitle)
        weatherNow = findViewById(R.id.weatherNow)
        forecastRow = findViewById(R.id.forecastRow)
        forecastViews = listOf<TextView>(
            findViewById(R.id.fc0),
            findViewById(R.id.fc1),
            findViewById(R.id.fc2),
            findViewById(R.id.fc3)
        )
        rvFeatured = findViewById(R.id.rvFeatured)
        rvAll = findViewById(R.id.rvAll)
        findViewById<View>(R.id.btnOptions).setOnClickListener { showMenu() }

        featuredAdapter = AppAdapter(
            R.layout.item_card, true,
            { e -> onFocused(e) }, { e -> launch(e) }, { e -> showInfo(e) }
        )
        allAdapter = AppAdapter(
            R.layout.item_app, false,
            { e -> onFocused(e) }, { e -> launch(e) }, { e -> showInfo(e) }
        )

        rvFeatured.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvAll.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvFeatured.adapter = featuredAdapter
        rvAll.adapter = allAdapter

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        loadApps()
        refreshWeather()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(packageReceiver)
        } catch (ex: Exception) {
            // já estava desregistado
        }
    }

    // Carregar no botão Home quando já estamos no launcher volta ao início.
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        rvFeatured.scrollToPosition(0)
        rvAll.scrollToPosition(0)
        focusFirst()
    }

    // A tecla Menu do comando abre as opções.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showMenu()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // O launcher não deve fechar com o botão Voltar.
    override fun onBackPressed() {
    }

    // ---------------------------------------------------------------- Apps

    private fun loadApps() {
        val id = ++loadId
        Thread {
            val pm = packageManager
            val seen = HashSet<String>()
            val entries = ArrayList<AppEntry>()
            val categories = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)

            for (cat in categories) {
                val query = Intent(Intent.ACTION_MAIN).addCategory(cat)
                for (ri in pm.queryIntentActivities(query, 0)) {
                    val ai = ri.activityInfo ?: continue
                    if (ai.packageName == packageName) continue
                    if (!seen.add(ai.packageName)) continue
                    val banner = try {
                        ai.loadBanner(pm)
                    } catch (ex: Exception) {
                        null
                    }
                    entries.add(
                        AppEntry(
                            ri.loadLabel(pm).toString(),
                            ai.packageName,
                            ComponentName(ai.packageName, ai.name),
                            ri.loadIcon(pm),
                            banner
                        )
                    )
                }
            }
            entries.sortBy { it.label.lowercase() }

            mainHandler.post {
                if (id != loadId || isDestroyed) return@post
                allEntries = entries
                applyEntries()
                if (!focusedOnce) focusFirst()
            }
        }.start()
    }

    private fun currentFeaturedPackages(): List<String> {
        val saved = prefs.getString(KEY_FEATURED, null)
        if (saved != null) return saved.split("\n").filter { it.isNotBlank() }
        val result = ArrayList<String>()
        for (key in FEATURED_DEFAULT) {
            val hit = allEntries.firstOrNull {
                it.label.lowercase().contains(key) || it.packageName.lowercase().contains(key)
            }
            if (hit != null && hit.packageName !in result) result.add(hit.packageName)
        }
        return result
    }

    private fun hiddenPackages(): Set<String> {
        return prefs.getStringSet(KEY_HIDDEN, null) ?: HIDDEN_DEFAULT
    }

    private fun applyEntries() {
        val byPackage = allEntries.associateBy { it.packageName }
        val featured = currentFeaturedPackages().mapNotNull { byPackage[it] }
        val hidden = hiddenPackages()
        val rest = allEntries.filter { it !in featured && it.packageName !in hidden }
        featuredAdapter.submit(featured)
        allAdapter.submit(rest)
    }

    private fun focusFirst() {
        val target = if (featuredAdapter.itemCount > 0) rvFeatured else rvAll
        target.postDelayed({
            target.requestFocus()
            focusedOnce = true
        }, 200)
    }

    private fun onFocused(e: AppEntry) {
        heroTitle.text = e.label
    }

    private fun launch(e: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.component = e.component
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(intent)
        } catch (ex: Exception) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showInfo(e: AppEntry) {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.packageName))
            )
        } catch (ex: Exception) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------- Tempo

    private fun refreshWeather() {
        mainHandler.removeCallbacks(weatherTick)
        val id = ++weatherId

        if (!prefs.getBoolean(KEY_SHOW_WEATHER, true)) {
            weatherNow.text = ""
            forecastRow.visibility = View.GONE
            return
        }

        weatherNow.text = getString(R.string.weather_loading)
        val city = prefs.getString(KEY_CITY, DEFAULT_CITY) ?: DEFAULT_CITY
        val fahrenheit = prefs.getBoolean(KEY_FAHRENHEIT, false)

        Thread {
            val result = try {
                Weather.fetch(city, fahrenheit)
            } catch (ex: Exception) {
                null
            }
            mainHandler.post {
                if (id != weatherId || isDestroyed) return@post
                showWeather(result)
                mainHandler.postDelayed(weatherTick, if (result != null) WEATHER_OK_MS else WEATHER_RETRY_MS)
            }
        }.start()
    }

    private fun showWeather(data: WeatherData?) {
        if (data == null) {
            weatherNow.text = getString(R.string.weather_unavailable)
            forecastRow.visibility = View.GONE
            return
        }
        weatherNow.text = data.place + " · " + data.nowTemp + data.unit + " · " + data.nowDesc
        forecastRow.visibility = View.VISIBLE
        for (i in forecastViews.indices) {
            val tv = forecastViews[i]
            val day = data.days.getOrNull(i)
            if (day == null) {
                tv.visibility = View.GONE
            } else {
                tv.visibility = View.VISIBLE
                tv.text = day.label + "\n" + day.max + "° / " + day.min + "°\n" + day.desc
            }
        }
    }

    // ------------------------------------------------------------ Opções

    private fun showMenu() {
        val city = prefs.getString(KEY_CITY, DEFAULT_CITY) ?: DEFAULT_CITY
        val fahrenheit = prefs.getBoolean(KEY_FAHRENHEIT, false)
        val showWeather = prefs.getBoolean(KEY_SHOW_WEATHER, true)
        val items = arrayOf(
            "Cidade do tempo: " + city,
            "Unidade de temperatura: " + (if (fahrenheit) "°F" else "°C"),
            "Previsão do tempo: " + (if (showWeather) "ligada" else "desligada"),
            "Escolher apps em destaque",
            "Esconder ou mostrar apps",
            "Repor predefinições"
        )
        AlertDialog.Builder(this)
            .setTitle("Opções")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> askCity()
                    1 -> {
                        prefs.edit().putBoolean(KEY_FAHRENHEIT, !fahrenheit).apply()
                        refreshWeather()
                        showMenu()
                    }
                    2 -> {
                        prefs.edit().putBoolean(KEY_SHOW_WEATHER, !showWeather).apply()
                        refreshWeather()
                        showMenu()
                    }
                    3 -> pickFeatured()
                    4 -> pickHidden()
                    5 -> confirmReset()
                    else -> {}
                }
            }
            .show()
    }

    private fun askCity() {
        val input = EditText(this)
        input.setSingleLine(true)
        input.setText(prefs.getString(KEY_CITY, DEFAULT_CITY))
        AlertDialog.Builder(this)
            .setTitle("Cidade para o tempo")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val city = input.text.toString().trim()
                if (city.isNotEmpty()) {
                    prefs.edit().putString(KEY_CITY, city).apply()
                    refreshWeather()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun pickFeatured() {
        val apps = allEntries
        val chosen = ArrayList<String>(currentFeaturedPackages())
        val labels = Array(apps.size) { apps[it].label }
        val checked = BooleanArray(apps.size) { apps[it].packageName in chosen }
        AlertDialog.Builder(this)
            .setTitle("Apps em destaque (ficam pela ordem em que as escolhes)")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val pkg = apps[which].packageName
                if (isChecked) {
                    if (pkg !in chosen) chosen.add(pkg)
                } else {
                    chosen.remove(pkg)
                }
            }
            .setPositiveButton("Guardar") { _, _ ->
                prefs.edit().putString(KEY_FEATURED, chosen.joinToString("\n")).apply()
                applyEntries()
                focusFirst()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun pickHidden() {
        val apps = allEntries
        val hidden = HashSet<String>(hiddenPackages())
        val labels = Array(apps.size) { apps[it].label }
        val checked = BooleanArray(apps.size) { apps[it].packageName in hidden }
        AlertDialog.Builder(this)
            .setTitle("Apps escondidas da linha \"Todas as apps\"")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val pkg = apps[which].packageName
                if (isChecked) {
                    hidden.add(pkg)
                } else {
                    hidden.remove(pkg)
                }
            }
            .setPositiveButton("Guardar") { _, _ ->
                prefs.edit().putStringSet(KEY_HIDDEN, HashSet<String>(hidden)).apply()
                applyEntries()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Repor predefinições")
            .setMessage("Voltar à cidade, unidade, apps em destaque e apps escondidas iniciais?")
            .setPositiveButton("Repor") { _, _ ->
                prefs.edit().clear().apply()
                applyEntries()
                refreshWeather()
                focusFirst()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
