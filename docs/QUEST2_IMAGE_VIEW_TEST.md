# v0.3.0 — Import and view a single local image in Quest 2

**Scope:** First image viewer prototype; no 2D-to-3D conversion yet.

## Using the test build

1. Install the stable v0.3.0 APK over your existing release (do not uninstall first).
2. Open **Android Image Editor VR** in the Quest headset.
3. Aim the right controller at **Open Image** and squeeze the right trigger.
4. On the Open Image screen, select **Import Image**. Android's system file picker should appear. Pick a JPG or PNG in Quest storage.
5. When you return to VR, the photograph should display as a large, properly proportioned panel in the dark environment, at about 1.9 metres in front of you.
6. Turn your head: the panel should stay positioned in VR space. The image itself is **flat 2D** during this milestone.
7. Select Import Image again to replace the currently previewed picture. Select Back to return home, and Open Image to view the last imported image.
8. Restart the app while offline: imported image should remain available.
9. Open the separate regular **Android Image Editor** launcher to verify manual/automatic GitHub updates still function.

## Technical scope / safeguards

- Android Storage Access Framework `ACTION_OPEN_DOCUMENT` selects one local image. No broad storage permission needed.
- The imported image is copied into app-private storage; the original remains untouched and the viewer works offline.
- Display copy is capped to a maximum 2048 pixels on either edge to respect Quest 2 memory. **This is not yet a non-destructive project store:** imported images replace the previous display image, and large sources are downsampled for preview.
- PNG/JPEG support is handled by Android's platform image decoder; non-decodable images produce an error message.
- A locally compiled OpenGL ES texture is rendered on the existing OpenXR world-anchored quad. The Quest 2 has not yet been physically tested by this development environment.
- Source app identifier, permanent signing certificate, releases, and updater are unchanged.

## Limitations / next build

No 3D conversion, depth map, free zoom, grasp controls, file browser thumbnails, SMB import, project gallery, or saved VR-native output yet. Those require separate staged development and headset tests.

Report whether the file picker appears, returns to immersive VR, shows the image upright with correct proportions, and whether back navigation and updater still work.
