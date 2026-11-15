# Android VR Collage — 3D asset pipeline (first milestone)

The original six environments remain shader-driven. This milestone introduces a
**real, indexed 3D mesh from a bundled glTF 2.0 asset** and renders it using a
separate OpenGL ES 3.0 vertex/fragment pass after the cylinder panorama.

## Current proof asset
- `app/src/main/assets/museum_pedestal.gltf`
- A fluted limestone museum pedestal/column with material-colored surfaces.
- Position and index geometry is interpreted from an embedded base64 glTF buffer.
- The loading code is `GltfMesh.java`; the optional GL mesh renderer is in
  `vr_ui.h` and is invoked per eye in `vr_native.cpp`.
- Existing saved cylinders, imported images, model selection and gallery controls
  are unchanged. The asset is drawn only in the Museum theme when Detailed 3D
  Scenery is enabled.

## Supported glTF subset (intentional)
- glTF 2.0 JSON with **one embedded data URI buffer**.
- `TRIANGLES` primitives; `POSITION` float32 VEC3 and 16-bit unsigned indices.
- `pbrMetallicRoughness.baseColorFactor` is interpreted as a simple color.
- Fixed vertex positions in meters with Y up and forward along -Z.
- Total decoded buffer <=2 MB; bounded index and vertex counts.
- No external files, arbitrary network loads, animation, skinning, textures,
  scene-node transforms, .glb, or full PBR yet.

The limited subset is a **pipeline validation**, not a declaration that other
glTF features are unsupported forever.

## Safety and performance
- Mesh loading is lazy on first immersive cylinder entry.
- A missing/malformed glTF or shader failure falls back to the original
  cylindrical renderer without interrupting VR startup.
- Per-eye depth buffer is allocated lazily, only after the mesh upload succeeds.
- Detailed 3D Scenery turns off mesh drawing as well as shader decorations.
- CI runs `python3 scripts/verify-3d-assets.py` before building Android.

## Next implementation milestones
1. Extend the parser to a validated general glTF 2.0 scene graph / .glb.
2. Add GPU UV texture and material support, bounded per-asset texture memory.
3. Author detailed museum mesh sets externally in Blender or a compatible tool.
4. Add mesh instancing / batching and distance-based detail switching.
5. Add robust asset-version manifests and offline project references.
6. Extend the asset library to the other five environments.

## Testing
Open VR home; enter Gallery; open a museum cylinder with Detailed 3D Scenery
enabled. The imported mesh should appear as a small fluted stone/gold pedestal
in front of the wall. Turn Detailed 3D Scenery off and reopen to confirm the
unchanged shader-only fallback. Reopen a saved cylinder and compare both eyes.
