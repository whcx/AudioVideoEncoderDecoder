

#include "include/Compiler.h"
#include "include/AVPlayer.h"
#include "include/AVManager.h"

AV_ED_BEGIN
using namespace std;
UpdateFramePtr AVPlayer::frameAvailableCallback = nullptr;

void AVPlayer::Unload()
{
}

AVPlayer::AVPlayer() {
    videoFrameQueue.setReleaseHandle(AVPlayer::releaseVideoFrame);
}

AVPlayer::~AVPlayer() {
    videoFrameQueue.setReleaseHandle(nullptr);
}

void AVPlayer::releaseVideoFrame(VideoFrame& frame){
//        if(nullptr != frame) {
//            frame = nullptr;
//        }
    }

void AVPlayer::FreeFrame() {
}

void AVPlayer::Start(const string& url) {
    JNIEnv* jniEnv = nullptr;
    bool result = false;
    if (AVManager::GetInstance().g_jvm == nullptr) {
        return;
    }
    videoFrameQueue.setEnable(true);
    result = AVManager::GetInstance().getJNIEnv(&jniEnv);
    jstring dataUrl = jniEnv->NewStringUTF(url.c_str());
    jniEnv->CallVoidMethod(AVManager::GetInstance().object,
                           AVManager::GetInstance().startPlay, dataUrl);
    jniEnv->DeleteLocalRef(dataUrl);
    dataUrl = nullptr;
    if (result) {
        AVManager::GetInstance().g_jvm->DetachCurrentThread();
    }
}

void AVPlayer::Stop(const string& url) {
    JNIEnv* jniEnv = nullptr;
    bool result = false;
    if (AVManager::GetInstance().g_jvm == nullptr) {
        return;
    }
    readyFlag = false;
    videoFrameQueue.setEnable(false);
    result = AVManager::GetInstance().getJNIEnv(&jniEnv);
    jstring dataUrl = jniEnv->NewStringUTF(url.c_str());
    jniEnv->CallVoidMethod(AVManager::GetInstance().object,
                           AVManager::GetInstance().stopPlay, dataUrl);
    jniEnv->DeleteLocalRef(dataUrl);
    dataUrl = nullptr;
    if (result) {
        AVManager::GetInstance().g_jvm->DetachCurrentThread();
    }
}

/**
*  @brief Get the video frames.
*  @details Get the video frames,from a Queue.
*
*  @param data The data of video frame, As a out parameter.
*/
void AVPlayer::GetData(uint8_t *&data, const string& url, int &width, int &height) {
    uint8_t* frame = nullptr;
    width = videoWidth;
    height = videoHeight;
    videoFrameQueue.deQueue(frame);
    data = frame;
}
bool AVPlayer::IsReady(const string& url) {
    return readyFlag;
}

void AVPlayer::SetUrl(const string& url) {
    JNIEnv *jniEnv = nullptr;
    bool result = false;
    if (nullptr == url.c_str() || AVManager::GetInstance().g_jvm == nullptr) {
        return;
    }
    result = AVManager::GetInstance().getJNIEnv(&jniEnv);
    jstring dataUrl = jniEnv->NewStringUTF(url.c_str());
    jniEnv->CallVoidMethod(AVManager::GetInstance().object,
                           AVManager::GetInstance().setDataSourceHttp,dataUrl);
    jniEnv->DeleteLocalRef(dataUrl);
    dataUrl = nullptr;
    if (result) {
        AVManager::GetInstance().g_jvm->DetachCurrentThread();
    }
}

void AVPlayer::destroyInstance() {
}

/**
*  @brief Update the video frames.
*  @details Update the video frames, at a specific frame rate. Save the video frame to a Queue.
*
*  @param src_ The data of video frame.
*  @param width The width of video frame.
*  @param height The height of video frame.
*/

void AVPlayer::updateVideoFrame(JNIEnv *env, jobject thiz,
                jbyteArray src_, jstring url,jint width, jint height, jboolean flag) {
        if (nullptr != src_) {
            videoFrameQueue.setEnable(true);
            jbyte* data = env->GetByteArrayElements(src_, 0);
            uint8_t* src = reinterpret_cast<uint8_t *>(data);

            readyFlag = true;
            videoWidth = width;
            videoHeight = height;
            videoFrameQueue.enQueue(src);
            env->ReleaseByteArrayElements(src_, data, JNI_ABORT);
        }
    }

    void AVPlayer::onVideoFrameAvailable(uint8_t *frame, int width, int height) {
        if (AVPlayer::frameAvailableCallback != nullptr) {
            (AVPlayer::frameAvailableCallback)(frame, width, height);
        }
    }
    void AVPlayer::setVideoFrameAvailableCallback(UpdateFramePtr callback) {
        AVPlayer::frameAvailableCallback = callback;
    }
AV_ED_END

