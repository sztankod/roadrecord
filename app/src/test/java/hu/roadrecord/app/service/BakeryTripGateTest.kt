package hu.roadrecord.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class BakeryTripGateTest {
    @Test fun firstInsideFixAfterManualStartNeverClosesTrip() {
        val gate = BakeryTripGate()
        repeat(5) { assertEquals(BakeryTripAction.NONE, gate.observe(inside = true, tripActive = true)) }
    }

    @Test fun restartInsideActiveTripNeverClosesTrip() {
        val gate = BakeryTripGate()
        assertEquals(BakeryTripAction.NONE, gate.observe(true, true))
        gate.reset()
        assertEquals(BakeryTripAction.NONE, gate.observe(true, true))
        assertEquals(BakeryTripAction.NONE, gate.observe(true, true))
    }

    @Test fun oneOutsideGpsSpikeIsNotProofOfDepartureAndReturn() {
        val gate = BakeryTripGate()
        gate.observe(true, true)
        assertEquals(BakeryTripAction.NONE, gate.observe(false, true))
        assertEquals(BakeryTripAction.NONE, gate.observe(true, true))
        assertEquals(BakeryTripAction.NONE, gate.observe(true, true))
    }

    @Test fun confirmedOutsideThenTwoInsideSamplesClosesExactlyOnce() {
        val gate = BakeryTripGate()
        assertEquals(BakeryTripAction.NONE, gate.observe(true, true))
        assertEquals(BakeryTripAction.NONE, gate.observe(false, true))
        assertEquals(BakeryTripAction.NONE, gate.observe(false, true))
        assertEquals(BakeryTripAction.NONE, gate.observe(true, true))
        assertEquals(BakeryTripAction.CLOSE, gate.observe(true, true))
        assertEquals(BakeryTripAction.NONE, gate.observe(true, true))
    }

    @Test fun leavingBakeryWithoutTripStartsOnlyOnRealBoundaryCrossing() {
        val gate = BakeryTripGate()
        assertEquals(BakeryTripAction.NONE, gate.observe(true, false))
        assertEquals(BakeryTripAction.START, gate.observe(false, false))
        assertEquals(BakeryTripAction.NONE, gate.observe(false, false))
    }

    @Test fun unknownInitialOutsideStateDoesNotStartTrip() {
        val gate = BakeryTripGate()
        assertEquals(BakeryTripAction.NONE, gate.observe(false, false))
        assertEquals(BakeryTripAction.NONE, gate.observe(false, false))
    }

    @Test fun newTripRequiresFreshOutsideEvidence() {
        val gate = BakeryTripGate()
        gate.observe(false, true)
        gate.observe(false, true)
        gate.observe(true, true)
        assertEquals(BakeryTripAction.CLOSE, gate.observe(true, true))
        gate.observe(true, false)
        assertEquals(BakeryTripAction.NONE, gate.observe(true, true))
        assertEquals(BakeryTripAction.NONE, gate.observe(true, true))
    }
}
