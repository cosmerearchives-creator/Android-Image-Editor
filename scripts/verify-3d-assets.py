#!/usr/bin/env python3
"""Validate the bundled embedded-buffer glTF test asset before Android builds."""
import base64
import json
import pathlib
import struct
import sys

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
    unit = (12 if component == 5126 else 2)
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
        indices = accessor(primitive["indices"], "SCALAR", 5123)
        assert positions[1] <= 50000
        assert indices[1] % 3 == 0
        for i in range(indices[1]):
            idx = struct.unpack_from("<H", data, indices[0] + i * indices[2])[0]
            assert idx < positions[1], "Triangle index overflow"
        triangles += indices[1] // 3
assert triangles > 0
print(f"glTF validated: {triangles} triangles; {len(data)} bytes")
