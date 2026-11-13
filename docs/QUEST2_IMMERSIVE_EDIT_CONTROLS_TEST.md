# Quest 2 v0.5.3 — Immersive dual-brush editing, strength controls and anchored canvas navigation

## Changes

- Both controller pointers render **at the same time**, even while both are painting.
- **Left hand:** blue-outlined circle and **minus** marker; left trigger pushes depth backward.
- **Right hand:** pink-outlined circle and **plus** marker; right trigger pulls depth toward you.
- The manual depth brush works on **Open Image and Immersive View**. The wide immersive canvas still keeps the imported image untouched; changes are saved to the 256×256 depth texture at stroke completion.
- **Immersive toolbar:** 3D ON/OFF, BRUSH ON/OFF, HARD/SOFT edge, EDGE ON/OFF, UNDO and REDO. **Bottom slider tracks:** stereo depth strength (0–200% normalized) and brush strength (0.1–3×). Aim right controller and squeeze/drag along each slider.
- The same strength sliders are available on Open Image, together with its existing brush-size left-stick gesture.
- **Immersive exit:** **right thumbstick click only**. B and trigger no longer exit; A still toggles 2D/3D as a shortcut.
- Home/placeholder views support one-grip positioning, both-grip scaling/planar rotation, and thumbstick resizing. Two-grip scaling in any view preserves the point you were pointing/grabbing rather than scaling outward only from the panel center.
- **Edge-aware brush** (ON by default) suppresses edits across strong luminance differences; this is an approximation, not semantic object segmentation. HARD is a flat disk and SOFT gives a feathered boundary.
- Existing updater, imported images and saved depth maps remain supported.

## On-device test sequence

1. Update from v0.5.2 to v0.5.3 in-place. Do not uninstall.
2. On the VR home screen, grab the panel with either grip, then both grips, and scale/rotate. Use right thumbstick up/down to resize it while pointing at a button. Verify buttons remain selectable after repositioning.
3. Open an image with a Quick depth map and switch BRUSH ON.
4. Point **both** hands at two different areas simultaneously. Verify **both rings remain visible**; left ring has a horizontal **minus**, right ring a **plus**. Hold left/right triggers together, then release.
5. Enter IMMERSIVE. Both brush rings should persist on the giant photo. Paint with left and right triggers directly in immersive mode.
6. Use the **HARD/SOFT** and **EDGE ON/OFF** toggles on the immersive toolbar to compare a crisp brush to feathered and edge-protected strokes.
7. Drag the **DEPTH** slider and the **BRUSH POWER** slider with the right trigger. Depth should control stereo intensity; brush power should control editing speed. Ensure pointing/dragging on the toolbar does **not** paint underneath it.
8. Check immersive Undo/Redo; exit only by clicking the **right thumbstick**. Press B and the right trigger when away from the toolbar: neither should exit.
9. Try resizing with **both grips** while aiming at a specific part of the photo. That detail should remain roughly anchored under your hands instead of flying away from the center.
10. Restart to confirm depth corrections remain saved. Verify check-for-updates, import, and 2D/3D still function.

## Known limitations

- Brush hardness is currently a two-state toggle and both brushes share size/power/settings. Independent left/right settings and continuous hardness sliders remain future work.
- The current 256×256 map and approximate edge-aware luminance guidance can cause coarse object boundaries. High-quality neural depth inference is not included.
- Immersive controls are intentionally displayed as slim bars at the top/bottom; these parts of the photo are not paintable until the UI can be hidden.
- The larger stereo image is still on a flat rectangular OpenXR compositor quad. Head-motion-based geometry is not yet implemented.
- Build CI cannot verify real headset gestures or stereoscopic comfort; physical Quest 2 testing is required.
