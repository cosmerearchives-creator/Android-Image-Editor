#!/usr/bin/env python3
"""Validate independently authored, textured glTF chair imported from Khronos."""
import base64
import json
from pathlib import Path

path=Path("app/src/main/assets/real_chair_damask.gltf")
doc=json.loads(path.read_text(encoding="utf-8"))
assert doc["asset"]["version"]=="2.0"
source=doc["extras"]["importProvenance"]
assert source["license"]=="CC BY 4.0" and source["author"]=="Eric Chadwick"
assert "ChairDamaskPurplegold" in source["source"]
assert doc.get("scene",0)==0 and len(doc["scenes"])==1
assert len(doc["meshes"])==11 and len(doc["materials"])==4
assert doc["extras"]["materialAtlas"]["materialSlots"]==[0,1,2,3]
assert len(doc["images"])==9
assert len(doc["buffers"])==1
uri=doc["buffers"][0]["uri"]
assert uri.startswith("data:application/octet-stream;base64,")
data=base64.b64decode(uri.split(",",1)[1],validate=True)
assert len(data)==doc["buffers"][0]["byteLength"]==310904
assert len(data)<=2_000_000
for image in doc["images"]:
    uri=image["uri"]
    assert uri.startswith("data:image/jpeg;base64,")
    raw=base64.b64decode(uri.split(",",1)[1],validate=True)
    assert raw.startswith(bytes((255,216,255))) and raw.endswith(bytes((255,217)))
    assert len(raw)<=1_000_000
views=doc["bufferViews"]
accessors=doc["accessors"]
for item in accessors:
    view=views[item["bufferView"]]
    units={"SCALAR":1,"VEC2":2,"VEC3":3,"VEC4":4}[item["type"]]
    size=units*{5123:2,5126:4}[item["componentType"]]
    start=view.get("byteOffset",0)+item.get("byteOffset",0)
    end=start+(item["count"]-1)*view.get("byteStride",size)+size
    assert 0<=start<end<=len(data)
    assert end<=view.get("byteOffset",0)+view["byteLength"]
import struct
triangles=0
for mesh in doc["meshes"]:
    for primitive in mesh["primitives"]:
        assert primitive.get("mode",4)==4
        assert 0<=primitive["material"]<len(doc["materials"])
        attributes=primitive["attributes"]
        pos=accessors[attributes["POSITION"]]
        uv=accessors[attributes["TEXCOORD_0"]]
        assert pos["count"]==uv["count"] and pos["count"]>0
        ids=accessors[primitive["indices"]]
        assert ids["count"]%3==0
        view=views[ids["bufferView"]]
        off=view.get("byteOffset",0)+ids.get("byteOffset",0)
        for n in range(ids["count"]):
            idx=struct.unpack_from("<H",data,off+n*2)[0]
            assert idx<pos["count"], "Invalid imported chair index"
        triangles+=ids["count"]//3
assert 5000<=triangles<=20000, "Unexpected professional chair geometry"
print(f"Imported chair validated: {len(doc['meshes'])} original meshes, "
      f"{triangles} triangles, {len(doc['images'])} source JPEG textures, CC BY 4.0")
