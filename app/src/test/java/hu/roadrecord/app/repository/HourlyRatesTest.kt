package hu.roadrecord.app.repository

import hu.roadrecord.app.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HourlyRatesTest {
    @Test fun existingRateBecomesInitialHistoryEntry(){
        val settings=AppSettings(hourlyRate=4800)
        assertEquals(listOf(HourlyRateEntry("1970-01-01",4800)),settings.hourlyRateEntries())
    }

    @Test fun earningsUseTheRateEffectiveOnEachDate(){
        val settings=AppSettings(hourlyRate=4500).withHourlyRateEntries(listOf(
            HourlyRateEntry("1970-01-01",3600),
            HourlyRateEntry("2026-11-15",7200)
        ))
        val zone=ZoneId.systemDefault()
        val start=LocalDate.of(2026,11,14).atTime(23,30).atZone(zone).toInstant().toEpochMilli()
        val end=LocalDate.of(2026,11,15).atTime(0,30).atZone(zone).toInstant().toEpochMilli()
        assertEquals(5400,earningsForIntervals(listOf(start until end),settings))
    }
}
