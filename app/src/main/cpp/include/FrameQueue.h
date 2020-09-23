#pragma once

#include <pthread.h>
#include <queue>
#include "Compiler.h"


AV_ED_BEGIN
typedef uint8_t* VideoFrame;
typedef void (*ReleaseHandle)(VideoFrame& frame);

using namespace std;

class FrameQueue{
public:
    FrameQueue();
    ~FrameQueue();

public:
    void enQueue(VideoFrame newFrame);
    bool deQueue(VideoFrame &value);
    void setEnable(bool enable);
    bool empty();
    size_t size();
    void clear();
    void setReleaseHandle(ReleaseHandle handle);
    const int MAX_NUM = 4; //In sync with MAX_BUFFERS of GWAVUtils.java
private:
    pthread_cond_t cond;
    pthread_mutex_t mutex;
    queue<VideoFrame> frameQueue;
    bool queueEnable;
    ReleaseHandle releaseHandle;
};

AV_ED_END