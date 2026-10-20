# Alternative routes to TV-sized WhatsApp video calls (not on the Sony's own chip)

Context carried in from the brief: Sony BRAVIA 4K VH21 (Google TV, Android 12, MediaTek 4-core, 3 GB RAM). WhatsApp Web in the user's own WebView wrapper works on the TV but is jittery: about 3.4 of 4 cores busy, roughly 190 MB RAM free, and the Brio 300 gives about 11.5–16 fps through the TV's camera HAL. User is in India, owns a Fire TV Stick 4K Max (AFTKA), and has a Logitech Brio 300. Phone OS unknown. Research date: 2026-10-20.

## Q1. External boxes: Google TV Streamer, Chromecast with Google TV, Nvidia Shield TV Pro, Xiaomi box, Fire TV Stick 4K Max

### Takeaway
None of these boxes has an official WhatsApp app or official WhatsApp Web calling. WhatsApp lists web calling only for Windows, Linux and macOS. Any box route means sideloading the WhatsApp Android APK as a linked device, or running the user's own WebView wrapper on the box. The Google TV Streamer, Fire TV Stick 4K Max and Xiaomi TV Box S all use quad Cortex-A55 MediaTek-class chips, probably no faster than the Sony. The Shield TV Pro is the only box with a clearly stronger, webcam-friendly platform. It now costs $299.99, is not officially sold in India, and its WhatsApp calling on Shield is unverified.

### Cited Findings
**Google TV Streamer (2024)**
- SoC is a MediaTek MT8696: 4x ARM Cortex-A55 at 2.0 GHz, 4 GB RAM, 32 GB storage — [FlatpanelsHD review](https://www.flatpanelshd.com/review.php?id=1729233401&subaction=showfull); [AndroidPCTV review](https://androidpctv.com/google-tv-streamer-4k-review) (adds a PowerVR GE9215 GPU). Chrome Unboxed says the MT8696 is "the same chip powering" another streamer (the snippet is cut off) — [Chrome Unboxed](https://chromeunboxed.com/the-google-tv-streamer-has-a-different-chip-under-the-hood-than-we-thought)
- Ports: USB-C (power/data), HDMI 2.1, Gigabit Ethernet. Wi-Fi 5. Five years of security updates — [Google Store UK tech specs](https://store.google.com/gb/product/google_tv_streamer_specs?hl=en-GB)
- US price is now $149.99. It launched at $99.99 and Google raised it by $50, blaming memory and component costs — [Google Store US](https://store.google.com/product/google_tv_streamer?hl=en-US); [Engadget](https://www.engadget.com/2248329/google-tv-streamer-150-50-percent-price-hike); [9to5Google](https://9to5google.com/2026/10/02/nvidia-shield-tv-pro-price-hike/)
- India: Amazon.in lists it at ₹14,990, sold by Redington India Limited and flagged "price higher than typical" — [Amazon.in](https://www.amazon.in/Google-Streamer-Streaming-Entertainment-Porcelain/dp/B0DBLWTQCT). Smartprix lists ₹15,999, "available from Dec 2025", currently out of stock — [Smartprix](https://www.smartprix.com/casting_devices/google-brand). In March 2025, Croma readers were still asking when it would launch in India — [Croma Unboxed](https://www.croma.com/unboxed/google-tv-streamer-announced). Whether it ever launched officially in India is **unverified**.
- Webcam: one reviewer says it has "a few USB Type-A and Type-C ports, allowing me to plug in a webcam (Works great on Google Meet)". This was through a hub, in 2024, before Meet left TVs — [EFTM review](https://eftm.com/2024/10/google-tv-streamer-review-256222)
- Webcam reliability: a user on the Google Nest Community (Nov 2024) says both the Streamer and the Chromecast with Google TV detect UVC webcams (Logitech C270, C615, generic) only some of the time, even with an OTG cable and the stock power adapter. Google's reply was generic and gave no fix. The same user says a cheaper Realme stick handled the same webcams fine — [Google Nest Community](https://www.googlenestcommunity.com/t5/Streaming/Google-TV-Streamer-and-Chromecast-with-Google-TV-are-having-issues-at/m-p/659782)

**Chromecast with Google TV (discontinued)**
- Replaced by the Google TV Streamer. 9to5Google calls it "the now-discontinued Chromecast with Google TV (4K)" — [9to5Google, Oct 2025](https://9to5google.com/2025/10/08/google-tv-streamer-is-an-easier-buy-at-80-in-prime-day-sale)
- 4K model: 2 GB RAM, 8 GB storage, $49.99 US — [Google Store US comparison](https://store.google.com/product/google_tv_streamer?hl=en-US)
- India: launched July 2022 at ₹6,399 on Flipkart — [Google India blog](https://blog.google/intl/en-in/products/hardware/our-best-chromecast-yet-now-with-google-tv). Flipkart still lists it at ₹6,399 — [Flipkart](https://www.flipkart.com/q/chromecast). The HD model is listed at ₹4,499 — [Smartprix](https://www.smartprix.com/casting_devices/google-brand)
- It has a single USB-C port, so a webcam needs a USB-C hub with power delivery — [jitsi-meet GitHub #7623](https://github.com/jitsi/jitsi-meet/issues/7623)
- In 2023 a Logitech BRIO 4K "worked with Google Meet on my Chromecast, albeit with significantly reduced quality, which I attribute to the Chromecast's processing capabilities" — [Google Meet Community thread](https://support.google.com/meet/thread/241207794/webcam-compatibility-issues-with-google-meet-on-nvidia-shield-tv-pro?hl=en)

**Nvidia Shield TV Pro (2019)**
- Tegra X1+ with a 256-core GPU and 3 GB RAM. Two USB 3.0 Type-A ports, Gigabit Ethernet, HDMI 2.0b, Android 11 — [Nvidia spec page](https://www.nvidia.com/en-gb/shield/shield-tv-pro)
- Price rose to $299.99 (from $199.99) on 2 Oct 2026, and the base "tube" Shield TV is out of production — [9to5Google](https://9to5google.com/2026/10/02/nvidia-shield-tv-pro-price-hike/); [TweakTown](https://www.tweaktown.com/news/113871/nvidia-raises-shield-tv-pro-price-by-50-percent-to-dollars299-99/index.html)
- India: no official channel found. An Nvidia forum thread asks where to buy one in India and mentions inflated Amazon India import prices — [NVIDIA forums](https://www.nvidia.com/en-us/geforce/forums/shield-tv/9/251844/where-to-buy-shield-tv-in-india). Smartprix shows "NVIDIA Shield TV ₹11,990", which is probably a stale listing for the discontinued base model (**unverified**) — [Smartprix](https://www.smartprix.com/casting_devices/google-brand)
- Webcams: the Shield "does detect USB webcams, at least a couple of Logitech ones I tried" (2020) — [jitsi-meet GitHub #7623](https://github.com/jitsi/jitsi-meet/issues/7623). A 2023 user found an OBSBOT Tiny 2 and a Logitech BRIO 4K "functional with other apps" on the Shield TV Pro. Only Google Meet ignored them. "Other video conferencing apps available on the Play Store for Android TV – all worked with the webcams." The user called the Shield "the only viable solution for leveraging the performance of high-end webcams on a TV" — [Google Meet Community](https://support.google.com/meet/thread/241207794/webcam-compatibility-issues-with-google-meet-on-nvidia-shield-tv-pro?hl=en)

**Xiaomi TV Box S (2nd Gen)**
- Quad Cortex-A55, Mali-G31 MP2, 2 GB RAM, 8 GB storage, one USB 2.0 port, HDMI 2.1, Google TV — [mi.com specs](https://www.mi.com/global/product/xiaomi-tv-box-s-2nd-gen/specs/)
- India price: one retailer lists ₹6,489 (MRP ₹6,999) — [dailydeals365](https://dailydeals365.in/product/xiaomi-tv-box-s-2nd-gen-4k-ultra-hd-streaming-media-player-google-tv-box-with-2gb-ram-8gb-rom-2-4g-5g-dual-wifi-bluetooth-5-2-dolby-atmos-dts-hd-dolby-vision-hdr10). A price tracker shows ₹2,059 on Amazon, which looks anomalous (**unverified**) — [PriceHistory](https://pricehistoryapp.com/product/xiaomi-tv-box-s-2nd-gen-4k-android-tv)
- No webcam or video-call reports were found for this box.

**Amazon Fire TV Stick 4K Max (owned) and Fire TV Cube**
- 4K Max 1st gen: MediaTek MT8696, 4 cores at 1.8 GHz, 2 GB RAM. Launched in India at ₹6,499 — [Indian Express](https://indianexpress.com/article/technology/gadgets/amazon-fire-tv-stick-4k-max-launched-in-india-at-rs-6499-7499561/lite). 4K Max 2nd gen (2023): MediaTek MT8696T, 4 cores at 2.0 GHz, 16 GB storage. The Fire TV Cube 3rd gen uses an 8-core Amlogic POP1-G — [AFTVnews comparison table](https://www.aftvnews.com/comparison-table-of-all-recent-2018-thru-2023-4k-fire-tv-stick-cube-models-1st-gen-vs-2nd-gen-firesticks-4k-vs-4k-max-vs-cube/)
- Amazon's webcam video calling was "limited to the newer second-generation model of the Cube", for Alexa calls to other Alexa devices — [The Verge, 2020](https://www.theverge.com/2020/12/2/21826108/amazon-fire-tv-cube-webcam-video-calling-now-available). Zoom on Fire TV Cube (2nd gen) followed in 2021. Amazon recommends a 1080p UVC webcam with a 60–90° field of view for 6–10 ft, and advises against 4K webcams — [Android Central](https://www.androidcentral.com/your-amazon-fire-tv-cube-2nd-gen-can-now-make-zoom-video-calls). Both are Alexa or Zoom, not WhatsApp.
- Fire TV Cube 3rd gen: 2 GB RAM, one USB 2.0 port, HDMI input, sold on amazon.in — [Amazon developer specs](https://developer.amazon.com/docs/device-specs/device-specifications-fire-tv-cube.html)

**WhatsApp on Android TV boxes (any brand)**
- WhatsApp has no TV app and no Play Store listing for TV. The APK can be sideloaded and linked as an extra device with the phone's QR code. A guide (low-authority, SEO-style) reports that "video calls only work vertically", that the image can be rotated or not fill the screen, that apps may crash on low-resource TVs, and that there are no real-time notifications. It does not confirm that a USB webcam works — [AndroidGuias, May 2025](https://en.androidguias.com/How-to-install-WhatsApp-on-Android-TV-and-get-the-most-out-of-it/)
- WhatsApp companion mode links secondary Android phones and tablets to an existing account, up to 4 devices. iPhone-primary accounts can link an Android companion — [Android Police, 2023](https://www.androidpolice.com/whatsapp-companion-mode-beta); [SamMobile](https://www.sammobile.com/news/more-users-can-now-use-whatsapps-companion-mode)
- WhatsApp Web calling is listed as "supported on: Windows, Linux and MacOS" (Android browsers and TV OSes are not listed) — [WhatsApp Help Center](https://faq.whatsapp.com/2092488194268012)

### Inferences
- The Google TV Streamer (MT8696, 4x A55 at 2.0 GHz) and the Fire TV Stick 4K Max 2nd gen (MT8696T, 4x 2.0 GHz) are the same CPU class. The Xiaomi box is weaker (2 GB RAM). On the Sony, the WebView call already saturates a 4-core MediaTek, so these boxes would very likely be just as jittery. The Streamer's extra RAM (4 GB vs the TV's 3 GB) might stop the out-of-memory crashes but would not fix CPU saturation.
- The Shield TV Pro's Tegra X1+ is an older design (Cortex-A57 cores, not confirmed in the sources above) with a stronger GPU, USB 3.0 and proven UVC handling. It is the only box likely to beat the TV, but the margin for WebRTC software encoding is **unverified**. At $299.99 plus import costs, it costs more than an N100 mini PC, which gets official WhatsApp support (see Q2).
- The Fire TV Stick 4K Max is a dead end for webcams. Amazon's only documented webcam support is on the Cube, and the user's notes say Fire OS also blocks Home takeover.

### Gaps
- No 2025–2026 first-hand report was found of the WhatsApp Android APK making video calls with a USB webcam on any Android TV box. Whether WhatsApp's Android app will even use an external (LENS_FACING_EXTERNAL) camera is **unverified**.
- No report was found of anyone running WhatsApp Web calling in a WebView or browser on a Shield or Streamer.
- The Sony VH21's exact SoC and core type were not found, so a direct CPU comparison with the MT8696 is not possible.
- Whether the Fire TV Stick 4K Max (any gen) recognises UVC webcams at all was not confirmed. Sources only mention the Cube.
- The CCwGTV chip is disputed. India Today says "Amlogic S905X5", which conflicts with common reports of an S905D3G. Neither was verified here.

## Q2. Mini PC or laptop over HDMI (Intel N100, Raspberry Pi 5) running WhatsApp desktop or WhatsApp Web

### Takeaway
This is the most "official" big-screen route. WhatsApp Web calling (1:1 and group, up to 32 people) became generally available on 28 July 2026. WhatsApp explicitly lists Linux, Windows and macOS with Chrome, Edge, Firefox and Safari. The Windows app has been a WebView2 wrapper of web.whatsapp.com since late 2025, so it is the same client either way. An Intel N100/N150 mini PC (about ₹11.5k and up in India) has hardware video encode and is the strongest cheap option. A Raspberry Pi 5 is officially covered (Linux plus Chromium) but has no hardware video encoder, and it is no longer cheaper.

### Cited Findings
**WhatsApp Web and desktop calling status**
- "WhatsApp Web calling is supported on: Windows, Linux and MacOS. Calling works best on Chrome, Edge, Safari and Firefox. Certain browser versions may not be supported." It is rolling out gradually. The page also warns: "Using a separate external microphone and speaker devices might cause an echo." — [WhatsApp Help Center: About WhatsApp Web calling](https://faq.whatsapp.com/2092488194268012)
- Minimum browsers: Chrome 100+, Edge 100+, Firefox 115+, Safari 15+, Opera 86+ — [WhatsApp Help Center: supported browsers](https://faq.whatsapp.com/803865374003522/?cms_platform=web)
- Video calls need an audio output, a mic and a camera. "Video calling isn't supported on devices without a camera." There is no auto-answer for video calls — [WhatsApp Help Center: 1:1 video call (Web)](https://faq.whatsapp.com/1862285217468140)
- Timeline: 1:1 web calls reached beta users on 9 Feb 2026. WABetaInfo notes this mainly benefits Linux, which has no desktop app — [WABetaInfo](https://wabetainfo.com/whatsapp-is-rolling-out-voice-and-video-calls-on-the-web). Group web calls (up to 32 people) reached beta in June 2026 — [India Today](https://www.indiatoday.in/technology/news/story/coming-soon-whatsapp-group-voice-and-video-calling-support-with-up-to-32-people-on-web-2926857-2026-06-15); [Digital Trends](https://www.digitaltrends.com/computing/whatsapp-web-is-finally-getting-group-calls-so-you-can-leave-your-phone-on-your-desk). The official launch on 28 Jul 2026 covered 1:1 and group audio and video on WhatsApp Web, Call Transfer between devices, Waiting Room, QuickHD and noise suppression, as a gradual rollout — [9to5Google](https://9to5google.com/2026/07/28/whatsapp-web-calling-new-features); [WhatsApp Blog](https://blog.whatsapp.com/introducing-web-calling-on-whatsapp-plus-more-new-updates); [9to5Mac](https://9to5mac.com/2026/07/28/whatsapp-web-rolls-out-audio-and-video-calling-with-call-transfer-support/)
- Call Transfer: "start a call on your mobile device… transfer it to WhatsApp Web or Desktop when you arrive home." WhatsApp's wording says "an active group call", while Tech Edition describes it as moving "an active conversation" — [9to5Google](https://9to5google.com/2026/07/28/whatsapp-web-calling-new-features); [Tech Edition](https://www.techedt.com/whatsapp-web-adds-voice-and-video-calling-for-browser-users)
- WhatsApp for Windows is now a WebView2 (Chromium) wrapper of web.whatsapp.com. Version 2.2584.3.0 was rolling out in all regions in Nov 2025 — [Windows Latest](https://www.windowslatest.com/2025/11/12/meta-just-killed-native-whatsapp-on-windows-11-now-it-opens-webview-uses-1gb-ram-all-the-time); [PCWorld](https://www.pcworld.com/article/2854027/whatsapps-windows-app-is-about-to-get-much-slower-for-pc-users.html). In Windows Latest's test, a video call in the new app used "almost 900MB of RAM and 25.8% CPU", against 316 MB for the old UWP app. The test PC's CPU was not stated — [Windows Latest](https://www.windowslatest.com/2025/11/22/i-tested-the-new-whatsapp-for-windows-11-a-web-wrapper-and-its-a-performance-nightmare/). Windows Latest also says web.whatsapp.com in Edge or Chrome is "less terrible" than the wrapper — [Windows Latest](https://www.windowslatest.com/2025/11/12/meta-just-killed-native-whatsapp-on-windows-11-now-it-opens-webview-uses-1gb-ram-all-the-time)

**Intel N100/N150 mini PC**
- N100: 4-core Alder Lake-N up to 3.4 GHz, 24-EU Intel graphics. Hardware encode for H.264, HEVC and VP9 (4Kp30); decode adds AV1 — [CNX Software](https://www.cnx-software.com/2024/04/29/raspberry-pi-5-intel-n100-mini-pc-comparison-features-benchmarks-price)
- Benchmarks: N100 Geekbench 6 about 1,200 single / 3,100 multi, vs Pi 5 about 900 / 2,150. N100 power is about 6–8 W idle and 25–30 W under load — [raspberry.tips, Apr 2026](https://raspberry.tips/en/smart-home/home-assistant-hardware-2026-raspberry-pi-5-or-n100-mini-pc)
- India prices: an Amazon.in "N100 mini PC" result shows ₹11,499 (MRP ₹14,999) — [Amazon.in search](https://www.amazon.in/n100-mini-pc/s?k=n100%20mini%20pc). Smartprix lists the Beelink Mini S12 Pro (N100, 16/500 GB) at ₹57,061 and the EQ12 at ₹74,811. These look like inflated import listings (**unverified**) — [Smartprix](https://www.smartprix.com/computers/beelink-brand). In Europe, a complete Beelink Mini S13 (N150, 16 GB, 500 GB, Win 11) is about €340 (July 2026) — [raspberry.tips](https://raspberry.tips/en/smart-home/home-assistant-hardware-2026-raspberry-pi-5-or-n100-mini-pc)

**Raspberry Pi 5**
- 4x Cortex-A76 at 2.4 GHz, VideoCore VII GPU, dual 4Kp60 HDMI. List prices: 1 GB $45, 2 GB $65, 4 GB $110, 8 GB $175, 16 GB $305 — [Raspberry Pi product brief](https://pip.raspberrypi.com/documents/RP-008348-DS-3-raspberry-pi-5-product-brief.pdf)
- RAM prices pushed the 8 GB board to about €185 at retailers (July 2026), so a complete N100 system now costs about the same as a comparable Pi 5 setup — [raspberry.tips](https://raspberry.tips/en/smart-home/home-assistant-hardware-2026-raspberry-pi-5-or-n100-mini-pc)
- No hardware H.264 encoder: all encoding is software (libx264/libav on the A76 cores). It can do 1080p30 H.264 in real time "using as little as 60% of" the CPU (the snippet is cut off) — [Raspberry Pi white paper, May 2026](https://pip-assets.raspberrypi.com/categories/685-app-notes-guides-whitepapers/documents/RP-010033-WP-1-H.264%20encoding%20performance%20on%20Raspberry%20Pi%205_series%20computers.pdf); [Raspberry Pi forums](https://forums.raspberrypi.com/viewtopic.php?t=378329)
- Historical: on a Pi 3, Jitsi video in Chromium was sluggish because VP8 is encoded and decoded in software — [jitsi-meet GitHub #1645, 2017](https://github.com/jitsi/jitsi-meet/issues/1645). One Pi 5 user found Chromium's video playback worse than Firefox — [Raspberry Pi forums](https://forums.raspberrypi.com/viewtopic.php?t=360614)

**Camera**
- Logitech says its BRIO works with any app that supports UVC, with no extra software, on Windows, macOS and ChromeOS. It advises against USB hubs. (This page covers the original BRIO, not the Brio 300) — [Logitech Sync hub](https://hub.sync.logitech.com/brio/post/system-requirements-and-video-collaboration-software-compatibility-RrNjN1qP8oD9fU4)
- A general guide recommends a PC over HDMI for "full keyboard, webcam and mic" use with WhatsApp Desktop or Web — [Quora answer](https://www.quora.com/Can-I-use-WhatsApp-on-my-smart-TV)

### Inferences
- An N100/N150 mini PC on Windows 11, with Chrome or Edge on web.whatsapp.com (or the WhatsApp app, which is the same web client), is on WhatsApp's officially supported list. It has hardware video encode and 8–16 GB RAM, so it should not hit the CPU and memory limits the Sony hits. That WhatsApp's WebRTC stack actually uses Intel's hardware encoder in Chrome is assumed, not confirmed. An old laptop with a webcam over HDMI is the zero-cost version of this route.
- Pi 5 plus Chromium is "supported" on paper (Linux). Without a hardware encoder, WebRTC encoding competes with the browser on the four A76 cores. Expect workable 720p at best, but this is **unverified**: no Pi 5 WhatsApp-call report was found.
- Both routes need living-room input: a wireless keyboard with touchpad, or an air mouse. You also get the full-screen landscape web UI, which suits a TV better than a mirrored portrait phone.

### Gaps
- No user report of a WhatsApp Web or desktop video call on an N100 mini PC or a Raspberry Pi 5 was found. Quality is inferred from hardware.
- Brio 300 specifics (resolution, fps, microphone) and its India price were not found in this research pass.
- Credible current Amazon.in, Flipkart or Croma prices for named N100/N150 models (Beelink, GMKtec) were not pinned down. Smartprix figures look inflated.

## Q3. Phone-based routes: mirroring or casting the phone's WhatsApp call, or using the TV only as a display

### Takeaway
The Sony VH21 (Google TV) accepts AirPlay 2 from an iPhone and Google Cast screen mirroring from Android. It does not accept Sony's Miracast "Screen mirroring", which is unsupported on Google TV models. General guides, including Indian ones, describe mirroring a WhatsApp video call to the TV as working, with the phone's own camera and mic. No source says WhatsApp blocks mirroring during calls, but WhatsApp does not document the scenario either. A wired USB-C-to-HDMI link (iPhone 15 and later, or an Android phone with DisplayPort Alt Mode) is the low-latency variant.

### Cited Findings
- The Sony Google TV help guide supports Chromecast built-in (cast from apps) and AirPlay 2, including iPhone "Mirroring" — [Sony Help Guide](https://helpguide.sony.net/tv/gusltn2/v1/en-003/05_04.html)
- Sony: "Apple devices, Google TV™ models, or 2020 and later Android TV™ models do not support Screen mirroring function" (Sony's Miracast feature) — [Sony Canada support](https://www.sony.ca/en/electronics/support/televisions-projectors-lcd-tvs-android-/kd-55x75k/articles/00044548). For Android phones mirroring to a Sony Google TV, the full-screen route is "device-dependent" and goes through Google Cast — [1001 TVs, Sep 2026](https://www.1001tvs.com/how-to-screen-mirror-to-sony-bravia-tv-google-tv)
- iPhone: Control Center, then Screen Mirroring, then pick the TV, then enter the passcode the first time — [Apple Support](https://support.apple.com/en-us/102661)
- How-to for WhatsApp calls on a smart TV: use the phone's Screen Cast, connect to the TV, then make the WhatsApp video call, "It will be visible on your smart TV too" — [Gadgets Now / Times of India](https://gadgetsnow.indiatimes.com/faqs/how-do-i-connect-my-whatsapp-to-my-tv/articleshow/83597127.cms). Vijay Sales (Indian retailer) also says WhatsApp has no TV app and recommends casting or mirroring, where "your device handles the camera and microphone" — [Vijay Sales](https://www.vijaysales.com/blog-listing/how-to-make-video-calls-on-smart-tv)
- HighSpeedInternet (Jun 2026) recommends against buying extra cameras or boxes: use phone mirroring to Google TV or Chromecast and the phone as the camera, plus a phone mount. For iPhone owners it recommends FaceTime on Apple TV — [HighSpeedInternet.com](https://www.highspeedinternet.com/resources/video-calling-on-tv)
- With mirroring, "Video calls will show on TV but microphone/camera stay on phone" — [Quora answer](https://www.quora.com/Can-I-use-WhatsApp-on-my-smart-TV)
- Latency and quality: mirroring encodes video in real time and "adds a small amount of lag" — [The Tech Gorilla](https://thetechgorilla.com/how-to-cast-phone-to-tv). AirPlay mirroring has "very low latency" but stutters on poor networks — [AirBeamTV](https://www.airbeam.tv/knowledge-base/how-does-screen-mirroring-from-an-iphone-or-ipad-to-a-tv-screen-work). AirPlay mirroring sends an H.264 stream from the frame buffer — [Apple Community](https://discussions.apple.com/thread/6106190). HDMI is "usually steadier for gaming, work and video calls" — [Laptop Outlet](https://www.laptopoutlet.co.uk/blog/mirror-or-cast-screen-to-tv.html)
- Battery: one tester loses "fifteen to twenty percent of battery per hour on an iPhone 14" while mirroring — [The Tech Gorilla](https://thetechgorilla.com/how-to-mirror-iphone-to-tv). Continuous mirroring "will drain a full battery in around two hours" — [The Tech Gorilla](https://thetechgorilla.com/how-to-cast-phone-to-tv)
- DRM apps can show a black screen when mirrored — [The Tech Gorilla](https://thetechgorilla.com/how-to-mirror-iphone-to-tv). No source found lists WhatsApp calls among them.
- Wired, iPhone: iPhone 17 (except 17e), 16 (except 16e) and 15 models can drive an HDMI TV over USB-C with an adapter, up to 4K60. It "mirrors the built-in display" — [Apple Support 105099](https://support.apple.com/en-am/105099). Apple sells a USB-C Digital AV Multiport Adapter with a passthrough charging port — [Apple Store](https://www.apple.com/shop/product/mw5m3am/a/usb-c-digital-av-multiport-adapter)
- Wired, Android: needs USB-C DisplayPort Alt Mode. "Many flagship Android phones support this feature, while many budget and mid-range phones do not" — [Computer Cable Store](https://www.computercablestore.com/connect-android-phone-to-tv). Samsung DeX works over a USB-C-to-HDMI cable. Wireless DeX needs a Miracast-capable TV — [Samsung US](https://www.samsung.com/us/support/answer/ANS10001955), and the Sony Google TV is not Miracast-capable (see above)
- Alternative phone-as-display route: start the call on the phone and use WhatsApp Call Transfer to move it to WhatsApp Web or Desktop on a TV-connected PC — [9to5Google](https://9to5google.com/2026/07/28/whatsapp-web-calling-new-features)

### Inferences
- Mirroring is free and uses WhatsApp's native mobile app, so its hardware encoders and echo cancellation do the work, not the TV's CPU. The TV only decodes an AirPlay or Cast stream, which it does in hardware. This should give a smoother call than the WebView route. The trade-offs are a portrait UI pillarboxed on the TV, the phone's camera angle and mount, and battery drain unless the phone is charging.
- Wired HDMI from an iPhone 15+ (or a DP-Alt-Mode Android phone) avoids Wi-Fi encode lag and should be the best-quality phone route. Combining the passthrough-charging adapter with a tripod mount near the TV solves battery and framing.
- The phone's mic picks up the TV speakers. Echo is handled by the phone's own processing, but this is unverified at living-room distances.

### Gaps
- No measured latency (ms) was found for AirPlay or Google Cast screen mirroring on Sony Google TVs.
- No Reddit or XDA first-hand report from 2025–2026 was found confirming that WhatsApp video calls mirror without a black screen on iOS 18/26 or Android 15/16. "WhatsApp allows mirroring" rests on how-to guides, not WhatsApp documentation (**assumed, not confirmed**).
- It was not confirmed whether WhatsApp's in-call UI rotates to landscape when mirrored or wired.
- India prices for USB-C-to-HDMI adapters were not collected.

## Q4. Dedicated TV video-calling devices still sold in 2026 (Portal TV, JioTV camera, Airtel, Samsung/LG)

### Takeaway
No dedicated TV calling device currently sold in India supports WhatsApp. Meta Portal TV was the only WhatsApp-capable TV device. It has been off sale since 31 Dec 2022 and most of its apps have been removed, though WhatsApp and Messenger calling reportedly still work. JioJoin with JioTVCamera works only on a Jio set-top box with a Jio fixed-line number, not WhatsApp. Samsung TVs and Android TV both lost Google Meet (Duo) in 2024–2025.

### Cited Findings
- Meta Portal: "Meta Portal hasn't been available for purchase since December 31, 2022… you'll notice changes to the available experiences and apps over time as we gradually discontinue them." You "can still use your Meta Portal to call family and friends" — [Meta Portal Help](https://www.meta.com/help/portal/28980127478297747). Portal TV cost $99 when Meta pulled consumer Portals in 2022 — [TechSpot](https://www.techspot.com/news/94898-meta-killing-off-portal-video-calling-devices-consumers.html). Portal supported Messenger and WhatsApp video calls — [Meta Portal features](https://www.meta.com/portal/features). Wikipedia cites Meta support for use "until February 2032" — [Wikipedia](https://en.wikipedia.org/wiki/Meta_Portal)
- Portal status in 2026, per a competitor's blog (biased source): Zoom was discontinued 31 Jan 2025, Teams and others were removed Apr 2025, Alexa was shut down Jan 2025, and the photo frame was removed Jan 2026. "Messenger and WhatsApp calling ✅ Still supported" — [CallGenie, Apr 2026](https://us.callgenietv.com/news/facebook-portal-alternatives-what-to-use-instead-in-2026)
- JioJoin is pre-installed on the Jio Set Top Box and works only there. It uses a JioHome fixed-line number and calls any mobile or landline in India. It works with a USB camera (JioTVCamera) or the phone's camera via the JioJoin app. "No, the JioJoin TV app is a JioHome-associated product and works only on Jio Set Top Box." — [Jio JioJoin page](https://www.jio.com/jiohome/services/jiojoin); [Jio FAQ: JioTVCamera](https://www.jio.com/help/faq/jiofiber/set-top-box/device-accessories/what-is-jiotvcamera). JioTVCamera launched at ₹2,999 (headline) — [Gadgets 360](https://www.gadgets360.com/cameras/news/jiotvcamera-india-launch-price-rs-2999-features-jio-fiber-set-top-box-2172740). The JioJoin phone app (Play Store, updated May 2026) has 4.3★ but recent reviews complain of dropped calls — [Google Play](https://play.google.com/store/apps/details?hl=en_US&id=com.jio.jse)
- Samsung TVs: Google Meet (formerly Duo) calling ended on Samsung TVs and Smart Monitors on 9 March 2024 — [9to5Google](https://9to5google.com/2024/02/13/google-meet-samsung-tv-shutdown); [TechRadar](https://www.techradar.com/televisions/samsung-tvs-are-losing-yet-another-google-feature-this-time-its-google-meet); [Google Meet Community](https://support.google.com/meet/thread/328104072/google-meet-is-being-removed-from-tvs?hl=en)
- Apple TV 4K (not WhatsApp): FaceTime and "compatible video conferencing apps" can use an iPhone or iPad as a Continuity Camera — [Apple Support](https://support.apple.com/guide/tv/use-your-iphone-or-ipad-as-a-camera-atvb45658929/tvos)

### Inferences
- For WhatsApp specifically, the "dedicated device" category is effectively empty in 2026. A used Portal TV is the only device with native WhatsApp TV calling, and it is a discontinued, shrinking platform.
- If the family would accept FaceTime instead of WhatsApp (iPhone households), Apple TV 4K with an iPhone as Continuity Camera is the most polished TV calling setup. It does not meet the WhatsApp requirement.

### Gaps
- No evidence was found on whether Portal TV was ever officially sold in India, or what a used one costs there.
- No information was found on Airtel Xstream, Tata Play or other Indian operator TV video calling.
- LG webOS camera and video-calling app support in 2026 was not found.
- The current Samsung TV camera app ecosystem (after Meet) was not found.
- Whether WhatsApp exists for tvOS was not confirmed. No tvOS WhatsApp app was seen in any source.
- An Apple TV 4K India price was not collected.

## Q5. Android TV apps that support USB webcams for video calls in 2026

### Takeaway
The main first-party option is gone: Google Meet calling on Android TV ended in early 2025, and Duo/Meet on Samsung TVs ended in March 2024. Webcams themselves work on Android TV boxes, especially the Shield. 2023 reports say other Play Store calling apps used them fine, but no current WhatsApp-capable Android TV app was found. Zoom has no proper Android TV build (sideload only); Zoom-on-TV was a Fire TV Cube (2nd gen) feature. Jitsi Meet can be sideloaded and works with Logitech webcams, but needs a mouse and keyboard.

### Cited Findings
- "Beginning early 2025, you won't be able to use Google Meet to make calls on your Android TV." Google suggests mobile, desktop, or casting a meeting instead — [Google Meet Help](https://support.google.com/meet/answer/13420160?hl=en). The shutdown was first spotted in an Sep 2024 APK teardown — [9to5Google](https://9to5google.com/2024/09/27/google-meet-android-tv-app-shutdown-soon); [Android Police](https://www.androidpolice.com/google-meets-android-tv-app-graveyard)
- The Shield TV Pro worked with OBSBOT Tiny 2 and Logitech BRIO 4K in "other video conferencing apps available on the Play Store for Android TV" (Oct 2023, app names not given) — [Google Meet Community](https://support.google.com/meet/thread/241207794/webcam-compatibility-issues-with-google-meet-on-nvidia-shield-tv-pro?hl=en)
- Jitsi Meet (sideloaded) worked on a Shield TV with a Logitech webcam and on a Chromecast with Google TV via a PD USB-C hub. "Right now you absolutely need a mouse and keyboard" — [jitsi-meet GitHub #7623](https://github.com/jitsi/jitsi-meet/issues/7623)
- Zoom "is not fully adapted to Android TV, so to download it you have to rely on external sources" (Spanish guide) — [Xataka Home](https://www.xatakahome.com/televisores/como-instalar-zoom-tele-android-tv-conectar-webcam-para-hacer-videollamadas). Zoom's official TV support was on the Fire TV Cube 2nd gen — [Android Central](https://www.androidcentral.com/your-amazon-fire-tv-cube-2nd-gen-can-now-make-zoom-video-calls)
- Older guidance (2021): the CCwGTV and other Android TV TVs (Sony, Hisense, TCL) could run Duo with a USB webcam, and TCL sold a TV webcam for Google TV — [Digital Trends](https://www.digitaltrends.com/home-theater/how-to-video-calls-on-tv/). Duo/Meet is now gone, so this is historical.
- WhatsApp APK on Android TV: linked-device login works, but it is "not optimized for television", with portrait-only or rotated video calls and possible crashes — [AndroidGuias](https://en.androidguias.com/How-to-install-WhatsApp-on-Android-TV-and-get-the-most-out-of-it/) (low-authority source)

### Inferences
- For WhatsApp on an Android TV device, the only candidates are (a) the sideloaded WhatsApp Android APK as a linked device, with camera support unverified, and (b) a WhatsApp Web WebView wrapper like the user's own tvchat app. Option (b) is not on WhatsApp's supported-platform list and is CPU-bound on MediaTek-class chips.
- The Brio 300 is a UVC device and should enumerate on Android TV boxes, as other Logitech webcams do. On the Sony it showed only two UVC interfaces and no USB audio, according to the user's notes. Whether that is the TV's kernel or the camera is unknown, so a separate USB mic may still be needed on Android TV boxes.

### Gaps
- No 2025–2026 list of working Android TV video-call apps with USB webcams was found. Names of currently maintained apps (Jitsi, Telegram, Viber, Botim, IMO) were not verified on Android TV.
- Whether the Brio 300 exposes its microphone as USB audio on a Shield or Google TV Streamer is unknown.

## Q6. Ranked comparison (cost, quality, effort, WhatsApp support)

### Takeaway
For WhatsApp specifically, the best value is (1) the phone itself, mirrored or wired to the TV, or (2) a cheap Windows N100/N150 mini PC (or an old laptop) with the Brio 300, running WhatsApp Web or WhatsApp for Windows. Every Android TV box route is unofficial for WhatsApp. Only the Shield is likely to beat the Sony's CPU, and it costs more than a mini PC.

### Cited Findings
(The ranking is in Inferences. These are the cost anchors used.)
- Free (already owned): AirPlay 2 and Google Cast on the Sony — [Sony Help Guide](https://helpguide.sony.net/tv/gusltn2/v1/en-003/05_04.html)
- iPhone 15/16/17 USB-C to HDMI, up to 4K60 — [Apple Support](https://support.apple.com/en-am/105099)
- N100 mini PC from ₹11,499 on Amazon.in (search result) — [Amazon.in](https://www.amazon.in/n100-mini-pc/s?k=n100%20mini%20pc)
- Raspberry Pi 5 boards from $45 (1 GB) to $175 (8 GB) list price. 8 GB is about €185 retail in mid-2026 — [RPi brief](https://pip.raspberrypi.com/documents/RP-008348-DS-3-raspberry-pi-5-product-brief.pdf); [raspberry.tips](https://raspberry.tips/en/smart-home/home-assistant-hardware-2026-raspberry-pi-5-or-n100-mini-pc)
- Google TV Streamer ₹14,990–15,999 (India listings) — [Amazon.in](https://www.amazon.in/Google-Streamer-Streaming-Entertainment-Porcelain/dp/B0DBLWTQCT); [Smartprix](https://www.smartprix.com/casting_devices/google-brand)
- Shield TV Pro $299.99 US, no official India channel — [9to5Google](https://9to5google.com/2026/10/02/nvidia-shield-tv-pro-price-hike/)
- JioTVCamera ₹2,999, needs a Jio set-top box and fixed line, no WhatsApp — [Gadgets 360](https://www.gadgets360.com/cameras/news/jiotvcamera-india-launch-price-rs-2999-features-jio-fiber-set-top-box-2172740); [Jio](https://www.jio.com/jiohome/services/jiojoin)

### Inferences
Ranking (best first). Quality ratings are expected values, inferred from hardware and the sources above. None was measured.

| # | Route | Approx. cost (India) | Expected quality | Effort | WhatsApp support |
|---|---|---|---|---|---|
| 1 | **N100/N150 mini PC (Windows 11) + Brio 300 over HDMI**, Chrome/Edge on web.whatsapp.com or WhatsApp for Windows; or an old laptop | ₹11.5k+ for the PC (₹0 with a spare laptop); plus a wireless keyboard/air mouse | High: hardware encode, 8–16 GB RAM, landscape full-screen UI, 1:1 and group up to 32 | Medium (one-time setup; the remote is a keyboard or air mouse; Brio mic should work on Windows, unverified) | **Official** (Windows/Linux listed; GA July 2026) |
| 2 | **Phone wired to TV** (iPhone 15+ USB-C to HDMI; Android with DP Alt Mode or Samsung DeX) + phone tripod near TV | Adapter cost only (INR not collected) | High: native app encoders, near-zero link latency; portrait UI pillarboxed; phone camera | Low per call (plug in), but the phone must sit at the TV | Native WhatsApp app; mirroring during calls is **assumed** (not documented by WhatsApp) |
| 3 | **Phone mirrored wirelessly**: iPhone to AirPlay 2 on the Sony; Android to Google Cast screen-cast | ₹0 | Medium: Wi-Fi encode lag and compression, about 15–20%/h battery drain on iPhone | Low | Same as #2: **assumed**; how-to guides say it works |
| 4 | **Raspberry Pi 5 + Chromium** on WhatsApp Web | $110–175 board list (4–8 GB) plus PSU, case, SD/SSD; RAM inflation narrows the gap to N100 | Low to medium: no hardware video encoder; Chromium video performance concerns | High (Linux setup, input devices) | **Official on paper** (Linux + Chromium); no Pi-specific report |
| 5 | **Nvidia Shield TV Pro** + sideloaded WhatsApp APK (linked device) or the user's WebView wrapper | $299.99 US plus import; no official India channel | Unknown; stronger GPU, USB 3.0 and good UVC support, but WhatsApp camera use is unverified | High | **Unofficial**, unverified |
| 6 | **Google TV Streamer** + USB-C hub + Brio | ₹14,990–15,999 listing, plus hub | Likely no better than the Sony (same 4x A55 MT8696 class); flaky UVC detection reported | High | **Unofficial**, unverified |
| 7 | **Xiaomi TV Box S 2nd Gen / Fire TV Stick 4K Max** | about ₹6.5k / already owned | Poor: 2 GB RAM, A55 cores; Fire Stick has no documented webcam support | High | **Unofficial**; Fire OS route not viable |
| 8 | **Used Meta Portal TV** | Second-hand only (India availability unknown) | Built for TV calling (smart camera) | Medium | WhatsApp calling reportedly still works (biased source); **discontinued**, apps being removed |
| — | Not WhatsApp: JioJoin + JioTVCamera (₹2,999, Jio STB only); Apple TV 4K + iPhone Continuity Camera (FaceTime only); Fire TV Cube webcam (Alexa calls/Zoom) | — | — | — | **No WhatsApp** |

- Hybrid trick for #1: with Call Transfer (July 2026), a call can start on the phone and move to the TV-connected PC's WhatsApp Web or Desktop. WhatsApp's wording mentions group calls, so 1:1 transfer is **unverified**.
- If the user wants to keep everything on Google TV hardware, the Shield is the only box worth testing. The Streamer and the Fire Stick are the same CPU class as the problem they would replace.

### Gaps
- No side-by-side measurements of call quality (resolution, fps, latency) were found for any of these routes. All quality columns are inferences.
- Firm INR prices for the Brio 300, USB-C-to-HDMI adapters, named N100 models on Amazon.in or Flipkart, Apple TV 4K and the Fire TV Cube 3rd gen were not collected.
- The Sony VH21's exact SoC was not identified, which limits the "is the box faster than the TV" comparison.
