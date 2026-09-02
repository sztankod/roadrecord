package hu.roadrecord.app.ui.widget

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Rect
import android.os.PowerManager
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat

/** The same small, draw-only scene for Compose and the WindowManager overlay. */
class DrivingAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val scene = DrivingScene(resources)
    private val visibleRect = Rect()
    private var animator: ValueAnimator? = null
    private var progress = 0f
    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { updateAnimationState() }
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { updateAnimationState() }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) stopAnimation() else updateAnimationState()
        }
    }

    /** Callers supply real trip state; view/window/screen visibility is checked independently. */
    var motionEnabled = false
        set(value) {
            if (field == value) return
            field = value
            updateAnimationState()
        }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        // No hardware layer: each frame changes, so a layer would add a texture pass.
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnScrollChangedListener(scrollListener)
        viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        ContextCompat.registerReceiver(context, screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        updateAnimationState()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        context.unregisterReceiver(screenReceiver)
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) updateAnimationState() else stopAnimation()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) updateAnimationState() else stopAnimation()
    }

    private fun updateAnimationState() {
        // Short-circuit also protects callbacks during the View constructor.
        val shouldRun = motionEnabled && isAttachedToWindow && isShown && windowVisibility == VISIBLE &&
            power.isInteractive && !keyguard.isKeyguardLocked && getGlobalVisibleRect(visibleRect) &&
            !visibleRect.isEmpty && ValueAnimator.areAnimatorsEnabled()
        if (!shouldRun) {
            stopAnimation()
        } else if (animator == null) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = DrivingMotion.LOOP_DURATION_MS
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    progress = it.animatedValue as Float
                    // Invalidate only this view, never Compose state or the layout.
                    invalidate()
                }
                start()
            }
        }
    }

    private fun stopAnimation() {
        animator?.removeAllUpdateListeners()
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        scene.draw(canvas, width.toFloat(), height.toFloat(), progress)
    }
}
