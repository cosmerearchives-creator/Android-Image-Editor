#include <jni.h>
#include <atomic>
#include <chrono>
#include <thread>
#include <vector>
#include <cmath>
#include "vr_ui.h"
#include <cstring>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>

namespace {
std::atomic<bool> stopRequested{false};
std::atomic<bool> imageNeedsReload{true};
std::atomic<bool> depthNeedsReload{true};
void callQuickConvert(JNIEnv* env,jobject activity) {
    jclass cls=env->GetObjectClass(activity);
    jmethodID method=env->GetMethodID(cls,"requestQuickConvert","()V");
    if(method)env->CallVoidMethod(activity,method);
    if(env->ExceptionCheck()){env->ExceptionDescribe();env->ExceptionClear();}
    env->DeleteLocalRef(cls);
}
void refreshDepth(JNIEnv* env,jobject activity,vrui::Renderer& menu) {
    menu.clearQuickDepth();
    menu.quickConverting=false;
    jclass activityClass=env->GetObjectClass(activity);
    jmethodID contextMethod=env->GetMethodID(activityClass,"getApplicationContext",
                                              "()Landroid/content/Context;");
    jobject ctx=env->CallObjectMethod(activity,contextMethod);
    jclass cls=env->FindClass("com/cosmerearchives/androidimageeditor/QuickDepth");
    if(cls&&ctx) {
        jmethodID method=env->GetStaticMethodID(cls,"loadPixels","(Landroid/content/Context;)[I");
        if(method) {
            jintArray pixels=static_cast<jintArray>(env->CallStaticObjectMethod(cls,method,ctx));
            if(pixels) {
                const jsize count=env->GetArrayLength(pixels);
                if(count==256*256+2){
                    jint sizes[2]{0,0};
                    env->GetIntArrayRegion(pixels,0,2,sizes);
                    if(sizes[0]==256 && sizes[1]==256) {
                        std::vector<jint> argb(256*256);
                        env->GetIntArrayRegion(pixels,2,256*256,argb.data());
                        std::vector<uint8_t> gray(256*256);
                        for(size_t i=0;i<gray.size();++i)
                            gray[i]=static_cast<uint8_t>(argb[i] & 0xFF);
                        menu.uploadQuickDepth(gray,256,256);
                    }
                }
                env->DeleteLocalRef(pixels);
            }
        }
    }
    if(env->ExceptionCheck()){env->ExceptionDescribe();env->ExceptionClear();}
    if(cls)env->DeleteLocalRef(cls);
    if(ctx)env->DeleteLocalRef(ctx);
    env->DeleteLocalRef(activityClass);
}
std::atomic<bool> resumeImagePage{false};
void callPicker(JNIEnv* env,jobject activity) {
    jclass activityClass=env->GetObjectClass(activity);
    jmethodID request=env->GetMethodID(activityClass,"requestImageImport","()V");
    if(request) env->CallVoidMethod(activity,request);
    if(env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    env->DeleteLocalRef(activityClass);
}
void refreshImage(JNIEnv* env,jobject activity,vrui::Renderer& menu) {
    jclass activityClass=env->GetObjectClass(activity);
    jmethodID appContext=env->GetMethodID(activityClass,"getApplicationContext",
                                           "()Landroid/content/Context;");
    jobject context=env->CallObjectMethod(activity,appContext);
    jclass storage=env->FindClass("com/cosmerearchives/androidimageeditor/ImageStorage");
    if(!storage || !context) {
        if(env->ExceptionCheck()){env->ExceptionDescribe();env->ExceptionClear();}
        env->DeleteLocalRef(activityClass);
        if(context)env->DeleteLocalRef(context);
        return;
    }
    jmethodID pixels=env->GetStaticMethodID(storage,"loadPixels","(Landroid/content/Context;)[I");
    jintArray data=static_cast<jintArray>(env->CallStaticObjectMethod(storage,pixels,context));
    if(env->ExceptionCheck()) {env->ExceptionDescribe();env->ExceptionClear();}
    if(data) {
        jsize count=env->GetArrayLength(data);
        if(count>=3){
            jint dimensions[2]{};
            env->GetIntArrayRegion(data,0,2,dimensions);
            int width=dimensions[0],height=dimensions[1];
            if(width>0&&height>0&&width<=2048&&height<=2048&&
               static_cast<int64_t>(width)*height+2==count) {
                std::vector<jint> argb(static_cast<size_t>(width)*height);
                env->GetIntArrayRegion(data,2,width*height,argb.data());
                std::vector<uint8_t> rgba(argb.size()*4);
                for(size_t i=0;i<argb.size();i++){
                    uint32_t p=static_cast<uint32_t>(argb[i]);
                    rgba[i*4]=static_cast<uint8_t>((p>>16)&255);
                    rgba[i*4+1]=static_cast<uint8_t>((p>>8)&255);
                    rgba[i*4+2]=static_cast<uint8_t>(p&255);
                    rgba[i*4+3]=255;
                }
                menu.uploadPhoto(rgba,width,height);
            }
        }
        env->DeleteLocalRef(data);
    }
    env->DeleteLocalRef(storage);
    env->DeleteLocalRef(context);
    env->DeleteLocalRef(activityClass);
}

constexpr const char* LOG_TAG = "ImageEditorXR";
void log(const char* message) { __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "%s", message); }
void check(XrResult result, const char* step) {
    if (XR_FAILED(result)) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s failed: %d", step, result);
        throw step;
    }
}
struct XrResources {
    XrInstance instance{XR_NULL_HANDLE};
    XrSession session{XR_NULL_HANDLE};
    XrSpace space{XR_NULL_HANDLE};
    XrSwapchain swapchain{XR_NULL_HANDLE};
    XrActionSet actionSet{XR_NULL_HANDLE};
    XrAction triggerAction{XR_NULL_HANDLE};
    XrAction aimAction{XR_NULL_HANDLE};
    XrAction stickAction{XR_NULL_HANDLE};
    XrAction gripAction{XR_NULL_HANDLE};
    XrAction leftGripAction{XR_NULL_HANDLE};
    XrAction leftAimAction{XR_NULL_HANDLE};
    XrAction compareAction{XR_NULL_HANDLE};
    XrAction exitViewAction{XR_NULL_HANDLE};
    XrSpace leftAimSpace{XR_NULL_HANDLE};
    XrSpace aimSpace{XR_NULL_HANDLE};
    EGLDisplay display{EGL_NO_DISPLAY};
    EGLContext context{EGL_NO_CONTEXT};
    EGLSurface surface{EGL_NO_SURFACE};
    GLuint fbo{0};
    ~XrResources() {
        if (fbo) glDeleteFramebuffers(1, &fbo);
        if (aimSpace != XR_NULL_HANDLE) xrDestroySpace(aimSpace);
        if (leftAimSpace != XR_NULL_HANDLE) xrDestroySpace(leftAimSpace);
        if (swapchain != XR_NULL_HANDLE) xrDestroySwapchain(swapchain);
        if (space != XR_NULL_HANDLE) xrDestroySpace(space);
        if (actionSet != XR_NULL_HANDLE) xrDestroyActionSet(actionSet);
        if (session != XR_NULL_HANDLE) xrDestroySession(session);
        if (instance != XR_NULL_HANDLE) xrDestroyInstance(instance);
        if (display != EGL_NO_DISPLAY) {
            eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            if (surface != EGL_NO_SURFACE) eglDestroySurface(display, surface);
            if (context != EGL_NO_CONTEXT) eglDestroyContext(display, context);
            eglTerminate(display);
        }
    }
};
XrPath path(XrInstance instance, const char* name) {
    XrPath result{};
    check(xrStringToPath(instance, name, &result), "xrStringToPath");
    return result;
}
void run(JNIEnv* env, jobject activity) {
    XrResources r;
    JavaVM* vm = nullptr;
    env->GetJavaVM(&vm);
    PFN_xrInitializeLoaderKHR initializeLoader = nullptr;
    check(xrGetInstanceProcAddr(XR_NULL_HANDLE, "xrInitializeLoaderKHR",
                                reinterpret_cast<PFN_xrVoidFunction*>(&initializeLoader)), "loader entry");
    XrLoaderInitInfoAndroidKHR loaderInfo{XR_TYPE_LOADER_INIT_INFO_ANDROID_KHR};
    loaderInfo.applicationVM = vm;
    loaderInfo.applicationContext = activity;
    check(initializeLoader(reinterpret_cast<XrLoaderInitInfoBaseHeaderKHR*>(&loaderInfo)), "initialize loader");

    const char* extensions[] = {
        XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME,
        XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME
    };
    XrInstanceCreateInfoAndroidKHR androidInfo{XR_TYPE_INSTANCE_CREATE_INFO_ANDROID_KHR};
    androidInfo.applicationVM = vm;
    androidInfo.applicationActivity = activity;
    XrInstanceCreateInfo info{XR_TYPE_INSTANCE_CREATE_INFO};
    info.next = &androidInfo;
    std::strncpy(info.applicationInfo.applicationName, "Android Image Editor", XR_MAX_APPLICATION_NAME_SIZE - 1);
    std::strncpy(info.applicationInfo.engineName, "Native Quest 2", XR_MAX_ENGINE_NAME_SIZE - 1);
    info.applicationInfo.apiVersion = XR_CURRENT_API_VERSION;
    info.enabledExtensionCount = 2;
    info.enabledExtensionNames = extensions;
    check(xrCreateInstance(&info, &r.instance), "xrCreateInstance");

    XrSystemGetInfo systemInfo{XR_TYPE_SYSTEM_GET_INFO};
    systemInfo.formFactor = XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY;
    XrSystemId system = XR_NULL_SYSTEM_ID;
    check(xrGetSystem(r.instance, &systemInfo, &system), "xrGetSystem");

    r.display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (r.display == EGL_NO_DISPLAY || !eglInitialize(r.display, nullptr, nullptr))
        throw "EGL initialize";
    const EGLint attribs[] = {EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
                             EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
                             EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8,
                             EGL_BLUE_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_NONE};
    EGLConfig config{};
    EGLint configCount = 0;
    if (!eglChooseConfig(r.display, attribs, &config, 1, &configCount) || !configCount)
        throw "EGL choose config";
    const EGLint contextAttribs[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    r.context = eglCreateContext(r.display, config, EGL_NO_CONTEXT, contextAttribs);
    const EGLint surfaceAttribs[] = {EGL_WIDTH, 16, EGL_HEIGHT, 16, EGL_NONE};
    r.surface = eglCreatePbufferSurface(r.display, config, surfaceAttribs);
    if (r.context == EGL_NO_CONTEXT || r.surface == EGL_NO_SURFACE ||
        !eglMakeCurrent(r.display, r.surface, r.surface, r.context))
        throw "EGL context";
    EGLint configId = 0;
    eglGetConfigAttrib(r.display, config, EGL_CONFIG_ID, &configId);
    PFN_xrGetOpenGLESGraphicsRequirementsKHR getGlesRequirements = nullptr;
    check(xrGetInstanceProcAddr(r.instance, "xrGetOpenGLESGraphicsRequirementsKHR",
          reinterpret_cast<PFN_xrVoidFunction*>(&getGlesRequirements)), "GLES requirements entry");
    XrGraphicsRequirementsOpenGLESKHR requirements{XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_ES_KHR};
    check(getGlesRequirements(r.instance, system, &requirements), "GLES requirements");
    XrGraphicsBindingOpenGLESAndroidKHR binding{XR_TYPE_GRAPHICS_BINDING_OPENGL_ES_ANDROID_KHR};
    binding.display = r.display;
    binding.config = config;
    binding.context = r.context;
    XrSessionCreateInfo sessionInfo{XR_TYPE_SESSION_CREATE_INFO};
    sessionInfo.next = &binding;
    sessionInfo.systemId = system;
    check(xrCreateSession(r.instance, &sessionInfo, &r.session), "xrCreateSession");

    XrReferenceSpaceCreateInfo spaceInfo{XR_TYPE_REFERENCE_SPACE_CREATE_INFO};
    spaceInfo.referenceSpaceType = XR_REFERENCE_SPACE_TYPE_LOCAL;
    spaceInfo.poseInReferenceSpace.orientation.w = 1.f;
    check(xrCreateReferenceSpace(r.session, &spaceInfo, &r.space), "xrCreateReferenceSpace");

    XrActionSetCreateInfo actionSetInfo{XR_TYPE_ACTION_SET_CREATE_INFO};
    std::strcpy(actionSetInfo.actionSetName, "gallery");
    std::strcpy(actionSetInfo.localizedActionSetName, "Gallery");
    check(xrCreateActionSet(r.instance, &actionSetInfo, &r.actionSet), "xrCreateActionSet");
    XrPath rightHand = path(r.instance, "/user/hand/right");
    XrPath leftHand = path(r.instance, "/user/hand/left");
    XrActionCreateInfo actionInfo{XR_TYPE_ACTION_CREATE_INFO};
    actionInfo.actionType = XR_ACTION_TYPE_FLOAT_INPUT;
    std::strcpy(actionInfo.actionName, "select");
    std::strcpy(actionInfo.localizedActionName, "Select");
    actionInfo.countSubactionPaths = 1;
    actionInfo.subactionPaths = &rightHand;
    check(xrCreateAction(r.actionSet, &actionInfo, &r.triggerAction), "xrCreateAction");
    XrActionCreateInfo aimInfo{XR_TYPE_ACTION_CREATE_INFO};
    aimInfo.actionType = XR_ACTION_TYPE_POSE_INPUT;
    std::strcpy(aimInfo.actionName, "aim");
    std::strcpy(aimInfo.localizedActionName, "Controller Aim");
    aimInfo.countSubactionPaths = 1;
    aimInfo.subactionPaths = &rightHand;
    check(xrCreateAction(r.actionSet, &aimInfo, &r.aimAction), "xrCreateAimAction");
    XrActionCreateInfo stickInfo{XR_TYPE_ACTION_CREATE_INFO};
    stickInfo.actionType=XR_ACTION_TYPE_VECTOR2F_INPUT;
    std::strcpy(stickInfo.actionName,"zoom_stick");
    std::strcpy(stickInfo.localizedActionName,"Zoom Stick");
    stickInfo.countSubactionPaths=1;
    stickInfo.subactionPaths=&rightHand;
    check(xrCreateAction(r.actionSet,&stickInfo,&r.stickAction),"xrCreateStickAction");
    XrActionCreateInfo gripInfo{XR_TYPE_ACTION_CREATE_INFO};
    gripInfo.actionType=XR_ACTION_TYPE_FLOAT_INPUT;
    std::strcpy(gripInfo.actionName,"move_grip");
    std::strcpy(gripInfo.localizedActionName,"Move Canvas");
    gripInfo.countSubactionPaths=1;
    gripInfo.subactionPaths=&rightHand;
    check(xrCreateAction(r.actionSet,&gripInfo,&r.gripAction),"xrCreateGripAction");
    XrActionCreateInfo leftAimInfo{XR_TYPE_ACTION_CREATE_INFO};
    leftAimInfo.actionType=XR_ACTION_TYPE_POSE_INPUT;
    std::strcpy(leftAimInfo.actionName,"left_aim");
    std::strcpy(leftAimInfo.localizedActionName,"Left Controller Aim");
    leftAimInfo.countSubactionPaths=1;
    leftAimInfo.subactionPaths=&leftHand;
    check(xrCreateAction(r.actionSet,&leftAimInfo,&r.leftAimAction),"xrCreateLeftAimAction");
    XrActionCreateInfo leftGripInfo{XR_TYPE_ACTION_CREATE_INFO};
    leftGripInfo.actionType=XR_ACTION_TYPE_FLOAT_INPUT;
    std::strcpy(leftGripInfo.actionName,"left_move_grip");
    std::strcpy(leftGripInfo.localizedActionName,"Left Move Canvas");
    leftGripInfo.countSubactionPaths=1;
    leftGripInfo.subactionPaths=&leftHand;
    check(xrCreateAction(r.actionSet,&leftGripInfo,&r.leftGripAction),"xrCreateLeftGripAction");
    XrActionCreateInfo compareInfo{XR_TYPE_ACTION_CREATE_INFO};
    compareInfo.actionType=XR_ACTION_TYPE_BOOLEAN_INPUT;
    std::strcpy(compareInfo.actionName,"compare_2d_3d");
    std::strcpy(compareInfo.localizedActionName,"Compare 2D and 3D");
    compareInfo.countSubactionPaths=1;
    compareInfo.subactionPaths=&rightHand;
    check(xrCreateAction(r.actionSet,&compareInfo,&r.compareAction),"xrCreateCompareAction");
    XrActionCreateInfo exitInfo{XR_TYPE_ACTION_CREATE_INFO};
    exitInfo.actionType=XR_ACTION_TYPE_BOOLEAN_INPUT;
    std::strcpy(exitInfo.actionName,"exit_immersive");
    std::strcpy(exitInfo.localizedActionName,"Return to Editor");
    exitInfo.countSubactionPaths=1;
    exitInfo.subactionPaths=&rightHand;
    check(xrCreateAction(r.actionSet,&exitInfo,&r.exitViewAction),"xrCreateExitViewAction");
    const XrActionSuggestedBinding suggested[] = {
        {r.triggerAction, path(r.instance, "/user/hand/right/input/trigger/value")},
        {r.aimAction, path(r.instance, "/user/hand/right/input/aim/pose")},
        {r.stickAction, path(r.instance, "/user/hand/right/input/thumbstick")},
        {r.gripAction, path(r.instance, "/user/hand/right/input/squeeze/value")},
        {r.leftAimAction, path(r.instance, "/user/hand/left/input/aim/pose")},
        {r.leftGripAction, path(r.instance, "/user/hand/left/input/squeeze/value")},
        {r.compareAction, path(r.instance, "/user/hand/right/input/a/click")},
        {r.exitViewAction, path(r.instance, "/user/hand/right/input/b/click")}
    };
    XrInteractionProfileSuggestedBinding profile{XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING};
    profile.interactionProfile = path(r.instance, "/interaction_profiles/oculus/touch_controller");
    profile.countSuggestedBindings = 8;
    profile.suggestedBindings = suggested;
    check(xrSuggestInteractionProfileBindings(r.instance, &profile), "xrSuggestInteractionProfileBindings");
    XrSessionActionSetsAttachInfo attach{XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO};
    attach.countActionSets = 1;
    attach.actionSets = &r.actionSet;
    check(xrAttachSessionActionSets(r.session, &attach), "xrAttachSessionActionSets");
    XrActionSpaceCreateInfo aimSpaceInfo{XR_TYPE_ACTION_SPACE_CREATE_INFO};
    aimSpaceInfo.action = r.aimAction;
    aimSpaceInfo.subactionPath = rightHand;
    aimSpaceInfo.poseInActionSpace.orientation.w = 1.f;
    check(xrCreateActionSpace(r.session, &aimSpaceInfo, &r.aimSpace), "xrCreateActionSpace");
    XrActionSpaceCreateInfo leftSpaceInfo{XR_TYPE_ACTION_SPACE_CREATE_INFO};
    leftSpaceInfo.action=r.leftAimAction;
    leftSpaceInfo.subactionPath=leftHand;
    leftSpaceInfo.poseInActionSpace.orientation.w=1.f;
    check(xrCreateActionSpace(r.session,&leftSpaceInfo,&r.leftAimSpace),"xrCreateLeftActionSpace");

    uint32_t formatsCount = 0;
    check(xrEnumerateSwapchainFormats(r.session, 0, &formatsCount, nullptr), "swapchain formats count");
    std::vector<int64_t> formats(formatsCount);
    check(xrEnumerateSwapchainFormats(r.session, formatsCount, &formatsCount, formats.data()), "swapchain formats");
    int64_t preferred = GL_RGBA8;
    bool available = false;
    for (auto f : formats) if (f == preferred) available = true;
    if (!available) throw "RGBA8 swapchain unavailable";

    // A compositor quad is fixed in local world space, so it stays put when the user turns.
    XrSwapchainCreateInfo swapInfo{XR_TYPE_SWAPCHAIN_CREATE_INFO};
    swapInfo.usageFlags = XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT;
    swapInfo.format = preferred;
    swapInfo.sampleCount = 1;
    // One double-width swapchain: each eye reads its own 1024x768 rectangle.
    swapInfo.width = 2048;
    swapInfo.height = 768;
    swapInfo.faceCount = 1;
    swapInfo.arraySize = 1;
    swapInfo.mipCount = 1;
    check(xrCreateSwapchain(r.session, &swapInfo, &r.swapchain), "xrCreateSwapchain");
    uint32_t imagesCount = 0;
    check(xrEnumerateSwapchainImages(r.swapchain, 0, &imagesCount, nullptr), "swapchain images count");
    std::vector<XrSwapchainImageOpenGLESKHR> images(imagesCount);
    for (auto& image : images) image.type = XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR;
    check(xrEnumerateSwapchainImages(r.swapchain, imagesCount, &imagesCount,
          reinterpret_cast<XrSwapchainImageBaseHeader*>(images.data())), "swapchain images");
    glGenFramebuffers(1, &r.fbo);
    vrui::Renderer menu;
    if (!menu.initialize()) throw "VR menu shader initialization";
    vrui::Page page = resumeImagePage.exchange(false) ? vrui::Page::Open : vrui::Page::Home;
    bool wasPressed = false;
    bool wasA=false,wasB=false;
    XrVector3f editorPosition{0.f,0.f,-1.9f};
    float editorScale=1.f,editorAngle=0.f,editorZoom=1.f,editorU=0.5f,editorV=0.5f;
    // One grip translates the canvas. Two grips translate, scale, and rotate around Z.
    enum class DragMode {None, Right, Left, Both};
    DragMode dragMode=DragMode::None;
    XrVector3f dragOrigin{0.f,0.f,0.f};
    XrVector3f dragPanel{0.f,0.f,-1.9f};
    XrVector3f panelPosition{0.f,0.f,-1.9f};
    float panelScale=1.f, panelAngle=0.f;
    float startScale=1.f,startAngle=0.f,startDistance=1.f,startHandAngle=0.f;
    XrVector3f startMidpoint{0.f,0.f,0.f};
    auto lastFrame=std::chrono::steady_clock::now();
    bool running = false;
    log("OpenXR compositor initialized");
    while (!stopRequested.load()) {
        XrEventDataBuffer event{XR_TYPE_EVENT_DATA_BUFFER};
        while (xrPollEvent(r.instance, &event) == XR_SUCCESS) {
            if (event.type == XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED) {
                const auto& changed = *reinterpret_cast<const XrEventDataSessionStateChanged*>(&event);
                if (changed.state == XR_SESSION_STATE_READY && !running) {
                    XrSessionBeginInfo begin{XR_TYPE_SESSION_BEGIN_INFO};
                    begin.primaryViewConfigurationType = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;
                    check(xrBeginSession(r.session, &begin), "xrBeginSession");
                    running = true;
                } else if (changed.state == XR_SESSION_STATE_STOPPING && running) {
                    xrEndSession(r.session);
                    running = false;
                } else if (changed.state == XR_SESSION_STATE_EXITING ||
                           changed.state == XR_SESSION_STATE_LOSS_PENDING) {
                    stopRequested.store(true);
                }
            }
            event = {XR_TYPE_EVENT_DATA_BUFFER};
        }
        if (!running) {
            std::this_thread::sleep_for(std::chrono::milliseconds(25));
            continue;
        }
        XrFrameWaitInfo waitInfo{XR_TYPE_FRAME_WAIT_INFO};
        XrFrameState frameState{XR_TYPE_FRAME_STATE};
        check(xrWaitFrame(r.session, &waitInfo, &frameState), "xrWaitFrame");
        XrFrameBeginInfo beginFrame{XR_TYPE_FRAME_BEGIN_INFO};
        check(xrBeginFrame(r.session, &beginFrame), "xrBeginFrame");
        XrCompositionLayerQuad quads[2]{
            {XR_TYPE_COMPOSITION_LAYER_QUAD},
            {XR_TYPE_COMPOSITION_LAYER_QUAD}
        };
        const XrCompositionLayerBaseHeader* layers[] = {
            reinterpret_cast<const XrCompositionLayerBaseHeader*>(&quads[0]),
            reinterpret_cast<const XrCompositionLayerBaseHeader*>(&quads[1])
        };
        uint32_t layerCount = 0;
        if (frameState.shouldRender) {
            XrActiveActionSet active{r.actionSet, XR_NULL_PATH};
            XrActionsSyncInfo sync{XR_TYPE_ACTIONS_SYNC_INFO};
            sync.countActiveActionSets = 1;
            sync.activeActionSets = &active;
            xrSyncActions(r.session, &sync);
            XrActionStateGetInfo get{XR_TYPE_ACTION_STATE_GET_INFO};
            get.action = r.triggerAction;
            get.subactionPath = rightHand;
            XrActionStateFloat pressed{XR_TYPE_ACTION_STATE_FLOAT};
            xrGetActionStateFloat(r.session, &get, &pressed);
            XrActionStateGetInfo compareGet{XR_TYPE_ACTION_STATE_GET_INFO};
            compareGet.action=r.compareAction;
            compareGet.subactionPath=rightHand;
            XrActionStateBoolean compare{XR_TYPE_ACTION_STATE_BOOLEAN};
            xrGetActionStateBoolean(r.session,&compareGet,&compare);
            XrActionStateGetInfo exitGet{XR_TYPE_ACTION_STATE_GET_INFO};
            exitGet.action=r.exitViewAction;
            exitGet.subactionPath=rightHand;
            XrActionStateBoolean exitView{XR_TYPE_ACTION_STATE_BOOLEAN};
            xrGetActionStateBoolean(r.session,&exitGet,&exitView);

            XrActionStateGetInfo stickGet{XR_TYPE_ACTION_STATE_GET_INFO};
            stickGet.action=r.stickAction;
            stickGet.subactionPath=rightHand;
            XrActionStateVector2f stick{XR_TYPE_ACTION_STATE_VECTOR2F};
            xrGetActionStateVector2f(r.session,&stickGet,&stick);
            XrActionStateGetInfo gripGet{XR_TYPE_ACTION_STATE_GET_INFO};
            gripGet.action=r.gripAction;
            gripGet.subactionPath=rightHand;
            XrActionStateFloat grip{XR_TYPE_ACTION_STATE_FLOAT};
            xrGetActionStateFloat(r.session,&gripGet,&grip);
            XrActionStateGetInfo leftGripGet{XR_TYPE_ACTION_STATE_GET_INFO};
            leftGripGet.action=r.leftGripAction;
            leftGripGet.subactionPath=leftHand;
            XrActionStateFloat leftGrip{XR_TYPE_ACTION_STATE_FLOAT};
            xrGetActionStateFloat(r.session,&leftGripGet,&leftGrip);
            const auto now=std::chrono::steady_clock::now();
            const float dt=std::clamp(std::chrono::duration<float>(now-lastFrame).count(),0.f,0.1f);
            lastFrame=now;
            // Point at the world-anchored panel using the controller aim pose.
            // The OpenXR action pose is queried at the compositor's predicted display time.
            bool pointer = false;
            float pointerX = 0.f, pointerY = 0.f;
            bool validAim=false;
            XrVector3f aimPosition{0.f,0.f,0.f};
            bool validLeftAim=false;
            XrVector3f leftPosition{0.f,0.f,0.f};
            XrActionStateGetInfo leftAimGet{XR_TYPE_ACTION_STATE_GET_INFO};
            leftAimGet.action=r.leftAimAction;
            leftAimGet.subactionPath=leftHand;
            XrActionStatePose leftAimState{XR_TYPE_ACTION_STATE_POSE};
            if(XR_SUCCEEDED(xrGetActionStatePose(r.session,&leftAimGet,&leftAimState))
                && leftAimState.isActive) {
                XrSpaceLocation leftLocation{XR_TYPE_SPACE_LOCATION};
                if(XR_SUCCEEDED(xrLocateSpace(r.leftAimSpace,r.space,frameState.predictedDisplayTime,
                                               &leftLocation)) &&
                    (leftLocation.locationFlags & XR_SPACE_LOCATION_POSITION_VALID_BIT) &&
                    (leftLocation.locationFlags & XR_SPACE_LOCATION_ORIENTATION_VALID_BIT)) {
                    validLeftAim=true;
                    leftPosition=leftLocation.pose.position;
                }
            }
            XrActionStateGetInfo aimGet{XR_TYPE_ACTION_STATE_GET_INFO};
            aimGet.action = r.aimAction;
            aimGet.subactionPath = rightHand;
            XrActionStatePose aimState{XR_TYPE_ACTION_STATE_POSE};
            if (XR_SUCCEEDED(xrGetActionStatePose(r.session, &aimGet, &aimState)) && aimState.isActive) {
                XrSpaceLocation location{XR_TYPE_SPACE_LOCATION};
                if (XR_SUCCEEDED(xrLocateSpace(r.aimSpace, r.space, frameState.predictedDisplayTime,
                                               &location)) &&
                    (location.locationFlags & XR_SPACE_LOCATION_POSITION_VALID_BIT) &&
                    (location.locationFlags & XR_SPACE_LOCATION_ORIENTATION_VALID_BIT)) {
                    validAim=true;
                    aimPosition=location.pose.position;
                    const auto& q = location.pose.orientation;
                    // Rotate (0,0,-1) by the controller's normalized quaternion.
                    const float dx = -2.f*(q.x*q.z + q.w*q.y);
                    const float dy = -2.f*(q.y*q.z - q.w*q.x);
                    const float dz = -1.f + 2.f*(q.x*q.x + q.y*q.y);
                    if (dz < -0.001f) {
                        const float t = (panelPosition.z - location.pose.position.z)/dz;
                        if (t > 0.f) {
                            const float x = location.pose.position.x + dx*t;
                            const float y = location.pose.position.y + dy*t;
                            const float px=x-panelPosition.x,py=y-panelPosition.y;
                            const float co=std::cos(panelAngle),si=std::sin(panelAngle);
                            const float localX=(co*px+si*py)/panelScale;
                            const float localY=(-si*px+co*py)/panelScale;
                            pointerX = (localX / 1.45f + 0.5f)*1024.f;
                            pointerY = (localY / 1.08f + 0.5f)*768.f;
                            pointer = pointerX>=0.f && pointerX<=1024.f &&
                                      pointerY>=0.f && pointerY<=768.f;
                        }
                    }
                }
            }
            const bool rightHeld=(page==vrui::Page::Open ||
                page==vrui::Page::Immersive) && validAim &&
                grip.isActive && grip.currentState>0.6f;
            const bool leftHeld=(page==vrui::Page::Open ||
                page==vrui::Page::Immersive) && validLeftAim &&
                leftGrip.isActive && leftGrip.currentState>0.6f;
            const DragMode nextMode=rightHeld && leftHeld ? DragMode::Both :
                rightHeld ? DragMode::Right : leftHeld ? DragMode::Left : DragMode::None;
            if(nextMode!=dragMode) {
                dragMode=nextMode;
                dragPanel=panelPosition;
                if(dragMode==DragMode::Both) {
                    startMidpoint={(aimPosition.x+leftPosition.x)*0.5f,
                                   (aimPosition.y+leftPosition.y)*0.5f,
                                   (aimPosition.z+leftPosition.z)*0.5f};
                    const float vx=aimPosition.x-leftPosition.x;
                    const float vy=aimPosition.y-leftPosition.y;
                    startDistance=std::max(0.05f,std::hypot(vx,vy));
                    startHandAngle=std::atan2(vy,vx);
                    startScale=panelScale;
                    startAngle=panelAngle;
                } else if(dragMode!=DragMode::None) {
                    dragOrigin=dragMode==DragMode::Right?aimPosition:leftPosition;
                }
            }
            if(dragMode==DragMode::Both) {
                const XrVector3f midpoint{(aimPosition.x+leftPosition.x)*0.5f,
                                           (aimPosition.y+leftPosition.y)*0.5f,
                                           (aimPosition.z+leftPosition.z)*0.5f};
                const float vx=aimPosition.x-leftPosition.x;
                const float vy=aimPosition.y-leftPosition.y;
                const float d=std::max(0.05f,std::hypot(vx,vy));
                panelScale=std::clamp(startScale*d/startDistance,0.5f,
                                      page==vrui::Page::Immersive?8.f:4.f);
                // Keep rotation planar for predictable ray intersection and no tilt discomfort.
                panelAngle=startAngle+std::remainder(std::atan2(vy,vx)-startHandAngle,
                                                      6.283185307179586f);
                panelPosition.x=std::clamp(dragPanel.x+midpoint.x-startMidpoint.x,-2.f,2.f);
                panelPosition.y=std::clamp(dragPanel.y+midpoint.y-startMidpoint.y,-1.5f,1.5f);
                panelPosition.z=std::clamp(dragPanel.z+midpoint.z-startMidpoint.z,-4.f,-0.55f);
            } else if(dragMode!=DragMode::None) {
                const auto& hand=dragMode==DragMode::Right?aimPosition:leftPosition;
                panelPosition.x=std::clamp(dragPanel.x+hand.x-dragOrigin.x,-2.f,2.f);
                panelPosition.y=std::clamp(dragPanel.y+hand.y-dragOrigin.y,-1.5f,1.5f);
                panelPosition.z=std::clamp(dragPanel.z+hand.z-dragOrigin.z,-4.f,-0.55f);
            }
            const bool dragging=dragMode!=DragMode::None;
            if((page==vrui::Page::Open || page==vrui::Page::Immersive) &&
               stick.isActive && !dragging && std::abs(stick.currentState.y)>0.15f) {
                menu.changeZoom(stick.currentState.y * dt * 1.5f,pointerX,pointerY,
                                pointer,page==vrui::Page::Immersive);
            }
            const int hovered=(pointer && !dragging && page!=vrui::Page::Immersive)
                ? vrui::buttonAt(page,pointerX,pointerY) : -1;
            const bool triggerDown=pressed.isActive && pressed.currentState>=0.5f;
            const bool aDown=compare.isActive && compare.currentState;
            const bool bDown=exitView.isActive && exitView.currentState;
            // Dedicated photo-only mode has no visible menu: B (or trigger) returns
            // to the editor, and A toggles the stereo comparison without leaving VR.
            if(page==vrui::Page::Immersive) {
                if((bDown&&!wasB)||(triggerDown&&!wasPressed)) {
                    page=vrui::Page::Open;
                    panelPosition=editorPosition;
                    panelScale=editorScale;
                    panelAngle=editorAngle;
                    menu.zoom=editorZoom;
                    menu.centerU=editorU;
                    menu.centerV=editorV;
                    dragMode=DragMode::None;
                } else if(aDown&&!wasA) {
                    menu.stereoEnabled=!menu.stereoEnabled;
                }
            } else if(triggerDown&&!wasPressed && hovered>=0) {
                if(hovered==3) page=vrui::Page::Home;
                else if(page==vrui::Page::Open && hovered==8 && menu.hasPhoto()) {
                    // Preserve the editor layout while immersing; the photo starts
                    // unzoomed on a large, gaze-explorable world-anchored canvas.
                    editorPosition=panelPosition;
                    editorScale=panelScale;
                    editorAngle=panelAngle;
                    editorZoom=menu.zoom;
                    editorU=menu.centerU;
                    editorV=menu.centerV;
                    menu.resetPhotoView();
                    panelPosition={0.f,0.f,-1.9f};
                    panelScale=3.7f;
                    panelAngle=0.f;
                    dragMode=DragMode::None;
                    page=vrui::Page::Immersive;
                } else if(page==vrui::Page::Open && hovered==9 &&
                          menu.hasPhoto() && !menu.quickConverting) {
                    menu.quickConverting=true;
                    callQuickConvert(env,activity);
                } else if(page==vrui::Page::Open && hovered==6 && menu.hasPhoto()) {
                    menu.stereoEnabled=!menu.stereoEnabled;
                } else if(page==vrui::Page::Open && hovered==7 && menu.hasPhoto()) {
                    menu.depthLevel=(menu.depthLevel+1)%3;
                } else if(page==vrui::Page::Open && hovered==5) {
                    menu.resetPhotoView();
                    panelPosition={0.f,0.f,-1.9f};
                    panelScale=1.f;
                    panelAngle=0.f;
                    dragMode=DragMode::None;
                } else if(page==vrui::Page::Open && hovered==4) {
                    resumeImagePage.store(true);
                    callPicker(env,activity);
                } else if(page==vrui::Page::Home) {
                    page=hovered==0?vrui::Page::Open:
                         hovered==1?vrui::Page::Projects:vrui::Page::Gallery;
                }
            }
            wasPressed=triggerDown;
            wasA=aDown;
            wasB=bDown;

            uint32_t index = 0;
            XrSwapchainImageAcquireInfo acquire{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO};
            check(xrAcquireSwapchainImage(r.swapchain, &acquire, &index), "acquire image");
            XrSwapchainImageWaitInfo waitImage{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO};
            waitImage.timeout = XR_INFINITE_DURATION;
            check(xrWaitSwapchainImage(r.swapchain, &waitImage), "wait image");
            glBindFramebuffer(GL_FRAMEBUFFER, r.fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D,
                                   images[index].image, 0);
            if(imageNeedsReload.exchange(false)) {
                refreshImage(env,activity,menu);
                depthNeedsReload.store(true);
            }
            if(depthNeedsReload.exchange(false))refreshDepth(env,activity,menu);
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_BLEND);
            for(int eye=0;eye<2;++eye) {
                glViewport(eye*1024,0,1024,768);
                glEnable(GL_SCISSOR_TEST);
                glScissor(eye*1024,0,1024,768);
                glClearColor(0.045f,0.038f,0.08f,1.f);
                glClear(GL_COLOR_BUFFER_BIT);
                glDisable(GL_SCISSOR_TEST);
                const float eyeSign=eye==0?-1.f:1.f;
                menu.draw(page,hovered,pointerX,pointerY,pointer,eyeSign);
            }
            glBindFramebuffer(GL_FRAMEBUFFER,0);
            glFlush();
            XrSwapchainImageReleaseInfo release{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO};
            check(xrReleaseSwapchainImage(r.swapchain,&release),"release image");
            for(int eye=0;eye<2;++eye) {
                auto& quad=quads[eye];
                quad.space=r.space;
                quad.eyeVisibility=eye==0?XR_EYE_VISIBILITY_LEFT:XR_EYE_VISIBILITY_RIGHT;
                quad.pose.orientation.z=std::sin(panelAngle*0.5f);
                quad.pose.orientation.w=std::cos(panelAngle*0.5f);
                quad.pose.position=panelPosition;
                quad.size={1.45f*panelScale,1.08f*panelScale};
                quad.subImage.swapchain=r.swapchain;
                quad.subImage.imageRect.offset={eye*1024,0};
                quad.subImage.imageRect.extent={1024,768};
            }
            layerCount=2;
        }
        XrFrameEndInfo end{XR_TYPE_FRAME_END_INFO};
        end.displayTime = frameState.predictedDisplayTime;
        end.environmentBlendMode = XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
        end.layerCount = layerCount;
        end.layers = layerCount ? layers : nullptr;
        check(xrEndFrame(r.session, &end), "xrEndFrame");
    }
    if (running) xrRequestExitSession(r.session);
    log("OpenXR session stopped");
}
} // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_cosmerearchives_androidimageeditor_VrActivity_runNative(JNIEnv* env, jobject activity) {
    stopRequested.store(false);
    imageNeedsReload.store(true);
    depthNeedsReload.store(true);
    try { run(env, activity); }
    catch (const char* message) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "OpenXR startup/render error: %s", message);
    }
}
extern "C" JNIEXPORT void JNICALL
Java_com_cosmerearchives_androidimageeditor_VrActivity_requestNativeStop(JNIEnv*, jobject) {
    stopRequested.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_cosmerearchives_androidimageeditor_VrActivity_notifyImageChanged(JNIEnv*, jobject) {
    imageNeedsReload.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_cosmerearchives_androidimageeditor_VrActivity_notifyDepthChanged(JNIEnv*, jobject) {
    depthNeedsReload.store(true);
}
