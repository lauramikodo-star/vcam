package com.example.vcam;


import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.InputConfiguration;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookMain implements IXposedHookLoadPackage {
    public static Surface mSurface;
    public static SurfaceTexture mSurfacetexture;
    public static MediaPlayer mMediaPlayer;
    public static SurfaceTexture fake_SurfaceTexture;
    public static Camera origin_preview_camera;

    public static Camera camera_onPreviewFrame;
    public static Camera start_preview_camera;
    public static volatile byte[] data_buffer = {0};
    public static byte[] input;
    public static int mhight;
    public static int mwidth;
    public static boolean is_someone_playing;
    public static boolean is_hooked;
    public static VideoToFrames hw_decode_obj;
    public static VideoToFrames c2_hw_decode_obj;
    public static VideoToFrames c2_hw_decode_obj_1;
    public static SurfaceTexture c1_fake_texture;
    public static Surface c1_fake_surface;
    public static SurfaceHolder ori_holder;
    public static MediaPlayer mplayer1;
    public static Camera mcamera1;
    public int imageReaderFormat = 0;
    public static boolean is_first_hook_build = true;

    public static int onemhight;
    public static int onemwidth;
    public static Class camera_callback_calss;

    public static String video_path = "/storage/emulated/0/DCIM/Camera1/";

    public static Surface c2_preview_Surfcae;
    public static Surface c2_preview_Surfcae_1;
    public static Surface c2_reader_Surfcae;
    public static Surface c2_reader_Surfcae_1;
    public static MediaPlayer c2_player;
    public static MediaPlayer c2_player_1;
    public static Surface c2_virtual_surface;
    public static SurfaceTexture c2_virtual_surfaceTexture;
    public boolean need_recreate;
    public static CameraDevice.StateCallback c2_state_cb;
    public static CaptureRequest.Builder c2_builder;
    public static SessionConfiguration fake_sessionConfiguration;
    public static SessionConfiguration sessionConfiguration;
    public static OutputConfiguration outputConfiguration;
    public boolean need_to_show_toast = true;

    public static int c2_ori_width = 1280;
    public static int c2_ori_height = 720;

    public static Class c2_state_callback;

    public static volatile long stillCaptureStartTime = 0;
    public static final long STILL_CAPTURE_TIMEOUT_MS = 2000;
    
    // Track ImageReader instances and their expected formats to handle format mismatches
    public static java.util.Map<ImageReader, Integer> imageReaderFormats = new java.util.concurrent.ConcurrentHashMap<>();
    // Track ImageReader instances with their OnImageAvailableListener for still capture injection
    public static java.util.Map<ImageReader, ImageReader.OnImageAvailableListener> imageReaderListeners = new java.util.concurrent.ConcurrentHashMap<>();
    // Track which ImageReaders are for still capture (JPEG format)
    public static java.util.Set<ImageReader> stillCaptureReaders = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    // Track ImageReader surfaces and their formats - KEY FIX for format mismatch
    public static java.util.Map<Surface, Integer> surfaceFormats = new java.util.concurrent.ConcurrentHashMap<>();
    // Track ImageReader surfaces that should NOT receive video frames (JPEG/YUV formats)
    public static java.util.Set<Surface> incompatibleSurfaces = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    // Flag to indicate if a still capture is pending (needs fake image injection)
    public static AtomicBoolean pendingStillCapture = new AtomicBoolean(false);
    // Handler for posting callbacks
    public static Handler callbackHandler = null;
    // Track the original surfaces for still capture (JPEG/YUV format surfaces)
    public static java.util.Map<Surface, Surface> originalCaptureTargets = new java.util.concurrent.ConcurrentHashMap<>();
    // Track the capture session for direct capture handling
    public static CameraCaptureSession activeCaptureSession = null;
    // Track pending capture callbacks
    public static CameraCaptureSession.CaptureCallback pendingCaptureCallback = null;
    public Context toast_content;
    
    // Static image path for still capture replacement
    public static String still_image_path = null;
    
    // ImageReader format constants
    public static final int FORMAT_JPEG = 256;  // 0x100
    public static final int FORMAT_YUV_420_888 = 35;  // 0x23
    public static final int FORMAT_RAW_PRIVATE = 4096;  // 0x1000

    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Exception {
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "setPreviewTexture", SurfaceTexture.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                File file = new File(video_path + "virtual.mp4");
                if (file.exists()) {
                    File control_file = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "disable.jpg");
                    if (control_file.exists()){
                        return;
                    }
                    if (is_hooked) {
                        is_hooked = false;
                        return;
                    }
                    if (param.args[0] == null) {
                        return;
                    }
                    if (param.args[0].equals(c1_fake_texture)) {
                        return;
                    }
                    if (origin_preview_camera != null && origin_preview_camera.equals(param.thisObject)) {
                        param.args[0] = fake_SurfaceTexture;
                        XposedBridge.log("【VCAM】发现重复" + origin_preview_camera.toString());
                        return;
                    } else {
                        XposedBridge.log("【VCAM】创建预览");
                    }

                    origin_preview_camera = (Camera) param.thisObject;
                    mSurfacetexture = (SurfaceTexture) param.args[0];
                    if (fake_SurfaceTexture == null) {
                        fake_SurfaceTexture = new SurfaceTexture(10);
                    } else {
                        fake_SurfaceTexture.release();
                        fake_SurfaceTexture = new SurfaceTexture(10);
                    }
                    param.args[0] = fake_SurfaceTexture;
                } else {
                    File toast_control = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "no_toast.jpg");
                    need_to_show_toast = !toast_control.exists();
                    if (toast_content != null && need_to_show_toast) {
                        try {
                            Toast.makeText(toast_content, "不存在替换视频\n" + lpparam.packageName + "当前路径：" + video_path, Toast.LENGTH_SHORT).show();
                        } catch (Exception ee) {
                            XposedBridge.log("【VCAM】[toast]" + ee.toString());
                        }
                    }
                }
            }
        });

        XposedHelpers.findAndHookMethod("android.hardware.camera2.CameraManager", lpparam.classLoader, "openCamera", String.class, CameraDevice.StateCallback.class, Handler.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[1] == null) {
                    return;
                }
                if (param.args[1].equals(c2_state_cb)) {
                    return;
                }
                c2_state_cb = (CameraDevice.StateCallback) param.args[1];
                c2_state_callback = param.args[1].getClass();
                File control_file = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "disable.jpg");
                if (control_file.exists()) {
                    return;
                }
                File file = new File(video_path + "virtual.mp4");
                File toast_control = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "no_toast.jpg");
                need_to_show_toast = !toast_control.exists();
                if (!file.exists()) {
                    if (toast_content != null && need_to_show_toast) {
                        try {
                            Toast.makeText(toast_content, "不存在替换视频\n" + lpparam.packageName + "当前路径：" + video_path, Toast.LENGTH_SHORT).show();
                        } catch (Exception ee) {
                            XposedBridge.log("【VCAM】[toast]" + ee.toString());
                        }
                    }
                    return;
                }
                XposedBridge.log("【VCAM】1位参数初始化相机，类：" + c2_state_callback.toString());
                is_first_hook_build = true;
                process_camera2_init(c2_state_callback);
            }
        });


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            XposedHelpers.findAndHookMethod("android.hardware.camera2.CameraManager", lpparam.classLoader, "openCamera", String.class, Executor.class, CameraDevice.StateCallback.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args[2] == null) {
                        return;
                    }
                    if (param.args[2].equals(c2_state_cb)) {
                        return;
                    }
                    c2_state_cb = (CameraDevice.StateCallback) param.args[2];
                    File control_file = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "disable.jpg");
                    if (control_file.exists()) {
                        return;
                    }
                    File file = new File(video_path + "virtual.mp4");
                    File toast_control = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "no_toast.jpg");
                    need_to_show_toast = !toast_control.exists();
                    if (!file.exists()) {
                        if (toast_content != null && need_to_show_toast) {
                            try {
                                Toast.makeText(toast_content, "不存在替换视频\n" + lpparam.packageName + "当前路径：" + video_path, Toast.LENGTH_SHORT).show();
                            } catch (Exception ee) {
                                XposedBridge.log("【VCAM】[toast]" + ee.toString());
                            }
                        }
                        return;
                    }
                    c2_state_callback = param.args[2].getClass();
                    XposedBridge.log("【VCAM】2位参数初始化相机，类：" + c2_state_callback.toString());
                    is_first_hook_build = true;
                    process_camera2_init(c2_state_callback);
                }
            });
        }


        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "setPreviewCallbackWithBuffer", Camera.PreviewCallback.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] != null) {
                    process_callback(param);
                }
            }
        });

        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "addCallbackBuffer", byte[].class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] != null) {
                    param.args[0] = new byte[((byte[]) param.args[0]).length];
                }
            }
        });

        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "setPreviewCallback", Camera.PreviewCallback.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] != null) {
                    process_callback(param);
                }
            }
        });

        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "setOneShotPreviewCallback", Camera.PreviewCallback.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] != null) {
                    process_callback(param);
                }
            }
        });

        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "takePicture", Camera.ShutterCallback.class, Camera.PictureCallback.class, Camera.PictureCallback.class, Camera.PictureCallback.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                XposedBridge.log("【VCAM】4参数拍照");
                if (param.args[1] != null) {
                    process_a_shot_YUV(param);
                }

                if (param.args[3] != null) {
                    process_a_shot_jpeg(param, 3);
                }
            }
        });

        XposedHelpers.findAndHookMethod("android.media.MediaRecorder", lpparam.classLoader, "setCamera", Camera.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                File toast_control = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "no_toast.jpg");
                need_to_show_toast = !toast_control.exists();
                XposedBridge.log("【VCAM】[record]" + lpparam.packageName);
                if (toast_content != null && need_to_show_toast) {
                    try {
                        Toast.makeText(toast_content, "应用：" + lpparam.appInfo.name + "(" + lpparam.packageName + ")" + "触发了录像，但目前无法拦截", Toast.LENGTH_SHORT).show();
                    }catch (Exception ee){
                        XposedBridge.log("【VCAM】[toast]" + Arrays.toString(ee.getStackTrace()));
                    }
                }
            }
        });

        XposedHelpers.findAndHookMethod("android.app.Instrumentation", lpparam.classLoader, "callApplicationOnCreate", Application.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                if (param.args[0] instanceof Application) {
                    try {
                        toast_content = ((Application) param.args[0]).getApplicationContext();
                    } catch (Exception ee) {
                        XposedBridge.log("【VCAM】" + ee.toString());
                    }
                    File force_private = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/private_dir.jpg");
                    if (toast_content != null) {//后半段用于强制私有目录
                        int auth_statue = 0;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                auth_statue += (toast_content.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) + 1);
                            } catch (Exception ee) {
                                XposedBridge.log("【VCAM】[permission-check]" + ee.toString());
                            }
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    auth_statue += (toast_content.checkSelfPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE) + 1);
                                }
                            } catch (Exception ee) {
                                XposedBridge.log("【VCAM】[permission-check]" + ee.toString());
                            }
                        }else {
                            if (toast_content.checkCallingPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ){
                                auth_statue = 2;
                            }
                        }
                        //权限判断完毕
                        if (auth_statue < 1 || force_private.exists()) {
                            File shown_file = new File(toast_content.getExternalFilesDir(null).getAbsolutePath() + "/Camera1/");
                            if ((!shown_file.isDirectory()) && shown_file.exists()) {
                                shown_file.delete();
                            }
                            if (!shown_file.exists()) {
                                shown_file.mkdir();
                            }
                            shown_file = new File(toast_content.getExternalFilesDir(null).getAbsolutePath() + "/Camera1/" + "has_shown");
                            File toast_force_file = new File(Environment.getExternalStorageDirectory().getPath()+ "/DCIM/Camera1/force_show.jpg");
                            if ((!lpparam.packageName.equals(BuildConfig.APPLICATION_ID)) && ((!shown_file.exists()) || toast_force_file.exists())) {
                                try {
                                    Toast.makeText(toast_content, lpparam.packageName+"未授予读取本地目录权限，请检查权限\nCamera1目前重定向为 " + toast_content.getExternalFilesDir(null).getAbsolutePath() + "/Camera1/", Toast.LENGTH_SHORT).show();
                                    FileOutputStream fos = new FileOutputStream(toast_content.getExternalFilesDir(null).getAbsolutePath() + "/Camera1/" + "has_shown");
                                    String info = "shown";
                                    fos.write(info.getBytes());
                                    fos.flush();
                                    fos.close();
                                } catch (Exception e) {
                                    XposedBridge.log("【VCAM】[switch-dir]" + e.toString());
                                }
                            }
                            video_path = toast_content.getExternalFilesDir(null).getAbsolutePath() + "/Camera1/";
                        }else {
                            video_path = Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/";
                        }
                    } else {
                        video_path = Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/";
                        File uni_DCIM_path = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/");
                        if (uni_DCIM_path.canWrite()) {
                            File uni_Camera1_path = new File(video_path);
                            if (!uni_Camera1_path.exists()) {
                                uni_Camera1_path.mkdir();
                            }
                        }
                    }
                }
            }
        });

        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "startPreview", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                File file = new File(video_path + "virtual.mp4");
                File toast_control = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "no_toast.jpg");
                need_to_show_toast = !toast_control.exists();
                if (!file.exists()) {
                    if (toast_content != null && need_to_show_toast) {
                        try {
                            Toast.makeText(toast_content, "不存在替换视频\n" + lpparam.packageName + "当前路径：" + video_path, Toast.LENGTH_SHORT).show();
                        } catch (Exception ee) {
                            XposedBridge.log("【VCAM】[toast]" + ee.toString());
                        }
                    }
                    return;
                }
                File control_file = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "disable.jpg");
                if (control_file.exists()) {
                    return;
                }
                is_someone_playing = false;
                XposedBridge.log("【VCAM】开始预览");
                start_preview_camera = (Camera) param.thisObject;
                if (ori_holder != null) {

                    if (mplayer1 == null) {
                        mplayer1 = new MediaPlayer();
                    } else {
                        mplayer1.release();
                        mplayer1 = null;
                        mplayer1 = new MediaPlayer();
                    }
                    if (!ori_holder.getSurface().isValid() || ori_holder == null) {
                        return;
                    }
                    mplayer1.setSurface(ori_holder.getSurface());
                    File sfile = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "no-silent.jpg");
                    if (!(sfile.exists() && (!is_someone_playing))) {
                        mplayer1.setVolume(0, 0);
                        is_someone_playing = false;
                    } else {
                        is_someone_playing = true;
                    }
                    mplayer1.setLooping(true);

                    mplayer1.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {
                            mplayer1.start();
                        }
                    });

                    try {
                        mplayer1.setDataSource(video_path + "virtual.mp4");
                        mplayer1.prepare();
                    } catch (IOException e) {
                        XposedBridge.log("【VCAM】" + e.toString());
                    }
                }


                if (mSurfacetexture != null) {
                    if (mSurface == null) {
                        mSurface = new Surface(mSurfacetexture);
                    } else {
                        mSurface.release();
                        mSurface = new Surface(mSurfacetexture);
                    }

                    if (mMediaPlayer == null) {
                        mMediaPlayer = new MediaPlayer();
                    } else {
                        mMediaPlayer.release();
                        mMediaPlayer = new MediaPlayer();
                    }

                    mMediaPlayer.setSurface(mSurface);

                    File sfile = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "no-silent.jpg");
                    if (!(sfile.exists() && (!is_someone_playing))) {
                        mMediaPlayer.setVolume(0, 0);
                        is_someone_playing = false;
                    } else {
                        is_someone_playing = true;
                    }
                    mMediaPlayer.setLooping(true);

                    mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {
                            mMediaPlayer.start();
                        }
                    });

                    try {
                        mMediaPlayer.setDataSource(video_path + "virtual.mp4");
                        mMediaPlayer.prepare();
                    } catch (IOException e) {
                        XposedBridge.log("【VCAM】" + e.toString());
                    }
                }
            }
        });

        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "setPreviewDisplay", SurfaceHolder.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("【VCAM】添加Surfaceview预览");
                File file = new File(video_path + "virtual.mp4");
                File toast_control = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "no_toast.jpg");
                need_to_show_toast = !toast_control.exists();
                if (!file.exists()) {
                    if (toast_content != null && need_to_show_toast) {
                        try {
                            Toast.makeText(toast_content, "不存在替换视频\n" + lpparam.packageName + "当前路径：" + video_path, Toast.LENGTH_SHORT).show();
                        } catch (Exception ee) {
                            XposedBridge.log("【VCAM】[toast]" + ee.toString());
                        }
                    }
                    return;
                }
                File control_file = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "disable.jpg");
                if (control_file.exists()) {
                    return;
                }
                mcamera1 = (Camera) param.thisObject;
                ori_holder = (SurfaceHolder) param.args[0];
                if (c1_fake_texture == null) {
                    c1_fake_texture = new SurfaceTexture(11);
                } else {
                    c1_fake_texture.release();
                    c1_fake_texture = null;
                    c1_fake_texture = new SurfaceTexture(11);
                }

                if (c1_fake_surface == null) {
                    c1_fake_surface = new Surface(c1_fake_texture);
                } else {
                    c1_fake_surface.release();
                    c1_fake_surface = null;
                    c1_fake_surface = new Surface(c1_fake_texture);
                }
                is_hooked = true;
                mcamera1.setPreviewTexture(c1_fake_texture);
                param.setResult(null);
            }
        });

        XposedHelpers.findAndHookMethod("android.hardware.camera2.CaptureRequest.Builder", lpparam.classLoader, "addTarget", Surface.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {

                if (param.args[0] == null) {
                    return;
                }
                if (param.thisObject == null) {
                    return;
                }
                File file = new File(video_path + "virtual.mp4");
                File toast_control = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "no_toast.jpg");
                need_to_show_toast = !toast_control.exists();
                if (!file.exists()) {
                    if (toast_content != null && need_to_show_toast) {
                        try {
                            Toast.makeText(toast_content, "不存在替换视频\n" + lpparam.packageName + "当前路径：" + video_path, Toast.LENGTH_SHORT).show();
                        } catch (Exception ee) {
                            XposedBridge.log("【VCAM】[toast]" + ee.toString());
                        }
                    }
                    return;
                }
                if (param.args[0].equals(c2_virtual_surface)) {
                    return;
                }
                File control_file = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "disable.jpg");
                if (control_file.exists()) {
                    return;
                }
                String surfaceInfo = param.args[0].toString();
                Surface targetSurface = (Surface) param.args[0];
                
                // Check if this surface is from an ImageReader with incompatible format
                // If so, DON'T store it for video frame feeding
                boolean isIncompatible = incompatibleSurfaces.contains(targetSurface);
                Integer surfaceFormat = surfaceFormats.get(targetSurface);
                
                if (isIncompatible) {
                    XposedBridge.log("【VCAM】Incompatible surface detected (format: " + 
                        (surfaceFormat != null ? "0x" + Integer.toHexString(surfaceFormat) : "unknown") + 
                        ") - marking for special handling");
                    // CRITICAL FIX: For JPEG/YUV surfaces used for still capture,
                    // we still redirect to virtual surface but trigger immediate completion
                    // to prevent infinite hang. The capture will "fail" gracefully.
                    // Store the original surface for reference
                    originalCaptureTargets.put(c2_virtual_surface, targetSurface);
                    // Set pending capture flag so we can handle this in acquireLatestImage
                    pendingStillCapture.set(true);
                } else if (surfaceInfo.contains("Surface(name=null)")) {
                    // This is an ImageReader surface or internal surface
                    // Per user requirement: "use the video only to feed preview surface"
                    // We must avoid feeding video to these unnamed surfaces as they are likely for analysis,
                    // background processing, or capture, not user-facing preview.
                    // Exceptions could be made if we knew for sure it was the preview, but usually preview has a name.
                    XposedBridge.log("【VCAM】Skipping unnamed surface (likely ImageReader/Analysis): " + surfaceInfo);
                } else {
                    if (c2_preview_Surfcae == null) {
                        c2_preview_Surfcae = targetSurface;
                    } else {
                        if ((!c2_preview_Surfcae.equals(targetSurface)) && c2_preview_Surfcae_1 == null) {
                            c2_preview_Surfcae_1 = targetSurface;
                        }
                    }
                }
                XposedBridge.log("【VCAM】添加目标：" + param.args[0].toString() + 
                    (surfaceFormat != null ? " format: 0x" + Integer.toHexString(surfaceFormat) : "") +
                    (isIncompatible ? " [INCOMPATIBLE]" : ""));
                param.args[0] = c2_virtual_surface;

            }
        });

        XposedHelpers.findAndHookMethod("android.hardware.camera2.CaptureRequest.Builder", lpparam.classLoader, "removeTarget", Surface.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {

                if (param.args[0] == null) {
                    return;
                }
                if (param.thisObject == null) {
                    return;
                }
                File file = new File(video_path + "virtual.mp4");
                File toast_control = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "no_toast.jpg");
                need_to_show_toast = !toast_control.exists();
                if (!file.exists()) {
                    if (toast_content != null && need_to_show_toast) {
                        try {
                            Toast.makeText(toast_content, "不存在替换视频\n" + lpparam.packageName + "当前路径：" + video_path, Toast.LENGTH_SHORT).show();
                        } catch (Exception ee) {
                            XposedBridge.log("【VCAM】[toast]" + ee.toString());
                        }
                    }
                    return;
                }
                File control_file = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "disable.jpg");
                if (control_file.exists()) {
                    return;
                }
                Surface rm_surf = (Surface) param.args[0];
                if (rm_surf.equals(c2_preview_Surfcae)) {
                    c2_preview_Surfcae = null;
                }
                if (rm_surf.equals(c2_preview_Surfcae_1)) {
                    c2_preview_Surfcae_1 = null;
                }
                if (rm_surf.equals(c2_reader_Surfcae_1)) {
                    c2_reader_Surfcae_1 = null;
                }
                if (rm_surf.equals(c2_reader_Surfcae)) {
                    c2_reader_Surfcae = null;
                }

                XposedBridge.log("【VCAM】移除目标：" + param.args[0].toString());
            }
        });

        XposedHelpers.findAndHookMethod("android.hardware.camera2.CaptureRequest.Builder", lpparam.classLoader, "build", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.thisObject == null) {
                    return;
                }
                if (param.thisObject.equals(c2_builder)) {
                    return;
                }
                c2_builder = (CaptureRequest.Builder) param.thisObject;
                File file = new File(video_path + "virtual.mp4");
                File toast_control = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "no_toast.jpg");
                need_to_show_toast = !toast_control.exists();
                if (!file.exists() && need_to_show_toast) {
                    if (toast_content != null) {
                        try {
                            Toast.makeText(toast_content, "不存在替换视频\n" + lpparam.packageName + "当前路径：" + video_path, Toast.LENGTH_SHORT).show();
                        } catch (Exception ee) {
                            XposedBridge.log("【VCAM】[toast]" + ee.toString());
                        }
                    }
                    return;
                }

                File control_file = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "disable.jpg");
                if (control_file.exists()) {
                    return;
                }
                XposedBridge.log("【VCAM】开始build请求");
                process_camera2_play();
            }
        });

/*        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "stopPreview", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.thisObject.equals(HookMain.origin_preview_camera) || param.thisObject.equals(HookMain.camera_onPreviewFrame) || param.thisObject.equals(HookMain.mcamera1)) {
                    if (hw_decode_obj != null) {
                        hw_decode_obj.stopDecode();
                    }
                    if (mplayer1 != null) {
                        mplayer1.release();
                        mplayer1 = null;
                    }
                    if (mMediaPlayer != null) {
                        mMediaPlayer.release();
                        mMediaPlayer = null;
                    }
                    is_someone_playing = false;

                    XposedBridge.log("停止预览");
                }
            }
        });*/

        XposedHelpers.findAndHookMethod("android.media.ImageReader", lpparam.classLoader, "newInstance", int.class, int.class, int.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                int width = (int) param.args[0];
                int height = (int) param.args[1];
                int format = (int) param.args[2];
                int maxImages = (int) param.args[3];
                
                XposedBridge.log("【VCAM】应用创建了渲染器：宽：" + width + " 高：" + height + " 格式：" + format + " (0x" + Integer.toHexString(format) + ")");
                c2_ori_width = width;
                c2_ori_height = height;
                imageReaderFormat = format;
                
                File toast_control = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "no_toast.jpg");
                need_to_show_toast = !toast_control.exists();
                if (toast_content != null && need_to_show_toast) {
                    try {
                        Toast.makeText(toast_content, "应用创建了渲染器：\n宽：" + width + "\n高：" + height + "\n格式：0x" + Integer.toHexString(format), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        XposedBridge.log("【VCAM】[toast]" + e.toString());
                    }
                }
                
                // Check if virtual camera is active
                File file = new File(video_path + "virtual.mp4");
                File control_file = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "disable.jpg");
                if (!file.exists() || control_file.exists()) {
                    return; // Virtual camera not active
                }
                
                // CRITICAL FIX: For YUV_420_888 format ImageReaders, we cannot feed RAW_PRIVATE frames
                // Change the format to a compatible one (PRIVATE = 0x22) that can receive MediaCodec output
                // Note: ImageFormat.PRIVATE = 34 (0x22)
                if (format == FORMAT_YUV_420_888) {
                    // Try to use PRIVATE format which is compatible with MediaCodec
                    // However, changing the format may break the app's processing
                    // Better approach: just mark it as incompatible and don't feed video to it
                    XposedBridge.log("【VCAM】ImageReader with YUV_420_888 format detected - will not feed video frames");
                }
            }
            
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                // Track the ImageReader and its configured format for later use
                if (param.getResult() != null) {
                    ImageReader reader = (ImageReader) param.getResult();
                    int format = (int) param.args[2];
                    imageReaderFormats.put(reader, format);
                    
                    // Get the surface from this ImageReader and track it
                    try {
                        Surface surface = reader.getSurface();
                        if (surface != null) {
                            surfaceFormats.put(surface, format);
                            
                            // Mark as incompatible if format is JPEG or YUV_420_888
                            // These formats cannot receive RAW_PRIVATE frames from MediaCodec
                            if (format == FORMAT_JPEG || format == FORMAT_YUV_420_888) {
                                incompatibleSurfaces.add(surface);
                                XposedBridge.log("【VCAM】Marked surface as incompatible (no video feed): format=" + format + " (0x" + Integer.toHexString(format) + ")");
                            }
                        }
                    } catch (Exception e) {
                        XposedBridge.log("【VCAM】Error getting ImageReader surface: " + e.getMessage());
                    }
                    
                    XposedBridge.log("【VCAM】Tracking ImageReader format: " + format + " (0x" + Integer.toHexString(format) + ")");
                }
            }
        });

        // Hook ImageReader.acquireNextImage to provide fake image on format mismatch
        XposedHelpers.findAndHookMethod("android.media.ImageReader", lpparam.classLoader, "acquireNextImage", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                // Check if virtual camera is active
                File file = new File(video_path + "virtual.mp4");
                File control_file = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "disable.jpg");
                if (!file.exists() || control_file.exists()) {
                    return; // Virtual camera not active, let it proceed normally
                }
                
                ImageReader reader = (ImageReader) param.thisObject;
                int format = reader.getImageFormat();
                
                // If pending still capture and format is YUV or JPEG, return null to prevent hang
                if (pendingStillCapture.get() && (format == ImageFormat.YUV_420_888 || format == ImageFormat.JPEG)) {
                    XposedBridge.log("【VCAM】acquireNextImage called during pending still capture, format: " + format + " - returning null");
                    pendingStillCapture.set(false);
                    param.setResult(null);
                }
            }
            
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                // If an exception was thrown, we catch it here
                if (param.getThrowable() != null) {
                    Throwable t = param.getThrowable();
                    if (t instanceof UnsupportedOperationException && 
                        t.getMessage() != null && 
                        t.getMessage().contains("doesn't match")) {
                        XposedBridge.log("【VCAM】Caught ImageReader format mismatch: " + t.getMessage());
                        // Return null instead of crashing - the app should handle null gracefully
                        param.setResult(null);
                        param.setThrowable(null);
                        
                        // If we have a pending still capture, mark it as failed
                        if (pendingStillCapture.get()) {
                            XposedBridge.log("【VCAM】Format mismatch during still capture");
                            pendingStillCapture.set(false);
                        }
                    }
                }
                
                // Also handle IllegalStateException (no buffers available)
                if (param.getThrowable() instanceof IllegalStateException) {
                    XposedBridge.log("【VCAM】Caught IllegalStateException in acquireNextImage: " + param.getThrowable().getMessage());
                    param.setResult(null);
                    param.setThrowable(null);
                    pendingStillCapture.set(false);
                }
            }
        });

        // Hook ImageReader.acquireLatestImage - CRITICAL for still capture
        // This is where the app tries to get the captured image
        XposedHelpers.findAndHookMethod("android.media.ImageReader", lpparam.classLoader, "acquireLatestImage", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                // Check if virtual camera is active
                File file = new File(video_path + "virtual.mp4");
                File control_file = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "disable.jpg");
                if (!file.exists() || control_file.exists()) {
                    return; // Virtual camera not active, let it proceed normally
                }
                
                ImageReader reader = (ImageReader) param.thisObject;
                int format = reader.getImageFormat();
                
                // Log the attempt
                if (pendingStillCapture.get() || format == ImageFormat.JPEG || format == ImageFormat.YUV_420_888) {
                    XposedBridge.log("【VCAM】acquireLatestImage called, format: " + format + " (0x" + Integer.toHexString(format) + "), pending: " + pendingStillCapture.get());
                }
                
                // CRITICAL FIX: If this is a still capture ImageReader and we're in pending capture state,
                // we inject the fake image instead of null or original image.
                if (pendingStillCapture.get() && (format == FORMAT_JPEG || format == FORMAT_YUV_420_888)) {
                    XposedBridge.log("【VCAM】Still capture ImageReader - injecting fake image");

                    try {
                        // Create and return the fake image
                        Image fakeImage = FakeImageHelper.createFakeImage(video_path, reader.getWidth(), reader.getHeight(), format, System.nanoTime());
                        if (fakeImage != null) {
                            // Return the fake image
                            param.setResult(fakeImage);
                            XposedBridge.log("【VCAM】Fake image injected successfully");
                        } else {
                            XposedBridge.log("【VCAM】Failed to create fake image - returning null");
                            param.setResult(null);
                        }
                    } catch (Throwable t) {
                         XposedBridge.log("【VCAM】Error injecting fake image: " + t.getMessage());
                         param.setResult(null);
                    }

                    // Reset pending state since we're "completing" the capture
                    pendingStillCapture.set(false);
                }
            }
            
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                // If an exception was thrown, we catch it here
                if (param.getThrowable() != null) {
                    Throwable t = param.getThrowable();
                    if (t instanceof UnsupportedOperationException && 
                        t.getMessage() != null && 
                        t.getMessage().contains("doesn't match")) {
                        XposedBridge.log("【VCAM】Caught ImageReader format mismatch in acquireLatestImage: " + t.getMessage());
                        // Return null instead of crashing
                        param.setResult(null);
                        param.setThrowable(null);
                        
                        // Mark pending capture as done
                        if (pendingStillCapture.get()) {
                            XposedBridge.log("【VCAM】Format mismatch during still capture in acquireLatestImage");
                            pendingStillCapture.set(false);
                        }
                    }
                }
                
                // Also handle IllegalStateException (no buffers available)
                if (param.getThrowable() instanceof IllegalStateException) {
                    XposedBridge.log("【VCAM】Caught IllegalStateException in acquireLatestImage: " + param.getThrowable().getMessage());
                    param.setResult(null);
                    param.setThrowable(null);
                    pendingStillCapture.set(false);
                }
            }
        });

        // Hook the internal acquireNextSurfaceImage method to handle format mismatch at lower level
        // Note: This method is private and takes a SurfaceImage (inner class) parameter
        // The method signature varies across Android versions, so we need to handle this carefully
        // CRITICAL: We must catch Throwable (not just Exception) because NoSuchMethodError is an Error
        try {
            // Try to find the SurfaceImage inner class first
            Class<?> surfaceImageClass = null;
            try {
                surfaceImageClass = XposedHelpers.findClass("android.media.ImageReader$SurfaceImage", lpparam.classLoader);
            } catch (Throwable classEx) {
                XposedBridge.log("【VCAM】SurfaceImage class not found, skipping acquireNextSurfaceImage hook: " + classEx.getMessage());
            }
            
            if (surfaceImageClass != null) {
                final Class<?> finalSurfaceImageClass = surfaceImageClass;
                XposedHelpers.findAndHookMethod("android.media.ImageReader", lpparam.classLoader, "acquireNextSurfaceImage", finalSurfaceImageClass, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.getThrowable() != null) {
                            Throwable t = param.getThrowable();
                            if (t instanceof UnsupportedOperationException && 
                                t.getMessage() != null && 
                                t.getMessage().contains("doesn't match")) {
                                XposedBridge.log("【VCAM】Caught format mismatch in acquireNextSurfaceImage: " + t.getMessage());
                                // Return ACQUIRE_NO_BUFS (1) instead of throwing - this signals no buffer available
                                // which is a safe error code that apps handle gracefully
                                param.setResult(1);
                                param.setThrowable(null);
                            }
                        }
                    }
                });
                XposedBridge.log("【VCAM】Successfully hooked acquireNextSurfaceImage");
            }
        } catch (Throwable t) {
            // Catch all errors including NoSuchMethodError, ClassNotFoundError, etc.
            // Method signature doesn't match on this Android version - this is expected on some devices
            // Log and continue - don't crash the module initialization
            XposedBridge.log("【VCAM】Could not hook acquireNextSurfaceImage (expected on some Android versions): " + t.getClass().getSimpleName() + " - " + t.getMessage());
        }
        
        // Hook ImageWriter to inject frames directly when available (API 23+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Class<?> imageWriterClass = XposedHelpers.findClass("android.media.ImageWriter", lpparam.classLoader);
                XposedBridge.log("【VCAM】ImageWriter class found, attempting to hook newInstance");
            } catch (Exception e) {
                XposedBridge.log("【VCAM】ImageWriter not available: " + e.getMessage());
            }
        }

        XposedHelpers.findAndHookMethod("android.hardware.camera2.CameraCaptureSession.CaptureCallback", lpparam.classLoader, "onCaptureFailed", CameraCaptureSession.class, CaptureRequest.class, CaptureFailure.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log("【VCAM】onCaptureFailed - 原因：" + ((CaptureFailure) param.args[2]).getReason());
                        // Reset pending capture flag on failure
                        pendingStillCapture.set(false);
                    }
                });
        
        // Hook onCaptureCompleted to trigger fake image injection after capture completes
        XposedHelpers.findAndHookMethod("android.hardware.camera2.CameraCaptureSession.CaptureCallback", lpparam.classLoader, "onCaptureCompleted", 
            CameraCaptureSession.class, CaptureRequest.class, TotalCaptureResult.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    // Check if we have a pending still capture
                    if (pendingStillCapture.get()) {
                        XposedBridge.log("【VCAM】onCaptureCompleted - triggering fake image for still capture");
                        
                        // Get the handler from the callback or use our stored one
                        Handler targetHandler = callbackHandler;
                        
                        // Trigger the image available callback with a short delay
                        if (targetHandler != null) {
                            targetHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    triggerImageAvailableCallbacks();
                                }
                            }, 50);
                        } else {
                            // Trigger directly in a new thread
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    triggerImageAvailableCallbacks();
                                }
                            }).start();
                        }
                    }
                }
            });
        
        // Hook ImageReader.setOnImageAvailableListener to track listeners and provide fake images
        XposedHelpers.findAndHookMethod("android.media.ImageReader", lpparam.classLoader, "setOnImageAvailableListener", 
            ImageReader.OnImageAvailableListener.class, Handler.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                ImageReader reader = (ImageReader) param.thisObject;
                ImageReader.OnImageAvailableListener listener = (ImageReader.OnImageAvailableListener) param.args[0];
                Handler handler = (Handler) param.args[1];
                
                if (listener != null) {
                    imageReaderListeners.put(reader, listener);
                    if (handler != null) {
                        callbackHandler = handler;
                    }
                    
                    int format = reader.getImageFormat();
                    XposedBridge.log("【VCAM】ImageReader.setOnImageAvailableListener - format: " + format + " (0x" + Integer.toHexString(format) + "), size: " + reader.getWidth() + "x" + reader.getHeight());
                    
                    // Track if this is a JPEG or YUV reader for still capture
                    if (format == ImageFormat.JPEG || format == ImageFormat.YUV_420_888) {
                        stillCaptureReaders.add(reader);
                        XposedBridge.log("【VCAM】Tracking ImageReader for still capture, format: " + format);
                    }
                }
            }
        });
        
        // Hook CameraCaptureSession.capture to detect still capture requests and inject fake image
        XposedHelpers.findAndHookMethod("android.hardware.camera2.CameraCaptureSession", lpparam.classLoader, "capture", 
            CaptureRequest.class, CameraCaptureSession.CaptureCallback.class, Handler.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                File file = new File(video_path + "virtual.mp4");
                File control_file = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "disable.jpg");
                if (!file.exists() || control_file.exists()) {
                    return;
                }
                
                CaptureRequest request = (CaptureRequest) param.args[0];
                CameraCaptureSession.CaptureCallback originalCallback = (CameraCaptureSession.CaptureCallback) param.args[1];
                Handler handler = (Handler) param.args[2];
                CameraCaptureSession session = (CameraCaptureSession) param.thisObject;
                
                // Store session and callback for later use
                activeCaptureSession = session;
                pendingCaptureCallback = originalCallback;
                
                if (originalCallback != null) {
                    XposedBridge.log("【VCAM】CameraCaptureSession.capture called - wrapping callback for still capture handling");
                    
                    // Store the handler for later use
                    if (handler != null) {
                        callbackHandler = handler;
                    }
                    
                    // Mark that we have a pending still capture
                    pendingStillCapture.set(true);
                    
                    // CRITICAL FIX: Instead of triggering fake callback (which leads to null image),
                    // we let the capture proceed but handle the result in onCaptureCompleted
                    // The key is to complete the capture flow so the app doesn't hang
                    
                    // Schedule a timeout handler to complete the capture if it gets stuck
                    Handler targetHandler = handler != null ? handler : callbackHandler;
                    if (targetHandler != null) {
                        targetHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (pendingStillCapture.get()) {
                                    XposedBridge.log("【VCAM】Still capture timeout - forcing completion");
                                    pendingStillCapture.set(false);
                                    // Trigger the image available callback for any tracked readers
                                    triggerFakeImageInjection(targetHandler);
                                }
                            }
                        }, 500); // 500ms timeout
                    }
                }
            }
        });
        
        // Also hook captureSingleRequest for newer APIs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                XposedHelpers.findAndHookMethod("android.hardware.camera2.CameraCaptureSession", lpparam.classLoader, "captureSingleRequest", 
                    CaptureRequest.class, Executor.class, CameraCaptureSession.CaptureCallback.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        File file = new File(video_path + "virtual.mp4");
                        File control_file = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "disable.jpg");
                        if (!file.exists() || control_file.exists()) {
                            return;
                        }
                        
                        XposedBridge.log("【VCAM】CameraCaptureSession.captureSingleRequest called - triggering fake image injection");
                        pendingStillCapture.set(true);
                        stillCaptureStartTime = System.currentTimeMillis();
                        triggerFakeImageInjection(null);
                    }
                });
            } catch (Exception e) {
                XposedBridge.log("【VCAM】Could not hook captureSingleRequest: " + e.getMessage());
            }
        }
    }
    
    /**
     * Trigger ImageReader callbacks for all tracked still capture readers
     * This is called after onCaptureCompleted to complete the capture flow
     */
    private void triggerImageAvailableCallbacks() {
        XposedBridge.log("【VCAM】Triggering ImageAvailable callbacks for all tracked readers");
        
        for (java.util.Map.Entry<ImageReader, ImageReader.OnImageAvailableListener> entry : imageReaderListeners.entrySet()) {
            ImageReader reader = entry.getKey();
            ImageReader.OnImageAvailableListener listener = entry.getValue();
            
            if (stillCaptureReaders.contains(reader) && listener != null) {
                try {
                    XposedBridge.log("【VCAM】Triggering onImageAvailable for format: " + reader.getImageFormat());
                    // Only trigger if we haven't timed out
                    if (stillCaptureStartTime == 0 || (System.currentTimeMillis() - stillCaptureStartTime) < STILL_CAPTURE_TIMEOUT_MS * 2) {
                        listener.onImageAvailable(reader);
                    } else {
                        XposedBridge.log("【VCAM】Skipping callback - capture already timed out");
                    }
                } catch (Exception e) {
                    XposedBridge.log("【VCAM】Error triggering callback: " + e.getMessage());
                }
            }
        }
        
        // Reset the pending capture flag
        pendingStillCapture.set(false);
        stillCaptureStartTime = 0;
    }
    
    /**
     * Trigger fake image injection for still capture
     * This loads the still image and triggers the onImageAvailable callback
     * CRITICAL: We need to ensure the app gets a valid response so it doesn't hang
     */
    private void triggerFakeImageInjection(Handler handler) {
        // Find the still image path
        String imagePath = video_path + "1000.bmp";
        File imageFile = new File(imagePath);
        
        // Try different image formats
        String[] tryPaths = {"1000.bmp", "1000.jpg", "1000.jpeg", "1000.png", "still.jpg", "still.bmp"};
        for (String path : tryPaths) {
            imageFile = new File(video_path + path);
            if (imageFile.exists()) {
                imagePath = video_path + path;
                break;
            }
        }
        
        if (!imageFile.exists()) {
            XposedBridge.log("【VCAM】No still image found, will skip capture gracefully");
            // Even without still image, we need to complete the capture flow
            // Mark capture as done so app doesn't hang
            pendingStillCapture.set(false);
            stillCaptureStartTime = 0;
        } else {
            still_image_path = imagePath;
            XposedBridge.log("【VCAM】Found still image at: " + imagePath);
        }
        
        // CRITICAL FIX: The root cause of infinite loading is that we trigger onImageAvailable
        // but the ImageReader has no actual image (acquireLatestImage returns null).
        // 
        // Solution: Don't trigger the callback at all - let the capture timeout/fail naturally.
        // The app should have error handling for failed captures.
        // 
        // OR: If we have readers, trigger them but ensure they can handle null.
        
        // For each tracked still capture ImageReader, trigger the callback
        // But only if we have a still image to provide
        boolean hasStillImage = imageFile.exists();
        
        for (java.util.Map.Entry<ImageReader, ImageReader.OnImageAvailableListener> entry : imageReaderListeners.entrySet()) {
            ImageReader reader = entry.getKey();
            ImageReader.OnImageAvailableListener listener = entry.getValue();
            
            if (stillCaptureReaders.contains(reader) && listener != null) {
                final ImageReader finalReader = reader;
                final ImageReader.OnImageAvailableListener finalListener = listener;
                final boolean hasFakeImage = hasStillImage;
                
                Runnable callbackRunnable = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            XposedBridge.log("【VCAM】Triggering onImageAvailable for ImageReader format: " + finalReader.getImageFormat());
                            
                            // Note: acquireLatestImage will return null due to format mismatch
                            // The app SHOULD handle this gracefully, but if it doesn't,
                            // we've hooked acquireLatestImage to suppress exceptions
                            finalListener.onImageAvailable(finalReader);
                            
                        } catch (Exception e) {
                            XposedBridge.log("【VCAM】Error in fake image callback: " + e.getMessage());
                        } finally {
                            // Reset the pending capture flag
                            pendingStillCapture.set(false);
                            stillCaptureStartTime = 0;
                        }
                    }
                };
                
                // Post with a short delay
                Handler targetHandler = handler != null ? handler : callbackHandler;
                if (targetHandler != null) {
                    targetHandler.postDelayed(callbackRunnable, 100);
                } else {
                    // Execute on new thread as fallback
                    new Thread(callbackRunnable).start();
                }
                
                // Only trigger for one reader per capture
                break;
            }
        }
        
        // If no still capture readers were found, reset the flag
        if (stillCaptureReaders.isEmpty()) {
            XposedBridge.log("【VCAM】No still capture ImageReaders tracked - capture may hang");
            pendingStillCapture.set(false);
            stillCaptureStartTime = 0;
        }
    }

    private void process_camera2_play() {
        
        // Check if reader surface is compatible before feeding video frames
        // CRITICAL FIX: Don't feed video to ImageReaders expecting YUV_420_888 or JPEG format
        boolean reader1Compatible = c2_reader_Surfcae != null && !incompatibleSurfaces.contains(c2_reader_Surfcae);
        boolean reader2Compatible = c2_reader_Surfcae_1 != null && !incompatibleSurfaces.contains(c2_reader_Surfcae_1);
        
        if (c2_reader_Surfcae != null) {
            Integer surfFormat = surfaceFormats.get(c2_reader_Surfcae);
            if (surfFormat != null && (surfFormat == FORMAT_JPEG || surfFormat == FORMAT_YUV_420_888)) {
                XposedBridge.log("【VCAM】Skipping video feed to reader surface - incompatible format: 0x" + Integer.toHexString(surfFormat));
                reader1Compatible = false;
            }
        }
        
        if (c2_reader_Surfcae_1 != null) {
            Integer surfFormat = surfaceFormats.get(c2_reader_Surfcae_1);
            if (surfFormat != null && (surfFormat == FORMAT_JPEG || surfFormat == FORMAT_YUV_420_888)) {
                XposedBridge.log("【VCAM】Skipping video feed to reader surface 1 - incompatible format: 0x" + Integer.toHexString(surfFormat));
                reader2Compatible = false;
            }
        }

        if (reader1Compatible && c2_reader_Surfcae != null) {
            if (c2_hw_decode_obj != null) {
                c2_hw_decode_obj.stopDecode();
                c2_hw_decode_obj = null;
            }

            c2_hw_decode_obj = new VideoToFrames();
            try {
                if (imageReaderFormat == 256) {
                    c2_hw_decode_obj.setSaveFrames("null", OutputImageFormat.JPEG);
                } else {
                    c2_hw_decode_obj.setSaveFrames("null", OutputImageFormat.NV21);
                }
                // Use original video resolution for better scaling - let the surface handle the scaling
                c2_hw_decode_obj.setUseOriginalResolution(true);
                c2_hw_decode_obj.set_surfcae(c2_reader_Surfcae);
                c2_hw_decode_obj.decode(video_path + "virtual.mp4");
                XposedBridge.log("【VCAM】Started video feed to reader surface");
            } catch (Throwable throwable) {
                XposedBridge.log("【VCAM】" + throwable);
            }
        }

        if (reader2Compatible && c2_reader_Surfcae_1 != null) {
            if (c2_hw_decode_obj_1 != null) {
                c2_hw_decode_obj_1.stopDecode();
                c2_hw_decode_obj_1 = null;
            }

            c2_hw_decode_obj_1 = new VideoToFrames();
            try {
                if (imageReaderFormat == 256) {
                    c2_hw_decode_obj_1.setSaveFrames("null", OutputImageFormat.JPEG);
                } else {
                    c2_hw_decode_obj_1.setSaveFrames("null", OutputImageFormat.NV21);
                }
                // Use original video resolution for better scaling
                c2_hw_decode_obj_1.setUseOriginalResolution(true);
                c2_hw_decode_obj_1.set_surfcae(c2_reader_Surfcae_1);
                c2_hw_decode_obj_1.decode(video_path + "virtual.mp4");
                XposedBridge.log("【VCAM】Started video feed to reader surface 1");
            } catch (Throwable throwable) {
                XposedBridge.log("【VCAM】" + throwable);
            }
        }


        if (c2_preview_Surfcae != null) {
            if (c2_player == null) {
                c2_player = new MediaPlayer();
            } else {
                c2_player.release();
                c2_player = new MediaPlayer();
            }
            c2_player.setSurface(c2_preview_Surfcae);
            File sfile = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "no-silent.jpg");
            if (!sfile.exists()) {
                c2_player.setVolume(0, 0);
            }
            c2_player.setLooping(true);

            try {
                c2_player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    public void onPrepared(MediaPlayer mp) {
                        c2_player.start();
                    }
                });
                c2_player.setDataSource(video_path + "virtual.mp4");
                c2_player.prepare();
            } catch (Exception e) {
                XposedBridge.log("【VCAM】[c2player][" + c2_preview_Surfcae.toString() + "]" + e);
            }
        }

        if (c2_preview_Surfcae_1 != null) {
            if (c2_player_1 == null) {
                c2_player_1 = new MediaPlayer();
            } else {
                c2_player_1.release();
                c2_player_1 = new MediaPlayer();
            }
            c2_player_1.setSurface(c2_preview_Surfcae_1);
            File sfile = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "no-silent.jpg");
            if (!sfile.exists()) {
                c2_player_1.setVolume(0, 0);
            }
            c2_player_1.setLooping(true);

            try {
                c2_player_1.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    public void onPrepared(MediaPlayer mp) {
                        c2_player_1.start();
                    }
                });
                c2_player_1.setDataSource(video_path + "virtual.mp4");
                c2_player_1.prepare();
            } catch (Exception e) {
                XposedBridge.log("【VCAM】[c2player1]" + "[ " + c2_preview_Surfcae_1.toString() + "]" + e);
            }
        }
        XposedBridge.log("【VCAM】Camera2处理过程完全执行");
    }

    private Surface create_virtual_surface() {
        if (need_recreate) {
            if (c2_virtual_surfaceTexture != null) {
                c2_virtual_surfaceTexture.release();
                c2_virtual_surfaceTexture = null;
            }
            if (c2_virtual_surface != null) {
                c2_virtual_surface.release();
                c2_virtual_surface = null;
            }
            c2_virtual_surfaceTexture = new SurfaceTexture(15);
            c2_virtual_surface = new Surface(c2_virtual_surfaceTexture);
            need_recreate = false;
        } else {
            if (c2_virtual_surface == null) {
                need_recreate = true;
                c2_virtual_surface = create_virtual_surface();
            }
        }
        XposedBridge.log("【VCAM】【重建垃圾场】" + c2_virtual_surface.toString());
        return c2_virtual_surface;
    }

    private void process_camera2_init(Class hooked_class) {

        XposedHelpers.findAndHookMethod(hooked_class, "onOpened", CameraDevice.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                need_recreate = true;
                create_virtual_surface();
                if (c2_player != null) {
                    c2_player.stop();
                    c2_player.reset();
                    c2_player.release();
                    c2_player = null;
                }
                if (c2_hw_decode_obj_1 != null) {
                    c2_hw_decode_obj_1.stopDecode();
                    c2_hw_decode_obj_1 = null;
                }
                if (c2_hw_decode_obj != null) {
                    c2_hw_decode_obj.stopDecode();
                    c2_hw_decode_obj = null;
                }
                if (c2_player_1 != null) {
                    c2_player_1.stop();
                    c2_player_1.reset();
                    c2_player_1.release();
                    c2_player_1 = null;
                }
                c2_preview_Surfcae_1 = null;
                c2_reader_Surfcae_1 = null;
                c2_reader_Surfcae = null;
                c2_preview_Surfcae = null;
                is_first_hook_build = true;
                XposedBridge.log("【VCAM】打开相机C2");

                File file = new File(video_path + "virtual.mp4");
                File toast_control = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "no_toast.jpg");
                need_to_show_toast = !toast_control.exists();
                if (!file.exists()) {
                    if (toast_content != null && need_to_show_toast) {
                        try {
                            Toast.makeText(toast_content, "不存在替换视频\n" + toast_content.getPackageName() + "当前路径：" + video_path, Toast.LENGTH_SHORT).show();
                        } catch (Exception ee) {
                            XposedBridge.log("【VCAM】[toast]" + ee.toString());
                        }
                    }
                    return;
                }
                XposedHelpers.findAndHookMethod(param.args[0].getClass(), "createCaptureSession", List.class, CameraCaptureSession.StateCallback.class, Handler.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam paramd) throws Throwable {
                        if (paramd.args[0] != null) {
                            XposedBridge.log("【VCAM】createCaptureSession创捷捕获，原始:" + paramd.args[0].toString() + "虚拟：" + c2_virtual_surface.toString());
                            paramd.args[0] = Arrays.asList(c2_virtual_surface);
                            if (paramd.args[1] != null) {
                                process_camera2Session_callback((CameraCaptureSession.StateCallback) paramd.args[1]);
                            }
                        }
                    }
                });

/*                XposedHelpers.findAndHookMethod(param.args[0].getClass(), "close", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam paramd) throws Throwable {
                        XposedBridge.log("C2终止预览");
                        if (c2_hw_decode_obj != null) {
                            c2_hw_decode_obj.stopDecode();
                            c2_hw_decode_obj = null;
                        }
                        if (c2_hw_decode_obj_1 != null) {
                            c2_hw_decode_obj_1.stopDecode();
                            c2_hw_decode_obj_1 = null;
                        }
                        if (c2_player != null) {
                            c2_player.release();
                            c2_player = null;
                        }
                        if (c2_player_1 != null){
                            c2_player_1.release();
                            c2_player_1 = null;
                        }
                        c2_preview_Surfcae_1 = null;
                        c2_reader_Surfcae_1 = null;
                        c2_reader_Surfcae = null;
                        c2_preview_Surfcae = null;
                        need_recreate = true;
                        is_first_hook_build= true;
                    }
                });*/

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    XposedHelpers.findAndHookMethod(param.args[0].getClass(), "createCaptureSessionByOutputConfigurations", List.class, CameraCaptureSession.StateCallback.class, Handler.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            super.beforeHookedMethod(param);
                            if (param.args[0] != null) {
                                outputConfiguration = new OutputConfiguration(c2_virtual_surface);
                                param.args[0] = Arrays.asList(outputConfiguration);

                                XposedBridge.log("【VCAM】执行了createCaptureSessionByOutputConfigurations-144777");
                                if (param.args[1] != null) {
                                    process_camera2Session_callback((CameraCaptureSession.StateCallback) param.args[1]);
                                }
                            }
                        }
                    });
                }


                    XposedHelpers.findAndHookMethod(param.args[0].getClass(), "createConstrainedHighSpeedCaptureSession", List.class, CameraCaptureSession.StateCallback.class, Handler.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            super.beforeHookedMethod(param);
                            if (param.args[0] != null) {
                                param.args[0] = Arrays.asList(c2_virtual_surface);
                                XposedBridge.log("【VCAM】执行了 createConstrainedHighSpeedCaptureSession -5484987");
                                if (param.args[1] != null) {
                                    process_camera2Session_callback((CameraCaptureSession.StateCallback) param.args[1]);
                                }
                            }
                        }
                    });


                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    XposedHelpers.findAndHookMethod(param.args[0].getClass(), "createReprocessableCaptureSession", InputConfiguration.class, List.class, CameraCaptureSession.StateCallback.class, Handler.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            super.beforeHookedMethod(param);
                            if (param.args[1] != null) {
                                param.args[1] = Arrays.asList(c2_virtual_surface);
                                XposedBridge.log("【VCAM】执行了 createReprocessableCaptureSession ");
                                if (param.args[2] != null) {
                                    process_camera2Session_callback((CameraCaptureSession.StateCallback) param.args[2]);
                                }
                            }
                        }
                    });
                }


                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    XposedHelpers.findAndHookMethod(param.args[0].getClass(), "createReprocessableCaptureSessionByConfigurations", InputConfiguration.class, List.class, CameraCaptureSession.StateCallback.class, Handler.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            super.beforeHookedMethod(param);
                            if (param.args[1] != null) {
                                outputConfiguration = new OutputConfiguration(c2_virtual_surface);
                                param.args[0] = Arrays.asList(outputConfiguration);
                                XposedBridge.log("【VCAM】执行了 createReprocessableCaptureSessionByConfigurations");
                                if (param.args[2] != null) {
                                    process_camera2Session_callback((CameraCaptureSession.StateCallback) param.args[2]);
                                }
                            }
                        }
                    });
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    XposedHelpers.findAndHookMethod(param.args[0].getClass(), "createCaptureSession", SessionConfiguration.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            super.beforeHookedMethod(param);
                            if (param.args[0] != null) {
                                XposedBridge.log("【VCAM】执行了 createCaptureSession -5484987");
                                sessionConfiguration = (SessionConfiguration) param.args[0];
                                outputConfiguration = new OutputConfiguration(c2_virtual_surface);
                                fake_sessionConfiguration = new SessionConfiguration(sessionConfiguration.getSessionType(),
                                        Arrays.asList(outputConfiguration),
                                        sessionConfiguration.getExecutor(),
                                        sessionConfiguration.getStateCallback());
                                param.args[0] = fake_sessionConfiguration;
                                process_camera2Session_callback(sessionConfiguration.getStateCallback());
                            }
                        }
                    });
                }
            }
        });


        XposedHelpers.findAndHookMethod(hooked_class, "onError", CameraDevice.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("【VCAM】相机错误onerror：" + (int) param.args[1]);
            }

        });


        XposedHelpers.findAndHookMethod(hooked_class, "onDisconnected", CameraDevice.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("【VCAM】相机断开onDisconnected ：");
            }

        });


    }

    private void process_a_shot_jpeg(XC_MethodHook.MethodHookParam param, int index) {
        try {
            XposedBridge.log("【VCAM】第二个jpeg:" + param.args[index].toString());
        } catch (Exception eee) {
            XposedBridge.log("【VCAM】" + eee);

        }
        Class callback = param.args[index].getClass();

        XposedHelpers.findAndHookMethod(callback, "onPictureTaken", byte[].class, android.hardware.Camera.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam paramd) throws Throwable {
                try {
                    Camera loaclcam = (Camera) paramd.args[1];
                    onemwidth = loaclcam.getParameters().getPreviewSize().width;
                    onemhight = loaclcam.getParameters().getPreviewSize().height;
                    XposedBridge.log("【VCAM】JPEG拍照回调初始化：宽：" + onemwidth + "高：" + onemhight + "对应的类：" + loaclcam.toString());
                    File toast_control = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "no_toast.jpg");
                    need_to_show_toast = !toast_control.exists();
                    if (toast_content != null && need_to_show_toast) {
                        try {
                            Toast.makeText(toast_content, "发现拍照\n宽：" + onemwidth + "\n高：" + onemhight + "\n格式：JPEG", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            XposedBridge.log("【VCAM】[toast]" + e.toString());
                        }
                    }
                    File control_file = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "disable.jpg");
                    if (control_file.exists()) {
                        return;
                    }

                    Bitmap pict = getBMP(video_path + "1000.bmp");
                    ByteArrayOutputStream temp_array = new ByteArrayOutputStream();
                    pict.compress(Bitmap.CompressFormat.JPEG, 100, temp_array);
                    byte[] jpeg_data = temp_array.toByteArray();
                    paramd.args[0] = jpeg_data;
                } catch (Exception ee) {
                    XposedBridge.log("【VCAM】" + ee.toString());
                }
            }
        });
    }

    private void process_a_shot_YUV(XC_MethodHook.MethodHookParam param) {
        try {
            XposedBridge.log("【VCAM】发现拍照YUV:" + param.args[1].toString());
        } catch (Exception eee) {
            XposedBridge.log("【VCAM】" + eee);
        }
        Class callback = param.args[1].getClass();
        XposedHelpers.findAndHookMethod(callback, "onPictureTaken", byte[].class, android.hardware.Camera.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam paramd) throws Throwable {
                try {
                    Camera loaclcam = (Camera) paramd.args[1];
                    onemwidth = loaclcam.getParameters().getPreviewSize().width;
                    onemhight = loaclcam.getParameters().getPreviewSize().height;
                    XposedBridge.log("【VCAM】YUV拍照回调初始化：宽：" + onemwidth + "高：" + onemhight + "对应的类：" + loaclcam.toString());
                    File toast_control = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "no_toast.jpg");
                    need_to_show_toast = !toast_control.exists();
                    if (toast_content != null && need_to_show_toast) {
                        try {
                            Toast.makeText(toast_content, "发现拍照\n宽：" + onemwidth + "\n高：" + onemhight + "\n格式：YUV_420_888", Toast.LENGTH_SHORT).show();
                        } catch (Exception ee) {
                            XposedBridge.log("【VCAM】[toast]" + ee.toString());
                        }
                    }
                    File control_file = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "disable.jpg");
                    if (control_file.exists()) {
                        return;
                    }
                    input = getYUVByBitmap(getBMP(video_path + "1000.bmp"));
                    paramd.args[0] = input;
                } catch (Exception ee) {
                    XposedBridge.log("【VCAM】" + ee.toString());
                }
            }
        });
    }

    private void process_callback(XC_MethodHook.MethodHookParam param) {
        Class preview_cb_class = param.args[0].getClass();
        int need_stop = 0;
        File control_file = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "disable.jpg");
        if (control_file.exists()) {
            need_stop = 1;
        }
        File file = new File(video_path + "virtual.mp4");
        File toast_control = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "no_toast.jpg");
        need_to_show_toast = !toast_control.exists();
        if (!file.exists()) {
            if (toast_content != null && need_to_show_toast) {
                try {
                    Toast.makeText(toast_content, "不存在替换视频\n" + toast_content.getPackageName() + "当前路径：" + video_path, Toast.LENGTH_SHORT).show();
                } catch (Exception ee) {
                    XposedBridge.log("【VCAM】[toast]" + ee);
                }
            }
            need_stop = 1;
        }
        int finalNeed_stop = need_stop;
        XposedHelpers.findAndHookMethod(preview_cb_class, "onPreviewFrame", byte[].class, android.hardware.Camera.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam paramd) throws Throwable {
                Camera localcam = (android.hardware.Camera) paramd.args[1];
                if (localcam.equals(camera_onPreviewFrame)) {
                    while (data_buffer == null) {
                    }
                    System.arraycopy(data_buffer, 0, paramd.args[0], 0, Math.min(data_buffer.length, ((byte[]) paramd.args[0]).length));
                } else {
                    camera_callback_calss = preview_cb_class;
                    camera_onPreviewFrame = (android.hardware.Camera) paramd.args[1];
                    mwidth = camera_onPreviewFrame.getParameters().getPreviewSize().width;
                    mhight = camera_onPreviewFrame.getParameters().getPreviewSize().height;
                    int frame_Rate = camera_onPreviewFrame.getParameters().getPreviewFrameRate();
                    XposedBridge.log("【VCAM】帧预览回调初始化：宽：" + mwidth + " 高：" + mhight + " 帧率：" + frame_Rate);
                    File toast_control = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/" + "no_toast.jpg");
                    need_to_show_toast = !toast_control.exists();
                    if (toast_content != null && need_to_show_toast) {
                        try {
                            Toast.makeText(toast_content, "发现预览\n宽：" + mwidth + "\n高：" + mhight + "\n" + "需要视频分辨率与其完全相同", Toast.LENGTH_SHORT).show();
                        } catch (Exception ee) {
                            XposedBridge.log("【VCAM】[toast]" + ee.toString());
                        }
                    }
                    if (finalNeed_stop == 1) {
                        return;
                    }
                    if (hw_decode_obj != null) {
                        hw_decode_obj.stopDecode();
                    }
                    hw_decode_obj = new VideoToFrames();
                    hw_decode_obj.setSaveFrames("", OutputImageFormat.NV21);
                    hw_decode_obj.decode(video_path + "virtual.mp4");
                    while (data_buffer == null) {
                    }
                    System.arraycopy(data_buffer, 0, paramd.args[0], 0, Math.min(data_buffer.length, ((byte[]) paramd.args[0]).length));
                }

            }
        });

    }

    private void process_camera2Session_callback(CameraCaptureSession.StateCallback callback_calss){
        if (callback_calss == null){
            return;
        }
        XposedHelpers.findAndHookMethod(callback_calss.getClass(), "onConfigureFailed", CameraCaptureSession.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("【VCAM】onConfigureFailed ：" + param.args[0].toString());
            }

        });

        XposedHelpers.findAndHookMethod(callback_calss.getClass(), "onConfigured", CameraCaptureSession.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("【VCAM】onConfigured ：" + param.args[0].toString());
            }
        });

        XposedHelpers.findAndHookMethod( callback_calss.getClass(), "onClosed", CameraCaptureSession.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("【VCAM】onClosed ："+ param.args[0].toString());
            }
        });
    }



    //以下代码来源：https://blog.csdn.net/jacke121/article/details/73888732
    private Bitmap getBMP(String file) throws Throwable {
        return BitmapFactory.decodeFile(file);
    }

    private static byte[] rgb2YCbCr420(int[] pixels, int width, int height) {
        int len = width * height;
        // yuv格式数组大小，y亮度占len长度，u,v各占len/4长度。
        byte[] yuv = new byte[len * 3 / 2];
        int y, u, v;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int rgb = (pixels[i * width + j]) & 0x00FFFFFF;
                int r = rgb & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = (rgb >> 16) & 0xFF;
                // 套用公式
                y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                y = y < 16 ? 16 : (Math.min(y, 255));
                u = u < 0 ? 0 : (Math.min(u, 255));
                v = v < 0 ? 0 : (Math.min(v, 255));
                // 赋值
                yuv[i * width + j] = (byte) y;
                yuv[len + (i >> 1) * width + (j & ~1)] = (byte) u;
                yuv[len + +(i >> 1) * width + (j & ~1) + 1] = (byte) v;
            }
        }
        return yuv;
    }

    private static byte[] getYUVByBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int size = width * height;
        int[] pixels = new int[size];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        return rgb2YCbCr420(pixels, width, height);
    }
}

