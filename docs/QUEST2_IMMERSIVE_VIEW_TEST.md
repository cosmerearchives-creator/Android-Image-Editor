# v0.4.1 Quest 2 immersive photograph viewer — on-device test

## Why this build exists

Earlier builds displayed images in a relatively small editing panel, making it hard to judge image depth and comfort. The new **Immersive** button opens an isolated, much larger, world-anchored photo viewer with the menu hidden. This is intentionally a wide planar photograph, **not** a 360-degree panorama, and it uses the existing experimental stereo effect (not an AI depth map).

## Test

1. Install v0.4.1 over the current signed build with Check for Updates. Keep existing app data.
2. Launch Android Image Editor VR, choose Open Image, import a photo or use the one stored previously.
3. Choose **IMMERSIVE** beside **IMPORT**.
4. The app should hide all controls and show only the photograph on a significantly larger virtual canvas. The default physical quad is about 5.37 m wide and 4 m tall at 1.9 m distance, so turning your head should reveal image regions outside the immediate field of view. The original photo's aspect ratio remains intact (letterboxing when needed).
5. Compare 2D and experimental stereo **without leaving the viewer**: press **right A** to toggle 3D ON/OFF (the chosen stereo strength is retained from the editor). Check whether the larger view helps you judge depth. Turn off 3D immediately if uncomfortable.
6. Push the right thumbstick up/down for 1×–12× magnification. Point the right controller at an area to bias zoom toward it; no cursor is displayed in immersive mode.
7. Move the photo using either grip; hold both grips to scale or rotate it, then release to anchor the canvas.
8. Press **right B** to return to Open Image with its normal-sized editing panel. The right trigger also exits immersive viewing as a fallback. Your previous editor view position/zoom should be restored.
9. Repeat with portrait and landscape images. Ensure importing, Back, Reset View, and the updater continue working after exiting immersive mode.
10. Restart the app offline and confirm the previously imported photo remains available.

## Bounds

- Immersive view does **not** stretch or permanently resize photo pixels, modify imported images, generate new depth maps, or save a 3D export.
- It is a spatial **flat rectangular canvas**, large enough to look around by turning your head, with the earlier per-eye stereo shader. The content does not wrap all the way around the viewer.
- The original image can be a maximum 2048 pixels per edge in the existing preview pipeline, and the current per-eye compositing resolution remains 1024×768; zoom may expose pixelation. High-resolution tiles are future work.
- Quest device behavior and comfort require on-device validation even when CI and signing pass.
