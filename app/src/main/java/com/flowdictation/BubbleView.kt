package com.flowdictation

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator

/** The floating round button. Drawn entirely in code so there are no image assets to get wrong. */
class BubbleView(context: Context) : View(context) {
    private val density = context.resources.displayMetrics.density
    private var state = Dictation.State.IDLE
    private var phase = 0f
    private var animator: ValueAnimator? = null

    private val colorIdle = Color.parseColor("#3B5BDB")
    private val colorRecording = Color.parseColor("#E5484D")
    private val colorProcessing = Color.parseColor("#F5A524")

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(90, 255, 255, 255)
        strokeWidth = 1.5f * density
    }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()

    fun setBubbleState(s: Dictation.State) {
        if (s == state) return
        state = s
        animator?.cancel()
        animator = null
        phase = 0f
        syncAnimator()
        invalidate()
    }

    private fun syncAnimator() {
        val need = state != Dictation.State.IDLE && isAttachedToWindow
        if (need && animator == null) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = if (state == Dictation.State.RECORDING) 1100L else 900L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    phase = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else if (!need) {
            animator?.cancel()
            animator = null
            phase = 0f
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncAnimator()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val maxR = Math.min(width, height) / 2f
        val r = maxR * 0.80f

        if (state == Dictation.State.RECORDING) {
            halo.color = colorRecording
            halo.alpha = ((1f - phase) * 110f).toInt()
            canvas.drawCircle(cx, cy, r + (maxR - r) * phase, halo)
        }

        fill.color = when (state) {
            Dictation.State.IDLE -> colorIdle
            Dictation.State.RECORDING -> colorRecording
            Dictation.State.PROCESSING -> colorProcessing
        }
        canvas.drawCircle(cx, cy, r, fill)
        canvas.drawCircle(cx, cy, r - ring.strokeWidth / 2f, ring)

        when (state) {
            Dictation.State.IDLE -> drawMic(canvas, cx, cy, r)
            Dictation.State.RECORDING -> {
                rect.set(cx - 0.32f * r, cy - 0.32f * r, cx + 0.32f * r, cy + 0.32f * r)
                canvas.drawRoundRect(rect, 0.08f * r, 0.08f * r, glyph)
            }
            Dictation.State.PROCESSING -> {
                line.strokeWidth = 0.14f * r
                rect.set(cx - 0.42f * r, cy - 0.42f * r, cx + 0.42f * r, cy + 0.42f * r)
                canvas.drawArc(rect, phase * 360f, 260f, false, line)
            }
        }
    }

    private fun drawMic(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        // capsule
        rect.set(cx - 0.20f * r, cy - 0.55f * r, cx + 0.20f * r, cy + 0.14f * r)
        canvas.drawRoundRect(rect, 0.20f * r, 0.20f * r, glyph)
        // cup
        line.strokeWidth = 0.11f * r
        rect.set(cx - 0.40f * r, cy - 0.30f * r, cx + 0.40f * r, cy + 0.36f * r)
        canvas.drawArc(rect, 0f, 180f, false, line)
        // stem and base
        canvas.drawLine(cx, cy + 0.36f * r, cx, cy + 0.56f * r, line)
        canvas.drawLine(cx - 0.22f * r, cy + 0.56f * r, cx + 0.22f * r, cy + 0.56f * r, line)
    }
}
