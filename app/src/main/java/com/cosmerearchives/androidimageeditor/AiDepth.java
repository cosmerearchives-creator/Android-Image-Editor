package com.cosmerearchives.androidimageeditor;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.FloatBuffer;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

/**
 * Experimental local AI depth inference. The model is downloaded only on demand,
 * SHA256 verified, then cached privately for use without a network connection.
 * No edits to the existing depth map occur until explicit user confirmation.
 */
public final class AiDepth {
    // Immutable upstream model revision, not mutable main branch.
    private static final String URL_MODEL =
        "https://huggingface.co/onnx-community/depth-anything-v2-small/resolve/" +
        "4472b7362082ad9968fee890ca0f1e5aca36b93d/onnx/model_quantized.onnx";
    private static final String MODEL_SHA =
        "fcf51f1b230362b28690bb9d1809bf0431f29cad20534e3f589bd7285547f20d";
    private static final int SIDE = 518;
    private static final int OUT = 256;
    private static final long MAX_SIZE = 40_000_000L;
    private AiDepth() {}

    private static void checkCancel(AtomicBoolean cancel) throws IOException {
        if(cancel.get() || Thread.currentThread().isInterrupted())
            throw new IOException("Conversion cancelled");
    }
    private static File file(Context ctx) {
        return new File(ctx.getFilesDir(),"depth_anything_v2_small_quantized.onnx");
    }
    private static String hex(byte[] input) {
        StringBuilder sb=new StringBuilder();
        for(byte b:input)sb.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return sb.toString();
    }
    private static boolean correctHash(File file) throws IOException {
        if(!file.isFile() || file.length()<1 || file.length()>MAX_SIZE)return false;
        try(InputStream input=new java.io.FileInputStream(file)){
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            byte[] buf=new byte[65536];int read;
            while((read=input.read(buf))!=-1)digest.update(buf,0,read);
            return MODEL_SHA.equals(hex(digest.digest()));
        }catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}
    }
    public static File ensureModel(Context ctx,AtomicBoolean cancel,IntConsumer status)
        throws IOException {
        File dest=file(ctx);
        if(correctHash(dest)){status.accept(50);return dest;}
        File staging=new File(ctx.getFilesDir(),"ai_model.part");
        HttpURLConnection connection=null;
        try{
            connection=(HttpURLConnection)new URL(URL_MODEL).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent","AndroidImageEditor/0.7.0");
            int code=connection.getResponseCode();
            if(code!=200)throw new IOException("AI model download HTTP "+code);
            if(!"https".equalsIgnoreCase(connection.getURL().getProtocol()))
                throw new IOException("Refusing insecure model download");
            long declared=connection.getContentLengthLong();
            if(declared>MAX_SIZE)throw new IOException("Model exceeds download limit");
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            long count=0;
            try(InputStream input=connection.getInputStream();
                FileOutputStream output=new FileOutputStream(staging)){
                byte[] block=new byte[65536];
                int read;
                while((read=input.read(block))!=-1){
                    checkCancel(cancel);
                    count+=read;
                    if(count>MAX_SIZE)throw new IOException("Model exceeds download limit");
                    digest.update(block,0,read);
                    output.write(block,0,read);
                    if(declared>0)status.accept((int)Math.min(49,49*count/declared));
                }
                output.getFD().sync();
            }
            if(!MODEL_SHA.equals(hex(digest.digest())))
                throw new IOException("AI model SHA-256 mismatch");
            if(!staging.renameTo(dest))
                throw new IOException("Unable to cache verified AI model");
            status.accept(50);
            return dest;
        } catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}
        finally{
            if(connection!=null)connection.disconnect();
            staging.delete();
        }
    }

    private static float[] inputFor(Bitmap source) {
        // Fixed input shape used by this ONNX export. A future model revision can
        // add upstream aspect-preserving resize / padding.
        Bitmap small=Bitmap.createScaledBitmap(source,SIDE,SIDE,true);
        int[] pixels=new int[SIDE*SIDE];
        small.getPixels(pixels,0,SIDE,0,0,SIDE,SIDE);
        if(small!=source)small.recycle();
        float[] data=new float[3*SIDE*SIDE];
        final float[] mean={.485f,.456f,.406f};
        final float[] std={.229f,.224f,.225f};
        int plane=SIDE*SIDE;
        for(int i=0;i<plane;i++){
            int p=pixels[i];
            data[i]=((Color.red(p)/255.f)-mean[0])/std[0];
            data[plane+i]=((Color.green(p)/255.f)-mean[1])/std[1];
            data[2*plane+i]=((Color.blue(p)/255.f)-mean[2])/std[2];
        }
        return data;
    }
    private static float[][] extractDepth(Object value) throws IOException {
        if(value instanceof float[][][]) {
            float[][][] a=(float[][][])value;
            if(a.length>0)return a[0];
        } else if(value instanceof float[][][][]){
            float[][][][] a=(float[][][][])value;
            if(a.length>0&&a[0].length>0)return a[0][0];
        }
        throw new IOException("Unexpected AI depth output tensor shape");
    }
    public static byte[] infer(Context ctx, AtomicBoolean cancel, IntConsumer status)
        throws Exception {
        File model=ensureModel(ctx,cancel,status);
        checkCancel(cancel);
        Bitmap original=BitmapFactory.decodeFile(
            new File(ctx.getFilesDir(),"vr_import.png").getAbsolutePath());
        if(original==null)throw new IOException("Import an image before AI conversion");
        float[] input;
        try{input=inputFor(original);}finally{original.recycle();}
        checkCancel(cancel);
        status.accept(55);
        OrtEnvironment environment=OrtEnvironment.getEnvironment();
        try(OrtSession.SessionOptions options=new OrtSession.SessionOptions()){
            options.setIntraOpNumThreads(2);
            try(OrtSession session=environment.createSession(model.getAbsolutePath(),options);
                OnnxTensor tensor=OnnxTensor.createTensor(environment,
                    FloatBuffer.wrap(input),new long[]{1,3,SIDE,SIDE});
                OrtSession.Result result=session.run(Collections.singletonMap(
                    session.getInputNames().iterator().next(),tensor))){
                checkCancel(cancel);
                status.accept(90);
                float[][] depth=extractDepth(result.get(0).getValue());
                int h=depth.length,w=h==0?0:depth[0].length;
                if(h<16||w<16)throw new IOException("Invalid AI depth map output");
                float low=Float.POSITIVE_INFINITY, high=Float.NEGATIVE_INFINITY;
                for(float[] row:depth)for(float v:row)if(Float.isFinite(v)){
                    low=Math.min(low,v);high=Math.max(high,v);
                }
                if(!Float.isFinite(low)||!Float.isFinite(high)||high-low<1e-7f)
                    throw new IOException("AI model produced no usable depth range");
                byte[] gray=new byte[OUT*OUT];
                for(int y=0;y<OUT;y++){
                    int sy=Math.min(h-1,(int)((y+.5f)*h/OUT));
                    for(int x=0;x<OUT;x++){
                        int sx=Math.min(w-1,(int)((x+.5f)*w/OUT));
                        float value=depth[sy][sx];
                        if(!Float.isFinite(value))value=low;
                        float normalized=Math.max(0.f,Math.min(1.f,(value-low)/(high-low)));
                        gray[y*OUT+x]=(byte)Math.round(normalized*255);
                    }
                }
                checkCancel(cancel);
                status.accept(95);
                return gray;
            }
        }
    }
}
