#pragma once
#include <jni.h>
#include <string>
#include "FrameQueue.h"

AV_ED_BEGIN
using namespace std;
typedef void (*UpdateFramePtr)(uint8_t* frame, int width, int height);
class AVPlayer {
public:
    AVPlayer();
public:
    void Unload();
    void destroyInstance();
    ~AVPlayer();

public:
    void SetUrl(const string& url);
    void GetData(uint8_t*& data, const string& url, int& width, int& height);
    void FreeFrame();
    void Stop(const string& url);
    void Start(const string& url);
    bool IsReady(const string& url);

    void updateVideoFrame(JNIEnv *env, jobject thiz,
                          jbyteArray src_, jstring url,jint width, jint height, jboolean flag);
    static void releaseVideoFrame(VideoFrame& frame);

    void onVideoFrameAvailable(uint8_t* frame, int width, int height);
    void setVideoFrameAvailableCallback(UpdateFramePtr callback);

public:
    int videoWidth;
    int videoHeight;
    int dataSize{ 0 };
    bool readyFlag{ false };

    FrameQueue videoFrameQueue;


private:
    static UpdateFramePtr frameAvailableCallback;
};
AV_ED_END