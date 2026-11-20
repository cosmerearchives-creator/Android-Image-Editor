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
    private static final int BATCH_PICK = 10;
    private static final int EXPORT_WALL = 11;
    private static final int EXPORT_SPHERE = 12;
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
    private native void reportBatchStatus(int state,int progress,int count);
    private native void notifyBatchGalleryReady(int mode);
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
    private final ExecutorService batchWorker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean batchBusy=new AtomicBoolean(false);
    private final AtomicBoolean batchCancel=new AtomicBoolean(false);
    private volatile int pendingBatchView=0;
    private volatile int activeSavedCylinder=-1;
    @SuppressWarnings("unused")
    public int loadGalleryTheme(){
        return getSharedPreferences("vr_gallery",MODE_PRIVATE).getInt("theme",0);
    }
    @SuppressWarnings("unused")
    public void saveGalleryTheme(int theme){
        getSharedPreferences("vr_gallery",MODE_PRIVATE).edit()
            .putInt("theme",Math.max(0,Math.min(5,theme))).apply();
    }
    @SuppressWarnings("unused")
    public int galleryRadiusCm(){
        return BatchStore.radiusCm(getApplicationContext());
    }
    @SuppressWarnings("unused")
    public int gallerySceneryDetail(){
        return getSharedPreferences("vr_gallery",MODE_PRIVATE)
            .getBoolean("sceneryDetail",true)?1:0;
    }
    @SuppressWarnings("unused")
    public int activeCylinderRadiusCm(){
        return activeSavedCylinder>=0
            ?BatchStore.savedRadiusCm(getApplicationContext(),activeSavedCylinder)
            :galleryRadiusCm();
    }
    /** Called once, lazily, on the OpenXR GL thread. A malformed bundled
     * asset simply falls back to the existing fully functional VR gallery.
     */
    @SuppressWarnings("unused")
    public float[] loadEnvironmentMesh(int theme){
        try{return GltfMesh.loadEnvironment(getApplicationContext(),theme);}
        catch(Exception error){
            Log.e(TAG,"Gallery theme "+theme+" glTF load failed; using shader fallback",error);
            return null;
        }
    }
    @SuppressWarnings("unused")
    public int[] loadEnvironmentTexture(int theme){
        try{return GltfMesh.loadEnvironmentTexture(getApplicationContext(),theme);}
        catch(Exception error){
            Log.e(TAG,"Gallery theme "+theme+" texture unavailable; using vertex colors",error);
            return null;
        }
    }
    @SuppressWarnings("unused")
    public int[] loadEnvironmentMaterialMap(int theme,int slot){
        try{return GltfMesh.loadEnvironmentMaterialMap(getApplicationContext(),theme,slot);}
        catch(Exception error){
            Log.e(TAG,"Optional material map unavailable; using material factors",error);
            return null;
        }
    }
    @SuppressWarnings("unused")
    public float[] loadImportedChairMesh(){
        try{return GltfMesh.loadImportedChairMesh(getApplicationContext());}
        catch(Exception error){
            Log.e(TAG,"Professionally modeled museum chair mesh failed",error);
            return null;
        }
    }
    @SuppressWarnings("unused")
    public int[] loadImportedChairMap(int slot){
        try{return GltfMesh.loadImportedChairMap(getApplicationContext(),slot);}
        catch(Exception error){
            Log.e(TAG,"Imported chair material map failed: "+slot,error);
            return null;
        }
    }
    @SuppressWarnings("unused")
    public int activeCylinderTheme(){
        return activeSavedCylinder>=0
            ?BatchStore.savedCylinderTheme(getApplicationContext(),activeSavedCylinder)
            :loadGalleryTheme();
    }
    @SuppressWarnings("unused")
    public void requestSaveCylinder(int theme){
        if(!batchBusy.compareAndSet(false,true))return;
        int count=BatchStore.count(getApplicationContext());
        if(count==0){
            batchBusy.set(false);
            reportBatchStatus(6,0,0);
            runOnUiThread(()->Toast.makeText(this,
                "Import images before saving a cylinder",Toast.LENGTH_LONG).show());
            return;
        }
        reportBatchStatus(3,0,count);
        batchWorker.execute(()->{
            try{
                BatchStore.build(getApplicationContext(),true,
                    p->reportBatchStatus(3,p,count));
                String saved=BatchStore.saveCylinder(getApplicationContext(),theme);
                reportBatchStatus(4,100,count);
                runOnUiThread(()->Toast.makeText(this,
                    "Cylinder saved: "+saved,Toast.LENGTH_LONG).show());
            }catch(Exception e){
                Log.e(TAG,"Cylinder save failed",e);
                reportBatchStatus(6,0,count);
                runOnUiThread(()->Toast.makeText(this,
                    "Couldn't save cylinder: "+e.getMessage(),Toast.LENGTH_LONG).show());
            }finally{batchBusy.set(false);}
        });
    }
    @SuppressWarnings("unused")
    public void requestGalleryLayout(){
        runOnUiThread(()->{
            int[] settings=BatchStore.layout(getApplicationContext());
            android.widget.LinearLayout layout=new android.widget.LinearLayout(this);
            layout.setOrientation(android.widget.LinearLayout.VERTICAL);
            int pad=(int)(16*getResources().getDisplayMetrics().density);
            layout.setPadding(pad,pad,pad,pad);
            android.widget.TextView spacingLabel=new android.widget.TextView(this);
            spacingLabel.setText("Photo spacing: "+settings[0]+"%");
            layout.addView(spacingLabel);
            android.widget.SeekBar spacing=new android.widget.SeekBar(this);
            spacing.setMax(12);spacing.setProgress(settings[0]);
            spacing.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
                public void onProgressChanged(android.widget.SeekBar bar,int value,boolean fromUser){
                    spacingLabel.setText("Photo spacing: "+value+"%");
                }
                public void onStartTrackingTouch(android.widget.SeekBar bar){}
                public void onStopTrackingTouch(android.widget.SeekBar bar){}
            });
            layout.addView(spacing);
            android.widget.TextView densityLabel=new android.widget.TextView(this);
            densityLabel.setText("Columns / density: "+settings[1]);
            layout.addView(densityLabel);
            android.widget.SeekBar density=new android.widget.SeekBar(this);
            density.setMax(5);density.setProgress(settings[1]-1);
            density.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
                public void onProgressChanged(android.widget.SeekBar bar,int value,boolean fromUser){
                    densityLabel.setText("Columns / density: "+(value+1));
                }
                public void onStartTrackingTouch(android.widget.SeekBar bar){}
                public void onStopTrackingTouch(android.widget.SeekBar bar){}
            });
            layout.addView(density);
            android.widget.TextView multiplierLabel=new android.widget.TextView(this);
            multiplierLabel.setText("Gallery multiplier: "+BatchStore.galleryMultiplier(this)+"x");
            layout.addView(multiplierLabel);
            android.widget.SeekBar multiplier=new android.widget.SeekBar(this);
            multiplier.setMax(7);
            multiplier.setProgress(BatchStore.galleryMultiplier(this)-1);
            multiplier.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
                public void onProgressChanged(android.widget.SeekBar bar,int value,boolean fromUser){
                    multiplierLabel.setText("Gallery multiplier: "+(value+1)+"x");
                }
                public void onStartTrackingTouch(android.widget.SeekBar bar){}
                public void onStopTrackingTouch(android.widget.SeekBar bar){}
            });
            layout.addView(multiplier);
            android.widget.TextView multiplierInfo=new android.widget.TextView(this);
            multiplierInfo.setText("Duplicates are only visual. Originals remain unchanged. Up to 96 displayed tiles.");
            layout.addView(multiplierInfo);
            android.widget.TextView radiusLabel=new android.widget.TextView(this);
            radiusLabel.setText("Cylinder radius: "+(BatchStore.radiusCm(this)/100f)+" m");
            layout.addView(radiusLabel);
            android.widget.SeekBar radius=new android.widget.SeekBar(this);
            radius.setMax(10);
            radius.setProgress(Math.max(0,Math.min(10,(BatchStore.radiusCm(this)-250)/25)));
            radius.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
                public void onProgressChanged(android.widget.SeekBar bar,int value,boolean fromUser){
                    radiusLabel.setText("Cylinder radius: "+((250+value*25)/100f)+" m");
                }
                public void onStartTrackingTouch(android.widget.SeekBar bar){}
                public void onStopTrackingTouch(android.widget.SeekBar bar){}
            });
            layout.addView(radius);
            new android.app.AlertDialog.Builder(this)
                .setTitle("Cylinder image layout")
                .setView(layout)
                .setPositiveButton("APPLY",(dialog,which)->{
                    BatchStore.saveLayout(getApplicationContext(),
                        spacing.getProgress(),density.getProgress()+1);
                    BatchStore.saveGalleryMultiplier(getApplicationContext(),multiplier.getProgress()+1);
                    BatchStore.saveRadiusCm(getApplicationContext(),250+radius.getProgress()*25);
                    Toast.makeText(this,"Layout saved. Reopen View Sphere to rebuild.",
                        Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("CANCEL",null).show();
        });
    }
    @SuppressWarnings("unused")
    public void requestSavedCylinders(){
        runOnUiThread(this::showSavedCylinderLibrary);
    }
    private void showSavedCylinderLibrary(){
        String[] names=BatchStore.savedCylinderNames(getApplicationContext());
        if(names.length==0){
            Toast.makeText(this,"No saved cylinders yet. Choose SAVE CYLINDER.",
                Toast.LENGTH_LONG).show();
            return;
        }
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);
        android.widget.LinearLayout list=new android.widget.LinearLayout(this);
        list.setOrientation(android.widget.LinearLayout.VERTICAL);
        scroll.addView(list);
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this)
            .setTitle("Saved Cylinders")
            .setView(scroll).setNegativeButton("CLOSE",null).create();
        for(int i=0;i<names.length;i++){
            final int index=i;
            android.widget.LinearLayout row=new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            android.graphics.Bitmap thumb=BatchStore.savedThumbnail(
                getApplicationContext(),i);
            android.widget.ImageView image=new android.widget.ImageView(this);
            if(thumb!=null)image.setImageBitmap(thumb);
            image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            int height=(int)(72*getResources().getDisplayMetrics().density);
            row.addView(image,new android.widget.LinearLayout.LayoutParams(height,height));
            android.widget.Button open=new android.widget.Button(this);
            open.setText(names[i]);
            open.setAllCaps(false);
            row.addView(open,new android.widget.LinearLayout.LayoutParams(0,height,1));
            open.setOnClickListener(v->{
                if(batchBusy.get())return;
                activeSavedCylinder=index;
                dialog.dismiss();
                notifyBatchGalleryReady(3);
            });
            android.widget.Button options=new android.widget.Button(this);
            options.setText("...");
            row.addView(options,new android.widget.LinearLayout.LayoutParams(
                (int)(66*getResources().getDisplayMetrics().density),height));
            options.setOnClickListener(v->{
                String[] actions={"Rename","Duplicate","Delete"};
                new android.app.AlertDialog.Builder(this)
                    .setTitle(BatchStore.savedCylinderLabel(getApplicationContext(),index))
                    .setItems(actions,(d,which)->{
                        if(which==0){
                            android.widget.EditText name=new android.widget.EditText(this);
                            name.setSingleLine(true);
                            name.setText(BatchStore.savedCylinderLabel(
                                getApplicationContext(),index));
                            new android.app.AlertDialog.Builder(this).setTitle("Rename cylinder")
                                .setView(name).setPositiveButton("SAVE",(edit,button)->{
                                    try{
                                        BatchStore.renameCylinder(getApplicationContext(),
                                            index,name.getText().toString());
                                        dialog.dismiss();showSavedCylinderLibrary();
                                    }catch(Exception e){
                                        Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();
                                    }
                                }).setNegativeButton("CANCEL",null).show();
                        }else if(which==1){
                            try{
                                BatchStore.duplicateCylinder(getApplicationContext(),index);
                                dialog.dismiss();showSavedCylinderLibrary();
                            }catch(Exception e){
                                Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();
                            }
                        }else{
                            new android.app.AlertDialog.Builder(this)
                                .setTitle("Delete saved cylinder?")
                                .setMessage("This permanently deletes this saved cylinder. Imported images are preserved.")
                                .setPositiveButton("DELETE",(confirm,button)->{
                                    try{
                                        BatchStore.deleteCylinder(getApplicationContext(),index);
                                        activeSavedCylinder=-1;
                                        dialog.dismiss();showSavedCylinderLibrary();
                                    }catch(Exception e){
                                        Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();
                                    }
                                }).setNegativeButton("CANCEL",null).show();
                        }
                    }).show();
            });
            list.addView(row);
        }
        dialog.show();
    }

    private static final int BATCH_FOLDER=13;
    private static final String PICKER_PREFS="batch_file_picker";
    private final java.util.LinkedHashSet<String> batchPicked=new java.util.LinkedHashSet<>();
    private final ExecutorService pickerThumbnails=Executors.newSingleThreadExecutor();
    private androidx.documentfile.provider.DocumentFile pickerRoot;
    private androidx.documentfile.provider.DocumentFile pickerCurrent;
    private android.app.AlertDialog pickerDialog;
    private android.widget.LinearLayout pickerRows;
    private android.widget.TextView pickerPath;
    private android.widget.TextView pickerSelectionCount;
    private final java.util.ArrayList<androidx.documentfile.provider.DocumentFile> pickerBack=
        new java.util.ArrayList<>();

    private void openBatchFolderGrant(){
        Intent request=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        try{startActivityForResult(request,BATCH_FOLDER);}
        catch(android.content.ActivityNotFoundException e){
            Toast.makeText(this,"Folder browser unavailable on this device",
                Toast.LENGTH_LONG).show();
        }
    }
    private void openBatchCustomPicker(){
        Uri saved=null;
        String value=getSharedPreferences(PICKER_PREFS,MODE_PRIVATE)
            .getString("tree",null);
        if(value!=null)try{saved=Uri.parse(value);}catch(Exception ignored){}
        if(saved==null){openBatchFolderGrant();return;}
        try{
            pickerRoot=androidx.documentfile.provider.DocumentFile.fromTreeUri(this,saved);
            if(pickerRoot==null||!pickerRoot.canRead())throw new SecurityException("Folder access expired");
            pickerCurrent=pickerRoot;
            pickerBack.clear();
            batchPicked.clear();
            createBatchPickerDialog();
        }catch(Exception ex){
            getSharedPreferences(PICKER_PREFS,MODE_PRIVATE).edit().remove("tree").apply();
            openBatchFolderGrant();
        }
    }
    /** Own folder browser: persistent folder access, multiple selections and
     * imported status. System UI is used only for the initial folder grant.
     */
    @SuppressWarnings("unused")
    public void requestBatchImport(){
        runOnUiThread(this::openBatchCustomPicker);
    }
    private void createBatchPickerDialog(){
        android.widget.LinearLayout root=new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad=(int)(10*getResources().getDisplayMetrics().density);
        root.setPadding(pad,pad,pad,pad);
        android.widget.LinearLayout header=new android.widget.LinearLayout(this);
        header.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        android.widget.Button back=new android.widget.Button(this);
        back.setText("UP");
        back.setOnClickListener(v->{
            if(!pickerBack.isEmpty()){
                pickerCurrent=pickerBack.remove(pickerBack.size()-1);
                refreshBatchPicker();
            }
        });
        header.addView(back);
        android.widget.Button folder=new android.widget.Button(this);
        folder.setText("CHANGE FOLDER");
        folder.setOnClickListener(v->openBatchFolderGrant());
        header.addView(folder);
        root.addView(header);
        pickerPath=new android.widget.TextView(this);
        pickerPath.setTextSize(14);
        root.addView(pickerPath);
        pickerSelectionCount=new android.widget.TextView(this);
        pickerSelectionCount.setTextSize(15);
        pickerSelectionCount.setPadding(0,pad,0,pad);
        root.addView(pickerSelectionCount);
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);
        pickerRows=new android.widget.LinearLayout(this);
        pickerRows.setOrientation(android.widget.LinearLayout.VERTICAL);
        scroll.addView(pickerRows);
        root.addView(scroll,new android.widget.LinearLayout.LayoutParams(-1,0,1));
        pickerDialog=new android.app.AlertDialog.Builder(this)
            .setTitle("Choose images")
            .setView(root)
            .setNegativeButton("CANCEL",null)
            .setPositiveButton("IMPORT SELECTED",(dialog,which)->{
                if(batchPicked.isEmpty())return;
                java.util.ArrayList<Uri> selected=new java.util.ArrayList<>();
                for(String name:batchPicked)selected.add(Uri.parse(name));
                importPickedImages(selected);
            })
            .create();
        pickerDialog.setOnDismissListener(d->pickerDialog=null);
        pickerDialog.show();
        refreshBatchPicker();
    }
    private void updatePickerCount(){
        if(pickerSelectionCount!=null)
            pickerSelectionCount.setText(batchPicked.size()+" images selected");
        if(pickerDialog!=null && pickerDialog.getButton(
                android.app.AlertDialog.BUTTON_POSITIVE)!=null)
            pickerDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                .setEnabled(!batchPicked.isEmpty());
    }
    private void refreshBatchPicker(){
        if(pickerRows==null||pickerCurrent==null)return;
        final androidx.documentfile.provider.DocumentFile folder=pickerCurrent;
        pickerPath.setText("Folder: "+(folder.getName()==null?"Images":folder.getName()));
        pickerRows.removeAllViews();
        android.widget.TextView loading=new android.widget.TextView(this);
        loading.setText("Loading images...");
        pickerRows.addView(loading);
        updatePickerCount();
        // SAF providers can be slow. Never query documents on the XR/UI thread.
        new Thread(()->{
            androidx.documentfile.provider.DocumentFile[] files;
            try{files=folder.listFiles();}
            catch(Exception ex){
                Log.e(TAG,"Failed to browse folder",ex);
                runOnUiThread(()->{
                    if(pickerCurrent==folder && pickerRows!=null){
                        pickerRows.removeAllViews();
                        android.widget.TextView err=new android.widget.TextView(this);
                        err.setText("Folder unavailable. Choose another folder.");
                        pickerRows.addView(err);
                    }
                });
                return;
            }
            final java.util.Set<String> imported=BatchStore.importedUris(getApplicationContext());
            java.util.Arrays.sort(files,(a,b)->{
                if(a.isDirectory()!=b.isDirectory())return a.isDirectory()?-1:1;
                return String.CASE_INSENSITIVE_ORDER.compare(
                    String.valueOf(a.getName()),String.valueOf(b.getName()));
            });
            runOnUiThread(()->{
                if(pickerCurrent!=folder||pickerRows==null)return;
                pickerRows.removeAllViews();
                int shown=0;
                for(androidx.documentfile.provider.DocumentFile item:files){
                    if(item.isDirectory()){
                        android.widget.Button go=new android.widget.Button(this);
                        go.setText("[FOLDER] "+item.getName());
                        go.setOnClickListener(v->{
                            pickerBack.add(pickerCurrent);
                            pickerCurrent=item;
                            refreshBatchPicker();
                        });
                        pickerRows.addView(go);
                    }else{
                        String type=item.getType();
                        String name=item.getName()==null?"Image":item.getName();
                        if(type==null||!type.startsWith("image/"))continue;
                        if(++shown>250)break; // Avoid huge layouts on Quest.
                        final Uri uri=item.getUri();
                        boolean alreadyImported=imported.contains(BatchStore.documentKey(uri));
                        android.widget.LinearLayout row=new android.widget.LinearLayout(this);
                        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                        android.widget.ImageView thumb=new android.widget.ImageView(this);
                        thumb.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                        int edge=(int)(64*getResources().getDisplayMetrics().density);
                        row.addView(thumb,new android.widget.LinearLayout.LayoutParams(edge,edge));
                        android.widget.CheckBox check=new android.widget.CheckBox(this);
                        check.setText((alreadyImported?"ALREADY IMPORTED  -  ":"")+name);
                        check.setChecked(batchPicked.contains(uri.toString()));
                        check.setOnCheckedChangeListener((button,checked)->{
                            if(checked)batchPicked.add(uri.toString());
                            else batchPicked.remove(uri.toString());
                            updatePickerCount();
                        });
                        row.addView(check,new android.widget.LinearLayout.LayoutParams(
                            0,edge,1));
                        row.setOnClickListener(v->check.setChecked(!check.isChecked()));
                        pickerRows.addView(row);
                        // Thumbnail decode stays off the UI thread and is capped
                        // to avoid a giant memory spike on Quest 2.
                        if(shown<=80)pickerThumbnails.execute(()->{
                            try(java.io.InputStream in=getContentResolver().openInputStream(uri)){
                                if(in==null)return;
                                android.graphics.BitmapFactory.Options opt=
                                    new android.graphics.BitmapFactory.Options();
                                opt.inSampleSize=16;
                                opt.inPreferredConfig=android.graphics.Bitmap.Config.RGB_565;
                                android.graphics.Bitmap bitmap=
                                    android.graphics.BitmapFactory.decodeStream(in,null,opt);
                                if(bitmap==null)return;
                                runOnUiThread(()->{
                                    if(pickerDialog!=null && pickerCurrent==folder)
                                        thumb.setImageBitmap(bitmap);
                                    else bitmap.recycle();
                                });
                            }catch(Exception ignored){}
                        });
                    }
                }
                if(shown==0){
                    android.widget.TextView noImages=new android.widget.TextView(this);
                    noImages.setText("No images in this folder. Open a subfolder.");
                    pickerRows.addView(noImages);
                }else if(shown>250){
                    android.widget.TextView limit=new android.widget.TextView(this);
                    limit.setText("Showing first 250 images in this folder.");
                    pickerRows.addView(limit);
                }
                updatePickerCount();
            });
        },"batch-folder-browser").start();
    }
    private void importPickedImages(java.util.List<Uri> picked){
        if(picked.isEmpty()||!batchBusy.compareAndSet(false,true))return;
        batchCancel.set(false);
        reportBatchStatus(1,0,picked.size());
        batchWorker.execute(()->{
            try{
                int added=BatchStore.importImages(getApplicationContext(),picked,
                    batchCancel,p->reportBatchStatus(1,p,picked.size()));
                reportBatchStatus(8,100,BatchStore.count(getApplicationContext()));
                runOnUiThread(()->Toast.makeText(this,
                    added+" added, "+(picked.size()-added)+" already imported (skipped)",
                    Toast.LENGTH_LONG).show());
            }catch(Exception e){
                Log.e(TAG,"Bulk import failed",e);
                reportBatchStatus(6,0,BatchStore.count(getApplicationContext()));
                runOnUiThread(()->Toast.makeText(this,
                    "Bulk import stopped: "+e.getMessage(),Toast.LENGTH_LONG).show());
            }finally{batchBusy.set(false);}
        });
    }
    @SuppressWarnings("unused")
    public void requestBatchConvert(){
        if(!batchBusy.compareAndSet(false,true))return;
        batchCancel.set(false);
        reportBatchStatus(2,0,BatchStore.count(getApplicationContext()));
        batchWorker.execute(()->{
            try{
                BatchStore.convertMissing(getApplicationContext(),batchCancel,
                    p->reportBatchStatus(2,p,BatchStore.count(getApplicationContext())));
                reportBatchStatus(4,100,BatchStore.count(getApplicationContext()));
                runOnUiThread(()->Toast.makeText(this,
                    "Bulk AI conversion cached",Toast.LENGTH_LONG).show());
            }catch(Exception e){
                Log.e(TAG,"Bulk AI conversion failed",e);
                reportBatchStatus(6,0,BatchStore.count(getApplicationContext()));
                runOnUiThread(()->Toast.makeText(this,
                    "Bulk conversion stopped: "+e.getMessage(),Toast.LENGTH_LONG).show());
            }finally{batchBusy.set(false);}
        });
    }
    /** Mode 1: zoomable wall; mode 2: viewer-centered panorama. */
    @SuppressWarnings("unused")
    public void requestBatchView(int mode,boolean confirmed){
        if(mode!=1&&mode!=2)return;
        int count=BatchStore.count(getApplicationContext());
        if(count==0){
            reportBatchStatus(6,0,0);return;
        }
        // The gallery atlas itself bounds memory and rendering cost. Large
        // libraries no longer require an interrupting confirmation.
        pendingBatchView=0;
        activeSavedCylinder=-1;
        if(!batchBusy.compareAndSet(false,true))return;
        reportBatchStatus(3,0,count);
        batchWorker.execute(()->{
            try{
                BatchStore.build(getApplicationContext(),mode==2,
                    p->reportBatchStatus(3,p,count));
                reportBatchStatus(4,100,count);
                notifyBatchGalleryReady(mode);
            }catch(Exception e){
                Log.e(TAG,"Gallery preparation failed",e);
                reportBatchStatus(6,0,count);
            }finally{batchBusy.set(false);}
        });
    }
    @SuppressWarnings("unused")
    public void confirmLargeBatchView(){
        // Legacy JNI entrypoint; simply opens the chosen view without warnings.
        int mode=pendingBatchView;
        if(mode!=0)requestBatchView(mode,true);
    }
    @SuppressWarnings("unused")
    public void requestBatchExport(boolean spherical){
        if(!batchBusy.compareAndSet(false,true))return;
        int count=BatchStore.count(getApplicationContext());
        if(count==0){batchBusy.set(false);reportBatchStatus(6,0,0);return;}
        reportBatchStatus(3,0,count);
        batchWorker.execute(()->{
            try{
                File output=spherical?BatchStore.sphere(getApplicationContext()):
                                      BatchStore.wall(getApplicationContext());
                if(!output.isFile())
                    BatchStore.build(getApplicationContext(),spherical,
                        p->reportBatchStatus(3,p,count));
                reportBatchStatus(7,100,count);
                runOnUiThread(()->{
                    try{
                        Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("image/png")
                            .putExtra(Intent.EXTRA_TITLE,
                                spherical?"vr_sphere_gallery.png":"vr_wall_gallery.png");
                        startActivityForResult(save,spherical?EXPORT_SPHERE:EXPORT_WALL);
                    }catch(RuntimeException e){
                        reportBatchStatus(6,0,count);
                        batchBusy.set(false);
                    }
                });
            }catch(Exception e){
                Log.e(TAG,"Export preparation failed",e);
                reportBatchStatus(6,0,count);
                batchBusy.set(false);
            }
        });
    }
    @SuppressWarnings("unused")
    public int[] batchStats(){
        return new int[]{BatchStore.count(getApplicationContext()),
                         BatchStore.converted(getApplicationContext())};
    }
    @SuppressWarnings("unused")
    public int[] batchPixels(boolean spherical,boolean depth){
        if(spherical&&activeSavedCylinder>=0)
            return BatchStore.loadSavedPixels(getApplicationContext(),
                activeSavedCylinder,depth);
        return BatchStore.loadPixels(getApplicationContext(),spherical,depth);
    }
    @SuppressWarnings("unused")
    public void requestOpenBatchImage(int index){
        if(!batchBusy.compareAndSet(false,true))return;
        batchWorker.execute(()->{
            try{
                aiCancel.set(true);segmentCancel.set(true);
                imageGeneration.incrementAndGet();depthGeneration.incrementAndGet();
                BatchStore.openImage(getApplicationContext(),index);
                reportAiDepthStatus(0,0);
                notifyImageChanged();notifyDepthChanged();notifyProjectListChanged();
            }catch(Exception e){
                Log.e(TAG,"Open batch image failed",e);
                runOnUiThread(()->Toast.makeText(this,
                    "Could not open batch image",Toast.LENGTH_LONG).show());
            }finally{batchBusy.set(false);}
        });
    }

    /**
     * Primary one-click workflow: verified on-device depth model, automatic
     * apply, then verified foreground segmentation. No manual preview step.
     * Both models remain cached for future offline conversions.
     */
    @SuppressWarnings("unused")
    public void requestAutoConvert() {
        if(aiRunning.get())return;
        // A completed AI scan is tied to the actual imported image. Keep it
        // through reopening projects and style changes; never infer twice
        // merely because the user selects Inverted or Exaggerated.
        if(new File(getFilesDir(),"vr_depth_ai_done.flag").isFile()
           && QuickDepth.available(getApplicationContext())){
            reportAiDepthStatus(6,100);
            return;
        }
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
                new File(getFilesDir(),"vr_depth_ai_done.flag").createNewFile();
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
                    new File(getFilesDir(),"vr_depth_ai_done.flag").createNewFile();
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
                    BatchStore.syncActive(getApplicationContext());
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
        new File(getFilesDir(),"vr_depth_ai_done.flag").delete();
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
        batchCancel.set(true);
        batchWorker.shutdownNow();
        pickerThumbnails.shutdownNow();
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
        if(request==EXPORT_WALL||request==EXPORT_SPHERE){
            if(result!=RESULT_OK||data==null||data.getData()==null){
                batchBusy.set(false);
                reportBatchStatus(0,0,BatchStore.count(getApplicationContext()));
                return;
            }
            final Uri target=data.getData();
            final boolean spherical=request==EXPORT_SPHERE;
            batchWorker.execute(()->{
                try(java.io.OutputStream out=getContentResolver().openOutputStream(target)){
                    if(out==null)throw new IOException("Cannot write export");
                    BatchStore.copyTo(getApplicationContext(),spherical,out,
                        progress->reportBatchStatus(7,progress,
                            BatchStore.count(getApplicationContext())));
                    reportBatchStatus(4,100,BatchStore.count(getApplicationContext()));
                    runOnUiThread(()->Toast.makeText(this,
                        "Gallery PNG exported",Toast.LENGTH_LONG).show());
                }catch(Exception e){
                    Log.e(TAG,"Bulk export failed",e);
                    reportBatchStatus(6,0,BatchStore.count(getApplicationContext()));
                }finally{batchBusy.set(false);}
            });
            return;
        }
        if(request==BATCH_FOLDER){
            if(result!=RESULT_OK||data==null||data.getData()==null)return;
            Uri tree=data.getData();
            try{
                getContentResolver().takePersistableUriPermission(tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getSharedPreferences(PICKER_PREFS,MODE_PRIVATE).edit()
                    .putString("tree",tree.toString()).apply();
                if(pickerDialog!=null)pickerDialog.dismiss();
                openBatchCustomPicker();
            }catch(SecurityException e){
                Log.e(TAG,"Folder access denied",e);
                Toast.makeText(this,"Folder permission wasn't granted",
                    Toast.LENGTH_LONG).show();
            }
            return;
        }
        if(request==BATCH_PICK){
            if(result!=RESULT_OK||data==null)return;
            java.util.ArrayList<Uri> picked=new java.util.ArrayList<>();
            if(data.getClipData()!=null){
                android.content.ClipData clip=data.getClipData();
                for(int i=0;i<clip.getItemCount();++i)
                    if(clip.getItemAt(i).getUri()!=null)
                        picked.add(clip.getItemAt(i).getUri());
            }else if(data.getData()!=null)picked.add(data.getData());
            importPickedImages(picked);
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
