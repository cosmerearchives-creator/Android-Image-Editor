#!/usr/bin/env python3
"""Check Quest video importer/playback wiring before APK compilation."""
from pathlib import Path

java=Path("app/src/main/java/com/cosmerearchives/androidimageeditor")
store=(java/"BatchStore.java").read_text()
activity=(java/"VrActivity.java").read_text()
player=(java/"GalleryVideoPlayer.java").read_text()
native=Path("app/src/main/cpp/vr_native.cpp").read_text()
renderer=Path("app/src/main/cpp/vr_ui.h").read_text()
shader=renderer.split('const char* sphereFs=R"(',1)[1].split(')";',1)[0]

assert 'private static final String VIDEO="video.mp4"' in store
assert "public static int importVideo(Context ctx,Uri uri,AtomicBoolean cancel)" in store
assert "boolean video=lower.endsWith(\".mp4\")" in store
assert "importVideoStream(ctx,zip,cancel" in store
assert "galleryVideoTileSlots(Context ctx)" in store
assert 'new String[]{"image/*","video/mp4"}' in activity
assert "startGalleryVideos(int[] textures)" in activity
assert "updateGalleryVideoFrames()" in activity
assert "stopGalleryVideos()" in activity
assert "galleryVideos.start(BatchStore.galleryVideoPaths(this),textures)" in activity
assert "player.setLooping(true)" in player
assert "player.prepareAsync()" in player
assert "new SurfaceTexture(textureIds[i])" in player
assert "frame.updateTexImage()" in player
assert "GL_OES_EGL_image_external_essl3" in shader
assert "samplerExternalOES video0,video1" in shader
assert "tileVideoSlots[96]" in shader
assert "liveGalleryPixel(texcoord)" in shader
assert "liveGalleryPixel(flyingUv)" in shader
assert "glBindTexture(GL_TEXTURE_EXTERNAL_OES" in renderer
assert 'GetMethodID(cls,"startGalleryVideos","([I)V")' in native
assert 'GetMethodID(cls,"galleryVideoTileSlots","()[I")' in native
assert 'batchMethod(env,activity,"updateGalleryVideoFrames","()V")' in native
assert 'batchMethod(env,activity,"stopGalleryVideos","()V")' in native
print("Quest looping MP4, ZIP importer, live tile and video fly-through checks passed")
