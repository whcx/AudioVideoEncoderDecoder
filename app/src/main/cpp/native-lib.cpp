#include <jni.h>
#include <string>
#include "include/Singleton.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_cxd_av_activity_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void*) {
    return JNI_VERSION_1_6;
}