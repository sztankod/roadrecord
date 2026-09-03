package hu.roadrecord.app.display

import hu.roadrecord.app.data.AppSettings
import org.junit.Assert.*
import org.junit.Test

class ScreenAwakeOptionsTest {
    private val options = ScreenAwakeOptions(enabled = true, limitMinutes = 60,
        dimEnabled = true, dimAfterMinutes = 2, dimPercent = 30)

    @Test fun disabledPolicyDoesNotScheduleOrDim() {
        assertEquals(ScreenAwakeDecision(false, false, null), options.copy(enabled = false).decision(0))
    }
    @Test fun firstDeadlineIsDimming() {
        assertEquals(ScreenAwakeDecision(true, false, 120_000), options.decision(0))
    }
    @Test fun dimmingAtDeadlineSchedulesOnlyExpiry() {
        assertEquals(ScreenAwakeDecision(true, true, 3_480_000), options.decision(120_000))
    }
    @Test fun expiryRestoresSystemBehaviorIncludingBrightness() {
        assertEquals(ScreenAwakeDecision(false, false, null), options.decision(3_600_000))
    }
    @Test fun touchRestartsThePolicyAndRestoresNormalBrightness() {
        assertTrue(options.decision(240_000).dimmed)
        assertEquals(ScreenAwakeDecision(true, false, 120_000), options.decision(0))
    }
    @Test fun unlimitedModeDoesNotPollAfterDimming() {
        assertEquals(ScreenAwakeDecision(true, true, null), options.copy(limitMinutes = 0).decision(240_000))
    }
    @Test fun unlimitedWithoutDimmingDoesNotScheduleAnyWork() {
        assertEquals(ScreenAwakeDecision(true, false, null), options.copy(limitMinutes = 0, dimEnabled = false).decision(0))
    }
    @Test fun invalidDimmingAfterExpiryCannotKeepDisplayAwake() {
        val value = options.copy(limitMinutes = 1, dimAfterMinutes = 2)
        assertEquals(ScreenAwakeDecision(true, false, 60_000), value.decision(0))
        assertEquals(ScreenAwakeDecision(false, false, null), value.decision(60_000))
    }
    @Test fun persistedValuesAreBoundedAndRoundTrip() {
        val normalized = options.copy(limitMinutes = 999, dimAfterMinutes = 0, dimPercent = 0).normalized()
        assertEquals(720, normalized.limitMinutes)
        assertEquals(1, normalized.dimAfterMinutes)
        assertEquals(5, normalized.dimPercent)
        assertEquals(options, ScreenAwakeOptions.from(options.applyTo(AppSettings())))
    }
    @Test fun savingDisplayOptionsPreservesOtherSettings() {
        val before = AppSettings(hourlyRate = 5678, currentPlaceId = 12, wazeOverlayEnabled = false)
        val after = options.applyTo(before)
        assertEquals(before.hourlyRate, after.hourlyRate)
        assertEquals(before.currentPlaceId, after.currentPlaceId)
        assertEquals(before.wazeOverlayEnabled, after.wazeOverlayEnabled)
    }
}
