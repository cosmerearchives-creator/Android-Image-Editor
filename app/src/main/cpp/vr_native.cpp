#include <jni.h>
#include <atomic>
#include <chrono>
#include <thread>
#include <vector>
#include <cstring>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>

namespace {
std::atomic<bool> stopRequested{false};
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
    EGLDisplay display{EGL_NO_DISPLAY};
    EGLContext context{EGL_NO_CONTEXT};
    EGLSurface surface{EGL_NO_SURFACE};
    GLuint fbo{0};
    ~XrResources() {
        if (session != XR_NULL_HANDLE) xrDestroySession(session);
        if (actionSet != XR_NULL_HANDLE) xrDestroyActionSet(actionSet);
        if (space != XR_NULL_HANDLE) xrDestroySpace(space);
        if (swapchain != XR_NULL_HANDLE) xrDestroySwapchain(swapchain);
        if (instance != XR_NULL_HANDLE) xrDestroyInstance(instance);
        if (fbo) glDeleteFramebuffers(1, &fbo);
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
    XrActionCreateInfo actionInfo{XR_TYPE_ACTION_CREATE_INFO};
    actionInfo.actionType = XR_ACTION_TYPE_FLOAT_INPUT;
    std::strcpy(actionInfo.actionName, "select");
    std::strcpy(actionInfo.localizedActionName, "Select");
    actionInfo.countSubactionPaths = 1;
    actionInfo.subactionPaths = &rightHand;
    check(xrCreateAction(r.actionSet, &actionInfo, &r.triggerAction), "xrCreateAction");
    XrActionSuggestedBinding suggested{r.triggerAction,
        path(r.instance, "/user/hand/right/input/trigger/value")};
    XrInteractionProfileSuggestedBinding profile{XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING};
    profile.interactionProfile = path(r.instance, "/interaction_profiles/oculus/touch_controller");
    profile.countSuggestedBindings = 1;
    profile.suggestedBindings = &suggested;
    check(xrSuggestInteractionProfileBindings(r.instance, &profile), "xrSuggestInteractionProfileBindings");
    XrSessionActionSetsAttachInfo attach{XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO};
    attach.countActionSets = 1;
    attach.actionSets = &r.actionSet;
    check(xrAttachSessionActionSets(r.session, &attach), "xrAttachSessionActionSets");

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
    swapInfo.width = 1024;
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
        XrCompositionLayerQuad quad{XR_TYPE_COMPOSITION_LAYER_QUAD};
        const XrCompositionLayerBaseHeader* layers[] = {
            reinterpret_cast<const XrCompositionLayerBaseHeader*>(&quad)
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

            uint32_t index = 0;
            XrSwapchainImageAcquireInfo acquire{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO};
            check(xrAcquireSwapchainImage(r.swapchain, &acquire, &index), "acquire image");
            XrSwapchainImageWaitInfo waitImage{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO};
            waitImage.timeout = XR_INFINITE_DURATION;
            check(xrWaitSwapchainImage(r.swapchain, &waitImage), "wait image");
            glBindFramebuffer(GL_FRAMEBUFFER, r.fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D,
                                   images[index].image, 0);
            glViewport(0, 0, 1024, 768);
            glDisable(GL_SCISSOR_TEST);
            glClearColor(0.045f, 0.038f, 0.08f, 1.f);
            glClear(GL_COLOR_BUFFER_BIT);
            glEnable(GL_SCISSOR_TEST);
            // Minimal purple home-screen prototype: two distinct interactive states.
            glScissor(90, 295, 844, 190);
            bool selected = pressed.isActive && pressed.currentState > 0.35f;
            glClearColor(selected ? 0.55f : 0.24f, 0.17f, selected ? 0.84f : 0.43f, 1.f);
            glClear(GL_COLOR_BUFFER_BIT);
            glScissor(270, 110, 484, 95);
            glClearColor(0.12f, 0.10f, 0.20f, 1.f);
            glClear(GL_COLOR_BUFFER_BIT);
            glDisable(GL_SCISSOR_TEST);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glFlush();
            XrSwapchainImageReleaseInfo release{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO};
            check(xrReleaseSwapchainImage(r.swapchain, &release), "release image");
            quad.space = r.space;
            quad.eyeVisibility = XR_EYE_VISIBILITY_BOTH;
            quad.pose.orientation.w = 1.f;
            quad.pose.position.z = -1.9f;
            quad.size = {1.45f, 1.08f};
            quad.subImage.swapchain = r.swapchain;
            quad.subImage.imageRect.extent = {1024, 768};
            layerCount = 1;
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
    try { run(env, activity); }
    catch (const char* message) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "OpenXR startup/render error: %s", message);
    }
}
extern "C" JNIEXPORT void JNICALL
Java_com_cosmerearchives_androidimageeditor_VrActivity_requestNativeStop(JNIEnv*, jobject) {
    stopRequested.store(true);
}
