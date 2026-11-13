# v0.5.1 — First manual VR depth brush

This build adds the first **real depth-map correction brush** to Android Image Editor for Quest 2. The existing offline Quick Convert generates a 256×256 depth map, and this build edits that map **without modifying the photograph**.

## Test instructions

1. Update in place to v0.5.1 through the existing Check for Updates workflow. Existing image/depth files are preserved.
2. Open a photograph. If you haven't generated a depth map for this image, tap **QUICK** and allow it to finish.
3. Tap **BRUSH OFF** (the fourth button beside Import / Immersive / Quick). It should switch to **BRUSH ON**. The image title area displays L PUSH R PULL and the current brush size.
4. Point the **right controller** at a detail in the photo and hold the **right trigger** to increase its depth (pull it forward).
5. Point the **left controller** at a detail and hold the **left trigger** to decrease its depth (push it backward).
6. The cursor ring should show the brush footprint. It turns **pink when pulling** and **blue when pushing** while the applicable controller's trigger is held. Release a trigger to finish a stroke.
7. Push the **left thumbstick up/down** to change the shared brush radius (3–60 depth pixels). The brush uses a soft edge; hard-edge and individual-hand controls are future features.
8. With 3D ON, compare the effect in the editor and press **IMMERSIVE** to see it on the larger canvas. Painting occurs only in Open Image, **not in Immersive View**.
9. Switch BRUSH OFF to navigate normally. Grip-to-move and two-grip resize/rotation must not paint while a grip is held.
10. Close and reopen the app. Your depth corrections should be loaded from the saved PNG.
11. Import a different image. The prior depth map must be invalidated; use QUICK again before painting the new image. Check that the updater still runs.

## Boundaries

- Manual depth edits are a single grayscale 256×256 image in app-private storage, not edits to the original photo.
- The user must first generate a Quick depth map. No automatic AI semantic segmentation or object detection yet.
- The stereo shader uses the edited texture for both eye views; the flat 2D switch remains unchanged.
- This iteration does **not** implement brush Undo/Redo, edge awareness, independent brush settings, separate projects, or high-quality depth estimation. Those are subsequent milestones.
- Saving happens off the OpenXR render thread at the end of each brush stroke. If the headset or app terminates during an unfinished stroke, that most recent unfinished work may be lost.
- Build/signing CI doesn't guarantee on-device controller mapping or paint ergonomics: report whether both hand triggers work, whether saved corrections survive relaunch, and whether depth direction is intuitive.
