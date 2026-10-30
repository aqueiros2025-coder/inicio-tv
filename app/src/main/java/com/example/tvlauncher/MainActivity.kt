package com.example.tvlauncher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : Activity() {

    companion object {
        // Nome (ou parte do nome) das apps em destaque, pela ordem em que aparecem.
        private val FEATURED = listOf("nuvio", "arvio", "wuplay", "playtorrio", "stremio")

        // Apps que nunca aparecem na linha "Todas as apps".
        private val HIDDEN_PACKAGES = setOf(
            "com.google.android.videos",
            "com.google.android.music",
            "com.google.android.youtube.tvkids"
        )
    }

    private lateinit var heroTitle: TextView
    private lateinit var rvFeatured: RecyclerView
    private lateinit var rvAll: RecyclerView
    private lateinit var featuredAdapter: AppAdapter
    private lateinit var allAdapter: AppAdapter

    private val mainHandler = Handler(Looper.getMainLooper())
    private var loadId = 0
    private var focusedOnce = false

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        heroTitle = findViewById(R.id.heroTitle)
        rvFeatured = findViewById(R.id.rvFeatured)
        rvAll = findViewById(R.id.rvAll)

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
    }

    override fun onDestroy() {
        super.onDestroy()
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

    // O launcher não deve fechar com o botão Voltar.
    override fun onBackPressed() {
    }

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

            val featured = ArrayList<AppEntry>()
            for (key in FEATURED) {
                val hit = entries.firstOrNull {
                    it.label.lowercase().contains(key) || it.packageName.lowercase().contains(key)
                }
                if (hit != null && !featured.contains(hit)) featured.add(hit)
            }
            val rest = entries.filter { it !in featured && it.packageName !in HIDDEN_PACKAGES }

            mainHandler.post {
                if (id != loadId || isDestroyed) return@post
                featuredAdapter.submit(featured)
                allAdapter.submit(rest)
                if (!focusedOnce) focusFirst()
            }
        }.start()
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
}
