package com.olsc.a2048

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.os.Bundle
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), BoardView.Listener {

    private lateinit var board: BoardView
    private lateinit var boardGlass: CartoonCardView
    private lateinit var scoreCard: CartoonCardView
    private lateinit var bestCard: CartoonCardView
    private lateinit var newGameBtn: CartoonCardView
    private lateinit var themeToggleBtn: CartoonCardView
    private lateinit var themeToggleText: TextView
    private lateinit var titleText: TextView
    private lateinit var hintText: TextView
    private lateinit var scoreLabel: TextView
    private lateinit var scoreValue: TextView
    private lateinit var bestLabel: TextView
    private lateinit var bestValue: TextView
    private lateinit var newGameBtnText: TextView
    private lateinit var backdrop: CartoonBackgroundView
    private lateinit var overlay: FrameLayout
    private lateinit var overlayCard: CartoonCardView
    private lateinit var overlayTitle: TextView
    private lateinit var overlayMessage: TextView
    private lateinit var overlayPrimaryBtn: CartoonCardView
    private lateinit var overlayPrimaryText: TextView
    private lateinit var overlaySecondaryBtn: CartoonCardView
    private lateinit var overlaySecondaryText: TextView

    private val prefs by lazy { getSharedPreferences("a2048", MODE_PRIVATE) }
    private var lastBest = 0
    private var wonAnnounced = false
    private val argbEvaluator = ArgbEvaluator()

    private val themeListener = {
        applyThemeColors(CartoonThemeManager.themeProgress)
    }

    private companion object {
        const val KEY_GRID = "grid"
        const val KEY_SCORE = "score"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        CartoonThemeManager.init(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        board = findViewById(R.id.board)
        boardGlass = findViewById(R.id.boardGlass)
        scoreCard = findViewById(R.id.scoreCard)
        bestCard = findViewById(R.id.bestCard)
        newGameBtn = findViewById(R.id.newGameBtn)
        themeToggleBtn = findViewById(R.id.themeToggleBtn)
        themeToggleText = findViewById(R.id.themeToggleText)
        titleText = findViewById(R.id.title)
        hintText = findViewById(R.id.hintText)
        scoreLabel = findViewById(R.id.scoreLabel)
        scoreValue = findViewById(R.id.scoreValue)
        bestLabel = findViewById(R.id.bestLabel)
        bestValue = findViewById(R.id.bestValue)
        newGameBtnText = findViewById(R.id.newGameBtnText)
        backdrop = findViewById(R.id.backdrop)
        overlay = findViewById(R.id.overlay)
        overlayCard = findViewById(R.id.overlayCard)
        overlayTitle = findViewById(R.id.overlayTitle)
        overlayMessage = findViewById(R.id.overlayMessage)
        overlayPrimaryBtn = findViewById(R.id.overlayPrimaryBtn)
        overlayPrimaryText = findViewById(R.id.overlayPrimaryText)
        overlaySecondaryBtn = findViewById(R.id.overlaySecondaryBtn)
        overlaySecondaryText = findViewById(R.id.overlaySecondaryText)

        CartoonThemeManager.addListener(themeListener)

        setupCards()
        setupButtons()
        applyThemeColors(CartoonThemeManager.themeProgress)

        lastBest = prefs.getInt("best", 0)
        board.listener = this
        board.initBest(lastBest)

        val restore = savedInstanceState
        board.post {
            if (restore != null) {
                board.restoreState(restore)
                wonAnnounced = board.isWon
            } else {
                val savedGrid = prefs.getString(KEY_GRID, null)
                val values = savedGrid?.split(',')?.mapNotNull { it.toIntOrNull() }?.toIntArray()
                if (values != null && values.size == Game2048.SIZE * Game2048.SIZE) {
                    val savedScore = prefs.getInt(KEY_SCORE, 0)
                    board.restoreGrid(values, savedScore, lastBest.coerceAtLeast(savedScore))
                    wonAnnounced = board.isWon
                } else {
                    board.newGame()
                }
            }
        }
    }

    override fun onDestroy() {
        CartoonThemeManager.removeListener(themeListener)
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        saveGameState()
    }

    override fun onStop() {
        super.onStop()
        saveGameState()
    }

    private fun saveGameState() {
        if (!::board.isInitialized) return
        prefs.edit()
            .putString(KEY_GRID, board.gridValues.joinToString(","))
            .putInt(KEY_SCORE, board.scoreNow)
            .putInt("best", lastBest)
            .apply()
    }

    // ---------- 卡通卡片与色彩渐变过渡 ----------

    private fun setupCards() {
        boardGlass.cornerRadius = 22f
        listOf(scoreCard, bestCard, themeToggleBtn, newGameBtn).forEach { it.cornerRadius = 16f }
        overlayCard.cornerRadius = 28f
        listOf(overlayPrimaryBtn, overlaySecondaryBtn).forEach { it.cornerRadius = 20f }
    }

    private fun applyThemeColors(progress: Float) {
        val primaryColor = argbEvaluator.evaluate(progress, 0xFF4A3E3D.toInt(), 0xFFF5EFFB.toInt()) as Int
        val secondaryColor = argbEvaluator.evaluate(progress, 0xFF8C7A78.toInt(), 0xFFB3A8C5.toInt()) as Int

        titleText.setTextColor(primaryColor)
        hintText.setTextColor(secondaryColor)
        scoreLabel.setTextColor(secondaryColor)
        scoreValue.setTextColor(primaryColor)
        bestLabel.setTextColor(secondaryColor)
        bestValue.setTextColor(primaryColor)
        newGameBtnText.setTextColor(primaryColor)
        overlayTitle.setTextColor(primaryColor)
        overlayMessage.setTextColor(secondaryColor)
        overlayPrimaryText.setTextColor(primaryColor)
        overlaySecondaryText.setTextColor(primaryColor)

        themeToggleText.text = if (CartoonThemeManager.isNightMode) "🌙" else "☀️"
    }

    private fun setupButtons() {
        pressable(newGameBtn) {
            hideOverlay()
            board.newGame()
        }
        pressableThemeToggle(themeToggleBtn) {
            CartoonThemeManager.toggleNightMode(this)
        }
        pressable(overlayPrimaryBtn) {
        }
        pressable(overlaySecondaryBtn) {
            hideOverlay()
        }
    }

    /** 太阳/月亮旋转 360° 炫酷切换按键 */
    private fun pressableThemeToggle(btn: CartoonCardView, onClick: () -> Unit) {
        btn.setOnClickListener { onClick() }
        btn.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80L).start()
                }

                MotionEvent.ACTION_UP -> {
                    v.animate()
                        .scaleX(1f).scaleY(1f)
                        .rotationBy(360f)
                        .setDuration(450L)
                        .setInterpolator(OvershootInterpolator(1.8f))
                        .start()
                    v.performClick()
                }

                MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
                }
            }
            true
        }
    }

    /** 卡通 Q 弹普通按钮 */
    private fun pressable(btn: CartoonCardView, onClick: () -> Unit) {
        btn.setOnClickListener { onClick() }
        btn.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.88f).scaleY(0.88f).rotation(-2f).setDuration(80L).start()
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f).scaleY(1f).rotation(0f)
                        .setDuration(160L)
                        .setInterpolator(OvershootInterpolator(2.0f))
                        .start()
                    if (event.actionMasked == MotionEvent.ACTION_UP) v.performClick()
                }
            }
            true
        }
    }

    // ---------- BoardView.Listener ----------

    override fun onScoreChanged(score: Int, best: Int) {
        val oldScore = scoreValue.text.toString().toIntOrNull() ?: 0
        if (score > oldScore) {
            pulseView(scoreCard)
        }
        val oldBest = bestValue.text.toString().toIntOrNull() ?: 0
        if (best > lastBest) {
            pulseView(bestCard)
        }

        animateNumber(scoreValue, score)
        animateNumber(bestValue, best)
        if (best > lastBest) {
            lastBest = best
            prefs.edit().putInt("best", best).apply()
        }
        saveGameState()
    }

    private fun pulseView(view: View) {
        view.animate()
            .scaleX(1.16f).scaleY(1.16f)
            .setDuration(120L)
            .withEndAction {
                view.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(160L)
                    .setInterpolator(OvershootInterpolator(1.8f))
                    .start()
            }
            .start()
    }

    override fun onGameOver(score: Int) {
        wonAnnounced = false
        showOverlay(
            title = getString(R.string.game_over),
            message = getString(R.string.game_over_hint),
            primaryText = getString(R.string.play_again),
            showSecondary = false,
        ) {
            board.newGame()
        }
    }

    override fun onWon(score: Int) {
        if (wonAnnounced) return
        wonAnnounced = true
        showOverlay(
            title = getString(R.string.you_won),
            message = getString(R.string.you_won_hint),
            primaryText = getString(R.string.keep_going),
            showSecondary = true,
        ) {
            hideOverlay()
        }
    }

    // ---------- 遮罩与动画 ----------

    private fun showOverlay(
        title: String,
        message: String,
        primaryText: String,
        showSecondary: Boolean,
        onPrimary: () -> Unit,
    ) {
        overlayTitle.text = title
        overlayMessage.text = message
        overlayPrimaryText.text = primaryText
        overlaySecondaryBtn.visibility = if (showSecondary) View.VISIBLE else View.GONE
        overlayPrimaryBtn.setOnClickListener {
            hideOverlay()
            onPrimary()
        }

        overlay.alpha = 0f
        overlay.visibility = View.VISIBLE
        overlay.animate().alpha(1f).setDuration(220L).start()

        overlayCard.scaleX = 0.4f
        overlayCard.scaleY = 0.4f
        overlayCard.rotation = -4f
        overlayCard.animate()
            .scaleX(1f).scaleY(1f).rotation(0f)
            .setDuration(360L)
            .setInterpolator(OvershootInterpolator(2.0f))
            .start()
    }

    private fun hideOverlay() {
        overlay.animate()
            .alpha(0f)
            .setDuration(180L)
            .withEndAction { overlay.visibility = View.GONE }
            .start()
    }

    private var scoreAnimator: ValueAnimator? = null
    private var bestAnimator: ValueAnimator? = null

    private fun animateNumber(textView: TextView, target: Int) {
        val old = textView.text.toString().toIntOrNull() ?: 0
        if (old == target) return
        val animator = ValueAnimator.ofInt(old, target).apply {
            duration = 320L
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener { textView.text = it.animatedValue.toString() }
        }
        if (textView === scoreValue) scoreAnimator?.cancel() else bestAnimator?.cancel()
        if (textView === scoreValue) scoreAnimator = animator else bestAnimator = animator
        animator.start()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        board.saveState(outState)
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
}
