package hu.roadrecord.app.ui.widget

import hu.roadrecord.app.data.EventType
import hu.roadrecord.app.data.WorkEvent

/** Use event state, never a persisted display label, to authorize an active-trip animation. */
internal fun isTripActive(events: List<WorkEvent>): Boolean =
    events.maxByOrNull { it.timestamp }?.type == EventType.TRIP_START

internal fun isOverlayOnRoad(tripActive: Boolean, currentName: String): Boolean =
    tripActive && currentName.equals("Úton", ignoreCase = true)
