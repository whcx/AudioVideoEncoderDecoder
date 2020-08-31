package com.cxd.av.activity;

import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.TextView;

import com.cxd.av.R;
import com.cxd.av.base.BaseAV;
import com.cxd.av.utils.StorageUtils;

import java.io.File;

public class MainActivity extends AbstractPermissionActivity {

    // Used to load the 'av-lib' library on application startup.
    static {
        System.loadLibrary("av-lib");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Example of a call to a native method
        TextView tv = findViewById(R.id.sample_text);
        tv.setText(stringFromJNI());

        if (isPermissionGranted()) {
            init();
        }
    }

    private void init() {
        StorageUtils.makeDirs(StorageUtils.STORAGE_DIR);
        BaseAV baseAV = BaseAV.getBaseAV();
        baseAV.setContext(this);
        baseAV.setDataSourceHttp("http://vfx.mtime.cn/Video/2019/03/19/mp4/190319222227698228.mp4");
        baseAV.start("http://vfx.mtime.cn/Video/2019/03/19/mp4/190319222227698228.mp4");
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
}
