package com.wavemoth.app

import androidx.compose.ui.geometry.Offset
import com.wavemoth.app.data.VisualizerStyle
import com.wavemoth.app.ui.home.VisualizerZone
import com.wavemoth.app.ui.home.continueRingDrag
import com.wavemoth.app.ui.home.movesAlongRing
import com.wavemoth.app.ui.home.ringFraction
import com.wavemoth.app.ui.home.visualizerZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualizerTest {

    @Test
    fun ringStartsAtTheTopAndGoesClockwise() {
        assertEquals(0f, ringFraction(0f, -10f), 0.001f)
        assertEquals(0.25f, ringFraction(10f, 0f), 0.001f)
        assertEquals(0.5f, ringFraction(0f, 10f), 0.001f)
        assertEquals(0.75f, ringFraction(-10f, 0f), 0.001f)
    }

    @Test
    fun draggingStopsAtTheTopInsteadOfJumpingAcross() {
        assertEquals(0.31f, continueRingDrag(0.3f, 0.31f), 0.001f)
        assertEquals(1f, continueRingDrag(0.98f, 0.02f), 0.001f)
        assertEquals(1f, continueRingDrag(1f, 0.05f), 0.001f)
        assertEquals(0f, continueRingDrag(0.02f, 0.98f), 0.001f)
        assertEquals(0f, continueRingDrag(0f, 0.95f), 0.001f)
    }

    @Test
    fun touchesLandOnTheRightPart() {
        assertEquals(VisualizerZone.Inside, visualizerZone(20f, 400f))
        assertEquals(VisualizerZone.Inside, visualizerZone(100f, 400f))
        assertEquals(VisualizerZone.Ring, visualizerZone(177f, 400f))
        assertEquals(VisualizerZone.Outside, visualizerZone(230f, 400f))
    }

    @Test
    fun onlyMovesAroundTheRingSeek() {
        // At the top of the ring, sideways follows it and up-and-down (scrolling) does not.
        assertTrue(movesAlongRing(Offset(0f, -100f), Offset(10f, 1f)))
        assertFalse(movesAlongRing(Offset(0f, -100f), Offset(1f, 10f)))
    }

    @Test
    fun unknownSavedStyleFallsBackToClassic() {
        assertEquals(VisualizerStyle.Classic, VisualizerStyle.fromName("nope"))
        assertEquals(VisualizerStyle.Classic, VisualizerStyle.fromName("Spiral"))  // a removed style
        assertEquals(VisualizerStyle.SoundWave, VisualizerStyle.fromName("SoundWave"))
    }
}
