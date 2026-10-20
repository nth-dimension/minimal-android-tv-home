# Cutting Android System WebView rendering cost on a low-power Android TV (render at lower resolution, scale up)

Scope note: the Chromium code quoted here is Chromium `main` (GitHub mirror) as read on 2026-10-20. The TV runs WebView 155.0.8059.43, so details may differ slightly from `main`. The Android framework code is AOSP `android12-release`, which matches the TV's API 31. "Device probe" means read-only `adb shell` queries I ran against the Sony on 2026-10-20 (192.168.68.117:5555). They returned: `ro.build.type=user`, device `BRAVIA_VH21`, model `BRAVIA 4K VH21`, `ro.product.manufacturer=Sony`, SoC manufacturer `Mediatek`, `wm size` = Physical 3840x2160 / Override 1920x1080, `wm density` = Physical 320 (no density override), WebView `com.google.android.webview 155.0.8059.43`, and dev.minimal.tvchat `targetSdk=36`, `flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]`, which means the installed build is **not debuggable**. I changed no device state.

## Q1. Where does WebView get its device scale factor (devicePixelRatio), and can an app lower it?

### Takeaway
WebView's DPR comes from the **Display** the WebView's Context is attached to, never from that Context's own Configuration. On API 31+ Chromium builds its own window context for that display from the *Application* context and reads `getResources().getDisplayMetrics().density`. That is why the ContextThemeWrapper with `densityDpi=160` was ignored. An app can lower the DPR in three ways: put the WebView on a different display with a lower density (VirtualDisplay + Presentation); change the package's CompatibilityInfo (Game Mode downscale, `cmd game downscale`); or change the system density (`wm density`). No WebSettings or androidx.webkit API exists for it, and `--force-device-scale-factor` works only on userdebug/eng builds.

### Cited Findings
- `AwContents.getWindowAndroid(Context)` creates an `ActivityWindowAndroid(context, …)` when the context unwraps to an Activity, otherwise a `new WindowAndroid(context, false)`. The result is cached per Context in a `WeakHashMap<Context, WindowAndroidWrapper>`. `AwContents` takes its DIP scale from `mWindowAndroid.getWindowAndroid().getDisplay().getDipScale()`. — [AwContents.java](https://github.com/chromium/chromium/blob/main/android_webview/java/src/org/chromium/android_webview/AwContents.java)
- The `WindowAndroid(Context, boolean)` constructor passes `DisplayAndroid.getNonMultiDisplay(context)`. — [WindowAndroid.java](https://github.com/chromium/chromium/blob/main/ui/android/java/src/org/chromium/ui/base/WindowAndroid.java)
- `getNonMultiDisplay(context)` calls `DisplayAndroidManager.getDefaultDisplayForContext(context)` and then `getManager().getDisplayAndroid(display)`. The Javadoc warns that "virtual, or 'fake', displays that are not associated with any context may be used in special cases, like Virtual Reality". — [DisplayAndroid.java](https://github.com/chromium/chromium/blob/main/ui/android/java/src/org/chromium/ui/display/DisplayAndroid.java)
- On API 30+, `getDefaultDisplayForContext` returns `context.getDisplay()` and falls back to the global default display if that throws `UnsupportedOperationException`. `getDisplayAndroid(display)` keeps **one `PhysicalDisplayAndroid` per display ID** in `mIdMap`, so every WebView on the same display shares one DPR. — [DisplayAndroidManager.java](https://github.com/chromium/chromium/blob/main/ui/android/java/src/org/chromium/ui/display/DisplayAndroidManager.java)
- In `PhysicalDisplayAndroid`, `USE_CONFIGURATION = SDK_INT >= S`. On S+ the constructor runs `appContext = ContextUtils.getApplicationContext(); mWindowContext = appContext.createWindowContext(display, TYPE_APPLICATION, null)`. `updateFromConfiguration()` then reads `mWindowContext.getResources().getDisplayMetrics()` (density, xdpi, ydpi) and takes bounds from `getMaximumWindowMetrics()`. Before S it uses `display.getRealMetrics()`. If `--force-device-scale-factor` is set (`DisplaySwitches.FORCE_DEVICE_SCALE_FACTOR`), `DisplayUtil.forcedScaleUpDisplayMetrics()` overrides density with the forced value. — [PhysicalDisplayAndroid.java](https://github.com/chromium/chromium/blob/main/ui/android/java/src/org/chromium/ui/display/PhysicalDisplayAndroid.java), [DisplayUtil.java](https://github.com/chromium/chromium/blob/main/ui/android/java/src/org/chromium/ui/display/DisplayUtil.java)
- On Android 12, `ContextImpl.createWindowContextResources()` passes `overrideConfig = null`. It uses `compatInfo = (displayId == Display.DEFAULT_DISPLAY) ? packageInfo.getCompatibilityInfo() : CompatibilityInfo.DEFAULT_COMPATIBILITY_INFO`. A window context therefore inherits no app-side Configuration overrides; on the default display it inherits only the *package* CompatibilityInfo. — [ContextImpl.java (android12)](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/core/java/android/app/ContextImpl.java)
- On Android 12, `Display.getRealMetrics()` (the pre-S Chromium path) uses `CompatibilityInfo.DEFAULT_COMPATIBILITY_INFO`, so it is unscaled as well. — [Display.java (android12)](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/core/java/android/view/Display.java)
- Chromium's own docs name VirtualDisplay + Presentation as "the officially supported way" to render a WebView offscreen: "VirtualDisplay can be used to create a Presentation and the WebView can be attached to the Presentation. WebView will then render into the SurfaceTexture of the VirtualDisplay." — [software_draw_deprecated.md](https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/docs/software_draw_deprecated.md)
- `createVirtualDisplay(name, width, height, densityDpi, surface, flags)` takes an explicit `densityDpi` (the VirtualDisplayConfig.Builder requires it to be > 0). Without `VIRTUAL_DISPLAY_FLAG_PUBLIC` the display is private: "A private virtual display belongs to the application that created it. Only the owner … [is] allowed to place windows upon it". — [DisplayManager.java (android12)](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/core/java/android/hardware/display/DisplayManager.java), [VirtualDisplayConfig.java](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/hardware/display/VirtualDisplayConfig.java)
- `Presentation` uses window type `TYPE_PRIVATE_PRESENTATION` when the display has `Display.FLAG_PRIVATE`, which covers the app's own private virtual display. — [Presentation.java (android12)](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/core/java/android/app/Presentation.java)
- The TV is a user build, has no density override (Physical density 320) and has a size override of 1920x1080 on a 3840x2160 panel. — Device probe (adb, 2026-10-20)

### Inferences
- The failed experiment is fully explained. The ContextThemeWrapper changed only that Context's Resources. Chromium resolved `context.getDisplay()` to display 0 and read density 2.0 from its own Application-derived window context. So DPR stayed 2, and a 960x540 px view became 480x270 CSS px.
- A WebView inside a `Presentation` on a private `VirtualDisplay` created with `densityDpi=160` should get a new `PhysicalDisplayAndroid` for that display ID with density 1.0, which gives DPR 1. That display ID then needs an Application-context `createWindowContext` to succeed (the app owns the display, so access should be allowed). Not tested on device.
- `createDisplayContext(display)` / `createConfigurationContext` on the *default* display cannot work: the density is read per display, not per context.
- I found no WebSettings, WebView, or androidx.webkit API that sets the device scale factor. `setInitialScale` changes only the page scale (Q2).

### Gaps
- Not verified on WebView 155 specifically (only on `main`) that `USE_CONFIGURATION`/`createWindowContext` behaves as above on API 31. The observed DPR=2 result is consistent with it.
- Not verified that Chromium's `createWindowContext(display, TYPE_APPLICATION)` succeeds on an app-owned private VirtualDisplay on Android 12. If it throws, the WebView could crash or fall back. Needs a device test.

## Q2. Can setInitialScale / useWideViewPort / overview mode / viewport or zoom injection make the page lay out at ~960x540 CSS px inside a 960x540 px view?

### Takeaway
Yes, almost certainly, without changing DPR. Keep the WebView at 960x540 px with `scaleX/scaleY=2`. Set `settings.useWideViewPort = false` and call `webView.setInitialScale(100)`. Blink's WebView quirk then sets layout width = view width in DIPs ÷ UA initial scale = 480 / 0.5 = **960 CSS px**, and height follows the aspect ratio (540), at page scale 0.5. One CSS px then equals one physical px. Side effects: `devicePixelRatio` still reports 2, `visualViewport.scale` = 0.5, and pinch-zoom must be disabled.

### Cited Findings
- `WebView.setInitialScale` Javadoc: "If initial scale is greater than 0, WebView starts with this value as initial scale. Please note that unlike the scale properties in the viewport meta tag, this method doesn't take the screen density into account." — [WebView.java (android12)](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/core/java/android/webkit/WebView.java)
- Implementation: `AwSettings::UpdateInitialPageScaleLocked` sends `SetInitialPageScale(initial_page_scale_percent / dip_scale / 100.0f)`, so `setInitialScale(100)` at DPR 2 gives page scale 0.5. — [aw_settings.cc](https://github.com/chromium/chromium/blob/main/android_webview/browser/aw_settings.cc)
- `setUseWideViewPort` Javadoc: "When the value of the setting is false, the layout width is always set to the width of the WebView control in device-independent (CSS) pixels. When the value is true and the page contains the viewport meta tag, the value of the width specified in the tag is used. If the page does not contain the tag or does not provide a width, then a wide viewport will be used." `setLoadWithOverviewMode`: "zooms out the content to fit on screen by width … taken into account when the content width is greater than the width of the WebView control." — [WebSettings.java (android12)](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/core/java/android/webkit/WebSettings.java)
- WebView always sets `wide_viewport_quirk = true` and `viewport_meta_enabled = true`. `use_wide_viewport` follows the setting and `initialize_at_minimum_page_scale` follows loadWithOverviewMode. The legacy quirks (`support_deprecated_target_density_dpi`, the `viewport_meta_non_user_scalable` quirk, `clobber_user_agent_initial_scale_quirk`, …) are enabled only when `getSupportLegacyQuirks` is true. — [aw_settings.cc](https://github.com/chromium/chromium/blob/main/android_webview/browser/aw_settings.cc)
- `PageScaleConstraintsSet::AdjustForAndroidWebViewQuirks`: when `!use_wide_viewport`, layout width = `GetLayoutWidthForNonWideViewport(icb_size_, …)` (= ICB width), and "if (user_agent_constraints_.initial_scale != -1 && (description.max_width.IsDeviceWidth() || ((max_width auto/extend-to-zoom) && description.zoom == -1))) { adjusted_layout_size_width /= user_agent_constraints_.initial_scale; … }". Height is then `ComputeHeightByAspectRatio`. When `use_wide_viewport` is true and the page has no width, layout width = `layout_fallback_width` (980). — [page_scale_constraints_set.cc](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/core/frame/page_scale_constraints_set.cc)
- Constraint precedence: `ComputeConstraintsStack()` applies default → page-defined → **user-agent** → auto-resize → fullscreen. `OverrideWith` lowers `minimum_scale` to the UA initial scale. `FitToContentsWidth` raises the minimum only to `view_width / contents_width` (= 0.5 for a 960-wide layout in a 480-DIP view). So a page's `initial-scale=1` / `minimum-scale=1` does not block the UA 0.5 scale. — [page_scale_constraints_set.cc](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/core/frame/page_scale_constraints_set.cc), [page_scale_constraints.cc](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/core/frame/page_scale_constraints.cc)
- Standardized CSS `zoom` (Chrome ~128) "changes various JS APIs to align with the spec" (getBoundingClientRect etc. include zoom). — [blink-dev Intent to Ship](https://groups.google.com/a/chromium.org/g/blink-dev/c/W8j6RKDeRoM/m/FyCKOAG9AAAJ), [Samsung policy note on Chrome 128](https://developer.samsung.com/browser/policy/standardized-browser-zoom-enabled.html)
- A curl of web.whatsapp.com from my machine returned only an error page (`<title>Error`), so I could not read WhatsApp's real `<meta name=viewport>`. — Device-independent fetch, 2026-10-20

### Inferences
- **Recipe A (smallest change to the current code):** WebView laid out at 960x540 px with `pivotX=pivotY=0` and `scaleX=scaleY=2`. Use the normal Activity context, not the density wrapper, which does nothing. Settings: `useWideViewPort=false`, `setInitialScale(100)`, `setSupportZoom(false)`, `builtInZoomControls=false`. Expected: `innerWidth`≈960, `innerHeight`≈540, `devicePixelRatio`=2, `visualViewport.scale`=0.5. This applies whether WhatsApp uses `width=device-width` or no viewport tag. If it uses a fixed `width=NNN`, the UA branch doesn't apply. In that case use `useWideViewPort=true` + `loadWithOverviewMode=true` and rewrite the tag to `width=960` at document start (MutationObserver), which gives a fit-to-width scale of 0.5.
- The cc raster scale for content is DSF × page scale × CSS transforms = 2 × 0.5 = 1.0, so tiles are rasterized at 960x540 (Q3 shows the View transform is not part of the raster scale).
- Side effects of keeping DPR=2 with page scale 0.5:
  - Canvas code sized as `innerWidth*devicePixelRatio` allocates 2x backing stores, which wastes raster on canvases.
  - `srcset`/`image-set` and `resolution` media queries pick 2x assets.
  - `visualViewport` and `screen` values look like a zoomed-out page.
  - Pinch/double-tap zoom must be off.
  - If the app's D-pad cursor injects MotionEvents straight into the WebView (not through the parent), coordinates must be divided by 2. Events routed through the parent ViewGroup are inverse-transformed automatically.
- CSS `zoom: 0.5` on `<html>` alone is worse. Blink's viewport stays 480 CSS px for `innerWidth` and width media queries, so WhatsApp's responsive breakpoints would likely pick a narrow layout. Inference only; not tested.
- `setTextZoom` scales only font size, not layout or pixel count, so it does not help here (from general WebView behavior; not re-verified in source).
- The `target-densitydpi=device-dpi` viewport quirk would give 1 CSS px = 1 device px. It needs `support_deprecated_target_density_dpi`, which is tied to legacy quirks (targetSdk < KitKat per my recollection of `supportsLegacyQuirks`; not re-verified). Not practical with targetSdk 36.

### Gaps
- Recipe A is derived from source, not tested on the device. Check with DevTools: `innerWidth`, `innerHeight`, `visualViewport.scale`, `devicePixelRatio`, `document.querySelector('meta[name=viewport]')?.content`.
- I could not determine WhatsApp Web's actual viewport meta content (bot/error page served to curl).

## Q3. Other ways to render fewer pixels: View scaling, SurfaceView/TextureView, layer types, offscreen rendering, Game Mode / per-app downscale

### Takeaway
1. **The View-transform approach already rasterizes at the WebView's layout size.** WebView's hardware draw composites with an identity transform and uses the View matrix only for tile priority. Only the final GPU stretch into the 1920x1080 window covers the full area, so Q2's Recipe A gives most of the raster savings.
2. **`cmd game downscale 0.5 dev.minimal.tvchat`** is available on this TV, needs no root, and should be allowed for this non-debuggable app because shell holds `OVERRIDE_COMPAT_CHANGE_CONFIG_ON_RELEASE_BUILD`. It should make the whole app window render into a 960x540 buffer with WebView DPR 1. This is derived from source and not yet tested.
3. **VirtualDisplay(960x540, 160 dpi) + Presentation → SurfaceView** is the officially documented offscreen path. It gives exact DPR-1 rendering, at the cost of manual input, focus, and IME handling.
4. **Software layers are unusable for video**, and hardware layers add a copy without reducing pixels.

### Cited Findings
- `SynchronousLayerTreeFrameSink::DemandDrawHw(viewport_size, viewport_rect_for_tile_priority, transform_for_tile_priority, …)` calls `client_->SetExternalTilePriorityConstraints(…)` and then `InvokeComposite(gfx::Transform(), gfx::Rect(viewport_size))`, i.e. an **identity** transform. The software path (`DemandDrawSw`) instead uses `canvas->getTotalMatrix()`. — [synchronous_layer_tree_frame_sink.cc](https://github.com/chromium/chromium/blob/main/third_party/blink/renderer/platform/widget/compositing/android_webview/synchronous_layer_tree_frame_sink.cc)
- `LayerTreeHostImpl::SetExternalTilePriorityConstraints(viewport_rect, transform)` stores only `viewport_rect_for_tile_priority_`; the transform argument is unused there. `DrawTransform()` returns `external_transform_`, which is set from the `OnDraw` transform (identity for HW draws). — [layer_tree_host_impl.cc](https://github.com/chromium/chromium/blob/main/cc/trees/layer_tree_host_impl.cc)
- Software draw: "deprecated … very slow … has known missing features, such as `<video>` or `<webgl>`". "Do not call `setLayerType(View.LAYER_TYPE_SOFTWARE)` on WebView or any parent of WebView." — [software_draw_deprecated.md](https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/docs/software_draw_deprecated.md)
- VirtualDisplay + Presentation is the supported offscreen path (quoted in Q1). — [software_draw_deprecated.md](https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/docs/software_draw_deprecated.md)
- Flutter's "Virtual Display" platform-view mode (used for WebView) shows the costs:
  - Touches have to be converted and re-synthesized as new MotionEvents ("may lose data").
  - "The embedded Android view normally can't get text input because it's inside a VirtualDisplay that is always reported as unfocused". Flutter works around this by proxying the InputConnection, which is "brittle"; "Some text functionality is still broken".
  - Accessibility has to be mirrored, using a fragile technique.

  — [Flutter Virtual-Display.md](https://flutter.googlesource.com/mirrors/flutter/+/HEAD/docs/platforms/android/Virtual-Display.md)
- A Flutter webview log on a MediaTek device: "Presentation is being dismissed because the display metrics have changed since it was created." — [flutter/flutter#24974](https://github.com/flutter/flutter/issues/24974)
- Android 12 `cmd game` help: "downscale [0.3|…|0.5|…|0.9|disable] <PACKAGE_NAME> — Force app to run at the specified scaling ratio." The implementation enables compat changes `DOWNSCALED` + `DOWNSCALE_xx` through `PlatformCompat.setOverrides`. — [GameManagerShellCommand.java (android12)](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/core/java/com/android/server/app/GameManagerShellCommand.java); the same help text is printed by the Sony. — Device probe (adb, 2026-10-20)
- `PlatformCompat.setOverrides` calls `killPackage(packageName)` after adding the overrides, so applying the downscale kills the running app. — [PlatformCompat.java (android12)](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/core/java/com/android/server/compat/PlatformCompat.java)
- `CompatModePackages.getCompatScale()` returns `1f / 0.5f` when `DOWNSCALED` and `DOWNSCALE_50` are enabled, with no game-category check. `compatibilityInfoForPackageLocked()` passes that value into `new CompatibilityInfo(…, compatScale)`. `DOWNSCALED`/`DOWNSCALE_50` are annotated `@ChangeId @Disabled @Overridable`. — [CompatModePackages.java (android12)](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/core/java/com/android/server/wm/CompatModePackages.java)
- `CompatibilityInfo`: with `overrideScale != 1`, `applicationInvertedScale = 1/overrideScale` (0.5), and `applyToDisplayMetrics` sets `density = noncompatDensity * invertedRatio`, so 2.0 becomes 1.0. — [CompatibilityInfo.java (android12)](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/core/java/android/content/res/CompatibilityInfo.java)
- `OverrideValidatorImpl` (android12) on user builds: "If the change is annotated as @Overridable, apps with the specific permission can set the override even on production builds" (`OVERRIDE_COMPAT_CHANGE_CONFIG_ON_RELEASE_BUILD`). Otherwise it requires `FLAG_DEBUGGABLE` on the target app. — [OverrideValidatorImpl.java (android12)](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/core/java/com/android/server/compat/OverrideValidatorImpl.java)
- On the Sony, `com.android.shell` has `android.permission.OVERRIDE_COMPAT_CHANGE_CONFIG_ON_RELEASE_BUILD: granted=true` and `OVERRIDE_COMPAT_CHANGE_CONFIG: granted=true`. dev.minimal.tvchat is not debuggable. — Device probe (adb, 2026-10-20)
- `GameManagerService` is started unconditionally in Android 12 `SystemServer` (no form-factor check around `startService(GAME_MANAGER_SERVICE_CLASS)`). — [SystemServer.java (android12)](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/java/com/android/server/SystemServer.java)
- Google's Game Mode docs: WM backbuffer downscaling is supported on "Select Android 12 devices" and Android 13+. "Child processes may not be resized correctly in Android 12. In particular, make sure toasts and pop-ups render correctly. We recommend that you limit the resize setting to at least 70%." "The downscaling intervention requires app restart." "…reduction of up to 30% of GPU and 10% of overall system power usage." — [Game Mode interventions](https://developer.android.com/games/optimize/adpf/gamemode/gamemode-interventions)

### Inferences
- **Ranking for this project (cheapest test first):**
  1. **Recipe A (Q2):** keep the current scaled WebView and add `useWideViewPort=false` + `setInitialScale(100)`. Raster and tile memory drop about 4x. Viz still draws the WebView quads into the full 1920x1080 window area each frame (a GPU stretch), and HWUI still redraws the app window on every WebView frame.
  2. **`adb shell cmd game downscale 0.5 dev.minimal.tvchat`**, then relaunch. Revert with `cmd game downscale disable dev.minimal.tvchat`.
     - Per source, the app's window context density becomes 1.0, so WebView DPR = 1.
     - The app window is laid out at 960x540 app pixels, so the CSS viewport stays 960x540 with no View scaling.
     - The window buffer is allocated small and SurfaceFlinger/HWC scales it up, which saves raster and composite. HWUI's RenderThread also works on a quarter of the pixels.
     - Needs no code change, but it applies to the whole app: cursor, overlays and any popups render at 540p.
     - Risks: Android 12 support is "select devices", popup/child-window issues, and WM-level input-coordinate mapping. Remove any `scaleX=2`/half-size layout when using it. Whether the override persists across reboots is unverified.
  3. **VirtualDisplay + Presentation + SurfaceView:**
     - Create a private `VirtualDisplay("wa", 960, 540, 160, surfaceView.holder.surface, 0)` and show a `Presentation` holding the WebView. The SurfaceView fills the screen and HWC scales 960x540 to 1920x1080.
     - Gives exact DPR 1 and any resolution you like (e.g. 1280x720 at 213 dpi).
     - Costs: an extra SurfaceFlinger composition of the virtual display (≈0.5 MP GPU copy per frame) and probably one extra frame of latency. You must forward D-pad and cursor events yourself (`webView.dispatchKeyEvent` / synthesized MotionEvents). IME and focus are hard, because the Presentation window is never focused. Permission prompts and file choosers should still work, since they are in-process callbacks.
     - The Presentation is dismissed if the display metrics change.
  4. `TextureView` instead of SurfaceView adds a GPU copy in the app's RenderThread. A SurfaceView lets HWC do the scaling (unverified for the MediaTek HWC).
- `LAYER_TYPE_HARDWARE` on the WebView renders into an offscreen layer and then composites it, which adds work without reducing pixels (inference). `LAYER_TYPE_SOFTWARE` breaks `<video>`.
- No per-app `wm size`/`wm density` exists on Android 12. The per-app downscale mechanism is the Game Mode compat override above.

### Gaps
- Not tested on device: (a) Recipe A's actual viewport and CPU savings; (b) whether `cmd game downscale 0.5` really yields DPR 1 / 960x540 CSS in WebView 155 and whether the Sony's SurfaceFlinger scales the buffer correctly; (c) whether WebView's `createWindowContext` works on a private VirtualDisplay.
- No measurements found of how much of the ~0.5 core of painting is per-pixel (raster/fill) versus per-frame overhead (IPC, draw calls, video texture import). If it is mostly per-frame, resolution cuts help less, and lowering the frame rate (Q4) matters more.

## Q4. Chromium command-line or WebView DevTools flags that reduce compositing cost; does `--force-device-scale-factor` work?

### Takeaway
On this TV (user build) WebView ignores `/data/local/tmp/webview-command-line` entirely. The check is on `Build.TYPE` (eng/userdebug), not on whether the app is debuggable, so `--force-device-scale-factor` is unavailable even though Chromium's display code would honor it. The only no-root route is **WebView DevTools → Flags**: a curated list that applies to all WebView apps and persists across reboots. It has no DPR flag. Relevant entries are `--disable-gpu-rasterization`, ANGLE/Vulkan, **WebViewSurfaceControlForTV** (hardware overlays), video BeginFrame throttling, and WebRTC metronome-on-VSync.

### Cited Findings
- `CommandLineUtil.initCommandLine()`: `if (AndroidInfo.isDebugAndroid()) { CommandLine.initFromFile("/data/local/tmp/webview-command-line") } else { CommandLine.init(null) }`. `isDebugAndroid()` returns `"eng".equals(Build.TYPE) || "userdebug".equals(Build.TYPE)`. — [CommandLineUtil.java](https://github.com/chromium/chromium/blob/main/android_webview/java/src/org/chromium/android_webview/common/CommandLineUtil.java), [AndroidInfo.java](https://github.com/chromium/chromium/blob/main/base/android/java/src/org/chromium/base/AndroidInfo.java)
- Doc: "this guide only applies to userdebug/eng devices and emulators … If it outputs 'user' then you can only use WebView DevTools." — [commandline-flags.md](https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/docs/commandline-flags.md)
- The TV reports `ro.build.type=user`. — Device probe (adb, 2026-10-20)
- `PhysicalDisplayAndroid.getForcedDIPScale()` reads `CommandLine…getSwitchValue(DisplaySwitches.FORCE_DEVICE_SCALE_FACTOR)` and, when > 0, forces `displayMetrics.density`. So the switch *is* honored where the command line can be set. — [PhysicalDisplayAndroid.java](https://github.com/chromium/chromium/blob/main/ui/android/java/src/org/chromium/ui/display/PhysicalDisplayAndroid.java)
- WebView DevTools: open it with `adb shell am start -a "com.android.webview.SHOW_DEV_UI"`. It offers "a curated set of experimental flags/features on production Android devices". You must kill and restart WebView apps for flags to apply, and "Starting in M84, toggled flags will be restored after WebView updates or rebooting your device." — [developer-ui.md](https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/docs/developer-ui.md)
- The curated list (`ProductionSupportedFlagList`) has no `force-device-scale-factor`. Entries relevant here:
  - `--disable-gpu-rasterization` ("rasterizes on the CPU only")
  - `--ignore-gpu-blocklist`
  - `--show-composited-layer-borders` (debug)
  - `DefaultANGLEVulkan`, `DefaultPassthroughCommandDecoder`
  - `WebViewSurfaceControl` ("Is not supported for TV, see WebViewSurfaceControlForTV") and `WebViewSurfaceControlForTV` ("Only supported on TV")
  - `AndroidYuvOverlayEvenAlignment` ("YUV hardware video overlays in SurfaceControl")
  - `LimitAImageReaderMaxSizeToOne` / `RelaxLimitAImageReaderMaxSizeToOne`
  - `WebViewEnableADPF` ("Pass WebView threads to HWUI ADPF session")
  - `AllowUndamagedNonrootRenderPassToSkip`
  - `OnBeginFrameThrottleVideo` ("throttling OnBeginFrame for video frame sinks with a preferred framerate defined")
  - `VSyncDecoding` ("Runs the WebRTC metronome off the VSync signal")

  — [ProductionSupportedFlagList.java](https://github.com/chromium/chromium/blob/main/android_webview/java/src/org/chromium/android_webview/common/ProductionSupportedFlagList.java)
- SurfaceControl/overlay gating:
  - `kWebViewSurfaceControl` is DISABLED_BY_DEFAULT and `kWebViewSurfaceControlForTV` is **ENABLED_BY_DEFAULT**.
  - `IsAndroidSurfaceControlEnabled()` returns false if `LimitAImageReaderMaxSizeToOne()`, and on WebView it requires thread-safe media. It uses the ForTV feature when `device_info::is_tv()`.
  - On TVs the AImageReader limit applies unless relaxed. The relax is enabled by default except for SoC "*Broadcom*", manufacturer "*Broadcom*|*Google*", and devices "G08|G10|G17|**BRAVIA_CT1**".
  - A MediaTek exclusion applies only when the model starts with "mt" and the Vulkan draw functor is used.

  — [gpu_finch_features.cc](https://github.com/chromium/chromium/blob/main/gpu/config/gpu_finch_features.cc)
- WebView overlays need draw-functor version ≥ 3 from HWUI (`overlays_mode`, `get_surface_control`). — [aw_draw_fn_impl.cc](https://github.com/chromium/chromium/blob/main/android_webview/browser/gfx/aw_draw_fn_impl.cc)
- This TV is device `BRAVIA_VH21`, manufacturer Sony, SoC manufacturer Mediatek, so it is not in the relax blocklists. The SurfaceFlinger layer list during idle chat showed only the app's main window layers (no call active). — Device probe (adb, 2026-10-20)

### Inferences
- On the Sony, WebView hardware overlays (SurfaceControl) are probably **on by default**, unless a server-side Finch config overrides them. HWUI on Android 12 must also support functor overlays. If video quads are promoted to HWC YUV planes, the Mali GPU does not composite them every frame. Verify during a call with `adb shell dumpsys SurfaceFlinger --list` (look for extra child layers under the tvchat window). Toggling `WebViewSurfaceControlForTV` in DevTools gives an A/B test.
- `--disable-gpu-rasterization` moves raster to CPU threads and is likely worse on a 4-core TV. ANGLE-Vulkan on Mali is an experiment with unknown benefit. Neither directly cuts pixels.
- No smooth-scrolling or frame-rate-cap switch is exposed on production builds. If WhatsApp's UI runs CSS animations during calls (pulsing indicators, timers), a document-start CSS injection (`animation`/`transition: none` on selected elements) may cut the number of composited frames. Untested.
- A debuggable app does not unlock the command-line file. The only paths are a userdebug/eng image or root.

### Gaps
- Not checked whether a Finch config has changed `WebViewSurfaceControlForTV` / `RelaxLimitAImageReaderMaxSizeToOne` for Sony/MediaTek in the field. WebView DevTools' "Flags" and "Components" pages on the TV would show the state.
- No source found that measures what any of these flags does to CPU or GPU use in a WebRTC call.

## Q5. Do `wm size` / `wm density` (system-wide, via adb) reduce WebView rendering cost on Android TV, and what do they break?

### Takeaway
Yes in principle. `wm size 960x540` plus `wm density 160` keeps the same 960x540-dp layout for every app while all windows, WebView included, render a quarter of the pixels at DPR 1. The hardware scales the result to the panel. But it is global: every app's UI becomes blurrier and it persists until reset. Changing density alone (keeping 1920x1080) only sets DPR 1 with a 1920x1080 CSS viewport, which renders the same number of pixels and makes the UI tiny.

### Cited Findings
- The Sony already runs with a size override: "Physical size: 3840x2160 / Override size: 1920x1080", "Physical density: 320". — Device probe (adb, 2026-10-20)
- Chromium on S+ reads density and bounds from a window context on the display (Q1), so a display-wide density/size change is what WebView picks up. — [PhysicalDisplayAndroid.java](https://github.com/chromium/chromium/blob/main/ui/android/java/src/org/chromium/ui/display/PhysicalDisplayAndroid.java)
- Weak anecdotal evidence only. A Banana Pi user reported `adb shell wm size 1920x1080` "did not work" on their board. — [Banana Pi forum](https://forum.banana-pi.org/t/bpi-m3-how-output-real-1080p/3276). Emby users changed density via adb to fix layout on TV boxes. — [Emby forum](https://emby.media/community/topic/62025-poster-images-not-displaying-properly)

### Inferences
- Expected effect with `wm size 960x540; wm density 160`: WebView at full screen = 960x540 px, DPR 1, CSS 960x540, so raster and composite both drop about 4x. HWUI for all apps and SurfaceFlinger client composition also drop.
- What it likely breaks or degrades:
  - The UI sharpness of every app, including the launcher.
  - Possibly apps that pick UI assets by display size.
  - Video apps should be mostly unaffected if they use a separate video plane or `Display.Mode` for resolution (ExoPlayer-style), but that is unverified for the Sony's planes.
  - Revert with `adb shell wm size 1920x1080` (or `wm size reset`, which on this TV may restore 3840x2160 rather than Sony's 1080p override, so use the explicit value) and `wm density reset`.
- Toggling it from inside the app needs `WRITE_SECURE_SETTINGS` (grantable via `pm grant` on many builds) plus hidden `IWindowManager` APIs, or a shell-uid helper (adb/Shizuku). Unverified on this device. The per-app `cmd game downscale` (Q3) is strictly better scoped.

### Gaps
- No primary source found that documents `wm size`/`wm density` side effects specifically on Google TV / Sony BRAVIA (video plane behavior, launcher, HDMI/CEC UI). Test before relying on it.
- Unverified whether `wm size reset` on this Sony returns to Sony's 1920x1080 override or to the 3840x2160 physical size.

## Q6. Measurements or reports of WebView/Chrome video-call performance on low-end Android TV / Mali GPUs

### Takeaway
I found no reliable, recent (2024–2026) measurements of WebView or Chrome WebRTC calls on Android TV or Mali hardware. The available reports are old and anecdotal. They point at software video decode or encode, not compositing, as the usual CPU hog. Google's only quantified number for resolution downscaling is "up to 30% GPU, 10% system power" (games).

### Cited Findings
- Arm community (Mali-400, RK3188, Android 5.1.1, WebView 57): "if we just play a simple video by HTML webview, the CPU usage reaches up to 90%". The responder suspected the CPU was doing the video decoding. — [Arm community thread](https://community.arm.com/support-forums/f/graphics-gaming-and-vr-forum/8709/mali400-rockchip-rk3188)
- Zoom SDK on an Amlogic Android TV box: "the cpu is at higher usage level with Zoom than webrtc", which the reporter attributed to Zoom not using hardware acceleration. — [Zoom developer forum](https://devforum.zoom.us/t/resolution-on-ott-android-unit/53514)
- Game Mode WM downscaling: "reduction of up to 30% of GPU and 10% of overall system power usage. The results can vary based on the device used". — [Game Mode interventions](https://developer.android.com/games/optimize/adpf/gamemode/gamemode-interventions)
- A Flutter issue reports WebView rendering problems and slowdowns that persist even with native WebView, resolved only by software rendering at reduced performance. It is not TV-specific. — [flutter/flutter#162439](https://github.com/flutter/flutter/issues/162439)
- The project's own call profile (from the parent's context, not an external source): page painting ≈0.5 core (RenderThread/VizWebView/Mali GPU thread). The other costs were WhatsApp workers and the page itself (~0.6 core together) and mic processing (~0.2).

### Inferences
- Halving linear resolution can cut raster and fill work by up to about 4x. The *per-frame* costs stay: HWUI RenderThread traversal, Viz frame aggregation, video-frame texture import, and GPU driver overhead. Expect a meaningful but well-under-4x CPU reduction in the ~0.5-core painting bucket. This is an estimate; measure with `top -H` / Perfetto before and after.
- If WebView overlays (Q4) are active for the remote video, much of the GPU's video work is already offloaded, and further savings must come from the page's non-video layers and frame count.

### Gaps
- No public benchmarks found for WebView 150+ WebRTC on MediaTek/Mali Google TV devices. On-device A/B measurement (Recipe A vs `cmd game downscale 0.5` vs baseline) is the only way to quantify the savings.
