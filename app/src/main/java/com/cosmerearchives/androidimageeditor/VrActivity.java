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
    private static final int ADD_COLLAGE_IMAGE = 9;
    private static final int INSTALL_PERMISSION = 8;
    static { System.loadLibrary("image_editor_xr"); }
    private Thread xrThread;
    private volatile boolean resumed;
    private volatile boolean restartAfterPause;

    private native void runNative();
    private native void requestNativeStop();
    private native void notifyImageChanged();
    private native void notifyDepthChanged();
    private native void reportAiDepthStatus(int state,int progress);
    private native void reportAiSegmentStatus(int state,int progress);
    private native void submitAiSelectionMask(byte[] mask);
    private final java.util.concurrent.atomic.AtomicBoolean aiCancel =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean aiRunning =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.ExecutorService aiWorker =
        java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicBoolean segmentCancel =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean segmentRunning =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.ExecutorService segmentWorker =
        java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicLong imageGeneration =
        new java.util.concurrent.atomic.AtomicLong();

    /**
     * Primary one-click workflow: verified on-device depth model, automatic
     * apply, then verified foreground segmentation. No manual preview step.
     * Both models remain cached for future offline conversions.
     */
    @SuppressWarnings("unused")
    public void requestAutoConvert() {
        if(!aiRunning.compareAndSet(false,true))return;
        aiCancel.set(false);
        final long depthEpoch=depthGeneration.get();
        final long imageEpoch=imageGeneration.get();
        QuickDepth.discardCandidate(getApplicationContext());
        reportAiDepthStatus(1,0);
        aiWorker.execute(() -> {
            try {
                byte[] estimated=AiDepth.infer(getApplicationContext(),aiCancel,
                    progress -> reportAiDepthStatus(progress<55?1:2,
                        Math.min(75,Math.round(progress*.75f))));
                if(aiCancel.get()||depthEpoch!=depthGeneration.get()||
                   imageEpoch!=imageGeneration.get()) {
                    reportAiDepthStatus(5,0);
                    return;
                }
                QuickDepth.saveCandidate(getApplicationContext(),estimated);
                if(!QuickDepth.applyCandidate(getApplicationContext()))
                    throw new IOException("Could not apply AI depth map");
                notifyDepthChanged();
                reportAiDepthStatus(2,80);
                // A depth result is valuable even if this second model can't
                // identify a distinct subject. Do not undo successful depth.
                if(!segmentRunning.compareAndSet(false,true)){
                    reportAiDepthStatus(7,100);
                    return;
                }
                try {
                    byte[] subject=AiSegment.infer(getApplicationContext(),aiCancel,
                        progress -> reportAiDepthStatus(progress<55?2:2,
                            80+Math.min(20,progress/5)));
                    if(aiCancel.get()||imageEpoch!=imageGeneration.get()){
                        reportAiDepthStatus(5,0);
                        return;
                    }
                    submitAiSelectionMask(subject);
                    reportAiSegmentStatus(3,100);
                    reportAiDepthStatus(6,100);
                    runOnUiThread(() -> Toast.makeText(this,
                        "AI 3D conversion ready",Toast.LENGTH_SHORT).show());
                }catch(Exception segmentError){
                    if(aiCancel.get()||imageEpoch!=imageGeneration.get()){
                        reportAiDepthStatus(5,0);
                        return;
                    }
                    Log.w(TAG,"Auto depth succeeded; subject selection unavailable",
                        segmentError);
                    reportAiSegmentStatus(4,0);
                    reportAiDepthStatus(7,100);
                    runOnUiThread(() -> Toast.makeText(this,
                        "AI depth ready; automatic subject selection unavailable",
                        Toast.LENGTH_LONG).show());
                }finally {
                    segmentRunning.set(false);
                }
            }catch(Exception error){
                boolean cancelled=aiCancel.get()||depthEpoch!=depthGeneration.get()||
                    imageEpoch!=imageGeneration.get();
                Log.w(TAG,"Automatic 3D conversion stopped",error);
                reportAiDepthStatus(cancelled?5:4,0);
                if(!cancelled)runOnUiThread(() -> Toast.makeText(this,
                    "AI conversion failed: "+error.getMessage(),
                    Toast.LENGTH_LONG).show());
            }finally {aiRunning.set(false);}
        });
    }

    /** Optional offline-after-download subject segmentation; never replaces depth. */
    @SuppressWarnings("unused")
    public void requestAiSubjectSelection() {
        if(!segmentRunning.compareAndSet(false,true))return;
        segmentCancel.set(false);
        final long generation=imageGeneration.get();
        reportAiSegmentStatus(1,0);
        segmentWorker.execute(() -> {
            try {
                byte[] mask=AiSegment.infer(getApplicationContext(),segmentCancel,
                    progress -> reportAiSegmentStatus(progress<60?1:2,progress));
                if(segmentCancel.get()||generation!=imageGeneration.get()){
                    reportAiSegmentStatus(5,0);
                    return;
                }
                submitAiSelectionMask(mask);
                reportAiSegmentStatus(3,100);
                runOnUiThread(() -> Toast.makeText(this,
                    "AI subject mask ready - refine or return to editor",
                    Toast.LENGTH_LONG).show());
            }catch(Exception error){
                boolean canceled=segmentCancel.get()||
                    generation!=imageGeneration.get();
                Log.w(TAG,"AI subject selection failed",error);
                reportAiSegmentStatus(canceled?5:4,0);
                if(!canceled)runOnUiThread(() -> Toast.makeText(this,
                    "AI selection unavailable: "+error.getMessage(),
                    Toast.LENGTH_LONG).show());
            }finally{segmentRunning.set(false);}
        });
    }

    /** Starts an optional ~27 MB verified model download, then fully local inference. */
    @SuppressWarnings("unused")
    public void requestAiDepthConvert() {
        if(!aiRunning.compareAndSet(false,true))return;
        aiCancel.set(false);
        final long generation=depthGeneration.get();
        QuickDepth.discardCandidate(getApplicationContext());
        notifyDepthChanged();
        reportAiDepthStatus(1,0);
        aiWorker.execute(() -> {
            try{
                byte[] preview=AiDepth.infer(getApplicationContext(),aiCancel,
                    progress -> reportAiDepthStatus(progress<55?1:2,progress));
                if(aiCancel.get() || generation!=depthGeneration.get()){
                    QuickDepth.discardCandidate(getApplicationContext());
                    reportAiDepthStatus(5,0);
                    return;
                }
                QuickDepth.saveCandidate(getApplicationContext(),preview);
                reportAiDepthStatus(3,100);
                notifyDepthChanged();
                runOnUiThread(() -> Toast.makeText(this,
                    "AI depth preview ready — apply or discard",Toast.LENGTH_LONG).show());
            }catch(Exception error){
                boolean canceled=aiCancel.get() || generation!=depthGeneration.get();
                Log.w(TAG,"AI conversion stopped",error);
                reportAiDepthStatus(canceled?5:4,0);
                if(!canceled)runOnUiThread(() -> Toast.makeText(this,
                    "AI conversion unavailable: "+error.getMessage(),
                    Toast.LENGTH_LONG).show());
            }finally{aiRunning.set(false);}
        });
    }
    @SuppressWarnings("unused")
    public void cancelAiDepthConvert(){
        aiCancel.set(true);
        if(!aiRunning.get()){
            QuickDepth.discardCandidate(getApplicationContext());
            reportAiDepthStatus(0,0);
            notifyDepthChanged();
        } else reportAiDepthStatus(5,0);
    }
    @SuppressWarnings("unused")
    public void applyAiDepthPreview(){
        if(aiRunning.get())return;
        depthGeneration.incrementAndGet();
        try{depthSaver.execute(() -> {
            try{
                if(QuickDepth.applyCandidate(getApplicationContext())){
                    reportAiDepthStatus(0,0);
                    notifyDepthChanged();
                    runOnUiThread(() -> Toast.makeText(this,
                        "AI depth applied",Toast.LENGTH_SHORT).show());
                }
            }catch(Exception error){
                Log.e(TAG,"AI depth apply failed",error);
                runOnUiThread(() -> Toast.makeText(this,
                    "Couldn't apply AI preview",Toast.LENGTH_LONG).show());
            }
        });}catch(java.util.concurrent.RejectedExecutionException ignored){}
    }

    private native void notifyProjectListChanged();

    /** Persist the floating controls separately from individual projects. */
    @SuppressWarnings("unused")
    public float[] loadFloatingPanelPose(){
        android.content.SharedPreferences pref=getSharedPreferences("vr_layout",MODE_PRIVATE);
        return new float[]{
            pref.getFloat("x",3.4f),pref.getFloat("y",0f),
            pref.getFloat("z",-1.7f),pref.getFloat("scale",1f)};
    }
    @SuppressWarnings("unused")
    public void saveFloatingPanelPose(float x,float y,float z,float scale){
        getSharedPreferences("vr_layout",MODE_PRIVATE).edit()
            .putFloat("x",x).putFloat("y",y).putFloat("z",z)
            .putFloat("scale",scale).apply();
    }


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
        aiCancel.set(true);
        segmentCancel.set(true);
        imageGeneration.incrementAndGet();
        reportAiSegmentStatus(0,0);
        depthGeneration.incrementAndGet();
        try {depthSaver.execute(() -> {
            try {
                QuickDepth.discardCandidate(getApplicationContext());
                reportAiDepthStatus(0,0);
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
        if(gray==null || gray.length!=1024*1024){
            Log.w(TAG,"Skipping invalid depth snapshot size: "+(gray==null?0:gray.length));
            return;
        }
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
        aiCancel.set(true);
        depthGeneration.incrementAndGet();
        QuickDepth.discardCandidate(getApplicationContext());
        reportAiDepthStatus(0,0);
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
        reportAiDepthStatus(QuickDepth.hasCandidate(getApplicationContext())?3:0,0);
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
        aiCancel.set(true);
        aiWorker.shutdownNow();
        segmentCancel.set(true);
        segmentWorker.shutdownNow();
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

    @SuppressWarnings("unused")
    public void requestAddCollageImage() {
        runOnUiThread(() -> {
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
            startActivityForResult(intent,ADD_COLLAGE_IMAGE);
        });
    }

    @SuppressWarnings("unused")
    public int[] collageStatus(){return CollageStore.status(getApplicationContext());}

    @SuppressWarnings("unused")
    public void editCollage(int operation) {
        new Thread(() -> {
            try {
                CollageStore.edit(getApplicationContext(),operation);
                notifyImageChanged();
            } catch(Exception e) {
                Log.e(TAG,"Collage edit failed",e);
                runOnUiThread(() -> Toast.makeText(this,"Collage: "+e.getMessage(),
                    Toast.LENGTH_LONG).show());
            }
        },"collage-edit").start();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if(request==INSTALL_PERMISSION){
            if(getPackageManager().canRequestPackageInstalls())openVerifiedInstaller();
            else reportVrUpdateStatus(6,0);
            return;
        }
        if ((request != PICK_IMAGE && request != ADD_COLLAGE_IMAGE) || result != RESULT_OK || data == null || data.getData() == null)
            return;
        final Uri selected = data.getData();
        aiCancel.set(true);
        segmentCancel.set(true);
        imageGeneration.incrementAndGet();
        reportAiSegmentStatus(0,0);
        depthGeneration.incrementAndGet();
        reportAiDepthStatus(0,0);
        Toast.makeText(this, "Importing image locally…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                if(request==ADD_COLLAGE_IMAGE) {
                    CollageStore.add(getApplicationContext(),selected);
                    notifyImageChanged();
                    runOnUiThread(() -> Toast.makeText(this,
                        "Collage layer added",Toast.LENGTH_SHORT).show());
                    return;
                }
                ImageStorage.importImage(getApplicationContext(), selected);
                CollageStore.reset(getApplicationContext());
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
            } catch (Exception error) {
                Log.e(TAG, "Image import failed", error);
                runOnUiThread(() -> Toast.makeText(this, "Image import failed: " + error.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        }, "image-import").start();
    }
}
