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
    private static final int W = 256;
    private static final int H = 256;
    private static final String DEPTH_FILE = "vr_depth_quick.png";
    private QuickDepth() {}

    private static File path(Context ctx) { return new File(ctx.getFilesDir(), DEPTH_FILE); }

    public static void discard(Context ctx) {
        path(ctx).delete();
    }

    public static boolean available(Context ctx) {
        return path(ctx).isFile();
    }

    public static int[] loadPixels(Context ctx) {
        Bitmap bitmap = BitmapFactory.decodeFile(path(ctx).getAbsolutePath());
        if (bitmap == null) return null;
        try {
            if (bitmap.getWidth() != W || bitmap.getHeight() != H) return null;
            int[] result = new int[W*H+2];
            result[0]=W;
            result[1]=H;
            bitmap.getPixels(result, 2, W, 0, 0, W, H);
            return result;
        } finally { bitmap.recycle(); }
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
            // Edge-aware 5x5 bilateral smoothing reduces pixel-level flicker
            // without mixing strong luminance boundaries indiscriminately.
            for(int y=0; y<H; y++) for(int x=0; x<W; x++) {
                int centerIndex=y*W+x;
                float sum=0, weights=0;
                for(int oy=-2; oy<=2; oy++) for(int ox=-2; ox<=2; ox++) {
                    int sx=Math.min(W-1,Math.max(0,x+ox));
                    int sy=Math.min(H-1,Math.max(0,y+oy));
                    int index=sy*W+sx;
                    float dl=luminance[index]-luminance[centerIndex];
                    float w=(float)Math.exp(-(ox*ox+oy*oy)/4.0f - dl*dl*22f);
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
