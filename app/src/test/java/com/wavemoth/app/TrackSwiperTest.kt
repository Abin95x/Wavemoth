package com.wavemoth.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wavemoth.app.FakePlayer.Companion.song
import com.wavemoth.app.ui.components.TrackSwiper
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.unit.dp

/** Swiping the song carousel: past the threshold it changes song, otherwise it springs back. */
@RunWith(AndroidJUnit4::class)
class TrackSwiperTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun swipesChangeSongAndEdgesSpringBack() {
        val songs = (1..3).map { song(it, title = "Track $it") }
        var index = 0
        var nexts = 0
        var previouses = 0
        rule.setContent {
            var i by remember { mutableIntStateOf(0) }
            TrackSwiper(
                current = songs[i],
                previous = songs.getOrNull(i - 1),
                next = songs.getOrNull(i + 1),
                onNext = { nexts++; i++; index = i },
                onPrevious = { previouses++; i--; index = i },
                modifier = Modifier.fillMaxWidth().height(64.dp).testTag("swiper"),
            ) { Text(it.title) }
        }
        val swiper = rule.onNodeWithTag("swiper")
        swiper.performTouchInput { swipeLeft() }; rule.waitForIdle()
        assertEquals(1, nexts); assertEquals(1, index)
        rule.onNodeWithText("Track 2").assertExists()

        swiper.performTouchInput { swipeRight() }; rule.waitForIdle()
        assertEquals(1, previouses); assertEquals(0, index)

        // Nothing before the first song: springs back.
        swiper.performTouchInput { swipeRight() }; rule.waitForIdle()
        assertEquals(1, previouses)
        rule.onNodeWithText("Track 1").assertExists()

        // A short, slow drag isn't enough.
        swiper.performTouchInput { swipeLeft(startX = centerX, endX = centerX - 40f, durationMillis = 1_000) }; rule.waitForIdle()
        assertEquals(1, nexts)
    }
}
