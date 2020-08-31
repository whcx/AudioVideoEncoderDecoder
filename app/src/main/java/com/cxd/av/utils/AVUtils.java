package com.cxd.av.utils;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class AVUtils {
    private final static String TAG = AVUtils.class.getSimpleName();
    public final static int COLOR_FormatI420 = 1;
    public final static int COLOR_FormatNV21 = 2;
    private int mValidPixWidth = 0;
    private int mValidPixHeight = 0;
    private int index = 0;
    private int MAX_BUFFERS = 4; //In sync with MAX_NUM of FrameQueue.h
    private int frameSize = 0;
    private ArrayList<byte[]> buffers = new ArrayList<byte[]>();
    private int rowDataSize = 0;
    private byte[] rowData = null;
    private long mMasterClock = 0;
    private boolean mHasAudio = false;
    public final long AV_SYNC_THRESHOLD_MIN = 30; //0.03s
    public final long AV_SYNC_THRESHOLD_MAX = 60; //0.06s

    public AVUtils() {
    }

    private void allocFrameBuffer(int dataSize) {
        if (null == buffers) {
            buffers = new ArrayList<byte[]>();
        }
        for (int i =0; i < MAX_BUFFERS; i++) {
            buffers.add(new byte[dataSize]);
        }
    }
    private void freeFrameBuffer() {
        frameSize = 0;
        if (null != buffers) {
            buffers.clear();
            buffers = null;
        }
    }
    //Check android image format validity for an image, only support below formats:
    private boolean checkImageFormatSupport(Image image) {
        int format = image.getFormat();
        switch (format) {
            case ImageFormat.YUV_420_888:
            case ImageFormat.NV21:
            case ImageFormat.YV12:
                return true;
        }
        return false;
    }

    private byte[] getDataFromImage(Image image, int colorFormat){
        if (!checkImageFormatSupport(image)) {
            return null;
        }

        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = mValidPixWidth = crop.width();
        int height = mValidPixHeight = crop.height();
        Image.Plane[] planes = image.getPlanes();
        int dataIndex = index++ % MAX_BUFFERS;
        int dataSize = width * height * ImageFormat.getBitsPerPixel(format) / 8;
        if (frameSize != dataSize) {
            frameSize = dataSize;
            allocFrameBuffer(dataSize);
        }

        if (rowDataSize != planes[0].getRowStride()) {
            rowDataSize = planes[0].getRowStride();
            rowData = new byte[rowDataSize];
        }
//        Log.Log(Log.LOG_INFO,TAG +",get data from " + planes.length + " planes"+",data="+buffers.get(dataIndex).length+",crop="+crop);
        int channelOffset = 0;
        int outputStride = 1;
        for (int i = 0; i < planes.length; i++) {
            switch (i) {
                case 0:
                    channelOffset = 0;
                    outputStride = 1;
                    break;
                case 1:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = width * height;
                        outputStride = 1;
                    } else if (colorFormat == COLOR_FormatNV21) {
                        channelOffset = width * height + 1;
                        outputStride = 2;
                    }
                    break;
                case 2:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = (int) (width * height * 1.25);
                        outputStride = 1;
                    } else if (colorFormat == COLOR_FormatNV21) {
                        channelOffset = width * height;
                        outputStride = 2;
                    }
                    break;
            }
            ByteBuffer buffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();

//            Log.Log(Log.LOG_INFO,TAG +"pixelStride " + pixelStride);
//            Log.Log(Log.LOG_INFO,TAG +"rowStride " + rowStride);
//            Log.Log(Log.LOG_INFO,TAG +"width " + width);
//            Log.Log(Log.LOG_INFO,TAG +"height " + height);
//            Log.Log(Log.LOG_INFO,TAG +"buffer size " + buffer.remaining());

            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w;
                    buffer.get(buffers.get(dataIndex), channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        buffers.get(dataIndex)[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
        }
        return buffers.get(dataIndex);
    }

    private void compressJPEG(String fileName, Image image) {
        FileOutputStream outputStream;
        try {
            outputStream = new FileOutputStream(fileName);
            Rect rect = image.getCropRect();
            YuvImage yuvImage = new YuvImage(getDataFromImage(image, COLOR_FormatNV21),
                    ImageFormat.NV21, rect.width(), rect.height(), null);
            yuvImage.compressToJpeg(rect, 100, outputStream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {

        }
    }

    private void dumpFrameFile(String fileName, byte[] data) {
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(fileName);
            outputStream.write(data);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void dumpI420File(Image image, int colorFormat,int frameCount, int width, int height) {
        String fileName = StorageUtils.STORAGE_DIR+ String.format("frame_%05d_I420_%d-%d.yuv",frameCount,width,height);
        dumpFrameFile(fileName, getDataFromImage(image, colorFormat));
    }

    public void dumpJpegFile(Image image, int frameCount) {
        String fileName = StorageUtils.STORAGE_DIR + String.format("frame_%05d.jpg", frameCount);
        File file = new File(fileName);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        compressJPEG(fileName, image);
    }

    public void updateVideoFrame(Image image, String url, int frameCount, int width, int height) {
//        nativeUpdateVideoFrame(getDataFromImage(image, COLOR_FormatI420),url, mValidPixWidth,mValidPixHeight,true);
        dumpJpegFile(image, frameCount);
    }

    public void setMasterClock(long clock) {
        mMasterClock = clock;
    }
    public long getMasterClock() {
        return mMasterClock;
    }

    public void setHasAudio(boolean flag) {
        mHasAudio = flag;
    }
    public boolean isHasAudio() {
        return mHasAudio;
    }

    public long computeTargetDelay(long lastDuration) {
        return Math.max(AV_SYNC_THRESHOLD_MIN, Math.min(AV_SYNC_THRESHOLD_MAX, lastDuration));
    }

    public void destroy() {
        freeFrameBuffer();
    }
    private native void nativeUpdateVideoFrame(byte[] src, String url, int width, int height,boolean flag);
}
