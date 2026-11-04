package com.example.tvlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.UUID

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
        private const val KEY_RECENT = "recent"
        private const val KEY_SHOW_RECENT = "show_recent"
        private const val KEY_WALLPAPER = "wallpaper"
        private const val KEY_UI_VERSION = "ui_version"
        private const val KEY_SOUNDS = "sounds"
        private const val KEY_SUGGEST = "suggest"
        private const val KEY_STREAM_APP = "stream_app"
        private const val KEY_HISTORY = "media_history"
        private const val KEY_SUG_MOVIES = "sug_movies"
        private const val KEY_SUG_SERIES = "sug_series"
        private const val KEY_SUG_TIME = "sug_time"
        private const val KEY_CITY = "city"
        private const val KEY_FAHRENHEIT = "fahrenheit"
        private const val KEY_SHOW_WEATHER = "show_weather"
        private const val DEFAULT_CITY = "Lisboa"
        private const val MAX_RECENT_STORED = 10
        private const val MAX_RECENT_SHOWN = 8
        private const val MAX_HISTORY = 12
        private const val WEATHER_OK_MS = 30 * 60 * 1000L
        private const val WEATHER_RETRY_MS = 5 * 60 * 1000L
        private const val SUGGEST_REFRESH_MS = 6 * 60 * 60 * 1000L
        private const val PICSUM_URL = "https://picsum.photos/1920/1080"
    }

    private lateinit var weatherNow: TextView
    private lateinit var forecastRow: View
    private lateinit var forecastViews: List<TextView>
    private lateinit var continueSection: View
    private lateinit var recentSection: View
    private lateinit var moviesSection: View
    private lateinit var seriesSection: View
    private lateinit var rvFeatured: RecyclerView
    private lateinit var rvContinue: RecyclerView
    private lateinit var rvRecent: RecyclerView
    private lateinit var rvMovies: RecyclerView
    private lateinit var rvSeries: RecyclerView
    private lateinit var rvAll: RecyclerView
    private lateinit var featuredAdapter: AppAdapter
    private lateinit var recentAdapter: AppAdapter
    private lateinit var allAdapter: AppAdapter
    private lateinit var continueAdapter: PosterAdapter
    private lateinit var moviesAdapter: PosterAdapter
    private lateinit var seriesAdapter: PosterAdapter
    private lateinit var bgGradient: GradientView
    private lateinit var bgImage: ImageView
    private lateinit var bgVideo: FillVideoView

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("inicio_tv", Context.MODE_PRIVATE)
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var allEntries: List<AppEntry> = emptyList()
    private var loadId = 0
    private var weatherId = 0
    private var wallpaperToken = 0
    private var focusedOnce = false
    private var resumed = false
    private var videoFile: File? = null
    private var qrServer: WallpaperServer? = null
    private val weatherTick = Runnable { refreshWeather() }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Sons do comando: geram-se uma vez e tocam em todas as teclas desta janela.
        Sounds.init(applicationContext)
        Sounds.enabled = prefs.getBoolean(KEY_SOUNDS, true)
        window.callback = SoundCallback(window.callback)

        weatherNow = findViewById(R.id.weatherNow)
        forecastRow = findViewById(R.id.forecastRow)
        forecastViews = listOf<TextView>(
            findViewById(R.id.fc0),
            findViewById(R.id.fc1),
            findViewById(R.id.fc2),
            findViewById(R.id.fc3)
        )
        continueSection = findViewById(R.id.continueSection)
        recentSection = findViewById(R.id.recentSection)
        moviesSection = findViewById(R.id.moviesSection)
        seriesSection = findViewById(R.id.seriesSection)
        rvFeatured = findViewById(R.id.rvFeatured)
        rvContinue = findViewById(R.id.rvContinue)
        rvRecent = findViewById(R.id.rvRecent)
        rvMovies = findViewById(R.id.rvMovies)
        rvSeries = findViewById(R.id.rvSeries)
        rvAll = findViewById(R.id.rvAll)
        bgGradient = findViewById(R.id.bgGradient)
        bgImage = findViewById(R.id.bgImage)
        bgVideo = findViewById(R.id.bgVideo)
        findViewById<View>(R.id.btnOptions).setOnClickListener { showMenu() }

        featuredAdapter = makeAdapter(R.layout.item_card, true)
        recentAdapter = makeAdapter(R.layout.item_card, true)
        allAdapter = makeAdapter(R.layout.item_app, false)
        continueAdapter = PosterAdapter({ m -> openMedia(m) }, { m -> removeFromHistory(m) })
        moviesAdapter = PosterAdapter({ m -> openMedia(m) }, { _ -> })
        seriesAdapter = PosterAdapter({ m -> openMedia(m) }, { _ -> })

        for (rv in listOf(rvFeatured, rvContinue, rvRecent, rvMovies, rvSeries, rvAll)) {
            rv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        }
        rvFeatured.adapter = featuredAdapter
        rvContinue.adapter = continueAdapter
        rvRecent.adapter = recentAdapter
        rvMovies.adapter = moviesAdapter
        rvSeries.adapter = seriesAdapter
        rvAll.adapter = allAdapter

        // Vídeo de fundo: sem som, em ciclo, e sem pedir o foco de áudio a outras apps.
        bgVideo.setOnPreparedListener { mp ->
            mp.isLooping = true
            mp.setVolume(0f, 0f)
        }
        bgVideo.setOnErrorListener { _, _, _ ->
            Toast.makeText(this, "Não foi possível reproduzir este vídeo", Toast.LENGTH_LONG).show()
            setWallpaper(Wallpapers.DEFAULT_ID)
            true
        }
        if (Build.VERSION.SDK_INT >= 26) {
            bgVideo.setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE)
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Na primeira vez com o novo estilo, quem ainda tinha o fundo antigo passa para as ondas azuis.
        if (prefs.getInt(KEY_UI_VERSION, 0) < 2) {
            val current = prefs.getString(KEY_WALLPAPER, null)
            val editor = prefs.edit().putInt(KEY_UI_VERSION, 2)
            if (current == null || current == "builtin:noite") {
                editor.putString(KEY_WALLPAPER, Wallpapers.DEFAULT_ID)
            }
            editor.apply()
        }

        applyWallpaper()
        loadApps()
        refreshWeather()
        loadSuggestionsFromCache()
        refreshSuggestions(false)
    }

    private fun makeAdapter(layout: Int, large: Boolean): AppAdapter {
        return AppAdapter(layout, large, { e -> launch(e) }, { e -> showInfo(e) })
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        bgGradient.setPaused(false)
        startVideo()
        if (allEntries.isNotEmpty()) applyEntries()
        applyMediaSections()
    }

    // Quando outra app está à frente, o fundo pára para poupar memória e processador.
    override fun onPause() {
        super.onPause()
        resumed = false
        bgGradient.setPaused(true)
        stopVideo()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        qrServer?.stop()
        qrServer = null
        try {
            unregisterReceiver(packageReceiver)
        } catch (ex: Exception) {
            // já estava desregistado
        }
    }

    // Carregar no botão Home quando já estamos no launcher volta ao início.
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        for (rv in listOf(rvFeatured, rvContinue, rvRecent, rvMovies, rvSeries, rvAll)) {
            rv.scrollToPosition(0)
        }
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

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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

    private fun recentPackages(): List<String> {
        return (prefs.getString(KEY_RECENT, "") ?: "").split("\n").filter { it.isNotBlank() }
    }

    private fun recordRecent(pkg: String) {
        val list = ArrayList<String>(recentPackages())
        list.remove(pkg)
        list.add(0, pkg)
        prefs.edit().putString(KEY_RECENT, list.take(MAX_RECENT_STORED).joinToString("\n")).apply()
    }

    private fun applyEntries() {
        val byPackage = allEntries.associateBy { it.packageName }
        val featured = currentFeaturedPackages().mapNotNull { byPackage[it] }
        val hidden = hiddenPackages()
        val rest = allEntries.filter { it !in featured && it.packageName !in hidden }

        val showRecent = prefs.getBoolean(KEY_SHOW_RECENT, true)
        val recents = if (showRecent) {
            recentPackages()
                .mapNotNull { byPackage[it] }
                .filter { it.packageName !in hidden }
                .take(MAX_RECENT_SHOWN)
        } else {
            emptyList()
        }

        featuredAdapter.submit(featured)
        recentAdapter.submit(recents)
        allAdapter.submit(rest)
        recentSection.visibility = if (recents.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun focusFirst() {
        val target = if (featuredAdapter.itemCount > 0) rvFeatured else rvAll
        target.postDelayed({
            target.requestFocus()
            focusedOnce = true
        }, 200)
    }

    private fun launch(e: AppEntry) {
        recordRecent(e.packageName)
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

    // ------------------------------------------- Filmes, séries e continuar a ver

    private fun history(): List<MediaItem> = MediaSource.fromJson(prefs.getString(KEY_HISTORY, null))

    private fun recordMedia(item: MediaItem) {
        val list = ArrayList<MediaItem>(history())
        list.removeAll { it.id == item.id }
        list.add(0, item)
        prefs.edit().putString(KEY_HISTORY, MediaSource.toJson(list.take(MAX_HISTORY))).apply()
    }

    private fun removeFromHistory(item: MediaItem) {
        val list = history().filter { it.id != item.id }
        prefs.edit().putString(KEY_HISTORY, MediaSource.toJson(list)).apply()
        applyMediaSections()
        Toast.makeText(this, "Removido de Continuar a ver", Toast.LENGTH_SHORT).show()
    }

    private fun applyMediaSections() {
        val suggest = prefs.getBoolean(KEY_SUGGEST, true)
        continueAdapter.submit(history())
        continueSection.visibility = if (continueAdapter.itemCount > 0) View.VISIBLE else View.GONE
        moviesSection.visibility =
            if (suggest && moviesAdapter.itemCount > 0) View.VISIBLE else View.GONE
        seriesSection.visibility =
            if (suggest && seriesAdapter.itemCount > 0) View.VISIBLE else View.GONE
    }

    private fun loadSuggestionsFromCache() {
        moviesAdapter.submit(MediaSource.fromJson(prefs.getString(KEY_SUG_MOVIES, null)))
        seriesAdapter.submit(MediaSource.fromJson(prefs.getString(KEY_SUG_SERIES, null)))
        applyMediaSections()
    }

    private fun refreshSuggestions(force: Boolean) {
        if (!prefs.getBoolean(KEY_SUGGEST, true)) return
        val last = prefs.getLong(KEY_SUG_TIME, 0L)
        val hasCache = moviesAdapter.itemCount > 0 && seriesAdapter.itemCount > 0
        if (!force && hasCache && System.currentTimeMillis() - last < SUGGEST_REFRESH_MS) return
        Thread {
            try {
                val movies = MediaSource.fetch("movie")
                val series = MediaSource.fetch("series")
                if (movies.isNotEmpty() && series.isNotEmpty()) {
                    prefs.edit()
                        .putString(KEY_SUG_MOVIES, MediaSource.toJson(movies))
                        .putString(KEY_SUG_SERIES, MediaSource.toJson(series))
                        .putLong(KEY_SUG_TIME, System.currentTimeMillis())
                        .apply()
                    mainHandler.post {
                        if (isDestroyed) return@post
                        moviesAdapter.submit(movies)
                        seriesAdapter.submit(series)
                        applyMediaSections()
                    }
                }
            } catch (ex: Exception) {
                // sem rede: ficam as sugestões guardadas da última vez
            }
        }.start()
    }

    private fun streamAppPackage(): String? {
        val chosen = prefs.getString(KEY_STREAM_APP, null)
        if (chosen != null) return chosen
        return allEntries.firstOrNull {
            it.packageName.lowercase().contains("stremio") || it.label.lowercase().contains("stremio")
        }?.packageName
    }

    private fun openMedia(item: MediaItem) {
        recordMedia(item)
        applyMediaSections()

        val pkg = streamAppPackage()
        if (pkg == null) {
            toast("Escolhe em Opções a app para abrir as sugestões")
            return
        }
        if (pkg.lowercase().contains("stremio")) {
            // Abre diretamente a página do filme ou da série no Stremio.
            val link = if (item.type == "series") {
                "stremio:///detail/series/" + item.id + "/" + item.id + ":1:1"
            } else {
                "stremio:///detail/movie/" + item.id + "/" + item.id
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            intent.setPackage(pkg)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(intent)
                return
            } catch (ex: Exception) {
                // a app não aceitou o link: abre-se só a app
            }
        }
        val app = allEntries.firstOrNull { it.packageName == pkg }
        if (app == null) {
            toast("A app escolhida não está instalada")
            return
        }
        toast("Esta app não abre títulos diretamente: procura \"" + item.name + "\"")
        launch(app)
    }

    private fun pickStreamApp() {
        val apps = allEntries
        val labels = Array(apps.size) { apps[it].label }
        AlertDialog.Builder(this)
            .setTitle("Abrir sugestões com qual app?")
            .setItems(labels) { _, which ->
                prefs.edit().putString(KEY_STREAM_APP, apps[which].packageName).apply()
                showMenu()
            }
            .setNeutralButton("Automático (Stremio)") { _, _ ->
                prefs.edit().remove(KEY_STREAM_APP).apply()
                showMenu()
            }
            .setNegativeButton("Voltar") { _, _ -> showMenu() }
            .showWithSounds()
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
                tv.text = day.label + "  " + day.max + "°/" + day.min + "°\n" + day.desc
            }
        }
    }

    // ------------------------------------------------------------ Fundos

    private fun setWallpaper(id: String) {
        prefs.edit().putString(KEY_WALLPAPER, id).apply()
        applyWallpaper()
    }

    private fun applyWallpaper() {
        val token = ++wallpaperToken
        val id = prefs.getString(KEY_WALLPAPER, Wallpapers.DEFAULT_ID) ?: Wallpapers.DEFAULT_ID
        stopVideo()
        videoFile = null

        val preset = Wallpapers.preset(id)
        if (preset != null) {
            showGradient(preset)
            return
        }

        val wf = WallpaperFiles.find(this, id)
        if (wf == null) {
            prefs.edit().remove(KEY_WALLPAPER).apply()
            showGradient(Wallpapers.BUILTINS[0])
            return
        }

        if (wf.isVideo) {
            bgImage.setImageDrawable(null)
            bgImage.visibility = View.GONE
            bgGradient.visibility = View.GONE
            videoFile = wf.file
            bgVideo.visibility = View.VISIBLE
            startVideo()
        } else {
            val dm = resources.displayMetrics
            Thread {
                val bmp = WallpaperFiles.decodeSampled(wf.file, dm.widthPixels, dm.heightPixels)
                mainHandler.post {
                    if (token != wallpaperToken || isDestroyed) return@post
                    if (bmp == null) {
                        toast("Não foi possível abrir esta imagem")
                        setWallpaper(Wallpapers.DEFAULT_ID)
                        return@post
                    }
                    bgVideo.visibility = View.GONE
                    bgImage.setImageBitmap(bmp)
                    bgImage.visibility = View.VISIBLE
                    bgGradient.visibility = View.GONE
                }
            }.start()
        }
    }

    private fun showGradient(p: GradientPreset) {
        bgVideo.visibility = View.GONE
        bgImage.setImageDrawable(null)
        bgImage.visibility = View.GONE
        bgGradient.visibility = View.VISIBLE
        bgGradient.setPreset(p)
    }

    private fun startVideo() {
        val f = videoFile ?: return
        if (!resumed) return
        bgVideo.visibility = View.VISIBLE
        bgVideo.setVideoURI(Uri.fromFile(f))
        bgVideo.start()
    }

    private fun stopVideo() {
        try {
            bgVideo.stopPlayback()
        } catch (ex: Exception) {
            // nada a parar
        }
    }

    private fun showWallpaperMenu() {
        val items = arrayOf(
            "Escolher fundo",
            "Descarregar da Internet",
            "Enviar do telemóvel (QR Code)",
            "Apagar um fundo descarregado"
        )
        AlertDialog.Builder(this)
            .setTitle("Fundos de ecrã")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> chooseWallpaper()
                    1 -> showDownloadMenu()
                    2 -> showQr()
                    3 -> pickWallpaperToDelete()
                    else -> {}
                }
            }
            .setNegativeButton("Voltar") { _, _ -> showMenu() }
            .showWithSounds()
    }

    private fun chooseWallpaper() {
        val current = prefs.getString(KEY_WALLPAPER, Wallpapers.DEFAULT_ID) ?: Wallpapers.DEFAULT_ID
        val ids = ArrayList<String>()
        val names = ArrayList<String>()
        for (p in Wallpapers.BUILTINS) {
            ids.add(p.id)
            names.add(p.name + if (p.animated) " (animado)" else " (fixo)")
        }
        for (w in WallpaperFiles.list(this)) {
            ids.add(w.id)
            names.add(w.displayName + if (w.isVideo) " (vídeo animado)" else " (imagem fixa)")
        }
        val labels = Array(names.size) { (if (ids[it] == current) "✓ " else "") + names[it] }
        AlertDialog.Builder(this)
            .setTitle("Escolher fundo")
            .setItems(labels) { _, which -> setWallpaper(ids[which]) }
            .setNegativeButton("Voltar") { _, _ -> showWallpaperMenu() }
            .showWithSounds()
    }

    private fun showDownloadMenu() {
        val items = arrayOf(
            "Catálogo: imagens fixas selecionadas",
            "Catálogo: vídeos animados selecionados",
            "Foto aleatória (Picsum, 1920×1080)",
            "Escrever um endereço (URL)"
        )
        AlertDialog.Builder(this)
            .setTitle("Descarregar da Internet")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showCatalog(Catalog.IMAGES, "Imagens fixas (Wikimedia Commons)")
                    1 -> showCatalog(Catalog.VIDEOS, "Vídeos animados (Mixkit)")
                    2 -> downloadWallpaper(listOf(PICSUM_URL), "Foto aleatória")
                    3 -> askUrl()
                    else -> {}
                }
            }
            .setNegativeButton("Voltar") { _, _ -> showWallpaperMenu() }
            .showWithSounds()
    }

    private fun showCatalog(items: List<CatalogItem>, title: String) {
        val done = WallpaperFiles.list(this).map { it.displayName }.toSet()
        val labels = Array(items.size) {
            (if (WallpaperFiles.displayFor(items[it].name) in done) "✓ " else "") + items[it].name
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(labels) { _, which -> downloadCatalogItem(items[which]) }
            .setNegativeButton("Voltar") { _, _ -> showDownloadMenu() }
            .showWithSounds()
    }

    private fun downloadCatalogItem(item: CatalogItem) {
        val shown = WallpaperFiles.displayFor(item.name)
        val existing = WallpaperFiles.list(this).firstOrNull {
            it.displayName == shown && it.isVideo == item.video
        }
        if (existing != null) {
            setWallpaper(existing.id)
            Toast.makeText(this, "Já descarregado: a aplicar", Toast.LENGTH_SHORT).show()
            return
        }
        downloadWallpaper(item.urls, item.name)
    }

    private fun askUrl() {
        val input = EditText(this)
        input.setSingleLine(true)
        input.hint = "https://..."
        AlertDialog.Builder(this)
            .setTitle("Endereço da imagem ou do vídeo")
            .setMessage("JPG, PNG, WEBP, MP4 ou WEBM. Dica: pelo telemóvel (QR Code) é mais fácil colar o endereço.")
            .setView(input)
            .setPositiveButton("Descarregar") { _, _ ->
                val address = input.text.toString().trim()
                if (address.startsWith("http://") || address.startsWith("https://")) {
                    downloadWallpaper(listOf(address), null)
                } else {
                    toast("O endereço tem de começar por http:// ou https://")
                }
            }
            .setNegativeButton("Cancelar", null)
            .showWithSounds()
    }

    private fun downloadWallpaper(urls: List<String>, baseName: String?) {
        Toast.makeText(this, "A descarregar o fundo…", Toast.LENGTH_LONG).show()
        Thread {
            try {
                val file = WallpaperFiles.downloadFirst(this, urls, baseName)
                mainHandler.post {
                    if (isDestroyed) return@post
                    Toast.makeText(this, "Fundo descarregado", Toast.LENGTH_SHORT).show()
                    setWallpaper(WallpaperFiles.toEntry(file).id)
                }
            } catch (ex: Exception) {
                mainHandler.post {
                    if (isDestroyed) return@post
                    toast("Falhou: " + (ex.message ?: "erro desconhecido"))
                }
            }
        }.start()
    }

    private fun showQr() {
        val ip = WallpaperServer.localIp()
        if (ip == null) {
            toast("A box não está ligada a uma rede local")
            return
        }
        val token = UUID.randomUUID().toString().replace("-", "").take(10)
        val server = WallpaperServer(this, token, object : WallpaperServer.Listener {
            override fun onFileReceived(file: File) {
                mainHandler.post {
                    if (isDestroyed) return@post
                    toast("Fundo recebido do telemóvel")
                    setWallpaper(WallpaperFiles.toEntry(file).id)
                }
            }

            override fun onUrlReceived(url: String) {
                mainHandler.post {
                    if (isDestroyed) return@post
                    downloadWallpaper(listOf(url), null)
                }
            }
        })
        val port = try {
            server.start()
        } catch (ex: Exception) {
            toast("Não foi possível abrir o servidor local")
            return
        }
        qrServer?.stop()
        qrServer = server

        val address = "http://" + ip + ":" + port + "/?t=" + token
        val density = resources.displayMetrics.density

        val image = ImageView(this)
        image.setImageBitmap(QrCode.bitmap(address, 560))
        image.layoutParams = LinearLayout.LayoutParams((240 * density).toInt(), (240 * density).toInt())

        val text = TextView(this)
        text.gravity = Gravity.CENTER
        text.setPadding(0, (12 * density).toInt(), 0, 0)
        text.text = "Lê o QR Code com a câmara do telemóvel (ligado à mesma rede da box) e envia uma " +
            "imagem ou um vídeo, ou cola um endereço.\n\n" + address

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        val pad = (20 * density).toInt()
        box.setPadding(pad, pad, pad, 0)
        box.addView(image)
        box.addView(text)

        AlertDialog.Builder(this)
            .setTitle("Enviar fundo do telemóvel")
            .setView(box)
            .setNegativeButton("Fechar", null)
            .setOnDismissListener {
                server.stop()
                if (qrServer === server) qrServer = null
            }
            .showWithSounds()
    }

    private fun pickWallpaperToDelete() {
        val files = WallpaperFiles.list(this)
        if (files.isEmpty()) {
            Toast.makeText(this, "Não há fundos descarregados", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = Array(files.size) {
            files[it].displayName + if (files[it].isVideo) " (vídeo animado)" else " (imagem fixa)"
        }
        AlertDialog.Builder(this)
            .setTitle("Apagar qual fundo?")
            .setItems(labels) { _, which ->
                val w = files[which]
                AlertDialog.Builder(this)
                    .setTitle("Apagar \"" + w.displayName + "\"?")
                    .setPositiveButton("Apagar") { _, _ ->
                        w.file.delete()
                        if (prefs.getString(KEY_WALLPAPER, Wallpapers.DEFAULT_ID) == w.id) {
                            prefs.edit().remove(KEY_WALLPAPER).apply()
                        }
                        applyWallpaper()
                    }
                    .setNegativeButton("Cancelar", null)
                    .showWithSounds()
            }
            .setNegativeButton("Voltar") { _, _ -> showWallpaperMenu() }
            .showWithSounds()
    }

    // ------------------------------------------------------------ Opções

    private fun showMenu() {
        val city = prefs.getString(KEY_CITY, DEFAULT_CITY) ?: DEFAULT_CITY
        val fahrenheit = prefs.getBoolean(KEY_FAHRENHEIT, false)
        val showWeather = prefs.getBoolean(KEY_SHOW_WEATHER, true)
        val showRecent = prefs.getBoolean(KEY_SHOW_RECENT, true)
        val sounds = prefs.getBoolean(KEY_SOUNDS, true)
        val suggest = prefs.getBoolean(KEY_SUGGEST, true)
        val streamPkg = prefs.getString(KEY_STREAM_APP, null)
        val streamLabel = allEntries.firstOrNull { it.packageName == streamPkg }?.label
            ?: "automático (Stremio)"

        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        fun add(label: String, action: () -> Unit) {
            labels.add(label)
            actions.add(action)
        }

        add("Fundos de ecrã…") { showWallpaperMenu() }
        add("Sons do comando: " + (if (sounds) "ligados" else "desligados")) {
            prefs.edit().putBoolean(KEY_SOUNDS, !sounds).apply()
            Sounds.enabled = !sounds
            showMenu()
        }
        add("Sugestões de filmes e séries: " + (if (suggest) "ligadas" else "desligadas")) {
            prefs.edit().putBoolean(KEY_SUGGEST, !suggest).apply()
            applyMediaSections()
            if (!suggest) refreshSuggestions(false)
            showMenu()
        }
        add("Abrir sugestões com: " + streamLabel) { pickStreamApp() }
        add("Linha \"Usadas recentemente\": " + (if (showRecent) "ligada" else "desligada")) {
            prefs.edit().putBoolean(KEY_SHOW_RECENT, !showRecent).apply()
            applyEntries()
            showMenu()
        }
        add("Limpar apps recentes") {
            prefs.edit().remove(KEY_RECENT).apply()
            applyEntries()
            Toast.makeText(this, "Apps recentes limpas", Toast.LENGTH_SHORT).show()
            showMenu()
        }
        add("Limpar \"Continuar a ver\"") {
            prefs.edit().remove(KEY_HISTORY).apply()
            applyMediaSections()
            Toast.makeText(this, "Lista limpa", Toast.LENGTH_SHORT).show()
            showMenu()
        }
        add("Cidade do tempo: " + city) { askCity() }
        add("Unidade de temperatura: " + (if (fahrenheit) "°F" else "°C")) {
            prefs.edit().putBoolean(KEY_FAHRENHEIT, !fahrenheit).apply()
            refreshWeather()
            showMenu()
        }
        add("Previsão do tempo: " + (if (showWeather) "ligada" else "desligada")) {
            prefs.edit().putBoolean(KEY_SHOW_WEATHER, !showWeather).apply()
            refreshWeather()
            showMenu()
        }
        add("Escolher apps em destaque") { pickFeatured() }
        add("Esconder ou mostrar apps") { pickHidden() }
        add("Repor predefinições") { confirmReset() }

        AlertDialog.Builder(this)
            .setTitle("Opções")
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .showWithSounds()
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
            .showWithSounds()
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
            .showWithSounds()
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
            .showWithSounds()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Repor predefinições")
            .setMessage(
                "Voltar ao fundo, sons, sugestões, cidade, unidade, apps em destaque, apps escondidas e " +
                    "recentes iniciais? Os ficheiros de fundos descarregados não são apagados."
            )
            .setPositiveButton("Repor") { _, _ ->
                prefs.edit().clear().putInt(KEY_UI_VERSION, 2).apply()
                Sounds.enabled = true
                applyWallpaper()
                applyEntries()
                applyMediaSections()
                refreshWeather()
                refreshSuggestions(true)
                focusFirst()
            }
            .setNegativeButton("Cancelar", null)
            .showWithSounds()
    }
}
