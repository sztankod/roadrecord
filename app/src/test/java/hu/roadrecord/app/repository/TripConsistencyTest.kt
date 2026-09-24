package hu.roadrecord.app.repository

import hu.roadrecord.app.data.EventType
import hu.roadrecord.app.data.Trip
import hu.roadrecord.app.data.WorkEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class TripConsistencyTest {
    private val closed = Trip(id = 7, workDayId = 4, startEventId = 10, endEventId = 11, distanceMeters = 42.0)

    @Test fun deletingOwnEndEventReopensTripWithoutChangingRecordedData() {
        val reopened = reopenTripForDeletedEvent(closed, WorkEvent(11, 4, EventType.TRIP_END, 123))
        assertEquals(null, reopened.endEventId)
        assertEquals(closed.copy(endEventId = null), reopened)
    }

    @Test fun unrelatedOrNonEndEventDoesNotReopenTrip() {
        assertEquals(closed, reopenTripForDeletedEvent(closed, WorkEvent(12, 4, EventType.TRIP_END, 123)))
        assertEquals(closed, reopenTripForDeletedEvent(closed, WorkEvent(11, 4, EventType.TRIP_START, 123)))
    }
}
