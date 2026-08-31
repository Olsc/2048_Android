package com.olsc.a2048

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.TimeInterpolator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import com.olsc.a2048.Game2048.Direction
import com.olsc.a2048.Game2048.MoveResult
import kotlin.math.abs
import kotlin.math.min

/**
 * 2048 棋盘：管理 4x4 瓦片、手势滑动与 Q 弹动画。
 */
class BoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    interface Listener {
        fun onScoreChanged(score: Int, best: Int)
        fun onGameOver(score: Int)
        fun onWon(score: Int)
    }

    private val engine = Game2048()
    private val tiles = Array(Game2048.SIZE) { arrayOfNulls<TileView>(Game2048.SIZE) }
    private var busy = false

    private var generation = 0

    val scoreNow: Int get() = engine.score
    val gridValues: IntArray get() = engine.flattenedGrid()
    val isWon: Boolean get() = engine.won
    val isGameOver: Boolean get() = engine.gameOver

    private companion object {
        const val STATE_GRID = "board.grid"
        const val STATE_SCORE = "board.score"
        const val STATE_BEST = "board.best"
    }

    var listener: Listener? = null

    private val cellPadding = dp(8f)
    private val cellGap = dp(9f)
    private var tileSize = 0f
    private val slotRadius = dp(11f)

    private val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val slotStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val slotRect = RectF()

    private val moveInterpolator: TimeInterpolator = DecelerateInterpolator(1.6f)
    private val popInterpolator: TimeInterpolator = OvershootInterpolator(2.2f)

    private var scrollAccumX = 0f
    private var scrollAccumY = 0f
    private var gestureMoved = false

    private val themeListener = { invalidate() }

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        CartoonThemeManager.addListener(themeListener)
    }

    override fun onDetachedFromWindow() {
        CartoonThemeManager.removeListener(themeListener)
        super.onDetachedFromWindow()
    }

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                scrollAccumX = 0f
                scrollAccumY = 0f
                gestureMoved = false
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                if (busy || gestureMoved) return true
                scrollAccumX -= distanceX
                scrollAccumY -= distanceY
                val threshold = dp(24f)
                if (abs(scrollAccumX) > threshold || abs(scrollAccumY) > threshold) {
                    val direction = if (abs(scrollAccumX) > abs(scrollAccumY)) {
                        if (scrollAccumX > 0) Direction.RIGHT else Direction.LEFT
                    } else {
                        if (scrollAccumY > 0) Direction.DOWN else Direction.UP
                    }
                    gestureMoved = true
                    performMove(direction)
                }
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                vx: Float,
                vy: Float,
            ): Boolean {
                if (busy || gestureMoved) return true
                val start = e1 ?: return false
                val dx = e2.x - start.x
                val dy = e2.y - start.y
                val threshold = dp(18f)
                val direction = when {
                    abs(dx) > abs(dy) && abs(dx) > threshold -> if (dx > 0) Direction.RIGHT else Direction.LEFT
                    abs(dy) > abs(dx) && abs(dy) > threshold -> if (dy > 0) Direction.DOWN else Direction.UP
                    else -> null
                }
                if (direction != null) performMove(direction)
                return direction != null
            }
        },
    )

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wSize = MeasureSpec.getSize(widthMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)
        val size = when {
            wSize > 0 && hSize > 0 -> min(wSize, hSize)
            wSize > 0 -> wSize
            hSize > 0 -> hSize
            else -> dp(300f).toInt()
        }
        setMeasuredDimension(size, size)
        val spec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        measureChildren(spec, spec)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        tileSize = (w - 2 * cellPadding - 3 * cellGap) / Game2048.SIZE.toFloat()
        removeCallbacks(relayoutRunnable)
        post(relayoutRunnable)
    }

    private val relayoutRunnable = Runnable {
        for (i in 0 until childCount) {
            val t = getChildAt(i) as? TileView ?: continue
            if (t.row in 0 until Game2048.SIZE && t.col in 0 until Game2048.SIZE) {
                placeTile(t, t.row, t.col)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val isNight = CartoonThemeManager.isNightMode

        slotPaint.color = if (isNight) 0x1AFFFFFF.toInt() else 0x184A3E3D.toInt()
        slotStrokePaint.color = if (isNight) 0x12FFFFFF.toInt() else 0x124A3E3D.toInt()

        for (r in 0 until Game2048.SIZE) {
            for (c in 0 until Game2048.SIZE) {
                val left = cellX(c)
                val top = cellY(r)
                slotRect.set(left, top, left + tileSize, top + tileSize)
                canvas.drawRoundRect(slotRect, slotRadius, slotRadius, slotPaint)
                canvas.drawRoundRect(slotRect, slotRadius, slotRadius, slotStrokePaint)
            }
        }
    }

    // ---------- 对外 API ----------

    fun newGame() {
        generation++
        busy = false
        removeAllViews()
        for (r in 0 until Game2048.SIZE) for (c in 0 until Game2048.SIZE) tiles[r][c] = null
        engine.reset()
        for (r in 0 until Game2048.SIZE) for (c in 0 until Game2048.SIZE) {
            val v = engine.grid[r][c]
            if (v != 0) spawnTile(r, c, v)
        }
        listener?.onScoreChanged(engine.score, engine.best)
    }

    fun initBest(value: Int) {
        engine.setBest(value)
    }

    fun saveState(bundle: Bundle) {
        bundle.putIntArray(STATE_GRID, engine.flattenedGrid())
        bundle.putInt(STATE_SCORE, engine.score)
        bundle.putInt(STATE_BEST, engine.best)
    }

    fun restoreState(bundle: Bundle) {
        val gridValues = bundle.getIntArray(STATE_GRID) ?: return
        restoreGrid(gridValues, bundle.getInt(STATE_SCORE), bundle.getInt(STATE_BEST))
    }

    fun restoreGrid(gridValues: IntArray, score: Int, best: Int) {
        generation++
        busy = false
        removeAllViews()
        for (r in 0 until Game2048.SIZE) for (c in 0 until Game2048.SIZE) tiles[r][c] = null
        engine.restoreState(gridValues, score, best)
        for (r in 0 until Game2048.SIZE) for (c in 0 until Game2048.SIZE) {
            val v = engine.grid[r][c]
            if (v != 0) {
                val tile = createTile()
                placeTile(tile, r, c)
                tile.setValue(v)
            }
        }
        listener?.onScoreChanged(engine.score, engine.best)
        if (engine.gameOver) {
            val gen = generation
            post { if (gen == generation) listener?.onGameOver(engine.score) }
        }
    }

    // ---------- 内部实现 ----------

    private fun cellX(c: Int) = cellPadding + c * (tileSize + cellGap)
    private fun cellY(r: Int) = cellPadding + r * (tileSize + cellGap)

    private fun createTile(): TileView {
        val tile = TileView(context)
        addView(tile, FrameLayout.LayoutParams(tileSize.toInt(), tileSize.toInt()))
        return tile
    }

    private fun placeTile(tile: TileView, r: Int, c: Int) {
        val lp = tile.layoutParams as FrameLayout.LayoutParams
        lp.width = tileSize.toInt()
        lp.height = tileSize.toInt()
        lp.leftMargin = cellX(c).toInt()
        lp.topMargin = cellY(r).toInt()
        tile.translationX = 0f
        tile.translationY = 0f
        tile.row = r
        tile.col = c
        tile.requestLayout()
        tiles[r][c] = tile
    }

    private fun removeTile(tile: TileView) {
        tile.row = -1
        tile.col = -1
        removeView(tile)
    }

    private fun rebuildTiles() {
        for (r in 0 until Game2048.SIZE) for (c in 0 until Game2048.SIZE) tiles[r][c] = null
        for (i in 0 until childCount) {
            val t = getChildAt(i) as? TileView ?: continue
            if (t.row in 0 until Game2048.SIZE && t.col in 0 until Game2048.SIZE) {
                tiles[t.row][t.col] = t
            }
        }
    }

    private fun spawnTile(r: Int, c: Int, v: Int) {
        val tile = createTile()
        placeTile(tile, r, c)
        tile.setValue(v)
        tile.alpha = 0f
        tile.scaleX = 0.1f
        tile.scaleY = 0.1f
        tile.rotation = -8f

        tile.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .rotation(0f)
            .setDuration(320L)
            .setInterpolator(popInterpolator)
            .withEndAction {
                tile.alpha = 1f
                tile.scaleX = 1f
                tile.scaleY = 1f
                tile.rotation = 0f
            }
            .start()
    }

    private fun bounceTile(tile: TileView) {
        val bounce = ObjectAnimator.ofPropertyValuesHolder(
            tile,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 1.28f, 0.88f, 1f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 0.78f, 1.18f, 1f),
            PropertyValuesHolder.ofFloat("rotation", 0f, -5f, 3f, 0f),
        )
        bounce.duration = 280L
        bounce.interpolator = DecelerateInterpolator(1.4f)
        bounce.start()
    }

    private fun showScoreToast(r: Int, c: Int, scoreGain: Int) {
        if (scoreGain <= 0) return
        val toast = android.widget.TextView(context).apply {
            text = "+$scoreGain"
            setTextColor(0xFFFF8800.toInt())
            textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(dp(3f), 0f, dp(1.5f), 0x77000000.toInt())
        }
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        val cx = cellX(c) + tileSize / 2f - dp(18f)
        val cy = cellY(r) + tileSize / 2f - dp(18f)
        lp.leftMargin = cx.toInt()
        lp.topMargin = cy.toInt()
        toast.layoutParams = lp

        addView(toast)

        toast.scaleX = 0.4f
        toast.scaleY = 0.4f
        toast.alpha = 1f
        toast.animate()
            .translationY(-dp(45f))
            .scaleX(1.25f)
            .scaleY(1.25f)
            .alpha(0f)
            .setDuration(560L)
            .setInterpolator(DecelerateInterpolator(1.3f))
            .withEndAction { removeView(toast) }
            .start()
    }

    private fun performMove(direction: Direction) {
        val result = engine.move(direction) ?: return
        busy = true
        val gen = generation
        val duration = 150L
        var remaining = 0

        for (anim in result.anims) {
            val tile = tiles[anim.fromRow][anim.fromCol] ?: continue
            remaining++
            val dx = (anim.toCol - anim.fromCol) * (tileSize + cellGap)
            val dy = (anim.toRow - anim.fromRow) * (tileSize + cellGap)

            val moveAnim = ObjectAnimator.ofPropertyValuesHolder(
                tile,
                PropertyValuesHolder.ofFloat("translationX", 0f, dx),
                PropertyValuesHolder.ofFloat("translationY", 0f, dy),
            )
            moveAnim.duration = duration
            moveAnim.interpolator = moveInterpolator
            moveAnim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (gen != generation || tile.parent !== this@BoardView) return
                    when {
                        anim.consumed -> {
                            removeTile(tile)
                        }

                        anim.merge -> {
                            tile.setValue(anim.value)
                            placeTile(tile, anim.toRow, anim.toCol)
                            bounceTile(tile)
                            showScoreToast(anim.toRow, anim.toCol, anim.value)
                        }

                        else -> {
                            placeTile(tile, anim.toRow, anim.toCol)
                        }
                    }
                    remaining--
                    if (remaining == 0) finishMove(result, gen)
                }
            })
            moveAnim.start()
        }
        if (remaining == 0) finishMove(result, gen)
    }

    private fun finishMove(result: MoveResult, gen: Int) {
        if (gen != generation) return
        rebuildTiles()

        val nr = result.newTileRow
        val nc = result.newTileCol
        if (nr in 0 until Game2048.SIZE && nc in 0 until Game2048.SIZE) {
            spawnTile(nr, nc, engine.grid[nr][nc])
        }

        listener?.onScoreChanged(engine.score, engine.best)
        busy = false
        when {
            result.gameOver -> listener?.onGameOver(engine.score)
            result.won -> listener?.onWon(engine.score)
        }
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
}
