package com.cxd.av.audiodecoder;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;

import com.cxd.av.base.BaseAV;
import com.cxd.av.utils.PlaybackConfiguration;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class AudioPlayer extends BaseAV {
    private final String TAG = AudioPlayer.class.getSimpleName();
    private String mUrlStr = null;
    private String mFileStr = null;
    private MediaExtractor mMediaExtractorAudio;
    private MediaCodec mMediaDecoderAudio;
    private int mAudioTrackIndex;
    private byte[] mChunk = null;
    private int mChunkSize = 0;
    private MediaFormat mAudioMediaFormat;
    private String mAudioMime;
    private AudioTrack mAudioTrack;
    private boolean mInputEOS = false;
    private boolean mOutputEOS = false;
    private boolean INITSUCCESS = false;
    private static final int LOOP_PLAYING = 1;
    private static final int STOP_PLAYING = 2;
    private static final int RESUME_PLAYING = 3;
    public long mAudioClock = 0;
    private long mSampleTimeUs = 0;
    private AudioPlayerHandler mAudioPlayerHandler;
    private HandlerThread mAudioHandlerThread;
    private Context mContext;
    private PlaybackConfiguration.PlayState mAudioState = PlaybackConfiguration.PlayState.PLAYER_STATE_IDLE;
    private final Object mStateSyncLock = new Object();

    public AudioPlayer(Context context, String uri, int numbers) {
        mContext = context;
        mUrlStr = uri;
        mAudioHandlerThread = new HandlerThread("AudioPlay-"+numbers);
        mAudioHandlerThread.start();
        mAudioPlayerHandler = new AudioPlayerHandler(mAudioHandlerThread.getLooper());
        BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).setHasAudio(false);
    }
    private class AudioPlayerHandler extends Handler {
        public AudioPlayerHandler(Looper looper){
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case LOOP_PLAYING:
                    if (isLooping) {
                        mMediaDecoderAudio.reset();
                        start();
                    }
                    return;
                case STOP_PLAYING:
                    mAudioHandlerThread.quitSafely();
                    mAudioHandlerThread = null;
                    mAudioPlayerHandler.removeCallbacksAndMessages(null);
                    mContext = null;
                    destroy();
                    return;
                case RESUME_PLAYING:
                    if (mMediaExtractorAudio != null && mSampleTimeUs != 0) {
                        submitTask(audioPlay);
                        mMediaExtractorAudio.seekTo(mSampleTimeUs / 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                    }
                    return;
                default:
                    break;
            }
        }
    }

    private void init() {
        mMediaExtractorAudio = new MediaExtractor();
        mFileStr = getBaseAV().mPlaybackConfiguration.getFileFromUri(mUrlStr);
        if (null == mFileStr) {
            setDataSourceHttp(mUrlStr);
        } else {
            setDataSourceFile(mFileStr);
        }
        createMediaDecoderAudio();
    }
    Runnable audioPlay = new Runnable() {
        @Override
        public void run() {
            init();
            if (INITSUCCESS) {
                mInputEOS = false;
                mOutputEOS = false;
                try {
                    startAudioPlay();
                } catch (IllegalStateException ise) {
                }
            }
        }
    };

    public void start() {
        submitTask(audioPlay);
    }

    @Override
    public void resume() {
        if (willActive()) {
            mAudioPlayerHandler.sendEmptyMessageDelayed(RESUME_PLAYING, 100);
        }
    }

    @Override
    public void pause() {
        changePlayerState(PlaybackConfiguration.PlayState.PLAYER_STATE_PAUSED);
    }

    public void stop() {
        mInputEOS = true;
        mOutputEOS = true;
        mSampleTimeUs = 0;
        changePlayerState(PlaybackConfiguration.PlayState.PLAYER_STATE_STOPPED);
        mAudioPlayerHandler.sendEmptyMessageDelayed(STOP_PLAYING, 200);
    }

    private void changePlayerState(PlaybackConfiguration.PlayState currentState) {
        synchronized(mStateSyncLock) {
            mAudioState = currentState;
        }
    }

    public boolean willActive() {
        switch (mAudioState) {
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
        switch (mAudioState) {
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
    @Override
    public void setDataSourceHttp(String httpPath) {
        try {
            mMediaExtractorAudio.setDataSource(httpPath);
            changePlayerState(PlaybackConfiguration.PlayState.PLAYER_STATE_IDLE);
        } catch (IOException e) {
            INITSUCCESS = false;
        }
    }

    public void setDataSourceFile(String path) {
        final File videoFile = new File(path);
        FileInputStream inputStream = null;
        try {
            if (videoFile.exists()) {
                inputStream = new FileInputStream(videoFile);
                FileDescriptor fd = inputStream.getFD();
                mMediaExtractorAudio.setDataSource(fd);
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

    private void createMediaDecoderAudio() {
        mAudioTrackIndex = getTrack();
        if (mAudioTrackIndex < 0) {
            INITSUCCESS = false;
            return;
        }
        try {
            mMediaExtractorAudio.selectTrack(mAudioTrackIndex);
            mAudioMediaFormat = mMediaExtractorAudio.getTrackFormat(mAudioTrackIndex);
            mAudioMime = mAudioMediaFormat.getString(MediaFormat.KEY_MIME);

            mMediaDecoderAudio = MediaCodec.createDecoderByType(mAudioMime);
            mMediaDecoderAudio.setCallback(mAudioCallback, mAudioPlayerHandler);
            mMediaDecoderAudio.configure(mAudioMediaFormat,null,null,0);
            INITSUCCESS = true;
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            INITSUCCESS = false;
        }
    }


    private void startAudioPlay() throws IllegalStateException{
        int sampleRate = mAudioMediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channelConfig = (mAudioMediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1 ?
                AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO);
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        mAudioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize,
                AudioTrack.MODE_STREAM
        );
        synchronized (BaseAV.getBaseAV().mSyncLock.get(mUrlStr)) {
            try {
                BaseAV.getBaseAV().mSyncLock.get(mUrlStr).wait(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).setHasAudio(true);
        changePlayerState(PlaybackConfiguration.PlayState.PLAYER_STATE_STARTED);
        mAudioTrack.play();
        mMediaDecoderAudio.start();
    }

    private MediaCodec.Callback mAudioCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
//            Log.Log(Log.LOG_DEBUG,TAG +",onInputBufferAvailable, audioplay, thread name :"
//                    + Thread.currentThread().getName() + ",index="+index);
            if (!mInputEOS && isActive()) {
                if (index > 0) {
                    try {
                        ByteBuffer buffer = codec.getInputBuffer(index);
                        buffer.clear();
                        int size = mMediaExtractorAudio.readSampleData(buffer, 0);
                        if (size < 0) {
                            mMediaDecoderAudio.queueInputBuffer(index, 0, 0,
                                    0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            mInputEOS = true;
                        } else {
                            mSampleTimeUs = mMediaExtractorAudio.getSampleTime();
                            mMediaDecoderAudio.queueInputBuffer(index, 0, size, mSampleTimeUs, 0);
                            mMediaExtractorAudio.advance();
                        }
                    } catch (IllegalStateException ise) {

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
                        BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).setMasterClock(0);
                        mAudioPlayerHandler.sendEmptyMessage(LOOP_PLAYING);
                        return;
                    }
                    try {
                        if (info.size > 0) {
                            ByteBuffer buffer = codec.getOutputBuffer(index);
                            if (mChunkSize != info.size) {
                                mChunkSize = info.size;
                                mChunk = new byte[mChunkSize];
                            }
                            buffer.get(mChunk);
                            buffer.clear();
                            mAudioClock = info.presentationTimeUs;
                            BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).setMasterClock(mAudioClock);
                            mAudioTrack.write(mChunk, info.offset, info.offset + info.size);
                        }
                        codec.releaseOutputBuffer(index, false);
                    } catch (IllegalStateException ise) {

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
            //mAudioTrack.setPlaybackRate(format.getInteger(MediaFormat.KEY_SAMPLE_RATE));
        }
    };

    @Override
    public int getTrack() {
        int result = -1;
        int numTracks = mMediaExtractorAudio.getTrackCount();
        for (int index = 0; index < numTracks; index++) {
            MediaFormat mediaFormat = mMediaExtractorAudio.getTrackFormat(index);
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).setHasAudio(true);

                return result = index;
            } else {
                BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).setHasAudio(false);
            }
        }
        return result;
    }

    public void destroy() {
        if (mAudioState != PlaybackConfiguration.PlayState.PLAYER_STATE_STOPPED) {
            stop();
        }
        changePlayerState(PlaybackConfiguration.PlayState.PLAYER_STATE_RELEASED);
        if (null != mMediaExtractorAudio) {
            mMediaExtractorAudio.release();
            mMediaExtractorAudio = null;
        }
        if (null != mAudioTrack) {
            mAudioTrack.stop();
            mAudioTrack.release();
            mAudioTrack = null;
        }
        if (null != mMediaDecoderAudio) {
            mMediaDecoderAudio.stop();
            mMediaDecoderAudio.release();
            mMediaDecoderAudio = null;
        }
        BaseAV.getBaseAV().mUriAVUtils.get(mUrlStr).destroy();
        mAudioMediaFormat = null;
        mAudioPlayerHandler = null;
    }
}
