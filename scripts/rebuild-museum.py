#!/usr/bin/env python3
"""Generate a detailed textured Museum room around the separately imported chair."""
import base64, json, math, pathlib, random, struct, zlib
random.seed(1534)
OUT=pathlib.Path("app/src/main/assets/museum_pedestal.gltf")
def png(kind):
    pix=bytearray()
    for y in range(256):
        for x in range(256):
            slot=x//128+2*(y//128);u=x%128;v=y%128
            n=random.randint(-7,7)
            if slot==0:
                grout=u%42<2 or v%42<2
                vein=abs(math.sin(u*.13+v*.048+math.sin(v*.11)*2))>.94
                c=(55,49,45) if grout else (172,158,140) if vein else (204,193,175)
            elif slot==1:
                g=math.sin(v*.34+math.sin(u*.065)*4)*14+math.sin(v*.10)*11
                c=(82+g,44+g*.6,27+g*.4) if v%40>=2 else (44,25,17)
            elif slot==2:c=(205,187,160)
            else:
                p=math.sin(u*.12)*math.sin(v*.12)+math.sin((u+v)*.065)
                c=(101+p*17,35+p*9,47+p*10)
            if kind==0:rgb=[max(0,min(255,int(a+n))) for a in c]
            elif kind==1:rgb=[128,128,255]
            else:rgb=[255,[205,173,229,233][slot],[5,18,0,0][slot]]
            pix.extend(rgb+[255])
    raw=b"".join(b"\0"+pix[y*1024:(y+1)*1024] for y in range(256))
    def chunk(k,v):return struct.pack(">I",len(v))+k+v+struct.pack(">I",zlib.crc32(k+v)&0xffffffff)
    binary=b"\x89PNG\r\n\x1a\n"+chunk(b"IHDR",struct.pack(">IIBBBBB",256,256,8,6,0,0,0))+chunk(b"IDAT",zlib.compress(raw,9))+chunk(b"IEND",b"")
    return "data:image/png;base64,"+base64.b64encode(binary).decode()
buf=bytearray();views=[];acc=[];meshes=[];nodes=[]
def accessor(values,typ,fmt):
    while len(buf)%4:buf.append(0)
    dat=struct.pack("<"+str(len(values))+fmt,*values);off=len(buf);buf.extend(dat)
    views.append(dict(buffer=0,byteOffset=off,byteLength=len(dat)))
    acc.append(dict(bufferView=len(views)-1,componentType={"f":5126,"H":5123}[fmt],count=len(values)//{"VEC3":3,"VEC2":2,"SCALAR":1}[typ],type=typ))
    return len(acc)-1
def make_mesh(name,mat,kind="box"):
    pos=[];uv=[];ind=[]
    if kind=="box":
        faces=[
            [(-1,-1,1),(1,-1,1),(1,1,1),(-1,1,1)],
            [(1,-1,-1),(-1,-1,-1),(-1,1,-1),(1,1,-1)],
            [(-1,-1,-1),(-1,-1,1),(-1,1,1),(-1,1,-1)],
            [(1,-1,1),(1,-1,-1),(1,1,-1),(1,1,1)],
            [(-1,1,1),(1,1,1),(1,1,-1),(-1,1,-1)],
            [(-1,-1,-1),(1,-1,-1),(1,-1,1),(-1,-1,1)]]
        for face in faces:
            b=len(pos)//3
            for v,t in zip(face,[(0,0),(.999,0),(.999,.999),(0,.999)]):pos+=v;uv+=t
            ind += [b,b+1,b+2,b,b+2,b+3]
    else:
        for j in range(16):
            a=2*math.pi*j/16;b=2*math.pi*(j+1)/16;k=len(pos)//3
            for ang,h,u,v in [(a,-1,0,0),(b,-1,.999,0),(b,1,.999,.999),(a,1,0,.999)]:
                pos += [math.cos(ang),h,math.sin(ang)];uv += [u,v]
            ind += [k,k+1,k+2,k,k+2,k+3]
    pa=accessor(pos,"VEC3","f");ta=accessor(uv,"VEC2","f");ia=accessor(ind,"SCALAR","H")
    meshes.append(dict(name=name,primitives=[dict(attributes=dict(POSITION=pa,TEXCOORD_0=ta),indices=ia,material=mat)]))
    return len(meshes)-1
stone=make_mesh("veined museum stone",0)
wood=make_mesh("aged walnut",1)
ivory=make_mesh("plaster",2)
fabric=make_mesh("burgundy damask",3)
turn=make_mesh("turned wood",1,"round")
col=make_mesh("stone column",2,"round")
def add(name,shape,loc,scale):
    nodes.append(dict(name=name,mesh=shape,translation=list(loc),scale=list(scale)))
add("stone floor",stone,(0,-1.96,0),(4.65,.055,4.65))
add("walnut ceiling",wood,(0,2.65,0),(4.67,.10,4.67))
add("north wall",ivory,(0,.32,-4.62),(4.65,2.32,.075))
add("east wall",ivory,(4.62,.32,0),(.075,2.32,4.65))
add("west wall",ivory,(-4.62,.32,0),(.075,2.32,4.65))
for side in (-1,1):
    add("walnut wainscoting",wood,(side*4.48,-1.25,0),(.10,.59,4.4))
    for z in (-3.,0.,3.):
        add("fabric wall inset",fabric,(side*4.42,.72,z),(.065,.95,.83))
        for d in (-1,1):
            add("horizontal picture frame",stone,(side*4.38,.72+d*.99,z),(.075,.034,.92))
            add("vertical picture frame",stone,(side*4.38,.72,z+d*.89),(.075,1.02,.035))
    for z in (-3.75,3.75):
        x=side*3.99
        add("gallery column",col,(x,.20,z),(.25,1.65,.25))
        add("column base",ivory,(x,-1.58,z),(.42,.15,.42))
        add("column capital",ivory,(x,1.89,z),(.44,.16,.44))
for x in (-2.9,0,2.9):
    add("rear gallery textile",fabric,(x,.63,-4.48),(.86,1.03,.075))
    for d in (-1,1):
        add("rear frame horizontal",stone,(x,.63+d*1.05,-4.40),(.94,.035,.08))
        add("rear frame vertical",stone,(x+d*.9,.63,-4.40),(.035,1.08,.08))
add("central display plinth",ivory,(-1.30,-1.46,-1.7),(.66,.43,.66))
add("plinth trim",wood,(-1.30,-1.01,-1.7),(.72,.055,.72))
add("exhibit base",ivory,(-1.30,-.64,-1.7),(.22,.31,.22))
add("burgundy museum rug",fabric,(.75,-1.89,-1.4),(2.22,.016,1.56))
for d in (-1,1):
    add("rug border",wood,(.75+d*2.15,-1.877,-1.4),(.03,.012,1.58))
    add("console turned leg",turn,(d*2.6,-1.18,-3.73),(.09,.62,.09))
add("console tabletop",wood,(0,-.52,-3.73),(2.85,.10,.40))
add("console apron",wood,(0,-.76,-3.73),(2.66,.15,.34))
for z in (-3.67,-3.19,-2.71,-2.23):add("coffered ceiling beam",wood,(0,2.44,z),(4.40,.14,.08))
materials=[]
for label,metal,rough in [("Polished stone",.09,.76),("Aged walnut",.03,.68),("Warm ivory plaster",0,.92),("Burgundy cloth",0,.96)]:
    materials.append(dict(name=label,pbrMetallicRoughness=dict(baseColorTexture=dict(index=0),metallicRoughnessTexture=dict(index=2),metallicFactor=metal,roughnessFactor=rough,baseColorFactor=[1,1,1,1]),normalTexture=dict(index=1)))
scene=dict(asset=dict(version="2.0",generator="Android Image Editor Museum rebuild"),scene=0,
scenes=[dict(name="Rebuilt Museum Showroom",nodes=list(range(len(nodes))))],nodes=nodes,meshes=meshes,
materials=materials,buffers=[dict(byteLength=len(buf),uri="data:application/octet-stream;base64,"+base64.b64encode(buf).decode())],
bufferViews=views,accessors=acc,images=[dict(uri=png(i)) for i in range(3)],
textures=[dict(source=i) for i in range(3)],
extras=dict(materialAtlas=dict(columns=2,rows=2,materialSlots=[0,1,2,3])))
assert 24<=len(nodes)<=96
OUT.parent.mkdir(parents=True,exist_ok=True)
OUT.write_text(json.dumps(scene,separators=(",",":")),encoding="utf-8")
print("Museum rebuilt:",len(nodes),"instances;",len(buf),"geometry bytes")
