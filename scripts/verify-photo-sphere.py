#!/usr/bin/env python3
"""Guard photo-only Quest sphere against accidental reintroduction of themed rooms."""
from pathlib import Path

root = Path("app/src/main/cpp")
native = (root / "vr_native.cpp").read_text(encoding="utf-8")
ui = (root / "vr_ui.h").read_text(encoding="utf-8")
activity = Path("app/src/main/java/com/cosmerearchives/androidimageeditor/VrActivity.java").read_text(encoding="utf-8")

assert "quietStars(vec3 direction)" in ui, "Sphere starfield not present"
shader = ui.split('const char* sphereFs=R"(', 1)[1].split(')";', 1)[0]
assert "liveGalleryPixel(texcoord)" in shader
assert "mix(sky,photo.rgb,photo.a)" in shader, "Photos must cover stars at opaque pixels"
for obsolete in ("galleryTheme", "environmentDetail", "museumArchitecture",
                 "sculpture(", "themedArchitecture(", "material(vec3"):
    assert obsolete not in shader, f"Obsolete themed shading: {obsolete}"
assert 'menu.drawSphere(quaternion,tangents,eyeSign,camera,galleryYaw,galleryTime)' in native
for call in ("menu.drawEnvironmentMesh(", "menu.drawChair(",
             "tryLoadEnvironmentAsset(env,activity,menu)",
             "tryLoadChairAsset(env,activity,menu)",
             'batchMethod(env,activity,"saveGalleryTheme"'):
    assert call not in native, f"Legacy environment remains active: {call}"
assert '"GALLERY THEME: "' not in ui, "Theme UI is still visible"
assert 'detail.setText("Detailed 3D scenery' not in activity
assert 'batchMethod(env,activity,"requestSaveCylinder","(I)V",0)' in native
print("Photo sphere contract passed: themed environments disabled, stars only behind gallery photos")
