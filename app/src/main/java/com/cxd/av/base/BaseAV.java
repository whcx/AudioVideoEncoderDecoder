package com.cxd.av.base;

import android.content.Context;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import android.os.Process;
import android.util.ArrayMap;

import com.cxd.av.audiodecoder.AudioPlayer;
import com.cxd.av.utils.AVUtils;
import com.cxd.av.utils.PlaybackConfiguration;
import com.cxd.av.videodecoder.VideoPlayer;

public class BaseAV {
    private final String TAG = BaseAV.class.getSimpleName();
    private Context mContext;
    public static boolean DEBUG = false;
    private volatile static BaseAV GMAVINSTANCE = null;
    public boolean isLooping = true;
    private String mUriStr = null;
    private final AtomicInteger PARALLEL_NUM = new AtomicInteger();
    public static long TIME_OUT = 1000; //1s
    private static final int CORE_POOL_SIZE = 4;
    private static final int MAX_POOL_SIZE = 6;
    private static final long KEEP_ALIVE_TIME = 4; // 4 s
    private final ThreadPoolExecutor mExecutor;
    public PlaybackConfiguration mPlaybackConfiguration = null;
    public Set<String> mUriSet = new HashSet<>();
    public Map<String, Object> mSyncLock = new ArrayMap<>();
    public Map<String, VideoPlayer> mUriVideoPlayer = new ArrayMap<>();
    public Map<String, AudioPlayer> mUriAudioPlayer = new ArrayMap<>();
    public Map<String, AVUtils> mUriAVUtils = new ArrayMap<>();

    public static BaseAV getBaseAV() {
        if (GMAVINSTANCE == null) {
            synchronized(BaseAV.class) {
                if (GMAVINSTANCE == null) {
                    GMAVINSTANCE = new BaseAV();
                }
            }
        }
        return GMAVINSTANCE;
    }
    public BaseAV() {
        mExecutor = new ThreadPoolExecutor(
                CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME,
                TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),
                new PriorityThreadFactory("av-player",
                        Process.THREAD_PRIORITY_BACKGROUND));
        setReady(false);
    }

    public void setContext(Context context) {
        mContext = context;
        initNativeInstance();
        mPlaybackConfiguration = new PlaybackConfiguration(mContext);
    }

    private static class DownLoadRunnable implements Runnable {
        @Override
        public void run() {
            BaseAV.getBaseAV().mPlaybackConfiguration.downLoadFileFromUri(BaseAV.getBaseAV().mUriStr);
        }
    }
    public void submitTask(Runnable task) {
        mExecutor.execute(task);
    }

    public void setReady(boolean flag){
        nativeSetReady(flag);
    }
    public void setLooping (boolean looping) {
        isLooping = looping;
    }
    public void start(String httpPath){
        if (null != mUriAudioPlayer.get(mUriStr)) {
            mUriAudioPlayer.get(mUriStr).start();
        }
        if (null != mUriVideoPlayer.get(mUriStr)) {
            mUriVideoPlayer.get(mUriStr).start();
        }
    }

    public void resume() {
        mUriAudioPlayer.forEach((key, value) -> {
            value.resume();
        });

        mUriVideoPlayer.forEach((key, value) -> {
            value.resume();
        });
    }
    public void pause() {
        mUriAudioPlayer.forEach((key, value) -> {
            value.pause();
        });

        mUriVideoPlayer.forEach((key, value) -> {
            value.pause();
        });
    }
    public void stop(String httpPath){
        isLooping = false;
        mContext = null;
        setReady(false);
        AudioPlayer gWAudioPlayer = mUriAudioPlayer.get(httpPath);
        if (null != gWAudioPlayer && gWAudioPlayer.isActive()) {
            gWAudioPlayer.stop();
            mUriAudioPlayer.remove(httpPath);
        }
        VideoPlayer gWVideoPlayer = mUriVideoPlayer.get(httpPath);
        if (null != gWVideoPlayer && gWVideoPlayer.isActive()) {
            gWVideoPlayer.stop();
            mUriVideoPlayer.remove(httpPath);
        }
    }

    public void setDataSourceHttp(String httpPath){
        assert (httpPath != null);
        mUriStr = httpPath;
        mUriSet.add(mUriStr);
        setupSyncLock(mUriStr);
        if (null == mPlaybackConfiguration.getFileFromUri(mUriStr)) {
            submitTask(new DownLoadRunnable());
        }
        if (null == mUriAVUtils.get(mUriStr)) {
            AVUtils gwavUtils = new AVUtils();
            mUriAVUtils.put(mUriStr, gwavUtils);
        }
        if (null == mUriAudioPlayer.get(mUriStr)) {
            AudioPlayer gWAudioPlayer = new AudioPlayer(mContext, mUriStr, PARALLEL_NUM.getAndIncrement());
            mUriAudioPlayer.put(mUriStr, gWAudioPlayer);
        }

        if (null == mUriVideoPlayer.get(mUriStr)) {
            VideoPlayer gWVideoPlayer = new VideoPlayer(mContext, mUriStr, PARALLEL_NUM.getAndIncrement());
            mUriVideoPlayer.put(mUriStr, gWVideoPlayer);
        }
    }

    private void setupSyncLock(String uriStr) {
        if (!mSyncLock.containsKey(uriStr)) {
            mSyncLock.put(uriStr, new Object());
        }
    }

    public int getTrack(){
        return -1;
    }
    public void destroy(){
        if (null != GMAVINSTANCE) {
            mUriAudioPlayer.forEach((key, value) -> {
                value.destroy();
                mUriAudioPlayer.clear();
            });

            mUriVideoPlayer.forEach((key, value) -> {
                value.destroy();
                mUriVideoPlayer.clear();
            });
            mUriAVUtils.clear();
            mUriSet.clear();
            mSyncLock.clear();
            mExecutor.shutdownNow();
            releaseNativeInstance();
            mPlaybackConfiguration.destroy();
            GMAVINSTANCE = null;
        }
    }


    public native void nativeSetReady(boolean flag);
    private native void initNativeInstance();
    private native void releaseNativeInstance();

    private static class PriorityThreadFactory implements ThreadFactory {

        private final int mPriority;
        private final AtomicInteger mNumber = new AtomicInteger();
        private final String mName;

        public PriorityThreadFactory(String name, int priority) {
            mName = name;
            mPriority = priority;
        }
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, mName + '-' + mNumber.getAndIncrement()) {
                @Override
                public void run() {
                    Process.setThreadPriority(mPriority);
                    super.run();
                }
            };
            thread.setDaemon(true);
            return thread;
        }
    }

}
