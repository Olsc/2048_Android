package com.olsc.a2048

import android.animation.ValueAnimator
import android.content.Context
import android.view.animation.DecelerateInterpolator

/**
 * 卡通主题管理器：支持日间/夜间模式的平滑过度动画（themeProgress 从 0.0f 到 1.0f 渐变）。
 */
object CartoonThemeManager {
    private const val PREF_NAME = "a2048"
    private const val KEY_NIGHT = "is_night_mode"

    var isNightMode: Boolean = false
        private set

    /** 0.0f = 纯日间模式, 1.0f = 纯夜间模式 */
    var themeProgress: Float = 0f
        private set

    private var themeAnimator: ValueAnimator? = null
    private val listeners = HashSet<() -> Unit>()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        isNightMode = prefs.getBoolean(KEY_NIGHT, false)
        themeProgress = if (isNightMode) 1f else 0f
    }

    fun toggleNightMode(context: Context, durationMs: Long = 450L): Boolean {
        isNightMode = !isNightMode
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NIGHT, isNightMode)
            .apply()

        val start = themeProgress
        val target = if (isNightMode) 1f else 0f

        themeAnimator?.cancel()
        themeAnimator = ValueAnimator.ofFloat(start, target).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { anim ->
                themeProgress = anim.animatedValue as Float
                notifyThemeChanged()
            }
            start()
        }
        return isNightMode
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyThemeChanged() {
        for (l in ArrayList(listeners)) {
            l.invoke()
        }
    }
}
