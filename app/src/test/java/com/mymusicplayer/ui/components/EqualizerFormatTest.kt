package com.mymusicplayer.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * [formatDb] renders the stored millibel band values as decibels for the EQ sliders.
 * The old implementation used integer division, so a +400 mB band displayed as "4".
 */
class EqualizerFormatTest {

    @Test
    fun `renders whole decibels without decimals`() {
        assertEquals("+4", formatDb(400))
        assertEquals("\u22122", formatDb(-200))
        assertEquals("0", formatDb(0))
    }

    @Test
    fun `renders fractional decibels with one decimal`() {
        assertEquals("+2.5", formatDb(250))
        assertEquals("\u221215", formatDb(-1500))
        assertEquals("+15", formatDb(1500))
    }
}
