# Quest 2 v0.4.0 — Experimental stereoscopic depth preview

This release is a **stereo-rendering proof of concept**, not object-aware AI depth estimation. It creates slightly different left/right image samples locally in an OpenGL ES shader using a rough brightness-and-center depth heuristic. Objects with similar brightness may be assigned incorrect depth; hair, transparent objects, edges and occluded backgrounds will be inaccurate.

## Try it

1. Update v0.3.2 to v0.4.0 through the existing updater, or install the signed APK over the previous version.
2. Open **Android Image Editor VR** > **Open Image**, and import a photograph or reuse your current image.
3. Select **3D OFF** to turn on the stereo preview. The button changes to **3D ON**.
4. Select **DEPTH MID** to cycle **DEPTH HIGH**, **DEPTH LOW**, then **DEPTH MID**.
5. Toggle **3D ON** back off to compare with the original flat view.
6. Try a few types of images (portraits, landscapes, clear foreground objects) and note which depth levels are comfortable or exhibit double images.
7. Verify existing controls: right thumbstick photo zoom, either grip panel movement, both grips scale/planar rotate, Reset View, Back, and Import Image.
8. Verify the app restarts and restores the imported image, and the separate updater activity still checks for releases.

## Important limitations

- Both eyes receive distinct 1024×768 images via eye-specific OpenXR quad layers and separate subimage rectangles in one wide swapchain.
- The input image remains unchanged; parallax preview is computed during rendering.
- No real monocular depth neural network, semantic object understanding, manual depth painting, saved depth map, image completion, head-parallax geometry or native 3D export is included.
- On-device testing is essential: a passing Android/NDK build and signed release do not guarantee that the Quest runtime accepts the two quad layers or that the apparent depth is comfortable.
- If the stereo effect causes eyestrain, turn **3D OFF** immediately; the intended behavior is that both eyes return to the original unwarped photo.

This prototype is groundwork for a later Quick/High-Quality depth conversion pipeline and hand-painted corrections.
