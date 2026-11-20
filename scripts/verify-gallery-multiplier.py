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
print("Randomized gallery multiplier and warning removal contracts passed")
