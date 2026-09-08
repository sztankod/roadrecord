package hu.roadrecord.app.theme

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class SolarThemeTest {
    private val zone=ZoneId.of("Europe/Budapest")

    @Test fun `Budapest summer solar window is realistic`() {
        val window=SolarTheme.window(LocalDate.of(2026,6,21),47.4979,19.0402,zone)!!
        val rise=window.sunrise.atZone(zone).toLocalTime()
        val set=window.sunset.atZone(zone).toLocalTime()
        assertTrue(rise in LocalTime.of(4,0)..LocalTime.of(6,0))
        assertTrue(set in LocalTime.of(20,0)..LocalTime.of(22,0))
    }

    @Test fun `dark mode starts thirty minutes before sunset and ends at sunrise`() {
        val date=LocalDate.of(2026,9,7);val window=SolarTheme.window(date,47.87,19.00,zone)!!
        assertFalse(SolarTheme.isDark(window.sunset.minusSeconds(31*60),47.87,19.00,zone))
        assertTrue(SolarTheme.isDark(window.sunset.minusSeconds(29*60),47.87,19.00,zone))
        assertTrue(SolarTheme.isDark(window.sunrise.minusSeconds(1),47.87,19.00,zone))
        assertFalse(SolarTheme.isDark(window.sunrise.plusSeconds(1),47.87,19.00,zone))
    }
}
