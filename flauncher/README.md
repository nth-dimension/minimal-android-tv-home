# FLauncher for a generic Android TV box

Some TVs can't run Google's launcher or Minimal Home's takeover cleanly. One example is a white-label Android TV 14 set (Hikeen/HiSilicon Hi3751V553, product `SmartTV2560F_DEV`, 32-bit ARM, 1.5 GB RAM) that ships the ad-heavy Dolphin OS launcher. On that TV this folder installs [FLauncher](https://gitlab.com/etienn01/flauncher), an open-source, ad-free Android TV launcher, built from source with a few changes.

Google's own launcher isn't an option there. The copies in the Android SDK's TV images are arm64-only, the TV is 32-bit, and the launcher expects to be a privileged system app, which would mean writing to `/system`.

## What the patch changes

`flauncher.patch` applies to upstream commit `4930cd7` (FLauncher 0.18):

- **No Firebase.** Analytics, Crashlytics and Remote Config are removed, along with their settings switches.
- **Unsplash wallpapers** use an access key given at build time. Upstream fetched it from Firebase Remote Config. Without a key, the Unsplash option is hidden. `unsplash_client` is upgraded to 4.0.0, because 2.1 crashes on the current Unsplash API. The app gains the `INTERNET` permission for this.
- **The Menu key on the remote opens an app's menu** (open, reorder, hide, remove, app info). Upstream only offers this on a long-press. adb can't produce that long-press, so this is how a script hides tiles.
- **The app card highlight animation is off by default.** It keeps a cheap TV's CPU and GPU busy while the home screen sits idle; on this TV it used about 100% of the CPU against about 5% without it. It can still be turned on in Settings.
- **The package name is a build option** (`FLAUNCHER_APPLICATION_ID`), for the firmware described below.
- **The release build uses upstream's signing key when present,** and otherwise this machine's debug key, which is fine for sideloading.
- **The unit tests** are updated to match, with new ones for the changes above.

## Why the package is `com.zeasn.launcher`

Hikeen modified Android itself on this TV: `com.android.server.wm.HikeenPropertyInject`, inside `/system/framework/services.jar`, runs every time an app comes to the front.

- **Opening YouTube, JioHotstar, Amazon Video or the TV's own player:** it disables the Play Store and Play services, to save RAM.
- **When a launcher from its built-in list comes back:** it enables them again.

That list has Dolphin and a few other vendors' launchers, but not FLauncher. As `me.efesser.flauncher`, the Play Store stayed off after the first YouTube session.

`build.sh` therefore builds FLauncher as `com.zeasn.launcher`. That name is on the firmware's list and isn't installed on this TV. The firmware then treats FLauncher exactly like its stock launcher, and nothing in the system is changed. `--app-id me.efesser.flauncher` builds upstream's name for other TVs.

## Why Dolphin is disabled

At boot, the user's storage is still locked for a moment, and Android only starts a Home app that can run before it unlocks. Dolphin is marked to run then; FLauncher, a Flutter app, isn't. So every boot landed on Dolphin until someone pressed Home.

With Dolphin disabled, Android's own placeholder (`FallbackHome` in TV Settings) covers that moment. It then hands over to the Home app, which is FLauncher. If the TV ever sits on a spinning "loading" screen after boot, re-enable Dolphin with `adb shell pm enable com.dolphinos.tv.launcher`.

## Build

```
flauncher/build.sh --fetch-toolchain      # downloads Flutter 3.7.5, JDK 17 and Gradle 7.4 into build/flauncher/
```

The build needs these pinned old tools: Flutter 3.7.5, JDK 17 (Gradle 7.4 doesn't run on newer JDKs) and Android SDK platform 33. The script checks each download's SHA-256 against its publisher's. If you have them already, set `FLUTTER_ROOT` and `JAVA_HOME` instead of `--fetch-toolchain`.

The script clones upstream at the pinned commit, applies the patch, and runs `flutter analyze` and `flutter test`. It then builds `build/flauncher/flauncher-arm32-com.zeasn.launcher.apk` for 32-bit ARM. The Unsplash key comes from `unsplash.accessKey` in `local.properties`, so the APK contains it: keep the APK out of git (`build/` is ignored). `--no-unsplash` builds without a key.

## Install

```
adb connect 192.168.0.103:5555
adb install -r build/flauncher/flauncher-arm32-com.zeasn.launcher.apk
adb shell cmd package set-home-activity com.zeasn.launcher/me.efesser.flauncher.MainActivity
adb shell pm disable-user --user 0 com.dolphinos.tv.launcher
```

To go back to Dolphin:

```
adb shell pm enable com.dolphinos.tv.launcher
adb shell cmd package set-home-activity com.dolphinos.tv.launcher/android.dolphinos.tv.launcher.ui.main.activity.MainPageActivity
```

On that TV, FLauncher's settings were: wallpaper from Unsplash (Landscape); tiles hidden for System Tracing, DMPDemo, HiTVPlayer, Android TV Remote Service, FLauncher itself and Dolphin; highlight animation off. FLauncher keeps showing apps that get disabled, so hide their tiles too (a disabled app's tile does nothing).

An APK signed with a different key can't be installed over this one. You would have to uninstall FLauncher first, which loses its settings.

## Check the TV

```
flauncher/check-tv.sh                      # read-only
flauncher/check-tv.sh --launch             # also opens apps, the Menu panel and tiles
flauncher/check-tv.sh --reboot --launch    # reboots first; turn Wireless debugging back on when asked
```

It checks the following:
- FLauncher is the Home app.
- Every package in `debloat/generic-androidtv14-hikeen-applied.txt` is still off.
- The Play Store, Play services, YouTube, JioHotstar, Settings and the phone remote are still on.
- Nothing is crash-looping, and memory, idle CPU, internet and DNS look normal.

With `--launch`, it also opens YouTube and JioHotstar and checks that the Play Store comes back on at Home. It exits non-zero if anything fails.

## Notes on that TV

- **Use adb on port 5555.** Turning on Wireless debugging (Settings → System → Developer options) also opens adb on 5555, which keeps working through Wi-Fi drops; the Wireless debugging port itself changes and switches off on every Wi-Fi blip. After a reboot adb is off entirely until Wireless debugging is turned on again.
- **While it's on, adb is open to the whole home network.** The firmware is a `userdebug` build without `ro.adb.secure`, so any device on the Wi-Fi can likely get a shell, and a `su` binary is present. Turn Wireless debugging off when done; a reboot also closes it.
- **The remote's Power key** suspends the TV to RAM and turns it back on in a few seconds, straight into FLauncher; adb (`KEYCODE_SLEEP`) doesn't put it to sleep, but `KEYCODE_POWER` does.
- **Miracast is disabled.** Opening it switched the TV's Wi-Fi to Wi-Fi Direct, which dropped it off the network until someone pressed Back.
- **The same firmware code kills the previous app** when YouTube and similar apps open. FLauncher therefore restarts (about a second) when you come back from them.
- **Persistent system apps keep running.** `com.dolphinos.advertisement` and `com.factory.factoryautomation` start even while disabled, but the ad overlay stopped after the reboot that followed disabling them.
- **`com.adups.fota` is removed for user 0, not just disabled.** Merely disabled, it crash-looped on network changes. `adb shell cmd package install-existing com.adups.fota` brings it back.

FLauncher is GPL-3.0, and the patch is a derivative of it under the same licence.
