package dev.minimal.tvhome.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRedirectRulesTest {
    private val launcher = HomeRedirectRules.AMAZON_LAUNCHER

    @Test fun fireOs8HomeScreenRedirects() =
        assertTrue(HomeRedirectRules.isAmazonHome(launcher, "com.amazon.tv.launcher.ui.HomeActivity_vNext"))

    @Test fun olderHomeScreenRedirects() =
        assertTrue(HomeRedirectRules.isAmazonHome(launcher, "com.amazon.tv.launcher.ui.HomeActivity"))

    @Test fun fireTvSettingsInsideTheLauncherAreLeftAlone() {
        assertFalse(HomeRedirectRules.isAmazonHome(launcher, "com.amazon.tv.launcher.ui.MainSettingsActivity"))
        assertFalse(HomeRedirectRules.isAmazonHome(launcher, "com.amazon.tv.launcher.ui.SettingsActivity"))
    }

    @Test fun otherAppsAreLeftAlone() {
        assertFalse(HomeRedirectRules.isAmazonHome("com.amazon.tv.settings.v2", "com.amazon.tv.settings.v2.hud.HudActivity"))
        assertFalse(HomeRedirectRules.isAmazonHome("dev.minimal.tvhome", "dev.minimal.tvhome.MainActivity"))
        assertFalse(HomeRedirectRules.isAmazonHome(null, null))
        assertFalse(HomeRedirectRules.isAmazonHome(launcher, null))
    }

    @Test fun aSecondEventRightAfterARedirectIsTheSamePress() {
        val home = "com.amazon.tv.launcher.ui.HomeActivity_vNext"
        assertTrue(HomeRedirectRules.shouldRedirect(launcher, home, now = 10_000, lastRedirectAt = 0))
        assertFalse(HomeRedirectRules.shouldRedirect(launcher, home, now = 10_400, lastRedirectAt = 10_000))
        assertTrue(HomeRedirectRules.shouldRedirect(launcher, home, now = 11_000, lastRedirectAt = 10_000))
    }

    @Test fun aRedirectEndsOnlyOnceMinimalHomeStaysUp() {
        val f = HomeRedirectRules.Front.MinimalHome
        assertEquals(HomeRedirectRules.Step.Wait, HomeRedirectRules.nextStep(f, now = 6_000, startedAt = 0, landedAt = 5_000))
        assertEquals(HomeRedirectRules.Step.Done, HomeRedirectRules.nextStep(f, now = 8_000, startedAt = 0, landedAt = 5_000))
    }

    @Test fun amazonHomeBackOnTopMeansLaunchAgain() =
        assertEquals(
            HomeRedirectRules.Step.Relaunch,
            HomeRedirectRules.nextStep(HomeRedirectRules.Front.AmazonHome, now = 7_000, startedAt = 0, landedAt = 5_000),
        )

    @Test fun anotherAppOrTooLongStops() {
        assertEquals(HomeRedirectRules.Step.Stop, HomeRedirectRules.nextStep(HomeRedirectRules.Front.OtherApp, 2_000, 0, 0))
        assertEquals(HomeRedirectRules.Step.Stop, HomeRedirectRules.nextStep(HomeRedirectRules.Front.AmazonHome, 15_000, 0, 0))
        assertEquals(HomeRedirectRules.Step.Wait, HomeRedirectRules.nextStep(HomeRedirectRules.Front.Unknown, 2_000, 0, 0))
    }

    @Test fun anotherActivityEndsTheRedirectButToastsDont() {
        assertTrue(HomeRedirectRules.leftAmazonHome("dev.minimal.tvhome", "dev.minimal.tvhome.MainActivity"))
        assertTrue(HomeRedirectRules.leftAmazonHome(launcher, "com.amazon.tv.launcher.ui.MainSettingsActivity"))
        assertFalse(HomeRedirectRules.leftAmazonHome(launcher, "com.amazon.tv.launcher.ui.HomeActivity_vNext"))
        assertFalse(HomeRedirectRules.leftAmazonHome("android", "android.widget.Toast\$TN"))
        assertFalse(HomeRedirectRules.leftAmazonHome("dev.minimal.tvhome", "android.view.View"))
    }

    @Test fun aKeyOnAmazonHomeRedirectsUnlessKnownElsewhereOrPending() {
        assertTrue(HomeRedirectRules.redirectOnFocus(launcher, amazonHomeInFront = true, pending = false))
        assertTrue(HomeRedirectRules.redirectOnFocus(launcher, amazonHomeInFront = null, pending = false))
        assertFalse(HomeRedirectRules.redirectOnFocus(launcher, amazonHomeInFront = false, pending = false))
        assertFalse(HomeRedirectRules.redirectOnFocus(launcher, amazonHomeInFront = true, pending = true))
        assertFalse(HomeRedirectRules.redirectOnFocus("com.netflix.ninja", amazonHomeInFront = null, pending = false))
    }
}
