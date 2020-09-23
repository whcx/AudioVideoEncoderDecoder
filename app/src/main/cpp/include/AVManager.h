
#pragma once
#include <jni.h>
#include <string>
#include <unordered_map>
#include "Singleton.h"
#include "AVPlayer.h"

AV_ED_BEGIN
using namespace std;
class AVManager : public Singleton<AVManager>
{
public:
    AVManager();
    ~AVManager();
    void DestroyInstance();

    void SetUrl(const string& url);
    void GetData(uint8_t*& data, const string& url, int& width, int& height);
    void Stop(const string& url);
    void Start(const string& url);
    bool IsReady(const string& url);
    void FreeFrame();
    void Unload();

public:
    std::unordered_map<string, std::shared_ptr<AVPlayer>> uriMediaPlayers;
    bool readyFlag{ false };
    JavaVM * g_jvm;
    jclass clazz;
    jobject object;
    jmethodID setDataSourceHttp;
    jmethodID startPlay;
    jmethodID stopPlay;
    bool getJNIEnv(JNIEnv **jniEnv);
};

AV_ED_END