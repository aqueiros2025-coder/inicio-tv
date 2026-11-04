package com.example.tvlauncher

import android.app.AlertDialog
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.view.KeyEvent
import android.view.Window
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

private data class Tone(val freq: Double, val ms: Int, val amp: Double)

// Sons curtos de toque ao carregar nas teclas do comando. Os sons são gerados pela própria app
// (sem ficheiros de terceiros) e tocados com SoundPool, que tem pouca latência.
object Sounds {

    @Volatile
    var enabled = true

    private var pool: SoundPool? = null
    private var move = 0
    private var select = 0
    private var back = 0
    private var lastMove = 0L

    fun init(context: Context) {
        if (pool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
        val dir = File(context.cacheDir, "sounds")
        dir.mkdirs()
        try {
            move = p.load(
                writeWav(File(dir, "move_v1.wav"), listOf(Tone(1500.0, 24, 0.45))), 1
            )
            select = p.load(
                writeWav(
                    File(dir, "select_v1.wav"),
                    listOf(Tone(740.0, 45, 0.5), Tone(1110.0, 70, 0.5))
                ), 1
            )
            back = p.load(
                writeWav(
                    File(dir, "back_v1.wav"),
                    listOf(Tone(900.0, 45, 0.45), Tone(600.0, 70, 0.45))
                ), 1
            )
            pool = p
        } catch (e: Exception) {
            p.release()
        }
    }

    // Chamado por cada tecla que chega à janela (ecrã principal e menus).
    fun onKey(event: KeyEvent) {
        if (!enabled || event.action != KeyEvent.ACTION_DOWN) return
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val now = SystemClock.uptimeMillis()
                if (now - lastMove >= 70) {
                    lastMove = now
                    play(move, 0.5f)
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_MENU -> if (event.repeatCount == 0) play(select, 0.8f)
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE -> if (event.repeatCount == 0) play(back, 0.7f)
            else -> {}
        }
    }

    private fun play(id: Int, volume: Float) {
        if (id != 0) pool?.play(id, volume, volume, 1, 0, 1f)
    }

    private fun writeWav(file: File, tones: List<Tone>): String {
        if (!file.exists()) {
            val rate = 22050
            val samples = ArrayList<Short>()
            for (t in tones) {
                val n = rate * t.ms / 1000
                val attackLen = rate / 500
                for (i in 0 until n) {
                    val time = i.toDouble() / rate
                    val progress = i.toDouble() / n
                    val attack = if (i < attackLen) i.toDouble() / attackLen else 1.0
                    val env = attack * (1.0 - progress) * (1.0 - progress)
                    val v = Math.sin(2.0 * Math.PI * t.freq * time) * env * t.amp
                    samples.add((v * 32767.0).toInt().toShort())
                }
            }
            val dataSize = samples.size * 2
            val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
            buf.put("RIFF".toByteArray(Charsets.US_ASCII))
            buf.putInt(36 + dataSize)
            buf.put("WAVE".toByteArray(Charsets.US_ASCII))
            buf.put("fmt ".toByteArray(Charsets.US_ASCII))
            buf.putInt(16)
            buf.putShort(1.toShort())
            buf.putShort(1.toShort())
            buf.putInt(rate)
            buf.putInt(rate * 2)
            buf.putShort(2.toShort())
            buf.putShort(16.toShort())
            buf.put("data".toByteArray(Charsets.US_ASCII))
            buf.putInt(dataSize)
            for (s in samples) buf.putShort(s)
            file.writeBytes(buf.array())
        }
        return file.absolutePath
    }
}

// Põe o som em todas as teclas que chegam a uma janela, sem mexer no resto do comportamento dela.
class SoundCallback(private val base: Window.Callback) : Window.Callback by base {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Sounds.onKey(event)
        return base.dispatchKeyEvent(event)
    }
}

// Mostra um diálogo com os sons do comando também ativos nos menus.
fun AlertDialog.Builder.showWithSounds(): AlertDialog {
    val dialog = create()
    val w = dialog.window
    if (w != null) {
        w.callback = SoundCallback(w.callback)
    }
    dialog.show()
    return dialog
}
