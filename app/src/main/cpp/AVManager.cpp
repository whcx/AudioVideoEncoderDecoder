#include "AVManager.h"

AV_ED_BEGIN
AVManager::AVManager() {
}

AVManager::~AVManager() {
    if (nullptr != AVManager::GetInstance().clazz) {
        AVManager::GetInstance().clazz = nullptr;
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_cxd_av_base_BaseAV_nativeSetReady(JNIEnv *env, jobject thiz, jboolean flag) {
// TODO: implement nativeSetReady()
}

extern "C"
JNIEXPORT void JNICALL
Java_com_cxd_av_base_BaseAV_initNativeInstance(JNIEnv *env, jobject thiz) {
    env->GetJavaVM(&(AVManager::GetInstance().g_jvm));
    AVManager::GetInstance().object = env->NewGlobalRef(thiz);
    AVManager::GetInstance().clazz = env->FindClass("com/cxd/av/base/BaseAV");
    AVManager::GetInstance().setDataSourceHttp = env->GetMethodID(
            AVManager::GetInstance().clazz,"setDataSourceHttp","(Ljava/lang/String;)V");
    AVManager::GetInstance().startPlay = env->GetMethodID(
            AVManager::GetInstance().clazz,"start","(Ljava/lang/String;)V");
    AVManager::GetInstance().stopPlay = env->GetMethodID(
            AVManager::GetInstance().clazz,"stop","(Ljava/lang/String;)V");
    AVManager::GetInstance().readyFlag = false;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_cxd_av_base_BaseAV_releaseNativeInstance(JNIEnv *env, jobject thiz) {
    if (nullptr != AVManager::GetInstance().object) {
        env->DeleteGlobalRef(AVManager::GetInstance().object);
        AVManager::GetInstance().object = nullptr;
    }
    AVManager::GetInstance().DestroyInstance();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_cxd_av_utils_AVUtils_nativeUpdateVideoFrame(JNIEnv *env, jobject thiz, jbyteArray src_,
                                                     jstring url, jint width, jint height,
                                                     jboolean flag) {
    if (nullptr != src_) {
        const char* nativeUrl = env->GetStringUTFChars(url, 0);
        auto iter = AVManager::GetInstance().uriMediaPlayers.find(nativeUrl);
        if (iter == AVManager::GetInstance().uriMediaPlayers.end()) {
            return;
        }
        std::shared_ptr<AVPlayer> videoPlayer = iter->second;
        videoPlayer->updateVideoFrame(env, thiz, src_, url, width, height, flag);
    }
}

    void AVManager::Unload()
    {
        auto iter = uriMediaPlayers.begin();
        while (iter != uriMediaPlayers.end()) {
            iter->second->Unload();
            iter++;
        }
        if (AVManager::GetInstance().g_jvm != nullptr) {
            JNIEnv* jniEnv = nullptr;
            bool result = AVManager::GetInstance().getJNIEnv(&jniEnv);
            if (nullptr != AVManager::GetInstance().object) {
                jniEnv->DeleteGlobalRef(AVManager::GetInstance().object);
                AVManager::GetInstance().object = nullptr;
            }
            if (result) {
                AVManager::GetInstance().g_jvm->DetachCurrentThread();
            }
            AVManager::GetInstance().g_jvm = nullptr;
        }
    }
    void AVManager::DestroyInstance() {
        auto iter = uriMediaPlayers.begin();
        while (iter != uriMediaPlayers.end()) {
            iter->second->destroyInstance();
            iter++;
        }
        uriMediaPlayers.clear();
    }

    bool AVManager::getJNIEnv(JNIEnv **jniEnv) {
        int result = AVManager::GetInstance().g_jvm->GetEnv((void **)jniEnv,JNI_VERSION_1_6);
        if (result == JNI_EDETACHED) {
            JavaVMAttachArgs jvmArgs;
            jvmArgs.version = JNI_VERSION_1_6;
            result = AVManager::GetInstance().g_jvm->AttachCurrentThread(jniEnv, &jvmArgs);
            if (JNI_OK == result) {
                return true;
            }
        }
        return false;
    }

    void AVManager::GetData(uint8_t *&data, const string& url, int &width, int &height) {
        auto iter = uriMediaPlayers.find(url);
        if (iter == uriMediaPlayers.end()) {
            return;
        }
        std::shared_ptr<AVPlayer> videoPlayer = iter->second;
        videoPlayer->GetData(data, url, width, height);
    }
    bool AVManager::IsReady(const string& url) {
        auto iter = uriMediaPlayers.find(url);
        if (iter == uriMediaPlayers.end()) {
            return false;
        }
        std::shared_ptr<AVPlayer> videoPlayer = iter->second;
        return videoPlayer->IsReady(url);
    }

/**
*  @brief Sets the data source
*  @details Sets the data source, To support multi instance playback. An Instance of AVPlayer is
*  created for each url.
*
*  @param url The resource you want to play.
*/
    void AVManager::SetUrl(const string& url) {
        auto iter = uriMediaPlayers.find(url);
        if (iter == uriMediaPlayers.end()) {
            std::shared_ptr<AVPlayer> videoPlayer = std::make_shared<AVPlayer>();
            uriMediaPlayers.insert(pair<string, std::shared_ptr<AVPlayer>>(url,videoPlayer));
//            uriMediaPlayers.insert_or_assign(url, videoPlayer);
        }
        std::shared_ptr<AVPlayer> videoPlayer= uriMediaPlayers[url];
        videoPlayer->SetUrl(url);
    }

/**
*  @brief start playback
*  @details start playback, Get the corresponding instance according to the Url,
*  then play back through the instance.
*
*  @param url The resource you want to play.
*/
    void AVManager::Start(const string& url) {
        auto iter = uriMediaPlayers.find(url);
        if (iter == uriMediaPlayers.end()) {
            return;
        }
        std::shared_ptr<AVPlayer> videoPlayer = iter->second;
        videoPlayer->Start(url);
    }

    void AVManager::Stop(const string& url) {
        auto iter = uriMediaPlayers.find(url);
        if (iter == uriMediaPlayers.end()) {
            return;
        }
        std::shared_ptr<AVPlayer> videoPlayer = iter->second;
        videoPlayer->Stop(url);
    }

    void AVManager::FreeFrame() {

    }

AV_ED_END