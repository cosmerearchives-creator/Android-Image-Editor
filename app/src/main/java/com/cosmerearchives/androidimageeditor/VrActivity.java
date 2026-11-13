package com.cosmerearchives.androidimageeditor;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import java.io.IOException;

/** Native OpenXR renderer with system image picker for local, offline VR viewing. */
public final class VrActivity extends Activity {
    private static final String TAG = "ImageEditorXR";
    private static final int PICK_IMAGE = 7;
    static { System.loadLibrary("image_editor_xr"); }
    private Thread xrThread;
    private volatile boolean resumed;
    private volatile boolean restartAfterPause;

    private native void runNative();
    private native void requestNativeStop();
    private native void notifyImageChanged();
    private native void notifyDepthChanged();
    // Serialize brush snapshots so an earlier stroke cannot overwrite a newer one.
    private final java.util.concurrent.ExecutorService depthSaver =
        java.util.concurrent.Executors.newSingleThreadExecutor();

    /** Called from XR at the end of a brush stroke. Copies bytes before returning. */
    @SuppressWarnings("unused")
    public void saveEditedDepth(byte[] gray) {
        if(gray==null || gray.length!=256*256)return;
        final byte[] snapshot=gray.clone();
        depthSaver.execute(() -> {
            try {
                QuickDepth.saveEdited(getApplicationContext(),snapshot);
            } catch (IOException | RuntimeException e) {
                Log.e(TAG,"Saving corrected depth failed",e);
                runOnUiThread(() -> Toast.makeText(this,
                        "Could not save depth corrections", Toast.LENGTH_LONG).show());
            }
        });
    }

    private final java.util.concurrent.atomic.AtomicBoolean converting =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    /** Runs Quick Convert off the XR/UI threads and reloads the depth texture when done. */
    @SuppressWarnings("unused")
    public void requestQuickConvert() {
        if(!converting.compareAndSet(false,true))return;
        new Thread(() -> {
            try {
                QuickDepth.convert(getApplicationContext());
                runOnUiThread(() -> Toast.makeText(this, "Quick depth map saved",
                        Toast.LENGTH_SHORT).show());
            } catch (IOException | RuntimeException e) {
                Log.e(TAG, "Quick Convert failed", e);
                runOnUiThread(() -> Toast.makeText(this,
                        "Quick Convert failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                converting.set(false);
                notifyDepthChanged();
            }
        }, "quick-convert").start();
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().getDecorView().setKeepScreenOn(true);
    }

    private void startVrIfNeeded() {
        if (!resumed || xrThread != null || isFinishing() || isDestroyed()) return;
        restartAfterPause = false;
        xrThread = new Thread(() -> {
            try {
                runNative();
            } catch (Throwable error) {
                Log.e(TAG, "OpenXR session failed", error);
            }
            runOnUiThread(() -> {
                xrThread = null;
                if (resumed && restartAfterPause) startVrIfNeeded();
            });
        }, "image-editor-openxr");
        xrThread.start();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        startVrIfNeeded();
    }

    @Override protected void onPause() {
        resumed = false;
        restartAfterPause = true;
        requestNativeStop();
        super.onPause();
    }

    @Override protected void onDestroy() {
        resumed = false;
        requestNativeStop();
        depthSaver.shutdown();
        super.onDestroy();
    }

    /** Invoked via JNI from OpenXR when Import Image is selected. */
    @SuppressWarnings("unused")
    public void requestImageImport() {
        runOnUiThread(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("image/*");
                startActivityForResult(intent, PICK_IMAGE);
            } catch (RuntimeException error) {
                Toast.makeText(this, "No compatible image picker found", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_IMAGE || result != RESULT_OK || data == null || data.getData() == null)
            return;
        final Uri selected = data.getData();
        Toast.makeText(this, "Importing image locally…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                ImageStorage.importImage(getApplicationContext(), selected);
                notifyImageChanged();
                runOnUiThread(() -> Toast.makeText(this, "Image ready in VR", Toast.LENGTH_SHORT).show());
            } catch (IOException | RuntimeException error) {
                Log.e(TAG, "Image import failed", error);
                runOnUiThread(() -> Toast.makeText(this, "Image import failed: " + error.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        }, "image-import").start();
    }
}
