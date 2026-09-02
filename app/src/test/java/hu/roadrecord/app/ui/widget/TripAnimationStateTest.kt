package hu.roadrecord.app.ui.widget

import hu.roadrecord.app.data.EventType
import hu.roadrecord.app.data.WorkEvent
import org.junit.Assert.*
import org.junit.Test

class TripAnimationStateTest {
    private fun event(type: EventType, time: Long) = WorkEvent(workDayId = 1, type = type, timestamp = time)
    @Test fun noTripOrOnlyWorkDoesNotAnimate() {
        assertFalse(isTripActive(emptyList()))
        assertFalse(isTripActive(listOf(event(EventType.WORK_START, 1))))
    }
    @Test fun tripStartEnablesAndTripEndDisablesAnimation() {
        val events = listOf(event(EventType.WORK_START, 1), event(EventType.TRIP_START, 2))
        assertTrue(isTripActive(events))
        assertFalse(isTripActive(events + event(EventType.TRIP_END, 3)))
        assertFalse(isTripActive(events + event(EventType.WORK_END, 4)))
    }
    @Test fun eventOrderDoesNotAffectLatestState() {
        assertTrue(isTripActive(listOf(event(EventType.TRIP_START, 5), event(EventType.TRIP_END, 3), event(EventType.WORK_START, 1))))
    }
    @Test fun staleOverlayLabelCannotAnimateAfterTripClosure() {
        assertFalse(isOverlayOnRoad(false, "Úton"))
        assertFalse(isOverlayOnRoad(true, "Pékség"))
        assertTrue(isOverlayOnRoad(true, "Úton"))
        assertTrue(isOverlayOnRoad(true, "ÚTON"))
    }
}
