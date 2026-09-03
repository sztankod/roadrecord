package hu.roadrecord.app.display

import hu.roadrecord.app.data.AppSettings

/** Pure policy: elapsed time is measured from the last real user interaction, not from GPS updates. */
internal data class ScreenAwakeOptions(
    val enabled: Boolean = false,
    val limitMinutes: Int = 60,
    val dimEnabled: Boolean = false,
    val dimAfterMinutes: Int = 2,
    val dimPercent: Int = 30,
) {
    fun normalized() = copy(limitMinutes = limitMinutes.coerceIn(0, 720),
        dimAfterMinutes = dimAfterMinutes.coerceIn(1, 120), dimPercent = dimPercent.coerceIn(5, 80))

    fun decision(elapsedMillis: Long): ScreenAwakeDecision {
        val safe = normalized()
        if (!safe.enabled) return ScreenAwakeDecision(false, false, null)
        val elapsed = elapsedMillis.coerceAtLeast(0)
        val expires = if (safe.limitMinutes == 0) Long.MAX_VALUE else safe.limitMinutes * 60_000L
        if (elapsed >= expires) return ScreenAwakeDecision(false, false, null)
        val dimAt = safe.dimAfterMinutes * 60_000L
        val dimmed = safe.dimEnabled && dimAt < expires && elapsed >= dimAt
        val next = if (safe.dimEnabled && !dimmed && dimAt < expires) dimAt else expires
        return ScreenAwakeDecision(true, dimmed, if (next == Long.MAX_VALUE) null else (next - elapsed).coerceAtLeast(1))
    }

    fun applyTo(settings: AppSettings): AppSettings = normalized().let {
        settings.copy(keepScreenOnEnabled = it.enabled, keepScreenOnLimitMinutes = it.limitMinutes,
            screenDimEnabled = it.dimEnabled, screenDimAfterMinutes = it.dimAfterMinutes, screenDimPercent = it.dimPercent)
    }

    companion object {
        fun from(settings: AppSettings) = ScreenAwakeOptions(settings.keepScreenOnEnabled,
            settings.keepScreenOnLimitMinutes, settings.screenDimEnabled, settings.screenDimAfterMinutes,
            settings.screenDimPercent).normalized()
    }
}

internal data class ScreenAwakeDecision(val keepAwake: Boolean, val dimmed: Boolean, val nextDelayMillis: Long?)
