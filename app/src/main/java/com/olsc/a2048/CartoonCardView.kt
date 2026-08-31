package com.olsc.a2048

import android.animation.ArgbEvaluator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.FrameLayout

/**
 * 可爱卡通卡片/面板（Canvas 自绘）。
 *
 * 支持日间/夜间模式平滑过渡动效：卡片底色、描边色与阴影随主题渐变自然渐变。
 */
open class CartoonCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    /** 圆角半径（dp） */
    var cornerRadius = 22f
        set(value) {
            field = value
            invalidate()
        }

    /** 兼容性属性 */
    var showInnerGlow = true

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val argbEvaluator = ArgbEvaluator()

    private val themeListener = { invalidate() }

    init {
        setWillNotDraw(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        CartoonThemeManager.addListener(themeListener)
    }

    override fun onDetachedFromWindow() {
        CartoonThemeManager.removeListener(themeListener)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val corner = dp(cornerRadius)
        val shadowOffsetY = dp(3.5f)
        val shadowOffsetX = dp(1f)
        val strokeW = dp(1.2f)

        val p = CartoonThemeManager.themeProgress

        // 插值计算当前平滑颜色
        val bgColor = argbEvaluator.evaluate(p, 0xFFFFFDF9.toInt(), 0xFF262033.toInt()) as Int
        val strokeColor = argbEvaluator.evaluate(p, 0xFFE5D8C5.toInt(), 0xFF453B57.toInt()) as Int
        val shadowColor = argbEvaluator.evaluate(p, 0x1A4A3E3D.toInt(), 0x4B000000.toInt()) as Int

        // 1) 底部 3D 立体柔和阴影
        rect.set(shadowOffsetX, shadowOffsetY, w + shadowOffsetX, h + shadowOffsetY)
        path.reset()
        path.addRoundRect(rect, corner, corner, Path.Direction.CW)
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = shadowColor
        canvas.drawPath(path, paint)

        // 2) 卡片主体底色
        rect.set(0f, 0f, w, h)
        path.reset()
        path.addRoundRect(rect, corner, corner, Path.Direction.CW)
        paint.color = bgColor
        canvas.drawPath(path, paint)

        // 3) 细腻柔和描边
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeW
        paint.color = strokeColor
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
}
