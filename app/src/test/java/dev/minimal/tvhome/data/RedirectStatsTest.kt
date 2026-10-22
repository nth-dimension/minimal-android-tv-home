package dev.minimal.tvhome.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RedirectStatsTest {
    @Test fun nothingYet() = assertEquals("none yet", RedirectStats.describe(RedirectStats.Summary()) { "" })

    @Test fun countsAndTheLastLanding() = assertEquals(
        "14 started, 13 landed (last in 5.1 s, at 11:42 PM), 1 reopened after Amazon's home came back, 0 gave up",
        RedirectStats.describe(
            RedirectStats.Summary(started = 14, landed = 13, cameBack = 1, gaveUp = 0, lastLandedAt = 1, lastTookMs = 5_060),
        ) { "11:42 PM" },
    )

    @Test fun noLandingYet() = assertEquals(
        "1 started, 0 landed, 0 reopened after Amazon's home came back, 1 gave up",
        RedirectStats.describe(RedirectStats.Summary(started = 1, gaveUp = 1)) { "" },
    )
}
