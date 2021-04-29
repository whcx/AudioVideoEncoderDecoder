package com.cxd.av.activity;

import android.os.Bundle;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.widget.TextView;

import com.cxd.av.R;
import com.cxd.av.base.BaseAV;
import com.cxd.av.utils.StorageUtils;
import com.cxd.av.views.GestureRecognizer;

import java.io.File;

public class MainActivity extends AbstractPermissionActivity implements GestureRecognizer.Listener{

    // Used to load the 'av-lib' library on application startup.
    static {
        System.loadLibrary("av-lib");
    }

    private GestureRecognizer mGestureRecognizer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Example of a call to a native method
        TextView tv = findViewById(R.id.sample_text);
        tv.setText(stringFromJNI() + stringFromJNITest());

        if (isPermissionGranted()) {
            init();
        }
        mGestureRecognizer = new GestureRecognizer(this, this);
    }

    private void init() {
        StorageUtils.makeDirs(StorageUtils.STORAGE_DIR);
        BaseAV baseAV = BaseAV.getBaseAV();
        baseAV.setContext(this);
        baseAV.setDataSourceHttp("http://vfx.mtime.cn/Video/2019/03/19/mp4/190319222227698228.mp4");
        baseAV.start("http://vfx.mtime.cn/Video/2019/03/19/mp4/190319222227698228.mp4");
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkExternalLegacy();
    }

    private static boolean checkExternalLegacy() {
        File dir = new File("/storage/emulated/0/LegacyStorage/files");
        if (!dir.isDirectory()) {
            dir.mkdirs();
            if (!dir.isDirectory()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
//        mRotationDetector.onTouchEvent(event);
        mGestureRecognizer.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    @Override
    protected void onGetPermissionsSuccess() {
        init();
    }

    @Override
    protected void onGetPermissionsFailure() {
        finish();
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
    public native String stringFromJNITest();

    @Override
    public boolean onSingleTapUp(float x, float y) {
        return false;
    }

    @Override
    public boolean onDoubleTap(float x, float y) {
        return false;
    }

    @Override
    public boolean onScroll(float dx, float dy, float totalX, float totalY) {
        return false;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }

    @Override
    public boolean onScaleBegin(float focusX, float focusY) {
        return false;
    }

    @Override
    public boolean onScale(float focusX, float focusY, float scale) {
        return false;
    }

    @Override
    public void onRotation(float angle) {

    }

    @Override
    public void onScaleEnd() {

    }

    @Override
    public void onDown(float x, float y) {

    }

    @Override
    public void onUp() {

    }
}
