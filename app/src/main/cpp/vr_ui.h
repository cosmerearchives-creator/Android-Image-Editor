#pragma once
#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <vector>
#include <GLES3/gl3.h>

// Small self-contained OpenGL ES menu renderer, no web/assets/font dependencies.
// Vertices are batched rather than issuing a draw call for each glyph.
namespace vrui {
constexpr int W=1024, H=768;
struct Color {float r,g,b;};
constexpr Color white{0.96f,0.95f,1.0f}, muted{0.67f,0.63f,0.76f},
    purple{0.34f,0.22f,0.52f}, purpleHover{0.52f,0.36f,0.76f},
    panel{0.095f,0.075f,0.14f}, button{0.17f,0.14f,0.25f};
enum class Page {Home, Open, Projects, Gallery, Immersive};
inline void box(std::vector<float>& vertices,float x,float y,float w,float h,Color c) {
    float a[6][2]={{x,y},{x+w,y},{x,y+h},{x+w,y},{x+w,y+h},{x,y+h}};
    for(auto& p:a) {vertices.push_back(p[0]);vertices.push_back(p[1]);
        vertices.push_back(c.r);vertices.push_back(c.g);vertices.push_back(c.b);}
}
inline std::array<uint8_t,7> glyph(char ch) {
    // 5x7 uppercase bitmap. Non-essential punctuation falls back to a space.
    switch(ch) {
    case 'A':return {14,17,17,31,17,17,17};
    case 'B':return {30,17,17,30,17,17,30};
    case 'C':return {14,17,16,16,16,17,14};
    case 'D':return {30,17,17,17,17,17,30};
    case 'E':return {31,16,16,30,16,16,31};
    case 'F':return {31,16,16,30,16,16,16};
    case 'G':return {14,17,16,23,17,17,14};
    case 'H':return {17,17,17,31,17,17,17};
    case 'I':return {31,4,4,4,4,4,31};
    case 'J':return {7,2,2,2,18,18,12};
    case 'K':return {17,18,20,24,20,18,17};
    case 'L':return {16,16,16,16,16,16,31};
    case 'M':return {17,27,21,21,17,17,17};
    case 'N':return {17,25,21,19,17,17,17};
    case 'O':return {14,17,17,17,17,17,14};
    case 'P':return {30,17,17,30,16,16,16};
    case 'Q':return {14,17,17,17,21,18,13};
    case 'R':return {30,17,17,30,20,18,17};
    case 'S':return {15,16,16,14,1,1,30};
    case 'T':return {31,4,4,4,4,4,4};
    case 'U':return {17,17,17,17,17,17,14};
    case 'V':return {17,17,17,17,17,10,4};
    case 'W':return {17,17,17,21,21,21,10};
    case 'X':return {17,17,10,4,10,17,17};
    case 'Y':return {17,17,10,4,4,4,4};
    case 'Z':return {31,1,2,4,8,16,31};
    case '0':return {14,17,19,21,25,17,14};
    case '1':return {4,12,4,4,4,4,14};
    case '2':return {14,17,1,2,4,8,31};
    case '3':return {30,1,1,14,1,1,30};
    case '4':return {2,6,10,18,31,2,2};
    case '5':return {31,16,30,1,1,17,14};
    case '6':return {6,8,16,30,17,17,14};
    case '7':return {31,1,2,4,8,8,8};
    case '8':return {14,17,17,14,17,17,14};
    case '9':return {14,17,17,15,1,2,12};
    case ':':return {0,4,4,0,4,4,0};
    case '.':return {0,0,0,0,0,12,12};
    case '-':return {0,0,0,31,0,0,0};
    default:return {0,0,0,0,0,0,0};
    }
}
inline void label(std::vector<float>& v,const char* s,int x,int y,int scale,Color color) {
    for (int k=0;s[k];++k) {
        unsigned char ch=static_cast<unsigned char>(s[k]);
        if(ch>='a'&&ch<='z') ch-=32;
        const auto rows=glyph(static_cast<char>(ch));
        for(int row=0;row<7;++row)
            for(int col=0;col<5;++col)
                if(rows[row]&(1<<(4-col)))
                    box(v,float(x+k*6*scale+col*scale),float(y+(6-row)*scale),
                        float(scale),float(scale),color);
    }
}
inline int buttonAt(Page page,float x,float y) {
    if(x<170||x>854) return -1;
    if(page==Page::Home) {
        if(y>=444&&y<=540)return 0;
        if(y>=315&&y<=411)return 1;
        if(y>=186&&y<=282)return 2;
    } else if (page==Page::Open) {
        if(y>=92&&y<=190) return 3;
        if(y>=205&&y<=291) {
            if(x<=333)return 4;
            if(x<=507)return 8;
            if(x<=677)return 9;
            return 10;
        }
        if(y>=36&&y<=88) {
            if(x>=645)return 5;
            if(x<=409)return 6;
            if(x>=420&&x<=639)return 7;
        }
    } else if(y>=92&&y<=190) return 3;
    return -1;
}
struct Renderer {
    GLuint program=0,vao=0,vbo=0,photoProgram=0,photoTexture=0,
           photoVao=0,photoVbo=0,depthTexture=0;
    bool hasQuickDepth() const {return depthTexture!=0;}
    bool quickConverting=false;
    bool brushMode=false;
    bool painting=false;
    bool paintingLeft=false;
    float brushRadius=13.f; // pixels of 256x256 depth image
    std::vector<uint8_t> depthCpu;
    bool hasDepthPixels() const {return depthCpu.size()==256u*256u;}
    bool hitPhoto(float sx,float sy,bool immersive,float& u,float& v) const {
        if(!hasPhoto())return false;
        float w=immersive?1024.f:704.f,h=immersive?768.f:300.f;
        float aspect=float(photoWidth)/float(photoHeight);
        if(aspect>w/h)h=w/aspect;else w=h*aspect;
        float x=512.f-w*.5f,y=(immersive?384.f:450.f)-h*.5f;
        if(sx<x||sx>x+w||sy<y||sy>y+h)return false;
        u=centerU+((sx-x)/w-.5f)/zoom;
        v=centerV+(.5f-(sy-y)/h)/zoom;
        return u>=0.f&&u<=1.f&&v>=0.f&&v<=1.f;
    }
    bool brushAt(float sx,float sy,bool pullTowardViewer) {
        if(!brushMode||!hasDepthPixels())return false;
        float u=0.f,v=0.f;
        if(!hitPhoto(sx,sy,false,u,v))return false;
        const float cx=u*255.f,cy=(1.f-v)*255.f;
        const int radius=static_cast<int>(std::ceil(brushRadius));
        const int xa=std::max(0,static_cast<int>(cx)-radius),
                  xb=std::min(255,static_cast<int>(cx)+radius),
                  ya=std::max(0,static_cast<int>(cy)-radius),
                  yb=std::min(255,static_cast<int>(cy)+radius);
        bool edited=false;
        for(int y=ya;y<=yb;++y)for(int x=xa;x<=xb;++x){
            const float dist=std::hypot(float(x)-cx,float(y)-cy);
            if(dist>brushRadius)continue;
            const float falloff=std::pow(std::max(0.f,1.f-dist/brushRadius),1.2f);
            const size_t i=size_t(y)*256u+size_t(x);
            const int delta=std::max(1,static_cast<int>(std::lround(8.f*falloff)));
            const int value=std::clamp(int(depthCpu[i])+(pullTowardViewer?delta:-delta),0,255);
            if(value!=int(depthCpu[i])){depthCpu[i]=static_cast<uint8_t>(value);edited=true;}
        }
        if(edited){
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D,depthTexture);
            glPixelStorei(GL_UNPACK_ALIGNMENT,1);
            glTexSubImage2D(GL_TEXTURE_2D,0,0,0,256,256,GL_RED,
                            GL_UNSIGNED_BYTE,depthCpu.data());
            glBindTexture(GL_TEXTURE_2D,0);
            glActiveTexture(GL_TEXTURE0);
        }
        return edited;
    }
    void clearQuickDepth() {
        if(depthTexture)glDeleteTextures(1,&depthTexture);
        depthTexture=0;
        depthCpu.clear();
    }
    void uploadQuickDepth(const std::vector<uint8_t>& gray,int width,int height){
        clearQuickDepth();
        if(width!=256||height!=256||gray.size()!=size_t(width)*size_t(height))return;
        depthCpu=gray;
        glGenTextures(1,&depthTexture);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D,depthTexture);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        glPixelStorei(GL_UNPACK_ALIGNMENT,1);
        glTexImage2D(GL_TEXTURE_2D,0,GL_R8,width,height,0,GL_RED,GL_UNSIGNED_BYTE,gray.data());
        glBindTexture(GL_TEXTURE_2D,0);
        glActiveTexture(GL_TEXTURE0);
    }
    int photoWidth=0,photoHeight=0;
    bool stereoEnabled=false;
    int depthLevel=1;
    float depthStrength() const {
        return stereoEnabled ? (depthLevel==0?0.45f:(depthLevel==1?0.8f:1.2f)) : 0.f;
    }
    float zoom=1.f, centerU=0.5f, centerV=0.5f;
    void resetPhotoView(){zoom=1.f;centerU=0.5f;centerV=0.5f;}
    void changeZoom(float delta,float cursorX,float cursorY,bool pointer,bool immersive=false){
        if(!hasPhoto() || std::abs(delta)<0.001f)return;
        const float next=std::clamp(zoom*std::exp(delta),1.f,12.f);
        if(std::abs(next-zoom)<0.00001f)return;
        float w=immersive?1024.f:704.f,h=immersive?768.f:300.f;
        const float ratio=float(photoWidth)/float(photoHeight);
        if(ratio>w/h)h=w/ratio;else w=h*ratio;
        const float x=512.f-w/2.f,y=(immersive?384.f:450.f)-h/2.f;
        const float px=pointer?std::clamp((cursorX-x)/w,0.f,1.f):0.5f;
        const float py=pointer?std::clamp((cursorY-y)/h,0.f,1.f):0.5f;
        // Hold the content under the aim cursor fixed while zooming.
        centerU+= (px-0.5f)*(1.f/zoom-1.f/next);
        centerV+= (py-0.5f)*(1.f/zoom-1.f/next);
        zoom=next;
        const float edge=0.5f/zoom;
        centerU=std::clamp(centerU,edge,1.f-edge);
        centerV=std::clamp(centerV,edge,1.f-edge);
    }
    bool hasPhoto() const {return photoTexture!=0;}
    static GLuint shader(GLenum type,const char* source) {
        GLuint s=glCreateShader(type);glShaderSource(s,1,&source,nullptr);
        glCompileShader(s);GLint good=0;glGetShaderiv(s,GL_COMPILE_STATUS,&good);
        if(!good){glDeleteShader(s);return 0;}return s;
    }
    bool initialize(){
        const char* vs=R"(#version 300 es
            layout(location=0) in vec2 pos;
            layout(location=1) in vec3 colorIn;
            out vec3 colorOut;
            void main(){gl_Position=vec4(pos.x/512.0-1.0,pos.y/384.0-1.0,0.0,1.0);colorOut=colorIn;}
        )";
        const char* fs=R"(#version 300 es
            precision mediump float;
            in vec3 colorOut;
            out vec4 frag;
            void main(){frag=vec4(colorOut,1.0);}
        )";
        GLuint a=shader(GL_VERTEX_SHADER,vs),b=shader(GL_FRAGMENT_SHADER,fs);
        if(!a||!b)return false;
        program=glCreateProgram();glAttachShader(program,a);glAttachShader(program,b);
        glLinkProgram(program);glDeleteShader(a);glDeleteShader(b);
        GLint linked=0;glGetProgramiv(program,GL_LINK_STATUS,&linked);
        if(!linked)return false;
        glGenVertexArrays(1,&vao);glGenBuffers(1,&vbo);
        glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);
        glEnableVertexAttribArray(0);glVertexAttribPointer(0,2,GL_FLOAT,GL_FALSE,5*sizeof(float),nullptr);
        glEnableVertexAttribArray(1);glVertexAttribPointer(1,3,GL_FLOAT,GL_FALSE,5*sizeof(float),
            reinterpret_cast<const void*>(2*sizeof(float)));
        glBindVertexArray(0);
        const char* photoVs=R"(#version 300 es
            layout(location=0) in vec2 pos;
            layout(location=2) in vec2 texCoord;
            out vec2 uv;
            void main(){
                gl_Position=vec4(pos.x/512.0-1.0,pos.y/384.0-1.0,0.0,1.0);
                uv=texCoord;
            }
        )";
        const char* photoFs=R"(#version 300 es
            precision mediump float;
            in vec2 uv;
            uniform sampler2D photograph;
            uniform sampler2D quickDepth;
            uniform int useQuickDepth;
            uniform float eyeSign;
            uniform float depthStrength;
            out vec4 frag;
            // Experimental depth cue based on image brightness and center weighting.
            float guessedDepth(vec2 p) {
                vec3 col=texture(photograph,clamp(p,vec2(0.001),vec2(0.999))).rgb;
                float luminance=dot(col,vec3(0.2126,0.7152,0.0722));
                float center=1.0-smoothstep(0.0,0.72,length(p-vec2(0.5)));
                return clamp(0.25+0.4*center+0.35*luminance,0.0,1.0);
            }
            void main() {
                vec2 sampleUv=uv;
                for(int i=0;i<2;i++){
                    float depth=useQuickDepth==1
                        ?texture(quickDepth,clamp(sampleUv,vec2(0.001),vec2(0.999))).r
                        :guessedDepth(sampleUv);
                    sampleUv.x=uv.x + eyeSign*depthStrength*(depth-0.5)*0.035;
                    sampleUv=clamp(sampleUv,vec2(0.001),vec2(0.999));
                }
                frag=texture(photograph,sampleUv);
            }
        )";
        GLuint pv=shader(GL_VERTEX_SHADER,photoVs),pf=shader(GL_FRAGMENT_SHADER,photoFs);
        if(!pv||!pf)return false;
        photoProgram=glCreateProgram();glAttachShader(photoProgram,pv);glAttachShader(photoProgram,pf);
        glLinkProgram(photoProgram);glDeleteShader(pv);glDeleteShader(pf);
        GLint ok=0;glGetProgramiv(photoProgram,GL_LINK_STATUS,&ok);
        if(!ok)return false;
        glGenVertexArrays(1,&photoVao);
        glGenBuffers(1,&photoVbo);
        glBindVertexArray(photoVao);
        glBindBuffer(GL_ARRAY_BUFFER,photoVbo);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0,2,GL_FLOAT,GL_FALSE,4*sizeof(float),nullptr);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2,2,GL_FLOAT,GL_FALSE,4*sizeof(float),
            reinterpret_cast<const void*>(2*sizeof(float)));
        glBindVertexArray(0);
        return true;
    }
    void uploadPhoto(const std::vector<uint8_t>& rgba,int width,int height){
        if(photoTexture){glDeleteTextures(1,&photoTexture);photoTexture=0;}
        photoWidth=0;photoHeight=0;
        if(width<=0||height<=0||rgba.size()!=size_t(width)*size_t(height)*4)return;
        glGenTextures(1,&photoTexture);
        glBindTexture(GL_TEXTURE_2D,photoTexture);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        glPixelStorei(GL_UNPACK_ALIGNMENT,1);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,width,height,0,GL_RGBA,GL_UNSIGNED_BYTE,rgba.data());
        glBindTexture(GL_TEXTURE_2D,0);
        photoWidth=width;photoHeight=height;
        resetPhotoView();
    }
    void drawPhoto(float eyeSign,bool immersive=false){
        if(!photoTexture || !photoProgram)return;
        // Fit without cropping or stretching, preserving photo aspect ratio.
        float w=immersive?1024.f:704.f,h=immersive?768.f:300.f;
        const float ratio=float(photoWidth)/float(photoHeight);
        if(ratio > w/h) h=w/ratio; else w=h*ratio;
        const float x=512.f-w/2.f,y=(immersive?384.f:450.f)-h/2.f;
        // Explicit normalized texture coordinates preserve source orientation.
        const float half=0.5f/zoom;
        const float u0=centerU-half,u1=centerU+half;
        const float v0=centerV-half,v1=centerV+half;
        const float vertices[]={
            x,y,u0,v1, x+w,y,u1,v1, x,y+h,u0,v0,
            x+w,y,u1,v1, x+w,y+h,u1,v0, x,y+h,u0,v0
        };
        glUseProgram(photoProgram);
        glUniform1i(glGetUniformLocation(photoProgram,"photograph"),0);
        glUniform1i(glGetUniformLocation(photoProgram,"quickDepth"),1);
        glUniform1i(glGetUniformLocation(photoProgram,"useQuickDepth"),hasQuickDepth()?1:0);
        glUniform1f(glGetUniformLocation(photoProgram,"eyeSign"),eyeSign);
        glUniform1f(glGetUniformLocation(photoProgram,"depthStrength"),depthStrength());
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D,photoTexture);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D,depthTexture);
        glActiveTexture(GL_TEXTURE0);
        glBindVertexArray(photoVao);
        glBindBuffer(GL_ARRAY_BUFFER,photoVbo);
        glBufferData(GL_ARRAY_BUFFER,sizeof(vertices),vertices,GL_DYNAMIC_DRAW);
        glDrawArrays(GL_TRIANGLES,0,6);
        glBindVertexArray(0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D,0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D,0);
    }
    void draw(Page page,int hovered,float cursorX,float cursorY,bool pointer,float eyeSign=0.f){
        if(page==Page::Immersive) {
            // Full photo only, letterboxed according to aspect ratio, no editor UI.
            if(hasPhoto()) drawPhoto(eyeSign,true);
            return;
        }
        std::vector<float> v;v.reserve(42000);
        box(v,42,48,940,674,panel);
        label(v,"ANDROID IMAGE EDITOR",172,645,5,white);
        if(page==Page::Home)label(v,"QUEST 2 VR STUDIO",312,594,3,muted);
        if(page==Page::Home){
            const char* names[]={"OPEN IMAGE","MY PROJECTS","VIEW GALLERY"};
            const int ys[]={444,315,186};
            for(int i=0;i<3;++i){
                box(v,170,float(ys[i]),684,96,hovered==i?purpleHover:(i==0?purple:button));
                int length=static_cast<int>(std::strlen(names[i]));
                label(v,names[i],512-length*9,ys[i]+30,3,white);
            }
            label(v,"SELECT WITH RIGHT TRIGGER",230,114,3,muted);
        } else {
            const char* title=page==Page::Open?"OPEN IMAGE":
                page==Page::Projects?"MY PROJECTS":"VIEW GALLERY";
            int n=static_cast<int>(std::strlen(title));
            label(v,title,512-n*15,page==Page::Open?615:475,5,white);
            if(page==Page::Open) {
                if(!hasPhoto()) label(v,"NO IMAGE IMPORTED",235,411,4,muted);
                box(v,170,205,163,86,hovered==4?purpleHover:button);
                label(v,"IMPORT",179,235,2,white);
                box(v,340,205,167,86,hovered==8?purpleHover:purple);
                label(v,"IMMERSIVE",356,235,2,white);
                box(v,514,205,163,86,hovered==9?purpleHover:button);
                label(v,"QUICK",559,235,2,white);
                box(v,684,205,170,86,hovered==10?purpleHover:(brushMode?purple:button));
                label(v,brushMode?"BRUSH ON":"BRUSH OFF",700,235,2,white);
                box(v,170,36,239,52,hovered==6?purpleHover:(stereoEnabled?purple:button));
                label(v,stereoEnabled?"3D ON":"3D OFF",218,51,2,white);
                box(v,420,36,219,52,hovered==7?purpleHover:button);
                label(v,depthLevel==0?"DEPTH LOW":depthLevel==1?"DEPTH MID":"DEPTH HIGH",
                      438,51,2,white);
                box(v,645,36,209,52,hovered==5?purpleHover:button);
                label(v,"RESET VIEW",665,51,2,white);
                if(hasPhoto()) {
                    label(v,hasQuickDepth()?"QUICK DEPTH MAP":quickConverting?"CONVERTING DEPTH":"HEURISTIC DEPTH",180,310,2,muted);
                    label(v,"BOTH GRIPS RESIZE ROTATE",204,304,2,muted);
                }
            } else {
                label(v,"COMING SOON",320,385,4,muted);
                label(v,"VR NAVIGATION TEST",272,318,3,muted);
            }
            box(v,170,92,684,98,hovered==3?purpleHover:purple);
            label(v,"BACK",440,121,4,white);
        }
        if(pointer){
            box(v,cursorX-13,cursorY-2,26,4,white);
            box(v,cursorX-2,cursorY-13,4,26,white);
        }
        glUseProgram(program);glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);
        glBufferData(GL_ARRAY_BUFFER,v.size()*sizeof(float),v.data(),GL_STREAM_DRAW);
        glDrawArrays(GL_TRIANGLES,0,static_cast<GLsizei>(v.size()/5));
        glBindVertexArray(0);
        if(page==Page::Open && hasPhoto()) {
            drawPhoto(eyeSign);
            if(pointer) {
                std::vector<float> cursor;
                box(cursor,cursorX-13,cursorY-2,26,4,white);
                box(cursor,cursorX-2,cursorY-13,4,26,white);
                glUseProgram(program);glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);
                glBufferData(GL_ARRAY_BUFFER,cursor.size()*sizeof(float),cursor.data(),GL_STREAM_DRAW);
                glDrawArrays(GL_TRIANGLES,0,static_cast<GLsizei>(cursor.size()/5));
                glBindVertexArray(0);
            }
            // Brush outline is drawn after the photo, at actual depth-map brush size.
            if(brushMode && hasDepthPixels() && pointer) {
                float u=0.f,v=0.f;
                if(hitPhoto(cursorX,cursorY,false,u,v)) {
                    float w=704.f,h=300.f;
                    const float aspect=float(photoWidth)/float(photoHeight);
                    if(aspect>w/h)h=w/aspect;else w=h*aspect;
                    const float radiusX=brushRadius*w*zoom/256.f;
                    const float radiusY=brushRadius*h*zoom/256.f;
                    std::vector<float> ring;
                    const Color feedback=painting?(paintingLeft?Color{0.30f,0.60f,1.f}:
                                                    Color{1.f,0.35f,0.68f}):Color{0.9f,0.8f,1.f};
                    constexpr int segments=28;
                    for(int i=0;i<segments;i++){
                        float a=float(i)*6.283185307f/segments;
                        float b=float(i+1)*6.283185307f/segments;
                        float px=cursorX+std::cos(a)*radiusX;
                        float py=cursorY+std::sin(a)*radiusY;
                        float qx=cursorX+std::cos(b)*radiusX;
                        float qy=cursorY+std::sin(b)*radiusY;
                        // Draw narrow rectangles along each segment.
                        const float dx=qx-px,dy=qy-py,length=std::hypot(dx,dy);
                        if(length<0.0001f)continue;
                        const float nx=-dy/length,ny=dx/length;
                        const float t=1.3f;
                        const float pts[6][2]={
                          {px+nx*t,py+ny*t},{qx+nx*t,qy+ny*t},{px-nx*t,py-ny*t},
                          {qx+nx*t,qy+ny*t},{qx-nx*t,qy-ny*t},{px-nx*t,py-ny*t}
                        };
                        for(const auto& p:pts){
                            ring.push_back(p[0]);ring.push_back(p[1]);
                            ring.push_back(feedback.r);ring.push_back(feedback.g);
                            ring.push_back(feedback.b);
                        }
                    }
                    glUseProgram(program);glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);
                    glBufferData(GL_ARRAY_BUFFER,ring.size()*sizeof(float),ring.data(),GL_STREAM_DRAW);
                    glDrawArrays(GL_TRIANGLES,0,static_cast<GLsizei>(ring.size()/5));
                    glBindVertexArray(0);
                }
            }
        }
    }
    ~Renderer(){clearQuickDepth();
        if(photoTexture)glDeleteTextures(1,&photoTexture);
        if(photoProgram)glDeleteProgram(photoProgram);
        if(photoVbo)glDeleteBuffers(1,&photoVbo);
        if(photoVao)glDeleteVertexArrays(1,&photoVao);
        if(vbo)glDeleteBuffers(1,&vbo);if(vao)glDeleteVertexArrays(1,&vao);
        if(program)glDeleteProgram(program);}
};
} // namespace vrui
