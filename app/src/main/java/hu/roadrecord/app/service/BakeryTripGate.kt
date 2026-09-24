package hu.roadrecord.app.service

internal enum class BakeryTripAction { NONE, START, CLOSE }

/** Filters bakery-boundary jitter and never treats an unknown initial state as a return. */
internal class BakeryTripGate(
    private val requiredOutsideSamples: Int = 2,
    private val requiredInsideSamples: Int = 2,
) {
    private var previousInside: Boolean? = null
    private var wasTripActive = false
    private var outsideSamples = 0
    private var confirmedOutside = false
    private var insideSamples = 0

    fun reset() {
        previousInside = null
        wasTripActive = false
        outsideSamples = 0
        confirmedOutside = false
        insideSamples = 0
    }

    @Synchronized
    fun observe(inside: Boolean, tripActive: Boolean): BakeryTripAction {
        if (tripActive && !wasTripActive) {
            outsideSamples = 0
            confirmedOutside = false
            insideSamples = 0
        } else if (!tripActive && wasTripActive) {
            outsideSamples = 0
            confirmedOutside = false
            insideSamples = 0
        }

        val action = if (!tripActive) {
            if (previousInside == true && !inside) BakeryTripAction.START else BakeryTripAction.NONE
        } else if (!inside) {
            outsideSamples++
            if (outsideSamples >= requiredOutsideSamples) confirmedOutside = true
            insideSamples = 0
            BakeryTripAction.NONE
        } else if (confirmedOutside) {
            insideSamples++
            if (insideSamples >= requiredInsideSamples) {
                outsideSamples = 0
                confirmedOutside = false
                insideSamples = 0
                BakeryTripAction.CLOSE
            } else BakeryTripAction.NONE
        } else {
            outsideSamples = 0
            insideSamples = 0
            BakeryTripAction.NONE
        }

        previousInside = inside
        wasTripActive = tripActive
        return action
    }
}
