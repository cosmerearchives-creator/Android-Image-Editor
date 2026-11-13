# v0.5.0 — First Quick Convert depth-map prototype

The main development goal of this release is a **generated, persisted depth map**. This is not yet a neural-network / AI depth estimator. The quick prototype uses an offline 256×256 depth calculation combining luminance, centered subject bias, a vertical bias and local edge-aware smoothing. It cannot truly know which objects are nearer or farther away and may get individual objects wrong.

## Test it on Quest 2

1. Update from v0.4.1 to v0.5.0 with the existing updater, or install the signed v0.5.0 APK over the prior build.
2. Open a photo in **Open Image**. The bottom caption initially shows HEURISTIC DEPTH unless a map for that exact imported photo has previously been generated.
3. Select **QUICK** (next to IMPORT and IMMERSIVE) once. Conversion is on-device/offline, and should not freeze headset input. After the map is stored and loaded, the caption reads QUICK DEPTH MAP.
4. Toggle **3D ON** and try DEPTH LOW/MID/HIGH. The renderer now uses the stored Quick depth texture instead of calculating per-pixel depth from the old shader heuristic.
5. Enter **IMMERSIVE**. The same generated map should be used to compare 2D/3D with A, using the large photo-only canvas.
6. Close and reopen the app: the converted map should remain available, including when offline.
7. Import a **different** photo. The old depth map must be invalidated. The caption should revert to HEURISTIC DEPTH until QUICK is pressed again; the old photo's depth must never be used for the new photo.
8. Verify the usual controls still work: import, thumbstick zoom, either-grip move, both-grips resize/rotate, immersive A/B, Back and Check for Updates.

## Implementation and limitations

- The generated map is 256×256, stored in private app storage as a grayscale PNG. The image preview remains unchanged.
- Stereo preview uses a depth sampler uploaded to a GL_R8 texture for both eyes. The 3D strength and switch to flat mode remain reversible.
- Quick conversion is processed in a Java worker thread; the XR graphics thread only loads the saved result.
- The source image import pipeline still retains only **one current preview image** at up to 2048px. Projects, multiple-image management, SMB, manual depth painting and export are future milestones.
- **High Quality Convert is not part of v0.5.0.** It will require a feasible, licensed local depth-estimation model benchmarked on the Quest 2.
- A compile pass does not guarantee the depth is convincing or comfortable; test against varied photographs. If the picture appears doubled or uncomfortable, switch 3D OFF.

## Feedback useful for development

Tell us whether QUICK finishes, its result persists after restarting, the large viewer looks different from the previous heuristic, and whether the depth looks natural or has obviously reversed foreground/background objects.
