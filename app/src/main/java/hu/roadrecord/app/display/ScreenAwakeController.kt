package hu.roadrecord.app.display

import android.content.ContentResolver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Window
import android.view.WindowManager

/** Activity-window-only brightness/keep-awake. No wake lock, global settings write or polling. */
internal class ScreenAwakeController(private val window: Window, private val resolver: ContentResolver) {
    private val handler = Handler(Looper.getMainLooper())
    private val deadline = Runnable { applyPolicy() }
    private var options = ScreenAwakeOptions()
    private var active = false
    private var lastInteractionAt = 0L
    private var originalBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    private var keepingAwake = false

    fun updateOptions(value: ScreenAwakeOptions) {
        val normalized = value.normalized()
        if (normalized == options) return
        options = normalized
        if (active) {
            lastInteractionAt = SystemClock.elapsedRealtime()
            applyPolicy()
        }
    }

    fun resume() {
        if (active) return
        originalBrightness = window.attributes.screenBrightness
        active = true
        lastInteractionAt = SystemClock.elapsedRealtime()
        applyPolicy()
    }

    fun userInteracted() {
        if (!active) return
        lastInteractionAt = SystemClock.elapsedRealtime()
        applyPolicy()
    }

    fun pause() {
        handler.removeCallbacks(deadline)
        if (!active) return
        active = false
        setKeepAwake(false)
        setBrightness(originalBrightness)
    }

    private fun applyPolicy() {
        handler.removeCallbacks(deadline)
        if (!active) return
        val decision = options.decision(SystemClock.elapsedRealtime() - lastInteractionAt)
        setKeepAwake(decision.keepAwake)
        val brightness = if (decision.dimmed) {
            val baseline = if (originalBrightness >= 0f) originalBrightness else {
                Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 255) / 255f
            }
            // A low system brightness must never be raised by the dimming feature.
            minOf(options.dimPercent / 100f, baseline.coerceIn(.01f, 1f))
        } else originalBrightness
        setBrightness(brightness)
        decision.nextDelayMillis?.let { handler.postDelayed(deadline, it) }
    }

    private fun setKeepAwake(value: Boolean) {
        if (keepingAwake == value) return
        keepingAwake = value
        if (value) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setBrightness(value: Float) {
        val attributes = window.attributes
        if (attributes.screenBrightness == value) return
        attributes.screenBrightness = value
        window.attributes = attributes
    }
}
