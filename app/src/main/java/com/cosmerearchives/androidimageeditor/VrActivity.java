package com.cosmerearchives.androidimageeditor;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import java.io.IOException;

/** Native OpenXR renderer with system image picker for local, offline VR viewing. */
public final class VrActivity extends Activity {
    private static final String TAG = "ImageEditorXR";
    private static final int PICK_IMAGE = 7;
    private static final int INSTALL_PERMISSION = 8;
    static { System.loadLibrary("image_editor_xr"); }
    private Thread xrThread;
    private volatile boolean resumed;
    private volatile boolean restartAfterPause;

    private native void runNative();
    private native void requestNativeStop();
    private native void notifyImageChanged();
    private native void notifyDepthChanged();
    private native void notifyProjectListChanged();

    /** Used by the VR Projects library. */
    @SuppressWarnings("unused")
    public String[] listLocalProjects(){
        try {return ProjectStore.list(getApplicationContext());}
        catch(RuntimeException e){Log.e(TAG,"Unable to list projects",e);return new String[0];}
    }

    /** All writes are serialized with brush-stroke snapshots on the same worker. */
    @SuppressWarnings("unused")
    public void requestSaveProject(boolean saveAsNew) {
        try {depthSaver.execute(() -> {
            try {
                ProjectStore.save(getApplicationContext(),saveAsNew);
                notifyProjectListChanged();
                runOnUiThread(() -> Toast.makeText(this,"Project saved",Toast.LENGTH_SHORT).show());
            } catch(Exception e){
                Log.e(TAG,"Project save failed",e);
                runOnUiThread(() -> Toast.makeText(this,"Could not save project",Toast.LENGTH_LONG).show());
            }
        });} catch(java.util.concurrent.RejectedExecutionException ignored){}
    }

    @SuppressWarnings("unused")
    public void requestOpenProject(int index){
        depthGeneration.incrementAndGet();
        try {depthSaver.execute(() -> {
            try {
                ProjectStore.open(getApplicationContext(),index);
                notifyImageChanged();
                notifyDepthChanged();
                notifyProjectListChanged();
                runOnUiThread(() -> Toast.makeText(this,"Project loaded",Toast.LENGTH_SHORT).show());
            } catch(Exception e){
                Log.e(TAG,"Project open failed",e);
                runOnUiThread(() -> Toast.makeText(this,"Could not open project",Toast.LENGTH_LONG).show());
            }
        });} catch(java.util.concurrent.RejectedExecutionException ignored){}
    }
    private native void reportVrUpdateStatus(int state,int percent);
    private final ExecutorService updateWorker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean updateRunning=new AtomicBoolean(false);
    private File pendingUpdate;

    /** Run the original signed GitHub updater directly from the immersive home menu. */
    @SuppressWarnings("unused")
    public void requestVrUpdateCheck() {
        if(!updateRunning.compareAndSet(false,true))return;
        reportVrUpdateStatus(1,0);
        updateWorker.execute(() -> {
            try {
                PackageInfo installed=getPackageManager().getPackageInfo(getPackageName(),
                        PackageManager.GET_SIGNING_CERTIFICATES);
                ReleaseUpdater updater=new ReleaseUpdater(getApplicationContext(),installed);
                File apk=updater.downloadUpdate(message -> {
                    // Only expose coarse progress to VR; network and signature checks
                    // stay in the existing, tested ReleaseUpdater implementation.
                    int percent=0;
                    int end=message.lastIndexOf('%');
                    if(end>=0) {
                        int start=end-1;
                        while(start>=0 && Character.isDigit(message.charAt(start))) start--;
                        try {percent=Integer.parseInt(message.substring(start+1,end));}
                        catch(NumberFormatException ignored){}
                    }
                    if(message.startsWith("Downloading"))reportVrUpdateStatus(2,percent);
                    else reportVrUpdateStatus(1,0);
                });
                runOnUiThread(() -> {
                    if(isFinishing()||isDestroyed())return;
                    if(apk==null)reportVrUpdateStatus(3,100);
                    else {
                        pendingUpdate=apk;
                        openVerifiedInstaller();
                    }
                });
            } catch(Exception error) {
                Log.e(TAG,"VR update check failed",error);
                reportVrUpdateStatus(5,0);
                runOnUiThread(() -> Toast.makeText(this,
                        "Update check failed: "+error.getMessage(),Toast.LENGTH_LONG).show());
            } finally {
                updateRunning.set(false);
            }
        });
    }

    private void openVerifiedInstaller() {
        if(pendingUpdate==null)return;
        try {
            if(!getPackageManager().canRequestPackageInstalls()) {
                reportVrUpdateStatus(6,0);
                Intent settings=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:"+getPackageName()));
                startActivityForResult(settings,INSTALL_PERMISSION);
                return;
            }
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".updates",pendingUpdate);
            Intent installer=new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri,"application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            reportVrUpdateStatus(4,100);
            startActivity(installer);
        } catch(RuntimeException error) {
            Log.e(TAG,"Unable to launch verified installer",error);
            reportVrUpdateStatus(5,0);
            Toast.makeText(this,"Could not open installer",Toast.LENGTH_LONG).show();
        }
    }
    // Serialize brush snapshots so an earlier stroke cannot overwrite a newer one.
    private final java.util.concurrent.ExecutorService depthSaver =
        java.util.concurrent.Executors.newSingleThreadExecutor();

    private final java.util.concurrent.atomic.AtomicLong depthGeneration =
        new java.util.concurrent.atomic.AtomicLong();

    /** The XR thread sends a copy at stroke end; saving never blocks rendering. */
    @SuppressWarnings("unused")
    public void saveEditedDepth(byte[] gray) {
        if(gray==null || gray.length!=256*256)return;
        final byte[] snapshot=gray.clone();
        final long expectedGeneration=depthGeneration.get();
        try {
            depthSaver.execute(() -> {
                if(expectedGeneration!=depthGeneration.get())return;
                try {
                    QuickDepth.saveEdited(getApplicationContext(),snapshot);
                } catch (IOException | RuntimeException e) {
                    Log.e(TAG,"Saving corrected depth failed",e);
                    runOnUiThread(() -> Toast.makeText(this,
                        "Could not save depth corrections",Toast.LENGTH_LONG).show());
                }
            });
        } catch(java.util.concurrent.RejectedExecutionException ex) {
            Log.w(TAG,"Depth saver already shut down",ex);
        }
    }

    private final java.util.concurrent.atomic.AtomicBoolean converting =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    /** Runs Quick Convert off the XR/UI threads and reloads the depth texture when done. */
    @SuppressWarnings("unused")
    public void requestQuickConvert() {
        if(!converting.compareAndSet(false,true))return;
        depthGeneration.incrementAndGet();
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
                if (isDestroyed()) depthSaver.shutdown();
                else if (resumed && restartAfterPause) startVrIfNeeded();
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
        if(xrThread==null)depthSaver.shutdown();
        updateWorker.shutdownNow();
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
        if(request==INSTALL_PERMISSION){
            if(getPackageManager().canRequestPackageInstalls())openVerifiedInstaller();
            else reportVrUpdateStatus(6,0);
            return;
        }
        if (request != PICK_IMAGE || result != RESULT_OK || data == null || data.getData() == null)
            return;
        final Uri selected = data.getData();
        depthGeneration.incrementAndGet();
        Toast.makeText(this, "Importing image locally…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                ImageStorage.importImage(getApplicationContext(), selected);
                String filename="UNTITLED";
                try(android.database.Cursor cursor=getContentResolver().query(selected,
                        new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
                        null,null,null)){
                    if(cursor!=null && cursor.moveToFirst()) filename=cursor.getString(0);
                }catch(RuntimeException ignored){}
                ProjectStore.imported(getApplicationContext(),filename);
                notifyProjectListChanged();
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
