#include "include/Compiler.h"
#include "include/FrameQueue.h"

AV_ED_BEGIN

FrameQueue::FrameQueue() {
        pthread_mutex_init(&mutex, 0);
        pthread_cond_init(&cond, 0);
    }
    FrameQueue::~FrameQueue() {
        pthread_cond_destroy(&cond);
        pthread_mutex_destroy(&mutex);
    }

    void FrameQueue::enQueue(VideoFrame newFrame) {
        pthread_mutex_lock(&mutex);
        if (queueEnable) {
            if(frameQueue.size() >= MAX_NUM) {
                frameQueue.pop();
                releaseHandle(newFrame);
            }
            frameQueue.push(newFrame);
            pthread_cond_signal(&cond);
        } else {
            releaseHandle(newFrame);
        }
        pthread_mutex_unlock(&mutex);
    }

    bool FrameQueue::deQueue(VideoFrame &value) {
        bool ret = false;
        pthread_mutex_lock(&mutex);
        while (queueEnable && frameQueue.empty()){
            pthread_cond_wait(&cond, &mutex);
        }
        if (!frameQueue.empty()){
            value = frameQueue.front();
            if (frameQueue.size() > 1)
                frameQueue.pop();
            ret = true;
        }
        pthread_mutex_unlock(&mutex);
        return ret;
    }

    void FrameQueue::setEnable(bool enable) {
        pthread_mutex_lock(&mutex);
        this->queueEnable = enable;
        pthread_cond_signal(&cond);
        pthread_mutex_unlock(&mutex);
    }

    bool FrameQueue::empty() {
        return frameQueue.empty();
    }

    size_t FrameQueue::size() {
        return frameQueue.size();
    }

    void FrameQueue::clear() {
        pthread_mutex_lock(&mutex);
        int size = frameQueue.size();
        for (int i = 0; i < size; ++i) {
            VideoFrame value = frameQueue.front();
            releaseHandle(value);
            frameQueue.pop();
        }
        pthread_mutex_unlock(&mutex);
    }

    void FrameQueue::setReleaseHandle(ReleaseHandle handle) {
        releaseHandle = handle;
    }

AV_ED_END
