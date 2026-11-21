#!/usr/bin/env python3
"""Source regression tests for randomized gallery fill without a size warning."""
from pathlib import Path

base = Path("app/src/main/java/com/cosmerearchives/androidimageeditor")
store = (base / "BatchStore.java").read_text()
activity = (base / "VrActivity.java").read_text()
native = Path("app/src/main/cpp/vr_native.cpp").read_text()
ui = Path("app/src/main/cpp/vr_ui.h").read_text()

assert "MAX_MULTIPLIED_TILES=96" in store
assert 'getInt("multiplier",1)' in store
assert "Collections.shuffle(tiles,new java.util.Random(seed))" in store
assert "File[] list=spherical?sphereTiles(ctx,originals):originals;" in store
assert "drawTiles(ctx,list,canvas,width,height/2,wallColumns,wallRows," in store
assert "sphere(ctx).delete();sphereDepth(ctx).delete();" in store
assert "BatchStore.saveGalleryMultiplier(getApplicationContext(),multiplier.getProgress()+1)" in activity
assert "if(count>BatchStore.WARN_COUNT" not in activity
assert '"CONFIRM VIEW OVER 16 IMAGES"' not in ui
assert "confirmLargeBatchView" not in native
assert "public static synchronized void resetGallery(Context ctx)" in store
assert "public void requestResetGallery()" in activity
assert 'batchMethod(env,activity,"requestResetGallery","()V")' in native
assert "menu.galleryResetConfirm" in native
assert "GallerySettings" in ui and "GallerySettings" in native
assert "public void adjustGallerySetting(int control)" in activity
assert 'batchMethod(env,activity,"adjustGallerySetting","(I)V",hovered-160)' in native
assert "gallerySettingFromJava(" in native
assert 'startActivityForResult(intent,BATCH_PICK)' in activity
assert 'putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)' in activity
shader = ui.split('const char* sphereFs=R"(',1)[1].split(')";',1)[0]
assert "float cycle=floor(galleryTime/24.)" in shader
assert "vec4 flying=texture(panorama,flyingUv)" in shader
assert "planeDistance=mix(radius-.65,-.85,progress)" in shader
print("Randomized gallery multiplier and warning removal contracts passed")
