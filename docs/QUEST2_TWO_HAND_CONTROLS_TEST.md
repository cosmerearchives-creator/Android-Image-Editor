# Quest 2 v0.3.2 — Two-hand canvas resize and rotation

This is an incremental spatial-viewer update. It does not perform depth conversion.

## Controller controls

- Hold **either one grip** to move the photo panel. Release to anchor its position.
- Hold **both grips** and move controllers apart/together to enlarge/shrink the panel (0.5–4x panel scale).
- While both grips are held, rotate the line between the controllers in the plane of the panel to rotate the canvas. Rotation is deliberately limited to the panel plane (roll): full 3D orientation is not yet implemented.
- Right thumbstick up/down continues to zoom into the photo detail within the canvas (1–12x); controller pointer zoom behavior remains.
- The Reset View button restores panel position, panel scale and orientation, and image zoom.
- Grip interaction disables triggering menu buttons until released.

## Quest test checklist

1. Update v0.3.1 to v0.3.2 without uninstalling, preserving the imported photo.
2. In **Open Image**, point at an imported photo and use the right thumbstick to zoom.
3. Hold **left grip only** and move that controller; panel should follow and stop when released.
4. Hold **right grip only**, move the panel, and release.
5. Hold **both grips** and spread your hands: panel should enlarge. Bring them together: it should shrink; panel size is clamped.
6. With both grips held, turn the line connecting your hands clockwise/counterclockwise: the panel should rotate without losing the image.
7. Release either grip while holding the other; the interaction should seamlessly switch to translation without sudden jumps.
8. Test the trigger aiming and buttons after moving and rotating the panel.
9. Select Reset View and confirm it restores defaults. Restart and test normal image import and the updater.

## Current limitations

The rendered canvas is still the same OpenXR compositor quad and remains a 2D photo; two grips change the quad's spatial scale and planar orientation, not its original pixels. No photo depth maps, stereo conversion, project system, SMB integration or independent gallery yet. Quest 2 interaction/comfort must be verified on device even when CI passes.
