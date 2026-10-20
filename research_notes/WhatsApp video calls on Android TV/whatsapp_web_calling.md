# WhatsApp Web browser calling: architecture, and which tuning levers a WebView wrapper actually controls

Context for readers: the app wraps web.whatsapp.com in Android System WebView 155 on a Sony BRAVIA (MediaTek SoC, Android 12, 4 cores, 3 GB RAM). It uses a desktop Chrome UA and cross-origin isolation (so SharedArrayBuffer exists), and it injects a document-start script. Observed during a call: several DedicatedWorker threads (~0.3 core), page main thread ~0.3 core, AudioInputDevice ~0.2 core, and an Android MediaCodec "CodecLooper" thread. Far-end video was low quality and jittery.

## 1. Does WhatsApp Web calling use standard browser WebRTC media, or its own WASM VoIP stack?

### Takeaway
It uses its own stack. WhatsApp ships its native C++ VoIP engine, built on pjlib/pjmedia, compiled to Emscripten WebAssembly and run on a pool of pthread Web Workers (this is why SharedArrayBuffer and Atomics are required). Audio (MLow/Opus) is encoded inside the WASM, and so are RTP, SRTP and SFrame. Video is H.264, and the engine has a "passthrough"/packetizer path for it. The browser's WebRTC is used only as a **transport**: an RTCPeerConnection DataChannel (ICE/DTLS/SCTP) to WhatsApp relay servers, with RTP multiplexed inside it. So browser codec negotiation, RTP senders and browser congestion control (GCC) are almost certainly not in the media path.

### Cited Findings
**Launch timeline and product facts**
- WhatsApp Web calling went to beta around Feb 9–15, 2026: 1:1 voice and video plus screen share, end-to-end encrypted with the Signal protocol, and pitched at Linux users who have no desktop app. — [WABetaInfo Feb 9](https://wabetainfo.com/whatsapp-is-rolling-out-voice-and-video-calls-on-the-web); [WABetaInfo Feb 15](https://wabetainfo.com/whatsapp-launches-encrypted-voice-and-video-calls-on-web)
- Group calls on web (up to 32 people, call links, waiting rooms) were in beta by mid-June 2026. — [Beebom, Jun 16 2026](https://gadgets.beebom.com/news/whatsapp-starts-testing-group-voice-and-video-calls-on-web-version)
- WhatsApp announced Web Calling on **July 28, 2026**, rolling out to everyone over time. It came with a Calls tab, call transfer between linked devices, waiting rooms, "QuickHD" ("calls shift to higher-resolution video faster when network conditions support it") and improved noise suppression. — [TechRepublic, Jul 29 2026](https://www.techrepublic.com/article/news-whatsapp-web-browser-voice-video-calling); [PCMag, Jul 28 2026](https://www.pcmag.com/news/whatsapp-web-adds-voice-and-video-call-support-plus-5-useful-new-perks); [Economic Times](http://widgets.economictimes.com/news/new-updates/whatsapp-introduces-web-browser-calling-no-app-download-required-check-other-new-features-introduced/articleshow/132727181.cms)
- On Oct 14, 2026, WhatsApp began rolling out guest calls that let people without an account join from a browser. — [WABetaInfo home](https://wabetainfo.com/)

**Browser requirements (official)**
- Help Center, "About supported browsers": WhatsApp Web is supported on Windows, Linux and macOS, and "Calling works best on Chrome, Edge, Firefox, Safari and Opera". Minimums are Chrome 100+, Edge 100+, Firefox 115+, Safari 15+ and Opera 86+. Android and ChromeOS are not listed. — [WhatsApp FAQ 803865374003522](https://faq.whatsapp.com/803865374003522)
- Help Center, "About WhatsApp Web calling": supported on Windows, Linux and macOS; "Certain browser versions may not be supported"; the feature is rolling out gradually. — [WhatsApp FAQ 2092488194268012](https://faq.whatsapp.com/2092488194268012/?locale=en_US&cms_platform=web)
- Help Center: "Use a headset for the best audio. Using a separate external microphone and speaker devices might cause echo." — [WhatsApp FAQ 1153602608602452](https://faq.whatsapp.com/1153602608602452/?cms_platform=web)
- No official WhatsApp document explains *why* SharedArrayBuffer is needed, or what the Safari "broken voip wasm" check is (see Gaps).

**Engine architecture (reverse engineering, 2026)**
- "WhatsApp Web implements its calling engine as an Emscripten-compiled WebAssembly module", with an explicit JS boundary into WebCrypto, WebRTC and the socket. — [WACRG: wasm analysis](https://wacrg.org/techniques/wasm-analysis); [WACRG GitHub README](https://github.com/WhiskeySockets/wacrg)
- Salman Ansari's write-up (Apr 2026):
  - The call stack is "a compiled WASM module with JavaScript around it acting as the browser adapter".
  - It is loaded by `WAWebVoipWebWasmLoader.js` and gated behind `WAWebABProps` experiment flags.
  - Its init logs show `pjlib 2.13 for POSIX initialized`, `pjmedia_codec_opus_init success`, and JS-registered audio capture/playback drivers (`WasmVoipAVDriverManager`).
  - The WASM calls JS hooks such as `sendDataToRelay({data,len,ip,port})`.
  - The audio device format he saw was 16 kHz, 320 samples per frame, 60 ms frame length (in his Node harness).
  — [hisalman.in](https://hisalman.in/blog/reverse-engineering-whatsapp-calls)
- baileys-caller(-optimized) runs "WhatsApp Web's VoIP WASM stack for Opus/RTP/SRTP, **WebRTC data channels for relay transport**". It also says "A pthread pool of worker_threads mirrors the browser's Web Worker pool the WASM expects" and that **WhatsApp Web's browser-oriented pthread pool is 20 workers**. In Node, cutting the pool from 20 to 4 lowered RSS from ~430 MB to ~160 MB. — [LuxyTeam/baileys-caller-optimized](https://github.com/LuxyTeam/baileys-caller-optimized/blob/main/README.md); [SheIITear/baileys-caller](https://github.com/SheIITear/baileys-caller)
- A second, independent implementation (WaCalls, in Go/pion) reaches WhatsApp relays with "ICE + DTLS + SCTP DataChannel" and carries MLow-encoded SRTP inside it. This confirms the relays speak a WebRTC DataChannel transport. — [JotaDev66/WaCalls](https://github.com/JotaDev66/WaCalls)
- WACRG video packetization spec (draft): the video track "MUST be carried over the same per-call media transport as audio: **RTP multiplexed onto the relay DataChannel**", protected with SRTP keys plus hop-by-hop SRTP. — [WACRG video-packetization](https://wacrg.org/spec/encodings/video-packetization)
- WACRG video codec spec (draft): "Video tracks MUST be H.264. The engine exposes an H.26x passthrough/packetizer path (`wa_h26x_passthrough`, `h26x_packetizer`) operating on H.264 NAL units". It also ships screen-content and luma-histogram detectors. Whether other video codecs exist is an open question. — [WACRG video](https://wacrg.org/spec/encodings/video); [WACRG home](https://wacrg.org/)
- Media plane details:
  - Audio is primarily MLow, an in-house CELP codec, with Opus as an alternative. It is carried over RED + Reed-Solomon FEC "into a WebRTC-NetEq receive engine".
  - RTP uses a WhatsApp profile called "WARP" (header extension 0xDEBE; audio payload types 120/121).
  - SRTP suite is AES_CM_128_HMAC_SHA1_80, applied in two layers (hop-by-hop and end-to-end). SFrame is used per frame.
  - **Congestion control and quality adaptation are listed as not yet understood.**
  — [WACRG media-srtp](https://wacrg.org/media-srtp); [WACRG warp/stun relay](https://github.com/WhiskeySockets/wacrg/blob/main/docs/transport/warp-stun-relay.md)
- Relays use a custom RFC 5389 STUN dialect (allocate 0x0003, ping 0x0801/pong 0x0802). Candidates come in the signaling `<offer>` `<destination>`/`<te>` elements with latency hints. Whether 1:1 calls are ever peer-to-peer is unknown. — [WACRG ice-and-relays](https://wacrg.org/ice-and-relays)
- Signaling uses `<call>` stanzas over the Noise-encrypted WebSocket. Advertising only 8 kHz audio steers a call from MLow to Opus narrow-band. — [WACRG architecture](https://wacrg.org/architecture); [WACRG stanza reference](https://wacrg.org/signaling/stanza-reference)
- Meta background:
  - Meta built MLow for low-end devices: "more than 20 percent of our calls are made on ARMv7 devices, and 10's of millions of daily calls on WhatsApp are on 10-year-old-plus devices". — [Meta Engineering, MLow, 2024](https://engineering.fb.com/2024/06/13/web/mlow-metas-low-bitrate-audio-codec)
  - Meta runs a long-lived fork of libwebrtc for its RTC products. The post names Messenger and Instagram, not WhatsApp Web. — [Meta Engineering, Escaping the Fork, Apr 2026](https://engineering.fb.com/2026/04/09/developer-tools/escaping-the-fork-how-meta-modernized-webrtc-across-50-use-cases/)
  - Meta published "Adopting AV1 for Real-Time Communication (RTC) at Scale" (Jun 22, 2026). The post itself returned 404 when fetched, so its content is unverified. — [Meta Engineering Video category](https://engineering.fb.com/category/video-engineering)

### Inferences
- **Why SharedArrayBuffer and Atomics are gated:** Emscripten pthreads need shared WASM memory (SAB) and Atomics. The 20-worker pthread pool matches the "multiple DedicatedWorker threads" we observed. The RTCPeerConnection check exists because the relay transport is a DataChannel. (Emscripten's SAB requirement is general knowledge, not fetched this session.)
- **Video is probably encoded and decoded by WebCodecs, while the WASM only packetizes and encrypts.** Three things point this way:
  1. The engine has a "passthrough" H.26x path rather than an embedded H.264 encoder (reverse engineers have not reported OpenH264 in the module).
  2. Our process shows a MediaCodec "CodecLooper" thread during calls. In Chromium, WebCodecs on Android goes through MediaCodec.
  3. A WASM-only H.264 encoder would show as heavy worker CPU, not as a CodecLooper thread.
  This is **unverified**. Other explanations: the CodecLooper belongs to a `<video>` element decoding something, or WhatsApp sends tracks through RTCPeerConnection for some call types. Verify with the instrumentation listed under Question 2.
- The "WebRTC-NetEq receive engine" and pjmedia sit inside the WASM, so jitter buffering and audio playout timing are WhatsApp's own. Browser `jitterBufferTarget`/`playoutDelayHint` would not apply.
- WhatsApp's own bandwidth adaptation (and QuickHD) decides the video bitrate and resolution. Underneath it, the browser's SCTP congestion control limits how fast bytes leave the DataChannel (see Question 4).

### Gaps
- No Meta/WhatsApp engineering post describes the web calling architecture. Everything above comes from community reverse engineering (WACRG marks most items "probable" or "draft").
- No source confirms WebCodecs use, the H.264 profile used on web, or how remote video is rendered (canvas/WebGL vs. MediaStreamTrackGenerator to `<video>`).
- The meaning of the Safari "broken voip wasm" gating case was not found.
- Whether DataChannels are opened ordered/reliable or unordered/partial-reliable is undocumented.
- Whether the pthread pool size is fixed at 20 in the browser, or derived from `navigator.hardwareConcurrency`, is undocumented.

## 2. Which page-script levers work, and which are bypassed by WhatsApp's own pipeline?

### Takeaway
If the media really travels inside a DataChannel, then `RTCRtpSender.setParameters` (maxBitrate, scaleResolutionDownBy, maxFramerate, degradationPreference), `setCodecPreferences` and track `contentHint` have **nothing to act on**: there are no RTP senders. The levers that still apply:
- getUserMedia / applyConstraints, which control capture size and frame rate.
- Audio-processing constraints.
- If WebCodecs is used: wrapping `VideoEncoder.configure` (hardware preference, latency mode, bitrate) and `VideoDecoder.configure` (`optimizeForLatency`).
- WebView-level Chromium flags.

The first step should be instrumentation, to confirm which APIs WhatsApp actually calls.

### Cited Findings
- Media is RTP inside the relay DataChannel, encoded and encrypted by the WASM. — [WACRG video-packetization](https://wacrg.org/spec/encodings/video-packetization); [baileys-caller-optimized](https://github.com/LuxyTeam/baileys-caller-optimized/blob/main/README.md)
- The WASM asks JS to register its audio capture and playback drivers; in the browser these map to Web Audio and MediaStream. — [hisalman.in](https://hisalman.in/blog/reverse-engineering-whatsapp-calls)
- `VideoEncoder.configure()` takes `hardwareAcceleration` ("no-preference" | "prefer-hardware" | "prefer-software"). — [MDN VideoEncoder.configure](https://developer.mozilla.org/en-US/docs/Web/API/VideoEncoder/configure)
- WebCodecs works in dedicated workers and pairs with MediaStreamTrackProcessor (track to VideoFrames) and MediaStreamTrackGenerator. — [Chrome for Developers: WebCodecs](https://developer.chrome.com/docs/web-platform/best-practices/webcodecs); [MDN WebCodecs](https://developer.mozilla.org/en-US/docs/Web/API/WebCodecs_API)
- In Chromium's Android hardware encoder (NdkVideoEncodeAccelerator), `require_low_delay` (WebCodecs `latencyMode: "realtime"` / WebRTC) sets MediaCodec `KEY_LATENCY=1` (skipped for H.264 Baseline, crbug 409110228) and `KEY_PRIORITY=0` (realtime). — [ndk_video_encode_accelerator.cc](https://chromium.googlesource.com/chromium/src/+/main/media/gpu/android/ndk_video_encode_accelerator.cc)
- Chromium decoder side: "WebCodecs and WebRTC already set the low_delay flag appropriately…" A Sep 2025 CL plumbs `KEY_LOW_LATENCY` into MediaFormat when `low_delay=true` and the codec supports it. A MediaTek "boost mode" sits behind the `media-codec-low-delay-mode` flag (in 143.0.7487.0+). — [crbug 439294798](https://issues.chromium.org/issues/439294798)
- If the browser's own WebRTC were sending video, these would apply:
  - With no explicit degradationPreference, libwebrtc maps contentHint "motion" to MAINTAIN_FRAMERATE and screencast/"detail"/"text" to MAINTAIN_RESOLUTION. BALANCED is used only under the `WebRTC-Video-BalancedDegradation` field trial; otherwise the default is MAINTAIN_FRAMERATE. — [webrtc_video_engine.cc](https://webrtc.googlesource.com/src/+/main/media/engine/webrtc_video_engine.cc)

### Inferences
**Bypassed (high confidence, if transport is DataChannel-only):**
- setParameters, setCodecPreferences, degradationPreference, and contentHint as an encoder hint.
- Browser GCC / `availableOutgoingBitrate`.
- Our planned "switch to H.264 for hardware encoding" via codec preferences is moot: WhatsApp video is already H.264 by protocol.

**Still effective:**
1. **Capture constraints.** On this Brio and HAL, the camera delivers ~16 fps at 640x360/480 but only ~11.5 fps from 848x480 up (from project notes). Clamping to 640x360 or 640x480 at 15 fps will likely give smoother motion and less per-frame encode, colour-conversion and upload work than the current 1280x720 cap.
   - Wrap `applyConstraints` too: the engine may renegotiate capture size mid-call (QuickHD).
   - Prefer a native camera mode, so Chromium does no software rescale (`resizeMode: "crop-and-scale"` scales frames in the renderer; this is general Chromium behaviour, not verified this session).
2. **Audio constraints.** AudioInputDevice at ~0.2 core is Chromium's capture-side processing (echo cancellation, noise suppression, gain control).
   - Turning these off saves CPU but risks echo with TV speakers. WhatsApp itself warns that separate mic and speakers cause echo.
   - The WASM may have its own noise suppression ("improved noise suppression"). Whether it also does echo cancellation is unknown.
3. **WebCodecs wrappers (only if confirmed used).**
   - Encoder: force `hardwareAcceleration: "prefer-hardware"` and `latencyMode: "realtime"`. Consider lowering `bitrate`/`framerate` if WhatsApp over-asks for this device.
   - Never change `width`/`height`/`codec`/`avc.format`: the packetizer and the far end expect what was signaled.
   - Decoder: set `optimizeForLatency: true` (maps to Chromium low_delay, and from there to `KEY_LOW_LATENCY` on MediaTek), and `hardwareAcceleration: "prefer-hardware"`.
4. **Chromium flags in WebView.** The WebView DevTools / developer-UI "Flags" screen can enable features such as `media-codec-low-delay-mode` for all WebView apps. (The existence of WebView's flag UI is general knowledge, not fetched this session.)
5. **Worker pool and memory.** 20 pthread workers likely contribute to the 850 MB renderer and the OOM on this 3 GB TV (in Node, 20→4 workers saved ~270 MB, though browser numbers will differ). This is speculative and risky: too small a pool can stall the engine. If the pool is computed from `navigator.hardwareConcurrency`, overriding it at document-start might shrink the pool.

**Instrumentation to run first (document-start script):**
- Wrap `RTCPeerConnection.prototype.createDataChannel` (log label and init: ordered, maxRetransmits, maxPacketLifeTime), `addTrack`, `addTransceiver`, `setCodecPreferences`.
- Wrap the `VideoEncoder`/`VideoDecoder` constructors and `configure`; `VideoEncoder.isConfigSupported`.
- Wrap `MediaStreamTrackProcessor`/`MediaStreamTrackGenerator`, `window.Worker` (count and URLs), `getUserMedia`/`applyConstraints`, and `AudioContext`/`audioWorklet.addModule`.
- Call `VideoEncoder.isConfigSupported({codec:"avc1.42e01f", width:640, height:360, hardwareAcceleration:"prefer-hardware", latencyMode:"realtime"})` inside this WebView to confirm a hardware H.264 encoder is exposed.

### Gaps
- Not verified that WhatsApp Web adds zero RTP transceivers in all call types; screen share and group calls might differ.
- Not verified whether WhatsApp's engine reads `track.getSettings()` and adapts its target to the clamped capture, or upscales.
- No evidence on whether the WASM performs its own echo cancellation.

## 3. Hardware codecs in Android WebView/Chrome (H.264, VP8, VP9, AV1), MediaTek specifics and blocklists

### Takeaway
Chromium on Android (WebView included) encodes through NdkVideoEncodeAccelerator (AMediaCodec), on by default since Android Q. It always prefers a hardware MediaCodec encoder, and only H.264 may fall back to a software MediaCodec (c2.android.avc.encoder); VP8, VP9 and AV1 require hardware. There is no blanket MediaTek encoder blocklist in current code. There is a MediaTek **VP8 decoder** blocklist before Android 13, with a small set of exempt TV chips.

Known MediaTek problems:
- An MTK AVC encoder failed to configure with the low-latency key and silently fell back to software. Fixed.
- MediaTek **decoders** show high WebRTC decode latency and stutter. Still open in 2026.

### Cited Findings
**Current Chromium encoder path**
- "Activate AndroidNdkVideoEncoder by default on Android Q+". — [Chromium commit f06cfb8](https://chromium.googlesource.com/chromium/src.git/+/f06cfb81954dffa52fa298fb1bb0ce7393836168)
- `FindMediaCodecFor` in the NDK encoder:
  - "Always prefer the hardware encoder if it exists".
  - A software codec is used only when software is required, or as a no-preference fallback.
  - For any codec other than H.264, `required_encoder_type = kHardware`.
  - Profiles are filtered by max resolution and max framerate.
  - The encoder crops to 16x16-aligned resolution ("Non 16x16 aligned resolutions don't work well with MediaCodec").
  - `encoder_info_.is_hardware_accelerated = IsHardwareCodec(name)`.
  - The supported codec set includes H.264, HEVC, VP8, VP9 and AV1, subject to the device's MediaCodec.
  — [ndk_video_encode_accelerator.cc](https://chromium.googlesource.com/chromium/src/+/main/media/gpu/android/ndk_video_encode_accelerator.cc)
- Codecs named `c2.android.*` count as software, and so do Samsung's `.sw.enc`/`.sw.dec`. — [MediaCodecUtil.java](https://chromium.googlesource.com/chromium/src/media/+/master/base/android/java/src/org/chromium/media/MediaCodecUtil.java)

**Software H.264 history**
- Historically "there is no SW H264 fallback on Android, neither OpenH264 nor FFMpeg. If MediaCodec fails to start, then H264 encoding/decoding will be broken". — [crbug 41325932](https://issues.chromium.org/41325932)
- A newer CL adds OpenH264 as an H.264 software fallback for WebRTC, WebCodecs and MediaRecorder on Android, behind a feature flag. — [crbug 40519162](https://issues.chromium.org/issues/40519162)

**MediaTek decoder blocklist**
- `IsDecoderSupportedByDevice`: "MediaTek decoders do not work properly on vp8 until Android T". For hardware strings starting "mt" with SDK < 33, VP8 hardware decode is disabled. Exempt chips: mt5599, mt5895, mt8768, mt8696 and mt5887 (several are TV SoCs). — [media_codec_util.cc](https://chromium.googlesource.com/chromium/src/+/main/media/base/android/media_codec_util.cc)

**MediaTek bugs**
- **crbug 409110228 (Fixed):**
  - On MediaTek devices, mostly Android 10, NdkVideoEncodeAccelerator failed to configure `OMX.MTK.VIDEO.ENCODER.AVC` when `require_low_delay` set the latency key.
  - WebRTC then fell back to the software `c2.android.avc.encoder` (Main profile).
  - The current code skips `KEY_LATENCY` for Baseline.
  — [crbug 409110228](https://issues.chromium.org/issues/409110228); [ndk_video_encode_accelerator.cc](https://chromium.googlesource.com/chromium/src/+/main/media/gpu/android/ndk_video_encode_accelerator.cc)
- **crbug 439294798 (open):** "Wrong choice of decoder on Mediatek devices creating high decode time".
  - Chrome used `c2.mtk.avc.decoder` rather than the `.lowlatency` variant. Decode times exceeded 14 ms, causing stutter in xCloud, Luna and GeForce Now. "This issue impact all chromium based browsers".
  - Fixes: a Sep 2025 low-latency plumbing CL (selects `c2.mtk.avc.decoder.lowlatency`), and MTK boost mode behind the `media-codec-low-delay-mode` flag.
  - Users still reported high decode time afterwards. In May 2026, a Microsoft dev on Canary 150 with the flag still saw "15ms+ per-frame decode times" at 1080p60.
  - A Fire TV (Lab126) engineer notes that low-latency mode misbehaves if B-frames are present.
  — [crbug 439294798](https://issues.chromium.org/issues/439294798)

**Other Android codec issues**
- crbug 40068493: H.264 encoder init failures on Android in Chrome 115 (fallback to VP8 failed). Fixed via a killswitch. — [crbug 40068493](https://issues.chromium.org/issues/40068493)
- crbug 40193222: hardware VP8/H.264 encoders on some phones produced corrupted video ("Some HW encoders delivers broken (displaced) video when width or height is not multiple to 16"). — [crbug 40193222](https://issues.chromium.org/issues/40193222)
- crbug 420989249: Android WebView 136 stopped advertising H.264 for WebRTC receive on one MediaTek-class tablet (Teclast P20HD, Android 10). — [crbug 420989249](https://issues.chromium.org/issues/420989249)

**Legacy (standalone libwebrtc Android SDK, not Chrome)**
- MediaTek H.264 High-profile hardware decode was gated behind the `WebRTC-MediaTekH264` field trial on Android O_MR1+. The old encoder factory listed VP8, VP9 and H.264 hardware encoders by vendor prefix. — [webrtc MediaCodecVideoDecoder.java](https://webrtc.googlesource.com/src/+/e26ff4b581f8f9cb2afd6b741bb1b0560c563f1b/sdk/android/api/org/webrtc/MediaCodecVideoDecoder.java); [webrtc HardwareVideoEncoderFactory.java](https://webrtc.googlesource.com/src/webrtc/+/a4a58a0dd60893f9b0f92ce47b8e1074f5c5c982/sdk/android/api/org/webrtc/HardwareVideoEncoderFactory.java)

### Inferences
- For WhatsApp, the codec is H.264 either way. The relevant MediaTek risks are therefore:
  1. Whether WebView picks the MTK hardware AVC encoder or the software `c2.android.avc.encoder`. This should be fine in Chromium 155, after the 409110228 fix.
  2. MTK AVC **decoder** latency and bursty output (439294798). This plausibly adds receive-side jitter on our TV. It would not explain *far-end* jitter, which comes from our send side.
- Far-end jitter is more likely caused by our uneven capture frame rate (11.5–16 fps, varying with light), CPU contention (3.4 of 4 cores busy), and WhatsApp's adaptation. It is less likely a codec choice.
- Check the encoder in use with `adb logcat | grep -iE "CCodec|ACodec|c2\.|OMX\."` during a call: `c2.mtk.avc.encoder`/`OMX.MTK.VIDEO.ENCODER.AVC` is hardware; `c2.android.avc.encoder` is software.

### Gaps
- The exact Sony BRAVIA VH21 SoC model (whether it is mt5895 or similar) was not confirmed. This matters only for VP8 decode.
- No public report found specifically on MediaTek *TV* SoC H.264 encoders in Chromium WebRTC/WebCodecs.

## 4. WebRTC CPU-overuse adaptation, qualityLimitationReason, and telling CPU limits from bandwidth limits

### Takeaway
libwebrtc's OveruseFrameDetector measures encode "usage" (processing time relative to the frame interval) and adapts down according to degradation preference: resolution-first for camera by default. The thresholds are overuse above 85% and underuse below 42%, or 200/150% for hardware encoders. getStats reports this as `qualityLimitationReason: "cpu"`; "bandwidth" takes priority when both apply.

**Caveat:** this machinery only exists for browser RTP senders. For WhatsApp's DataChannel transport, getStats will show no video `outbound-rtp`, only `data-channel`, `transport` and `candidate-pair` entries. WhatsApp's own engine does the adaptation where we cannot see it.

### Cited Findings
- Default `CpuOveruseOptions`:
  - `high_encode_usage_threshold_percent = 85`; `low = (85-1)/2` (42).
  - `min_frame_samples = 120`; `high_threshold_consecutive_count = 2`; `frame_timeout_interval_ms = 1500`.
  - The observer interface is AdaptDown ("reduce the resolution or framerate") and AdaptUp.
  — [overuse_frame_detector.h](https://webrtc.googlesource.com/src/+/main/video/adaptation/overuse_frame_detector.h)
- "Hardware accelerated encoders are assumed to be pipelined; give them additional overuse time": low 150 / high 200. CPU overuse detection is skipped entirely when `encoder_info().enable_cpu_overuse_detection` is false. The CPU resource maps to reason "cpu"; the quality scaler maps to "quality". — [video_stream_encoder_resource_manager.cc](https://webrtc.googlesource.com/src/+/main/video/adaptation/video_stream_encoder_resource_manager.cc)
- Degradation preference default: MAINTAIN_FRAMERATE (drop resolution) for camera/"motion". MAINTAIN_RESOLUTION for screencast/"detail"/"text". BALANCED only under a field trial. — [webrtc_video_engine.cc](https://webrtc.googlesource.com/src/+/main/media/engine/webrtc_video_engine.cc)
- W3C webrtc-stats:
  - `qualityLimitationReason` is one of "none", "cpu", "bandwidth", "other". If several apply, report in priority "bandwidth" > "cpu" > "other".
  - `qualityLimitationDurations` gives seconds per state.
  - `qualityLimitationResolutionChanges` counts resolution changes while limited (720p→480p→720p = 2).
  - `outbound-rtp` also has `encoderImplementation` and `powerEfficientEncoder`.
  - `candidate-pair.availableOutgoingBitrate` "is calculated by the underlying congestion control by combining the available bitrate for all the outgoing RTP streams".
  — [W3C webrtc-stats](https://www.w3.org/TR/webrtc-stats/)
- WebRTC DataChannels run over SCTP with "TCP-friendly congestion control". Data channels "must be congestion controlled". Partially reliable and unordered delivery are supported. — [RFC 8831](https://www.rfc-editor.org/rfc/rfc8831.pdf)
- Chrome's dcSCTP caps SCTP retransmission-timer backoff at 3 s, so recovery after long loss is faster. — [dcsctp_transport.cc](https://webrtc.googlesource.com/src/+/fb4ad29e3b1efc1faaac6cc8bfc7df1af3024d30/media/sctp/dcsctp_transport.cc)

### Inferences
**Diagnosis recipe for browser RTP video (generic):**
- **CPU-limited:** `qualityLimitationReason == "cpu"`; outbound `frameWidth`/`frameHeight` below `media-source` width/height; `totalEncodeTime/framesEncoded` near the frame interval; `encoderImplementation` names a software encoder; `powerEfficientEncoder == false`.
- **Bandwidth-limited:** reason "bandwidth"; `targetBitrate` tracking a low `availableOutgoingBitrate`; rising `remote-inbound-rtp` packetsLost/roundTripTime; NACK/PLI counts.
- **Source-limited:** `media-source.framesPerSecond` itself is low (the camera gives ~11.5 fps). This shows as reason "none" with low fps.

**For WhatsApp in our WebView:**
- Expect no video `outbound-rtp`, so `window.__tvchatStats()` as written may return empty video stats.
- Use instead: `data-channel` bytesSent/bytesReceived per second (actual media throughput both ways), `candidate-pair` currentRoundTripTime, `transport` bytes and packets, and `media-source` stats only if a track is attached to the PeerConnection (it probably is not).
- For CPU, use per-thread `top -H` / `simpleperf` on the device (CodecLooper vs DedicatedWorker vs CrRendererMain vs AudioInputDevice).
- Sampling `MediaStreamTrack.getSettings().frameRate` and `requestVideoFrameCallback` on the self-view gives the real capture rate.
- If the DataChannel is reliable/ordered (unknown), Wi-Fi loss causes head-of-line blocking. Combined with SCTP's loss-based backoff, that produces bursty, jittery delivery even at good bandwidth. Logging `createDataChannel` options will tell.

### Gaps
- How WhatsApp's own engine estimates bandwidth or reacts to CPU load (WACRG: "out of scope until we can observe them").
- Whether Chromium populates `availableOutgoingBitrate` for SCTP-only connections. Not verified; per spec it is defined over RTP streams.

## 5. 2026 reports of WhatsApp Web calls on low-end hardware, Linux, Chromebooks or Android browsers

### Takeaway
Coverage is almost entirely launch news. I found **no** 2026 measurements or bug reports about WhatsApp Web call CPU or quality on low-end machines, Linux, Chromebooks, or Android browsers or WebViews. Android/ChromeOS are not in WhatsApp's official supported-OS list for web calling. Older reports (2023–2025) describe WhatsApp Web in general using heavy CPU and RAM.

### Cited Findings
- Chrome Unboxed (Aug 22, 2026) found web calling live on a Chromebook in Chrome and called it a big win for ChromeOS, where the Android app is clunky. No performance data. — [Chrome Unboxed](https://chromeunboxed.com/whatsapp-web-voice-and-video-calls-in-the-browser-is-a-game-changer/)
- Coverage frames web calling as useful for Linux users, for Windows users who don't run the desktop app, and for devices that can't run the desktop app "due to the lack of system resources". These are editorial claims with no measurements. — [Beebom, Feb 2026](https://gadgets.beebom.com/news/whatsapp-web-voice-and-video-calling-rolling-out); [WABetaInfo](https://wabetainfo.com/whatsapp-is-rolling-out-voice-and-video-calls-on-the-web); [gHacks, Feb 2026](https://www.ghacks.net/2026/02/11/whatsapp-web-finally-gets-built-in-voice-and-video-calling/)
- WhatsApp's own pages list only Windows, Linux and macOS for web calling. — [WhatsApp FAQ](https://faq.whatsapp.com/2092488194268012/?locale=en_US&cms_platform=web); [Supported browsers](https://faq.whatsapp.com/803865374003522)
- General WhatsApp Web resource complaints, not call-specific: CPU spiking to 40–100% on Chrome, Firefox and Linux (2023), and unbounded RAM growth in a Firefox fork (2025). — [Reddit r/whatsapp](https://www.reddit.com/r/whatsapp/comments/187k0s9/whatsapp_web_consuming_way_too_much_cpu_pc); [zen-browser #6944](https://github.com/zen-browser/desktop/issues/6944)
- From reverse engineering: the browser pool is 20 pthread workers. In Node, 20 workers cost ~430 MB RSS versus ~160 MB for 4. — [baileys-caller-optimized](https://github.com/LuxyTeam/baileys-caller-optimized/blob/main/README.md)

### Inferences
- Our setup is outside WhatsApp's supported matrix: an Android WebView posing as desktop Chrome on a 4-core MediaTek TV with 3 GB RAM. WhatsApp's desktop-oriented defaults (20 workers, HD targets, QuickHD ramp-up) probably assume far more headroom than this device has. That is consistent with what we saw: 3.4 of 4 cores busy, about 190 MB free, and a renderer OOM.
- The most promising wins, in order:
  1. Reduce capture to the camera's smooth mode (640x360/480 at about 15 fps).
  2. Cut page painting (the biggest consumer, ~0.5 core) by rendering the WebView at lower resolution or scale.
  3. Confirm hardware H.264 encode and decode, and try decoder `optimizeForLatency` plus the low-delay flag.
  4. Reduce memory pressure so the renderer isn't OOM-killed. Android's `WebView.setRendererPriorityPolicy` and fewer workers are candidates; neither is verified here.

### Gaps
- No user reports or benchmarks of WhatsApp Web calls on Linux single-board computers, low-end Chromebooks, or Android Chrome in desktop mode were found.
- No information on whether WhatsApp lowers its video targets on low `navigator.hardwareConcurrency`/`deviceMemory` values. If it does, spoofing those values could make the engine choose lighter settings. Untested idea.
