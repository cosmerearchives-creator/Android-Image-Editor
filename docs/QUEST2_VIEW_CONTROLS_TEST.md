# Quest 2 image viewing controls — v0.3.1

This version builds on the tested local image importer and adds an incremental subset of the agreed VR navigation controls. It does **not** perform stereoscopic image conversion.

## Test on Quest 2

1. Update from v0.3.0 to v0.3.1 using Check for Updates (or install the signed release APK over the existing installation). Do not uninstall first.
2. Open **Android Image Editor VR** > **Open Image** and import or revisit a picture.
3. Point your **right controller** at a distinctive detail in the picture. Push the **right thumbstick up** to zoom in, and down to zoom out. The targeted detail should stay approximately under your pointer, with a 12x upper limit and 1x lower limit.
4. Hold the **right grip** and move the controller in space. The floating panel should follow your controller's translation; release the grip to leave it at that position.
5. Verify that pulling the right trigger can still select **Import Image**, **Reset View**, and **Back**, and that gripping does not activate menu buttons.
6. Select **Reset View** on the Open Image page. Photo magnification should reset to 1x, and the panel should move to its initial location about 1.9m ahead.
7. Restart the app and verify the locally stored image still loads. Check the original image file remains unmodified and the regular updater launcher still operates.

## Boundaries and next steps

- Zoom applies to the photo contents within the existing viewer panel; it is an initial magnification tool, **not yet a wall-sized immersive canvas**.
- The right grip currently translates the entire panel, and cannot yet rotate or resize it. Two-grip gestures, left-controller interactions and separate full immersive gallery mode follow in later builds.
- Preview moves and magnification do not modify the imported image or its stored pixels.
- Full 2D-to-3D depth-map conversion, sculpting, gallery/project folders, and SMB import have not been implemented.
- UI controls still require physical Quest 2 validation, even though Android/NDK CI and release signing pass.
