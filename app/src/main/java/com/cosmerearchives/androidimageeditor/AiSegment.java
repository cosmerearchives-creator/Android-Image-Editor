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
 * On-device salient-subject segmentation using U2NetP, not a category detector.
 * Apache-2.0 U2-Net model by Qin et al. (Pattern Recognition 2020).
 * ONNX asset mirrored from rembg and SHA256 pinned; ~4.6 MB on first use.
 * Existing manual and depth-guided selection remain available.
 */
public final class AiSegment {
    private static final String MODEL_URL =
        "https://huggingface.co/edgetools/u2netp/resolve/" +
        "25dee37ab19c5b6ad64ba6578eba63f1ae07720c/u2netp.onnx";
    private static final String MODEL_SHA =
        "309c8469258dda742793dce0ebea8e6dd393174f89934733ecc8b14c76f4ddd8";
    private static final long MAX_BYTES=8_000_000L;
    private static final int SIDE=320;
    private static final int MASK_EDGE=1024;
    private AiSegment(){}

    private static void checkCancel(AtomicBoolean cancel) throws IOException {
        if(cancel.get()||Thread.currentThread().isInterrupted())
            throw new IOException("Segmentation cancelled");
    }
    private static File modelFile(Context ctx){
        return new File(ctx.getFilesDir(),"u2netp_checked.onnx");
    }
    private static String shaHex(byte[] data){
        StringBuilder out=new StringBuilder(data.length*2);
        for(byte b:data)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return out.toString();
    }
    private static MessageDigest sha256() throws IOException {
        try{return MessageDigest.getInstance("SHA-256");}
        catch(java.security.NoSuchAlgorithmException e){throw new IOException(e);}
    }
    private static boolean verify(File file) throws IOException {
        if(!file.isFile()||file.length()<1||file.length()>MAX_BYTES)return false;
        MessageDigest digest=sha256();
        try(InputStream input=new java.io.FileInputStream(file)){
            byte[] buf=new byte[65536];
            int n;
            while((n=input.read(buf))!=-1)digest.update(buf,0,n);
        }
        return MODEL_SHA.equals(shaHex(digest.digest()));
    }
    private static File ensureModel(Context ctx,AtomicBoolean cancel,IntConsumer status)
        throws IOException {
        File dest=modelFile(ctx);
        if(verify(dest)){status.accept(55);return dest;}
        File temp=new File(ctx.getFilesDir(),"u2netp_download.partial");
        HttpURLConnection connection=null;
        try{
            connection=(HttpURLConnection)new URL(MODEL_URL).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent","AndroidImageEditor/0.10.2");
            int response=connection.getResponseCode();
            if(response!=200)throw new IOException("Model download HTTP "+response);
            if(!"https".equalsIgnoreCase(connection.getURL().getProtocol()))
                throw new IOException("Insecure model redirect");
            long declared=connection.getContentLengthLong();
            if(declared>MAX_BYTES)throw new IOException("Model exceeds size limit");
            MessageDigest digest=sha256();
            long total=0;
            try(InputStream input=connection.getInputStream();
                FileOutputStream out=new FileOutputStream(temp)){
                byte[] buf=new byte[65536];
                int n;
                while((n=input.read(buf))!=-1){
                    checkCancel(cancel);
                    total+=n;
                    if(total>MAX_BYTES)throw new IOException("Model exceeds size limit");
                    digest.update(buf,0,n);
                    out.write(buf,0,n);
                    if(declared>0)status.accept((int)Math.min(50,50*total/declared));
                }
                out.getFD().sync();
            }
            if(!MODEL_SHA.equals(shaHex(digest.digest())))
                throw new IOException("AI segmentation model verification failed");
            if(!temp.renameTo(dest))throw new IOException("Unable to cache model");
            status.accept(55);
            return dest;
        }finally{
            if(connection!=null)connection.disconnect();
            temp.delete();
        }
    }

    private static float[] inputFor(Bitmap original) {
        Bitmap scaled=Bitmap.createScaledBitmap(original,SIDE,SIDE,true);
        try{
            int n=SIDE*SIDE;
            int[] pixels=new int[n];
            scaled.getPixels(pixels,0,SIDE,0,0,SIDE,SIDE);
            final float[] mean={.485f,.456f,.406f};
            final float[] std={.229f,.224f,.225f};
            float[] data=new float[n*3];
            float maxValue=0.f;
            for(int p:pixels){
                maxValue=Math.max(maxValue,Math.max(Color.red(p),
                    Math.max(Color.green(p),Color.blue(p)))/255.f);
            }
            maxValue=Math.max(maxValue,1.e-6f);
            for(int i=0;i<n;i++){
                int p=pixels[i];
                data[i]=((Color.red(p)/255.f/maxValue)-mean[0])/std[0];
                data[n+i]=((Color.green(p)/255.f/maxValue)-mean[1])/std[1];
                data[2*n+i]=((Color.blue(p)/255.f/maxValue)-mean[2])/std[2];
            }
            return data;
        }finally{if(scaled!=original)scaled.recycle();}
    }
    private static float[][] fusedOutput(Object raw) throws IOException{
        if(raw instanceof float[][][][]){
            float[][][][] v=(float[][][][])raw;
            if(v.length>0 && v[0].length>0)return v[0][0];
        }
        if(raw instanceof float[][][]){
            float[][][] v=(float[][][])raw;
            if(v.length>0)return v[0];
        }
        throw new IOException("Unexpected segmentation output");
    }

    public static byte[] infer(Context ctx,AtomicBoolean cancel,IntConsumer progress)
        throws Exception {
        return infer(ctx,cancel,progress,new File(ctx.getFilesDir(),"vr_import.png"));
    }
    public static byte[] infer(Context ctx,AtomicBoolean cancel,IntConsumer progress,
                               File photoFile) throws Exception {
        File model=ensureModel(ctx,cancel,progress);
        checkCancel(cancel);
        Bitmap original=BitmapFactory.decodeFile(
            photoFile.getAbsolutePath());
        if(original==null)throw new IOException("Import a photo first");
        float[] input;
        try{input=inputFor(original);}finally{original.recycle();}
        checkCancel(cancel);
        progress.accept(65);
        OrtEnvironment environment=OrtEnvironment.getEnvironment();
        try(OrtSession.SessionOptions opts=new OrtSession.SessionOptions()){
            opts.setIntraOpNumThreads(2);
            try(OrtSession session=environment.createSession(model.getAbsolutePath(),opts);
                OnnxTensor tensor=OnnxTensor.createTensor(environment,
                    FloatBuffer.wrap(input),new long[]{1,3,SIDE,SIDE});
                OrtSession.Result output=session.run(Collections.singletonMap(
                    session.getInputNames().iterator().next(),tensor))){
                checkCancel(cancel);
                float[][] probabilities=fusedOutput(output.get(0).getValue());
                int h=probabilities.length,w=h>0?probabilities[0].length:0;
                if(h<16||w<16)throw new IOException("Invalid mask dimensions");
                float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
                for(float[] line:probabilities)for(float p:line){
                    if(Float.isFinite(p)){min=Math.min(min,p);max=Math.max(max,p);}
                }
                if(!Float.isFinite(min)||max-min<.0001f)
                    throw new IOException("Model found no distinct subject");
                byte[] mask=new byte[MASK_EDGE*MASK_EDGE];
                int count=0;
                for(int y=0;y<MASK_EDGE;y++){
                    if((y&63)==0)checkCancel(cancel);
                    float fy=(y+.5f)*h/MASK_EDGE-.5f;
                    int y0=Math.max(0,Math.min(h-1,(int)Math.floor(fy)));
                    int y1=Math.min(h-1,y0+1);
                    float ty=Math.max(0.f,Math.min(1.f,fy-y0));
                    for(int x=0;x<MASK_EDGE;x++){
                        float fx=(x+.5f)*w/MASK_EDGE-.5f;
                        int x0=Math.max(0,Math.min(w-1,(int)Math.floor(fx)));
                        int x1=Math.min(w-1,x0+1);
                        float tx=Math.max(0.f,Math.min(1.f,fx-x0));
                        float a=probabilities[y0][x0]*(1-tx)+probabilities[y0][x1]*tx;
                        float b=probabilities[y1][x0]*(1-tx)+probabilities[y1][x1]*tx;
                        float normalized=((a*(1-ty)+b*ty)-min)/(max-min);
                        if(normalized>=.5f){mask[y*MASK_EDGE+x]=(byte)255;count++;}
                    }
                }
                if(count<MASK_EDGE*MASK_EDGE/500 ||
                   count>MASK_EDGE*MASK_EDGE*95/100)
                    throw new IOException("No reliable foreground subject detected");
                checkCancel(cancel);
                progress.accept(100);
                return mask;
            }
        }
    }
}
