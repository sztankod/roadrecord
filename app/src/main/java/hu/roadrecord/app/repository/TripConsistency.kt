package hu.roadrecord.app.repository

import hu.roadrecord.app.data.EventType
import hu.roadrecord.app.data.Trip
import hu.roadrecord.app.data.WorkEvent

internal fun reopenTripForDeletedEvent(trip: Trip, event: WorkEvent): Trip =
    if (event.type == EventType.TRIP_END && trip.endEventId == event.id) trip.copy(endEventId = null) else trip
