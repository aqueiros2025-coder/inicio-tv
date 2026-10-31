package com.example.tvlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

// Fundo em gradiente. Se o preset for animado, o gradiente roda devagar (cerca de 15 imagens/s,
// para pesar pouco numa box fraca) e pára sozinho quando a vista fica escondida ou em pausa.
class GradientView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var preset: GradientPreset = Wallpapers.BUILTINS[0]
    private val paint = Paint()
    private var angle = 45f
    private var paused = false

    private val tick = object : Runnable {
        override fun run() {
            angle = (angle + 0.5f) % 360f
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
}
