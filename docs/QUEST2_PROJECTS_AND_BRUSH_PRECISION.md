# Quest 2 v0.6.0 — Brush precision + multi-project library

Combines the previously planned v0.5.5 and v0.5.6 milestones with the reported left-brush/brush-size fixes.

## What's new

- Each hand has **separate size, strength, and gradual hardness** controls on the independent floating panel in immersive mode. Radius spans 3–60 pixels in the current 256×256 depth map. Hardness ranges from a fully feathered edge to a solid disk. The older HARD/SOFT button acts as a quick preset for *both* brushes.
- Floating panel has three rows of two sliders (left and right power, size, and hardness) plus the stereo-depth slider, all with clear labels and numeric brush-radius readout. Gripping the panel header with **both hands** changes its size (0.5–2x); one grip moves it. The panel position and size are saved between app restarts.
- Stroke interpolation fills gaps when moving a brush quickly over the photograph. The edge-aware luminance suppression from previous builds remains available.
- **Left controller cursor:** the blue minus brush ring is rendered whenever Brush Mode is active and the left controller's ray reaches the photo, including before a depth map exists. A secondary tracked left-grip pose is used if the native left aim pose is inactive. The right cursor remains a plus. Both are independent.
- **Project saving and management:** Open Image has **SAVE PROJECT** and **SAVE AS NEW**. Each saved project has its own local photograph and corrected depth map. **MY PROJECTS** on the VR home screen lists four saved projects per page; select one to reopen it with its depth changes. New imports do not overwrite previously saved project directories.
- Existing VR Check for Updates, default editor, Quick Convert, immersive editing, thumbstick-only immersive exit and signing identity remain unchanged.

## Test

1. Update in place from v0.5.4 to v0.6.0. Do not uninstall; existing imported photos and current depth map are retained.
2. Open a photo, enable Brush Mode. Point **both** controllers at the image without pressing either trigger. Confirm a **left blue circle/minus** and **right pink circle/plus** appear simultaneously. If the left cursor remains missing, note whether the left controller is visibly tracked elsewhere on the Quest.
3. Generate a Quick depth map if needed. Paint using each trigger. Rapidly move both controllers across an edge and check for gaps or jagged marks.
4. Enter Immersive View and turn toward the floating tool panel. Move the panel using one grip on MOVE PANEL; grab with both controllers to resize. Exit and reopen the app: layout should be retained.
5. Adjust the left and right **size sliders** separately, watching the ring widths change. Compare independent hardness, strength and edge awareness. The left thumbstick still changes the left brush's radius.
6. In Open Image, choose **SAVE PROJECT**. Make another correction and save again. Return to home, open **MY PROJECTS**, and select the saved entry. Verify the photograph and latest depth map reload together.
7. Import a different photograph, Quick Convert and save it as a new project. Reopen the first saved project from MY PROJECTS; its photo and depth must be independent of the second.
8. Choose **SAVE AS NEW** while editing a saved project; My Projects should show a second entry. Verify both entries remain loadable after app restart.
9. Check project list pagination when there are more than four projects, the original importer, Undo/Redo, Check for Updates, the VR stereo effect and controller gestures.

## Scope and limits

- Project snapshots store the **preview copy of the photograph** (up to 2048 pixels on its longest side) and the edited **256×256 depth-map PNG**. They do not yet include full original-resolution files, editable versions, a project rename/delete workflow, or export.
- Brush settings and Undo/Redo history are session-local, not currently saved per project. Corrected depth-map pixels *are* persisted in each saved project.
- The photo editor still uses a working-copy model. Save before replacing an unsaved image or switching projects.
- The tracked left-grip pose fallback can have a different ray direction than the left aim pose; confirm it feels accurate on the actual Quest 2.
- The Quick Convert depth map remains a local heuristic, not a semantic/neural depth estimator.
- CI validates build and signing, not physical controller tracking, ergonomics, persistence across device power loss or stereo comfort.
