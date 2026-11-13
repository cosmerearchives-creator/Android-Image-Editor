#pragma once
#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <vector>
#include <string>
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
        if(y>=58&&y<=154)return 11;
    } else if (page==Page::Open) {
        if(y>=665&&y<=715) {
            if(x<=385)return 14;
            if(x<=610)return 15;
            if(x>=645&&x<=746)return 12;
            if(x>=754)return 13;
        }
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
    } else if (page==Page::Projects) {
        if(y>=92&&y<=190)return 3;
        if(y>=580&&y<=635)return x<512?46:47;
        const int ys[]={495,405,315,225};
        for(int i=0;i<4;i++)if(y>=ys[i]&&y<=ys[i]+64)return 40+i;
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
    bool paintingRight=false;
    bool brushSoft=true;
    bool edgeAware=true;
    float brushStrength=1.0f;
    float leftStrength=1.0f,rightStrength=1.0f;
    float leftRadius=13.f,rightRadius=13.f;
    float leftHardness=0.f,rightHardness=0.f;
    std::vector<std::string> projectNames;
    int projectPage=0;
    void setProjects(std::vector<std::string> titles){
        projectNames=std::move(titles);
        projectPage=std::clamp(projectPage,0,std::max(0,int((projectNames.size()+3)/4)-1));
    }
    float disparityStrength=0.8f;
    std::vector<float> photoLuminance;
    // Immersive controls are rendered on a separate physical compositor quad.
    // Coordinates use that quad's 1024x768 logical UI space.
    int toolbarAt(float x,float y,bool immersive) const {
        if(immersive){
            if(x<16||x>1008||y<16||y>752)return -1;
            if(y>=660)return 28;
            if(y>=515&&y<=615){
                if(x<260)return 20;
                if(x<500)return 21;
                if(x<740)return 22;
                return 23;
            }
            if(y>=405&&y<=490)return x<510?24:25;
            if(y>=320&&y<=395)return 26;
            if(y>=235&&y<=310)return x<510?29:27;
            if(y>=150&&y<=225)return x<510?30:31;
            if(y>=65&&y<=140)return x<510?32:33;
            return -1;
        }
        if(y>=335&&y<=405){
            if(x>=180&&x<=495)return 26;
            if(x>=510&&x<=845)return 27;
        }
        return -1;
    }
    void slideAt(int target,float x,bool immersive){
        if(!immersive){
            if(target==26)disparityStrength=std::clamp((x-180.f)/315.f*2.f,0.f,2.f);
            if(target==27)rightStrength=std::clamp(0.1f+(x-510.f)/335.f*2.9f,0.1f,3.f);
            return;
        }
        const float lo=(target==26)?45.f:(target==27||target==31||target==33)?540.f:45.f;
        const float hi=(target==26)?975.f:(target==27||target==31||target==33)?975.f:480.f;
        const float t=std::clamp((x-lo)/(hi-lo),0.f,1.f);
        switch(target){
            case 26:disparityStrength=t*2.f;break;
            case 29:leftStrength=0.1f+t*2.9f;break;
            case 27:rightStrength=0.1f+t*2.9f;break;
            case 30:leftRadius=3.f+t*57.f;break;
            case 31:rightRadius=3.f+t*57.f;break;
            case 32:leftHardness=t;break;
            case 33:rightHardness=t;break;
        }
    }
    // Stroke-wise history; cap memory (~1 MiB each stack at 16 snapshots).
    std::vector<std::vector<uint8_t>> undoHistory,redoHistory;
    std::vector<uint8_t> strokeBefore;
    bool strokeActive=false;
    bool canUndo() const {return !undoHistory.empty();}
    bool canRedo() const {return !redoHistory.empty();}
    void beginStroke() {
        if(strokeActive||!hasDepthPixels())return;
        strokeBefore=depthCpu;
        strokeActive=true;
    }
    bool finishStroke() {
        if(!strokeActive)return false;
        strokeActive=false;
        bool modified=strokeBefore!=depthCpu;
        if(modified) {
            if(undoHistory.size()>=16)undoHistory.erase(undoHistory.begin());
            undoHistory.push_back(std::move(strokeBefore));
            redoHistory.clear();
        }
        strokeBefore.clear();
        return modified;
    }
    void restoreGpuDepth() {
        if(!hasDepthPixels()||!depthTexture)return;
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D,depthTexture);
        glPixelStorei(GL_UNPACK_ALIGNMENT,1);
        glTexSubImage2D(GL_TEXTURE_2D,0,0,0,256,256,GL_RED,
                        GL_UNSIGNED_BYTE,depthCpu.data());
        glBindTexture(GL_TEXTURE_2D,0);
        glActiveTexture(GL_TEXTURE0);
    }
    bool undoDepth() {
        if(strokeActive||undoHistory.empty()||!hasDepthPixels())return false;
        if(redoHistory.size()>=16)redoHistory.erase(redoHistory.begin());
        redoHistory.push_back(depthCpu);
        depthCpu=std::move(undoHistory.back());
        undoHistory.pop_back();
        restoreGpuDepth();
        return true;
    }
    bool redoDepth() {
        if(strokeActive||redoHistory.empty()||!hasDepthPixels())return false;
        if(undoHistory.size()>=16)undoHistory.erase(undoHistory.begin());
        undoHistory.push_back(depthCpu);
        depthCpu=std::move(redoHistory.back());
        redoHistory.pop_back();
        restoreGpuDepth();
        return true;
    }
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
    bool brushAt(float sx,float sy,bool pullTowardViewer,bool immersive=false) {
        if(!brushMode||!hasDepthPixels())return false;
        float u=0.f,v=0.f;
        if(!hitPhoto(sx,sy,immersive,u,v))return false;
        const float cx=u*255.f,cy=(1.f-v)*255.f;
        const float radiusValue=pullTowardViewer?rightRadius:leftRadius;
        const float hardness=pullTowardViewer?rightHardness:leftHardness;
        const int radius=static_cast<int>(std::ceil(radiusValue));
        const int xa=std::max(0,static_cast<int>(cx)-radius),
                  xb=std::min(255,static_cast<int>(cx)+radius),
                  ya=std::max(0,static_cast<int>(cy)-radius),
                  yb=std::min(255,static_cast<int>(cy)+radius);
        bool edited=false;
        for(int y=ya;y<=yb;++y)for(int x=xa;x<=xb;++x){
            const float dist=std::hypot(float(x)-cx,float(y)-cy);
            if(dist>radiusValue)continue;
            const float relative=dist/radiusValue;
            const float falloff=relative<=hardness?1.f:
                std::pow(std::clamp((1.f-relative)/std::max(0.01f,1.f-hardness),0.f,1.f),1.3f);
            const size_t i=size_t(y)*256u+size_t(x);
            // Optional protection against painting across visible photo edges.
            const float edgePenalty=(edgeAware && photoLuminance.size()==256u*256u)
                ?std::exp(-std::abs(photoLuminance[i]-photoLuminance[
                    size_t(std::clamp(int(cy),0,255))*256u+
                    size_t(std::clamp(int(cx),0,255))])*10.f):1.f;
            const int delta=std::max(0,static_cast<int>(std::lround(
                8.f*(pullTowardViewer?rightStrength:leftStrength)*falloff*edgePenalty)));
            if(!delta)continue;
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
        undoHistory.clear();redoHistory.clear();strokeBefore.clear();
        strokeActive=false;
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
    // 0 idle, 1 checking, 2 downloading, 3 up-to-date,
    // 4 installer opened, 5 error, 6 permission required.
    int updateState=0,updatePercent=0;
    int depthLevel=1; // Legacy preset remains for compatibility with old controls.
    float depthStrength() const {
        return stereoEnabled ? disparityStrength : 0.f;
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
        // Cached at depth-map resolution; used only for brush edge guidance.
        photoLuminance.resize(256u*256u);
        for(int y=0;y<256;++y)for(int x=0;x<256;++x) {
            const int sx=std::min(width-1,x*width/256);
            const int sy=std::min(height-1,y*height/256);
            const size_t offset=(size_t(sy)*width+sx)*4;
            photoLuminance[size_t(y)*256+x]=
                (0.2126f*rgba[offset]+0.7152f*rgba[offset+1]+0.0722f*rgba[offset+2])/255.f;
        }
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
            box(v,170,58,684,96,hovered==11?purpleHover:button);
            label(v,"CHECK FOR UPDATES",290,90,3,white);
            switch(updateState) {
                case 1:label(v,"CHECKING",715,168,2,muted);break;
                case 2: {
                    const std::string status="DOWNLOAD "+std::to_string(updatePercent)+"%";
                    label(v,status.c_str(),695,168,2,muted);break;
                }
                case 3:label(v,"UP TO DATE",695,168,2,muted);break;
                case 4:label(v,"INSTALLER OPEN",650,168,2,muted);break;
                case 5:label(v,"UPDATE FAILED",665,168,2,muted);break;
                case 6:label(v,"ALLOW INSTALL",670,168,2,muted);break;
                default:break;
            }
        } else {
            const char* title=page==Page::Open?"OPEN IMAGE":
                page==Page::Projects?"MY PROJECTS":"VIEW GALLERY";
            int n=static_cast<int>(std::strlen(title));
            label(v,title,512-n*15,page==Page::Open?615:475,5,white);
            if(page==Page::Open) {
                box(v,645,665,101,50,hovered==12&&canUndo()?purpleHover:button);
                label(v,"UNDO",658,678,2,canUndo()?white:muted);
                box(v,754,665,100,50,hovered==13&&canRedo()?purpleHover:button);
                label(v,"REDO",765,678,2,canRedo()?white:muted);
                if(brushMode && hasDepthPixels()) {
                    label(v,"L PUSH R PULL",702,630,2,muted);
                    const std::string brushText="SIZE "+std::to_string(int(brushRadius));
                    label(v,brushText.c_str(),702,604,2,muted);
                }
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
        glUseProgram(program);glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);
        glBufferData(GL_ARRAY_BUFFER,v.size()*sizeof(float),v.data(),GL_STREAM_DRAW);
        glDrawArrays(GL_TRIANGLES,0,static_cast<GLsizei>(v.size()/5));
        glBindVertexArray(0);
        if(page==Page::Open && hasPhoto()) drawPhoto(eyeSign);
    }
    void submitUi(const std::vector<float>& triangles) {
        if(triangles.empty())return;
        glUseProgram(program);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER,vbo);
        glBufferData(GL_ARRAY_BUFFER,triangles.size()*sizeof(float),
                     triangles.data(),GL_STREAM_DRAW);
        glDrawArrays(GL_TRIANGLES,0,static_cast<GLsizei>(triangles.size()/5));
        glBindVertexArray(0);
    }
    void drawTools(Page page,int hover) {
        if(page!=Page::Open)return;
        std::vector<float> v;
        box(v,170,330,684,84,{0.095f,0.075f,0.14f});
        const float dl=180.f,dr=495.f,bl=510.f,br=845.f,y=348.f;
        label(v,"DEPTH",184,375,2,white);
        label(v,"RIGHT POWER",514,375,2,white);
        box(v,dl,y,dr-dl,7,{0.25f,0.20f,0.32f});
        box(v,dl,y,(dr-dl)*disparityStrength/2.f,7,purpleHover);
        box(v,dl+(dr-dl)*disparityStrength/2.f-5,y-6,10,19,white);
        box(v,bl,y,br-bl,7,{0.25f,0.20f,0.32f});
        box(v,bl,y,(br-bl)*(rightStrength-0.1f)/2.9f,7,purpleHover);
        box(v,bl+(br-bl)*(rightStrength-0.1f)/2.9f-5,y-6,10,19,white);
        submitUi(v);
    }
    void drawFloatingTools(int hover,float pointerX,float pointerY,bool pointer) {
        std::vector<float> v;
        box(v,0,0,1024,768,{0.075f,0.055f,0.13f});
        box(v,16,657,992,101,hover==28?purpleHover:purple);
        label(v,"MOVE PANEL - HOLD GRIP",92,693,4,white);
        const char* names[]={
            stereoEnabled?"3D ON":"3D OFF",brushMode?"BRUSH ON":"BRUSH OFF",
            brushSoft?"SOFT":"HARD",edgeAware?"EDGE ON":"EDGE OFF",
            "UNDO","REDO"
        };
        const int ids[]={20,21,22,23,24,25};
        const float bx[]={18,265,512,759,18,512};
        const float by[]={515,515,515,515,405,405};
        const float bw[]={229,229,229,249,476,494};
        const float bh[]={100,100,100,100,85,85};
        for(int i=0;i<6;i++) {
            const bool chosen=(i==0&&stereoEnabled)||(i==1&&brushMode)||
                              (i==2&&brushSoft)||(i==3&&edgeAware);
            box(v,bx[i],by[i],bw[i],bh[i],hover==ids[i]?purpleHover:
                chosen?purple:button);
            label(v,names[i],int(bx[i]+18),int(by[i]+33),3,
                  (i==4&&!canUndo())||(i==5&&!canRedo())?muted:white);
        }
        const int sy[]={323,208,93};
        const char* captions[]={"DEPTH STRENGTH","LEFT PUSH POWER","RIGHT PULL POWER"};
        const float frac[]={disparityStrength/2.f,
                            (leftStrength-0.1f)/2.9f,
                            (rightStrength-0.1f)/2.9f};
        for(int i=0;i<3;i++){
            label(v,captions[i],45,sy[i]+35,3,white);
            const float y=float(sy[i]);
            box(v,45,y,928,15,{0.26f,0.20f,0.32f});
            box(v,45,y,928*frac[i],15,purpleHover);
            box(v,45+928*frac[i]-9,y-12,18,39,white);
        }
        if(pointer){
            box(v,pointerX-16,pointerY-3,32,6,white);
            box(v,pointerX-3,pointerY-16,6,32,white);
        }
        submitUi(v);
    }
    void drawHandCursor(float x,float y,bool valid,bool left,bool active,
                        bool immersive) {
        if(!valid)return;
        std::vector<float> v;
        // Both cursors are rendered independently even when both hands point
        // to the photo. Plus is right/pull; minus is left/push.
        if(brushMode) {
            float u=0.f,w=0.f;
            if(hitPhoto(x,y,immersive,u,w)) {
                float pw=immersive?1024.f:704.f,ph=immersive?768.f:300.f;
                const float ratio=float(photoWidth)/float(photoHeight);
                if(ratio>pw/ph)ph=pw/ratio;else pw=ph*ratio;
                const float radiusValue=left?leftRadius:rightRadius;
                const float rx=std::max(2.f,radiusValue*pw*zoom/256.f);
                const float ry=std::max(2.f,radiusValue*ph*zoom/256.f);
                const Color tint=left?Color{0.31f,0.65f,1.f}:Color{1.f,0.36f,0.67f};
                const Color ink=active?tint:tint;
                constexpr int segs=48;
                for(int i=0;i<segs;i++){
                    const float a=float(i)*6.283185307f/segs;
                    const float b=float(i+1)*6.283185307f/segs;
                    const float ax=x+std::cos(a)*rx,ay=y+std::sin(a)*ry;
                    const float bx=x+std::cos(b)*rx,by=y+std::sin(b)*ry;
                    const float dx=bx-ax,dy=by-ay,len=std::hypot(dx,dy);
                    if(len<0.0001f)continue;
                    const float nx=-dy/len,ny=dx/len,t=1.4f;
                    const float points[6][2]={
                        {ax+nx*t,ay+ny*t},{bx+nx*t,by+ny*t},{ax-nx*t,ay-ny*t},
                        {bx+nx*t,by+ny*t},{bx-nx*t,by-ny*t},{ax-nx*t,ay-ny*t}
                    };
                    for(const auto& p:points){
                        v.push_back(p[0]);v.push_back(p[1]);
                        v.push_back(ink.r);v.push_back(ink.g);v.push_back(ink.b);
                    }
                }
                box(v,x-11,y-2,22,4,ink);
                if(!left)box(v,x-2,y-11,4,22,ink);
                submitUi(v);
                return;
            }
        }
        // Keep a distinct left-hand marker even when the depth map has
        // not been generated. A valid left aim must never disappear.
        const Color tint=left?Color{0.31f,0.65f,1.f}:white;
        box(v,x-9,y-2,18,4,tint);
        if(!left)box(v,x-2,y-9,4,18,tint);
        submitUi(v);
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
