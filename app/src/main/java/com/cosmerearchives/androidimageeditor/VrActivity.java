package com.cosmerearchives.androidimageeditor;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

/**
 * Quest OpenXR prototype. This renders in the headset compositor, not an Android 2D window.
 * The existing MainActivity remains the update and installation entry point.
 */
public final class VrActivity extends Activity {
    private static final String TAG = "ImageEditorXR";
    static { System.loadLibrary("image_editor_xr"); }
    private Thread xrThread;

    private native void runNative();
    private native void requestNativeStop();

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().getDecorView().setKeepScreenOn(true);
    }

    @Override protected void onResume() {
        super.onResume();
        if (xrThread != null) return;
        xrThread = new Thread(() -> {
            try {
                runNative();
            } catch (Throwable error) {
                Log.e(TAG, "OpenXR session failed", error);
            }
            runOnUiThread(() -> {
                xrThread = null;
                if (!isFinishing() && !isDestroyed()) finish();
            });
        }, "image-editor-openxr");
        xrThread.start();
    }

    @Override protected void onPause() {
        requestNativeStop();
        super.onPause();
    }

    @Override protected void onDestroy() {
        requestNativeStop();
        super.onDestroy();
    }
}
