package com.cosmerearchives.androidimageeditor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

/** Persistent, independently editable and reusable on-device batch library. */
public final class BatchStore {
    // A fixed-size atlas provides an inherent performance limit without warning dialogs.
    private static final int MAX_MULTIPLIED_TILES=96;
    private static final int SOURCE_MAX=2048;
    private static final int DEPTH_EDGE=1024;
    private static final String PHOTO="source.png",DEPTH="depth.png",MASK="mask.bin";
    private static final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    public static int[] layout(Context ctx){
        android.content.SharedPreferences prefs=ctx.getSharedPreferences("vr_gallery_layout",Context.MODE_PRIVATE);
        return new int[]{prefs.getInt("spacing",2),prefs.getInt("density",3)};
    }
    /** Display copies only; original media files are never duplicated. */
    public static int galleryMultiplier(Context ctx){
        return Math.max(1,Math.min(8,ctx.getSharedPreferences("vr_gallery_layout",Context.MODE_PRIVATE)
            .getInt("multiplier",1)));
    }
    public static void saveGalleryMultiplier(Context ctx,int multiplier){
        ctx.getSharedPreferences("vr_gallery_layout",Context.MODE_PRIVATE).edit()
            .putInt("multiplier",Math.max(1,Math.min(8,multiplier))).apply();
        sphere(ctx).delete();sphereDepth(ctx).delete();
    }
    /** Stable shuffle keeps the image atlas aligned with its depth atlas. */
    private static File[] sphereTiles(Context ctx,File[] originals){
        if(originals.length==0)return originals;
        int desired=Math.min(MAX_MULTIPLIED_TILES,originals.length*galleryMultiplier(ctx));
        desired=Math.max(originals.length,desired);
        java.util.ArrayList<File> tiles=new java.util.ArrayList<>(desired);
        tiles.addAll(Arrays.asList(originals));
        for(int i=originals.length;i<desired;i++)tiles.add(originals[i%originals.length]);
        long seed=0x426A3F1AL;
        for(File item:originals)seed=seed*31L+item.getName().hashCode();
        java.util.Collections.shuffle(tiles,new java.util.Random(seed));
        return tiles.toArray(new File[0]);
    }
    /** 0 disables motion; other values are seconds between fly-through starts. */
    public static int flyInterval(Context ctx){
        int value=ctx.getSharedPreferences("vr_gallery_layout",Context.MODE_PRIVATE)
            .getInt("flyInterval",24);
        return value==0?0:Math.max(12,Math.min(120,value));
    }
    public static int flyOpacityPercent(Context ctx){
        return Math.max(10,Math.min(100,ctx.getSharedPreferences("vr_gallery_layout",Context.MODE_PRIVATE)
            .getInt("flyOpacityPercent",100)));
    }
    public static void saveFlyInterval(Context ctx,int seconds){
        ctx.getSharedPreferences("vr_gallery_layout",Context.MODE_PRIVATE).edit()
            .putInt("flyInterval",seconds==0?0:Math.max(12,Math.min(120,seconds))).apply();
    }
    public static void saveFlyOpacityPercent(Context ctx,int percent){
        ctx.getSharedPreferences("vr_gallery_layout",Context.MODE_PRIVATE).edit()
            .putInt("flyOpacityPercent",Math.max(10,Math.min(100,percent))).apply();
    }
    public static int radiusCm(Context ctx){
        return ctx.getSharedPreferences("vr_gallery_layout",Context.MODE_PRIVATE)
            .getInt("radiusCm",300);
    }
    public static void saveRadiusCm(Context ctx,int cm){
        ctx.getSharedPreferences("vr_gallery_layout",Context.MODE_PRIVATE).edit()
            .putInt("radiusCm",Math.max(250,Math.min(500,cm))).apply();
    }
    public static int savedRadiusCm(Context ctx,int index){
        try{
            File folder=savedAt(ctx,index);
            File radius=new File(folder,"radius.txt");
            if(radius.isFile()){
                String value=new String(java.nio.file.Files.readAllBytes(radius.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8).trim();
                return Math.max(250,Math.min(500,Integer.parseInt(value)));
            }
        }catch(Exception ignored){}
        return 300; // Older rooms were rendered at a 3.0 m radius.
    }
    public static void saveLayout(Context ctx,int spacing,int density){
        ctx.getSharedPreferences("vr_gallery_layout",Context.MODE_PRIVATE).edit()
            .putInt("spacing",Math.max(0,Math.min(12,spacing)))
            .putInt("density",Math.max(1,Math.min(6,density))).apply();
        sphere(ctx).delete();sphereDepth(ctx).delete();
    }

    private BatchStore(){}

    private static File root(Context context){
        File out=new File(context.getFilesDir(),"vr_batch");
        if(!out.isDirectory()&&!out.mkdirs())throw new IllegalStateException("Batch storage unavailable");
        return out;
    }
    private static File cache(Context ctx,String name){return new File(root(ctx),name);}
    public static File wall(Context ctx){return cache(ctx,"wall.png");}
    public static File wallDepth(Context ctx){return cache(ctx,"wall_depth.png");}
    public static File sphere(Context ctx){return cache(ctx,"cylinder_v5.png");}
    public static File sphereDepth(Context ctx){return cache(ctx,"cylinder_depth_v5.png");}
    private static File[] entries(Context ctx){
        File[] items=root(ctx).listFiles(f->f.isDirectory()&&new File(f,PHOTO).isFile());
        if(items==null)return new File[0];
        Arrays.sort(items,Comparator.comparing(File::getName));
        return items;
    }
    private static final String DISPLAY_NAME="import_name.txt";
    private static final String SOURCE_URI="import_uri.txt";
    public static String documentKey(Uri uri){
        // The same image can be returned as /document/ID or /tree/.../document/ID.
        // Normalize both so previously selected images retain their badge.
        try{
            String id=android.provider.DocumentsContract.getDocumentId(uri);
            if(id!=null)return uri.getAuthority()+":"+id;
        }catch(Exception ignored){}
        return uri.toString();
    }
    public static java.util.Set<String> importedUris(Context ctx){
        java.util.HashSet<String> ids=new java.util.HashSet<>();
        for(File entry:entries(ctx)){
            File record=new File(entry,SOURCE_URI);
            if(!record.isFile())continue;
            try(InputStream in=new FileInputStream(record)){
                byte[] data=new byte[(int)Math.min(4096,record.length())];
                int read=in.read(data);
                if(read>0){
                    String all=new String(data,0,read,
                        java.nio.charset.StandardCharsets.UTF_8);
                    for(String line:all.split("\\n"))if(!line.isEmpty())ids.add(line.trim());
                }
            }catch(IOException ignored){}
        }
        return ids;
    }
    private static void saveSourceUri(Uri source,File entry){
        File record=new File(entry,SOURCE_URI);
        String value=documentKey(source);
        try{
            String existing="";
            if(record.isFile()){
                try(InputStream in=new FileInputStream(record)){
                    byte[] bytes=new byte[(int)Math.min(4096,record.length())];
                    int count=in.read(bytes);
                    if(count>0)existing=new String(bytes,0,count,
                        java.nio.charset.StandardCharsets.UTF_8);
                }
            }
            for(String line:existing.split("\\n"))if(value.equals(line.trim()))return;
            try(FileOutputStream out=new FileOutputStream(record,true)){
                if(!existing.isEmpty()&&existing.charAt(existing.length()-1)!=10)out.write('\n');
                out.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.write('\n');
            }
        }catch(IOException ignored){}
    }
    public static File[] importedImages(Context ctx){return entries(ctx);}
    public static String importedName(File entry){
        File info=new File(entry,DISPLAY_NAME);
        if(info.isFile()){
            try{
                byte[] data=new byte[(int)Math.min(256,info.length())];
                try(FileInputStream in=new FileInputStream(info)){
                    int n=in.read(data);
                    if(n>0)return new String(data,0,n,java.nio.charset.StandardCharsets.UTF_8);
                }
            }catch(IOException ignored){}
        }
        return "Previously imported image";
    }
    private static void writeImportedName(Context ctx,Uri source,File entry){
        String name=null;
        try(android.database.Cursor cursor=ctx.getContentResolver().query(
                source,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},
                null,null,null)){
            if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);
        }catch(RuntimeException ignored){}
        if(name==null||name.trim().isEmpty())name="Imported image";
        name=name.replace('\n',' ').replace('\r',' ');
        if(name.length()>110)name=name.substring(0,110);
        try(FileOutputStream out=new FileOutputStream(new File(entry,DISPLAY_NAME))){
            out.write(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }catch(IOException ignored){}
    }
    public static int count(Context ctx){return entries(ctx).length;}
    public static int converted(Context ctx){
        int result=0;
        for(File f:entries(ctx))if(new File(f,DEPTH).isFile())result++;
        return result;
    }
    private static void cancelled(AtomicBoolean flag)throws IOException{
        if(flag.get()||Thread.currentThread().isInterrupted())throw new IOException("Cancelled");
    }
    private static String hash(File input)throws Exception{
        MessageDigest d=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(input)){
            byte[] buf=new byte[65536];int n;
            while((n=in.read(buf))!=-1)d.update(buf,0,n);
        }
        StringBuilder b=new StringBuilder();
        for(byte x:d.digest())b.append(String.format(Locale.ROOT,"%02x",x&255));
        return b.toString();
    }
    private static void savePng(Bitmap bitmap,File destination)throws IOException{
        File temporary=new File(destination.getParentFile(),destination.getName()+".partial");
        try(FileOutputStream out=new FileOutputStream(temporary)){
            if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))
                throw new IOException("Unable to encode image");
            out.getFD().sync();
        }catch(IOException e){temporary.delete();throw e;}
        if(!temporary.renameTo(destination)){
            temporary.delete();throw new IOException("Unable to commit image");
        }
    }
    private static Bitmap decodeSelected(Context ctx,Uri source)throws IOException{
        BitmapFactory.Options opts=new BitmapFactory.Options();
        opts.inJustDecodeBounds=true;
        try(InputStream in=ctx.getContentResolver().openInputStream(source)){
            if(in==null)throw new IOException("Unavailable imported image");
            BitmapFactory.decodeStream(in,null,opts);
        }
        if(opts.outWidth<=0||opts.outHeight<=0)throw new IOException("Invalid image");
        int sample=1;
        while(opts.outWidth/sample>SOURCE_MAX*2 ||
              opts.outHeight/sample>SOURCE_MAX*2)sample*=2;
        BitmapFactory.Options scaledOpts=new BitmapFactory.Options();
        scaledOpts.inSampleSize=sample;
        Bitmap loaded;
        try(InputStream in=ctx.getContentResolver().openInputStream(source)){
            if(in==null)throw new IOException("Unavailable imported image");
            loaded=BitmapFactory.decodeStream(in,null,scaledOpts);
        }
        if(loaded==null)throw new IOException("Unsupported image");
        if(loaded.getWidth()<=SOURCE_MAX&&loaded.getHeight()<=SOURCE_MAX)return loaded;
        float factor=Math.min((float)SOURCE_MAX/loaded.getWidth(),
                              (float)SOURCE_MAX/loaded.getHeight());
        Bitmap smaller=Bitmap.createScaledBitmap(loaded,
            Math.max(1,Math.round(loaded.getWidth()*factor)),
            Math.max(1,Math.round(loaded.getHeight()*factor)),true);
        loaded.recycle();return smaller;
    }
    /** Duplicates are identified by the content of the locally decoded source. */
    public static int importImages(Context ctx,List<Uri> sources,
                                                 AtomicBoolean flag,IntConsumer progress)
        throws Exception{
        int added=0;
        for(int i=0;i<sources.size();++i){
            cancelled(flag);
            Bitmap bitmap=decodeSelected(ctx,sources.get(i));
            File temp=cache(ctx,"incoming_"+i+".png");
            try{savePng(bitmap,temp);}finally{bitmap.recycle();}
            String id;
            try{id=hash(temp);}catch(Exception error){temp.delete();throw error;}
            File dir=new File(root(ctx),"image_"+id.substring(0,24));
            if(!dir.exists()){
                if(!dir.mkdirs()){temp.delete();throw new IOException("Cannot create image entry");}
                if(!temp.renameTo(new File(dir,PHOTO))){
                    temp.delete();throw new IOException("Cannot store imported image");
                }
                writeImportedName(ctx,sources.get(i),dir);
                saveSourceUri(sources.get(i),dir);
                added++;
            }else {
                temp.delete();
                // Older cached imports have no source URI: learn it when the
                // user selects the duplicate again without duplicating media.
                saveSourceUri(sources.get(i),dir);
                if(!new File(dir,DISPLAY_NAME).isFile())
                    writeImportedName(ctx,sources.get(i),dir);
            }
            progress.accept((i+1)*100/sources.size());
        }
        // Invalidate gallery atlases only if a genuinely new image arrived.
        if(added>0){
            wall(ctx).delete();wallDepth(ctx).delete();
            sphere(ctx).delete();sphereDepth(ctx).delete();
        }
        return added;
    }
    /**
     * Import standard PNG/JPEG/WebP images inside a ZIP without extracting any
     * archive paths to disk. ZIP names are labels only, preventing zip-slip.
     * Each entry is capped before bitmap decoding; all decoded images enter
     * the same content-addressed batch library as individual imports.
     */
    public static int importZip(Context ctx,Uri archive,AtomicBoolean cancel,
                                IntConsumer progress)throws Exception{
        final long maxCompressedOrExpandedImage=32L*1024*1024;
        final long maxArchiveInput=1024L*1024*1024;
        final int maxEntries=2000;
        int added=0,scanned=0;
        long totalUncompressed=0;
        try(InputStream raw=ctx.getContentResolver().openInputStream(archive)){
            if(raw==null)throw new IOException("Cannot open ZIP");
            try(java.util.zip.ZipInputStream zip=new java.util.zip.ZipInputStream(
                    new java.io.BufferedInputStream(raw))){
                java.util.zip.ZipEntry entry;
                while((entry=zip.getNextEntry())!=null){
                    cancelled(cancel);
                    if(++scanned>maxEntries)throw new IOException("ZIP has too many entries");
                    if(entry.isDirectory()){zip.closeEntry();continue;}
                    String filename=entry.getName();
                    String lower=filename.toLowerCase(Locale.ROOT);
                    boolean video=lower.endsWith(".mp4");
                    if(!(video||lower.endsWith(".png")||lower.endsWith(".jpg")||
                         lower.endsWith(".jpeg")||lower.endsWith(".webp"))){
                        zip.closeEntry();continue;
                    }
                    if(video){
                        // Stream MP4 directly to a bounded private file, never buffer
                        // a 256 MB movie in the Quest Java heap.
                        added+=importVideoStream(ctx,zip,cancel,cache(ctx,"zip_incoming_video.mp4"));
                        zip.closeEntry();
                        progress.accept(Math.min(99,scanned*100/maxEntries));
                        continue;
                    }
                    java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
                    byte[] chunk=new byte[32768];int n;long thisEntry=0;
                    while((n=zip.read(chunk))!=-1){
                        cancelled(cancel);
                        thisEntry+=n;
                        totalUncompressed+=n;
                        if(thisEntry>(video?VIDEO_MAX_BYTES:maxCompressedOrExpandedImage)||
                           totalUncompressed>maxArchiveInput)
                            throw new IOException("ZIP image or archive is too large");
                        bytes.write(chunk,0,n);
                    }
                    byte[] imageBytes=bytes.toByteArray();
                    android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();
                    opts.inJustDecodeBounds=true;
                    BitmapFactory.decodeByteArray(imageBytes,0,imageBytes.length,opts);
                    if(opts.outWidth<=0||opts.outHeight<=0||
                       (long)opts.outWidth*opts.outHeight>100000000L)
                        throw new IOException("Invalid or oversized ZIP image: "+filename);
                    int sample=1;
                    while(opts.outWidth/sample>SOURCE_MAX*2||
                          opts.outHeight/sample>SOURCE_MAX*2)sample*=2;
                    android.graphics.BitmapFactory.Options decodeOpts=new android.graphics.BitmapFactory.Options();
                    decodeOpts.inSampleSize=sample;
                    Bitmap bitmap=BitmapFactory.decodeByteArray(imageBytes,0,imageBytes.length,decodeOpts);
                    if(bitmap==null)throw new IOException("Cannot decode ZIP image: "+filename);
                    Bitmap ready=bitmap;
                    try{
                        if(bitmap.getWidth()>SOURCE_MAX||bitmap.getHeight()>SOURCE_MAX){
                            float ratio=Math.min((float)SOURCE_MAX/bitmap.getWidth(),
                                (float)SOURCE_MAX/bitmap.getHeight());
                            ready=Bitmap.createScaledBitmap(bitmap,
                                Math.max(1,Math.round(bitmap.getWidth()*ratio)),
                                Math.max(1,Math.round(bitmap.getHeight()*ratio)),true);
                        }
                        File temp=cache(ctx,"zip_incoming.png");
                        savePng(ready,temp);
                        String id=hash(temp);
                        File folder=new File(root(ctx),"image_"+id.substring(0,24));
                        if(!folder.exists()){
                            if(!folder.mkdirs()){temp.delete();throw new IOException("Cannot create image entry");}
                            if(!temp.renameTo(new File(folder,PHOTO))){
                                temp.delete();throw new IOException("Cannot store ZIP image");
                            }
                            String safeName=filename.replace('\\','/');
                            safeName=safeName.substring(safeName.lastIndexOf('/')+1);
                            if(safeName.length()>110)safeName=safeName.substring(0,110);
                            try(FileOutputStream output=new FileOutputStream(new File(folder,DISPLAY_NAME))){
                                output.write(safeName.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            }
                            added++;
                        }else temp.delete();
                    }finally{
                        if(ready!=bitmap)ready.recycle();
                        bitmap.recycle();
                    }
                    zip.closeEntry();
                    progress.accept(Math.min(99,scanned*100/maxEntries));
                }
            }
        }
        if(added>0){
            wall(ctx).delete();wallDepth(ctx).delete();
            sphere(ctx).delete();sphereDepth(ctx).delete();
        }
        progress.accept(100);
        if(scanned==0)throw new IOException("ZIP is empty");
        return added;
    }
    private static final String VIDEO="video.mp4";
    private static final long VIDEO_MAX_BYTES=256L*1024*1024;
    /** Hardware playable MP4 is copied into app-private library storage. */
    public static int importVideo(Context ctx,Uri uri,AtomicBoolean cancel)throws Exception{
        File temp=cache(ctx,"incoming_video.mp4");
        try(InputStream input=ctx.getContentResolver().openInputStream(uri)){
            if(input==null)throw new IOException("Cannot read MP4");
            return importVideoStream(ctx,input,cancel,temp);
        }
    }
    private static int importVideoStream(Context ctx,InputStream input,
                                         AtomicBoolean cancel,File temp)throws Exception{
        long total=0;
        try{
            try(FileOutputStream out=new FileOutputStream(temp)){
                byte[] buf=new byte[65536];int n;
                while((n=input.read(buf))!=-1){
                    cancelled(cancel);
                    total+=n;
                    if(total>VIDEO_MAX_BYTES)throw new IOException("MP4 exceeds 256 MB import limit");
                    out.write(buf,0,n);
                }
                out.getFD().sync();
            }
            android.media.MediaMetadataRetriever retriever=
                new android.media.MediaMetadataRetriever();
            Bitmap frame=null;
            try{
                retriever.setDataSource(temp.getAbsolutePath());
                frame=retriever.getFrameAtTime(0,
                    android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            }finally{retriever.release();}
            if(frame==null)throw new IOException("Unable to decode MP4 thumbnail");
            String id=hash(temp);
            File directory=new File(root(ctx),"image_"+id.substring(0,24));
            if(directory.exists()){frame.recycle();return 0;}
            if(!directory.mkdirs()){frame.recycle();throw new IOException("Cannot store video");}
            try{
                Bitmap scaled=frame;
                try{
                    if(frame.getWidth()>SOURCE_MAX||frame.getHeight()>SOURCE_MAX){
                        float ratio=Math.min((float)SOURCE_MAX/frame.getWidth(),
                            (float)SOURCE_MAX/frame.getHeight());
                        scaled=Bitmap.createScaledBitmap(frame,
                            Math.max(1,Math.round(frame.getWidth()*ratio)),
                            Math.max(1,Math.round(frame.getHeight()*ratio)),true);
                    }
                    savePng(scaled,new File(directory,PHOTO));
                }finally{
                    if(scaled!=frame)scaled.recycle();
                    frame.recycle();
                }
                if(!temp.renameTo(new File(directory,VIDEO)))
                    throw new IOException("Cannot commit imported video");
                try(FileOutputStream out=new FileOutputStream(new File(directory,DISPLAY_NAME))){
                    out.write("Imported MP4".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }catch(Exception failure){
                deleteGalleryEntry(directory);
                throw failure;
            }
            wall(ctx).delete();wallDepth(ctx).delete();
            sphere(ctx).delete();sphereDepth(ctx).delete();
            return 1;
        }finally{temp.delete();}
    }
    /** Map all repeated video tiles to up to two unique active video decoders. */
    public static String[] galleryVideoPaths(Context ctx){
        java.util.ArrayList<String> paths=new java.util.ArrayList<>();
        for(File folder:entries(ctx)){
            File video=new File(folder,VIDEO);
            if(video.isFile()&&paths.size()<2)paths.add(video.getAbsolutePath());
        }
        return paths.toArray(new String[0]);
    }
    public static int[] galleryVideoTileSlots(Context ctx){
        File[] tiles=sphereTiles(ctx,entries(ctx));
        String[] paths=galleryVideoPaths(ctx);
        int[] mapping=new int[96];
        for(int i=0;i<Math.min(96,tiles.length);i++){
            String path=new File(tiles[i],VIDEO).getAbsolutePath();
            for(int slot=0;slot<paths.length;slot++){
                if(path.equals(paths[slot])){mapping[i]=slot+1;break;}
            }
        }
        return mapping;
    }
    private static void saveGray(byte[] pixels,File out)throws IOException{
        // Depth Anything V2 returns 256x256; our editable depth cache is 1024x1024.
        // Resample at save time, preserving the existing editor/brush contract.
        final int edge=(int)Math.sqrt(pixels.length);
        if((edge!=256&&edge!=DEPTH_EDGE)||edge*edge!=pixels.length)
            throw new IOException("Unsupported AI depth dimensions: "+pixels.length);
        int[] argb=new int[pixels.length];
        for(int i=0;i<argb.length;i++){
            int gray=pixels[i]&255;
            argb[i]=Color.argb(255,gray,gray,gray);
        }
        Bitmap source=Bitmap.createBitmap(argb,edge,edge,Bitmap.Config.ARGB_8888);
        Bitmap scaled=source;
        try{
            if(edge!=DEPTH_EDGE)
                scaled=Bitmap.createScaledBitmap(source,DEPTH_EDGE,DEPTH_EDGE,true);
            savePng(scaled,out);
        }finally{
            if(scaled!=source)scaled.recycle();
            source.recycle();
        }
    }
    /** Process only missing cached results. Never touches the current editor image. */
    public static void convertMissing(Context ctx,AtomicBoolean cancel,IntConsumer progress)
        throws Exception {
        File[] list=entries(ctx);
        int total=list.length;
        if(total==0)throw new IOException("Import images first");
        for(int i=0;i<total;i++){
            cancelled(cancel);
            final int current=i, size=total;
            File entry=list[i],depth=new File(entry,DEPTH),mask=new File(entry,MASK);
            File maskAttempted=new File(entry,"mask_attempted");
            if(!depth.isFile()){
                byte[] result=AiDepth.infer(ctx,cancel,p->
                    progress.accept((current*100+Math.min(79,p*80/100))/size),
                    new File(entry,PHOTO));
                cancelled(cancel);
                saveGray(result,depth);
            }
            // Segmentation is optional; failures must not delete usable depth.
            if(!mask.isFile()&&!maskAttempted.isFile()){
                try{
                    byte[] subject=AiSegment.infer(ctx,cancel,p->
                        progress.accept((current*100+80+Math.min(19,p/5))/size),
                        new File(entry,PHOTO));
                    cancelled(cancel);
                    try(FileOutputStream stream=new FileOutputStream(mask)){
                        stream.write(subject);stream.getFD().sync();
                    }
                }catch(IOException e){
                    if(cancel.get())throw e;
                    // Missing a distinct foreground is valid for landscapes.
                }catch(Exception e){
                    if(cancel.get())throw e;
                }
                // Unsegmentable landscapes should not be rescanned on every
                // batch request; model retry can be a separate future option.
                if(!cancel.get())maskAttempted.createNewFile();
            }
            progress.accept((i+1)*100/total);
        }
        wall(ctx).delete();wallDepth(ctx).delete();
        sphere(ctx).delete();sphereDepth(ctx).delete();
    }
    private static Bitmap decode(File file){
        return file.isFile()?BitmapFactory.decodeFile(file.getAbsolutePath()):null;
    }
    private static void drawTiles(Context ctx,File[] items,Canvas canvas,int width,int height,
                                  int cols,int rows,boolean depths,boolean gallerySection,IntConsumer progress){
        Paint backdrop=new Paint();
        // The cylindrical gallery needs transparent gaps so its environment is visible.
        // Existing wall exports continue to use their original solid background.
        if(!gallerySection)canvas.drawColor(depths?Color.rgb(96,96,96):Color.rgb(19,17,28));
        int tileW=width/cols,tileH=height/rows;
        for(int i=0;i<items.length;i++){
            Bitmap image=decode(new File(items[i],depths?DEPTH:PHOTO));
            if(image!=null){
                try{
                    int ix=i%cols,iy=i/cols;
                    int[] settings=gallerySection?layout(ctx):null;
                    float margin=gallerySection?
                        Math.max(1f,Math.min(tileW,tileH)*settings[0]/100f):
                        depths?2:Math.max(4,Math.min(tileW,tileH)/45f);
                    float innerW=tileW-2*margin,innerH=tileH-2*margin;
                    float scale=Math.min(innerW/image.getWidth(),innerH/image.getHeight());
                    float drawW=image.getWidth()*scale,drawH=image.getHeight()*scale;
                    float left=ix*tileW+(tileW-drawW)/2.f;
                    float top=iy*tileH+(tileH-drawH)/2.f;
                    canvas.drawBitmap(image,null,new android.graphics.RectF(
                        left,top,left+drawW,top+drawH),paint);
                }finally{image.recycle();}
            }
            if(progress!=null)progress.accept((i+1)*100/items.length);
        }
    }
    private static Bitmap produce(Context ctx,boolean spherical,boolean depths,
                                  IntConsumer progress)throws IOException{
        File[] originals=entries(ctx);
        if(originals.length==0)throw new IOException("Import images first");
        File[] list=spherical?sphereTiles(ctx,originals):originals;
        // Keep the VR wall/sphere atlases bounded even for large libraries.
        int columns=spherical?Math.max(4,(int)Math.ceil(Math.sqrt(list.length*2.0))):
                              Math.max(1,(int)Math.ceil(Math.sqrt(list.length)));
        // Keep small walls small instead of allocating 64 MiB for one image.
        final int side=Math.min(3072,Math.max(1024,columns*512));
        final int width=spherical?(depths?2048:4096):depths?1024:side;
        final int height=spherical?(depths?1024:2048):depths?1024:side;
        Bitmap atlas=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        int rows=(list.length+columns-1)/columns;
        Canvas canvas=new Canvas(atlas);
        if(!spherical){
            drawTiles(ctx,list,canvas,width,height,columns,rows,depths,false,progress);
        }else{
            // The ceiling and floor belong to the chosen environment.
            // All pictures live on the wall, which occupies the central 50%
            // of the atlas. Leave the caps fully transparent.
            canvas.drawColor(Color.TRANSPARENT,android.graphics.PorterDuff.Mode.CLEAR);
            canvas.save();
            canvas.translate(0,height/4);
            canvas.clipRect(0,0,width,height/2);
            int density=layout(ctx)[1];
            int wallColumns=Math.max(1,(int)Math.ceil(Math.sqrt(list.length*density)));
            int wallRows=(list.length+wallColumns-1)/wallColumns;
            drawTiles(ctx,list,canvas,width,height/2,wallColumns,wallRows,
                depths,true,progress);
            canvas.restore();
        }
        return atlas;
    }
    public static void build(Context ctx,boolean spherical,
                                          IntConsumer progress)throws IOException{
        File output=spherical?sphere(ctx):wall(ctx);
        File outputDepth=spherical?sphereDepth(ctx):wallDepth(ctx);
        if(output.isFile()&&outputDepth.isFile()){
            progress.accept(100);
            return;
        }
        Bitmap photo=produce(ctx,spherical,false,p->
            progress.accept(Math.min(75,p*3/4)));
        try{savePng(photo,output);}finally{photo.recycle();}
        Bitmap depth=produce(ctx,spherical,true,p->
            progress.accept(75+Math.min(25,p/4)));
        try{savePng(depth,outputDepth);}finally{depth.recycle();}
        progress.accept(100);
    }
    private static File savedRoot(Context ctx){
        File directory=new File(ctx.getFilesDir(),"vr_saved_cylinders");
        if(!directory.isDirectory()&&!directory.mkdirs())
            throw new IllegalStateException("Cannot create saved cylinder library");
        return directory;
    }
    public static synchronized String saveCylinder(Context ctx,int theme)throws IOException{
        if(!sphere(ctx).isFile()||!sphereDepth(ctx).isFile())
            throw new IOException("Build the cylinder before saving");
        File folder=new File(savedRoot(ctx),
            "room_"+new java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS",
                Locale.ROOT).format(new java.util.Date()));
        if(!folder.mkdirs())throw new IOException("Cannot create cylinder");
        try{
            java.nio.file.Files.copy(sphere(ctx).toPath(),
                new File(folder,"wall.png").toPath());
            java.nio.file.Files.copy(sphereDepth(ctx).toPath(),
                new File(folder,"depth.png").toPath());
            try(FileOutputStream stream=new FileOutputStream(new File(folder,"theme.txt"))){
                stream.write(Integer.toString(Math.max(0,Math.min(5,theme)))
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                stream.getFD().sync();
            }
            try(FileOutputStream stream=new FileOutputStream(new File(folder,"radius.txt"))){
                stream.write(Integer.toString(radiusCm(ctx))
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                stream.getFD().sync();
            }
            return folder.getName();
        }catch(IOException e){
            for(File item:folder.listFiles()==null?new File[0]:folder.listFiles())
                item.delete();
            folder.delete();
            throw e;
        }
    }
    private static File[] savedEntries(Context ctx){
        File[] list=savedRoot(ctx).listFiles(f->f.isDirectory()&&
            new File(f,"wall.png").isFile()&&new File(f,"depth.png").isFile());
        if(list==null)return new File[0];
        Arrays.sort(list,(a,b)->b.getName().compareTo(a.getName()));
        return list;
    }
    private static File savedAt(Context ctx,int index)throws IOException{
        File[] entries=savedEntries(ctx);
        if(index<0||index>=entries.length)throw new IOException("Cylinder no longer exists");
        return entries[index];
    }
    public static String savedCylinderLabel(Context ctx,int index){
        try{
            File entry=savedAt(ctx,index);
            File name=new File(entry,"name.txt");
            if(name.isFile()){
                byte[] bytes=java.nio.file.Files.readAllBytes(name.toPath());
                String custom=new String(bytes,java.nio.charset.StandardCharsets.UTF_8).trim();
                if(!custom.isEmpty())return custom;
            }
            return entry.getName().replace("room_","").replace('_',' ');
        }catch(Exception ignored){return "Saved cylinder";}
    }
    public static synchronized void renameCylinder(Context ctx,int index,String label)
            throws IOException{
        File entry=savedAt(ctx,index);
        String safe=label==null?"":label.trim().replace('\n',' ').replace('\r',' ');
        if(safe.isEmpty()||safe.length()>70)throw new IOException("Name must be 1-70 characters");
        try(FileOutputStream output=new FileOutputStream(new File(entry,"name.txt"))){
            output.write(safe.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.getFD().sync();
        }
    }
    public static synchronized void deleteCylinder(Context ctx,int index)throws IOException{
        File entry=savedAt(ctx,index);
        File[] children=entry.listFiles();
        if(children!=null)for(File file:children)
            if(!file.delete())throw new IOException("Couldn't delete saved cylinder file");
        if(!entry.delete())throw new IOException("Couldn't remove saved cylinder");
    }
    public static synchronized String duplicateCylinder(Context ctx,int index)throws IOException{
        File source=savedAt(ctx,index);
        File copy=new File(savedRoot(ctx),"room_"+new java.text.SimpleDateFormat(
            "yyyyMMdd_HHmmss_SSS",Locale.ROOT).format(new java.util.Date()));
        if(!copy.mkdirs())throw new IOException("Couldn't create cylinder copy");
        try{
            File[] items=source.listFiles();
            if(items==null)throw new IOException("Source unavailable");
            for(File file:items)if(file.isFile())
                java.nio.file.Files.copy(file.toPath(),new File(copy,file.getName()).toPath());
            String previous=savedCylinderLabel(ctx,index);
            try(FileOutputStream output=new FileOutputStream(new File(copy,"name.txt"))){
                output.write((previous+" (Copy)").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return copy.getName();
        }catch(IOException ex){
            File[] files=copy.listFiles();
            if(files!=null)for(File file:files)file.delete();
            copy.delete();
            throw ex;
        }
    }
    public static Bitmap savedThumbnail(Context ctx,int index){
        try{
            File file=new File(savedAt(ctx,index),"wall.png");
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inSampleSize=16;
            options.inPreferredConfig=Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getAbsolutePath(),options);
        }catch(Exception ignored){return null;}
    }
    public static String[] savedCylinderNames(Context ctx){
        File[] list=savedEntries(ctx);
        String[] names=new String[list.length];
        String[] themes={"Museum","Club","Tiki Party","Fantasy Fire",
            "Dungeon","Hanging on Trees"};
        for(int i=0;i<list.length;i++){
            String stamp=list[i].getName().replace("room_","").replace('_',' ');
            names[i]=savedCylinderLabel(ctx,i)+"  -  "+themes[savedCylinderTheme(ctx,i)];
        }
        return names;
    }
    public static int savedCylinderTheme(Context ctx,int index){
        File[] items=savedEntries(ctx);
        if(index<0||index>=items.length)return 0;
        try(InputStream in=new FileInputStream(new File(items[index],"theme.txt"))){
            byte[] data=new byte[8];
            int length=in.read(data);
            if(length>0)return Math.max(0,Math.min(5,Integer.parseInt(
                new String(data,0,length,java.nio.charset.StandardCharsets.UTF_8).trim())));
        }catch(Exception ignored){}
        return 0;
    }
    public static int[] loadSavedPixels(Context ctx,int index,boolean depth){
        File[] items=savedEntries(ctx);
        if(index<0||index>=items.length)return null;
        File file=new File(items[index],depth?"depth.png":"wall.png");
        Bitmap image=decode(file);
        if(image==null)return null;
        try{
            int w=image.getWidth(),h=image.getHeight();
            int[] result=new int[w*h+2];
            result[0]=w;result[1]=h;
            image.getPixels(result,2,w,0,0,w,h);
            return result;
        }finally{image.recycle();}
    }
    public static int[] loadPixels(Context ctx,boolean spherical,boolean depth){
        File file=spherical?(depth?sphereDepth(ctx):sphere(ctx)):
                             (depth?wallDepth(ctx):wall(ctx));
        Bitmap image=decode(file);
        if(image==null)return null;
        try{
            int width=image.getWidth(),height=image.getHeight();
            int[] out=new int[width*height+2];
            out[0]=width;out[1]=height;
            image.getPixels(out,2,width,0,0,width,height);
            return out;
        }finally{image.recycle();}
    }
    public static void openImage(Context ctx,int index)throws IOException{
        File[] list=entries(ctx);
        if(index<0||index>=list.length)throw new IOException("Batch image not found");
        File source=new File(list[index],PHOTO);
        File depth=new File(list[index],DEPTH);
        File target=new File(ctx.getFilesDir(),"vr_import.png");
        File depthTarget=new File(ctx.getFilesDir(),"vr_depth_quick.png");
        replace(source,target);
        if(depth.isFile())replace(depth,depthTarget);else depthTarget.delete();
        ProjectStore.imported(ctx,"BULK IMAGE "+(index+1));
        CollageStore.reset(ctx);
        ctx.getSharedPreferences("vr_batch",0).edit()
            .putString("active_id",list[index].getName()).apply();
        File aiMarker=new File(ctx.getFilesDir(),"vr_depth_ai_done.flag");
        if(depth.isFile())aiMarker.createNewFile();else aiMarker.delete();
    }
    /** Empty the current working gallery without deleting device originals,
     * independent editor projects, exports, or separately saved galleries. */
    public static synchronized void resetGallery(Context ctx)throws IOException{
        File[] imported=entries(ctx);
        for(File folder:imported)deleteGalleryEntry(folder);
        wall(ctx).delete();wallDepth(ctx).delete();
        sphere(ctx).delete();sphereDepth(ctx).delete();
        clearActive(ctx);
    }
    private static void deleteGalleryEntry(File target)throws IOException{
        File[] children=target.listFiles();
        if(children!=null)for(File child:children){
            if(child.isDirectory())deleteGalleryEntry(child);
            else if(!child.delete())throw new IOException("Couldn't clear gallery item");
        }
        if(!target.delete())throw new IOException("Couldn't clear gallery item folder");
    }
    public static void clearActive(Context ctx){
        ctx.getSharedPreferences("vr_batch",0).edit().remove("active_id").apply();
    }
    /** Sync a manually edited batch image back to its cached depth. */
    public static void syncActive(Context ctx)throws IOException{
        String id=ctx.getSharedPreferences("vr_batch",0).getString("active_id",null);
        if(id==null)return;
        File folder=new File(root(ctx),id);
        if(!folder.isDirectory())return;
        File active=new File(ctx.getFilesDir(),"vr_depth_quick.png");
        if(active.isFile())replace(active,new File(folder,DEPTH));
        wall(ctx).delete();wallDepth(ctx).delete();
        sphere(ctx).delete();sphereDepth(ctx).delete();
    }
    private static void replace(File source,File dest)throws IOException{
        File stage=new File(dest.getParentFile(),dest.getName()+".bulk.partial");
        try(InputStream in=new FileInputStream(source);
            FileOutputStream out=new FileOutputStream(stage)){
            byte[] buffer=new byte[65536];int n;
            while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            out.getFD().sync();
        }
        if(!stage.renameTo(dest)){
            stage.delete();throw new IOException("Failed to open cached image");
        }
    }
    public static void copyTo(Context ctx,boolean spherical,OutputStream stream,
                              IntConsumer progress)throws IOException{
        File file=spherical?sphere(ctx):wall(ctx);
        if(!file.isFile())throw new IOException("Generate a gallery first");
        long written=0;
        try(InputStream in=new FileInputStream(file)){
            byte[] buffer=new byte[65536];int n;
            while((n=in.read(buffer))!=-1){
                stream.write(buffer,0,n);
                written+=n;
                progress.accept((int)Math.min(100,100L*written/
                                          Math.max(1L,file.length())));
            }
        }
        stream.flush();
        progress.accept(100);
    }
}
