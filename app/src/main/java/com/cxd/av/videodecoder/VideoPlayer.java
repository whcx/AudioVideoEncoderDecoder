package com.cxd.av.videodecoder;

import android.content.Context;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.cxd.av.base.BaseAV;
import com.cxd.av.utils.PlaybackConfiguration;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class VideoPlayer extends BaseAV {
    private final String TAG = VideoPlayer.class.getSimpleName();
    private String mUrlStr = null;
    private String mFileStr = null;
    private final int DEFAULTRATE = 30;
    private int mFrameRate = 30;
    private int MAX_IMAGES = 4; //In sync with MAX_NUM of FrameQueue.h
    private int mImageFormat = ImageFormat.UNKNOWN;
    private int mWidth;
    private int mHeight;
    private final int DECODERCOLORFORMAT = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
    private MediaExtractor mMediaExtractorVideo;
    private MediaCodec mMediaDecoderVideo;
    private int mVideoTrackIndex;
    private MediaFormat mVideoMediaFormat;
    private String mVideoMime;
    private int mOutputVideoFrameCount = 0;
    private boolean mInputEOS = false;
    private boolean mOutputEOS = false;
    private boolean INITSUCCESS = false;
    private static final int LOOP_PLAYING = 1;
    private static final int STOP_PLAYING = 2;
    private static final int RESUME_PLAYING = 3;
    private long mFrameDuration =0;
    private VideoPlayerHandler mVideoPlayerHandler;
    private HandlerThread mVideoHandlerThread;
    private Context mContext;
    private long presentationTimeUS;
    private PlaybackConfiguration.PlayState mVideoState = PlaybackConfiguration.PlayState.PLAYER_STATE_IDLE;
    private final Object mStateSyncLock = new Object();
    private ImageAvailableListener mImageListener;
    private ImageReader mImageReader;
    private Surface mReaderSurface;

    public VideoPlayer(Context context, String uri, int numbers) {
        mContext = context;
        mUrlStr = uri;
        mImageListener = new ImageAvailableListener();
        mVideoHandlerThread = new HandlerThread("VideoPlay-"+numbers);
        mVideoHandlerThread.start();
        mVideoPlayerHandler = new VideoPlayerHandler(mVideoHandlerThread.getLooper());
        setReady(false);
    }
    private class VideoPlayerHandler extends Handler {
        public VideoPlayerHandler(Looper looper){
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case LOOP_PLAYING:
                    android.util.Log.d(TAG, "onImageAvailable,isLooping=" + isLooping);
                    if (isLooping) {
                        mMediaDecoderVideo.reset();
                        start();
                    }
                    return;
                case STOP_PLAYING:
                    mVideoHandlerThread.quitSafely();
                    mVideoHandlerThread = null;
                    mVideoPlayerHandler.removeCallbacksAndMessages(null);
                    mContext = null;
                    destroy();
                    return;
                case RESUME_PLAYING:
                    if (mMediaExtractorVideo != null && presentationTimeUS != 0) {
                        submitTask(videoPlay);
                        mMediaExtractorVideo.seekTo(presentationTimeUS / 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                    }
                    return;
                default:
                    break;
            }
        }
    }

    private void init() {
        mMediaExtractorVideo = new MediaExtractor();
        mFileStr = getBaseAV().mPlaybackConfiguration.getFileFromUri(mUrlStr);
        if (null == mFileStr) {
            setDataSourceHttp(mUrlStr);
        } else {
            setDataSourceFile(mFileStr);
        }
        createMediaDecoderVideo();
    }
    Runnable videoPlay = new Runnable() {
        @Override
        public void run() {
            init();
            if (INITSUCCESS) {
                mInputEOS = false;
                mOutputEOS = false;
                try {
                    if (mImageFormat == ImageFormat.YUV_420_888) {
                        updateVideoFrameReaderAsync();
                    } else {
                        updateVideoFrame();
                    }
                } catch (IllegalStateException | IllegalArgumentException ise) {
                    ise.printStackTrace();
                }
            }
        }
    };

    public void start() {
        submitTask(videoPlay);
    }

    @Override
    public void resume() {
        if (willActive()) {
            mVideoPlayerHandler.sendEmptyMessageDelayed(RESUME_PLAYING, 100);
        }
    }

    @Override
    public void pause() {
        changePlayerState(PlaybackConfiguration.PlayState.PLAYER_STATE_PAUSED);
    }

    public void stop() {
        mInputEOS = true;
        mOutputEOS =true;
        presentationTimeUS = 0;
        changePlayerState(PlaybackConfiguration.PlayState.PLAYER_STATE_STOPPED);
        mVideoPlayerHandler.sendEmptyMessageDelayed(STOP_PLAYING, 200);
    }

    private void changePlayerState(PlaybackConfiguration.PlayState currentState) {
        synchronized(mStateSyncLock) {
            mVideoState = currentState;
        }
    }

    public boolean willActive() {
        switch (mVideoState) {
            case PLAYER_STATE_STOPPED:
            case PLAYER_STATE_PAUSED:
                return true;
            case PLAYER_STATE_IDLE:
            case PLAYER_STATE_STARTED:
            case PLAYER_STATE_RELEASED:
            case PLAYER_STATE_UNKNOWN:
            default:
                return false;
        }
    }

    public boolean isActive() {
        switch (mVideoState) {
            case PLAYER_STATE_STARTED:
                return true;
            case PLAYER_STATE_IDLE:
            case PLAYER_STATE_STOPPED:
            case PLAYER_STATE_PAUSED:
            case PLAYER_STATE_RELEASED:
            case PLAYER_STATE_UNKNOWN:
            default:
                return false;
        }
    }
    public void setDataSourceFile(String path) {
        final File videoFile = new File(path);
        FileInputStream inputStream = null;
        try {
            if (videoFile.exists()) {
                inputStream = new FileInputStream(videoFile);
                FileDescriptor fd = inputStream.getFD();
                mMediaExtractorVideo.setDataSource(fd);
            }
            changePlayerState(PlaybackConfiguration.PlayState.PLAYER_STATE_IDLE);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != inputStream) {
                try {
                    inputStream.close();
                    inputStream = null;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void setDataSourceHttp(String httpPath) {
        try {
            mMediaExtractorVideo.setDataSource(httpPath);
            changePlayerState(PlaybackConfiguration.PlayState.PLAYER_STATE_IDLE);
        } catch (IOException e) {
            INITSUCCESS = false;
            e.printStackTrace();
        }
    }


    private void createMediaDecoderVideo() {
        try {
            mVideoTrackIndex = getTrack();
            if (mVideoTrackIndex < 0) {
                INITSUCCESS = false;
                return;
            }
            mMediaExtractorVideo.selectTrack(mVideoTrackIndex);
            mVideoMediaFormat = mMediaExtractorVideo.getTrackFormat(mVideoTrackIndex);
            //the decoder decodes the frame to the specified format.
            mVideoMediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, DECODERCOLORFORMAT);
            mVideoMime = mVideoMediaFormat.getString(MediaFormat.KEY_MIME);
            mFrameRate = (mVideoMediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE) > DEFAULTRATE)?
                    mVideoMediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE) : DEFAULTRATE;

            mMediaDecoderVideo = MediaCodec.createDecoderByType(mVideoMime);
            INITSUCCESS = true;
        } catch (IOException | NullPointerException e) {
            INITSUCCESS = false;
            throw new RuntimeException("create MediaDecoderVideo failed", e);
        }
    }

    private void updateVideoFrame() throws IllegalStateException, IllegalArgumentException{
        mOutputVideoFrameCount = 0;
        mInputEOS = false;
        mOutputEOS = false;
        mMediaDecoderVideo.setCallback(mVideoDecoderCallback, mVideoPlayerHandler);
        mMediaDecoderVideo.configure(mVideoMediaFormat,null,null,0);
        synchronized (BaseAV.getBaseAV().mSyncLock.get(mUrlStr)) {
            BaseAV.getBaseAV().mSyncLock.get(mUrlStr).notifyAll();
        }
        changePlayerState(PlaybackConfiguration.PlayState.PLAYER_STATE_STARTED);
        setReady(true);
        mMediaDecoderVideo.start();
    }

    private void updateVideoFrameReaderAsync() throws IllegalStateException, IllegalArgumentException{
        mOutputVideoFrameCount = 0;
        mInputEOS = false;
        mOutputEOS = false;
        mWidth = mVideoMediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        mHeight = mVideoMediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
        mImageReader = ImageReader.newInstance(mWidth, mHeight, ImageFormat.YUV_420_888, MAX_IMAGES);
        mReaderSurface = mImageReader.getSurface();
        mImageReader.setOnImageAvailableListener(mImageListener, mVideoPlayerHandler);
        mMediaDecoderVideo.setCallback(mCallbackReader, mVideoPlayerHandler);
        mMediaDecoderVideo.configure(mVideoMediaFormat, mReaderSurface, null, 0);

        synchronized (BaseAV.getBaseAV().mSyncLock.get(mUrlStr)) {
            BaseAV.getBaseAV().mSyncLock.get(mUrlStr).notifyAll();
        }
        changePlayerState(PlaybackConfiguration.PlayState.PLAYER_STATE_STARTED);
        setReady(true);
        mMediaDecoderVideo.start();
    }

    private MediaCodec.Callback mCallbackReader = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
            if (!mInputEOS && isActive()) {
                if (index > 0) {
                    try {
                        ByteBuffer inputBuffer = codec.getInputBuffer(index);
                        int sampleSize = mMediaExtractorVideo.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(index, 0, 0,
                                    0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            mInputEOS = true;
                        } else {
                            presentationTimeUS = mMediaExtractorVideo.getSampleTime();
                            codec.queueInputBuffer(index, 0, sampleSize,
                                    presentationTimeUS, 0);
                            mMediaExtractorVideo.advance();
                        }
                    } catch (IllegalStateException ise) {
                        ise.printStackTrace();
                    }
                }
            }
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
            if (!mOutputEOS && isActive()) {
                if (index >= 0) {
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        mOutputEOS = true;
                        mVideoPlayerHandler.sendEmptyMessage(LOOP_PLAYING);
                        return;
                    }
                    boolean doRender = (info.size != 0);
                    long mCurrentFrameTime =0;
                    if (doRender) {
                        Image image = null;
                        mOutputVideoFrameCount++;
                        try {
                            long diff = 0;
                            long syncThreshold  = 0;
                            float delay = (1.0f / mFrameRate) * 1000;
                            mCurrentFrameTime = System.currentTimeMillis();
                            codec.releaseOutputBuffer(index, true);
                            if (BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).isHasAudio()) {
                                if (BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).getMasterClock() == 0) {
                                    synchronized (BaseAV.getBaseAV().mSyncLock.get(mUrlStr)) {
                                        BaseAV.getBaseAV().mSyncLock.get(mUrlStr).notifyAll();
                                    }
                                }
                                diff = (info.presentationTimeUs - BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).getMasterClock()) / 1000;
                                syncThreshold  = BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).computeTargetDelay(mFrameDuration);
                                if (diff <= -syncThreshold || diff < 0) {
                                    delay = Math.max(0, delay + diff);
                                } else if (diff > syncThreshold) {
                                    delay = Math.min(delay + diff, syncThreshold);
                                }
                            }
                            Thread.sleep((long)delay);
                            if (mOutputEOS) {
                                return;
                            }
                            image = mImageListener.getImage((long)delay);
                            if (null != image){
                                BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).updateVideoFrame(image, mUrlStr,mOutputVideoFrameCount, mWidth, mHeight);
                            }
                        } catch (InterruptedException | IllegalStateException e) {
                            e.printStackTrace();
                        } finally {
                            if (image != null) {
                                image.close();
                            }
                        }
                        mFrameDuration = System.currentTimeMillis() - mCurrentFrameTime;
                    }
                }
            }
        }

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
            changePlayerState(PlaybackConfiguration.PlayState.PLAYER_STATE_UNKNOWN);
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
        }
    };

    private static class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
        private final LinkedBlockingQueue<Image> mQueue =
                new LinkedBlockingQueue<Image>();
        @Override
        public void onImageAvailable(ImageReader reader) {
            try {
                mQueue.put(reader.acquireNextImage());
            }catch (InterruptedException | IllegalStateException e) {
                e.printStackTrace();
            }
        }

        public Image getImage(long timeout) throws InterruptedException {
            Image image = mQueue.poll(timeout, TimeUnit.MILLISECONDS);
            return image;
        }
    }

    private MediaCodec.Callback mVideoDecoderCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
            if (!mInputEOS && isActive()) {
                if (index > 0) {
                    try {
                        ByteBuffer inputBuffer = codec.getInputBuffer(index);
                        inputBuffer.clear();
                        int sampleSize = mMediaExtractorVideo.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(index, 0, 0,
                                    0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            mInputEOS = true;
                        } else {
                            presentationTimeUS = mMediaExtractorVideo.getSampleTime();
                            codec.queueInputBuffer(index, 0, sampleSize,
                                    presentationTimeUS, 0);
                            mMediaExtractorVideo.advance();
                        }
                    } catch (IllegalStateException ise) {
                        ise.printStackTrace();
                    }
                }
            }
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
            if (!mOutputEOS && isActive()) {
                float delay = (1.0f / mFrameRate) * 1000;
                mWidth = mVideoMediaFormat.getInteger(MediaFormat.KEY_WIDTH);
                mHeight = mVideoMediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
                if (index >= 0) {
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        mOutputEOS = true;
                        mVideoPlayerHandler.sendEmptyMessage(LOOP_PLAYING);
                        return;
                    }
                    boolean doRender = (info.size != 0);
                    long mCurrentFrameTime =0;
                    if (doRender) {
                        try {
                            mOutputVideoFrameCount++;
                            // the output image is null ,if the codec was configured with an output surface.
                            Image image = codec.getOutputImage(index);
                            if (null != image) {
                                long diff = 0;
                                long syncThreshold = 0;
                                if (BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).isHasAudio()) {
                                    if (BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).getMasterClock() == 0) {
                                        synchronized (BaseAV.getBaseAV().mSyncLock.get(mUrlStr)) {
                                            BaseAV.getBaseAV().mSyncLock.get(mUrlStr).notifyAll();
                                        }
                                    }
                                    diff = (info.presentationTimeUs - BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).getMasterClock()) / 1000;
                                    mCurrentFrameTime = System.currentTimeMillis();
                                    syncThreshold = BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).computeTargetDelay(mFrameDuration);
                                    if (diff <= -syncThreshold || diff < 0) {
                                        delay = Math.max(0, delay + diff);
                                    } else if (diff > syncThreshold) {
                                        delay = Math.min(delay + diff, syncThreshold);
                                    }
                                }
//                            Log.Log(Log.LOG_INFO, TAG + ",onOutputBufferAvailable, isHasAudio() :"
//                                    + BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).isHasAudio()
//                                    + ",mVideoClock=" + info.presentationTimeUs +",mAudioClock="
//                                    + BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).getMasterClock()
//                                    + ",diff =" + diff + ",syncThreshold  ="+syncThreshold  +",delay="+delay
//                                    +",mHeight="+mHeight +",mWidth="+mWidth);
                                Thread.sleep((long) delay);
                                if (mOutputEOS) {
                                    return;
                                }
                                BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).updateVideoFrame(image, mUrlStr, mOutputVideoFrameCount, mWidth, mHeight);
                                mImageFormat = image.getFormat();
                                image.close();
                            }
                            codec.releaseOutputBuffer(index, false);
                        } catch (IllegalStateException | InterruptedException e) {
                            e.printStackTrace();
                        }
                        mFrameDuration = System.currentTimeMillis() - mCurrentFrameTime;
                    }
                }
            }
        }

        @Override
        public void onError(@NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
            changePlayerState(PlaybackConfiguration.PlayState.PLAYER_STATE_UNKNOWN);
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
//            mVideoMediaFormat = format;
        }
    };

    @Override
    public int getTrack() {
        int result = -1;
        int numTracks = mMediaExtractorVideo.getTrackCount();
        for (int index = 0; index < numTracks; index++) {
            MediaFormat mediaFormat = mMediaExtractorVideo.getTrackFormat(index);
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                return result = index;
            } else {
            }
        }
        return result;
    }

    private void updateVideoFrameReaderSync() {
        mOutputVideoFrameCount = 0;
        mInputEOS = false;
        mOutputEOS = false;
        mWidth = mVideoMediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        mHeight = mVideoMediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
        mImageReader = ImageReader.newInstance(mWidth, mHeight, ImageFormat.YUV_420_888, MAX_IMAGES);
        mReaderSurface = mImageReader.getSurface();
        mImageReader.setOnImageAvailableListener(mImageListener, mVideoPlayerHandler);
        mMediaDecoderVideo.configure(mVideoMediaFormat, mReaderSurface, null, 0);

        synchronized (BaseAV.getBaseAV().mSyncLock.get(mUrlStr)) {
            BaseAV.getBaseAV().mSyncLock.get(mUrlStr).notifyAll();
        }
        changePlayerState(PlaybackConfiguration.PlayState.PLAYER_STATE_STARTED);
        setReady(true);
        mMediaDecoderVideo.start();
        float delay = (1.0f / mFrameRate) * 1000;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long mCurrentFrameTime =0;
        while (!mInputEOS && !mOutputEOS) {
            mCurrentFrameTime = System.currentTimeMillis();

            int inputBufIndex = mMediaDecoderVideo.dequeueInputBuffer(10000);
            if (inputBufIndex >= 0) {
                ByteBuffer dstBuf = mMediaDecoderVideo.getInputBuffer(inputBufIndex);
                int sampleSize = mMediaExtractorVideo.readSampleData(dstBuf, 0);
                long presentationTimeUs = mMediaExtractorVideo.getSampleTime();
                if (sampleSize < 0) {
                    mMediaDecoderVideo.queueInputBuffer(inputBufIndex, 0, 0,
                            0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    mInputEOS = true;
                } else {
                    presentationTimeUS = mMediaExtractorVideo.getSampleTime();
                    mMediaDecoderVideo.queueInputBuffer(inputBufIndex, 0, sampleSize,
                            presentationTimeUs, 0);
                    mMediaExtractorVideo.advance();
                }
            }

            int res = mMediaDecoderVideo.dequeueOutputBuffer(info, 10000);
            if (res == MediaCodec.INFO_TRY_AGAIN_LATER) {
                Log.d(TAG ,"no output frame available.");
            } else {
                //cannot be obtained the flag of End-of-Stream,..Synchronous Processing....
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    mOutputEOS = true;
                    mVideoPlayerHandler.sendEmptyMessage(LOOP_PLAYING);
                    break;
                }
                boolean doRender = (info.size != 0);
                if (doRender) {
                    Image image = null;
                    long diff = 0;
                    long syncThreshold  = 0;
                    mOutputVideoFrameCount++;
                    try {
                        mMediaDecoderVideo.releaseOutputBuffer(res, true);

                        if (BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).isHasAudio()) {
                            if (BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).getMasterClock() == 0) {
                                synchronized (BaseAV.getBaseAV().mSyncLock.get(mUrlStr)) {
                                    BaseAV.getBaseAV().mSyncLock.get(mUrlStr).notifyAll();
                                }
                            }
                            diff = (info.presentationTimeUs - BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).getMasterClock()) / 1000;
                            syncThreshold  = BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).computeTargetDelay(mFrameDuration);
                            if (diff <= -syncThreshold || diff < 0) {
                                delay = Math.max(0, delay + diff);
                            } else if (diff > syncThreshold) {
                                delay = Math.min(delay + diff, syncThreshold);
                            }
                        }
                        Thread.sleep((long)delay);
                        image = mImageListener.getImage((long)delay);
                        if (image != null) {
                            BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).updateVideoFrame(image, mUrlStr, mOutputVideoFrameCount, mWidth, mHeight);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                    mFrameDuration = System.currentTimeMillis() - mCurrentFrameTime;
                    if (mInputEOS) {
                        mOutputEOS = true;
                        mVideoPlayerHandler.sendEmptyMessage(LOOP_PLAYING);
                    }
                }
            }
        }
    }

    public void destroy() {
        if (mVideoState != PlaybackConfiguration.PlayState.PLAYER_STATE_STOPPED) {
            stop();
        }
        changePlayerState(PlaybackConfiguration.PlayState.PLAYER_STATE_RELEASED);
        if (null != mMediaExtractorVideo) {
            mMediaExtractorVideo.release();
            mMediaExtractorVideo = null;
        }
        if (null != mMediaDecoderVideo) {
            mMediaDecoderVideo.stop();
            mMediaDecoderVideo.release();
            mMediaDecoderVideo = null;
        }
        BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).destroy();
        mVideoMediaFormat = null;
        mVideoPlayerHandler = null;
    }
}
