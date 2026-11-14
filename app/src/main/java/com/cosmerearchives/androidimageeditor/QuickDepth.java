package com.cosmerearchives.androidimageeditor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Small offline, deterministic Quick Convert prototype.
 * NOT neural network depth estimation. Results are approximate and depend on
 * brightness, center prominence, vertical location, and edge-preserving smoothing.
 */
public final class QuickDepth {
    // Quick heuristic generation is 512px; manual editing remains 1024px.
    private static final int W = 512;
    private static final int H = 512;
    private static final int EDIT_EDGE=1024;
    private static final String DEPTH_FILE = "vr_depth_quick.png";
    private static final String CANDIDATE = "vr_depth_ai_candidate.png";
    private QuickDepth() {}

    private static File path(Context ctx) { return new File(ctx.getFilesDir(), DEPTH_FILE); }

    public static synchronized void discardCandidate(Context ctx){
        new File(ctx.getFilesDir(),CANDIDATE).delete();
    }
    public static synchronized boolean hasCandidate(Context ctx){
        return new File(ctx.getFilesDir(),CANDIDATE).isFile();
    }
    public static synchronized void saveCandidate(Context ctx,byte[] gray) throws IOException {
        saveImage(new File(ctx.getFilesDir(),CANDIDATE),gray);
    }
    public static synchronized boolean applyCandidate(Context ctx) throws IOException {
        File candidate=new File(ctx.getFilesDir(),CANDIDATE);
        if(!candidate.isFile())return false;
        File staging=new File(ctx.getFilesDir(),DEPTH_FILE+".ai.new");
        try(java.io.FileInputStream source=new java.io.FileInputStream(candidate);
            FileOutputStream dest=new FileOutputStream(staging)){
            byte[] bytes=new byte[65536];int length;
            while((length=source.read(bytes))!=-1)dest.write(bytes,0,length);
            dest.getFD().sync();
        }
        if(!staging.renameTo(path(ctx))){
            staging.delete();
            throw new IOException("Unable to apply AI depth");
        }
        discardCandidate(ctx);
        return true;
    }
    private static void saveImage(File destination,byte[] gray) throws IOException {
        if(gray==null || gray.length!=256*256)
            throw new IOException("Invalid depth map dimensions");
        int[] encoded=new int[256*256];
        for(int i=0;i<encoded.length;i++){
            int value=gray[i]&255;encoded[i]=Color.argb(255,value,value,value);
        }
        Bitmap bitmap=Bitmap.createBitmap(encoded,256,256,Bitmap.Config.ARGB_8888);
        File stage=new File(destination.getParentFile(),destination.getName()+".partial");
        try {
            try(FileOutputStream stream=new FileOutputStream(stage)) {
                if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,stream))
                    throw new IOException("Depth preview PNG encoding failed");
                stream.getFD().sync();
            }
            if(!stage.renameTo(destination))
                throw new IOException("Depth preview could not be saved");
        }finally{bitmap.recycle();stage.delete();}
    }
    // Load legacy 256px and 512px depth PNGs plus native 1024px edits.
    // Upscaling preserves project compatibility but cannot invent detail.
    private static int[] normalizedPixels(File source) {
        Bitmap bitmap=BitmapFactory.decodeFile(source.getAbsolutePath());
        if(bitmap==null)return null;
        Bitmap resized=bitmap;
        try {
            if(bitmap.getWidth()!=bitmap.getHeight() ||
               (bitmap.getWidth()!=256 && bitmap.getWidth()!=512 &&
                bitmap.getWidth()!=EDIT_EDGE))return null;
            if(bitmap.getWidth()!=EDIT_EDGE)
                resized=Bitmap.createScaledBitmap(bitmap,EDIT_EDGE,EDIT_EDGE,true);
            int[] result=new int[EDIT_EDGE*EDIT_EDGE+2];
            result[0]=EDIT_EDGE;result[1]=EDIT_EDGE;
            resized.getPixels(result,2,EDIT_EDGE,0,0,EDIT_EDGE,EDIT_EDGE);
            return result;
        }finally{
            if(resized!=bitmap)resized.recycle();
            bitmap.recycle();
        }
    }
    public static synchronized int[] loadPreviewPixels(Context ctx) {
        File candidate=new File(ctx.getFilesDir(),CANDIDATE);
        if(candidate.isFile()){
            int[] preview=normalizedPixels(candidate);
            if(preview!=null)return preview;
        }
        return loadPixels(ctx);
    }
    public static synchronized void discard(Context ctx) {
        path(ctx).delete();
        discardCandidate(ctx);
    }

    public static boolean available(Context ctx) {
        return path(ctx).isFile();
    }

    public static int[] loadPixels(Context ctx) {
        return normalizedPixels(path(ctx));
    }

    /** Persist a 1024px corrected depth map; legacy depth PNGs remain readable. */
    public static synchronized void saveEdited(Context ctx, byte[] gray) throws IOException {
        if(gray==null || gray.length!=EDIT_EDGE*EDIT_EDGE)
            throw new IOException("Invalid depth-map dimensions");
        int[] argb=new int[EDIT_EDGE*EDIT_EDGE];
        for(int i=0;i<argb.length;i++){
            int value=gray[i] & 0xff;
            argb[i]=Color.argb(255,value,value,value);
        }
        Bitmap bitmap=Bitmap.createBitmap(argb,EDIT_EDGE,EDIT_EDGE,Bitmap.Config.ARGB_8888);
        File staging=new File(ctx.getFilesDir(),DEPTH_FILE+".edited.partial");
        try {
            try(FileOutputStream stream=new FileOutputStream(staging)){
                if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,stream))
                    throw new IOException("Unable to encode corrected depth map");
                stream.getFD().sync();
            }
            if(!staging.renameTo(path(ctx)))
                throw new IOException("Unable to commit corrected depth map");
        } finally {
            bitmap.recycle();
            staging.delete();
        }
    }

    public static void convert(Context ctx) throws IOException {
        Bitmap source = BitmapFactory.decodeFile(
            new File(ctx.getFilesDir(), "vr_import.png").getAbsolutePath());
        if (source == null) throw new IOException("Import a photograph first");
        Bitmap sample = null, depthBitmap = null;
        try {
            sample = Bitmap.createScaledBitmap(source, W, H, true);
            final int count=W*H;
            int[] pixels = new int[count];
            sample.getPixels(pixels, 0, W, 0, 0, W, H);
            float[] luminance = new float[count];
            float[] result = new float[count];
            float[] smooth = new float[count];
            // Keep full RGB at the native estimation grid. Equal-brightness
            // differently colored objects must remain distinct boundaries.
            int[] rgb = pixels;
            for(int y=0; y<H; y++) {
                for(int x=0; x<W; x++) {
                    int index=y*W+x, p=pixels[index];
                    float l=(0.2126f*Color.red(p)+0.7152f*Color.green(p)+
                             0.0722f*Color.blue(p))/255f;
                    luminance[index]=l;
                    float dx=(x-(W-1)*0.5f)/(W*0.5f);
                    float dy=(y-(H-1)*0.5f)/(H*0.5f);
                    float center=Math.max(0f,1f-(float)Math.sqrt(dx*dx+dy*dy));
                    float height=(H-1-y)/(float)(H-1);
                    result[index]=clamp(0.18f+0.36f*l+0.29f*center+
                                        0.17f*height,0.05f,0.95f);
                }
            }
            // RGB- and depth-guided smoothing preserves boundaries between
            // equal-luminance colors and avoids bleeding depth through edges.
            for(int y=0; y<H; y++) for(int x=0; x<W; x++) {
                int centerIndex=y*W+x;
                float sum=0, weights=0;
                for(int oy=-2; oy<=2; oy++) for(int ox=-2; ox<=2; ox++) {
                    int sx=Math.min(W-1,Math.max(0,x+ox));
                    int sy=Math.min(H-1,Math.max(0,y+oy));
                    int index=sy*W+sx;
                    int a=rgb[index],b=rgb[centerIndex];
                    float dr=(Color.red(a)-Color.red(b))/255f;
                    float dg=(Color.green(a)-Color.green(b))/255f;
                    float db=(Color.blue(a)-Color.blue(b))/255f;
                    float colorDistance=dr*dr+dg*dg+db*db;
                    float depthDifference=result[index]-result[centerIndex];
                    float w=(float)Math.exp(-(ox*ox+oy*oy)/4.0f -
                        colorDistance*22f-depthDifference*50f);
                    sum+=result[index]*w;
                    weights+=w;
                }
                smooth[centerIndex]=sum/Math.max(weights,0.00001f);
            }
            int[] encoded=new int[count];
            for(int i=0;i<count;i++) {
                int gray=Math.max(0,Math.min(255,Math.round(smooth[i]*255f)));
                encoded[i]=Color.argb(255,gray,gray,gray);
            }
            depthBitmap=Bitmap.createBitmap(encoded,W,H,Bitmap.Config.ARGB_8888);
            File staging=new File(ctx.getFilesDir(),DEPTH_FILE+".partial");
            try(FileOutputStream stream=new FileOutputStream(staging)) {
                if(!depthBitmap.compress(Bitmap.CompressFormat.PNG,100,stream))
                    throw new IOException("Depth map could not be saved");
                stream.getFD().sync();
            } catch(IOException error) {
                staging.delete();
                throw error;
            }
            if(!staging.renameTo(path(ctx))) {
                staging.delete();
                throw new IOException("Depth map could not be stored");
            }
        } finally {
            if(depthBitmap!=null) depthBitmap.recycle();
            if(sample!=null) sample.recycle();
            source.recycle();
        }
    }
    private static float clamp(float n,float a,float b) {
        return Math.max(a,Math.min(b,n));
    }
}
