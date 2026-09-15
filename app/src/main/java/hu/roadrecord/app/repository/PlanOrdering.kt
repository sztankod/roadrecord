package hu.roadrecord.app.repository

import hu.roadrecord.app.data.DailyPlacePlan
import hu.roadrecord.app.data.LocationPlace
import hu.roadrecord.app.data.TourOrderGroup

/** Stored positions belong to the full day; optimizer positions belong to the remaining route. */
internal object PlanOrdering {
    fun ordered(plans: List<DailyPlacePlan>, places: List<LocationPlace>, groups:List<TourOrderGroup> = emptyList()): List<DailyPlacePlan> {
        val byId = places.associateBy { it.id }
        val sorted = plans.sortedWith(compareBy<DailyPlacePlan> { it.sortHint ?: Int.MAX_VALUE }.thenBy { it.placeId })
        if(groups.isEmpty()){
            val starts=sorted.filter{byId[it.placeId]?.defaultTourAnchor=="START"}.sortedBy{byId[it.placeId]?.defaultTourOrder?:0}
            val ends=sorted.filter{byId[it.placeId]?.defaultTourAnchor=="END"}.sortedBy{byId[it.placeId]?.defaultTourOrder?:0}
            val legacy=(starts+ends).map{it.placeId}.toSet()
            return (starts+sorted.filterNot{it.placeId in legacy}+ends).mapIndexed{index,plan->plan.copy(sortHint=index)}
        }
        val orderedGroups=groups.sortedBy{it.sortOrder}
        val grouped=orderedGroups.associateWith{group->sorted.filter{byId[it.placeId]?.tourOrderGroupId==group.id}.sortedBy{byId[it.placeId]?.defaultTourOrder?:0}}
        val anchors=grouped.values.flatten().map{it.placeId}.toSet()
        val free=sorted.filterNot{it.placeId in anchors}
        val arranged=if(orderedGroups.size<2) grouped.values.flatten()+free else orderedGroups.dropLast(1).flatMap{grouped[it].orEmpty()}+free+grouped[orderedGroups.last()].orEmpty()
        return arranged
            .mapIndexed { index, plan -> plan.copy(sortHint = index) }
    }

    fun locks(plans: List<DailyPlacePlan>, places: List<LocationPlace>, routeIds: List<Long>,groups:List<TourOrderGroup> = emptyList()): Map<Long, Int> {
        val ids = routeIds.toSet()
        val anchors = places.filter { it.tourOrderGroupId!=null || it.defaultTourAnchor in setOf("START", "END") }.map { it.id }.toSet()
        return ordered(plans, places,groups).filter { it.placeId in ids }.mapIndexedNotNull { index, plan ->
            if (plan.lockedPosition != null || plan.placeId in anchors) plan.placeId to index else null
        }.toMap()
    }

    /** Replace only the requested slots; never renumber a partial route over completed rows. */
    fun reorder(plans: List<DailyPlacePlan>, places: List<LocationPlace>, requested: List<Long>, unlockedId: Long? = null,groups:List<TourOrderGroup> = emptyList()): List<DailyPlacePlan> {
        val previous = ordered(plans, places,groups)
        val byId = previous.associateBy { it.placeId }
        val anchors = places.filter { it.tourOrderGroupId!=null || it.defaultTourAnchor in setOf("START", "END") }.map { it.id }.toSet()
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
