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