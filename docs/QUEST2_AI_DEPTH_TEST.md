# Quest 2 v0.7.0 — Experimental AI depth conversion prototype

This milestone adds **Depth Anything V2 Small quantized ONNX** for actual, on-device monocular depth inference. It is **not** the previous brightness-only heuristic. It preserves the existing Quick Convert fallback and requires explicit confirmation before replacing a corrected depth map.

## Model and trust

- Source: `onnx-community/depth-anything-v2-small` on Hugging Face, immutable commit `4472b7362082ad9968fee890ca0f1e5aca36b93d`.
- Model: `onnx/model_quantized.onnx`, approximately 27.3 MB, SHA-256 `fcf51f1b230362b28690bb9d1809bf0431f29cad20534e3f589bd7285547f20d`.
- License: Apache-2.0. The app downloads the model **once when START AI is pressed**, stores it in app-private storage, checks its SHA-256 and uses it offline thereafter.
- Inference: ONNX Runtime Android CPU provider, two threads, fixed 518×518 float input. Output is normalized into a 256×256 depth-map preview for the existing stereo brush/render pipeline. The source photo never leaves the device.
- The first run needs internet for the model download. Subsequent runs can operate offline. The APK does not bundle model weights.

## On-headset test

1. Update in place from v0.6.0 to v0.7.0. Do not uninstall, preserving project data.
2. Open a photo. Verify Quick Convert and normal manual brushing remain functional.
3. On Open Image select **AI DEPTH**. Select **START AI**. The app should report model download progress and then an inference state; allow time for the first conversion.
4. While it runs, try **CANCEL**. Existing depth must remain unchanged. Restart conversion; it should reuse any fully verified previously downloaded model.
5. When **AI PREVIEW READY** appears, inspect the generated depth in the editor image. The previous saved depth map should not yet be replaced. Painting in the preview state is disabled to avoid accidentally writing over it.
6. Select **DISCARD**. Verify the prior Quick/manual corrected depth returns.
7. Repeat START AI, then select **APPLY**. Confirm the new AI depth becomes the active editable depth map; proceed to immersive view and try per-hand corrections.
8. Save as a project, switch to a different project, and reopen. Confirm each project loads its own image/depth. Run the conversion offline a second time.
9. Confirm the VR updater still works and immersive painting/plus/minus cursors, floating controls, left/right size/strength sliders, and thumbstick-only immersive exit remain intact.

## Honest limitations / follow-up

- This is the **first experimental neural depth inference path**. CI can validate compilation and signing, but **not** the Quest's memory use, runtime tensor shape, model operator support, inference duration, image quality, or comfort. Runtime failures are reported and Quick Convert remains available.
- The current model preprocessing resizes to a square 518×518 image. Future refinements should preserve aspect ratio and benchmark lower-input resolutions, model alternatives, and output orientation.
- Although model inference is on-device, network access is required for the initial model download.
- Progress is detailed during download; neural inference currently shows a stage indicator rather than per-layer percentage.
- Depth maps remain at 256×256; higher-resolution editable depth and model quality benchmarking are future steps. The AI image itself does not get permanently modified until Apply.
