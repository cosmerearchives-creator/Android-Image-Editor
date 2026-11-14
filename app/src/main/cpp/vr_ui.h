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
enum class Page {Home, Open, Projects, Gallery, Immersive, AiDepth, Collage, Curves, Mask};
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
        if(y>=730&&y<=765)return x<512?70:90;
        if(y>=590&&y<=650)return x<390?60:x<610?61:62;
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
    } else if(page==Page::Mask){
        if(y>=720&&y<=760){
            if(x<395)return 117;
            if(x<625)return 118;
            return 119;
        }
        if(y>=665&&y<=715)return x<512?100:101;
        if(y>=110&&y<=180)return x<512?120:121;
        if(y>=590&&y<=648){
            if(x<340)return 102;
            if(x<512)return 103;
            if(x<684)return 104;
            return 105;
        }
        if(y>=205&&y<=291){
            if(x<340)return 106;
            if(x<512)return 107;
            if(x<684)return 108;
            return 109;
        }
        if(y>=490&&y<=550)return 116;
        if(y>=402&&y<=480)return 115;
        if(y>=310&&y<=360){
            if(x<512)return 112;
            if(x<684)return 113;
            return 114;
        }
        if(y>=36&&y<=88)return 110;
        return -1;
    } else if(page==Page::Curves){
        if(y>=36&&y<=105)return 91;
        if(y>=116&&y<=186)return 111;
        const int rows[]={535,425,315,205};
        for(int i=0;i<4;i++)if(y>=rows[i]&&y<=rows[i]+70)return 92+i*2+(x>=512?1:0);
        return -1;
    } else if(page==Page::Collage){
        if(y>=720&&y<=762){if(x<342)return 83;if(x<512)return 84;if(x<682)return 85;return 86;}
        if(y>=665&&y<=715)return x<512?71:72;
        if(y>=590&&y<=650){if(x<342)return 73;if(x<512)return 74;if(x<682)return 75;return 76;}
        if(y>=205&&y<=291){if(x<342)return 77;if(x<512)return 78;if(x<682)return 79;return 80;}
        if(y>=36&&y<=88)return x<512?81:82;
        if(y>=92&&y<=190)return 3;
    } else if (page==Page::AiDepth) {
        if(y>=92&&y<=190)return 3;
        if(y>=205&&y<=291){
            if(x<=389)return 50;
            if(x<=617)return 51;
            return 52;
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
    int aiState=0; // 0 idle, 1 model download, 2 local inference,
                    // 3 preview ready, 4 error, 5 canceled.
    int aiPercent=0;
    bool aiPreview=false;

    // v0.8: connected-color selection, constrained depth sculpting.
    int collageCount=0,collageActive=0;
    bool selectionMode=false;
    bool maskSubtract=false;
    int maskToolMode=0; // 0 literal paint, 1 smart region, 2 isolate clicked selection
    float maskBrushRadius=28.f;
    std::vector<std::vector<uint8_t>> maskUndo,maskRedo;
    std::vector<uint8_t> maskStrokeBefore;
    bool maskStrokeActive=false;
    bool canUndoMask()const{return !maskUndo.empty();}
    bool canRedoMask()const{return !maskRedo.empty();}
    void beginMaskEdit(){if(maskStrokeActive)return;maskStrokeBefore=selectionMask;maskStrokeActive=true;}
    bool finishMaskEdit(){
        if(!maskStrokeActive)return false;
        maskStrokeActive=false;
        const bool changed=maskStrokeBefore!=selectionMask;
        if(changed){
            if(maskUndo.size()>=8)maskUndo.erase(maskUndo.begin());
            maskUndo.push_back(std::move(maskStrokeBefore));
            maskRedo.clear();
        }
        maskStrokeBefore.clear();return changed;
    }
    bool undoMask(){
        if(maskStrokeActive||maskUndo.empty())return false;
        if(maskRedo.size()>=8)maskRedo.erase(maskRedo.begin());
        maskRedo.push_back(std::move(selectionMask));
        selectionMask=std::move(maskUndo.back());maskUndo.pop_back();
        if(hasSelection())uploadMask();else clearMask();
        return true;
    }
    bool redoMask(){
        if(maskStrokeActive||maskRedo.empty())return false;
        if(maskUndo.size()>=8)maskUndo.erase(maskUndo.begin());
        maskUndo.push_back(std::move(selectionMask));
        selectionMask=std::move(maskRedo.back());maskRedo.pop_back();
        if(hasSelection())uploadMask();else clearMask();
        return true;
    }
    int maskTolerance=65;
    bool depthGuidedSelection=true;
    int maskDepthTolerance=30;
    int autoMaskStatus=0; // 0 idle, 1 generated, 2 no reliable region
    int aiSegmentState=0, aiSegmentPercent=0;
    // Loading a new AI mask replaces the current selection, but does not
    // modify the depth map or remove manual refinement capability.
    bool applyAiMask(const std::vector<uint8_t>& mask){
        if(mask.size()!=size_t(MASK_EDGE)*MASK_EDGE||!hasPhoto())return false;
        beginMaskEdit();
        selectionMask=mask;
        uploadMask();
        autoMaskStatus=0;
        finishMaskEdit();
        return true;
    }
    std::vector<uint8_t> selectionMask;
    GLuint selectionTexture=0;
    static constexpr int MASK_EDGE=1024;
    bool hasSelection()const{return selectionMask.size()==size_t(MASK_EDGE)*MASK_EDGE;}
    void uploadMask(){
        if(!hasSelection())return;
        if(!selectionTexture)glGenTextures(1,&selectionTexture);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D,selectionTexture);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        glPixelStorei(GL_UNPACK_ALIGNMENT,1);
        glTexImage2D(GL_TEXTURE_2D,0,GL_R8,MASK_EDGE,MASK_EDGE,0,GL_RED,GL_UNSIGNED_BYTE,selectionMask.data());
        glBindTexture(GL_TEXTURE_2D,0);
        glActiveTexture(GL_TEXTURE0);
    }
    void clearMask(){
        autoMaskStatus=0;
        selectionMask.clear();
        if(selectionTexture)glDeleteTextures(1,&selectionTexture);
        selectionTexture=0;
    }
    void invertMask(){
        if(!hasSelection())return;
        for(auto& p:selectionMask)p=p?0:255;
        uploadMask();
    }
    // Literal pixel edit: no flood fill, no model call and no color matching.
    // A controller stroke is one history entry. Update only its local GPU patch.
    bool paintMaskAt(float sx,float sy,bool add,bool fullPhoto=true){
        float u=0.f,v=0.f;
        if(!hitPhoto(sx,sy,fullPhoto,u,v))return false;
        const float cx=u*float(MASK_EDGE-1),cy=(1.f-v)*float(MASK_EDGE-1);
        const int radius=int(std::ceil(maskBrushRadius));
        const int xa=std::max(0,int(cx)-radius),xb=std::min(MASK_EDGE-1,int(cx)+radius);
        const int ya=std::max(0,int(cy)-radius),yb=std::min(MASK_EDGE-1,int(cy)+radius);
        if(!hasSelection()){
            if(!add)return false;
            selectionMask.assign(size_t(MASK_EDGE)*MASK_EDGE,0);
        }
        bool changed=false;
        for(int y=ya;y<=yb;++y)for(int x=xa;x<=xb;++x){
            if(std::hypot(float(x)-cx,float(y)-cy)>maskBrushRadius)continue;
            uint8_t& pixel=selectionMask[size_t(y)*MASK_EDGE+size_t(x)];
            const uint8_t desired=add?255:0;
            if(pixel!=desired){pixel=desired;changed=true;}
        }
        if(!changed)return false;
        if(!selectionTexture){uploadMask();return true;}
        const int width=xb-xa+1,height=yb-ya+1;
        std::vector<uint8_t> patch(size_t(width)*height);
        for(int row=0;row<height;++row)
            std::copy_n(selectionMask.data()+size_t(ya+row)*MASK_EDGE+size_t(xa),
                        width,patch.data()+size_t(row)*width);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D,selectionTexture);
        glPixelStorei(GL_UNPACK_ALIGNMENT,1);
        glTexSubImage2D(GL_TEXTURE_2D,0,xa,ya,width,height,
                        GL_RED,GL_UNSIGNED_BYTE,patch.data());
        glBindTexture(GL_TEXTURE_2D,0);
        glActiveTexture(GL_TEXTURE0);
        return true;
    }
    void adjustMaskTolerance(int delta){
        maskTolerance=std::clamp(maskTolerance+delta,10,150);
    }
    void growOrShrinkMask(bool grow){
        if(!hasSelection())return;
        std::vector<uint8_t> next(selectionMask.size(),0);
        for(int y=0;y<MASK_EDGE;++y)for(int x=0;x<MASK_EDGE;++x){
            bool result=!grow;
            for(int oy=-1;oy<=1;++oy)for(int ox=-1;ox<=1;++ox){
                const int sx=x+ox,sy=y+oy;
                const bool chosen=sx>=0&&sx<MASK_EDGE&&sy>=0&&sy<MASK_EDGE
                    &&selectionMask[size_t(sy)*size_t(MASK_EDGE)+size_t(sx)]!=0;
                if(grow)result=result||chosen;
                else result=result&&chosen;
            }
            next[size_t(y)*size_t(MASK_EDGE)+size_t(x)]=result?255:0;
        }
        selectionMask=std::move(next);
        uploadMask();
    }
    void selectAllMask(){selectionMask.assign(size_t(MASK_EDGE)*MASK_EDGE,255);uploadMask();}
    void smoothMask(){
        if(!hasSelection())return;
        std::vector<uint8_t> next(selectionMask.size(),0);
        for(int y=0;y<MASK_EDGE;++y)for(int x=0;x<MASK_EDGE;++x){
            int count=0;
            for(int oy=-1;oy<=1;++oy)for(int ox=-1;ox<=1;++ox){
                const int sx=x+ox,sy=y+oy;
                if(sx>=0&&sx<MASK_EDGE&&sy>=0&&sy<MASK_EDGE&&
                   selectionMask[size_t(sy)*size_t(MASK_EDGE)+size_t(sx)])++count;
            }
            next[size_t(y)*size_t(MASK_EDGE)+size_t(x)]=count>=5?255:0;
        }
        selectionMask=std::move(next);
        uploadMask();
    }
    // Automatic foreground proposal from the currently applied depth map.
    // The optional AI depth model supplies the depth map; this routine is
    // geometric segmentation, NOT semantic object recognition.
    bool autoForegroundMask(){
        if(!hasDepthPixels() || !hasPhoto())return false;
        constexpr int edge=MASK_EDGE;
        constexpr size_t count=size_t(edge)*edge;
        // Use the top fifth of the image's relative depth distribution.
        // This adapts to each imported image rather than assuming fixed
        // near/far values in a grayscale depth PNG.
        std::array<int,256> histogram{};
        uint8_t minimum=255,maximum=0;
        for(uint8_t d:depthCpu){
            histogram[d]++;
            minimum=std::min(minimum,d);
            maximum=std::max(maximum,d);
        }
        if(int(maximum)-int(minimum)<14)return false;
        int accumulation=0;
        int threshold=int(maximum);
        for(int d=255;d>=0;--d){
            accumulation+=histogram[size_t(d)];
            if(accumulation>=int(count/5)){
                threshold=d;
                break;
            }
        }
        // Prevent an almost-uniform image from becoming "all foreground."
        threshold=std::max(threshold,int(minimum)+(int(maximum)-int(minimum))/3);
        std::vector<uint8_t> visited(count,0),candidate(count,0);
        std::vector<int> queue;
        queue.reserve(16384);
        float bestScore=-1.f;
        std::vector<int> winner;
        for(size_t start=0;start<count;++start){
            if(visited[start]||depthCpu[start]<threshold)continue;
            queue.clear();
            queue.push_back(int(start));
            visited[start]=1;
            double centerWeight=0.0,depthSum=0.0;
            for(size_t head=0;head<queue.size();++head){
                const int p=queue[head],x=p%edge,y=p/edge;
                const float dx=(float(x)-float(edge)/2.f)/(float(edge)/2.f);
                const float dy=(float(y)-float(edge)/2.f)/(float(edge)/2.f);
                centerWeight+=std::max(0.0,1.0-double(dx*dx+dy*dy));
                depthSum+=depthCpu[size_t(p)];
                const int around[]={x>0?p-1:-1,x+1<edge?p+1:-1,
                                    y>0?p-edge:-1,y+1<edge?p+edge:-1};
                for(int next:around){
                    if(next<0||visited[size_t(next)]||
                       depthCpu[size_t(next)]<threshold)continue;
                    visited[size_t(next)]=1;
                    queue.push_back(next);
                }
            }
            if(queue.size()<256)continue;
            // Large, central, near components are preferred to isolated
            // highlights; do not silently select the entire image.
            if(queue.size()>count*3/4)continue;
            const double area=double(queue.size());
            const float score=float(
                area*(0.5+centerWeight/std::max(1.0,area))*
                (0.5+depthSum/(255.0*std::max(1.0,area))));
            if(score>bestScore){
                bestScore=score;
                winner=queue;
            }
        }
        if(winner.empty())return false;
        selectionMask.assign(count,0);
        for(int pixel:winner)selectionMask[size_t(pixel)]=255;
        uploadMask();
        return true;
    }
    // Keep one clicked region within an existing AI/manual selection.
    // Connected depth-and-color guided isolation, not semantic classification.
    bool isolateAt(float sx,float sy){
        if(!hasSelection()||photoRgb.size()!=size_t(MASK_EDGE)*MASK_EDGE*3u)return false;
        float u=0.f,v=0.f;
        if(!hitPhoto(sx,sy,true,u,v))return false;
        const int x=std::clamp(int(u*(MASK_EDGE-1)),0,MASK_EDGE-1);
        const int y=std::clamp(int((1.f-v)*(MASK_EDGE-1)),0,MASK_EDGE-1);
        const int start=y*MASK_EDGE+x;
        if(!selectionMask[size_t(start)])return false;
        const int seedColor=start*3;
        const bool useDepth=hasDepthPixels();
        const int baseDepth=useDepth?int(depthCpu[size_t(start)]):0;
        std::vector<uint8_t> selected(selectionMask.size(),0);
        std::vector<int> queue{start};
        selected[size_t(start)]=255;
        for(size_t i=0;i<queue.size();++i){
            const int p=queue[i],px=p%MASK_EDGE,py=p/MASK_EDGE;
            const int around[]={px>0?p-1:-1,px<MASK_EDGE-1?p+1:-1,
                                py>0?p-MASK_EDGE:-1,py<MASK_EDGE-1?p+MASK_EDGE:-1};
            for(int q:around){
                if(q<0||selected[size_t(q)]||!selectionMask[size_t(q)])continue;
                const int color=q*3,near=p*3;
                int seedDistance=0,localDistance=0;
                for(int c=0;c<3;++c){
                    int sd=int(photoRgb[size_t(color+c)])-
                           int(photoRgb[size_t(seedColor+c)]);
                    int ld=int(photoRgb[size_t(color+c)])-
                           int(photoRgb[size_t(near+c)]);
                    seedDistance+=sd*sd;localDistance+=ld*ld;
                }
                const int threshold=std::max(25,maskTolerance);
                if(seedDistance>threshold*threshold*2||
                   localDistance>threshold*threshold/2)continue;
                if(useDepth){
                    const int d=int(depthCpu[size_t(q)]);
                    if(std::abs(d-baseDepth)>maskDepthTolerance ||
                       std::abs(d-int(depthCpu[size_t(p)]))>
                           std::max(8,maskDepthTolerance/2))continue;
                }
                selected[size_t(q)]=255;
                queue.push_back(q);
            }
        }
        if(queue.size()<8 || queue.size()==selectionMask.size())return false;
        selectionMask=std::move(selected);
        uploadMask();
        return true;
    }
    bool selectAt(float sx,float sy,bool additive=true,bool fullPhoto=false){
        if(photoRgb.size()!=size_t(DEPTH_EDGE)*DEPTH_EDGE*3u)return false;
        float u=0.f,v=0.f;
        if(!hitPhoto(sx,sy,fullPhoto,u,v))return false;
        int x=std::clamp(int(u*(MASK_EDGE-1)),0,MASK_EDGE-1),y=std::clamp(int((1.f-v)*(MASK_EDGE-1)),0,MASK_EDGE-1);
        int start=y*MASK_EDGE+x;
        std::vector<uint8_t> chosen(size_t(MASK_EDGE)*MASK_EDGE,0);
        std::vector<int> pending{start};
        chosen[start]=255;
        const int seed=(y*DEPTH_EDGE+x)*3;
        const int red=photoRgb[seed],green=photoRgb[seed+1],blue=photoRgb[seed+2];
        const bool useDepth=depthGuidedSelection&&hasDepthPixels();
        const int seedDepth=useDepth?int(depthCpu[size_t(start)]):0;
        for(size_t i=0;i<pending.size();i++){
            int p=pending[i],px=p%MASK_EDGE,py=p/MASK_EDGE;
            const int around[]={px? p-1:-1,px<MASK_EDGE-1?p+1:-1,py?p-MASK_EDGE:-1,py<MASK_EDGE-1?p+MASK_EDGE:-1};
            for(int q:around){
                if(q<0||chosen[q])continue;
                const int colorIndex=q*3;
                int dr=int(photoRgb[colorIndex])-red,dg=int(photoRgb[colorIndex+1])-green,
                    db=int(photoRgb[colorIndex+2])-blue;
                // Require similarity both to the seed and to the advancing
                // selection frontier. This avoids crossing sharp color edges
                // through a single gradual chain of superficially similar pixels.
                if(dr*dr+dg*dg+db*db>maskTolerance*maskTolerance)continue;
                const int frontierIndex=p*3;
                const int fr=int(photoRgb[colorIndex])-int(photoRgb[frontierIndex]);
                const int fg=int(photoRgb[colorIndex+1])-int(photoRgb[frontierIndex+1]);
                const int fb=int(photoRgb[colorIndex+2])-int(photoRgb[frontierIndex+2]);
                const int localThreshold=std::max(12,maskTolerance/2);
                if(fr*fr+fg*fg+fb*fb>localThreshold*localThreshold)continue;
                if(useDepth){
                    const int sampleDepth=int(depthCpu[size_t(q)]);
                    const int frontierDepth=int(depthCpu[size_t(p)]);
                    // Reject abrupt depth jumps and distant depth layers,
                    // even when a subject and its background share a color.
                    if(std::abs(sampleDepth-seedDepth)>maskDepthTolerance ||
                       std::abs(sampleDepth-frontierDepth)>
                           std::max(8,maskDepthTolerance/2))continue;
                }
                chosen[q]=255;pending.push_back(q);
            }
        }
        if(!hasSelection()){
            selectionMask.assign(size_t(MASK_EDGE)*MASK_EDGE,0);
        }
        for(size_t p=0;p<chosen.size();++p){
            if(!chosen[p])continue;
            selectionMask[p]=additive?255:0;
        }
        uploadMask();
        selectionMode=false;
        return true;
    }
    bool brushMode=false;
    bool painting=false;
    bool paintingLeft=false;
    bool paintingRight=false;
    int brushBlockReason=0;
    const char* brushFeedback() const {
        switch(brushBlockReason) {
            case 1:return "BRUSH OFF - ENABLE BRUSH";
            case 2:return "NO DEPTH - PRESS QUICK";
            case 3:return "DEPTH STILL PROCESSING";
            case 4:return "SELECT AREA ACTIVE";
            case 5:return "FINISH AI PREVIEW";
            case 6:return "RELEASE GRIP TO SCULPT";
            case 7:return "AIM AT THE PHOTO";
            case 8:return "MOVE AWAY FROM CONTROLS";
            default:return nullptr;
        }
    }
    bool brushSoft=true;
    bool edgeAware=true;
    float brushStrength=1.0f;
    float leftStrength=1.0f,rightStrength=1.0f;
    float leftRadius=52.f,rightRadius=52.f;
    float leftHardness=0.f,rightHardness=0.f;
    std::vector<std::string> projectNames;
    int projectPage=0;
    void setProjects(std::vector<std::string> titles){
        projectNames=std::move(titles);
        projectPage=std::clamp(projectPage,0,std::max(0,int((projectNames.size()+3)/4)-1));
    }
    float disparityStrength=0.8f;
    float nearBoost=0.f,farPush=0.f,depthContrast=1.f,depthMidpoint=0.5f;
    int gapFillMode=1; // 0 off, 1 conservative, 2 stronger; preview only
    float gapFillStrength() const {return gapFillMode==2?1.f:gapFillMode==1?0.45f:0.f;}
    void adjustCurve(int button){
        const float sign=button%2?1.f:-1.f;
        switch((button-92)/2){
            case 0:nearBoost=std::clamp(nearBoost+sign*.1f,0.f,2.f);break;
            case 1:farPush=std::clamp(farPush+sign*.1f,0.f,2.f);break;
            case 2:depthContrast=std::clamp(depthContrast+sign*.1f,0.4f,3.f);break;
            case 3:depthMidpoint=std::clamp(depthMidpoint+sign*.025f,.2f,.8f);break;
        }
    }
    std::vector<float> photoLuminance;
    std::vector<uint8_t> photoRgb;
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
        if(y>=300&&y<=349){
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
            case 30:leftRadius=12.f+t*228.f;break;
            case 31:rightRadius=12.f+t*228.f;break;
            case 32:leftHardness=t;break;
            case 33:rightHardness=t;break;
        }
    }
    // 1024px maps use 1 MiB per snapshot; cap undo/redo at 8 strokes each.
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
            if(undoHistory.size()>=8)undoHistory.erase(undoHistory.begin());
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
        glTexSubImage2D(GL_TEXTURE_2D,0,0,0,DEPTH_EDGE,DEPTH_EDGE,GL_RED,
                        GL_UNSIGNED_BYTE,depthCpu.data());
        glBindTexture(GL_TEXTURE_2D,0);
        glActiveTexture(GL_TEXTURE0);
    }
    bool undoDepth() {
        if(strokeActive||undoHistory.empty()||!hasDepthPixels())return false;
        if(redoHistory.size()>=8)redoHistory.erase(redoHistory.begin());
        redoHistory.push_back(depthCpu);
        depthCpu=std::move(undoHistory.back());
        undoHistory.pop_back();
        restoreGpuDepth();
        return true;
    }
    bool redoDepth() {
        if(strokeActive||redoHistory.empty()||!hasDepthPixels())return false;
        if(undoHistory.size()>=8)undoHistory.erase(undoHistory.begin());
        undoHistory.push_back(depthCpu);
        depthCpu=std::move(redoHistory.back());
        redoHistory.pop_back();
        restoreGpuDepth();
        return true;
    }
    static constexpr int DEPTH_EDGE=1024;
    float brushRadius=52.f; // 1024px depth pixels; 2x old size maintains physical radius
    std::vector<uint8_t> depthCpu;
    bool hasDepthPixels() const {return depthCpu.size()==size_t(DEPTH_EDGE)*DEPTH_EDGE;}
    bool hitPhoto(float sx,float sy,bool immersive,float& u,float& v) const {
        if(!hasPhoto())return false;
        float w=immersive?1024.f:684.f,h=immersive?768.f:208.f;
        float aspect=float(photoWidth)/float(photoHeight);
        if(aspect>w/h)h=w/aspect;else w=h*aspect;
        float x=512.f-w*.5f,y=(immersive?384.f:457.f)-h*.5f;
        if(sx<x||sx>x+w||sy<y||sy>y+h)return false;
        u=centerU+((sx-x)/w-.5f)/zoom;
        v=centerV+(.5f-(sy-y)/h)/zoom;
        return u>=0.f&&u<=1.f&&v>=0.f&&v<=1.f;
    }
    bool brushAt(float sx,float sy,bool pullTowardViewer,bool immersive=false) {
        if(!brushMode||!hasDepthPixels())return false;
        float u=0.f,v=0.f;
        if(!hitPhoto(sx,sy,immersive,u,v))return false;
        const float cx=u*float(DEPTH_EDGE-1),cy=(1.f-v)*float(DEPTH_EDGE-1);
        const float radiusValue=pullTowardViewer?rightRadius:leftRadius;
        const float hardness=pullTowardViewer?rightHardness:leftHardness;
        const int radius=static_cast<int>(std::ceil(radiusValue));
        const int xa=std::max(0,static_cast<int>(cx)-radius),
                  xb=std::min(DEPTH_EDGE-1,static_cast<int>(cx)+radius),
                  ya=std::max(0,static_cast<int>(cy)-radius),
                  yb=std::min(DEPTH_EDGE-1,static_cast<int>(cy)+radius);
        bool edited=false;
        for(int y=ya;y<=yb;++y)for(int x=xa;x<=xb;++x){
            const float dist=std::hypot(float(x)-cx,float(y)-cy);
            if(dist>radiusValue)continue;
            const float relative=dist/radiusValue;
            const float falloff=relative<=hardness?1.f:
                std::pow(std::clamp((1.f-relative)/std::max(0.01f,1.f-hardness),0.f,1.f),1.3f);
            const size_t i=size_t(y)*DEPTH_EDGE+size_t(x);
            if(hasSelection()&&!selectionMask[i])continue;
            // Compare *color* rather than luminance alone so same-brightness,
            // different-hue object boundaries are protected too.
            float edgePenalty=1.f;
            if(edgeAware && photoRgb.size()==size_t(DEPTH_EDGE)*DEPTH_EDGE*3u){
                const size_t anchor=(size_t(std::clamp(int(cy),0,DEPTH_EDGE-1))*DEPTH_EDGE+
                                     size_t(std::clamp(int(cx),0,DEPTH_EDGE-1)))*3u;
                const size_t sample=i*3u;
                const float red=float(int(photoRgb[sample])-int(photoRgb[anchor]))/255.f;
                const float green=float(int(photoRgb[sample+1])-int(photoRgb[anchor+1]))/255.f;
                const float blue=float(int(photoRgb[sample+2])-int(photoRgb[anchor+2]))/255.f;
                edgePenalty=std::exp(-(red*red+green*green+blue*blue)*16.f);
            }
            const int delta=std::max(0,static_cast<int>(std::lround(
                8.f*(pullTowardViewer?rightStrength:leftStrength)*falloff*edgePenalty)));
            if(!delta)continue;
            const int value=std::clamp(int(depthCpu[i])+(pullTowardViewer?delta:-delta),0,255);
            if(value!=int(depthCpu[i])){depthCpu[i]=static_cast<uint8_t>(value);edited=true;}
        }
        if(edited){
            // Upload only the small edited rectangle, not the entire 1 MiB
            // depth map for every interpolated brush sample.
            const int width=xb-xa+1,height=yb-ya+1;
            std::vector<uint8_t> patch(size_t(width)*height);
            for(int row=0;row<height;++row){
                const uint8_t* source=depthCpu.data()+
                    size_t(ya+row)*DEPTH_EDGE+size_t(xa);
                std::copy_n(source,width,patch.data()+size_t(row)*width);
            }
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D,depthTexture);
            glPixelStorei(GL_UNPACK_ALIGNMENT,1);
            glTexSubImage2D(GL_TEXTURE_2D,0,xa,ya,width,height,GL_RED,
                            GL_UNSIGNED_BYTE,patch.data());
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
        if(width!=DEPTH_EDGE||height!=DEPTH_EDGE||gray.size()!=size_t(width)*size_t(height))return;
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
        float w=immersive?1024.f:684.f,h=immersive?768.f:208.f;
        const float ratio=float(photoWidth)/float(photoHeight);
        if(ratio>w/h)h=w/ratio;else w=h*ratio;
        const float x=512.f-w/2.f,y=(immersive?384.f:457.f)-h/2.f;
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
            precision highp float;
            in vec2 uv;
            uniform sampler2D photograph;
            uniform sampler2D quickDepth;
             uniform sampler2D selectionMask;
             uniform int hasSelection;
            uniform int useQuickDepth;
            uniform float eyeSign;
            uniform float depthStrength;
             uniform float nearBoost;
             uniform float farPush;
             uniform float depthContrast;
             uniform float depthMidpoint;
             uniform float gapFillStrength;
            out vec4 frag;
            // Stable inverse stereo warp for strong depth settings.
            // Keep the true depth center neutral to minimize unwanted drift.
            float guessedDepth(vec2 p) {
                vec3 col=texture(photograph,clamp(p,vec2(0.001),vec2(0.999))).rgb;
                float lum=dot(col,vec3(0.2126,0.7152,0.0722));
                float center=1.0-smoothstep(0.0,0.72,length(p-vec2(0.5)));
                return clamp(0.25+0.4*center+0.35*lum,0.0,1.0);
            }
            float mapDepth(vec2 p) {
                p=clamp(p,vec2(0.001),vec2(0.999));
                return useQuickDepth==1?texture(quickDepth,p).r:guessedDepth(p);
            }
            float guidedDepth(vec2 p) {
                float base=mapDepth(p);
                if(useQuickDepth==0)return base;
                vec2 texel=1.0/vec2(textureSize(quickDepth,0));
                // Weight nearby samples by depth similarity. This reduces
                // block boundaries without blurring separate depth objects.
                float sum=base*4.0;
                float weight=4.0;
                for(int i=0;i<4;i++){
                    vec2 delta=i==0?vec2(texel.x,0.0):
                               i==1?vec2(-texel.x,0.0):
                               i==2?vec2(0.0,texel.y):vec2(0.0,-texel.y);
                    float d=mapDepth(p+delta);
                    float w=exp(-abs(d-base)*28.0);
                    sum+=d*w;
                    weight+=w;
                }
                return sum/weight;
            }
            float curveDepth(float d){
                float delta=(d-depthMidpoint)*depthContrast;
                delta=delta>=0.0?delta*(1.0+nearBoost):delta*(1.0+farPush);
                return clamp(depthMidpoint+delta,0.0,1.0);
            }
            void main() {
                vec2 sampleUv=uv;
                const float disparityUnit=0.035;
                // Additional fixed-point passes improve convergence when
                // disparity is much stronger than the previous default.
                for(int i=0;i<5;i++){
                    float depth=curveDepth(guidedDepth(sampleUv));
                    float offset=eyeSign*depthStrength*(depth-0.5)*disparityUnit;
                    // Retain a narrow safe border to avoid black edge sampling.
                    vec2 target=vec2(uv.x+offset,uv.y);
                    sampleUv=mix(sampleUv,clamp(target,vec2(0.001),vec2(0.999)),0.8);
                }
                // A final depth check preferentially chooses nearer surfaces
                // when the inverse warp crosses a sharp foreground boundary.
                float foreground=curveDepth(guidedDepth(sampleUv));
                vec2 next=clamp(vec2(uv.x+eyeSign*depthStrength*
                                    (foreground-0.5)*disparityUnit,uv.y),
                                vec2(0.001),vec2(0.999));
                float nextDepth=curveDepth(guidedDepth(next));
                if(nextDepth>foreground+0.12)sampleUv=next;
                // Edge-gap compensation from nearby known background.
                // This is not generative inpainting of hidden content.
                sampleUv=clamp(sampleUv,vec2(0.001),vec2(0.999));
                vec3 result=texture(photograph,sampleUv).rgb;
                if(gapFillStrength>0.0 && useQuickDepth==1 &&
                   abs(eyeSign*depthStrength)>0.05){
                    float stepX=1.0/float(textureSize(photograph,0).x);
                    float depthStep=2.0/float(textureSize(quickDepth,0).x);
                    float centerD=mapDepth(sampleUv);
                    float leftD=mapDepth(sampleUv-vec2(depthStep,0.0));
                    float rightD=mapDepth(sampleUv+vec2(depthStep,0.0));
                    float edge=max(abs(centerD-leftD),abs(centerD-rightD));
                    if(edge>0.075){
                        float best=1.0;
                        vec2 bestUv=sampleUv;
                        float reach=max(stepX*2.0,
                            min(0.032,abs(depthStrength)*0.015));
                        for(int k=1;k<=6;k++){
                            float offset=reach*(float(k)/6.0);
                            for(int side=0;side<2;side++){
                                float signX=side==0?-1.0:1.0;
                                vec2 candidate=clamp(sampleUv+
                                    vec2(signX*offset,0.0),
                                    vec2(0.001),vec2(0.999));
                                float d=mapDepth(candidate);
                                float backgroundGain=centerD-d;
                                if(backgroundGain>0.09){
                                    float score=offset/reach-3.0*backgroundGain;
                                    if(score<best){
                                        best=score;
                                        bestUv=candidate;
                                    }
                                }
                            }
                        }
                        if(best<1.0){
                            float amount=gapFillStrength*
                                smoothstep(0.075,0.22,edge)*0.8;
                            result=mix(result,texture(photograph,bestUv).rgb,
                                       amount);
                        }
                    }
                }
                frag=vec4(result,1.0);
                if(hasSelection==1 && texture(selectionMask,sampleUv).r>0.5)
                    frag.rgb=mix(frag.rgb,vec3(0.35,0.25,0.85),0.32);
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
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D,selectionTexture);
        glActiveTexture(GL_TEXTURE0);
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
        clearMask();selectionMode=false;
        maskUndo.clear();maskRedo.clear();maskStrokeBefore.clear();
        maskStrokeActive=false;
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
        photoLuminance.resize(size_t(DEPTH_EDGE)*DEPTH_EDGE);
        photoRgb.resize(size_t(DEPTH_EDGE)*DEPTH_EDGE*3u);
        for(int y=0;y<DEPTH_EDGE;++y)for(int x=0;x<DEPTH_EDGE;++x) {
            const int sx=std::min(width-1,x*width/DEPTH_EDGE);
            const int sy=std::min(height-1,y*height/DEPTH_EDGE);
            const size_t offset=(size_t(sy)*width+sx)*4;
            const size_t index=size_t(y)*DEPTH_EDGE+size_t(x);
            photoLuminance[index]=(0.2126f*rgba[offset]+
                0.7152f*rgba[offset+1]+0.0722f*rgba[offset+2])/255.f;
            photoRgb[index*3]=rgba[offset];
            photoRgb[index*3+1]=rgba[offset+1];
            photoRgb[index*3+2]=rgba[offset+2];
        }
        resetPhotoView();
    }
    void drawPhoto(float eyeSign,bool immersive=false){
        if(!photoTexture || !photoProgram)return;
        // Fit without cropping or stretching, preserving photo aspect ratio.
        float w=immersive?1024.f:684.f,h=immersive?768.f:208.f;
        const float ratio=float(photoWidth)/float(photoHeight);
        if(ratio > w/h) h=w/ratio; else w=h*ratio;
        const float x=512.f-w/2.f,y=(immersive?384.f:457.f)-h/2.f;
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
        glUniform1i(glGetUniformLocation(photoProgram,"selectionMask"),2);
        glUniform1i(glGetUniformLocation(photoProgram,"hasSelection"),hasSelection()?1:0);
        glUniform1i(glGetUniformLocation(photoProgram,"useQuickDepth"),hasQuickDepth()?1:0);
        glUniform1f(glGetUniformLocation(photoProgram,"eyeSign"),eyeSign);
        glUniform1f(glGetUniformLocation(photoProgram,"depthStrength"),depthStrength());
        glUniform1f(glGetUniformLocation(photoProgram,"nearBoost"),nearBoost);
        glUniform1f(glGetUniformLocation(photoProgram,"farPush"),farPush);
        glUniform1f(glGetUniformLocation(photoProgram,"depthContrast"),depthContrast);
        glUniform1f(glGetUniformLocation(photoProgram,"depthMidpoint"),depthMidpoint);
        glUniform1f(glGetUniformLocation(photoProgram,"gapFillStrength"),gapFillStrength());
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D,photoTexture);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D,depthTexture);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D,selectionTexture);
        glActiveTexture(GL_TEXTURE0);
        glBindVertexArray(photoVao);
        glBindBuffer(GL_ARRAY_BUFFER,photoVbo);
        glBufferData(GL_ARRAY_BUFFER,sizeof(vertices),vertices,GL_DYNAMIC_DRAW);
        glDrawArrays(GL_TRIANGLES,0,6);
        glBindVertexArray(0);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D,0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D,0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D,0);
    }
    void draw(Page page,int hovered,float cursorX,float cursorY,bool pointer,float eyeSign=0.f,bool showPhoto=true){
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
                page==Page::Projects?"MY PROJECTS":
                page==Page::AiDepth?"AI DEPTH":
                page==Page::Mask?"REFINE SELECTION":
                page==Page::Collage?"COLLAGE LAYERS":
                page==Page::Curves?"DEPTH CURVES":"VIEW GALLERY";
            int n=static_cast<int>(std::strlen(title));
            if(page!=Page::Open && page!=Page::Mask)
                label(v,title,512-n*15,page==Page::Projects?630:475,5,white);
            if(page==Page::Open) {
                box(v,170,665,215,50,hovered==14?purpleHover:button);
                label(v,"SAVE PROJECT",184,678,2,white);
                box(v,395,665,215,50,hovered==15?purpleHover:button);
                label(v,"SAVE AS NEW",409,678,2,white);
                box(v,645,665,101,50,hovered==12&&canUndo()?purpleHover:button);
                label(v,"UNDO",658,678,2,canUndo()?white:muted);
                box(v,754,665,100,50,hovered==13&&canRedo()?purpleHover:button);
                label(v,"REDO",765,678,2,canRedo()?white:muted);
                box(v,170,730,336,36,hovered==70?purpleHover:purple);
                label(v,"COLLAGE LAYERS",221,738,2,white);
                box(v,518,730,336,36,hovered==90?purpleHover:purple);
                label(v,"DEPTH CURVES",596,738,2,white);
                box(v,170,590,210,58,hovered==60?purpleHover:(selectionMode?purple:button));
                label(v,"REFINE MASK",184,610,2,white);
                box(v,390,590,210,58,hovered==61?purpleHover:button);
                label(v,"CLEAR MASK",404,610,2,hasSelection()?white:muted);
                box(v,610,590,244,58,hovered==62?purpleHover:button);
                label(v,"INVERT MASK",624,610,2,hasSelection()?white:muted);
                const char* state=!hasPhoto()?"IMPORT AN IMAGE":
                    quickConverting?"CREATING DEPTH...":
                    !hasDepthPixels()?"QUICK DEPTH REQUIRED":
                    brushMode?"BRUSH READY":"DEPTH READY";
                const char* feedback=brushFeedback();
                label(v,feedback?feedback:state,176,567,2,
                      feedback?Color{1.f,.73f,.22f}:quickConverting?purpleHover:muted);
                if(brushMode && hasDepthPixels()){
                    const std::string sizes="L "+std::to_string(int(leftRadius))+
                        "  R "+std::to_string(int(rightRadius));
                    label(v,sizes.c_str(),700,567,2,muted);
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
                label(v,"AI DEPTH",458,51,2,white);
                box(v,645,36,209,52,hovered==5?purpleHover:button);
                label(v,"RESET VIEW",665,51,2,white);
                if(hasPhoto()) {
                    label(v,"INDEX TRIGGERS SCULPT",175,187,2,muted);
                }
            } else if(page==Page::Mask){
                // Photo occupies y=353..561. All controls stay outside it.
                label(v,hasSelection()?"SELECTION ACTIVE":"SELECT AN OBJECT",176,565,2,muted);
                const std::string tolerance="TOLERANCE "+std::to_string(maskTolerance);
                label(v,tolerance.c_str(),610,565,2,muted);
                box(v,170,720,212,40,hovered==117?purpleHover:button);
                label(v,"MASK UNDO",184,732,2,canUndoMask()?white:muted);
                box(v,399,720,210,40,hovered==118?purpleHover:button);
                label(v,"MASK REDO",413,732,2,canRedoMask()?white:muted);
                box(v,625,720,229,40,hovered==119?purpleHover:
                    maskToolMode!=0?purple:button);
                label(v,maskToolMode==0?"DIRECT PAINT":maskToolMode==1?"SMART FILL":"ISOLATE TAP",636,732,2,white);
                box(v,170,110,330,70,hovered==120?purpleHover:button);
                label(v,"SMALLER BRUSH",190,133,2,white);
                box(v,524,110,330,70,hovered==121?purpleHover:button);
                label(v,"LARGER BRUSH",544,133,2,white);
                const std::string brushInfo="MASK BRUSH "+std::to_string(int(maskBrushRadius))+" PX";
                label(v,brushInfo.c_str(),176,189,2,muted);
                box(v,170,665,330,50,hovered==100?purpleHover:
                    !maskSubtract?purple:button);
                label(v,maskToolMode==0?"PAINT ADD":maskToolMode==1?"SMART ADD":"ISOLATE KEEP",191,680,2,white);
                box(v,524,665,330,50,hovered==101?purpleHover:
                    maskSubtract?purple:button);
                label(v,maskToolMode==0?"PAINT ERASE":maskToolMode==1?"SMART REMOVE":"ISOLATE KEEP",543,680,2,white);
                const char* operations[]={"LESS COLOR","MORE COLOR","EXPAND","CONTRACT",
                    "SMOOTH","INVERT","CLEAR","SELECT ALL"};
                for(int i=0;i<8;i++){
                    const int x=170+(i%4)*172;
                    const int y=i<4?590:205;
                    box(v,float(x),float(y),164,i<4?58:86,
                        hovered==102+i?purpleHover:button);
                    label(v,operations[i],x+8,y+(i<4?20:35),2,white);
                }
                box(v,170,490,684,60,hovered==116?purpleHover:button);
                const char* aiText=aiSegmentState==1?"DOWNLOADING AI MASK MODEL":
                    aiSegmentState==2?"AI SELECTING SUBJECT":
                    aiSegmentState==3?"AI MASK READY - TAP TO RERUN":
                    "AI SELECT SUBJECT";
                label(v,aiText,184,512,2,white);
                if(aiSegmentState==1||aiSegmentState==2){
                    const std::string progress="AI "+std::to_string(aiSegmentPercent)+"%";
                    label(v,progress.c_str(),685,512,2,muted);
                }
                if(aiSegmentState==4)
                    label(v,"AI SELECTION FAILED - RETRY",180,554,2,muted);
                box(v,170,402,684,78,hovered==115?purpleHover:purple);
                label(v,"AUTO FOREGROUND FROM DEPTH",190,428,2,white);
                if(autoMaskStatus==1)
                    label(v,"FOREGROUND MASK READY",183,385,2,muted);
                else if(autoMaskStatus==2)
                    label(v,"NO DISTINCT FOREGROUND FOUND",183,385,2,muted);
                box(v,170,310,330,50,hovered==112?purpleHover:
                    depthGuidedSelection?purple:button);
                label(v,depthGuidedSelection?"DEPTH GUIDE ON":"DEPTH GUIDE OFF",
                      186,326,2,white);
                box(v,524,310,153,50,hovered==113?purpleHover:button);
                label(v,"DEPTH -",538,326,2,white);
                box(v,689,310,165,50,hovered==114?purpleHover:button);
                label(v,"DEPTH +",704,326,2,white);
                const std::string depthInfo="DEPTH TOLERANCE "+std::to_string(maskDepthTolerance);
                label(v,depthInfo.c_str(),176,373,2,muted);
                if(depthGuidedSelection&&!hasDepthPixels())
                    label(v,"CREATE OR APPLY DEPTH FIRST",176,391,2,muted);
                box(v,170,36,684,52,hovered==110?purpleHover:purple);
                label(v,"DONE - RETURN TO EDITOR",270,53,2,white);
            } else if(page==Page::Curves){
                box(v,170,36,684,69,hovered==91?purpleHover:purple);
                label(v,"BACK TO EDITOR",303,57,3,white);
                const char* names[]={"NEAR BOOST","FAR PUSH","CONTRAST","MIDPOINT"};
                const float vals[]={nearBoost,farPush,depthContrast,depthMidpoint};
                const int ys[]={535,425,315,205};
                for(int i=0;i<4;i++){
                    const int y=ys[i];
                    box(v,170,float(y),330,70,hovered==92+i*2?purpleHover:button);
                    box(v,524,float(y),330,70,hovered==93+i*2?purpleHover:button);
                    label(v,"-",224,y+23,3,white);
                    label(v,"+",577,y+23,3,white);
                    label(v,names[i],340,y+79,2,muted);
                    const std::string value=std::to_string(int(vals[i]*100.f))+" PCT";
                    label(v,value.c_str(),648,y+80,2,white);
                }
                box(v,170,116,684,70,hovered==111?purpleHover:button);
                const char* fill=gapFillMode==0?"GAP FILL OFF":
                    gapFillMode==1?"GAP FILL LOW":"GAP FILL HIGH";
                label(v,fill,288,137,3,white);
                label(v,"PREVIEW ONLY - NOT AI INPAINTING",190,120,1,muted);
            } else if(page==Page::Collage){
                const std::string count="LAYER "+std::to_string(collageCount?collageActive+1:0)+
                    " OF "+std::to_string(collageCount);
                label(v,count.c_str(),182,549,3,white);
                const char* more[]={"ROTATE -","ROTATE +","SEND BACK","BRING FRONT"};
                for(int i=0;i<4;i++){
                    const int x=170+i*172;
                    box(v,float(x),720,164,42,hovered==83+i?purpleHover:button);
                    label(v,more[i],x+8,733,2,white);
                }
                const char* top[]={"PREV LAYER","NEXT LAYER"};
                for(int i=0;i<2;i++){
                    int x=170+i*344;
                    box(v,float(x),665,337,50,hovered==71+i?purpleHover:button);
                    label(v,top[i],x+25,680,3,white);
                }
                const char* moves[]={"MOVE LEFT","MOVE RIGHT","MOVE UP","MOVE DOWN"};
                const char* sizes[]={"SHRINK","GROW","PUSH BACK","PULL FRONT"};
                for(int i=0;i<4;i++){
                    int x=170+i*172;
                    box(v,float(x),590,164,58,hovered==73+i?purpleHover:button);
                    label(v,moves[i],x+8,610,2,white);
                    box(v,float(x),205,164,86,hovered==77+i?purpleHover:button);
                    label(v,sizes[i],x+8,237,2,white);
                }
                box(v,170,36,337,52,hovered==81?purpleHover:purple);
                label(v,"ADD IMAGE",212,52,3,white);
                box(v,517,36,337,52,hovered==82?purpleHover:button);
                label(v,"BACK TO EDITOR",535,52,2,white);
                label(v,"SELECT LAYER THEN POSITION SIZE DEPTH",182,320,2,muted);
            } else if(page==Page::AiDepth) {
                const char* status=aiState==1?"DOWNLOADING AI MODEL":
                    aiState==2?"AI CONVERSION RUNNING":
                    aiState==3?"AI PREVIEW READY":
                    aiState==4?"AI CONVERSION FAILED":
                    aiState==5?"CONVERSION CANCELLED":"READY FOR AI CONVERSION";
                label(v,status,184,591,3,white);
                const std::string progress="PROGRESS "+std::to_string(aiPercent)+"%";
                label(v,progress.c_str(),184,551,2,muted);
                box(v,184,527,657,10,{0.25f,0.20f,0.32f});
                box(v,184,527,657.f*float(aiPercent)/100.f,10,purpleHover);
                label(v,aiPreview?"PREVIEW ONLY - ORIGINAL DEPTH IS SAFE":
                       "NO AI PREVIEW APPLIED",184,305,2,muted);
                box(v,170,205,219,86,hovered==50?purpleHover:button);
                label(v,aiState==1||aiState==2?"WORKING":"START AI",183,234,2,white);
                box(v,400,205,217,86,hovered==51?purpleHover:button);
                label(v,"APPLY",460,234,2,aiPreview?white:muted);
                box(v,629,205,225,86,hovered==52?purpleHover:button);
                label(v,aiState==1||aiState==2?"CANCEL":"DISCARD",646,234,2,white);
            } else if(page==Page::Projects) {
                const int ys[]={495,405,315,225};
                for(int i=0;i<4;i++){
                    int index=projectPage*4+i;
                    if(index>=int(projectNames.size()))break;
                    box(v,170,float(ys[i]),684,64,hovered==40+i?purpleHover:button);
                    std::string shown=projectNames[index];
                    for(char& ch:shown) if(ch>='a'&&ch<='z')ch=char(ch-32);
                    if(shown.size()>22)shown.resize(22);
                    label(v,shown.c_str(),196,ys[i]+18,3,white);
                }
                if(projectNames.empty())label(v,"NO SAVED PROJECTS",240,400,3,muted);
                box(v,170,580,329,50,hovered==46?purpleHover:button);
                label(v,"PREVIOUS",243,592,2,white);
                box(v,515,580,339,50,hovered==47?purpleHover:button);
                label(v,"NEXT PAGE",573,592,2,white);
                const std::string pageText="PAGE "+std::to_string(projectPage+1)+
                    " / "+std::to_string(std::max(1,int((projectNames.size()+3)/4)));
                label(v,pageText.c_str(),360,558,2,muted);
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
        if(showPhoto && (page==Page::Open||page==Page::AiDepth||page==Page::Collage||page==Page::Mask) && hasPhoto()) drawPhoto(eyeSign);
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
        box(v,170,298,684,53,{0.095f,0.075f,0.14f});
        const float dl=180.f,dr=495.f,bl=510.f,br=845.f,y=307.f;
        label(v,"DEPTH",184,328,2,white);
        label(v,"RIGHT POWER",514,328,2,white);
        box(v,dl,y,dr-dl,7,{0.25f,0.20f,0.32f});
        box(v,dl,y,(dr-dl)*disparityStrength/2.f,7,purpleHover);
        box(v,dl+(dr-dl)*disparityStrength/2.f-5,y-6,10,19,white);
        box(v,bl,y,br-bl,7,{0.25f,0.20f,0.32f});
        box(v,bl,y,(br-bl)*(rightStrength-0.1f)/2.9f,7,purpleHover);
        box(v,bl+(br-bl)*(rightStrength-0.1f)/2.9f-5,y-6,10,19,white);
        submitUi(v);
    }
    // Tool controls are rendered into their own compositor quad.
    // This handle is outside every page's existing hitboxes.
    void drawFloatingHandle(bool hovered,float x,float y,bool pointer) {
        std::vector<float> v;
        box(v,16,710,145,51,hovered?purpleHover:purple);
        label(v,"MOVE",39,729,2,white);
        if(pointer){
            box(v,x-12,y-3,24,6,white);
            box(v,x-3,y-12,6,24,white);
        }
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
        // All controls are explicit: depth plus two separate size, power and
        // hardness controls. Every brush parameter is labeled on the panel.
        const float full=disparityStrength/2.f;
        label(v,"STEREO DEPTH",45,379,3,white);
        box(v,45,348,930,12,{0.26f,0.20f,0.32f});
        box(v,45,348,930*full,12,purpleHover);
        box(v,45+930*full-6,339,12,30,white);
        const float leftValues[]={ (leftStrength-.1f)/2.9f,
                                   (leftRadius-12.f)/228.f,leftHardness };
        const float rightValues[]={ (rightStrength-.1f)/2.9f,
                                    (rightRadius-12.f)/228.f,rightHardness };
        const char* namesL[]={"LEFT PUSH POWER","LEFT BRUSH SIZE","LEFT HARDNESS"};
        const char* namesR[]={"RIGHT PULL POWER","RIGHT BRUSH SIZE","RIGHT HARDNESS"};
        const int ys[]={265,180,95};
        for(int i=0;i<3;i++){
            const float y=float(ys[i]);
            label(v,namesL[i],45,int(y+30),2,white);
            label(v,namesR[i],540,int(y+30),2,white);
            const float vals[]={leftValues[i],rightValues[i]};
            const float xs[]={45.f,540.f};
            for(int j=0;j<2;j++){
                float x=xs[j];
                box(v,x,y,435,12,{0.26f,0.20f,0.32f});
                box(v,x,y,435*vals[j],12,purpleHover);
                box(v,x+435*vals[j]-6,y-9,12,30,white);
            }
        }
        const std::string sizes="L "+std::to_string(int(leftRadius))+
            " PX     R "+std::to_string(int(rightRadius))+" PX";
        label(v,sizes.c_str(),350,151,2,muted);
        if(pointer){
            box(v,pointerX-16,pointerY-3,32,6,white);
            box(v,pointerX-3,pointerY-16,6,32,white);
        }
        submitUi(v);
    }
    void drawAiOverlay(){
        std::vector<float> v;
        box(v,170,524,684,84,{0.075f,0.055f,0.13f});
        const char* status=aiState==1?"DOWNLOADING AI MODEL":
            aiState==2?"PROCESSING ON DEVICE":
            aiState==3?"AI PREVIEW READY":
            aiState==4?"AI CONVERSION FAILED":
            aiState==5?"CONVERSION CANCELLED":"READY FOR AI CONVERSION";
        label(v,status,192,569,3,white);
        const std::string progress=std::to_string(aiPercent)+"%";
        label(v,progress.c_str(),755,571,2,muted);
        box(v,192,540,636,8,{0.25f,0.20f,0.32f});
        box(v,192,540,636.f*float(aiPercent)/100.f,8,purpleHover);
        submitUi(v);
    }
    void drawSelectionCursor(float x,float y,bool valid,bool held){
        if(!valid||!hasPhoto())return;
        float u=0.f,vCoord=0.f;
        if(!hitPhoto(x,y,true,u,vCoord))return;
        const float aspect=float(photoWidth)/photoHeight;
        float pw=1024.f,ph=768.f;
        if(aspect>pw/ph)ph=pw/aspect;else pw=ph*aspect;
        const float radius=maskToolMode==0?
            std::clamp(maskBrushRadius*std::min(pw,ph)*zoom/MASK_EDGE,3.f,240.f):12.f;
        const Color ink=maskSubtract?Color{1.f,.42f,.3f}:
                         Color{.35f,1.f,.49f};
        const Color halo{.015f,.012f,.025f};
        std::vector<float> geometry;
        constexpr int segments=48;
        for(int pass=0;pass<2;++pass){
            const float thickness=pass==0?6.f:3.f;
            const Color color=pass==0?halo:ink;
            for(int i=0;i<segments;++i){
                const float a=6.283185307f*float(i)/segments;
                const float b=6.283185307f*float(i+1)/segments;
                const float ax=x+std::cos(a)*radius,ay=y+std::sin(a)*radius;
                const float bx=x+std::cos(b)*radius,by=y+std::sin(b)*radius;
                const float dx=bx-ax,dy=by-ay,len=std::hypot(dx,dy);
                if(len<1.e-5f)continue;
                const float nx=-dy/len*thickness*.5f,ny=dx/len*thickness*.5f;
                const float vertices[6][2]={{ax+nx,ay+ny},{bx+nx,by+ny},
                    {ax-nx,ay-ny},{bx+nx,by+ny},{bx-nx,by-ny},{ax-nx,ay-ny}};
                for(const auto& pt:vertices){
                    geometry.push_back(pt[0]);geometry.push_back(pt[1]);
                    geometry.push_back(color.r);geometry.push_back(color.g);
                    geometry.push_back(color.b);
                }
            }
        }
        box(geometry,x-8,y-2,16,4,ink);
        if(!maskSubtract)box(geometry,x-2,y-8,4,16,ink);
        submitUi(geometry);
    }
    // Cursor outlines are always legible in Quest: strong dark halo, vivid
    // hand-specific stroke, and a visibly different color while sculpting.
    void drawHandCursor(float x,float y,bool valid,bool left,bool active,
                        bool immersive,bool held=false) {
        if(!valid)return;
        std::vector<float> v;
        const Color idle=left?Color{0.26f,0.77f,1.f}:Color{1.f,0.42f,0.73f};
        // Green means sculpting, amber means trigger held but sculpt blocked.
        const Color ink=active?Color{0.34f,1.f,0.31f}:
                        held?Color{1.f,0.75f,0.18f}:idle;
        const Color outline{0.015f,0.012f,0.025f};
        if(brushMode && hasPhoto()){
            float radius=26.f;
            float u=0.f,w=0.f;
            if(hitPhoto(x,y,immersive,u,w)){
                float pw=immersive?1024.f:684.f,ph=immersive?768.f:208.f;
                const float aspect=float(photoWidth)/float(photoHeight);
                if(aspect>pw/ph)ph=pw/aspect;else pw=ph*aspect;
                // The brush operates on the 1024px depth grid; preserve
                // physical guide size as resolution changes.
                const float brushPixels=left?leftRadius:rightRadius;
                radius=std::max(14.f,brushPixels*std::min(pw,ph)*zoom/float(DEPTH_EDGE));
            }
            radius=std::min(radius,300.f);
            constexpr int segments=64;
            auto outlineRing=[&](float thick,Color color){
                for(int i=0;i<segments;i++){
                    const float a=float(i)*6.283185307f/segments;
                    const float b=float(i+1)*6.283185307f/segments;
                    const float ax=x+std::cos(a)*radius,ay=y+std::sin(a)*radius;
                    const float bx=x+std::cos(b)*radius,by=y+std::sin(b)*radius;
                    const float dx=bx-ax,dy=by-ay,length=std::hypot(dx,dy);
                    if(length<0.0001f)continue;
                    const float nx=-dy/length*thick*.5f;
                    const float ny=dx/length*thick*.5f;
                    const float points[6][2]={
                        {ax+nx,ay+ny},{bx+nx,by+ny},{ax-nx,ay-ny},
                        {bx+nx,by+ny},{bx-nx,by-ny},{ax-nx,ay-ny}
                    };
                    for(const auto& p:points){
                        v.push_back(p[0]);v.push_back(p[1]);
                        v.push_back(color.r);v.push_back(color.g);v.push_back(color.b);
                    }
                }
            };
            outlineRing(8.f,outline);
            outlineRing(4.5f,ink);
            // Center symbols remain distinct: left pushes (-), right pulls (+).
            box(v,x-15,y-5,30,10,outline);
            box(v,x-12,y-3,24,6,ink);
            if(!left){
                box(v,x-5,y-15,10,30,outline);
                box(v,x-3,y-12,6,24,ink);
            }
        }else{
            box(v,x-12,y-4,24,8,outline);
            box(v,x-10,y-2,20,4,ink);
            if(!left){
                box(v,x-4,y-12,8,24,outline);
                box(v,x-2,y-10,4,20,ink);
            }
        }
        submitUi(v);
    }
    ~Renderer(){clearMask();clearQuickDepth();
        if(photoTexture)glDeleteTextures(1,&photoTexture);
        if(photoProgram)glDeleteProgram(photoProgram);
        if(photoVbo)glDeleteBuffers(1,&photoVbo);
        if(photoVao)glDeleteVertexArrays(1,&photoVao);
        if(vbo)glDeleteBuffers(1,&vbo);if(vao)glDeleteVertexArrays(1,&vao);
        if(program)glDeleteProgram(program);}
};
} // namespace vrui
