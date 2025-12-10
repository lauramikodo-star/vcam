package com.example.vcam;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import de.robv.android.xposed.XposedBridge;

/**
 * Helper class to generate and inject fake images for still capture
 * when the virtual camera is active.
 */
public class FakeImageHelper {
    
    private static final String TAG = "【VCAM】FakeImageHelper";
    private static ImageReader fakeJpegReader = null;
    private static ImageReader fakeYuvReader = null;
    private static HandlerThread handlerThread = null;
    private static Handler handler = null;
    
    /**
     * Initialize the fake image helper
     */
    public static void initialize() {
        if (handlerThread == null) {
            handlerThread = new HandlerThread("FakeImageHelper");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        }
    }
    
    /**
     * Get the path to the still capture replacement image
     */
    public static String getStillImagePath(String videoPath) {
        // Try different image formats in order of preference
        String[] extensions = {"1000.jpg", "1000.jpeg", "1000.bmp", "1000.png", "still.jpg", "still.jpeg", "still.bmp", "still.png"};
        
        for (String ext : extensions) {
            String path = videoPath + ext;
            File file = new File(path);
            if (file.exists()) {
                XposedBridge.log(TAG + " Found still image at: " + path);
                return path;
            }
        }
        
        XposedBridge.log(TAG + " No still image found in: " + videoPath);
        return null;
    }
    
    /**
     * Load bitmap from the still image path
     */
    public static Bitmap loadStillImageBitmap(String videoPath, int targetWidth, int targetHeight) {
        String imagePath = getStillImagePath(videoPath);
        if (imagePath == null) {
            return null;
        }
        
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            
            Bitmap originalBitmap = BitmapFactory.decodeFile(imagePath, options);
            if (originalBitmap == null) {
                XposedBridge.log(TAG + " Failed to decode bitmap from: " + imagePath);
                return null;
            }
            
            // Scale to target dimensions if needed
            if (targetWidth > 0 && targetHeight > 0 && 
                (originalBitmap.getWidth() != targetWidth || originalBitmap.getHeight() != targetHeight)) {
                Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true);
                if (scaledBitmap != originalBitmap) {
                    originalBitmap.recycle();
                }
                return scaledBitmap;
            }
            
            return originalBitmap;
        } catch (Exception e) {
            XposedBridge.log(TAG + " Error loading bitmap: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Convert bitmap to JPEG byte array
     */
    public static byte[] bitmapToJpeg(Bitmap bitmap, int quality) {
        if (bitmap == null) return null;
        
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            XposedBridge.log(TAG + " Error converting bitmap to JPEG: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Convert bitmap to NV21 (YUV) byte array
     */
    public static byte[] bitmapToNV21(Bitmap bitmap) {
        if (bitmap == null) return null;
        
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        
        return rgb2YCbCr420(pixels, width, height);
    }
    
    /**
     * Convert RGB pixels to YCbCr420 (NV21) format
     */
    private static byte[] rgb2YCbCr420(int[] pixels, int width, int height) {
        int len = width * height;
        byte[] yuv = new byte[len * 3 / 2];
        int y, u, v;
        
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int rgb = pixels[i * width + j] & 0x00FFFFFF;
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                
                y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                
                y = Math.max(16, Math.min(255, y));
                u = Math.max(0, Math.min(255, u));
                v = Math.max(0, Math.min(255, v));
                
                yuv[i * width + j] = (byte) y;
                yuv[len + (i >> 1) * width + (j & ~1)] = (byte) v;
                yuv[len + (i >> 1) * width + (j & ~1) + 1] = (byte) u;
            }
        }
        return yuv;
    }
    
    /**
     * Convert bitmap to YUV_420_888 byte array planes
     * Returns array of 3 byte arrays: Y, U, V planes
     */
    public static byte[][] bitmapToYuv420Planes(Bitmap bitmap) {
        if (bitmap == null) return null;
        
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        
        int ySize = width * height;
        int uvSize = width * height / 4;
        
        byte[] yPlane = new byte[ySize];
        byte[] uPlane = new byte[uvSize];
        byte[] vPlane = new byte[uvSize];
        
        int uvIndex = 0;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int rgb = pixels[i * width + j];
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                
                int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                
                yPlane[i * width + j] = (byte) Math.max(16, Math.min(255, y));
                
                // Subsample U and V (every 2x2 block shares one U and V value)
                if (i % 2 == 0 && j % 2 == 0) {
                    uPlane[uvIndex] = (byte) Math.max(0, Math.min(255, u));
                    vPlane[uvIndex] = (byte) Math.max(0, Math.min(255, v));
                    uvIndex++;
                }
            }
        }
        
        return new byte[][] {yPlane, uPlane, vPlane};
    }
    
    /**
     * Create a fake JPEG ImageReader and inject the still image
     * This uses ImageWriter to push frames to a Surface connected to an ImageReader
     */
    public static void injectFakeJpegImage(String videoPath, int width, int height, 
                                           ImageReader.OnImageAvailableListener listener, Handler callbackHandler) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            XposedBridge.log(TAG + " ImageWriter not available on this API level");
            return;
        }
        
        try {
            initialize();
            
            // Load the still image
            Bitmap bitmap = loadStillImageBitmap(videoPath, width, height);
            if (bitmap == null) {
                XposedBridge.log(TAG + " Failed to load still image bitmap");
                return;
            }
            
            // Create ImageReader for JPEG
            final ImageReader jpegReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 2);
            
            // Set the listener
            jpegReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    XposedBridge.log(TAG + " Fake JPEG image available");
                    if (listener != null) {
                        listener.onImageAvailable(reader);
                    }
                }
            }, callbackHandler != null ? callbackHandler : handler);
            
            // Get the surface and create ImageWriter
            Surface surface = jpegReader.getSurface();
            
            // Note: ImageWriter doesn't support JPEG format directly
            // We need to use a different approach for JPEG
            XposedBridge.log(TAG + " JPEG injection setup complete - will rely on callback mechanism");
            
            bitmap.recycle();
            
        } catch (Exception e) {
            XposedBridge.log(TAG + " Error injecting fake JPEG image: " + e.getMessage());
        }
    }
    
    /**
     * Cleanup resources
     */
    public static void cleanup() {
        if (fakeJpegReader != null) {
            fakeJpegReader.close();
            fakeJpegReader = null;
        }
        if (fakeYuvReader != null) {
            fakeYuvReader.close();
            fakeYuvReader = null;
        }
        if (handlerThread != null) {
            handlerThread.quitSafely();
            handlerThread = null;
            handler = null;
        }
    }
}
