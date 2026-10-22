package dev.minimal.tvhome.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTimerTest {
    @Test fun fullMinutesShowExactly() = assertEquals(10, SleepTimer.minutesLeft(600_000, 0))
    @Test fun partMinutesRoundUp() = assertEquals(10, SleepTimer.minutesLeft(599_000, 0))
    @Test fun lastSecondsShowOneMinute() = assertEquals(1, SleepTimer.minutesLeft(1_000, 0))
    @Test fun endedShowsZero() = assertEquals(0, SleepTimer.minutesLeft(0, 5_000))
    @Test fun choicesAreTheAskedOnes() = assertEquals(listOf(10, 15, 20, 30), SleepTimer.CHOICES)
}
