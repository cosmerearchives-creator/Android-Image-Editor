package com.cosmerearchives.androidimageeditor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Local, offline, non-destructive collage layers. All source bitmaps stay separate. */
public final class CollageStore {
    private static final String DIR="vr_collage";
    private static final String MANIFEST="layers.json";
    private static final int MAX_LAYERS=12;
    private static final int SIZE=1024;
    private CollageStore(){}

    private static File dir(Context ctx){return new File(ctx.getFilesDir(),DIR);}
    private static File manifest(Context ctx){return new File(dir(ctx),MANIFEST);}
    private static File layer(Context ctx,int id){return new File(dir(ctx),"layer_"+id+".png");}
    private static File photo(Context ctx){return new File(ctx.getFilesDir(),"vr_import.png");}
    private static File depth(Context ctx){return new File(ctx.getFilesDir(),"vr_depth_quick.png");}

    private static void copy(File from,File to)throws IOException{
        try(FileInputStream in=new FileInputStream(from);FileOutputStream out=new FileOutputStream(to)){
            byte[] buffer=new byte[65536];int len;
            while((len=in.read(buffer))!=-1)out.write(buffer,0,len);
            out.getFD().sync();
        }
    }
    private static JSONObject read(Context ctx)throws Exception {
        File source=manifest(ctx);
        if(!source.isFile())return null;
        try(FileInputStream in=new FileInputStream(source)){
            byte[] bytes=new byte[(int)Math.min(source.length(),128*1024)];
            int pos=0,n;
            while(pos<bytes.length&&(n=in.read(bytes,pos,bytes.length-pos))>0)pos+=n;
            return new JSONObject(new String(bytes,0,pos,StandardCharsets.UTF_8));
        }
    }
    private static void write(Context ctx,JSONObject obj)throws Exception{
        File directory=dir(ctx);
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create collage directory");
        File temp=new File(directory,MANIFEST+".tmp");
        try(FileOutputStream out=new FileOutputStream(temp)){
            out.write(obj.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();
        }
        if(!temp.renameTo(manifest(ctx)))throw new IOException("Cannot save layer settings");
    }
    private static JSONObject item(int id){
        JSONObject o=new JSONObject();
        try{o.put("id",id);o.put("x",0.5);o.put("y",0.5);o.put("size",1.0);o.put("depth",0.5);}
        catch(Exception e){throw new IllegalStateException(e);}
        return o;
    }
    public static synchronized void reset(Context ctx) {
        removeTree(dir(ctx));
    }
    private static void removeTree(File file){
        if(file.isDirectory()){
            File[] children=file.listFiles();
            if(children!=null)for(File child:children)removeTree(child);
        }
        file.delete();
    }
    private static JSONObject ensure(Context ctx)throws Exception{
        JSONObject state=read(ctx);
        if(state!=null)return state;
        if(!photo(ctx).isFile())throw new IOException("Import an image first");
        File folder=dir(ctx);
        if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Cannot create collage");
        copy(photo(ctx),layer(ctx,0));
        JSONArray items=new JSONArray();items.put(item(0));
        state=new JSONObject();state.put("layers",items);state.put("active",0);state.put("next",1);
        write(ctx,state);
        return state;
    }
    public static synchronized int[] status(Context ctx){
        try{
            JSONObject state=read(ctx);
            if(state==null)return new int[]{0,0};
            JSONArray layers=state.getJSONArray("layers");
            return new int[]{layers.length(),Math.max(0,state.optInt("active",0))};
        }catch(Exception e){return new int[]{0,0};}
    }
    private static Bitmap decode(Context ctx,Uri uri)throws IOException{
        BitmapFactory.Options opts=new BitmapFactory.Options();
        opts.inJustDecodeBounds=true;
        try(InputStream in=ctx.getContentResolver().openInputStream(uri)){
            if(in==null)throw new IOException("Cannot open image");
            BitmapFactory.decodeStream(in,null,opts);
        }
        if(opts.outWidth<1||opts.outHeight<1)throw new IOException("Unsupported image");
        int sample=1;
        while(opts.outWidth/sample>2048||opts.outHeight/sample>2048)sample*=2;
        BitmapFactory.Options sized=new BitmapFactory.Options();sized.inSampleSize=sample;
        try(InputStream in=ctx.getContentResolver().openInputStream(uri)){
            if(in==null)throw new IOException("Cannot open image");
            Bitmap result=BitmapFactory.decodeStream(in,null,sized);
            if(result==null)throw new IOException("Invalid image");
            return result;
        }
    }
    public static synchronized void add(Context ctx,Uri uri)throws Exception{
        JSONObject state=ensure(ctx);
        JSONArray items=state.getJSONArray("layers");
        if(items.length()>=MAX_LAYERS)throw new IOException("Maximum 12 layers");
        int id=state.getInt("next");
        Bitmap bitmap=decode(ctx,uri);
        File target=layer(ctx,id);
        try(FileOutputStream out=new FileOutputStream(target)){
            if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Cannot save layer");
            out.getFD().sync();
        }finally{bitmap.recycle();}
        JSONObject entry=item(id);
        entry.put("size",0.55);
        items.put(entry);
        state.put("next",id+1);state.put("active",items.length()-1);
        write(ctx,state);
        compose(ctx,state);
    }
    // Action: 0 prev, 1 next, 2 left, 3 right, 4 up, 5 down,
    // 6 smaller, 7 larger, 8 backward (layer depth), 9 forward.
    public static synchronized void edit(Context ctx,int action)throws Exception{
        JSONObject state=ensure(ctx);
        JSONArray items=state.getJSONArray("layers");
        int total=items.length(),active=state.optInt("active",0);
        active=Math.max(0,Math.min(total-1,active));
        if(action==0)active=(active+total-1)%total;
        else if(action==1)active=(active+1)%total;
        else{
            JSONObject part=items.getJSONObject(active);
            String key=action==2||action==3?"x":action==4||action==5?"y":
                       action==6||action==7?"size":"depth";
            double step=(action==2||action==4||action==6||action==8)?-0.06:0.06;
            double value=part.getDouble(key)+step;
            part.put(key,Math.max(key.equals("size")?0.12:0.0,Math.min(key.equals("size")?2.8:1.0,value)));
        }
        state.put("active",active);
        write(ctx,state);
        if(action>=2)compose(ctx,state);
    }
    private static void saveBitmap(File file,Bitmap bitmap)throws IOException{
        File staging=new File(file.getPath()+".tmp");
        try(FileOutputStream out=new FileOutputStream(staging)){
            if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Could not save collage");
            out.getFD().sync();
        }
        if(!staging.renameTo(file))throw new IOException("Could not commit collage");
    }
    private static void compose(Context ctx,JSONObject state)throws Exception{
        JSONArray layers=state.getJSONArray("layers");
        Bitmap combined=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888);
        Bitmap depthMap=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);
        Canvas photoCanvas=new Canvas(combined),depthCanvas=new Canvas(depthMap);
        photoCanvas.drawColor(android.graphics.Color.TRANSPARENT,android.graphics.PorterDuff.Mode.CLEAR);
        depthCanvas.drawColor(android.graphics.Color.rgb(128,128,128));
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        for(int i=0;i<layers.length();i++){
            JSONObject entry=layers.getJSONObject(i);
            Bitmap src=BitmapFactory.decodeFile(layer(ctx,entry.getInt("id")).getPath());
            if(src==null)continue;
            try{
                float scale=(float)entry.getDouble("size");
                float cx=(float)entry.getDouble("x")*SIZE;
                float cy=(float)entry.getDouble("y")*SIZE;
                float aspect=(float)src.getWidth()/Math.max(1,src.getHeight());
                float w=scale*SIZE,h=scale*SIZE;
                if(aspect>=1)h/=aspect;else w*=aspect;
                RectF bounds=new RectF(cx-w/2,cy-h/2,cx+w/2,cy+h/2);
                photoCanvas.drawBitmap(src,null,bounds,paint);
                // A layer's alpha defines the area where its independent depth applies.
                Bitmap alpha=Bitmap.createScaledBitmap(src,256,256,true);
                Bitmap coverage=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);
                Canvas mask=new Canvas(coverage);
                RectF db=new RectF(bounds.left/4,bounds.top/4,bounds.right/4,bounds.bottom/4);
                mask.drawBitmap(src,null,db,paint);
                int[] pixels=new int[65536];coverage.getPixels(pixels,0,256,0,0,256,256);
                int gray=Math.max(0,Math.min(255,(int)Math.round(entry.getDouble("depth")*255)));
                Paint dp=new Paint();
                // Apply coverage using SRC_OVER with source alpha to preserve feathered transparency.
                for(int k=0;k<pixels.length;k++){
                    int alphaValue=(pixels[k]>>>24)&255;
                    pixels[k]=(alphaValue<<24)|(gray<<16)|(gray<<8)|gray;
                }
                coverage.setPixels(pixels,0,256,0,0,256,256);
                depthCanvas.drawBitmap(coverage,0,0,dp);
                alpha.recycle();coverage.recycle();
            }finally{src.recycle();}
        }
        try{saveBitmap(photo(ctx),combined);saveBitmap(depth(ctx),depthMap);}
        finally{combined.recycle();depthMap.recycle();}
    }
    public static synchronized void saveToProject(Context ctx,File destination)throws IOException{
        File source=dir(ctx),target=new File(destination,DIR);
        removeTree(target);
        if(!manifest(ctx).isFile())return;
        if(!target.mkdirs())throw new IOException("Cannot save collage layers");
        File[] files=source.listFiles();
        if(files!=null)for(File part:files)if(part.isFile())copy(part,new File(target,part.getName()));
    }
    public static synchronized void openFromProject(Context ctx,File project)throws IOException{
        reset(ctx);
        File source=new File(project,DIR);
        if(!new File(source,MANIFEST).isFile())return;
        File target=dir(ctx);
        if(!target.mkdirs())throw new IOException("Cannot open collage layers");
        File[] files=source.listFiles();
        if(files!=null)for(File part:files)if(part.isFile())copy(part,new File(target,part.getName()));
    }
}
