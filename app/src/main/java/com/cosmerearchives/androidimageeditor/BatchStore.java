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
    public static final int WARN_COUNT=16;
    private static final int SOURCE_MAX=2048;
    private static final int DEPTH_EDGE=1024;
    private static final String PHOTO="source.png",DEPTH="depth.png",MASK="mask.bin";
    private static final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private BatchStore(){}

    private static File root(Context context){
        File out=new File(context.getFilesDir(),"vr_batch");
        if(!out.isDirectory()&&!out.mkdirs())throw new IllegalStateException("Batch storage unavailable");
        return out;
    }
    private static File cache(Context ctx,String name){return new File(root(ctx),name);}
    public static File wall(Context ctx){return cache(ctx,"wall.png");}
    public static File wallDepth(Context ctx){return cache(ctx,"wall_depth.png");}
    public static File sphere(Context ctx){return cache(ctx,"sphere.png");}
    public static File sphereDepth(Context ctx){return cache(ctx,"sphere_depth.png");}
    private static File[] entries(Context ctx){
        File[] items=root(ctx).listFiles(f->f.isDirectory()&&new File(f,PHOTO).isFile());
        if(items==null)return new File[0];
        Arrays.sort(items,Comparator.comparing(File::getName));
        return items;
    }
    public static synchronized int count(Context ctx){return entries(ctx).length;}
    public static synchronized int converted(Context ctx){
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
    public static synchronized int importImages(Context ctx,List<Uri> sources,
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
                added++;
            }else temp.delete();
            progress.accept((i+1)*100/sources.size());
        }
        // These contact sheets may be stale after new imports.
        wall(ctx).delete();wallDepth(ctx).delete();
        sphere(ctx).delete();sphereDepth(ctx).delete();
        return added;
    }
    private static void saveGray(byte[] pixels,File out)throws IOException{
        if(pixels.length!=DEPTH_EDGE*DEPTH_EDGE)throw new IOException("Invalid depth dimensions");
        int[] argb=new int[pixels.length];
        for(int i=0;i<argb.length;i++){
            int gray=pixels[i]&255;
            argb[i]=Color.argb(255,gray,gray,gray);
        }
        Bitmap map=Bitmap.createBitmap(argb,DEPTH_EDGE,DEPTH_EDGE,Bitmap.Config.ARGB_8888);
        try{savePng(map,out);}finally{map.recycle();}
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
                                  int cols,int rows,boolean depths,IntConsumer progress){
        Paint backdrop=new Paint();
        canvas.drawColor(depths?Color.rgb(96,96,96):Color.rgb(19,17,28));
        int tileW=width/cols,tileH=height/rows;
        for(int i=0;i<items.length;i++){
            Bitmap image=decode(new File(items[i],depths?DEPTH:PHOTO));
            if(image!=null){
                try{
                    int ix=i%cols,iy=i/cols;
                    float margin=depths?2:Math.max(4,Math.min(tileW,tileH)/45f);
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
        File[] list=entries(ctx);
        if(list.length==0)throw new IOException("Import images first");
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
        drawTiles(ctx,list,canvas,width,height,columns,rows,depths,progress);
        return atlas;
    }
    public static synchronized void build(Context ctx,boolean spherical,
                                          IntConsumer progress)throws IOException{
        File output=spherical?sphere(ctx):wall(ctx);
        File outputDepth=spherical?sphereDepth(ctx):wallDepth(ctx);
        Bitmap photo=produce(ctx,spherical,false,p->
            progress.accept(Math.min(75,p*3/4)));
        try{savePng(photo,output);}finally{photo.recycle();}
        Bitmap depth=produce(ctx,spherical,true,p->
            progress.accept(75+Math.min(25,p/4)));
        try{savePng(depth,outputDepth);}finally{depth.recycle();}
        progress.accept(100);
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
    public static synchronized void openImage(Context ctx,int index)throws IOException{
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
    public static void clearActive(Context ctx){
        ctx.getSharedPreferences("vr_batch",0).edit().remove("active_id").apply();
    }
    /** Sync a manually edited batch image back to its cached depth. */
    public static synchronized void syncActive(Context ctx)throws IOException{
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
