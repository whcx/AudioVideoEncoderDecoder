# AudioVideoEncoderDecoder
存储权限申请：
当前项目targetSdkVersion 29，按之前方式动态申请权限  Manifest.permission.READ_EXTERNAL_STORAGE,  Manifest.permission.WRITE_EXTERNAL_STORAGE,无法在sdcard的共有目录创建文件。
可以使用context.getExternalFilesDir(type);获取应用私有的文件目录，即：/storage/emulated/0/Android/data/com.cxd.av/files;
或者，manifest中添加android:requestLegacyExternalStorage="true"，依然使用共有目录。

1,使用MediaCodec解码视频文件，
