package hu.roadrecord.app.photo

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoOptimizerTest {
    @Test fun usesPowerOfTwoSamplingWithoutDroppingBelowTargetPrematurely(){
        assertEquals(1,PhotoOptimizer.sampleSize(1600,900,1600))
        assertEquals(2,PhotoOptimizer.sampleSize(4000,3000,1600))
        assertEquals(4,PhotoOptimizer.sampleSize(8000,6000,1600))
    }
}
