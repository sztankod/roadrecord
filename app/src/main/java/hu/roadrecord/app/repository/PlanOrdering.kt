package hu.roadrecord.app.repository

import hu.roadrecord.app.data.DailyPlacePlan
import hu.roadrecord.app.data.LocationPlace

/** Stored positions belong to the full day; optimizer positions belong to the remaining route. */
internal object PlanOrdering {
    fun ordered(plans: List<DailyPlacePlan>, places: List<LocationPlace>): List<DailyPlacePlan> {
        val byId = places.associateBy { it.id }
        val sorted = plans.sortedWith(compareBy<DailyPlacePlan> { it.sortHint ?: Int.MAX_VALUE }.thenBy { it.placeId })
        val starts = sorted.filter { byId[it.placeId]?.defaultTourAnchor == "START" }
            .sortedBy { byId[it.placeId]?.defaultTourOrder ?: 0 }
        val ends = sorted.filter { byId[it.placeId]?.defaultTourAnchor == "END" }
            .sortedBy { byId[it.placeId]?.defaultTourOrder ?: 0 }
        val anchors = (starts + ends).map { it.placeId }.toSet()
        return (starts + sorted.filterNot { it.placeId in anchors } + ends)
            .mapIndexed { index, plan -> plan.copy(sortHint = index) }
    }

    fun locks(plans: List<DailyPlacePlan>, places: List<LocationPlace>, routeIds: List<Long>): Map<Long, Int> {
        val ids = routeIds.toSet()
        val anchors = places.filter { it.defaultTourAnchor in setOf("START", "END") }.map { it.id }.toSet()
        return ordered(plans, places).filter { it.placeId in ids }.mapIndexedNotNull { index, plan ->
            if (plan.lockedPosition != null || plan.placeId in anchors) plan.placeId to index else null
        }.toMap()
    }

    /** Replace only the requested slots; never renumber a partial route over completed rows. */
    fun reorder(plans: List<DailyPlacePlan>, places: List<LocationPlace>, requested: List<Long>, unlockedId: Long? = null): List<DailyPlacePlan> {
        val previous = ordered(plans, places)
        val byId = previous.associateBy { it.placeId }
        val anchors = places.filter { it.defaultTourAnchor in setOf("START", "END") }.map { it.id }.toSet()
        val requestedIds = requested.distinct().filter { it in byId }.toSet()
        fun fixed(plan: DailyPlacePlan) = plan.placeId in anchors || (plan.lockedPosition != null && plan.placeId != unlockedId)
        val free = requested.distinct().mapNotNull { byId[it] }.filterNot { fixed(it) }.iterator()
        val merged = previous.map { plan ->
            if (plan.placeId in requestedIds && !fixed(plan)) free.next() else plan
        }
        return merged.mapIndexed { index, plan ->
            plan.copy(sortHint = index, lockedPosition = if (fixed(plan)) index else null)
        }
    }
}
