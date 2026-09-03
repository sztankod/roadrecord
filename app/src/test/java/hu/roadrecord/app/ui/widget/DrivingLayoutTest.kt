package hu.roadrecord.app.ui.widget

import org.junit.Assert.*
import org.junit.Test

class DrivingLayoutTest {
    @Test fun mainBandIsTwoThirdsOfPreviousHeightAtEveryWidth() {
        for (width in listOf(280f, 320f, 336f, 400f, 600f, 840f)) {
            val oldHeight = 42f + minOf(width * 145f / 400f, 160f)
            assertEquals(oldHeight * 2f / 3f, DrivingLayout.mainBandHeight(width), .001f)
        }
    }
    @Test fun highRoofVanFitsWithoutChangingAspectRatio() {
        val vanHeight = DrivingLayout.VAN_WIDTH / (1556f / 564f)
        assertTrue(vanHeight < DrivingLayout.VAN_BASELINE)
        assertTrue(DrivingLayout.VAN_BASELINE < DrivingLayout.SCENE_HEIGHT)
    }
}
