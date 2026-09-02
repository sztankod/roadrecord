package hu.roadrecord.app.ui.widget

import kotlin.math.PI
import kotlin.math.sin

/** One periodic clock keeps road, wheels and suspension identical in both hosts. */
internal object DrivingMotion {
    const val LOOP_DURATION_MS = 1_800L
    const val DASH_PITCH = 50f
    fun roadOffset(progress: Float) = -progress * DASH_PITCH * 2f
    // Positive Android rotation is clockwise: correct for the right-facing reference car.
    fun wheelDegrees(progress: Float) = progress * 360f
    fun suspensionOffset(progress: Float) = sin(progress * 2.0 * PI).toFloat() * .3f
}
