package dev.minimal.tvhome.data

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
}
