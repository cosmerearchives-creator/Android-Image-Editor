#!/usr/bin/env python3
"""Catch JNI signature/call regressions before expensive Android NDK setup."""
from pathlib import Path

native = Path("app/src/main/cpp/vr_native.cpp").read_text()
renderer = Path("app/src/main/cpp/vr_ui.h").read_text()
activity = Path(
    "app/src/main/java/com/cosmerearchives/androidimageeditor/VrActivity.java"
).read_text()

checks = {
    "batchStats JNI remains parameterless":
        'GetMethodID(cls,"batchStats","()[I")' in native
        and 'CallObjectMethod(activity,method));' in native,
    "mesh JNI accepts theme":
        'GetMethodID(cls,"loadEnvironmentMesh","(I)[F")' in native
        and 'CallObjectMethod(activity,method,static_cast<jint>(theme)));' in native,
    "texture JNI accepts theme":
        '"loadEnvironmentTexture","(I)[I"' in native
        and 'CallObjectMethod(activity,textureMethod,static_cast<jint>(theme)));'
        in native,
    "native renderer mesh API":
        "renderer.uploadEnvironmentMesh(vertices)" in native
        and "bool uploadEnvironmentMesh(" in renderer,
    "native renderer texture API":
        "renderer.uploadEnvironmentTexture(rgba,width,height)" in native
        and "bool uploadEnvironmentTexture(" in renderer,
    "native renderer draw API":
        "menu.drawEnvironmentMesh(" in native
        and "void drawEnvironmentMesh(" in renderer,
    "20-float PBR vertex layout with detail anchor":
        "count%20!=0||count>3600000" in native
        and "vertices.size()%20!=0" in renderer
        and "layout(location=4) in vec4 detailAnchor;" in renderer
        and "environmentVertices=static_cast<GLsizei>(vertices.size()/20)" in renderer,
    "optional PBR material JNI slots":
        '"loadEnvironmentMaterialMap","(II)[I"' in native
        and "uploadEnvironmentMaterialMap(rgba,w,h,slot)" in native
        and "bool uploadEnvironmentMaterialMap(" in renderer
        and "int[] loadEnvironmentMaterialMap(int theme,int slot)" in activity,
    "PBR material shader inputs":
        "layout(location=5) in vec2 metalRough;" in renderer
        and "layout(location=6) in vec3 emissive;" in renderer
        and "normalTexture" in renderer
        and "ormTexture" in renderer
        and "emissiveTexture" in renderer,
    "dynamic decorative detail threshold":
        "distance(cameraPosition,spin(detailAnchor.xyz,-galleryYaw))" in renderer
        and "detailAnchor.w>0.0" in renderer,
    "Java activity mesh/texture API":
        "float[] loadEnvironmentMesh(int theme)" in activity
        and "int[] loadEnvironmentTexture(int theme)" in activity,
}
for label, good in checks.items():
    if not good:
        raise AssertionError("JNI regression: " + label)

stats = native.split("void refreshBatchStats(", 1)[1].split(
    "bool batchPixels(", 1
)[0]
assert "static_cast<jint>(theme)" not in stats, (
    "batchStats has no theme argument"
)
print("JNI source contract checks passed")
