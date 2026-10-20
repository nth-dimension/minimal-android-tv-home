# Native WhatsApp Android app on Android TV / Google TV as a linked device for USB-webcam video calls

Scope note: the target is a Sony BRAVIA 4K VH21 (Google TV, Android 12, MediaTek, 3 GB RAM) with a Logitech Brio 300. Research date: 2026-10-20. "Accessed 2026-10" marks undated Help Center pages, which showed a © 2026 footer when fetched. Project facts are cited to the local project memory file (`/Users/nilay/.claude/projects/-Users-nilay-Claude-Code-Sessions-minimal-android-tv-home/memory/minimal-tv-home-project.md`, "project notes").

## 1. Can WhatsApp for Android be linked as a companion device to an existing account (2026), and does that work on Android TV or need phone/tablet hardware?

### Takeaway
Yes. Since 2023 the standard WhatsApp Android APK can link to an existing account as a "companion phone" or "Android tablet", using a QR code that the primary phone scans. Calls aren't on the list of unsupported linked-device features. TVs are not on WhatsApp's list of supported linked devices, though, and I found no official or dated first-hand report of the linking flow on an Android TV. The flow itself doesn't seem to need telephony, a camera or a touchscreen on the linked device (the TV only shows the QR code), but that's inferred, not confirmed.

### Cited Findings
- **How linking works (accessed 2026-10).** WhatsApp's Help Center says one primary phone can link up to four devices. The supported list is: Windows, Mac, Web, Android tablet, iPad, WearOS, Companion Phones, Meta AI glasses, VR. TVs are not listed. — [WhatsApp Help: About linked devices](https://faq.whatsapp.com/378279804439436/?cms_platform=android)
- **Where WhatsApp says to link (accessed 2026-10).** "Only link on WhatsApp Web, WhatsApp for Windows and Mac, WhatsApp Android Tablets, WhatsApp companion phones, or AI glasses." It also says: "Linking your device through unofficial apps or websites may put your account at risk." — [WhatsApp Help: About linked devices](https://faq.whatsapp.com/general/download-and-installation/about-linked-devices)
- **Logout timers (accessed 2026-10).** Linked devices log out if the primary phone isn't used for more than 14 days. WhatsApp also disconnects any linked device that is inactive for 30 days. — [WhatsApp Help: About linked devices](https://faq.whatsapp.com/general/download-and-installation/about-linked-devices)
- **Features missing on linked devices (accessed 2026-10).** Viewing live location, creating and viewing broadcast lists, and WhatsApp Flows. Voice and video calls are not on the list. — [WhatsApp Help: About linked devices (Android)](https://faq.whatsapp.com/378279804439436/?cms_platform=android)
- **Companion-phone steps (accessed 2026-10).**
  1. Install WhatsApp on the second Android device and tap "Agree and continue".
  2. Open More options (⋮) and choose "Link as companion device". This shows a QR code.
  3. On the primary phone, open Linked devices > Link a device (Android) or Settings > Linked devices > Link device (iPhone), then scan the code.
  - Live location isn't supported on companion phones.
  - Once linked, the second device shows "This is a linked device."
  — [WhatsApp Help: About linking WhatsApp to a second phone](https://faq.whatsapp.com/1046791737425017)
- **Android tablets (accessed 2026-10).** The app shows a linking QR code when opened. If it doesn't, More Options > Link a device brings it up. A tablet can also register its own account. A linked tablet syncs up to two years of chat history. The page warns: "Linked devices might not have all the features found on WhatsApp for your phone." — [WhatsApp Help: About WhatsApp for Android tablets](https://faq.whatsapp.com/670052398160812)
- **New tablet setup screen (2026-07-03).** WhatsApp is testing a "Choose an option" first-run screen on Android tablets: "Log into WhatsApp" links the tablet as a companion, and the other option registers it as a primary device. WABetaInfo says companion mode itself is unchanged. iPad got a similar choice in WhatsApp 26.25.74. — [WABetaInfo, 2026-07-03](https://wabetainfo.com/whatsapp-is-testing-a-new-setup-screen-for-android-tablets)
- **Timeline.**
  - Tablet companion mode reached beta testers in v2.22.21.5/.6 (2022-09). Android Police's hands-on found the phone UI "stretched out wide", and the QR screen appeared only after a restart. — [Android Police, 2022-09-23](https://www.androidpolice.com/whatsapp-multi-device-tablets-beta-roll-out)
  - Phone companion mode reached some beta testers in v2.22.24.18 (2022-11). — [WABetaInfo, 2022-11-13](https://wabetainfo.com/whatsapp-is-releasing-companion-mode-on-android-beta)
  - All beta testers got it in 2023-04 (v2.23.8.2). WABetaInfo wrote that "your personal messages and calls remain end-to-end encrypted even when using WhatsApp from a linked mobile phone." — [WABetaInfo, 2023-04-10](https://wabetainfo.com/whatsapp-is-releasing-the-companion-mode-for-all-beta-testers-on-android); [SamMobile, 2023-04-11](https://www.sammobile.com/news/more-users-can-now-use-whatsapps-companion-mode)
  - Gallabox describes companion mode as "launched in April 2023". This is a secondary source. — [Gallabox](https://gallabox.com/blog/how-to-use-whatsapp-on-two-phones)
  - Posting status updates from companion devices was in beta in 2.24.1.4 (2023-12). — [Android Police, 2023-12-21](https://www.androidpolice.com/linked-devices-whatsapp-beta-android-status-updates)
- **Call buttons on a non-phone Android device (2025-03).** A community member running WhatsApp's Android app on a Pixel Slate (ChromeOS) reported that both the voice and video call icons were there. They hadn't tested a call. — [Chromebook Community thread, 2025-02/03](https://support.google.com/chromebook/thread/324491475/can-i-use-whatsapp-video-calling-on-chromebook?hl=en)
- **Device requirements (accessed 2026-10).** Supported Android devices are "Android phones running Android 6.0 and newer" that "can receive SMS messages or phone calls". Separately, WhatsApp "supports a selection of devices that you can securely link to your phone, including iPad, Android tablets, and WearOS smart watches." — [WhatsApp Help: About supported devices](https://faq.whatsapp.com/2366980643469578/?cms_platform=android)
- **Current APK and OS floor.** The current APK is 2.26.40.76 (2026-10-17), needs Android 6.0+, and is about 93 MB. — [APKPure listing](https://apkpure.com/whatsapp-android/com.whatsapp). WhatsApp drops Android versions below 6.0 from 2026-09-08. — [WABetaInfo, 2026-04](https://wabetainfo.com/whatsapp-to-drop-support-for-older-android-versions-in-september-2026)
- **Why it isn't in the TV Play Store.** To install on conventional TVs, an app must declare `android.hardware.touchscreen` `required="false"` (plus the leanback declarations). — [Android Developers: Manage TV controllers](https://developer.android.com/training/tv/get-started/controllers)
- **Pre-2023 reports are obsolete.** A 2020 r/ShieldAndroidTV answer said that even with the APK you can't have two Android devices on one account. That stopped being true with companion mode (above). — [Reddit r/ShieldAndroidTV, 2020-07](https://www.reddit.com/r/ShieldAndroidTV/comments/hr3vkr/whatsapp_on_the_nvidia_shield)

### Inferences
- **Telephony.** The SMS/phone-call requirement applies to registering a primary account. A companion links by QR code, so the TV shouldn't need a SIM or telephony.
- **Camera for linking.** The TV doesn't need a camera to link: it displays the QR code and the phone scans it.
- **Phone mode or tablet mode.** Which mode the app picks on a 1080p TV depends on how it classifies the screen. Either way there's a documented path ("Link as companion device" in the phone build, or the tablet QR screen / "Link a device").
- **Slot count.** The native app would take one of the four linked-device slots. The existing tvchat WebView session (web.whatsapp.com) already takes another. Both are subject to the 14-day rule.
- **Calls should be available.** Calls are absent from the unsupported list, WABetaInfo describes calls on linked phones, and the call buttons appear on a ChromeOS large-screen device. So calls are very likely available on a linked TV, though no TV source confirms it.

### Gaps
- I found no dated, first-hand 2024–2026 report of linking the native WhatsApp app on an Android TV or Google TV device.
- Unknown whether WhatsApp on a TV-class device (no telephony, `android.software.leanback`, no touchscreen) defaults to phone or tablet mode, or blocks anything. To check, run `aapt dump badging` on the APK for its `uses-feature` list, then try it on the TV.
- Whether Google Play Services is needed for push/incoming calls on a linked device. Not checked; the Sony has GMS anyway. Fire TV has no GMS ([Android Police, 2020](https://www.androidpolice.com/2020/05/12/how-to-sideload-any-application-to-an-amazon-fire-tv)).

## 2. Have people run the WhatsApp APK on Android TV / Google TV / Chromecast / Shield / Fire TV and made USB-webcam video calls? What worked and what broke?

### Takeaway
Reliable reports are close to non-existent. I found no dated, first-hand 2024–2026 account of a successful native WhatsApp video call over a USB webcam on any Android TV device. What exists is either old and negative, or about similar platforms (ChromeOS, Waydroid, Android-x86), where results are mixed. The camera usually works in some apps and not in others, so support depends on the app as well as the TV.

### Cited Findings
- **Smart TV, 2021 (anecdotal).** A FlyerTalk user wrote that "Whatsapp, for instance, did not work on my smart TV. However, no issues with Google Duo, Skype, MS Teams and Zoom." No details were given, and the post predates companion mode. — [FlyerTalk, 2021-07](https://www.flyertalk.com/forum/travel-technology/2045986-whatsapp-zoom-video-calls-tv.html?styleid=22)
- **Chromecast with Google TV, 2021.** With a USB hub plus a webcam, Google Duo worked. For sideloaded apps the reviewer said "don't count on it". Webcams on Android TVs gave "mixed results", and Google TV-era TVs were "the most compatible". — [Digital Trends, 2021-04-15](https://www.digitaltrends.com/home-theater/how-to-video-calls-on-tv/)
- **ChromeOS, 2025-09/10 (closest recent report).** On a Chromebox and a Lenovo Flex 5 Chromebook, the Android apps WhatsApp, Telegram and Teams could use the USB webcams' microphones, but not their video. In WhatsApp the built-in Chromebook camera worked; switching to any of three Logitech USB cameras failed. ChromeOS's own Camera app saw every camera. ChromeOS runs Android apps through ARC, so this is only a partial proxy for Android TV. — [Chromebook Community, 2025-09-28, edited 2025-10-18](https://support.google.com/chromebook/thread/376667759/usb-camera-s-not-working-in-some-android-apps?hl=en)
- **Waydroid, 2026-07.** With the external camera HAL reporting 0 devices, "WhatsApp cannot make video calls" while voice calls, mic and speaker worked. So WhatsApp needs a camera that Android enumerates. — [waydroid/waydroid #2365, 2026-07-21](https://github.com/waydroid/waydroid/issues/2365)
- **Android-x86 VM (2023, positive but indirect).** A Linux user said their only way to take WhatsApp video calls on a PC was a QEMU VM running android-x86 with a dedicated low-bandwidth USB webcam (C270/C310) passed through. That implies WhatsApp's Android app does use a passed-through UVC camera on that HAL. — [waydroid/waydroid #1040, 2023-08](https://github.com/waydroid/waydroid/issues/1040)
- **Google Meet, 2023.** Meet on Google TV/Android TV said "No camera found" with a USB webcam that Skype used fine. — [Google Meet Community, 2023-11-23](https://support.google.com/meet/thread/245212177/does-google-meet-app-on-google-tv-android-tv-not-support-usb-webcams-thx?hl=en)
- **TV-native calling apps keep disappearing.**
  - r/AndroidTV users say Google deprecated Meet on Android TV in early 2025. — [Reddit r/AndroidTV, 2026-01](https://www.reddit.com/r/AndroidTV/comments/1qfbxjw/alternative_for_meet_on_android_tv)
  - Another user said TrueConf, Arattai and Jitsi Meet all failed to create or join rooms on their TV. — [Reddit r/AndroidTV, ~2026](https://www.reddit.com/r/AndroidTV/comments/1rtbl54/video_meeting_app_in_replacement_for_google_meet)
- **YouTube install guides.** "How to install WhatsApp on Smart TV" guides exist (Downloader app, TCL/Mi TV). The results don't show working video calls, and I couldn't date or check them. — [YouTube rgRQsNy9b9E](https://www.youtube.com/watch?v=rgRQsNy9b9E); [YouTube 093NWyBlbQw](https://www.youtube.com/watch?v=093NWyBlbQw); [YouTube UKExm1BJ_3s](https://www.youtube.com/watch?v=UKExm1BJ_3s)
- **Fire TV.** Fire TV lacks Google Play Services, runs old Android versions, and many apps are touch-only. — [Android Police, 2020](https://www.androidpolice.com/2020/05/12/how-to-sideload-any-application-to-an-amazon-fire-tv). Fire TV Cube (2nd gen) supports UVC webcams, but only for Alexa calls. — [AFTVnews, 2020-12-02](https://www.aftvnews.com/amazon-fire-tv-cube-can-now-do-two-way-video-calling-via-usb-webcam)
- **Unreliable source.** An Alibaba "buying guide" (2026-07) claims WhatsApp's camera handling "remains unstable across 80% of devices" and that WhatsApp "bypasses Android TV's camera permission model". It gives no evidence and reads like generated SEO content; don't use it. — [Alibaba guide](https://electronics.alibaba.com/buyingguides/android-tv-box-webcam-support-guide)

### Inferences
- **Prior odds.** It's genuinely uncertain whether native WhatsApp will use the Brio. The Sony's HAL working for Camera2 and Camera1 apps (project notes) is necessary but not enough. The ChromeOS failure shows WhatsApp can reject a USB camera even when the OS enumerates it. The android-x86 report shows it can work when the HAL presents the camera in a way WhatsApp accepts (see Q3).
- **Sony-specific risks still apply.** BRAVIA CAM services holding the camera were fixed by disabling them. The MTK V4L2 HAL wedging into "VIDIOC_S_PARM busy" after a reconfigure remains (project notes). WhatsApp opening and reconfiguring the camera at its own sizes could trigger the wedge.
- **Mic.** The Brio 300 exposes no USB audio interface on this TV (project notes), so the native app would also need a separate USB mic. Google's own TV calling guidance says remote mics can't be used for calls ([Android TV Help: Meet](https://support.google.com/androidtv/answer/10079968?hl=en)).

### Gaps
- No first-hand native WhatsApp + USB webcam result on Android TV, Google TV, Shield, CCwGTV or Fire TV dated 2024–2026 surfaced. Reddit search via these tools turned up only unrelated or pre-2023 threads.
- No reports on landscape/orientation or mirroring of the self-view in WhatsApp's call UI on a TV.

## 3. Does WhatsApp's video call use Camera2 with external cameras (LENS_FACING_EXTERNAL), or only front/back? Any "no camera found" reports?

### Takeaway
WhatsApp doesn't document this. The best evidence suggests WhatsApp has, at least historically, used the legacy Camera1 API and only camera IDs 0/1. That may actually help on this Sony. Android's camera service reports any EXTERNAL camera to Camera1 apps as a front camera, and the Brio is the TV's only camera. If current WhatsApp builds have moved to Camera2 and filter for front/back facing, the Brio would be ignored, which would match the ChromeOS and DeX complaints. A 10-minute logcat test on the TV settles it.

### Cited Findings
- **Camera1 sees external cameras as front cameras.** AOSP Android 12 `CameraProviderManager::DeviceInfo3::getCameraInfo` contains `case ANDROID_LENS_FACING_EXTERNAL: // Map external to front for legacy API` and falls through to `CAMERA_FACING_FRONT`. Camera1 apps therefore see a USB webcam as a front-facing camera. — [AOSP frameworks/av, android-12.0.0_r1](https://android.googlesource.com/platform/frameworks/av/+/refs/tags/android-12.0.0_r1/services/camera/libcameraservice/common/CameraProviderManager.cpp)
- **Which cameras Camera1 sees.** The same file builds the Camera1-visible list via `getAPI1CompatibleCameraDeviceIds()`. It drops near-infrared and hidden secure cameras, and keeps only one ID per facing for logical multi-camera groups. — [AOSP, same file](https://android.googlesource.com/platform/frameworks/av/+/refs/tags/android-12.0.0_r1/services/camera/libcameraservice/common/CameraProviderManager.cpp)
- **WhatsApp on Camera1 (2021 log).** A Waydroid logcat shows `CameraService::connect call (PID -1 "com.whatsapp", camera ID 0) for HAL version default and Camera API version 1`. That's evidence WhatsApp opened camera 0 through Camera1 in that 2021 build. Which WhatsApp screen it was (in-app camera or call) isn't stated. — [waydroid/waydroid #170](https://github.com/waydroid/waydroid/issues/170)
- **"Only IDs 0 and 1" (unverified).** A single Android StackExchange answer says WhatsApp "only has access to camera id 0 and 1", and that its linked-devices QR scanner uses only camera 0. — [Android StackExchange, 2022-06](https://android.stackexchange.com/questions/247161/change-default-camera-to-usb-camera)
- **Forum claims that external webcams aren't supported.**
  - XDA forum reply: "AFAIK as of October 2023, WhatsApp does not officially support using external webcams for video calls on Android devices". This is a forum claim, not an official one. — [XDA, 2024-01-07](https://xdaforums.com/t/can-i-plug-a-usb-c-webcam-in-my-phone-and-it-work-on-whatsapp-video-calls.4649637)
  - A 2026-04 r/SamsungDex feature request asks WhatsApp to support USB/external cameras for video calls, pointing at Android 17's new camera device-type APIs. That implies WhatsApp didn't use them on Samsung DeX as of April 2026. — [Reddit r/SamsungDex, 2026-04-14](https://www.reddit.com/r/SamsungDex/comments/1slgmb4/feature_request_usbexternal_camera_support_for)
  - Samsung's community said in 2021–2023 that Galaxy phones don't expose USB webcams to apps at all. That makes the DeX evidence weaker as a test of WhatsApp's own logic. — [Samsung Community, 2021](https://us.community.samsung.com/t5/Samsung-Apps-and-Services/Unable-to-recognize-Dex-USB-Webcam/td-p/1645607)
- **Platform background.**
  - Android supports UVC webcams through Camera2 and the camera HAL. Apps see the EXTERNAL hardware level from API 28. It's aimed at "lightweight use cases such as video chatting" and isn't meant for high-resolution or high-speed streaming. — [AOSP: External USB cameras](https://source.android.com/docs/core/camera/external-usb-cameras)
  - `LENS_FACING_EXTERNAL` (API 23) has "no fixed facing relative to the device's screen". Android's docs tell apps not to assume at most one front and one back camera. API 37 (Android 17) adds `INFO_DEVICE_TYPE_EXTERNAL`. — [Android Developers: CameraMetadata](https://developer.android.com/reference/android/hardware/camera2/CameraMetadata)
  - In CameraX, requiring `LENS_FACING_EXTERNAL` "is currently experimental and may produce unexpected behaviors". Apps built on CameraX's front/back selectors may skip external cameras. — [Android Developers: CameraSelector.Builder](https://developer.android.com/reference/androidx/camera/core/CameraSelector.Builder)
  - In testing reported on Stack Overflow, Samsung S9/A31/A51 and Pixel 2 XL phones exposed no external cameras. — [Stack Overflow, 2019–2022](https://stackoverflow.com/questions/57846505/accessing-a-usb-camera-using-android-camera2-api)
- **"No camera" and black-screen reports.**
  - ChromeOS (2025): switching WhatsApp to a USB camera fails while its mic works. — [Chromebook Community](https://support.google.com/chromebook/thread/376667759/usb-camera-s-not-working-in-some-android-apps?hl=en)
  - Waydroid (2026): no enumerated camera means no video calls. — [waydroid #2365](https://github.com/waydroid/waydroid/issues/2365)
  - Meet on Google TV (2023): "No camera found". — [Google Meet Community](https://support.google.com/meet/thread/245212177/does-google-meet-app-on-google-tv-android-tv-not-support-usb-webcams-thx?hl=en)
  - WhatsApp's own help: if another app is using the camera you may get "a black screen, or video calls might not connect properly". — [WhatsApp Help: Can't use camera](https://faq.whatsapp.com/1674076070094255)
  - WhatsApp's web help: "You can't make a video call on a device without a camera." — [WhatsApp Help: Can't make video calls (web)](https://faq.whatsapp.com/846009687015768/?cms_platform=web)

### Inferences
- **Camera1 path likely works on the Sony.** The Sony already works with Camera1 apps, and the Brio is the only camera. So `Camera.getNumberOfCameras()` should be 1, index 0 should be the Brio, and `CameraInfo.facing` should be FRONT (per the AOSP mapping). If WhatsApp's call path still uses Camera1 or IDs 0/1, it should find a "front camera" and work.
- **Camera2 path might not.** If WhatsApp uses Camera2 or CameraX and filters for FRONT/BACK, the Brio (EXTERNAL) would be ignored and WhatsApp would treat the TV as camera-less. That probably means voice only or a greyed-out video button. This fits the ChromeOS result, but ChromeOS's ARC camera stack is different, so it doesn't prove WhatsApp's logic.
- **Test plan.** During a WhatsApp video call, run `adb logcat | grep -i "CameraService::connect"`. It shows the camera ID and "Camera API version 1/2" WhatsApp uses. `adb shell dumpsys media.camera` shows each device's legacy "Facing: Front/Back". Uninstalling WhatsApp afterwards removes the companion.
- **Expected video quality.** Even if WhatsApp picks up the Brio, frame rate is capped by the HAL (~16 fps at 640x480, ~11.5 fps at 720p/1080p; project notes). An EXTERNAL device has no fixed orientation, so self-view rotation or mirroring could look wrong. That's untested.

### Gaps
- Which camera API current WhatsApp (2.26.x) uses for calls, and whether it accepts `LENS_FACING_EXTERNAL`. Nothing public, and no decompilation was done.
- Whether WhatsApp needs `android.hardware.camera.front` or similar features at runtime to offer video.

## 4. Can WhatsApp Android be driven with a D-pad remote (accessibility, mouse/air-mouse, Mouse Toggle, phone touchpad remotes)?

### Takeaway
WhatsApp's phone/tablet UI isn't designed for D-pad. Android's default focus navigation gets you part of the way, and a pointer layer covers the rest: an accessibility-service cursor (Mouse Toggle, remouse), a USB or Bluetooth mouse or air-mouse, or a phone remote. No source tests WhatsApp's own screens (call controls, incoming-call UI) under D-pad, so plan to test on the TV.

### Cited Findings
- **Google's TV rules.** TV apps "must be operable from a remote controller that only has up, down, left, right, select, Back, and Home buttons". Pointer remotes (air mice, trackpads) are an optional layer, declared via `android.software.leanback.supports_touch`. WhatsApp, a phone/tablet app, isn't built to these rules. — [Android Developers: Manage TV controllers](https://developer.android.com/training/tv/get-started/controllers); [Android Developers: TV navigation](https://developer.android.com/training/tv/get-started/navigation)
- **Mouse Toggle for Android TV.**
  - An accessibility service turns the D-pad into a mouse pointer, toggled by a button combo (e.g. double-press Play/Pause, or Vol+ then Vol−). Long-press or Home leaves mouse mode. It's on the Play Store (stock and rooted versions), and the reviewer said it fixed sideloaded apps that "wouldn't work with the standard remote". An air mouse is the "more robust" alternative. — [androidtvnews.com, 2023-06-14](https://androidtvnews.com/mouse-toggle-android-tv)
  - Current version is 1.52. — [APKPure](https://apkpure.com/mouse-toggle-for-android-tv/com.fluxii.androidtv.mousetoggle)
- **remouse (open source).** An Android TV accessibility service that "replaces focus-based navigation with a real cursor you steer using the remote's arrow keys". It's aimed at "phone apps sideloaded onto a TV box". — [GitHub zy0816/remouse](https://github.com/zy0816/remouse)
- **Phone as remote.** Android TV Remote Service / the Google TV app offers "d-pad and touchpad modes" and keyboard input from the phone; it was updated 2026-09-07. — [Google Play: Android TV Remote Service](https://play.google.com/store/apps/details?id=com.google.android.tv.remote.service&hl=en_US)
- **Layout.** The tablet companion UI is the phone UI "stretched out wide" (2022 beta hands-on). — [Android Police, 2022-09-23](https://www.androidpolice.com/whatsapp-multi-device-tablets-beta-roll-out)
- **Generic claim.** A Quora answer says sideloaded WhatsApp on a TV has "poor" usability without a touchscreen. It's flagged as AI-generated ("Assistant" bot); treat it as opinion. — [Quora](https://www.quora.com/Can-I-use-WhatsApp-on-my-smart-TV)

### Inferences
- **Hard spots for D-pad.** WhatsApp uses standard Android views (lists, text fields, toolbars), so D-pad focus will probably reach most chat-list and chat screens. The likely trouble spots are long-press menus, swipe gestures, and in-call controls that auto-hide on a timer. Incoming-call answer UI may also be hard, because WhatsApp on phones uses full-screen intents and heads-up notifications, and Google TV's System UI may not show those for non-TV apps.
- **Pointer setups.**
  - Lowest effort: Mouse Toggle, or a cheap USB/Bluetooth air-mouse. Android supports HID mice natively; that's general platform knowledge, not checked on this Sony.
  - Most integrated: the project's own launcher could include a pointer overlay, the same idea as tvchat's D-pad cursor, but that needs an accessibility service to inject taps into another app.
- **Launching it.** The project's custom launcher can list WhatsApp if it queries `CATEGORY_LAUNCHER` apps. Stock Google TV launchers usually show only leanback apps (commonly reported; not verified here).

### Gaps
- No source tested WhatsApp's call screen, incoming-call screen, or on-screen keyboard entry with only a D-pad.
- Whether incoming WhatsApp calls ring and show on a Google TV screen when WhatsApp is backgrounded. This is critical for receiving calls and needs a test on the TV.

## 5. Native WhatsApp video call performance on low-end Android boxes vs WhatsApp Web; hardware codecs

### Takeaway
Meta's 2024 and 2026 engineering posts describe a phone-first video stack for Messenger and WhatsApp. It switches between H.264 and AV1 using per-device ML eligibility, encode latency, battery and memory. On low-resolution calls Meta prefers software encoders to hardware ones, and it turns AV1 off on low-memory devices. A native call on a 3 GB MediaTek TV probably avoids most of the Chromium/WebView overhead that's making the current tvchat calls jittery. The camera HAL's fps limit doesn't change either way. No one has published benchmarks for native WhatsApp on TV boxes.

### Cited Findings
- **Meta 2024: AV1 and HD for mobile RTC.** — [Engineering at Meta, 2024-03-20](https://engineering.fb.com/2024/03/20/video-engineering/mobile-rtc-video-av1-hd)
  - Many calls run at 50 kbps or less.
  - Mobile hardware encoders can do 4K60, but at "7 FPS, 320×180, 200 Kbps" their output is "often worse than those of software encoders running on the same mobile device".
  - Meta built a "hybrid encoder" that switches between H.264 and AV1 mid-call based on CPU usage, battery level or encoding time, with codecs negotiated via WebRTC/SDP.
  - AV1 used more memory, so Meta "had to disable AV1 on devices with low memory" because of out-of-memory crashes.
- **Meta 2026: AV1 on most mobile devices.** — [Engineering at Meta, 2026-06-22](https://engineering.fb.com/2026/06/22/video-engineering/adopting-av1-for-real-time-communication-rtc-meta); [Meta Engineering FB post](https://www.facebook.com/Engineering/posts/deploying-advanced-video-codecs-for-real-time-communication-rtc-at-a-global-scal/1443529524476191); [PPC Land summary](https://ppc.land/metas-av1-codec-now-runs-on-most-mobile-devices-for-video-calls/)
  - AV1 now runs on "the majority of mobile devices" in Messenger and WhatsApp.
  - Typical RTC video bitrates are 10–400 kbps, and AV1 saves at least 20% versus H.264 on low-end and mid-range devices.
  - An "ultra-low-complexity" AV1 preset costs about as much as H.264. It uses the dav1d decoder and ML-based device eligibility (an "rtc_score").
  - At runtime the encoder preset drops, or the call switches to H.264, when encode latency nears real-time limits. Low battery also forces H.264.
  - Calls can be asymmetric: a device encodes H.264 while decoding AV1.
- **WebRTC.** WhatsApp's stack is WebRTC-based: Meta wrote about modernizing its WebRTC fork across "50+ use cases" (2026-04-09; title only, not read). — [Engineering at Meta, Video Engineering archive](https://engineering.fb.com/category/video-engineering/)
- **WhatsApp Web calling.**
  - 1:1 voice and video calls on WhatsApp Web started rolling out to web beta users on 2026-02-09 (article updated 2026-06-12). They're end-to-end encrypted, support screen share, and were expanding beyond beta "over the next few weeks". — [WABetaInfo, 2026-02-09](https://wabetainfo.com/whatsapp-is-rolling-out-voice-and-video-calls-on-the-web)
  - Group calls, call links and scheduled calls on the web were in development in 2026-01. — [WABetaInfo, 2026-01-19](https://wabetainfo.com/whatsapp-is-working-on-voice-and-video-calls-for-group-chats-on-web)
- **Android's UVC path.** It isn't meant for "high-resolution and high-speed streaming". — [AOSP: External USB cameras](https://source.android.com/docs/core/camera/external-usb-cameras)
- **Measured on this TV with the tvchat WebView client (2026-10-20, project notes).**
  - The WebView renderer used about 2 of 4 cores and 850 MB; the TV ran out of memory and the renderer crashed.
  - After fixes, calls worked but were jittery at 3.4/4 cores busy with ~190 MB free.
  - Page painting alone took ~0.5 core.
  - Brio frame rate: ~16 fps at 640x360/480, ~11.5 fps from 848x480 to 1080p.

### Inferences
- **CPU and memory.** A native call skips the browser's renderer and compositor, the JS/WASM call stack and page painting. It should use much less CPU and RAM than the WebView client, and should be less likely to hit out-of-memory on a 3 GB TV.
- **Codec choice.** Meta's ML eligibility will probably put this MediaTek TV on H.264, or on low-complexity AV1 with automatic fallback. Software encoding at low resolution is likely, given Meta prefers software encoders at small sizes. How much the TV's hardware codecs get used is unknown.
- **Frame rate.** The ~11–16 fps camera ceiling stays with either client. Native would help smoothness (rendering/CPU starvation) more than sharpness.
- **Background services.** A native app runs alongside Sony services with no `killBackgroundProcesses` trick unless the launcher does it. The tvchat module has that permission, so it could clear memory before starting WhatsApp.

### Gaps
- No benchmarks or user reports of native WhatsApp call CPU, fps or memory on TV boxes (Shield, CCwGTV, MediaTek TVs).
- Whether WhatsApp uses Android MediaCodec hardware encoders at all on non-phone devices; the Meta posts don't say for WhatsApp specifically.

## 6. Terms-of-service and account risk: sideloading, unofficial clients, official APK as a companion

### Takeaway
The real ban risk is from modded or unofficial clients (GB WhatsApp and the like) and scraping. The official, unmodified APK linked as a companion is WhatsApp's own supported mechanism. There's one wrinkle: WhatsApp's ban page also names an "unsupported device", and TVs aren't on the supported list. I found no report of anyone banned for running the official app on a TV, so the residual risk looks low but isn't zero. Get the APK from whatsapp.com/android or Google Play, never a modified build. Root isn't needed.

### Cited Findings
- **Unofficial apps (accessed 2026-10).** Using unofficial apps, or "linking your WhatsApp account to unofficial versions of WhatsApp", violates the Terms of Service. Consequences include temporary or permanent bans, or "restrictions on your account, including the ability to link devices". Named examples: GB WhatsApp, FM WhatsApp, TeleMessage, TM WhatsApp. Affected users may see "You need the official WhatsApp to log in". For Android, WhatsApp points users to its website download when the app isn't available. — [WhatsApp Help: About unofficial apps](https://faq.whatsapp.com/general/security-and-privacy/about-unofficial-apps)
- **The "unsupported device" wording (accessed 2026-10).** "Using an unauthorized application and/or unsupported device violates our Terms of Service and can result in your account being banned." — [WhatsApp Help: About account bans for unofficial apps](https://faq.whatsapp.com/1064395290901991)
- **Temporary bans (accessed 2026-10).** These are tied to "using an unofficial version of WhatsApp or harvesting information, known as scraping". — [WhatsApp Help: About temporarily banned accounts](https://faq.whatsapp.com/1848531392146538)
- **No support for unsupported setups (accessed 2026-10).** "We don't provide support for unsupported apps, devices, or versions of WhatsApp, or jailbroken or rooted devices." — [WhatsApp Help: About account bans](https://faq.whatsapp.com/465883178708358/?locale=en_US); [After your account is unbanned](https://faq.whatsapp.com/1297640017664693)
- **Linking guidance (accessed 2026-10).** "Linking your device through unofficial apps or websites may put your account at risk", with a list of where to link (TVs not included). — [WhatsApp Help: About linked devices](https://faq.whatsapp.com/general/download-and-installation/about-linked-devices)
- **Unrecognized-device check (accessed 2026-10).** When linking, WhatsApp may flag an "unrecognized device" and ask the primary phone to confirm with "Link device" or "Don't link". — [WhatsApp Help: How to link a device](https://faq.whatsapp.com/1317564962315842)
- **Play Protect.** Google Play Protect may flag, disable and uninstall unofficial WhatsApp builds ("This app is fake…"). — [WhatsApp Help: About the Google warning](https://faq.whatsapp.com/451658463723880)

### Inferences
- **Official APK on a TV.** Sideloading the official, signed APK is not an "unofficial app". The open question is the "unsupported device" clause, and I found no evidence it's enforced against official clients on TVs. Watch for the unrecognized-device prompt during linking. Confirming on the phone is the expected flow.
- **The tvchat WebView client.** It loads the official web.whatsapp.com but spoofs a desktop Chrome user agent and client hints. That's a grey area against "unofficial apps or websites". I found no enforcement reports for WebView wrappers of the official web client, but this is unverified either way. The native official APK is arguably the cleaner option in ToS terms.
- **Updates.** The TV's Play Store won't offer WhatsApp, so the sideloaded APK needs manual updates (whatsapp.com APK or adb). Stale versions eventually hit "update required" lockouts. That's general WhatsApp behaviour and wasn't checked for companions.

### Gaps
- No WhatsApp statement specifically about TVs or set-top boxes.
- No user reports, positive or negative, of account actions after running the official APK as a companion on non-phone, non-tablet hardware.
