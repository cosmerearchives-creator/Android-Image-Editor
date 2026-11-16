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
 * v0.15.0 uses a bounded 20-float vertex stream with optional detail LOD.
 * Invalid assets fall back without interrupting VR startup.
 */
public final class GltfMesh {
    private GltfMesh(){}

    private static final String[] SCENES = {
        "museum_pedestal.gltf", "club_environment.gltf",
        "tiki_environment.gltf", "fire_environment.gltf",
        "dungeon_environment.gltf", "woodland_environment.gltf"
    };
    private static JSONObject readEnvironment(Context context,int theme) throws Exception {
        if(theme<0||theme>=SCENES.length)
            throw new IllegalArgumentException("Invalid environment theme");

        byte[] json;
        try(InputStream stream=context.getAssets().open(SCENES[theme])){
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
    public static int[] loadEnvironmentTexture(Context context,int theme) throws Exception{
        return loadEnvironmentMaterialMap(context,theme,0);
    }
    /** Optional glTF material images: 0 base color, 1 normal, 2 packed
     * occlusion-roughness-metallic, 3 emissive. Bound to the first material's
     * texture definitions; future builds add full per-primitive textures. */
    public static int[] loadEnvironmentMaterialMap(Context context,int theme,int slot)
            throws Exception{
        if(slot<0||slot>3)throw new IllegalArgumentException("Unknown material slot");
        JSONObject doc=readEnvironment(context,theme);
        JSONArray materials=doc.optJSONArray("materials");
        JSONArray textures=doc.optJSONArray("textures");
        JSONArray images=doc.optJSONArray("images");
        if(images==null||images.length()==0)return null;
        int imageIndex=0;
        if(slot>0){
            if(materials==null||materials.length()==0||textures==null)return null;
            JSONObject mat=materials.getJSONObject(0),spec=null;
            if(slot==1)spec=mat.optJSONObject("normalTexture");
            if(slot==2){
                JSONObject pbr=mat.optJSONObject("pbrMetallicRoughness");
                if(pbr!=null)spec=pbr.optJSONObject("metallicRoughnessTexture");
            }
            if(slot==3)spec=mat.optJSONObject("emissiveTexture");
            if(spec==null)return null;
            int texIndex=spec.getInt("index");
            if(texIndex<0||texIndex>=textures.length())
                throw new IllegalArgumentException("Invalid texture reference");
            imageIndex=textures.getJSONObject(texIndex).getInt("source");
        }
        if(imageIndex<0||imageIndex>=images.length())
            throw new IllegalArgumentException("Invalid image reference");
        String uri=images.getJSONObject(imageIndex).getString("uri");
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
    // glTF column-major scene transforms are baked into the bounded GPU
    // vertex stream. This supports multiple meshes, nested nodes, and reuse.
    private static final class Instance {
        final int mesh;
        final float[] matrix;
        Instance(int mesh,float[] matrix){this.mesh=mesh;this.matrix=matrix;}
    }
    private static float[] identity(){
        return new float[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
    }
    private static float[] multiply(float[] a,float[] b){
        float[] c=new float[16];
        for(int col=0;col<4;col++)
            for(int row=0;row<4;row++)
                for(int k=0;k<4;k++)
                    c[col*4+row]+=a[k*4+row]*b[col*4+k];
        return c;
    }
    private static float[] localTransform(JSONObject node)throws Exception{
        JSONArray matrix=node.optJSONArray("matrix");
        if(matrix!=null){
            if(matrix.length()!=16)throw new IllegalArgumentException("Invalid matrix");
            float[] values=new float[16];
            for(int i=0;i<16;i++)values[i]=(float)matrix.getDouble(i);
            return values;
        }
        JSONArray t=node.optJSONArray("translation"),q=node.optJSONArray("rotation");
        JSONArray scale=node.optJSONArray("scale");
        float tx=t==null?0:(float)t.getDouble(0);
        float ty=t==null?0:(float)t.getDouble(1);
        float tz=t==null?0:(float)t.getDouble(2);
        float x=q==null?0:(float)q.getDouble(0);
        float y=q==null?0:(float)q.getDouble(1);
        float z=q==null?0:(float)q.getDouble(2);
        float w=q==null?1:(float)q.getDouble(3);
        float norm=x*x+y*y+z*z+w*w;
        if(norm<.0001f)throw new IllegalArgumentException("Zero rotation");
        float inv=1f/(float)Math.sqrt(norm);
        x*=inv;y*=inv;z*=inv;w*=inv;
        float sx=scale==null?1:(float)scale.getDouble(0);
        float sy=scale==null?1:(float)scale.getDouble(1);
        float sz=scale==null?1:(float)scale.getDouble(2);
        return new float[]{
            (1-2*(y*y+z*z))*sx,(2*(x*y+z*w))*sx,(2*(x*z-y*w))*sx,0,
            (2*(x*y-z*w))*sy,(1-2*(x*x+z*z))*sy,(2*(y*z+x*w))*sy,0,
            (2*(x*z+y*w))*sz,(2*(y*z-x*w))*sz,(1-2*(x*x+y*y))*sz,0,
            tx,ty,tz,1
        };
    }
    private static void visitNode(JSONArray nodes,int index,float[] parent,
            boolean[] active,java.util.ArrayList<Instance> output,int depth)
            throws Exception{
        if(index<0||index>=nodes.length()||depth>12||active[index])
            throw new IllegalArgumentException("Invalid/cyclic scene graph");
        if(output.size()>96)throw new IllegalArgumentException("Scene instance budget");
        active[index]=true;
        JSONObject node=nodes.getJSONObject(index);
        float[] matrix=multiply(parent,localTransform(node));
        if(node.has("mesh"))output.add(new Instance(node.getInt("mesh"),matrix));
        JSONArray children=node.optJSONArray("children");
        if(children!=null)for(int i=0;i<children.length();i++)
            visitNode(nodes,children.getInt(i),matrix,active,output,depth+1);
        active[index]=false;
    }
    private static java.util.ArrayList<Instance> sceneInstances(JSONObject doc)
            throws Exception{
        java.util.ArrayList<Instance> output=new java.util.ArrayList<>();
        JSONArray nodes=doc.optJSONArray("nodes");
        JSONArray scenes=doc.optJSONArray("scenes");
        if(nodes==null||scenes==null||scenes.length()==0)
            throw new IllegalArgumentException("Missing glTF scene");
        int current=doc.optInt("scene",0);
        JSONArray roots=scenes.getJSONObject(current).getJSONArray("nodes");
        boolean[] active=new boolean[nodes.length()];
        for(int i=0;i<roots.length();i++)
            visitNode(nodes,roots.getInt(i),identity(),active,output,0);
        if(output.isEmpty()||output.size()>96)
            throw new IllegalArgumentException("Invalid scene size");
        return output;
    }
    private static JSONObject importedChair;
    private static JSONObject readImportedChair(Context context) throws Exception {
        if(importedChair==null){
            try(InputStream stream=context.getAssets().open("real_chair_damask.gltf")){
                java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
                byte[] chunk=new byte[8192];int n;
                while((n=stream.read(chunk))!=-1)out.write(chunk,0,n);
                if(out.size()>4_000_000)
                    throw new IllegalArgumentException("Imported asset exceeds budget");
                importedChair=new JSONObject(new String(out.toByteArray(),
                    java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return importedChair;
    }
    /** Imported externally authored chair, not generated primitive geometry. */
    public static float[] loadImportedChairMesh(Context context) throws Exception {
        return buildMesh(readImportedChair(context),true);
    }
    public static int[] loadImportedChairMap(Context context,int slot) throws Exception {
        if(slot<0||slot>3)throw new IllegalArgumentException("Invalid chair map");
        if(slot==3)return null; // Authored chair has no emissive material.
        JSONObject doc=readImportedChair(context);
        final int[][] sourceIds={{2,-1,6,8},{0,-1,5,-1},{3,4,7,-1}};
        final int[] sources=sourceIds[slot];
        JSONArray images=doc.getJSONArray("images");
        android.graphics.Bitmap atlas=android.graphics.Bitmap.createBitmap(
            512,512,android.graphics.Bitmap.Config.ARGB_8888);
        try{
            android.graphics.Canvas canvas=new android.graphics.Canvas(atlas);
            android.graphics.Paint paint=new android.graphics.Paint(
                android.graphics.Paint.FILTER_BITMAP_FLAG);
            int neutral=slot==0?0xffffffff:
                slot==1?0xff8080ff:0xffffb800;
            canvas.drawColor(neutral);
            for(int tile=0;tile<4;tile++){
                if(sources[tile]<0)continue;
                String uri=images.getJSONObject(sources[tile]).getString("uri");
                String prefix="data:image/jpeg;base64,";
                if(!uri.startsWith(prefix))
                    throw new IllegalArgumentException("Chair JPEG not embedded");
                byte[] jpg=Base64.decode(uri.substring(prefix.length()),Base64.DEFAULT);
                if(jpg.length>1_000_000)
                    throw new IllegalArgumentException("Chair map too large");
                android.graphics.BitmapFactory.Options options=
                    new android.graphics.BitmapFactory.Options();
                options.inSampleSize=2;
                android.graphics.Bitmap source=
                    android.graphics.BitmapFactory.decodeByteArray(jpg,0,jpg.length,options);
                if(source==null)throw new IllegalArgumentException("Invalid chair JPG");
                try{
                    int left=(tile%2)*256,top=(tile/2)*256;
                    canvas.drawBitmap(source,null,
                        new android.graphics.Rect(left,top,left+256,top+256),paint);
                }finally{source.recycle();}
            }
            int[] result=new int[512*512+2];
            result[0]=512;result[1]=512;
            atlas.getPixels(result,2,512,0,0,512,512);
            return result;
        }finally{atlas.recycle();}
    }
    public static float[] loadEnvironment(Context context,int theme) throws Exception {
        return buildMesh(readEnvironment(context,theme),false);
    }
    private static float[] buildMesh(JSONObject doc,boolean isImportedChair)
            throws Exception {
        // Reject unsafe/nonfinite transforms before they reach the GPU.
        // A single invalid imported node must not corrupt the VR render pass.
        for(Instance instance:sceneInstances(doc)){
            for(float value:instance.matrix)
                if(!Float.isFinite(value)||Math.abs(value)>100000f)
                    throw new IllegalArgumentException("Unsafe scene transform");
        }
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
        for(Instance instance:sceneInstances(doc)){
            int meshId=instance.mesh;
            if(meshId<0||meshId>=meshes.length())throw new IllegalArgumentException("Invalid mesh ref");
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
                // glTF accessors must stay within their declared bufferView,
                // not merely within the overall binary buffer.
                int viewStart=positionView.optInt("byteOffset",0);
                int viewLength=positionView.getInt("byteLength");
                if(offset<viewStart||
                   (long)offset+(long)(vertexCount-1)*stride+12>
                       (long)viewStart+viewLength)
                    throw new IllegalArgumentException("Position exceeds bufferView");
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
                    int uvViewStart=texView.optInt("byteOffset",0);
                    if(uvOffset<uvViewStart||
                       (long)uvOffset+(long)(vertexCount-1)*uvStride+8>
                           (long)uvViewStart+texView.getInt("byteLength"))
                        throw new IllegalArgumentException("UV exceeds bufferView");
                    for(int i=0;i<vertexCount;i++){
                        uv[i*2]=binary.getFloat(uvOffset+i*uvStride);
                        uv[i*2+1]=binary.getFloat(uvOffset+i*uvStride+4);
                    }
                }
                // Optional per-material texture-atlas slots share three
                // bounded GL textures rather than one draw/texture per object.
                int atlasSlot=-1;
                JSONObject atlas=doc.optJSONObject("extras");
                if(atlas!=null)atlas=atlas.optJSONObject("materialAtlas");
                if(atlas!=null&&primitive.has("material")){
                    JSONArray slots=atlas.getJSONArray("materialSlots");
                    int materialId=primitive.getInt("material");
                    if(atlas.getInt("columns")!=2||atlas.getInt("rows")!=2||
                       materialId<0||materialId>=slots.length())
                        throw new IllegalArgumentException("Invalid material atlas");
                    atlasSlot=slots.getInt(materialId);
                    if(atlasSlot<0||atlasSlot>3)
                        throw new IllegalArgumentException("Invalid atlas tile");
                }
                float red=.75f,green=.66f,blue=.48f;
                float metallic=0f,roughness=.8f,emR=0f,emG=0f,emB=0f;
                if(materials!=null&&primitive.has("material")){
                    JSONObject material=materials.getJSONObject(primitive.getInt("material"));
                    JSONObject pbr=material.optJSONObject("pbrMetallicRoughness");
                    if(pbr!=null){
                        JSONArray rgba=pbr.optJSONArray("baseColorFactor");
                        if(rgba!=null){red=(float)rgba.getDouble(0);
                            green=(float)rgba.getDouble(1);blue=(float)rgba.getDouble(2);}
                        metallic=(float)pbr.optDouble("metallicFactor",1.0);
                        roughness=(float)pbr.optDouble("roughnessFactor",1.0);
                    }
                    JSONArray emissive=material.optJSONArray("emissiveFactor");
                    if(emissive!=null&&emissive.length()==3){
                        emR=(float)emissive.getDouble(0);
                        emG=(float)emissive.getDouble(1);
                        emB=(float)emissive.getDouble(2);
                    }
                }
                if(!Float.isFinite(metallic)||!Float.isFinite(roughness)||
                   !Float.isFinite(emR)||!Float.isFinite(emG)||!Float.isFinite(emB))
                    throw new IllegalArgumentException("Invalid material factors");
                metallic=Math.max(0,Math.min(1,metallic));
                roughness=Math.max(.08f,Math.min(1,roughness));
                emR=Math.max(0,Math.min(4,emR));
                emG=Math.max(0,Math.min(4,emG));
                emB=Math.max(0,Math.min(4,emB));
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
                // Optional model-level distance LOD: retain structural meshes
                // while culling decorative detail only when viewed remotely.
                // glTF geometry is already transformed into room space.
                final boolean optionalDetail=meshId==1;
                float centerX=0,centerY=0,centerZ=0;
                for(int v=0;v<vertexCount;v++){
                    float vx=positions[v*3],vy=positions[v*3+1],vz=positions[v*3+2];
                    centerX+=instance.matrix[0]*vx+instance.matrix[4]*vy+
                        instance.matrix[8]*vz+instance.matrix[12];
                    centerY+=instance.matrix[1]*vx+instance.matrix[5]*vy+
                        instance.matrix[9]*vz+instance.matrix[13];
                    centerZ+=instance.matrix[2]*vx+instance.matrix[6]*vy+
                        instance.matrix[10]*vz+instance.matrix[14];
                }
                centerX/=vertexCount;centerY/=vertexCount;centerZ/=vertexCount;
                final float detailRange=optionalDetail?3.35f:0f;
                // Transform each indexed triangle once and compute a
                // world-space flat normal for reliable low-cost museum light.
                float[] m=instance.matrix;
                for(int i=0;i<indexCount;i+=3){
                    float[] tri=new float[9];
                    int[] refs=new int[3];
                    for(int corner=0;corner<3;corner++){
                        int index=binary.getShort(base+(i+corner)*2)&65535;
                        if(index>=vertexCount)
                            throw new IllegalArgumentException("Index out of bounds");
                        refs[corner]=index;
                        float px=positions[index*3],py=positions[index*3+1],
                            pz=positions[index*3+2];
                        float wx=m[0]*px+m[4]*py+m[8]*pz+m[12];
                        float wy=m[1]*px+m[5]*py+m[9]*pz+m[13];
                        float wz=m[2]*px+m[6]*py+m[10]*pz+m[14];
                        if(isImportedChair){
                            wx=wx*1.45f+1.30f;
                            wy=wy*1.45f-1.90f;
                            wz=wz*1.45f-1.35f;
                        }
                        if(!Float.isFinite(wx)||!Float.isFinite(wy)||!Float.isFinite(wz))
                            throw new IllegalArgumentException("Nonfinite model geometry");
                        tri[corner*3]=wx;tri[corner*3+1]=wy;tri[corner*3+2]=wz;
                    }
                    float ax=tri[3]-tri[0],ay=tri[4]-tri[1],az=tri[5]-tri[2];
                    float bx=tri[6]-tri[0],by=tri[7]-tri[1],bz=tri[8]-tri[2];
                    float nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
                    float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
                    if(length>.00001f){nx/=length;ny/=length;nz/=length;}
                    else{nx=0;ny=1;nz=0;}
                    for(int corner=0;corner<3;corner++){
                        int index=refs[corner];
                        output.add(tri[corner*3]);output.add(tri[corner*3+1]);
                        output.add(tri[corner*3+2]);
                        output.add(red);output.add(green);output.add(blue);
                        if(atlasSlot>=0){
                            // Two-pixel tile gutters prevent bilinear color
                            // bleed; repeat source UV within each material.
                            float u=uv[index*2],v=uv[index*2+1];
                            if(isImportedChair&&materials!=null&&
                               primitive.has("material")){
                                JSONObject mat=materials.getJSONObject(
                                    primitive.getInt("material"));
                                JSONObject pbr=mat.optJSONObject("pbrMetallicRoughness");
                                JSONObject baseColorTexture=pbr==null?null:
                                     pbr.optJSONObject("baseColorTexture");
                                JSONObject ext=baseColorTexture==null?null:
                                     baseColorTexture.optJSONObject("extensions");
                                JSONObject transform=ext==null?null:
                                    ext.optJSONObject("KHR_texture_transform");
                                if(transform!=null){
                                    JSONArray scale=transform.optJSONArray("scale");
                                    JSONArray offsetUV=transform.optJSONArray("offset");
                                    float su=scale==null?1:(float)scale.getDouble(0);
                                    float sv=scale==null?1:(float)scale.getDouble(1);
                                    float angle=(float)transform.optDouble("rotation",0);
                                    float cu=(float)Math.cos(angle),si=(float)Math.sin(angle);
                                    float uu=u*su,vv=v*sv;
                                    u=cu*uu-si*vv+(offsetUV==null?0:
                                        (float)offsetUV.getDouble(0));
                                    v=si*uu+cu*vv+(offsetUV==null?0:
                                        (float)offsetUV.getDouble(1));
                                }
                            }
                            u=u-(float)Math.floor(u);
                            v=v-(float)Math.floor(v);
                            float tileU=(u*.96875f+.015625f)*.5f+
                                (atlasSlot%2)*.5f;
                            float tileV=(v*.96875f+.015625f)*.5f+
                                (atlasSlot/2)*.5f;
                            output.add(tileU);output.add(tileV);
                        }else{
                            output.add(uv[index*2]);output.add(uv[index*2+1]);
                        }
                        output.add(nx);output.add(ny);output.add(nz);
                        output.add(centerX);output.add(centerY);
                        output.add(centerZ);output.add(detailRange);
                        output.add(metallic);output.add(roughness);
                        output.add(emR);output.add(emG);output.add(emB);
                    }
                    if(output.size()>3600000)
                        throw new IllegalArgumentException("Scene triangle budget");
                }
            }
        }
        if(output.isEmpty()||output.size()>3600000)
            throw new IllegalArgumentException("Empty or oversized mesh");
        float[] result=new float[output.size()];
        for(int i=0;i<result.length;i++)result[i]=output.get(i);
        return result;
    }
}
