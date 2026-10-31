package com.example.tvlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.sin

// Fundo desenhado por código: gradientes (que podem rodar devagar) ou ondas de luz azuis.
// A animação corre a cerca de 15 imagens/s, para pesar pouco numa box fraca, e pára sozinha
// quando a vista fica escondida ou em pausa.
class GradientView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var preset: GradientPreset = Wallpapers.BUILTINS[0]
    private val paint = Paint()
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var angle = 45f
    private var phase = 0f
    private var paused = false

    private val ribbonColors = intArrayOf(
        0x1AFFFFFF, 0x2A3D8BFF, 0x1FFFFFFF, 0x332B5BFF
    )

    private val tick = object : Runnable {
        override fun run() {
            if (preset.waves) {
                phase += 0.02f
            } else {
                angle = (angle + 0.5f) % 360f
            }
            invalidate()
            if (shouldRun()) postDelayed(this, 66)
        }
    }

    private fun shouldRun(): Boolean {
        return preset.animated && !paused && isAttachedToWindow && visibility == VISIBLE
    }

    private fun restart() {
        removeCallbacks(tick)
        if (shouldRun()) postDelayed(tick, 66)
    }

    fun setPreset(p: GradientPreset) {
        preset = p
        angle = 45f
        phase = 0f
        invalidate()
        restart()
    }

    fun setPaused(value: Boolean) {
        paused = value
        restart()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restart()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView === this) restart()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        if (preset.waves) {
            drawWaves(canvas, w, h)
        } else {
            drawGradient(canvas, w, h)
        }
    }

    private fun drawGradient(canvas: Canvas, w: Float, h: Float) {
        val rad = Math.toRadians(angle.toDouble())
        val r = (Math.hypot(w.toDouble(), h.toDouble()) / 2.0).toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val dx = (Math.cos(rad) * r).toFloat()
        val dy = (Math.sin(rad) * r).toFloat()
        paint.shader = LinearGradient(
            cx - dx, cy - dy, cx + dx, cy + dy,
            preset.colors, null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
    }

    // Azul-escuro em degradé vertical, uma luz suave no canto e quatro faixas onduladas translúcidas.
    private fun drawWaves(canvas: Canvas, w: Float, h: Float) {
        paint.shader = LinearGradient(0f, 0f, 0f, h, preset.colors, null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)

        paint.shader = RadialGradient(
            w * 0.72f, h * 0.12f, max(w, h) * 0.65f,
            0x404DA3FF, 0x004DA3FF, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val step = 24f
        for (i in 0 until ribbonColors.size) {
            val baseY = h * (0.42f + 0.13f * i)
            val amp = h * (0.05f + 0.012f * i)
            val k = (2.0 * Math.PI / (w * (0.9f + 0.25f * i))).toFloat()
            val ph = phase * (1f + 0.35f * i) + i * 1.7f
            path.reset()
            path.moveTo(0f, h)
            var x = 0f
            while (x <= w + step) {
                val y = baseY +
                    amp * sin(k * x + ph) +
                    amp * 0.5f * sin(2.3f * k * x - ph * 0.7f)
                path.lineTo(x, y)
                x += step
            }
            path.lineTo(w + step, h)
            path.close()
            wavePaint.color = ribbonColors[i]
            canvas.drawPath(path, wavePaint)
        }
    }
}
