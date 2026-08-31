package com.olsc.a2048

import android.animation.ArgbEvaluator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * 简约卡通风格背景：支持日间/夜间模式平滑无缝过渡渐变，配合气泡漂移与闪烁四角星。
 */
class CartoonBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var onAnimatedFrame: (() -> Unit)? = null

    private class CartoonBlob(
        val anchorX: Float,
        val anchorY: Float,
        val radiusRatio: Float,
        val dayColorHex: Long,
        val nightColorHex: Long,
        val speed: Float,
        val drift: Float,
        val phase: Float,
    )

    private class CartoonStar(
        val anchorX: Float,
        val anchorY: Float,
        val sizeRatio: Float,
        val speed: Float,
        val phase: Float,
    )

    private val blobs = listOf(
        CartoonBlob(0.82f, 0.15f, 0.42f, 0xFFFFEAA7, 0xFF6C5CE7, 0.25f, 0.85f, 0.0f),
        CartoonBlob(0.15f, 0.28f, 0.38f, 0xFFA8E6CF, 0xFF00CEC9, 0.20f, 0.60f, 1.5f),
        CartoonBlob(0.75f, 0.72f, 0.40f, 0xFFBDC581, 0xFF0984E3, 0.18f, 0.90f, 3.0f),
        CartoonBlob(0.28f, 0.82f, 0.35f, 0xFFC7ECEE, 0xFF74B9FF, 0.22f, 0.70f, 4.2f),
        CartoonBlob(0.50f, 0.45f, 0.30f, 0xFF74B9FF, 0xFFA29BFE, 0.15f, 0.50f, 2.1f),
        CartoonBlob(0.85f, 0.42f, 0.28f, 0xFFFFBE76, 0xFFFD79A8, 0.19f, 0.65f, 0.8f),
    )

    private val stars = listOf(
        CartoonStar(0.12f, 0.15f, 0.025f, 0.8f, 0.0f),
        CartoonStar(0.88f, 0.25f, 0.030f, 0.6f, 1.2f),
        CartoonStar(0.78f, 0.62f, 0.022f, 0.7f, 2.5f),
        CartoonStar(0.20f, 0.70f, 0.028f, 0.9f, 3.8f),
        CartoonStar(0.50f, 0.10f, 0.020f, 0.5f, 5.0f),
        CartoonStar(0.35f, 0.40f, 0.024f, 0.75f, 1.8f),
        CartoonStar(0.68f, 0.85f, 0.026f, 0.65f, 4.1f),
    )

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val starPath = Path()
    private val blobPaints = ArrayList<Pair<CartoonBlob, Paint>>()
    private val argbEvaluator = ArgbEvaluator()

    private var w = 0
    private var h = 0
    private var time = 0f
    private var lastNanos = 0L
    private var frameCount = 0

    private val themeListener = {
        invalidate()
    }

    private val frameCallback: Choreographer.FrameCallback = Choreographer.FrameCallback { nanos ->
        if (lastNanos != 0L) {
            val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceAtMost(0.05f)
            time += dt
            if (w > 0 && h > 0) {
                if (++frameCount % 2 == 0) invalidate()
                if (frameCount % 60 == 0) onAnimatedFrame?.invoke()
            }
        }
        lastNanos = nanos
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        CartoonThemeManager.addListener(themeListener)
        lastNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        CartoonThemeManager.removeListener(themeListener)
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        this.w = w
        this.h = h
        blobPaints.clear()
        for (b in blobs) {
            blobPaints.add(b to Paint(Paint.ANTI_ALIAS_FLAG))
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (w <= 0 || h <= 0) return

        val p = CartoonThemeManager.themeProgress

        // 1) 背景渐变平滑插值
        val bgTop = argbEvaluator.evaluate(p, 0xFFFFFBF3.toInt(), 0xFF1A1628.toInt()) as Int
        val bgMid = argbEvaluator.evaluate(p, 0xFFFFF5E4.toInt(), 0xFF141122.toInt()) as Int
        val bgBot = argbEvaluator.evaluate(p, 0xFFEBF5FB.toInt(), 0xFF0E0C1A.toInt()) as Int

        bgPaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(bgTop, bgMid, bgBot),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        // 2) 气泡渐变着色与绘制
        for ((b, paint) in blobPaints) {
            val hex = argbEvaluator.evaluate(p, b.dayColorHex.toInt(), b.nightColorHex.toInt()) as Int
            val alpha = (120 - (30 * p)).toInt()
            val core = Color.argb(alpha, Color.red(hex), Color.green(hex), Color.blue(hex))
            val edge = 0x00FFFFFF

            paint.shader = RadialGradient(
                0f, 0f, b.radiusRatio * w,
                intArrayOf(core, edge),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )

            val cx = (b.anchorX + sin(time * b.speed + b.phase) * b.drift * 0.06f) * w
            val cy = (b.anchorY + cos(time * b.speed * 0.8f + b.phase) * b.drift * 0.06f) * h
            val breath = 1f + 0.08f * sin(time * b.speed * 1.5f + b.phase)

            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(breath, breath)
            canvas.drawCircle(0f, 0f, b.radiusRatio * w, paint)
            canvas.restore()
        }

        // 3) 闪烁四角星星渐变绘制
        val starAlpha = (0x77 + (0x55 * p)).toInt().coerceIn(0, 255)
        starPaint.color = Color.argb(starAlpha, 255, 255, 255)

        for (star in stars) {
            val cx = star.anchorX * w
            val cy = (star.anchorY + sin(time * 0.4f + star.phase) * 0.02f) * h
            val scale = 0.7f + 0.35f * sin(time * star.speed * 3f + star.phase)
            val size = star.sizeRatio * w * scale

            drawCartoonStar(canvas, cx, cy, size)
        }
    }

    private fun drawCartoonStar(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        starPath.reset()
        starPath.moveTo(cx, cy - size)
        starPath.quadTo(cx, cy, cx + size, cy)
        starPath.quadTo(cx, cy, cx, cy + size)
        starPath.quadTo(cx, cy, cx - size, cy)
        starPath.quadTo(cx, cy, cx, cy - size)
        starPath.close()

        canvas.drawPath(starPath, starPaint)
    }
}
