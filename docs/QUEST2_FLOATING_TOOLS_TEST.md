# Quest 2 v0.5.4 — Floating immersive tools and Brush Mode fix

## Changes
- Immersive controls are no longer drawn into the photograph. Instead they render as a **separate VR compositor quad** at the side of the large image. Its position does not follow the photo when you grip/move the image.
- In immersive mode, point at the floating panel's purple **MOVE PANEL - HOLD GRIP** header and hold either controller grip to move only the control panel. Release to leave it there. The control panel starts just beyond the large picture's right edge; turn your head toward the right to find it.
- The panel contains 3D ON/OFF, BRUSH ON/OFF, HARD/SOFT, EDGE ON/OFF, Undo, Redo, plus sliders for Depth Strength, Left Push Power and Right Pull Power.
- Drag a slider by holding the **right trigger** on its track. Left and right brush effects have separate strength values, each 0.1–3×. Existing left-stick brush-radius adjustment remains.
- **BRUSH ON/OFF now toggles even before a depth map has been generated**. If there is no depth map yet, generate one with Quick Convert before painting.
- Both plus/minus brush cursors remain on the photo only; toolbar interaction will not paint underneath the tools.
- Immersive exit remains **right thumbstick click only**. Home updater, current photo, saved depth map and undo/redo remain available.

## Headset tests
1. Install the signed APK over the existing app; do not uninstall.
2. Open a photograph and turn BRUSH ON in the normal Open Image page; the button must visibly switch to BRUSH OFF after clicking again, even if no Quick depth map is present. Generate Quick depth if needed.
3. Enter Immersive View. Look right for the floating control panel. It must not cover the image.
4. Point the right controller at BRUSH ON/OFF on the floating panel. The toggle must respond to trigger clicks. Check HARD/SOFT, EDGE, 3D and Undo/Redo buttons.
5. Grab the panel by its **MOVE PANEL** header using either grip; move it closer, higher or farther away, then release it. The photograph must not move.
6. Point at the photo with both hands. Both rings should remain visible; left is minus/blue and right is plus/pink. Paint with both hands and confirm depth changes.
7. Move the **LEFT PUSH POWER** slider, then **RIGHT PULL POWER**. Each should change only its corresponding brush.
8. Use the Depth Strength slider to change stereo effect, with the image remaining unobstructed.
9. Check one-/two-grip photo navigation, Undo/Redo and exit via the right thumbstick click.
10. Check that existing imports, updates and saved edits survive restarting the application.

## Current limitations
- The floating panel is a second *flat* OpenXR compositor surface, not a curved UI, and its default position may require turning right.
- It can be translated with a grip, but not independently scaled or rotated in this release.
- Brush power settings are in-memory and reset on reopening the app; corrected depth-map pixels remain saved.
- Edge-aware brush still uses luminance guidance and a 256×256 depth texture; accurate semantic object detection remains future work.
- CI validates Android and native build/signing but cannot reproduce Quest controller targeting or ergonomic comfort.
