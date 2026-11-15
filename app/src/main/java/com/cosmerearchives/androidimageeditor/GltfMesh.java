package com.cosmerearchives.androidimageeditor;

import android.content.Context;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Strict, offline glTF 2.0 proof-of-pipeline loader.
 * Supports embedded base64 buffer, float32 POSITION, unsigned-short indices,
 * baseColorFactor, TEXCOORD_0 and an embedded PNG texture on TRIANGLES.
 * Invalid assets never crash VR startup. Full scenes, glb, PBR, and animation are next.
 */
public final class GltfMesh {
    private GltfMesh(){}

    private static JSONObject readMuseum(Context context) throws Exception {
        byte[] json;
        try(InputStream stream=context.getAssets().open("museum_pedestal.gltf")){
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
            byte[] chunk=new byte[8192];int n;
            while((n=stream.read(chunk))!=-1)out.write(chunk,0,n);
            json=out.toByteArray();
        }
        JSONObject doc=new JSONObject(new String(json,java.nio.charset.StandardCharsets.UTF_8));
        if(!"2.0".equals(doc.getJSONObject("asset").getString("version")))
            throw new IllegalArgumentException("Not glTF 2.0");
        return doc;
    }
    public static int[] loadMuseumTexture(Context context) throws Exception{
        JSONObject doc=readMuseum(context);
        JSONArray images=doc.optJSONArray("images");
        if(images==null||images.length()==0)return null;
        String uri=images.getJSONObject(0).getString("uri");
        String prefix="data:image/png;base64,";
        if(!uri.startsWith(prefix))
            throw new IllegalArgumentException("Only embedded PNG images supported");
        byte[] bytes=Base64.decode(uri.substring(prefix.length()),Base64.DEFAULT);
        if(bytes.length>1_000_000)throw new IllegalArgumentException("Texture too large");
        android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(
            bytes,0,bytes.length);
        if(bitmap==null)throw new IllegalArgumentException("Invalid PNG");
        try{
            int width=bitmap.getWidth(),height=bitmap.getHeight();
            if(width<1||height<1||width>512||height>512||
                (long)width*height>512*512)
                throw new IllegalArgumentException("Texture exceeds Quest budget");
            int[] pixels=new int[width*height+2];
            pixels[0]=width;pixels[1]=height;
            bitmap.getPixels(pixels,2,width,0,0,width,height);
            return pixels;
        }finally{bitmap.recycle();}
    }
    public static float[] loadMuseum(Context context) throws Exception {
        JSONObject doc=readMuseum(context);
        JSONArray buffers=doc.getJSONArray("buffers");
        if(buffers.length()!=1)throw new IllegalArgumentException("Only one embedded buffer");
        String uri=buffers.getJSONObject(0).getString("uri");
        String prefix="data:application/octet-stream;base64,";
        if(!uri.startsWith(prefix))throw new IllegalArgumentException("External glTF buffers unsupported");
        byte[] bytes=Base64.decode(uri.substring(prefix.length()),Base64.DEFAULT);
        if(bytes.length>2_000_000)throw new IllegalArgumentException("Model too large");
        ByteBuffer binary=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        JSONArray meshes=doc.getJSONArray("meshes");
        JSONArray accessors=doc.getJSONArray("accessors");
        JSONArray views=doc.getJSONArray("bufferViews");
        JSONArray materials=doc.optJSONArray("materials");
        java.util.ArrayList<Float> output=new java.util.ArrayList<>();
        for(int meshId=0;meshId<meshes.length();meshId++){
            JSONArray primitives=meshes.getJSONObject(meshId).getJSONArray("primitives");
            for(int k=0;k<primitives.length();k++){
                JSONObject primitive=primitives.getJSONObject(k);
                if(primitive.optInt("mode",4)!=4)throw new IllegalArgumentException("Triangles only");
                int positionId=primitive.getJSONObject("attributes").getInt("POSITION");
                JSONObject position=accessors.getJSONObject(positionId);
                if(position.getInt("componentType")!=5126||
                   !"VEC3".equals(position.getString("type")))
                    throw new IllegalArgumentException("Float32 positions required");
                int vertexCount=position.getInt("count");
                if(vertexCount<3||vertexCount>50000)throw new IllegalArgumentException("Invalid vertex count");
                JSONObject positionView=views.getJSONObject(position.getInt("bufferView"));
                int offset=positionView.optInt("byteOffset",0)+position.optInt("byteOffset",0);
                int stride=positionView.optInt("byteStride",12);
                if(stride<12||stride>256||offset<0||
                   (long)offset+(long)(vertexCount-1)*stride+12>bytes.length)
                    throw new IllegalArgumentException("Position accessor out of bounds");
                float[] positions=new float[vertexCount*3];
                for(int i=0;i<vertexCount;i++)
                    for(int axis=0;axis<3;axis++)
                        positions[i*3+axis]=binary.getFloat(offset+i*stride+axis*4);
                float[] uv=new float[vertexCount*2];
                JSONObject attrs=primitive.getJSONObject("attributes");
                if(attrs.has("TEXCOORD_0")){
                    JSONObject tex=accessors.getJSONObject(attrs.getInt("TEXCOORD_0"));
                    if(tex.getInt("componentType")!=5126||
                       !"VEC2".equals(tex.getString("type"))||
                       tex.getInt("count")!=vertexCount)
                        throw new IllegalArgumentException("Unsupported UV accessor");
                    JSONObject texView=views.getJSONObject(tex.getInt("bufferView"));
                    int uvOffset=texView.optInt("byteOffset",0)+tex.optInt("byteOffset",0);
                    int uvStride=texView.optInt("byteStride",8);
                    if(uvStride<8||uvStride>256||uvOffset<0||
                       (long)uvOffset+(long)(vertexCount-1)*uvStride+8>bytes.length)
                        throw new IllegalArgumentException("UV accessor out of bounds");
                    for(int i=0;i<vertexCount;i++){
                        uv[i*2]=binary.getFloat(uvOffset+i*uvStride);
                        uv[i*2+1]=binary.getFloat(uvOffset+i*uvStride+4);
                    }
                }
                float red=.75f,green=.66f,blue=.48f;
                if(materials!=null&&primitive.has("material")){
                    JSONObject material=materials.getJSONObject(primitive.getInt("material"));
                    JSONObject pbr=material.optJSONObject("pbrMetallicRoughness");
                    if(pbr!=null){
                        JSONArray rgba=pbr.optJSONArray("baseColorFactor");
                        if(rgba!=null){red=(float)rgba.getDouble(0);
                            green=(float)rgba.getDouble(1);blue=(float)rgba.getDouble(2);}
                    }
                }
                if(!primitive.has("indices"))throw new IllegalArgumentException("Indexed geometry required");
                JSONObject indices=accessors.getJSONObject(primitive.getInt("indices"));
                if(indices.getInt("componentType")!=5123||
                   !"SCALAR".equals(indices.getString("type")))
                    throw new IllegalArgumentException("Unsigned short indices required");
                int indexCount=indices.getInt("count");
                if(indexCount<3||indexCount>180000||indexCount%3!=0)
                    throw new IllegalArgumentException("Invalid triangles");
                JSONObject indexView=views.getJSONObject(indices.getInt("bufferView"));
                int base=indexView.optInt("byteOffset",0)+indices.optInt("byteOffset",0);
                if(base<0||(long)base+indexCount*2L>bytes.length)
                    throw new IllegalArgumentException("Indices out of bounds");
                for(int i=0;i<indexCount;i++){
                    int index=binary.getShort(base+i*2)&65535;
                    if(index>=vertexCount)throw new IllegalArgumentException("Index out of bounds");
                    for(int axis=0;axis<3;axis++)output.add(positions[index*3+axis]);
                    output.add(red);output.add(green);output.add(blue);
                    output.add(uv[index*2]);output.add(uv[index*2+1]);
                }
            }
        }
        if(output.isEmpty()||output.size()>1440000)
            throw new IllegalArgumentException("Empty or oversized mesh");
        float[] result=new float[output.size()];
        for(int i=0;i<result.length;i++)result[i]=output.get(i);
        return result;
    }
}
