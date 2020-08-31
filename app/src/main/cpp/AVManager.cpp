#include "AVManager.h"

AVManager::AVManager() {

}

AVManager::~AVManager() {

}
extern "C"
JNIEXPORT void JNICALL
Java_com_cxd_av_base_BaseAV_nativeSetReady(JNIEnv *env, jobject thiz, jboolean flag) {
// TODO: implement nativeSetReady()
}

extern "C"
JNIEXPORT void JNICALL
Java_com_cxd_av_base_BaseAV_initNativeInstance(JNIEnv *env, jobject thiz) {
// TODO: implement initNativeInstance()
}

extern "C"
JNIEXPORT void JNICALL
Java_com_cxd_av_base_BaseAV_releaseNativeInstance(JNIEnv *env, jobject thiz) {
// TODO: implement releaseNativeInstance()
}

extern "C"
JNIEXPORT void JNICALL
Java_com_cxd_av_utils_AVUtils_nativeUpdateVideoFrame(JNIEnv *env, jobject thiz, jbyteArray src,
                                                     jstring url, jint width, jint height,
                                                     jboolean flag) {
    // TODO: implement nativeUpdateVideoFrame()
}
