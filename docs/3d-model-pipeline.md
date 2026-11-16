# Android VR Collage — 3D asset pipeline (first milestone)

The six environments retain their original shader-driven backgrounds. This pipeline introduces a
**real, indexed 3D mesh from a bundled glTF 2.0 asset** and renders it using a
separate OpenGL ES 3.0 vertex/fragment pass after the cylinder panorama.

## v0.15.0: Six modeled environments

The 3D mesh pass now supports all six gallery themes through one bounded,
offline glTF loader and one renderer:

| Theme | Bundled asset | Mesh types | Placed objects | Placed triangles |
|---|---|---:|---:|---:|
| Museum | `museum_pedestal.gltf` | 5 | 25 | 6,464 |
| Nightclub | `club_environment.gltf` | 3 | 15 | 3,744 |
| Tiki Party | `tiki_environment.gltf` | 3 | 15 | 2,176 |
| Fantasy Fire | `fire_environment.gltf` | 3 | 15 | 1,964 |
| Dungeon | `dungeon_environment.gltf` | 3 | 15 | 1,464 |
| Woodland | `woodland_environment.gltf` | 3 | 15 | 7,528 |

Each has unique authored geometry and its own embedded, bounded PNG image.
Geometry is placed using glTF scene nodes. The existing cylindrical photo
surface, themes, saved galleries and 'Detailed 3D Scenery' switch remain intact.

**Distance-detail policy:** Every vertex carries the position of its
architectural part and a detail threshold. Principal structures and centerpiece
meshes remain visible at any gallery distance. Optional second-mesh decorative
details are clipped from rendering when the viewpoint moves more than 3.35
meters from that part. This reduces distant fragment workload without extra
asset loads or rebuilds. The vertex processing still occurs, so this is
**GPU raster-detail culling**, not full mesh-substitution LOD or CPU draw
culling. It is the first Quest-oriented detail step; complete LOD will use
lower-poly substitute geometry and per-object GPU batching.

Six-scene asset validation now runs before Android builds, together with
a native/Java JNI contract check that also enforces the 15-float vertex
stride. Failed asset loads still fall back to the existing panorama shaders.

## v0.15.1 — Realism shader foundation

The mesh shader now uses a lightweight GGX microfacet model with Schlick
Fresnel, a metallic/roughness material response, gamma-aware colors and
emissive material contributions. These are much more plausible material
responses than the old diffuse-only lighting, but **not full glTF PBR**.
The two existing directional lights remain the lighting sources; there is
not yet image-based lighting, baked GI, physically accurate shadows or HDR.

glTF `metallicFactor`, `roughnessFactor` and `emissiveFactor` travel
alongside vertex colors, UV coordinates, normals and LOD data in a bounded
20-float vertex layout. Additional optional embedded PNG slots use the
first material's texture references: tangent-space normal, combined ORM
(occlusion R, roughness G, metallic B) and emissive. Missing maps safely
use material factors. The Museum contains two new **small procedural
test maps** for normal detail and ORM so we can test the path on Quest 2.

**Important limitation:** this first pass uses one shared optional map
set per *scene*, taken from the first material; it does **not** yet switch
texture sets per primitive. That will be necessary for convincing wood,
stone and metallic objects within the same room. Current sample assets
remain early models, not photorealistic scene assets. The next museum
art-quality pass needs separately authored 512px textures, material-specific
draw ranges, tangent corrections, baked occlusion and lighting adjustments.
Keep GPU image dimensions <=512x512 per map and test frame rates on Quest 2.

## Current museum scene
- `app/src/main/assets/museum_pedestal.gltf`
- A modular museum scene with fluted limestone columns and separate gold cornice geometry.
- v0.13.0 introduced multiple meshes, reusable nodes and nested transforms.
- v0.14.0 expands the museum to five reusable mesh definitions placed in 25 scene nodes: columns, cornices, carved arches, wooden benches, and a central marble-like sculpture. UVs and the existing bounded texture are retained.
- v0.14.0 adds world-space per-triangle normals and low-cost two-direction warm lighting in the OpenGL ES mesh shader. It is not physically based rendering.
- Position, UV, and index geometry is interpreted from an embedded base64 glTF buffer.
- v0.12.1 adds a real embedded PNG stone texture and per-vertex TEXCOORD_0 UVs; the optional OpenGL ES 3 renderer uploads and samples the decoded image.
- The loading code is `GltfMesh.java`; the optional GL mesh renderer is in
  `vr_ui.h` and is invoked per eye in `vr_native.cpp`.
- Existing saved cylinders, imported images, model selection and gallery controls
  are unchanged. The asset is drawn only in the Museum theme when Detailed 3D
  Scenery is enabled.

## Supported glTF subset (intentional)
- glTF 2.0 JSON with **one embedded data URI buffer**.
- `TRIANGLES` primitives; `POSITION` float32 VEC3 and 16-bit unsigned indices.
- `pbrMetallicRoughness.baseColorFactor` is interpreted as the material tint.
- A single bounded embedded `data:image/png;base64` PNG (up to 512×512) is decoded through Android BitmapFactory and uploaded as a GL texture.
- Float32 `TEXCOORD_0` VEC2 per-vertex UV coordinates are supported; material-specific images and full PBR remain future milestones.
- Scenes use Y-up positions in meters. Nested glTF nodes support `matrix` or translation/rotation/scale (TRS), baked into the bounded vertex stream.
- Total decoded buffer <=2 MB; bounded index and vertex counts.
- No external files, arbitrary network loads, animation, skinning, multiple
  textures, .glb, or full PBR yet.

Scene transforms and mesh reuse still feed a single flattened GPU vertex stream; per-instance GPU draw batching and collision are separate future milestones.

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
1. Add a bounded .glb reader, interleaved and additional accessor formats, and scene graph test assets.
2. Support multiple material textures and normal/roughness maps with total GPU texture budgets.
3. Replace initial themed environment geometry with richer artist-authored Blender assets and theme-specific textures.
4. Add mesh instancing / batching and distance-based detail switching.
5. Add robust asset-version manifests and offline project references.
6. Tune distance and geometric LOD using Quest 2 headset performance measurements.

## Testing
Open VR home; enter Gallery; open a museum cylinder with Detailed 3D Scenery
enabled. The Museum should show fluted columns, gold trim, arched gallery bays,
wood benches and a central carved display. Look for directional shading on
the curved arch and sculpture surfaces; all geometry should remain stable
when turning or approaching it. Turn Detailed 3D Scenery off and reopen to confirm the
unchanged shader-only fallback. Reopen a saved cylinder and compare both eyes.
