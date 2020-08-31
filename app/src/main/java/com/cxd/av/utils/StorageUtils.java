package com.cxd.av.utils;

import android.content.Context;

import java.io.File;
import java.io.IOException;

public class StorageUtils {
    public static final String STORAGE_DIR = "/sdcard/AV/";

    public static void makeDirs(String path) {
        File file = new File(path);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    // /storage/emulated/0/Android/data/com.cxd.av/files;
    public static String getSandboxPath(Context context, String type) {
        // /storage/emulated/0/Android/data/com.cxd.av/files;
        File dir  = context.getExternalFilesDir(type);
        return dir.getAbsolutePath() + File.separator;
    }

}
