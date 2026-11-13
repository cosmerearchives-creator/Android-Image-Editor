# Quest 2 v0.5.2 — VR updater, import return, depth Undo/Redo

## Changes

- **VR home → CHECK FOR UPDATES** now runs the existing GitHub Releases updater without having to open the separate Android launcher. Checking, download percentage, up-to-date, failure, and installer hand-off states display in VR. The existing release verification (version, package identity, SHA-256, and signing certificate) remains unchanged. Android system approval screens will still be flat because the system installer owns them.
- **Image picker return fix:** launching VR from the original flat MainActivity now finishes MainActivity rather than leaving it behind as the next activity. The OpenXR renderer continues using its onPause / onResume lifecycle, and returning from the system picker should take you back to the VR Open Image page rather than showing the first Android screen.
- **Depth brush Undo/Redo:** two new buttons appear at the top-right of **Open Image**. Each completed trigger stroke becomes one undoable operation. The last 16 snapshots are retained in memory. Undo/Redo update the GPU depth map immediately and persist the corrected depth image without modifying the source photograph. Repainting after an Undo clears the redo stack. Importing or reconverting a photo resets the history.

## Test on Quest 2

1. Install v0.5.2 as an in-place update, preserving your existing imported image and depth map.
2. From the VR **home** screen, select **CHECK FOR UPDATES**. Because v0.5.2 is currently the latest stable release, you should eventually see **UP TO DATE**. The status should stay in the VR environment; there should be no need to visit the separate flat updater launcher. On a future version, the button should download a verified signed APK and hand it to Android's installer.
3. Select **Open Image → Import** and pick another photograph. When the picker closes, verify you return to Open Image in VR, *not* the flat Android screen with Enter Quest VR. Repeat after cancelling a picker and after relaunching from the original flat launcher.
4. Generate a depth map with **QUICK** if needed, activate **BRUSH ON**, and use left trigger to push or right trigger to pull areas. After completing a stroke, **UNDO** should restore the previous depth map; **REDO** should reapply it.
5. Make two distinct strokes and undo twice. Redo twice. Undo once and paint again: Redo should now be unavailable.
6. Return to immersive viewing; examine the undone and redone changes. Restart the app to verify the latest corrected depth map persists.
7. Confirm imported photo and depth map remain intact across the update, and that full VR viewing, grip movement, 2D/3D toggle, and Back continue working.

## Limitations

- Undo/Redo is **stroke-level** and only in RAM for the current session; the latest result is saved persistently but undo history is not restored after relaunch.
- Update checks work in the VR home UI; Android's permission/installation screens still come from the OS.
- Import return behavior may depend on Quest OS version and must be confirmed in-headset; CI cannot simulate immersive activity transitions.
- A Quick depth map is still required for brush editing, and the current depth quality is not neural model-based.
