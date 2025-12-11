package com.example.vcam;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
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
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookMain implements IXposedHookLoadPackage {
    
    // ==================== Camera1 API Fields ====================
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
    public static SurfaceTexture c1_fake_texture;
    public static Surface c1_fake_surface;
    public static SurfaceHolder ori_holder;
    public static MediaPlayer mplayer1;
    public static Camera mcamera1;
    public static int onemhight;
    public static int onemwidth;
    public static Class camera_callback_calss;
    
    // ==================== Camera2 API Fields ====================
    public static VideoToFrames c2_hw_decode_obj;
    public static VideoToFrames c2_hw_decode_obj_1;
    public int imageReaderFormat = 0;
    public static boolean is_first_hook_build = true;
    
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
    
    // ==================== Still Capture Handling ====================
    public static volatile long stillCaptureStartTime = 0;
    public static final long STILL_CAPTURE_TIMEOUT_MS = 3000;
    
    // Track ImageReader instances and their formats
    public static ConcurrentHashMap<ImageReader, Integer> imageReaderFormats = new ConcurrentHashMap<>();
    // Track ImageReader listeners for callback triggering
    public static ConcurrentHashMap<ImageReader, ImageReader.OnImageAvailableListener> imageReaderListeners = new ConcurrentHashMap<>();
    // Track which ImageReaders are for still capture (JPEG/YUV format)
    public static java.util.Set<ImageReader> stillCaptureReaders = ConcurrentHashMap.newKeySet();
    // Track ImageReader surfaces and their formats
    public static ConcurrentHashMap<Surface, Integer> surfaceFormats = new ConcurrentHashMap<>();
    // Track surfaces that should NOT receive video frames
    public static java.util.Set<Surface> incompatibleSurfaces = ConcurrentHashMap.newKeySet();
    // Flag for pending still capture
    public static AtomicBoolean pendingStillCapture = new AtomicBoolean(false);
    // Counter for capture attempts (to prevent infinite retries)
    public static AtomicInteger captureAttempts = new AtomicInteger(0);
    // Handler for callbacks
    public static Handler callbackHandler = null;
    public static Handler mainHandler = new Handler(Looper.getMainLooper());
    // Track active capture session
    public static CameraCaptureSession activeCaptureSession = null;
    
    public Context toast_content;
    
    // Static image path for still capture
    public static String still_image_path = null;
    
    // ImageReader format constants
    public static final int FORMAT_JPEG = ImageFormat.JPEG;           // 256 (0x100)
    public static final int FORMAT_YUV_420_888 = ImageFormat.YUV_420_888;  // 35 (0x23)
    public static final int FORMAT_RAW_PRIVATE = 0x1000;              // 4096 (RAW_PRIVATE)
    
    // Maximum capture attempts before giving up
    public static final int MAX_CAPTURE_ATTEMPTS = 3;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        
        // ==================== Initialize Helpers ====================
        FakeImageHelper.initialize();
        
        // ==================== Application Context Hook ====================
        XposedHelpers.findAndHookMethod("android.app.Instrumentation", lpparam.classLoader, 
            "callApplicationOnCreate", Application.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[0] instanceof Application) {
                    try {
                        toast_content = ((Application) param.args[0]).getApplicationContext();
                    } catch (Exception e) {
                        XposedBridge.log("【VCAM】" + e.toString());
                    }
                    
                    setupVideoPath(lpparam);
                }
            }
        });
        
        // ==================== Camera1 API Hooks ====================
        hookCamera1API(lpparam);
        
        // ==================== Camera2 API Hooks ====================
        hookCamera2API(lpparam);
        
        // ==================== ImageReader Hooks (CRITICAL for still capture) ====================
        hookImageReader(lpparam);
        
        // ==================== CameraCaptureSession Hooks ====================
        hookCaptureSession(lpparam);
    }
    
    /**
     * Setup the video path based on permissions
     */
    private void setupVideoPath(XC_LoadPackage.LoadPackageParam lpparam) {
        File force_private = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/private_dir.jpg");
        
        if (toast_content != null) {
            int auth_status = checkPermissions();
            
            if (auth_status < 1 || force_private.exists()) {
                // Use private directory
                File shown_file = new File(toast_content.getExternalFilesDir(null).getAbsolutePath() + "/Camera1/");
                if ((!shown_file.isDirectory()) && shown_file.exists()) {
                    shown_file.delete();
                }
                if (!shown_file.exists()) {
                    shown_file.mkdirs();
                }
                
                video_path = toast_content.getExternalFilesDir(null).getAbsolutePath() + "/Camera1/";
                showPrivateDirectoryToast(lpparam);
            } else {
                video_path = Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/";
                ensureDirectoryExists(video_path);
            }
        } else {
            video_path = Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/";
            ensureDirectoryExists(video_path);
        }
    }
    
    private int checkPermissions() {
        int auth_status = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && toast_content != null) {
            try {
                auth_status += (toast_content.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) + 1);
            } catch (Exception e) {
                XposedBridge.log("【VCAM】[permission-check]" + e.toString());
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    auth_status += (toast_content.checkSelfPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE) + 1);
                }
            } catch (Exception e) {
                XposedBridge.log("【VCAM】[permission-check]" + e.toString());
            }
        } else if (toast_content != null) {
            if (toast_content.checkCallingPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                auth_status = 2;
            }
        }
        return auth_status;
    }
    
    private void ensureDirectoryExists(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
    
    private void showPrivateDirectoryToast(XC_LoadPackage.LoadPackageParam lpparam) {
        File shown_file = new File(toast_content.getExternalFilesDir(null).getAbsolutePath() + "/Camera1/has_shown");
        File toast_force_file = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/force_show.jpg");
        
        if ((!lpparam.packageName.equals(BuildConfig.APPLICATION_ID)) && ((!shown_file.exists()) || toast_force_file.exists())) {
            try {
                if (need_to_show_toast) {
                    Toast.makeText(toast_content, lpparam.packageName + " 未授予读取本地目录权限\nCamera1目前重定向为:\n" + video_path, Toast.LENGTH_SHORT).show();
                }
                FileOutputStream fos = new FileOutputStream(shown_file);
                fos.write("shown".getBytes());
                fos.flush();
                fos.close();
            } catch (Exception e) {
                XposedBridge.log("【VCAM】[switch-dir]" + e.toString());
            }
        }
    }
    
    /**
     * Check if virtual camera is active
     */
    private boolean isVirtualCameraActive() {
        File file = new File(video_path + "virtual.mp4");
        File control_file = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/disable.jpg");
        return file.exists() && !control_file.exists();
    }
    
    /**
     * Show toast if video file doesn't exist
     */
    private void showNoVideoToast(String packageName) {
        File toast_control = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/no_toast.jpg");
        need_to_show_toast = !toast_control.exists();
        
        if (toast_content != null && need_to_show_toast) {
            mainHandler.post(() -> {
                try {
                    Toast.makeText(toast_content, "不存在替换视频\n" + packageName + "\n当前路径：" + video_path, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    XposedBridge.log("【VCAM】[toast]" + e.toString());
                }
            });
        }
    }
    
    // ==================== Camera1 API Hooks ====================
    private void hookCamera1API(XC_LoadPackage.LoadPackageParam lpparam) {
        // setPreviewTexture hook
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, 
            "setPreviewTexture", SurfaceTexture.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!isVirtualCameraActive()) {
                    showNoVideoToast(lpparam.packageName);
                    return;
                }
                
                if (is_hooked || param.args[0] == null) {
                    is_hooked = false;
                    return;
                }
                
                if (param.args[0].equals(c1_fake_texture)) {
                    return;
                }
                
                if (origin_preview_camera != null && origin_preview_camera.equals(param.thisObject)) {
                    param.args[0] = fake_SurfaceTexture;
                    return;
                }
                
                XposedBridge.log("【VCAM】创建预览");
                origin_preview_camera = (Camera) param.thisObject;
                mSurfacetexture = (SurfaceTexture) param.args[0];
                
                if (fake_SurfaceTexture != null) {
                    fake_SurfaceTexture.release();
                }
                fake_SurfaceTexture = new SurfaceTexture(10);
                param.args[0] = fake_SurfaceTexture;
            }
        });
        
        // setPreviewDisplay hook
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, 
            "setPreviewDisplay", SurfaceHolder.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!isVirtualCameraActive()) {
                    showNoVideoToast(lpparam.packageName);
                    return;
                }
                
                XposedBridge.log("【VCAM】添加Surfaceview预览");
                mcamera1 = (Camera) param.thisObject;
                ori_holder = (SurfaceHolder) param.args[0];
                
                if (c1_fake_texture != null) {
                    c1_fake_texture.release();
                }
                c1_fake_texture = new SurfaceTexture(11);
                
                if (c1_fake_surface != null) {
                    c1_fake_surface.release();
                }
                c1_fake_surface = new Surface(c1_fake_texture);
                
                is_hooked = true;
                mcamera1.setPreviewTexture(c1_fake_texture);
                param.setResult(null);
            }
        });
        
        // startPreview hook
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, 
            "startPreview", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!isVirtualCameraActive()) {
                    showNoVideoToast(lpparam.packageName);
                    return;
                }
                
                is_someone_playing = false;
                XposedBridge.log("【VCAM】开始预览");
                start_preview_camera = (Camera) param.thisObject;
                
                startCamera1Preview();
            }
        });
        
        // Preview callback hooks
        hookCamera1Callbacks(lpparam);
        
        // takePicture hook
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, 
            "takePicture", Camera.ShutterCallback.class, Camera.PictureCallback.class, 
            Camera.PictureCallback.class, Camera.PictureCallback.class, new XC_MethodHook() {
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
    }
    
    private void startCamera1Preview() {
        // SurfaceHolder preview
        if (ori_holder != null && ori_holder.getSurface().isValid()) {
            setupMediaPlayer(mplayer1, ori_holder.getSurface(), mp -> mplayer1 = mp);
        }
        
        // SurfaceTexture preview
        if (mSurfacetexture != null) {
            if (mSurface != null) {
                mSurface.release();
            }
            mSurface = new Surface(mSurfacetexture);
            setupMediaPlayer(mMediaPlayer, mSurface, mp -> mMediaPlayer = mp);
        }
    }
    
    private void setupMediaPlayer(MediaPlayer player, Surface surface, java.util.function.Consumer<MediaPlayer> setter) {
        if (player != null) {
            player.release();
        }
        player = new MediaPlayer();
        setter.accept(player);
        
        player.setSurface(surface);
        
        File sfile = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/no-silent.jpg");
        if (!(sfile.exists() && !is_someone_playing)) {
            player.setVolume(0, 0);
        } else {
            is_someone_playing = true;
        }
        
        player.setLooping(true);
        
        final MediaPlayer finalPlayer = player;
        player.setOnPreparedListener(mp -> finalPlayer.start());
        
        try {
            player.setDataSource(video_path + "virtual.mp4");
            player.prepare();
        } catch (IOException e) {
            XposedBridge.log("【VCAM】" + e.toString());
        }
    }
    
    private void hookCamera1Callbacks(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, 
            "setPreviewCallbackWithBuffer", Camera.PreviewCallback.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] != null) {
                    process_callback(param);
                }
            }
        });
        
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, 
            "setPreviewCallback", Camera.PreviewCallback.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] != null) {
                    process_callback(param);
                }
            }
        });
        
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, 
            "setOneShotPreviewCallback", Camera.PreviewCallback.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] != null) {
                    process_callback(param);
                }
            }
        });
        
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, 
            "addCallbackBuffer", byte[].class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] != null) {
                    param.args[0] = new byte[((byte[]) param.args[0]).length];
                }
            }
        });
    }
    
    // ==================== Camera2 API Hooks ====================
    private void hookCamera2API(XC_LoadPackage.LoadPackageParam lpparam) {
        // openCamera with Handler
        XposedHelpers.findAndHookMethod("android.hardware.camera2.CameraManager", lpparam.classLoader, 
            "openCamera", String.class, CameraDevice.StateCallback.class, Handler.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[1] == null || param.args[1].equals(c2_state_cb)) {
                    return;
                }
                
                if (!isVirtualCameraActive()) {
                    showNoVideoToast(lpparam.packageName);
                    return;
                }
                
                c2_state_cb = (CameraDevice.StateCallback) param.args[1];
                c2_state_callback = param.args[1].getClass();
                
                XposedBridge.log("【VCAM】1位参数初始化相机，类：" + c2_state_callback.toString());
                is_first_hook_build = true;
                process_camera2_init(c2_state_callback);
            }
        });
        
        // openCamera with Executor (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            XposedHelpers.findAndHookMethod("android.hardware.camera2.CameraManager", lpparam.classLoader, 
                "openCamera", String.class, Executor.class, CameraDevice.StateCallback.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args[2] == null || param.args[2].equals(c2_state_cb)) {
                        return;
                    }
                    
                    if (!isVirtualCameraActive()) {
                        showNoVideoToast(lpparam.packageName);
                        return;
                    }
                    
                    c2_state_cb = (CameraDevice.StateCallback) param.args[2];
                    c2_state_callback = param.args[2].getClass();
                    
                    XposedBridge.log("【VCAM】2位参数初始化相机，类：" + c2_state_callback.toString());
                    is_first_hook_build = true;
                    process_camera2_init(c2_state_callback);
                }
            });
        }
        
        // CaptureRequest.Builder hooks
        hookCaptureRequestBuilder(lpparam);
    }
    
    private void hookCaptureRequestBuilder(XC_LoadPackage.LoadPackageParam lpparam) {
        // addTarget hook
        XposedHelpers.findAndHookMethod("android.hardware.camera2.CaptureRequest.Builder", lpparam.classLoader, 
            "addTarget", Surface.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] == null || !isVirtualCameraActive()) {
                    return;
                }
                
                if (param.args[0].equals(c2_virtual_surface)) {
                    return;
                }
                
                Surface targetSurface = (Surface) param.args[0];
                String surfaceInfo = targetSurface.toString();
                Integer surfaceFormat = surfaceFormats.get(targetSurface);
                boolean isIncompatible = incompatibleSurfaces.contains(targetSurface);
                
                if (isIncompatible) {
                    XposedBridge.log("【VCAM】Incompatible surface detected (format: " + 
                        (surfaceFormat != null ? "0x" + Integer.toHexString(surfaceFormat) : "unknown") + 
                        ") - still capture will be handled specially");
                    pendingStillCapture.set(true);
                    captureAttempts.set(0);
                } else if (surfaceInfo.contains("Surface(name=null)")) {
                    // ImageReader surface
                    if (surfaceFormat == null || 
                        (surfaceFormat != FORMAT_JPEG && surfaceFormat != FORMAT_YUV_420_888)) {
                        if (c2_reader_Surfcae == null) {
                            c2_reader_Surfcae = targetSurface;
                            XposedBridge.log("【VCAM】Stored reader surface: " + surfaceInfo);
                        } else if (!c2_reader_Surfcae.equals(targetSurface) && c2_reader_Surfcae_1 == null) {
                            c2_reader_Surfcae_1 = targetSurface;
                            XposedBridge.log("【VCAM】Stored reader surface 1: " + surfaceInfo);
                        }
                    }
                } else {
                    // Preview surface
                    if (c2_preview_Surfcae == null) {
                        c2_preview_Surfcae = targetSurface;
                    } else if (!c2_preview_Surfcae.equals(targetSurface) && c2_preview_Surfcae_1 == null) {
                        c2_preview_Surfcae_1 = targetSurface;
                    }
                }
                
                XposedBridge.log("【VCAM】添加目标：" + surfaceInfo + 
                    (surfaceFormat != null ? " format: 0x" + Integer.toHexString(surfaceFormat) : "") +
                    (isIncompatible ? " [STILL_CAPTURE]" : ""));
                
                param.args[0] = c2_virtual_surface;
            }
        });
        
        // removeTarget hook
        XposedHelpers.findAndHookMethod("android.hardware.camera2.CaptureRequest.Builder", lpparam.classLoader, 
            "removeTarget", Surface.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] == null || !isVirtualCameraActive()) {
                    return;
                }
                
                Surface rm_surf = (Surface) param.args[0];
                if (rm_surf.equals(c2_preview_Surfcae)) c2_preview_Surfcae = null;
                if (rm_surf.equals(c2_preview_Surfcae_1)) c2_preview_Surfcae_1 = null;
                if (rm_surf.equals(c2_reader_Surfcae)) c2_reader_Surfcae = null;
                if (rm_surf.equals(c2_reader_Surfcae_1)) c2_reader_Surfcae_1 = null;
                
                XposedBridge.log("【VCAM】移除目标：" + param.args[0].toString());
            }
        });
        
        // build hook
        XposedHelpers.findAndHookMethod("android.hardware.camera2.CaptureRequest.Builder", lpparam.classLoader, 
            "build", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.thisObject == null || param.thisObject.equals(c2_builder)) {
                    return;
                }
                
                if (!isVirtualCameraActive()) {
                    showNoVideoToast(toast_content != null ? toast_content.getPackageName() : "unknown");
                    return;
                }
                
                c2_builder = (CaptureRequest.Builder) param.thisObject;
                XposedBridge.log("【VCAM】开始build请求");
                process_camera2_play();
            }
        });
    }
    
    // ==================== ImageReader Hooks ====================
    private void hookImageReader(XC_LoadPackage.LoadPackageParam lpparam) {
        // newInstance hook - track ImageReader creation
        XposedHelpers.findAndHookMethod("android.media.ImageReader", lpparam.classLoader, 
            "newInstance", int.class, int.class, int.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                int width = (int) param.args[0];
                int height = (int) param.args[1];
                int format = (int) param.args[2];
                
                XposedBridge.log("【VCAM】应用创建了渲染器：宽：" + width + " 高：" + height + 
                    " 格式：" + format + " (0x" + Integer.toHexString(format) + ")");
                
                c2_ori_width = width;
                c2_ori_height = height;
                imageReaderFormat = format;
            }
            
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.getResult() == null) return;
                
                ImageReader reader = (ImageReader) param.getResult();
                int format = (int) param.args[2];
                
                imageReaderFormats.put(reader, format);
                
                try {
                    Surface surface = reader.getSurface();
                    if (surface != null) {
                        surfaceFormats.put(surface, format);
                        
                        if (format == FORMAT_JPEG || format == FORMAT_YUV_420_888) {
                            incompatibleSurfaces.add(surface);
                            XposedBridge.log("【VCAM】Marked surface as incompatible: format=0x" + Integer.toHexString(format));
                        }
                    }
                } catch (Exception e) {
                    XposedBridge.log("【VCAM】Error getting ImageReader surface: " + e.getMessage());
                }
            }
        });
        
        // setOnImageAvailableListener hook
        XposedHelpers.findAndHookMethod("android.media.ImageReader", lpparam.classLoader, 
            "setOnImageAvailableListener", ImageReader.OnImageAvailableListener.class, Handler.class, 
            new XC_MethodHook() {
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
                    XposedBridge.log("【VCAM】ImageReader.setOnImageAvailableListener - format: " + format + 
                        " (0x" + Integer.toHexString(format) + "), size: " + reader.getWidth() + "x" + reader.getHeight());
                    
                    if (format == FORMAT_JPEG || format == FORMAT_YUV_420_888) {
                        stillCaptureReaders.add(reader);
                        XposedBridge.log("【VCAM】Tracking ImageReader for still capture");
                    }
                }
            }
        });
        
        // acquireNextImage hook
        XposedHelpers.findAndHookMethod("android.media.ImageReader", lpparam.classLoader, 
            "acquireNextImage", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!isVirtualCameraActive()) return;
                
                ImageReader reader = (ImageReader) param.thisObject;
                int format = reader.getImageFormat();
                
                if (pendingStillCapture.get() && (format == FORMAT_JPEG || format == FORMAT_YUV_420_888)) {
                    XposedBridge.log("【VCAM】acquireNextImage - pending still capture, returning null");
                    completeStillCapture();
                    param.setResult(null);
                }
            }
            
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                handleImageAcquisitionError(param);
            }
        });
        
        // acquireLatestImage hook - CRITICAL for still capture
        XposedHelpers.findAndHookMethod("android.media.ImageReader", lpparam.classLoader, 
            "acquireLatestImage", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!isVirtualCameraActive()) return;
                
                ImageReader reader = (ImageReader) param.thisObject;
                int format = reader.getImageFormat();
                
                if (pendingStillCapture.get() && (format == FORMAT_JPEG || format == FORMAT_YUV_420_888)) {
                    XposedBridge.log("【VCAM】acquireLatestImage - still capture ImageReader, returning null to prevent hang");
                    completeStillCapture();
                    param.setResult(null);
                }
            }
            
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                handleImageAcquisitionError(param);
            }
        });
    }
    
    private void handleImageAcquisitionError(XC_MethodHook.MethodHookParam param) {
        Throwable t = param.getThrowable();
        if (t != null) {
            if (t instanceof UnsupportedOperationException && 
                t.getMessage() != null && t.getMessage().contains("doesn't match")) {
                XposedBridge.log("【VCAM】Caught ImageReader format mismatch: " + t.getMessage());
                param.setResult(null);
                param.setThrowable(null);
                completeStillCapture();
            } else if (t instanceof IllegalStateException) {
                XposedBridge.log("【VCAM】Caught IllegalStateException: " + t.getMessage());
                param.setResult(null);
                param.setThrowable(null);
                completeStillCapture();
            }
        }
    }
    
    // ==================== CameraCaptureSession Hooks ====================
    private void hookCaptureSession(XC_LoadPackage.LoadPackageParam lpparam) {
        // capture hook
        XposedHelpers.findAndHookMethod("android.hardware.camera2.CameraCaptureSession", lpparam.classLoader, 
            "capture", CaptureRequest.class, CameraCaptureSession.CaptureCallback.class, Handler.class, 
            new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!isVirtualCameraActive()) return;
                
                Handler handler = (Handler) param.args[2];
                if (handler != null) {
                    callbackHandler = handler;
                }
                
                if (pendingStillCapture.get()) {
                    XposedBridge.log("【VCAM】CameraCaptureSession.capture - pending still capture detected");
                    stillCaptureStartTime = System.currentTimeMillis();
                    
                    // Schedule timeout handler
                    Handler targetHandler = handler != null ? handler : mainHandler;
                    targetHandler.postDelayed(() -> {
                        if (pendingStillCapture.get()) {
                            XposedBridge.log("【VCAM】Still capture timeout - forcing completion");
                            triggerFakeImageCallback();
                            completeStillCapture();
                        }
                    }, STILL_CAPTURE_TIMEOUT_MS);
                }
            }
        });
        
        // onCaptureCompleted hook
        XposedHelpers.findAndHookMethod("android.hardware.camera2.CameraCaptureSession.CaptureCallback", 
            lpparam.classLoader, "onCaptureCompleted", 
            CameraCaptureSession.class, CaptureRequest.class, TotalCaptureResult.class,
            new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (pendingStillCapture.get()) {
                    XposedBridge.log("【VCAM】onCaptureCompleted - triggering fake image callback");
                    
                    Handler targetHandler = callbackHandler != null ? callbackHandler : mainHandler;
                    targetHandler.postDelayed(() -> {
                        triggerFakeImageCallback();
                    }, 50);
                }
            }
        });
        
        // onCaptureFailed hook
        XposedHelpers.findAndHookMethod("android.hardware.camera2.CameraCaptureSession.CaptureCallback", 
            lpparam.classLoader, "onCaptureFailed", 
            CameraCaptureSession.class, CaptureRequest.class, CaptureFailure.class,
            new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                XposedBridge.log("【VCAM】onCaptureFailed - reason: " + ((CaptureFailure) param.args[2]).getReason());
                completeStillCapture();
            }
        });
    }
    
    // ==================== Still Capture Handling ====================
    
    /**
     * Complete the still capture process
     */
    private void completeStillCapture() {
        pendingStillCapture.set(false);
        stillCaptureStartTime = 0;
        captureAttempts.set(0);
    }
    
    /**
     * Trigger fake image callback for all tracked still capture readers
     */
    private void triggerFakeImageCallback() {
        XposedBridge.log("【VCAM】Triggering ImageAvailable callbacks for still capture readers");
        
        for (java.util.Map.Entry<ImageReader, ImageReader.OnImageAvailableListener> entry : 
                imageReaderListeners.entrySet()) {
            ImageReader reader = entry.getKey();
            ImageReader.OnImageAvailableListener listener = entry.getValue();
            
            if (stillCaptureReaders.contains(reader) && listener != null) {
                try {
                    int attempts = captureAttempts.incrementAndGet();
                    if (attempts <= MAX_CAPTURE_ATTEMPTS) {
                        XposedBridge.log("【VCAM】Triggering onImageAvailable for format: " + 
                            reader.getImageFormat() + " (attempt " + attempts + ")");
                        listener.onImageAvailable(reader);
                    } else {
                        XposedBridge.log("【VCAM】Max capture attempts reached, skipping callback");
                    }
                } catch (Exception e) {
                    XposedBridge.log("【VCAM】Error triggering callback: " + e.getMessage());
                }
                break;  // Only trigger for one reader
            }
        }
        
        completeStillCapture();
    }
    
    // ==================== Camera2 Video Playback ====================
    
    private void process_camera2_play() {
        XposedBridge.log("【VCAM】Camera2处理过程执行中...");
        
        // Check compatibility before feeding video
        boolean reader1Compatible = c2_reader_Surfcae != null && !incompatibleSurfaces.contains(c2_reader_Surfcae);
        boolean reader2Compatible = c2_reader_Surfcae_1 != null && !incompatibleSurfaces.contains(c2_reader_Surfcae_1);
        
        // Start video decoder for compatible reader surfaces
        if (reader1Compatible) {
            startVideoDecoder(c2_reader_Surfcae, () -> c2_hw_decode_obj, v -> c2_hw_decode_obj = v);
        }
        
        if (reader2Compatible) {
            startVideoDecoder(c2_reader_Surfcae_1, () -> c2_hw_decode_obj_1, v -> c2_hw_decode_obj_1 = v);
        }
        
        // Start MediaPlayer for preview surfaces
        if (c2_preview_Surfcae != null) {
            startPreviewPlayer(c2_preview_Surfcae, () -> c2_player, v -> c2_player = v);
        }
        
        if (c2_preview_Surfcae_1 != null) {
            startPreviewPlayer(c2_preview_Surfcae_1, () -> c2_player_1, v -> c2_player_1 = v);
        }
        
        XposedBridge.log("【VCAM】Camera2处理过程完全执行");
    }
    
    private void startVideoDecoder(Surface surface, 
                                    java.util.function.Supplier<VideoToFrames> getter,
                                    java.util.function.Consumer<VideoToFrames> setter) {
        VideoToFrames decoder = getter.get();
        if (decoder != null) {
            decoder.stopDecode();
        }
        
        decoder = new VideoToFrames();
        setter.accept(decoder);
        
        try {
            if (imageReaderFormat == FORMAT_JPEG) {
                decoder.setSaveFrames("null", OutputImageFormat.JPEG);
            } else {
                decoder.setSaveFrames("null", OutputImageFormat.NV21);
            }
            decoder.setUseOriginalResolution(true);
            decoder.set_surfcae(surface);
            decoder.decode(video_path + "virtual.mp4");
            XposedBridge.log("【VCAM】Started video decoder for surface");
        } catch (Throwable t) {
            XposedBridge.log("【VCAM】Error starting decoder: " + t.getMessage());
        }
    }
    
    private void startPreviewPlayer(Surface surface,
                                     java.util.function.Supplier<MediaPlayer> getter,
                                     java.util.function.Consumer<MediaPlayer> setter) {
        MediaPlayer player = getter.get();
        if (player != null) {
            player.release();
        }
        
        player = new MediaPlayer();
        setter.accept(player);
        
        player.setSurface(surface);
        
        File sfile = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/no-silent.jpg");
        if (!sfile.exists()) {
            player.setVolume(0, 0);
        }
        player.setLooping(true);
        
        final MediaPlayer finalPlayer = player;
        player.setOnPreparedListener(mp -> finalPlayer.start());
        
        try {
            player.setDataSource(video_path + "virtual.mp4");
            player.prepare();
        } catch (Exception e) {
            XposedBridge.log("【VCAM】[c2player] " + e.getMessage());
        }
    }
    
    private Surface create_virtual_surface() {
        if (need_recreate) {
            if (c2_virtual_surfaceTexture != null) {
                c2_virtual_surfaceTexture.release();
            }
            if (c2_virtual_surface != null) {
                c2_virtual_surface.release();
            }
            c2_virtual_surfaceTexture = new SurfaceTexture(15);
            c2_virtual_surface = new Surface(c2_virtual_surfaceTexture);
            need_recreate = false;
        } else if (c2_virtual_surface == null) {
            need_recreate = true;
            c2_virtual_surface = create_virtual_surface();
        }
        XposedBridge.log("【VCAM】【重建垃圾场】" + c2_virtual_surface.toString());
        return c2_virtual_surface;
    }
    
    private void process_camera2_init(Class hooked_class) {
        XposedHelpers.findAndHookMethod(hooked_class, "onOpened", CameraDevice.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // Reset state
                need_recreate = true;
                create_virtual_surface();
                
                // Cleanup existing players/decoders
                cleanupCamera2Resources();
                
                // Reset surfaces
                c2_preview_Surfcae = null;
                c2_preview_Surfcae_1 = null;
                c2_reader_Surfcae = null;
                c2_reader_Surfcae_1 = null;
                is_first_hook_build = true;
                
                XposedBridge.log("【VCAM】打开相机C2");
                
                if (!isVirtualCameraActive()) {
                    showNoVideoToast(toast_content != null ? toast_content.getPackageName() : "unknown");
                    return;
                }
                
                // Hook createCaptureSession variants
                hookCaptureSessionCreation(param.args[0].getClass());
            }
        });
        
        XposedHelpers.findAndHookMethod(hooked_class, "onError", CameraDevice.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                XposedBridge.log("【VCAM】相机错误 onError: " + (int) param.args[1]);
            }
        });
        
        XposedHelpers.findAndHookMethod(hooked_class, "onDisconnected", CameraDevice.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                XposedBridge.log("【VCAM】相机断开 onDisconnected");
            }
        });
    }
    
    private void hookCaptureSessionCreation(Class<?> cameraDeviceClass) {
        // createCaptureSession with List
        XposedHelpers.findAndHookMethod(cameraDeviceClass, "createCaptureSession", 
            List.class, CameraCaptureSession.StateCallback.class, Handler.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] != null) {
                    XposedBridge.log("【VCAM】createCaptureSession 原始:" + param.args[0].toString());
                    param.args[0] = Arrays.asList(c2_virtual_surface);
                    if (param.args[1] != null) {
                        process_camera2Session_callback((CameraCaptureSession.StateCallback) param.args[1]);
                    }
                }
            }
        });
        
        // createCaptureSession with SessionConfiguration (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            XposedHelpers.findAndHookMethod(cameraDeviceClass, "createCaptureSession", 
                SessionConfiguration.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args[0] != null) {
                        XposedBridge.log("【VCAM】执行了 createCaptureSession with SessionConfiguration");
                        sessionConfiguration = (SessionConfiguration) param.args[0];
                        outputConfiguration = new OutputConfiguration(c2_virtual_surface);
                        fake_sessionConfiguration = new SessionConfiguration(
                            sessionConfiguration.getSessionType(),
                            Arrays.asList(outputConfiguration),
                            sessionConfiguration.getExecutor(),
                            sessionConfiguration.getStateCallback()
                        );
                        param.args[0] = fake_sessionConfiguration;
                        process_camera2Session_callback(sessionConfiguration.getStateCallback());
                    }
                }
            });
        }
        
        // Other session creation methods
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            XposedHelpers.findAndHookMethod(cameraDeviceClass, "createCaptureSessionByOutputConfigurations", 
                List.class, CameraCaptureSession.StateCallback.class, Handler.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args[0] != null) {
                        outputConfiguration = new OutputConfiguration(c2_virtual_surface);
                        param.args[0] = Arrays.asList(outputConfiguration);
                        XposedBridge.log("【VCAM】执行了 createCaptureSessionByOutputConfigurations");
                        if (param.args[1] != null) {
                            process_camera2Session_callback((CameraCaptureSession.StateCallback) param.args[1]);
                        }
                    }
                }
            });
        }
    }
    
    private void cleanupCamera2Resources() {
        if (c2_player != null) {
            try {
                c2_player.stop();
                c2_player.release();
            } catch (Exception e) { }
            c2_player = null;
        }
        if (c2_player_1 != null) {
            try {
                c2_player_1.stop();
                c2_player_1.release();
            } catch (Exception e) { }
            c2_player_1 = null;
        }
        if (c2_hw_decode_obj != null) {
            c2_hw_decode_obj.stopDecode();
            c2_hw_decode_obj = null;
        }
        if (c2_hw_decode_obj_1 != null) {
            c2_hw_decode_obj_1.stopDecode();
            c2_hw_decode_obj_1 = null;
        }
    }
    
    private void process_camera2Session_callback(CameraCaptureSession.StateCallback callback) {
        if (callback == null) return;
        
        XposedHelpers.findAndHookMethod(callback.getClass(), "onConfigured", 
            CameraCaptureSession.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                XposedBridge.log("【VCAM】onConfigured: " + param.args[0].toString());
                activeCaptureSession = (CameraCaptureSession) param.args[0];
            }
        });
        
        XposedHelpers.findAndHookMethod(callback.getClass(), "onConfigureFailed", 
            CameraCaptureSession.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                XposedBridge.log("【VCAM】onConfigureFailed: " + param.args[0].toString());
            }
        });
        
        XposedHelpers.findAndHookMethod(callback.getClass(), "onClosed", 
            CameraCaptureSession.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                XposedBridge.log("【VCAM】onClosed: " + param.args[0].toString());
            }
        });
    }
    
    // ==================== Camera1 Helper Methods ====================
    
    private void process_callback(XC_MethodHook.MethodHookParam param) {
        Class preview_cb_class = param.args[0].getClass();
        
        if (!isVirtualCameraActive()) {
            showNoVideoToast(toast_content != null ? toast_content.getPackageName() : "unknown");
            return;
        }
        
        XposedHelpers.findAndHookMethod(preview_cb_class, "onPreviewFrame", 
            byte[].class, android.hardware.Camera.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam paramd) throws Throwable {
                Camera localcam = (android.hardware.Camera) paramd.args[1];
                
                if (localcam.equals(camera_onPreviewFrame)) {
                    while (data_buffer == null) { Thread.yield(); }
                    System.arraycopy(data_buffer, 0, paramd.args[0], 0, 
                        Math.min(data_buffer.length, ((byte[]) paramd.args[0]).length));
                } else {
                    camera_callback_calss = preview_cb_class;
                    camera_onPreviewFrame = localcam;
                    mwidth = camera_onPreviewFrame.getParameters().getPreviewSize().width;
                    mhight = camera_onPreviewFrame.getParameters().getPreviewSize().height;
                    
                    XposedBridge.log("【VCAM】帧预览回调初始化：宽：" + mwidth + " 高：" + mhight);
                    
                    if (hw_decode_obj != null) {
                        hw_decode_obj.stopDecode();
                    }
                    hw_decode_obj = new VideoToFrames();
                    hw_decode_obj.setSaveFrames("", OutputImageFormat.NV21);
                    hw_decode_obj.decode(video_path + "virtual.mp4");
                    
                    while (data_buffer == null) { Thread.yield(); }
                    System.arraycopy(data_buffer, 0, paramd.args[0], 0, 
                        Math.min(data_buffer.length, ((byte[]) paramd.args[0]).length));
                }
            }
        });
    }
    
    private void process_a_shot_jpeg(XC_MethodHook.MethodHookParam param, int index) {
        Class callback = param.args[index].getClass();
        
        XposedHelpers.findAndHookMethod(callback, "onPictureTaken", 
            byte[].class, android.hardware.Camera.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam paramd) throws Throwable {
                if (!isVirtualCameraActive()) return;
                
                try {
                    Camera localcam = (Camera) paramd.args[1];
                    onemwidth = localcam.getParameters().getPreviewSize().width;
                    onemhight = localcam.getParameters().getPreviewSize().height;
                    
                    XposedBridge.log("【VCAM】JPEG拍照回调：宽：" + onemwidth + " 高：" + onemhight);
                    
                    Bitmap pict = getBMP(video_path + "1000.bmp");
                    if (pict != null) {
                        ByteArrayOutputStream temp_array = new ByteArrayOutputStream();
                        pict.compress(Bitmap.CompressFormat.JPEG, 100, temp_array);
                        paramd.args[0] = temp_array.toByteArray();
                        pict.recycle();
                    }
                } catch (Exception e) {
                    XposedBridge.log("【VCAM】" + e.toString());
                }
            }
        });
    }
    
    private void process_a_shot_YUV(XC_MethodHook.MethodHookParam param) {
        Class callback = param.args[1].getClass();
        
        XposedHelpers.findAndHookMethod(callback, "onPictureTaken", 
            byte[].class, android.hardware.Camera.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam paramd) throws Throwable {
                if (!isVirtualCameraActive()) return;
                
                try {
                    Camera localcam = (Camera) paramd.args[1];
                    onemwidth = localcam.getParameters().getPreviewSize().width;
                    onemhight = localcam.getParameters().getPreviewSize().height;
                    
                    XposedBridge.log("【VCAM】YUV拍照回调：宽：" + onemwidth + " 高：" + onemhight);
                    
                    input = getYUVByBitmap(getBMP(video_path + "1000.bmp"));
                    if (input != null) {
                        paramd.args[0] = input;
                    }
                } catch (Exception e) {
                    XposedBridge.log("【VCAM】" + e.toString());
                }
            }
        });
    }
    
    // ==================== Utility Methods ====================
    
    private Bitmap getBMP(String file) {
        return BitmapFactory.decodeFile(file);
    }
    
    private static byte[] rgb2YCbCr420(int[] pixels, int width, int height) {
        int len = width * height;
        byte[] yuv = new byte[len * 3 / 2];
        int y, u, v;
        
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int rgb = (pixels[i * width + j]) & 0x00FFFFFF;
                int r = rgb & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = (rgb >> 16) & 0xFF;
                
                y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                
                y = Math.max(16, Math.min(255, y));
                u = Math.max(0, Math.min(255, u));
                v = Math.max(0, Math.min(255, v));
                
                yuv[i * width + j] = (byte) y;
                yuv[len + (i >> 1) * width + (j & ~1)] = (byte) u;
                yuv[len + (i >> 1) * width + (j & ~1) + 1] = (byte) v;
            }
        }
        return yuv;
    }
    
    private static byte[] getYUVByBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;
        
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        return rgb2YCbCr420(pixels, width, height);
    }
}
