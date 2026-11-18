package com.wavemoth.app

import androidx.compose.ui.geometry.Offset
import com.wavemoth.app.data.VisualizerProgress
import com.wavemoth.app.data.VisualizerStyle
import com.wavemoth.app.ui.home.SpectrumBars
import com.wavemoth.app.ui.home.VisualizerState
import com.wavemoth.app.ui.home.VisualizerZone
import com.wavemoth.app.ui.home.continueRingDrag
import com.wavemoth.app.ui.home.hueRotation
import com.wavemoth.app.ui.home.movesAlongBar
import com.wavemoth.app.ui.home.movesAlongRing
import com.wavemoth.app.ui.home.ringFraction
import com.wavemoth.app.ui.home.straightFraction
import com.wavemoth.app.ui.home.straightZone
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

    @Test
    fun eachStyleHasTheProgressShapeOfItsDesign() {
        assertEquals(VisualizerProgress.Round, VisualizerStyle.Classic.progress)
        assertEquals(VisualizerProgress.Round, VisualizerStyle.SoundWave.progress)
        for (style in listOf(VisualizerStyle.RoseGold, VisualizerStyle.LedMeter, VisualizerStyle.Waveform, VisualizerStyle.Prism)) {
            assertEquals(style.name, VisualizerProgress.Straight, style.progress)
        }
    }

    @Test
    fun straightBarSeeksAlongItsLength() {
        assertEquals(VisualizerZone.Inside, straightZone(100f, 300f))
        assertEquals(VisualizerZone.Ring, straightZone(280f, 300f))
        assertEquals(0f, straightFraction(0f, 400f), 0.001f)
        assertEquals(0.5f, straightFraction(200f, 400f), 0.001f)
        assertEquals(1f, straightFraction(400f, 400f), 0.001f)
        assertTrue(movesAlongBar(Offset(10f, 1f)))
        assertFalse(movesAlongBar(Offset(1f, 10f)))
    }

    @Test
    fun peaksRestOnTopThenFall() {
        val state = VisualizerState().apply { target.fill(0.8f) }
        val bars = SpectrumBars(4, launch = false, state)
        bars.step(1 / 60f, state)
        assertEquals(0.8f, bars.peaks[0], 0.001f)

        state.target.fill(0f)
        repeat(20) { bars.step(1 / 60f, state) }
        assertTrue(bars.levels[0] < 0.5f)
        assertEquals(0.8f, bars.peaks[0], 0.001f)  // still held
        repeat(120) { bars.step(1 / 60f, state) }
        assertTrue(bars.peaks[0] < 0.8f)
    }

    @Test
    fun launchedPeaksFlyAboveTheBarAndComeBack() {
        val state = VisualizerState().apply { target.fill(0.5f) }
        val bars = SpectrumBars(4, launch = true, state)
        bars.step(1 / 60f, state)
        repeat(10) { bars.step(1 / 60f, state) }
        assertTrue(bars.peaks[0] > 0.6f)

        state.target.fill(0f)
        repeat(600) { bars.step(1 / 60f, state) }
        assertEquals(0f, bars.levels[0], 0.001f)
        assertEquals(0f, bars.peaks[0], 0.001f)
    }

    @Test
    fun hueRotationByNothingLeavesColoursAlone() {
        val matrix = hueRotation(0f)
        for (row in 0 until 3) for (column in 0 until 3) {
            assertEquals(if (row == column) 1f else 0f, matrix[row * 5 + column], 0.01f)
        }
        assertEquals(1f, matrix[18], 0f)
    }
}
