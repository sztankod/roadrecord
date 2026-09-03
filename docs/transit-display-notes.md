# Transit animation and foreground display controls

## Behavior

- The current-stop section shows the animation only when the trip is active and no current stop is known. The next stop stays below it, including landscape orientation. The previous separate top-level animation item is removed.
- Main animation background: light `#F7FAFC`. Height: `(42 + min(widthDp * 145 / 400, 160)) * 2/3`. The vehicle uses uniform scaling; the card never grows to accommodate it.
- Main and floating views use the same Transit sprite, road renderer and 1,800 ms linear clock. Road displacement and wheel rotation are both four times their previous values.
- The source image is decoded and contour-masked once. Body and rim bitmaps are cached; drawing does not allocate paths, gradients or bitmaps on each frame.
- Animation visibility and screen-off guards remain in `DrivingAnimationView`.
- Settings → **Kijelző és ébren tartás** applies to any visible RoadRecord screen. Inactivity timers reset on interaction and on foreground entry. The lifetime can be 1–720 minutes or unlimited (0). Optional dimming starts after 1–120 inactive minutes, before expiry, to at most 5–80% brightness. It never deliberately raises an already lower system brightness.
- Pausing the activity clears the keep-screen-on flag and restores its original brightness. The feature neither changes global Android settings nor uses a wake lock. Expiry hands control back to Android's normal display timeout; it does not forcibly lock the phone.
- Database migration 24→25 adds four settings, preserves existing data and copies the previous keep-awake choice. Dimming defaults to off.

## Verification

Automated tests cover active-trip/current-stop gating, loop continuity and direction, the fourfold speed change, two-thirds card sizing, display timing, unlimited/disabled modes, persistence, and non-destructive migration intent.

Verified on 2026-09-03: `:app:assembleDebug :app:testDebugUnitTest` succeeded; 24 JVM tests passed with zero failures/errors. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. `git diff --check` reported no whitespace errors. No Android device or emulator was connected, so runtime appearance, brightness behavior and frame-time/CPU measurements remain unverified.

On-device checks still required:

1. Upgrade a copy of a v24 database; verify existing records and keep-awake choice remain.
2. Enable a 3-minute limit and dim after 1 minute at 30%. Verify dimming, touch restoration, and handing control back to Android after the limit.
3. Repeat with unlimited mode; switch apps, lock/unlock, rotate the phone, open dialogs, and test with system brightness below the selected dim percentage.
4. Open/close a trip, enter/leave a known current stop, switch tabs and scroll the status out of view. Verify only the visible active-trip scene animates.
5. Check the main and overlay visuals on a narrow phone and landscape; inspect the Transit contour and wheel registration. Measure actual frame time/CPU on a device before making performance claims.

## Graphic provenance

Asset: `app/src/main/res/drawable-nodpi/driving_transit_source.png`.
Generated with the built-in ImageGen tool, using the user's `1-Photo-1.jpg` as the mandatory reference. The returned source has a neutral opaque background, removed by the native contour mask when loaded. The old sedan resource was replaced; its previous version is recoverable from Git.

Final prompt:

> Use case: background-extraction. Asset: a single photorealistic vehicle sprite for native Android, not a UI mockup. Extract and faithfully reproduce only the white/silver Ford Transit panel cargo VAN shown in the main panel of the supplied mandatory reference. Long extended wheelbase, high roof, modern full-size Ford Transit (NOT Transit Custom, NOT a passenger minibus), no rear passenger windows. Exactly side-on view, front pointing RIGHT, both wheels fully visible on a horizontal baseline, natural tall roof and elongated body proportions. Match the reference's light metallic panels, black lower bumpers and side trim, dark cab windows, realistic understated steel wheels. One van only, centered with modest margins, crisp realistic catalog photo, soft neutral light. NO road, city, ground, cast shadow, letters or UI. GENUINE TRANSPARENT ALPHA PNG background; do not draw a checkerboard. If actual alpha is not supported, use a perfectly solid pure white #FFFFFF background, absolutely no checkerboard, no gradients and no ground shadow. Do not squash the van; preserve its high-roof proportions. Landscape output.
