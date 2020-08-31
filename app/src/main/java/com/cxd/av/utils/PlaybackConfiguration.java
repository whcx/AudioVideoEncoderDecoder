package com.cxd.av.utils;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.SystemClock;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.ArrayMap;

import com.cxd.av.base.BaseAV;

import java.io.File;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class PlaybackConfiguration {
    private final String TAG = PlaybackConfiguration.class.getSimpleName();
    private final Context mContext;
    private final TelephonyManager mTelephonyManager;
    private final PhoneStateChangeListener mPhoneStateListener = new PhoneStateChangeListener();
    private Map<String, File> mUrlLocalFileMap = new ArrayMap<>();
    private DownloadManager mDownLoadManager = null;
    private boolean mDownLoadSuccess;
    private final int WAIT_FOR_DOWNLOAD_POLL_TIME = 1 * 1000;  // 1 second
    private final int MAX_WAIT_FOR_DOWNLOAD_TIME = 3 * 60 * 1000; // 3 minute

    public PlaybackConfiguration(Context context) {
        mContext = context;
        mTelephonyManager = (TelephonyManager)mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mDownLoadManager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
        initializeTelephonyListeners();
    }

    private void initializeTelephonyListeners() {
        mTelephonyManager.listen(mPhoneStateListener.init(), PhoneStateListener.LISTEN_CALL_STATE);
    }

    private void clearConfiguration() {
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
    }

    public void destroy() {
        clearConfiguration();
        mUrlLocalFileMap.forEach((uri, file) -> {
            if (file.exists())
                file.delete();
        });
    }


    public enum PlayState {
        PLAYER_STATE_UNKNOWN,
        PLAYER_STATE_RELEASED,
        PLAYER_STATE_IDLE,
        PLAYER_STATE_STARTED,
        PLAYER_STATE_PAUSED,
        PLAYER_STATE_STOPPED
    }

    public void downLoadFileFromUri(String uri) {
        String existFile = getFileFromUri(uri);
        if ((uri != null && !uri.equals("")) && (null == existFile)) {
            String fileName = uri.substring(uri.lastIndexOf("/") + 1);
            String localDir = mContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES).getPath();
            File localFile = new File(localDir, fileName);
            if (localFile.exists()) {
                mUrlLocalFileMap.put(uri, localFile);
                return;
            }
            Uri serverUri = Uri.parse(uri);
            DownloadManager.Request request = new DownloadManager.Request(serverUri);
            Uri localUri = Uri.fromFile(localFile);
            request.setDestinationUri(localUri);
            long dlRequest = mDownLoadManager.enqueue(request);
            mDownLoadSuccess = false;
            doWaitForDownloadOrTimeout(new DownloadManager.Query().setFilterById(dlRequest),
                    WAIT_FOR_DOWNLOAD_POLL_TIME, MAX_WAIT_FOR_DOWNLOAD_TIME);
            if (mDownLoadSuccess) {
                mUrlLocalFileMap.put(uri, localFile);
            }
        }
    }

    public String getFileFromUri(String uri) {
        File localFile = mUrlLocalFileMap.get(uri);
        if ((null != localFile) && localFile.exists() && localFile.canRead()) {
            return localFile.getAbsolutePath();
        }
        return null;
    }

    private void doWaitForDownloadOrTimeout(DownloadManager.Query query, long poll, long timeoutMillis) {
        int currentWaitTime = 0;
        while (true) {
            query.setFilterByStatus(DownloadManager.STATUS_PENDING |
                    DownloadManager.STATUS_PAUSED | DownloadManager.STATUS_RUNNING);
            Cursor cursor = mDownLoadManager.query(query);
            try {
                if (cursor.getCount() ==0) {
                    mDownLoadSuccess = true;
                    break;
                }
                currentWaitTime = timeoutWait(currentWaitTime, poll, timeoutMillis,
                        "Timed out waiting for download to finish.");
            } catch (TimeoutException e){
                e.printStackTrace();
            } finally {
                cursor.close();
            }
        }
    }

    private int timeoutWait(int currentTotalWaitTime, long poll, long maxTimeoutMillis,
                            String timeOutMsg) throws TimeoutException{
        long now = SystemClock.elapsedRealtime();
        long end = now + poll;
        while (now < end) {
            try {
                Thread.sleep(end - now);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            now = SystemClock.elapsedRealtime();
        }
        currentTotalWaitTime += poll;
        if (currentTotalWaitTime > maxTimeoutMillis) {
            throw new TimeoutException(timeOutMsg);
        }
        return currentTotalWaitTime;
    }

    private final class PhoneStateChangeListener extends PhoneStateListener {
        private int mPhoneCallState;
        PhoneStateChangeListener init() {
            mPhoneCallState = -1;
            return this;
        }

        @Override
        public void onCallStateChanged(int state, String ignored) {
            if (mPhoneCallState == -1) {
                mPhoneCallState = state;
            }
            // call active.
            if (state != TelephonyManager.CALL_STATE_IDLE && state != mPhoneCallState) {
                BaseAV.getBaseAV().mUriSet.forEach((uri) -> {
                    BaseAV.getBaseAV().stop(uri);
                });
            }
        }
    }
}
