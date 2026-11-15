#!/usr/bin/env python3
"""Validate the bundled embedded-buffer glTF test asset before Android builds."""
import base64
import json
import pathlib
import struct
import sys
import io
import zlib

path = pathlib.Path("app/src/main/assets/museum_pedestal.gltf")
with path.open(encoding="utf-8") as stream:
    model = json.load(stream)
assert model["asset"]["version"] == "2.0", "Unsupported glTF version"
assert len(model["buffers"]) == 1, "Expected one embedded buffer"
uri = model["buffers"][0]["uri"]
prefix = "data:application/octet-stream;base64,"
assert uri.startswith(prefix), "External model buffer is not bundled"
data = base64.b64decode(uri[len(prefix):], validate=True)
assert len(data) == model["buffers"][0]["byteLength"], "Buffer length mismatch"
assert len(data) <= 2_000_000, "Quest 2 model exceeds budget"
views = model["bufferViews"]
accessors = model["accessors"]

def accessor(index, kind, component):
    item = accessors[index]
    assert item["type"] == kind and item["componentType"] == component
    view = views[item["bufferView"]]
    offset = view.get("byteOffset", 0) + item.get("byteOffset", 0)
    component_bytes = {5126: 4, 5123: 2}[component]
    components = {"SCALAR": 1, "VEC2": 2, "VEC3": 3}[kind]
    unit = component_bytes * components
    count = item["count"]
    stride = view.get("byteStride", unit)
    assert 0 <= offset < len(data) and 0 < count <= 180000
    assert unit <= stride <= 256, "Invalid stride"
    assert offset + (count - 1) * stride + unit <= len(data), "Accessor overflow"
    return offset, count, stride

triangles = 0
for mesh in model["meshes"]:
    for primitive in mesh["primitives"]:
        assert primitive.get("mode", 4) == 4, "Expected triangles"
        positions = accessor(primitive["attributes"]["POSITION"], "VEC3", 5126)
        uv = accessor(primitive["attributes"]["TEXCOORD_0"], "VEC2", 5126)
        assert uv[1] == positions[1], "UV and position vertex counts differ"
        indices = accessor(primitive["indices"], "SCALAR", 5123)
        assert positions[1] <= 50000
        assert indices[1] % 3 == 0
        for i in range(indices[1]):
            idx = struct.unpack_from("<H", data, indices[0] + i * indices[2])[0]
            assert idx < positions[1], "Triangle index overflow"
        triangles += indices[1] // 3
assert triangles > 0
# Validate every referenced scene node, nested transforms, and mesh reference.
nodes = model.get("nodes", [])
scenes = model.get("scenes", [])
assert scenes and nodes, "Missing scene graph"
roots = scenes[model.get("scene", 0)]["nodes"]
instances = []
def visit(index, stack, depth):
    assert 0 <= index < len(nodes), "Invalid scene node"
    assert index not in stack and depth <= 12, "Cyclic/deep glTF graph"
    node = nodes[index]
    for name, count in (("translation", 3), ("rotation", 4), ("scale", 3),
                        ("matrix", 16)):
        if name in node:
            assert len(node[name]) == count, "Invalid transform size"
            assert all(abs(float(x)) < 1e6 for x in node[name]), "Unsafe transform"
    if "mesh" in node:
        assert 0 <= node["mesh"] < len(model["meshes"]), "Invalid mesh reference"
        instances.append(node["mesh"])
        assert len(instances) <= 96, "Excessive mesh instances"
    for child in node.get("children", []):
        visit(child, stack | {index}, depth + 1)
for root in roots:
    visit(root, set(), 0)
assert len(instances) >= 24 and len(set(instances)) >= 5, (
    "Detailed museum scene assets missing")
expanded_triangles = sum(
    sum(accessors[p["indices"]]["count"] // 3
        for p in model["meshes"][mesh]["primitives"])
    for mesh in instances
)
assert 0 < expanded_triangles <= 60000, "Expanded mesh exceeds VR triangle budget"
images = model["images"]
assert len(images) == 1, "Expected one bounded embedded texture"
texture_uri = images[0]["uri"]
png_prefix = "data:image/png;base64,"
assert texture_uri.startswith(png_prefix), "Texture must be embedded PNG"
png = base64.b64decode(texture_uri[len(png_prefix):], validate=True)
assert len(png) <= 1_000_000, "Embedded texture too large"
assert png[:8] == b"\x89PNG\r\n\x1a\n", "Bad PNG signature"
width, height = struct.unpack_from(">II", png, 16)
assert 0 < width <= 512 and 0 < height <= 512, "Image dimensions out of bounds"
chunks = io.BytesIO(png[8:])
payload = bytearray()
found_end = False
while chunk := chunks.read(4):
    length = struct.unpack(">I", chunk)[0]
    assert length <= 1_000_000, "PNG chunk too large"
    kind = chunks.read(4)
    body = chunks.read(length)
    crc = chunks.read(4)
    assert len(body) == length and len(crc) == 4
    assert zlib.crc32(kind + body) == struct.unpack(">I", crc)[0], "Invalid PNG CRC"
    if kind == b"IDAT":
        payload.extend(body)
    if kind == b"IEND":
        found_end = True
        break
assert found_end and payload, "PNG missing image data or end marker"
assert len(zlib.decompress(payload)) > 0, "PNG data could not decompress"
print(f"glTF validated: {triangles} source / {expanded_triangles} placed triangles, {len(instances)} instances, "
      f"{len(data)} mesh bytes; {width}x{height} PNG")

