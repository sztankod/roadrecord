package hu.roadrecord.app.ui.widget

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class DrivingMotionTest {
    @Test fun loopIsWithinRequestedRange() {
        assertTrue(DrivingMotion.LOOP_DURATION_MS in 1500L..2000L)
    }
    @Test fun roadMovesContinuouslyRightToLeft() {
        var previous = DrivingMotion.roadOffset(0f)
        for (step in 1..100) {
            val next = DrivingMotion.roadOffset(step / 100f)
            assertTrue(next < previous)
            previous = next
        }
    }
    @Test fun roadAndWheelsMeetAtLoopBoundary() {
        assertEquals(0f, DrivingMotion.roadOffset(1f) % DrivingMotion.DASH_PITCH, .0001f)
        assertEquals(0f, DrivingMotion.wheelDegrees(1f) % 360f, .0001f)
        assertEquals(DrivingMotion.suspensionOffset(0f), DrivingMotion.suspensionOffset(1f), .0001f)
    }
    @Test fun wheelsTurnClockwiseForRightFacingCar() {
        assertEquals(90f, DrivingMotion.wheelDegrees(.25f), .0001f)
    }
    @Test fun suspensionRemainsSubtle() {
        for (step in 0..100) assertTrue(abs(DrivingMotion.suspensionOffset(step / 100f)) <= .301f)
    }
}
