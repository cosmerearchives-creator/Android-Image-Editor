# First Quest 2 prototype: v0.2.0

This is an **immersive rendering and input prototype**, not yet the finished gallery home screen or 2D-to-3D editor. The original updater is preserved in the Android activity.

## Expected experience

- In the Quest app launcher, choose **Android Image Editor VR** to open the immersive entry point.
- The headset should enter a dark, world-anchored immersive environment.
- A rectangular purple panel should appear approximately 1.9 metres in front of the initial head position. The panel contains a large central purple rectangle and a smaller dark one. There is **no text on the panel yet**.
- Squeeze the **right trigger**. The large rectangle should brighten while squeezed, then return when released. This verifies one OpenXR input binding; pointer/ray and functional buttons are not implemented.
- Turn your head. The panel should remain anchored in world space rather than following your gaze.
- The regular **Android Image Editor** launcher is still available to check for updates and see updater status. Both activities belong to the same installed application.

## Quest testing checklist

1. Install the signed v0.2.0 APK over v0.1.1 if v0.1.1 is already installed on Quest; confirm it updates without removing local app data. If no app is installed on Quest, install the signed v0.2.0 APK directly.
2. Confirm both the VR and updater entry points launch and that the headset enters **immersive mode**, not a flat floating Android window.
3. Verify the panel does not track your gaze, the right trigger changes its color, and no motion sickness or eye discomfort develops.
4. Exit and reopen the VR app; repeat after headset recenter. Verify no crash on suspend/resume.
5. In the regular Android launcher check for updates; confirm installed version reports v0.2.0 and the stable release check reports up-to-date.
6. Capture exact Quest OS version, error messages and behavior to inform the next iteration.

Do not claim headset compatibility until these steps are verified on a physical Quest 2. The app does not yet import, convert, save or view photographs.

## Rollback

The v0.1.1 APK remains published. Android does not normally allow downgrading in place; do **not** uninstall without first backing up any user data. If a prototype fails, leave the repository's release history intact and fix with a higher signed version.

## Build provenance

Native loader: `org.khronos.openxr:openxr_loader_for_android:1.1.43`; OpenXR Android runtime from Quest; arm64-v8a, pinned Android NDK/CMake in CI. Releases continue using the existing permanent signing identity and stable GitHub Releases updater.
