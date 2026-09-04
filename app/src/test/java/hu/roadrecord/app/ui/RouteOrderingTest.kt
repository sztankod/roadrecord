package hu.roadrecord.app.ui

import hu.roadrecord.app.data.DailyPlacePlan
import hu.roadrecord.app.data.LocationPlace
import hu.roadrecord.app.repository.PlanOrdering
import org.junit.Assert.*
import org.junit.Test

class RouteOrderingTest {
    private val places = (1L..10L).map { id -> LocationPlace(id = id,
        name = if (id == 10L) "Zöld Paradicsom" else "Stop $id",
        latitude = 47.0, longitude = 19.0 + (10 - id) * .01) }
    private val start = LocationPlace(id = 100, name = "Start", latitude = 47.0, longitude = 19.0)
    private fun plans(completed: Set<Long> = emptySet()) = places.mapIndexed { index, p ->
        DailyPlacePlan(1, p.id, visited = p.id in completed, sortHint = index,
            lockedPosition = if (p.id == 10L) index else null)
    }
    private fun matrix(stops: List<LocationPlace>): RoadMatrix {
        val points = listOf(start) + stops
        // The last locked stop is deliberately cheapest, reproducing the old greedy selection.
        val values = Array(points.size) { from -> DoubleArray(points.size) { to ->
            if (from == to) 0.0 else if (points[to].id == 10L) 1.0 else 100.0
        } }
        return RoadMatrix(values, values)
    }

    @Test fun lastLockRemapsAfterCompletedStopsForRealOptimizerAndFallback() {
        val plans = plans(setOf(1, 2, 3))
        val remaining = places.filter { p -> plans.any { it.placeId == p.id && !it.visited } }
        val locks = PlanOrdering.locks(plans, places, remaining.map { it.id })
        assertEquals(mapOf(10L to 6), locks)
        assertEquals(10L, optimizeByTime(start, null, remaining, locks, matrix(remaining)).stops.last().id)
        assertEquals(10L, airFallback(start, null, remaining, locks).stops.last().id)
    }

    @Test fun allCompletionCountsKeepLastStopLastAcrossRepeatedOptimizeAndSave() {
        var saved = plans()
        for (count in 0..9) {
            saved = saved.map { it.copy(visited = it.placeId <= count) }
            val remaining = places.filter { it.id > count }
            val locks = PlanOrdering.locks(saved, places, remaining.map { it.id })
            val optimized = optimizeByTime(start, null, remaining, locks, matrix(remaining))
            assertEquals(10L, optimized.stops.last().id)
            saved = PlanOrdering.reorder(saved, places, optimized.stops.map { it.id })
            assertEquals(10, saved.map { it.sortHint }.distinct().size)
            assertEquals(9, saved.last().lockedPosition)
            assertEquals(count, saved.count { it.visited })
        }
    }

    @Test fun subsetSaveKeepsCompletedRowsAndGlobalPositions() {
        val original = plans(setOf(1, 3))
        val saved = PlanOrdering.reorder(original, places, listOf(9, 8, 7, 6, 5, 4, 2, 10))
        assertEquals(1L, saved[0].placeId)
        assertEquals(3L, saved[2].placeId)
        assertTrue(saved[0].visited && saved[2].visited)
        assertEquals(10L, saved.last().placeId)
        assertEquals((0..9).toList(), saved.map { it.sortHint })
    }

    @Test fun savedEndAnchorRepairsPreviouslyCorruptedFirstRecommendation() {
        val anchored = places.map { if (it.id == 10L) it.copy(defaultTourAnchor = "END") else it }
        val corrupt = plans().map { it.copy(sortHint = if (it.placeId == 10L) 0 else it.placeId.toInt(),
            lockedPosition = if (it.placeId == 10L) 0 else null) }
        val ordered = PlanOrdering.ordered(corrupt, anchored).sortedBy { it.sortHint }
        assertEquals(1L, ordered.first().placeId)
        assertEquals(10L, ordered.last().placeId)
        assertEquals(9, PlanOrdering.locks(corrupt, anchored, places.map { it.id })[10L])
    }

    @Test fun excludingStartEndAndInactiveStopsAlsoRemapsLocks() {
        val remaining = listOf(4L, 6L, 8L, 10L)
        assertEquals(mapOf(10L to 3), PlanOrdering.locks(plans(), places, remaining))
    }

    @Test fun savingCannotMoveLockedStopEvenIfRequestedFirst() {
        val saved = PlanOrdering.reorder(plans(), places, listOf(10L) + (1L..9L).toList())
        assertEquals(10L, saved.last().placeId)
        assertEquals(9, saved.last().lockedPosition)
    }

    @Test fun explicitDragCanUnlockOnlyDraggedStop() {
        val original = plans().map { if (it.placeId == 5L) it.copy(lockedPosition = 4) else it }
        val saved = PlanOrdering.reorder(original, places, listOf(10L) + (1L..9L).toList(), 10L)
        assertEquals(10L, saved.first().placeId)
        assertNull(saved.first().lockedPosition)
        assertEquals(5L, saved[4].placeId)
        assertEquals(4, saved[4].lockedPosition)
    }

    @Test fun multipleEndAnchorsRetainConfiguredOrderAndCompletedFlags() {
        val anchored = places.map { when (it.id) {
            9L -> it.copy(defaultTourAnchor = "END", defaultTourOrder = 0)
            10L -> it.copy(defaultTourAnchor = "END", defaultTourOrder = 1)
            else -> it
        } }
        val saved = PlanOrdering.reorder(plans(setOf(1, 9)), anchored, (10L downTo 1L).toList())
        assertEquals(listOf(9L, 10L), saved.takeLast(2).map { it.placeId })
        assertTrue(saved.first { it.placeId == 9L }.visited)
    }

    @Test fun emptyAndStaleDuplicateRequestsNeverLoseRows() {
        assertEquals(emptyMap<Long, Int>(), PlanOrdering.locks(plans(), places, emptyList()))
        val saved = PlanOrdering.reorder(plans(), places, listOf(2L, 2L, 999L, 1L))
        assertEquals((1L..10L).toSet(), saved.map { it.placeId }.toSet())
        assertEquals(10, saved.size)
        assertEquals(10, saved.map { it.sortHint }.distinct().size)
    }
}
