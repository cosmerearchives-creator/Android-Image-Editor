# Quest 2 v0.2.1 interactive menu — on-device test

This is a **VR menu navigation test**, not an image editor. It requires physical Quest 2 validation.

## Expected in the headset

Launch **Android Image Editor VR** from the Quest app launcher. The immersive world-anchored panel is dark with purple accents and text:

- ANDROID IMAGE EDITOR / QUEST 2 VR STUDIO
- OPEN IMAGE
- MY PROJECTS
- VIEW GALLERY

Point the **right controller** at a button. A small white aiming cross should follow its pointing direction on the panel; the hovered button brightens. Squeeze and release the **right trigger** to open a placeholder screen. It will say COMING SOON and show a **BACK** button. Selecting BACK returns to the home screen.

All three menu buttons should navigate, but **none imports images yet**. The separate regular Android launcher still provides Check for Updates, including automatic launch checking.

## Checklist

1. Install the published, permanently signed v0.2.1 APK over the existing v0.2.0 build (without uninstalling). Verify the installed version changes.
2. Open the VR launcher; confirm it is truly immersive rather than a flat Android window.
3. Confirm both eyes can read the home text comfortably and head rotation leaves the panel world-anchored.
4. Move the right controller: the cross should track the controller's aim; hovering each button should highlight it.
5. Press right trigger on Open Image, My Projects, and View Gallery, and navigate back after each.
6. Try pressing the trigger while pointing outside any button; no navigation should occur.
7. Exit, reopen, suspend/resume and recenter; check for crashes.
8. Open the ordinary Android launcher to confirm the automatic and manual GitHub update check remains usable.

Report headset OS version, any missing cursor/text, strange colors, app crashes, and whether each button works. This version has **no image import, gallery, project data, brush tool or VR depth conversion**.

## Implementation limits

The buttons route only to intentionally labelled placeholder pages. The pointer projects the right controller's OpenXR aim pose onto the local-space quad, and the trigger uses press-edge detection (one navigation per squeeze). Rendering uses locally bundled procedural 5x7 text geometry, with no remote assets or network calls. No new Android permissions are needed.
