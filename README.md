# Minimal Home

An ad-free home screen for Google TV, with two layouts to choose from.

**Minimal** shows three rows and nothing else:

1. **Continue watching**: in-progress shows and movies that your apps publish to the system, plus what you played in apps that don't (see [Apps that don't publish](#apps-that-dont-publish)). They're shown as a deck of big cards: the selected show in front, the next ones stacked behind it. Left and right move through the deck.
2. **Recent**: apps ordered by when you last opened them.
3. **Favorites**: apps you pin, followed by an **All apps** tile.

**Top Shelf** is modeled on the Apple TV home screen:

- A large shelf across the top shows what you're watching, over a blurred backdrop of the focused poster.
- Below it, all your apps sit in a five-column grid. Pinned favorites come first, so they form the top row.
- The shelf follows the app focused in the top row. It shows that app's in-progress items if it has any, and Up Next from all apps otherwise.

Switch between them under **Options**, at the top right of either layout or in any app's long-press menu.

Either layout can show a **photo background from Unsplash** instead of the solid dark one. See [Unsplash backgrounds](#unsplash-backgrounds).

No ads, no recommendations, no analytics. Network use is limited to poster images that your apps put in their Continue watching entries and, if you turn it on, Unsplash photos.

## Setup

You need a computer with this repo and the TV on the same network.

1. **Enable developer mode on the TV.** Settings → System → About → click *Android TV OS build* seven times.
2. **Enable debugging.** Settings → System → Developer options → turn on *USB debugging*, or *Wireless debugging* on newer builds. On Android 14 with Ethernet plugged in, wireless debugging may refuse to stay on; unplug Ethernet while setting up.
3. **Connect.** Find the TV's IP under Settings → Network, then run the command below and accept the prompt on the TV, ticking *Always allow*.
   ```
   adb connect <tv-ip>:5555
   ```
   With wireless debugging, use the pairing code and port shown on the TV: `adb pair <ip>:<port>` first, then `adb connect`.
4. **Install and take over Home.**
   ```
   scripts/install.sh
   ```
   This builds the app, installs it, grants its permissions (TV listings, usage access, and playback access), makes it able to turn the TV off for the sleep timer, sets its Unsplash screensaver (`--keep-screensaver` leaves yours alone), and disables the stock Google TV launcher so Home opens Minimal Home. It starts Minimal Home before disabling anything, checks afterwards that Home really opens it, and puts the stock launcher back if not. If the TV listings grant fails, the app asks for it once on screen and otherwise shows a hint row.

   On macOS, if `adb connect` says *No route to host* while the TV is on, macOS's Local Network privacy is blocking adb. The scripts restart the adb server and retry; if that doesn't help, allow your terminal under System Settings → Privacy & Security → Local Network.

`adb` is found on your PATH or at `~/Library/Android/sdk/platform-tools/adb`. Set `ADB=` to override. The build uses Android Studio's bundled Java if no other is installed.

## Undo

```
scripts/restore.sh --reboot
```

This re-enables the stock launcher, makes it the Home app again, puts the TV's previous screensaver back, and reboots. Add `--uninstall` to remove Minimal Home too; the script refuses to uninstall unless a stock launcher is enabled, so the TV is never left without a home screen. Nothing is uninstalled from the TV by the install script, only disabled, so restore is always possible.

## Removing the TV's own bloat (optional)

Minimal Home replaces the home screen, but the TV still runs Sony's and Google's tracking, promo and demo services in the background. `debloat/sony-bravia-vh21.txt` lists the ones on a BRAVIA VH21 in numbered batches, each line commented. Everything is disabled, never uninstalled, and the script saves the TV's package lists to `backups/` before changing anything.

```
scripts/debloat.sh debloat/sony-bravia-vh21.txt --dry-run     # see what would change
scripts/debloat.sh debloat/sony-bravia-vh21.txt --batch 1     # one batch at a time
scripts/debloat.sh debloat/sony-bravia-vh21.txt --undo        # re-enable everything in the list
```

Apply a batch, then check Home, Settings, an HDMI input and a streaming app before the next. Comment out any line for an app you use. The script remembers what it disabled (`backups/changed-<list>.txt`), and `--undo` only re-enables those packages, never ones the TV shipped with disabled. The list keeps everything the TV needs (Live TV and inputs, HDMI-CEC, firmware updates), plus Chromecast, voice search and the phone remote. It is adapted from the Sony lists in [cleanyourtv](https://github.com/Gathin23/cleanyourtv) (MIT).

## Using it

- **Home** or **Back** jumps back to the top of the screen and focuses the first Continue watching card (the first Up Next card in Top Shelf). In Minimal, **Down** from the card deck always goes to the first app of the row below it.
- **Long-press** any app to pin it, unpin it, move it left or right in Favorites, open its App info, or open Home options.
- **Search** at the top right finds a title or a video. Type with the on-screen keyboard or press **Voice**. As you type, matching shows from Continue watching and matching apps appear. Under *Search for "…" in*, pick an app and it opens its own search with your words filled in, or pick *All of Google TV* to search every app at once. Apps you used most recently come first.
  - Tried on the BRAVIA: **YouTube** and **Prime Video** open straight on results for your words (Prime after its profile picker). **JioHotstar** opens its search page, but you type the words again there. **Netflix** and **Apple TV** take the search link, but they block screenshots, so it's unconfirmed whether they show results or just open. *All of Google TV* shows results from every app.
  - Other apps get a tile only if they accept Android's standard search request. Xstream Play doesn't, so it has no tile.
- **Timer** at the top right turns the TV off after 10, 15, 20 or 30 minutes. Pick a time, then open any app; the timer keeps running behind it. While it runs, the button reads *Off in 9 min*. A minute before the end a message says the TV is about to turn off. At the end, whatever is playing is paused, so the app keeps its place, and the TV goes into standby. Open **Timer** again to pick a new time or cancel. If the TV is turned off before the timer ends, the timer just clears.
- **Options** at the top right picks the layout and opens Diagnostics, which shows what the launcher can see on your TV.
- **Settings** opens system settings. In Minimal it's a button at the top right. In Top Shelf it's the Settings tile in the app grid.

## Unsplash backgrounds

Unsplash photos need a free access key, because Unsplash only serves photos to apps through its API.

1. Sign in at [unsplash.com/developers](https://unsplash.com/developers), create an app, and copy its **Access Key**. A new app is in Demo mode, which allows 50 requests an hour. That is plenty here, since the TV asks for one photo per hour at most.
2. Install with the key:
   ```
   scripts/install.sh --unsplash-key YOUR_ACCESS_KEY
   ```
   The key is saved to `local.properties`, which git ignores, and built into the app. Don't share the APK.
3. On the TV, open **Options**, then **Background**, and pick **Unsplash photos**. Pick one or more topics (each new photo comes from one of them, chosen at random) and how often the photo changes. **New photo now** fetches one right away.

How it behaves:

- The current photo is saved on the TV, so it appears instantly after a reboot and stays up when offline.
- If a download fails, the previous photo stays and Options shows why.
- A dark overlay keeps text readable. In Top Shelf the photo replaces the blurred poster behind the shelf.
- The photographer is credited in the bottom-right corner, as Unsplash's API guidelines require. Each photo used is also reported to Unsplash, which the guidelines require too.

## Screensaver

Minimal Home includes a screensaver of Unsplash photos, in the topic picked in Options. It uses the same Unsplash key as the background.

- The photo changes every 5 minutes, crossfading in and drifting slowly, with a clock and the photographer's credit.
- A new photo is downloaded every 15 minutes; the changes in between cycle through the last 8, which stay on the TV so it starts instantly and keeps going offline. That keeps an evening of screensaver well inside Unsplash's 50 requests an hour.
- It only starts when the TV sits idle. Video apps keep the screen awake, so it never interrupts playback. Any button wakes the TV.
- **Options → Preview screensaver** shows it straight away.

`scripts/install.sh` makes it the TV's screensaver and remembers the previous one in `backups/`; `scripts/restore.sh` puts that one back. Google TV's own settings may not list third-party screensavers, so the script sets it over adb. A system update can reset it; run the install script again. The TV's idle time before the screensaver starts stays in Settings → System → Energy saver (or Ambient mode, depending on the TV).

## Check what Continue watching can see

Coverage depends on each app. Some publish their progress to the system, some don't.

1. Watch a few minutes of something in each app you care about.
2. Open Minimal Home, then **Options**, then **Diagnostics**.
3. The screen lists every Continue watching entry by app. The same lines go to logcat:
   ```
   adb logcat -s MinimalHome
   ```

An app missing from that list does not publish its progress. Minimal Home covers those apps itself, as below.

### Apps that don't publish

Some apps, such as JioHotstar and YouTube, never publish Continue watching rows. For those, Minimal Home notes what they play while they play it: title, artwork and how far you got. It does this through Android's media sessions, which the TV's own Now Playing controls use too. It needs notification-listener access to see them, which `scripts/install.sh` grants. It does not read notifications.

- Only apps with no Continue watching rows of their own are added, so Netflix and Prime Video keep their own lists.
- Music (anything with an album), clips under a minute, live streams, and videos past 95% are skipped.
- Progress is saved when playback pauses, stops or changes title, and every two minutes while it plays.
- Entries older than 30 days drop off.
- These entries have no deep link, so opening one opens the app, not the episode.
- Diagnostics lists everything recorded under *Recorded playbacks*.
- Apple TV gives its media session no title, so nothing is recorded for it. It doesn't need to be: once you've watched something, Apple TV publishes its own Continue watching rows, with deep links.

## Known risks

- **Casting.** One report says casting to a Chromecast with Google TV stopped working after a reboot with the stock launcher disabled. On the BRAVIA VH21 used here, casting worked with it disabled. If it fails on yours, run `scripts/restore.sh --reboot`.
- **Wi-Fi and the setup wizard.** On Google TV, a Wi-Fi network saved during the TV's first setup belongs to the setup wizard (`setupwraith`), and Android deletes it when the wizard is disabled, leaving the TV offline. The install and debloat scripts check `dumpsys wifi` and skip the wizard in that case; forget and rejoin the network in Settings, then run the script again. On the BRAVIA used here the network belongs to the system, so this didn't apply.
- **Factory reset.** Re-enable the setup wizard first (`scripts/restore.sh`), since a reset needs it.
- **Network debugging is a door into the TV.** It's plaintext on port 5555. When you're done changing things, turn off *USB debugging* and *Wireless debugging* in Developer options and revoke the authorisations. Minimal Home keeps working; turn it back on to update.
- **System updates** can re-enable the stock launcher. Run `scripts/install.sh --skip-build` again.
- **Sleep timer and device admin.** To put the TV into standby, Minimal Home is a device admin with one policy, *lock screen*; `scripts/install.sh` turns this on. Android won't uninstall an active device admin, so use `scripts/restore.sh --uninstall`, which removes the admin first. By hand:
  ```
  adb shell am broadcast -a dev.minimal.tvhome.REMOVE_ADMIN -n dev.minimal.tvhome/.data.SleepTimerCommands
  ```
- **Voice search** and the Assistant button are separate apps and keep working. The stock launcher's own search screen is gone.

## Ads inside apps

Minimal Home can't remove ads inside streaming apps. Android's Private DNS can block many of them system-wide by pointing the TV at an ad-blocking resolver such as AdGuard DNS. That resolver then sees every domain the TV looks up, it doesn't stop YouTube's ads, and if it goes down apps report no internet. Not set up by these scripts; to try it:

```
adb shell settings put global private_dns_specifier dns.adguard-dns.com
adb shell settings put global private_dns_mode hostname
adb shell settings put global private_dns_mode opportunistic   # to switch back
```

## Development

```
./gradlew :app:testDebugUnitTest   # unit tests for ranking, favorites, wallpaper and timer rules
./gradlew :app:assembleDebug       # debug APK
./gradlew :app:lintDebug           # Android lint
```

Posters and Unsplash photos go through Android's HTTP disk cache (48 MB under the app's cache folder), so a restart doesn't re-download them. All bitmaps are decoded through `data/Images.kt`, scaled to what's shown and in 16-bit colour for photos. App banners and icons are scaled once, off the main thread, when the app list loads; the list itself only reloads when a package is installed, removed or updated.

Code layout:

- `app/src/main/java/dev/minimal/tvhome/data/`: app list, Continue watching, recent apps, preferences, launching. Sorting rules live in `Ranking.kt`.
- `app/src/main/java/dev/minimal/tvhome/ui/`: Compose for TV screens. `HomeScreen.kt` is Minimal, `ShelfHomeScreen.kt` is Top Shelf.
- `scripts/`: install and restore over ADB.

To try the sleep timer without waiting, set one in seconds over adb (adb only; apps can't send this):

```
adb shell am broadcast -a dev.minimal.tvhome.SLEEP_TIMER_SET --el seconds 90 -n dev.minimal.tvhome/.data.SleepTimerCommands
```

The release build is signed with your local debug key, which is fine for sideloading onto your own TV.

## WhatsApp TV (separate app, experimental)

`tvchat/` is a second app, *WhatsApp TV*: WhatsApp Web in a full-screen WebView, for chats and calls on the TV with a USB camera such as a Logitech Brio. WhatsApp Web supports voice and video calls in the browser since mid-2026. The app tells WhatsApp it is desktop Chrome, since WhatsApp Web refuses other browsers. That is unofficial, and WhatsApp could stop accepting it.

```
./gradlew :tvchat:assembleRelease
adb install -r -g tvchat/build/outputs/apk/release/tvchat-release.apk   # -g grants camera and mic
```

Open it from the app list and scan the QR code with your phone (WhatsApp → Linked devices → Link a device). The remote drives an on-screen pointer:

- **Arrows** move the pointer, faster while held. At the top or bottom edge they scroll what's under the pointer.
- **OK** clicks. **Channel up/down** scroll a page at a time.
- **Back** closes whatever is open in WhatsApp; press it twice to leave the app.

Typing with the remote works through the on-screen keyboard but is slow; a USB or Bluetooth keyboard helps. The app keeps the screen on while it's open, and it only loads WhatsApp's own pages.
