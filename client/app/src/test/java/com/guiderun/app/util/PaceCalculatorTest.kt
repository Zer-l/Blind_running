package com.guiderun.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaceCalculatorTest {

    @Test
    fun `currentPace returns correct pace`() {
        // 1km in 300 seconds = 5'00" pace
        assertEquals(300, PaceCalculator.currentPace(1000, 300))
    }

    @Test
    fun `currentPace returns null for zero distance`() {
        assertNull(PaceCalculator.currentPace(0, 300))
    }

    @Test
    fun `currentPace returns null for zero duration`() {
        assertNull(PaceCalculator.currentPace(1000, 0))
    }

    @Test
    fun `avgPace returns correct pace`() {
        // 5km in 1500 seconds = 5'00" pace
        assertEquals(300, PaceCalculator.avgPace(5000, 1500))
    }

    @Test
    fun `avgPace returns null for zero distance`() {
        assertNull(PaceCalculator.avgPace(0, 1500))
    }

    @Test
    fun `slidingAverage with fewer than 5 samples`() {
        assertEquals(300, PaceCalculator.slidingAverage(listOf(290, 300, 310)))
    }

    @Test
    fun `slidingAverage with exactly 5 samples`() {
        assertEquals(300, PaceCalculator.slidingAverage(listOf(280, 290, 300, 310, 320)))
    }

    @Test
    fun `slidingAverage with more than 5 samples takes last 5`() {
        // Last 5: 300, 310, 320, 330, 340 → avg = 320
        assertEquals(320, PaceCalculator.slidingAverage(listOf(100, 200, 300, 310, 320, 330, 340)))
    }

    @Test
    fun `slidingAverage returns null for empty list`() {
        assertNull(PaceCalculator.slidingAverage(emptyList()))
    }

    @Test
    fun `formatPace formats correctly`() {
        assertEquals("5'30\"", PaceCalculator.formatPace(330))
        assertEquals("4'00\"", PaceCalculator.formatPace(240))
        assertEquals("10'15\"", PaceCalculator.formatPace(615))
    }
}
