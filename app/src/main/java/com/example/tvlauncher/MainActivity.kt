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
        private const val KEY_CITY = "city"
        private const val KEY_FAHRENHEIT = "fahrenheit"
        private const val KEY_SHOW_WEATHER = "show_weather"
        private const val DEFAULT_CITY = "Lisboa"
        private const val MAX_RECENT_STORED = 10
        private const val MAX_RECENT_SHOWN = 8
        private const val WEATHER_OK_MS = 30 * 60 * 1000L
        private const val WEATHER_RETRY_MS = 5 * 60 * 1000L
        private const val PICSUM_URL = "https://picsum.photos/1920/1080"
    }

    private lateinit var heroTitle: TextView
    private lateinit var weatherNow: TextView
    private lateinit var forecastRow: View
    private lateinit var forecastViews: List<TextView>
    private lateinit var recentSection: View
    private lateinit var rvFeatured: RecyclerView
    private lateinit var rvRecent: RecyclerView
    private lateinit var rvAll: RecyclerView
    private lateinit var featuredAdapter: AppAdapter
    private lateinit var recentAdapter: AppAdapter
    private lateinit var allAdapter: AppAdapter
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

        heroTitle = findViewById(R.id.heroTitle)
        weatherNow = findViewById(R.id.weatherNow)
        forecastRow = findViewById(R.id.forecastRow)
        forecastViews = listOf<TextView>(
            findViewById(R.id.fc0),
            findViewById(R.id.fc1),
            findViewById(R.id.fc2),
            findViewById(R.id.fc3)
        )
        recentSection = findViewById(R.id.recentSection)
        rvFeatured = findViewById(R.id.rvFeatured)
        rvRecent = findViewById(R.id.rvRecent)
        rvAll = findViewById(R.id.rvAll)
        bgGradient = findViewById(R.id.bgGradient)
        bgImage = findViewById(R.id.bgImage)
        bgVideo = findViewById(R.id.bgVideo)
        findViewById<View>(R.id.btnOptions).setOnClickListener { showMenu() }

        featuredAdapter = makeAdapter(R.layout.item_card, true)
        recentAdapter = makeAdapter(R.layout.item_card, true)
        allAdapter = makeAdapter(R.layout.item_app, false)

        rvFeatured.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvRecent.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvAll.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvFeatured.adapter = featuredAdapter
        rvRecent.adapter = recentAdapter
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

        applyWallpaper()
        loadApps()
        refreshWeather()
    }

    private fun makeAdapter(layout: Int, large: Boolean): AppAdapter {
        return AppAdapter(
            layout, large,
            { e -> onFocused(e) }, { e -> launch(e) }, { e -> showInfo(e) }
        )
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        bgGradient.setPaused(false)
        startVideo()
        if (allEntries.isNotEmpty()) applyEntries()
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
        rvFeatured.scrollToPosition(0)
        rvRecent.scrollToPosition(0)
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

    private fun onFocused(e: AppEntry) {
        heroTitle.text = e.label
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
                        Toast.makeText(this, "Não foi possível abrir esta imagem", Toast.LENGTH_LONG).show()
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
            .show()
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
            .show()
    }

    private fun showDownloadMenu() {
        val items = arrayOf(
            "Foto aleatória (Picsum, 1920×1080)",
            "Escrever um endereço (URL)"
        )
        AlertDialog.Builder(this)
            .setTitle("Descarregar da Internet")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> downloadWallpaper(PICSUM_URL, "Foto aleatória")
                    1 -> askUrl()
                    else -> {}
                }
            }
            .setNegativeButton("Voltar") { _, _ -> showWallpaperMenu() }
            .show()
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
                    downloadWallpaper(address, null)
                } else {
                    Toast.makeText(this, "O endereço tem de começar por http:// ou https://", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun downloadWallpaper(address: String, baseName: String?) {
        Toast.makeText(this, "A descarregar o fundo…", Toast.LENGTH_LONG).show()
        Thread {
            try {
                val file = WallpaperFiles.download(this, address, baseName)
                mainHandler.post {
                    if (isDestroyed) return@post
                    Toast.makeText(this, "Fundo descarregado", Toast.LENGTH_SHORT).show()
                    setWallpaper(WallpaperFiles.toEntry(file).id)
                }
            } catch (ex: Exception) {
                mainHandler.post {
                    if (isDestroyed) return@post
                    Toast.makeText(this, "Falhou: " + (ex.message ?: "erro desconhecido"), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun showQr() {
        val ip = WallpaperServer.localIp()
        if (ip == null) {
            Toast.makeText(this, "A box não está ligada a uma rede local", Toast.LENGTH_LONG).show()
            return
        }
        val token = UUID.randomUUID().toString().replace("-", "").take(10)
        val server = WallpaperServer(this, token, object : WallpaperServer.Listener {
            override fun onFileReceived(file: File) {
                mainHandler.post {
                    if (isDestroyed) return@post
                    Toast.makeText(this@MainActivity, "Fundo recebido do telemóvel", Toast.LENGTH_LONG).show()
                    setWallpaper(WallpaperFiles.toEntry(file).id)
                }
            }

            override fun onUrlReceived(url: String) {
                mainHandler.post {
                    if (isDestroyed) return@post
                    downloadWallpaper(url, null)
                }
            }
        })
        val port = try {
            server.start()
        } catch (ex: Exception) {
            Toast.makeText(this, "Não foi possível abrir o servidor local", Toast.LENGTH_LONG).show()
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
            .show()
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
                    .show()
            }
            .setNegativeButton("Voltar") { _, _ -> showWallpaperMenu() }
            .show()
    }

    // ------------------------------------------------------------ Opções

    private fun showMenu() {
        val city = prefs.getString(KEY_CITY, DEFAULT_CITY) ?: DEFAULT_CITY
        val fahrenheit = prefs.getBoolean(KEY_FAHRENHEIT, false)
        val showWeather = prefs.getBoolean(KEY_SHOW_WEATHER, true)
        val showRecent = prefs.getBoolean(KEY_SHOW_RECENT, true)
        val items = arrayOf(
            "Fundos de ecrã…",
            "Linha \"Usadas recentemente\": " + (if (showRecent) "ligada" else "desligada"),
            "Limpar apps recentes",
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
                    0 -> showWallpaperMenu()
                    1 -> {
                        prefs.edit().putBoolean(KEY_SHOW_RECENT, !showRecent).apply()
                        applyEntries()
                        showMenu()
                    }
                    2 -> {
                        prefs.edit().remove(KEY_RECENT).apply()
                        applyEntries()
                        Toast.makeText(this, "Apps recentes limpas", Toast.LENGTH_SHORT).show()
                        showMenu()
                    }
                    3 -> askCity()
                    4 -> {
                        prefs.edit().putBoolean(KEY_FAHRENHEIT, !fahrenheit).apply()
                        refreshWeather()
                        showMenu()
                    }
                    5 -> {
                        prefs.edit().putBoolean(KEY_SHOW_WEATHER, !showWeather).apply()
                        refreshWeather()
                        showMenu()
                    }
                    6 -> pickFeatured()
                    7 -> pickHidden()
                    8 -> confirmReset()
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
            .setMessage(
                "Voltar ao fundo, cidade, unidade, apps em destaque, apps escondidas e recentes iniciais? " +
                    "Os ficheiros de fundos descarregados não são apagados."
            )
            .setPositiveButton("Repor") { _, _ ->
                prefs.edit().clear().apply()
                applyWallpaper()
                applyEntries()
                refreshWeather()
                focusFirst()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
