package com.olsc.a2048

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View

/**
 * 2048 数字瓦片（使用 assets 底图 + 自然圆角与高清立体数字）。
 */
class TileView constructor(context: Context) : View(context) {

    var value: Int = 0
        private set

    /** 当前所在格子（由 BoardView 维护）。 */
    var row: Int = -1
    var col: Int = -1

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 高清清晰数字画笔 */
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }

    private val bodyPath = Path()
    private val shadowPath = Path()
    private val bodyRect = RectF()
    private val shadowRect = RectF()
    private val srcRect = Rect()
    private val corner = dp(13f)

    private var bgColor = 0xFFEEE4DA.toInt()

    init {
        setWillNotDraw(false)
    }

    fun setValue(v: Int) {
        value = v
        bgColor = paletteColor(v)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        if (w <= 0 || h <= 0) return

        // 1) 底部立体柔和阴影
        val shadowY = dp(3.5f)
        shadowRect.set(0f, shadowY, w, h + shadowY)
        shadowPath.reset()
        shadowPath.addRoundRect(shadowRect, corner, corner, Path.Direction.CW)
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = 0x22000000.toInt()
        canvas.drawPath(shadowPath, paint)

        // 2) 瓦片底图（无粗描边，从 assets 加载 2.png, 4.png... 铺满自然圆角）
        bodyRect.set(0f, 0f, w, h)
        bodyPath.reset()
        bodyPath.addRoundRect(bodyRect, corner, corner, Path.Direction.CW)

        val tileBitmap = TileAssets.getBitmap(context, value)
        if (tileBitmap != null) {
            val saveCount = canvas.save()
            canvas.clipPath(bodyPath)
            srcRect.set(0, 0, tileBitmap.width, tileBitmap.height)
            paint.color = 0xFFFFFFFF.toInt()
            paint.alpha = 255
            canvas.drawBitmap(tileBitmap, srcRect, bodyRect, paint)
            canvas.restoreToCount(saveCount)
        } else {
            paint.color = bgColor
            canvas.drawPath(bodyPath, paint)
        }

        // 3) 数字绘制：纯白高清晰文本 + 微距柔和立体阴影（取代生硬黑色描边）
        val textSize = when {
            value < 100 -> dp(33f)
            value < 1000 -> dp(28f)
            value < 10000 -> dp(23f)
            else -> dp(19f)
        }
        textPaint.textSize = textSize
        textPaint.setShadowLayer(dp(2.5f), 0f, dp(1.5f), 0x77000000.toInt())

        val text = value.toString()
        val baseline = h / 2f - (textPaint.ascent() + textPaint.descent()) / 2f

        canvas.drawText(text, w / 2f, baseline, textPaint)
    }

    /** 兜底纯色底色 */
    private fun paletteColor(v: Int): Int = when (v) {
        2 -> 0xFFEEE4DA.toInt()
        4 -> 0xFFEDE0C8.toInt()
        8 -> 0xFFF2B179.toInt()
        16 -> 0xFFF59563.toInt()
        32 -> 0xFFF67C5F.toInt()
        64 -> 0xFFF65E3B.toInt()
        128 -> 0xFFEDCF72.toInt()
        256 -> 0xFFEDCC61.toInt()
        512 -> 0xFFEDC850.toInt()
        1024 -> 0xFFEDC53F.toInt()
        2048 -> 0xFFEDC22E.toInt()
        else -> 0xFF3C3A32.toInt()
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
}

/** Assets 瓦片底图缓存 */
private object TileAssets {
    private val cache = HashMap<Int, Bitmap>()

    fun getBitmap(context: Context, value: Int): Bitmap? {
        if (value <= 0) return null
        if (cache.containsKey(value)) return cache[value]

        val bitmap = try {
            context.assets.open("$value.png").use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            if (value > 2048) {
                try {
                    context.assets.open("2048.png").use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                } catch (e2: Exception) {
                    null
                }
            } else {
                null
            }
        }

        if (bitmap != null) {
            cache[value] = bitmap
        }
        return bitmap
    }
}
