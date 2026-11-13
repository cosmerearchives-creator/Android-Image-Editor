# Quest 2 VR foundation — development specification

Status: planning implemented as repository documentation; **VR executable not yet implemented**. This document records the agreed direction and the next independently testable milestone.

## Hardware and platform
- Standalone **Meta Quest 2** is the primary target. Phone support is not in the initial scope.
- Native Android package ID remains `com.cosmerearchives.androidimageeditor`.
- Use **OpenXR** for headset tracking, stereo composition, controller poses and inputs; do not confuse a conventional Android 2D activity shown on a virtual screen with immersive VR.
- Dark, minimal surrounding space, one large world-anchored canvas, a small collapsible interface, subtle purple accents.
- Keep offline editing/viewing possible. Internet only for updater and optional SMB imports.
- Preserve existing signed GitHub Releases, version mapping, signing certificate and updater implementation.

## Next testable milestone: immersive Quest 2 shell

**Do not ship a claimed VR milestone until it actually renders stereoscopically on Quest 2.**

1. Establish reproducible Android arm64-v8a OpenXR build with fixed, documented SDK/NDK and dependencies.
2. Add a Quest-compatible immersive launch path while preserving access to the existing updater (launch check, manual Check for Updates, progress/errors, install prompt).
3. Initialize and manage OpenXR instance/session, per-eye swapchains, frame submission and lifecycle correctly.
4. Display an entirely dark virtual environment with a large, comfortably positioned, world-anchored home panel.
5. Make controller rays and one primary action operable (e.g., select a placeholder Open Image control); don't claim image import before implemented.
6. Test install/update over an existing v0.1.1 install on Quest 2, foreground/background, headset recentering, input, frame stability, and installer handoff.
7. Publish stable APK **only after** CI success and release-signature checks. Preserve version increments, main-only release policy and existing stable installer workflow.

**Acceptance checks:** starts in immersive HMD mode (not a 2D window); both eyes render correctly; image/text is legible; controller input works; headset motion does not make the panel follow gaze; updater still checks and installs an upgrade; no unnecessary permissions. If OpenXR bootstrap or on-device behavior fails, record the blocker and avoid claiming the milestone complete.

### Engineering integration notes
- Current app is a Java `MainActivity` using Android widgets, plus `ReleaseUpdater` and `StableVersion`; those updater classes should remain functional and can be adapted behind a VR-compatible UI surface.
- Current CI assembles/tests via `scripts/ci-build.sh`, and publication is a guarded, manually dispatched workflow using the existing permanent signing identity.
- Choose a maintained OpenXR implementation or a reproducibly vendored/locked native dependency; confirm licensing and compatibility before making it part of CI.
- Avoid introducing proprietary Quest runtime binaries into the repository without checking distribution rights.
- Do not make an empty OpenXR manifest declaration that incorrectly promises immersive operation without a working render loop.

## Subsequent milestones (not part of first immersive shell)

**Single image editor:** locally import one image, non-destructively copy original into a self-contained project, view in VR, generate a depth map with Quick and High Quality modes (on-device capability to be benchmarked), preview and save.

**Depth sculpting:** left trigger pushes back, right trigger brings forward; continued press gradually changes depth, analog trigger strength can control rate. Adjustable brush sizes and initially soft/hard brush shapes. Toggle linking the hands' brushes; toggle edge-aware correction and colored painting feedback. Live stereoscopic preview. Undo/redo.

**Spatial manipulation:** one grip moves canvas; both grips resize/rotate it; thumbstick zooms toward active brush pointer, reset view restores default framing. Editing zoom/pose must not change image or depth data.

**Projects/gallery:** simple default home screen; Continue Editing appears when applicable; editable projects contain their own media; manual Save plus isolated automatic crash recovery and Restore/Discard choices. Projects shown in thumbnail grid. Gallery holds exported independent copies in user-created nested folders; controller left/right browses within folder; default immersive view, extreme zoom, head-directed exploration, optional remember-per-image viewing setup, Edit Original Project when it exists.

**Exports:** native depth-preserving 3D and standard flat 2D initially; no SBS requirement initially. Gallery and project data are separate.

**Later:** 360° spherical multi-image collage and optional phone support, prompt-assisted editing, slideshow, alternative environments.

## References
- Meta OpenXR support: https://developers.meta.com/vr/documentation/native/android/mobile-openxr/
- Meta hello_xr headset build instructions: https://developers.meta.com/vr/documentation/native/android/mobile-build-run-hello-xr-app/
- Meta OpenXR SDK: https://github.com/meta-quest/Meta-OpenXR-SDK
