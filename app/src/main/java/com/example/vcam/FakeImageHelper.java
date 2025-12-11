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
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import de.robv.android.xposed.XposedBridge;

/**
 * Helper class to generate and inject fake images for still capture
 * when the virtual camera is active.
 * 
 * CRITICAL: This class handles the conversion of still images to formats
 * expected by apps (JPEG, YUV_420_888) when they capture photos.
 */
public class FakeImageHelper {
    
    private static final String TAG = "【VCAM】FakeImageHelper";
    private static ImageReader fakeJpegReader = null;
    private static ImageReader fakeYuvReader = null;
    private static HandlerThread handlerThread = null;
    private static Handler handler = null;
    
    // Cached bitmap to avoid repeated file I/O
    private static Bitmap cachedStillBitmap = null;
    private static String cachedStillPath = null;
    private static long cachedStillLastModified = 0;
    
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
     * Get the handler for callbacks
     */
    public static Handler getHandler() {
        initialize();
        return handler;
    }
    
    /**
     * Get the path to the still capture replacement image
     * Searches for various image formats in order of preference
     */
    public static String getStillImagePath(String videoPath) {
        // Try different image formats in order of preference
        String[] extensions = {
            "1000.jpg", "1000.jpeg", "1000.bmp", "1000.png",
            "still.jpg", "still.jpeg", "still.bmp", "still.png",
            "capture.jpg", "capture.jpeg", "capture.bmp", "capture.png"
        };
        
        for (String ext : extensions) {
            String path = videoPath + ext;
            File file = new File(path);
            if (file.exists() && file.canRead()) {
                XposedBridge.log(TAG + " Found still image at: " + path);
                return path;
            }
        }
        
        XposedBridge.log(TAG + " No still image found in: " + videoPath);
        return null;
    }
    
    /**
     * Check if a still image exists
     */
    public static boolean hasStillImage(String videoPath) {
        return getStillImagePath(videoPath) != null;
    }
    
    /**
     * Load bitmap from the still image path with caching
     */
    public static Bitmap loadStillImageBitmap(String videoPath, int targetWidth, int targetHeight) {
        String imagePath = getStillImagePath(videoPath);
        if (imagePath == null) {
            return createPlaceholderBitmap(targetWidth, targetHeight);
        }
        
        try {
            File imageFile = new File(imagePath);
            long lastModified = imageFile.lastModified();
            
            // Check cache validity
            if (cachedStillBitmap != null && !cachedStillBitmap.isRecycled() &&
                imagePath.equals(cachedStillPath) && lastModified == cachedStillLastModified) {
                // Return scaled copy of cached bitmap
                if (targetWidth > 0 && targetHeight > 0) {
                    return Bitmap.createScaledBitmap(cachedStillBitmap, targetWidth, targetHeight, true);
                }
                return cachedStillBitmap.copy(cachedStillBitmap.getConfig(), false);
            }
            
            // Load new bitmap
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            
            // First, decode bounds only to calculate sample size
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imagePath, options);
            
            // Calculate sample size for memory efficiency
            if (targetWidth > 0 && targetHeight > 0) {
                options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight);
            }
            
            options.inJustDecodeBounds = false;
            Bitmap originalBitmap = BitmapFactory.decodeFile(imagePath, options);
            
            if (originalBitmap == null) {
                XposedBridge.log(TAG + " Failed to decode bitmap from: " + imagePath);
                return createPlaceholderBitmap(targetWidth, targetHeight);
            }
            
            // Update cache
            if (cachedStillBitmap != null && !cachedStillBitmap.isRecycled()) {
                cachedStillBitmap.recycle();
            }
            cachedStillBitmap = originalBitmap;
            cachedStillPath = imagePath;
            cachedStillLastModified = lastModified;
            
            // Scale to target dimensions if needed
            if (targetWidth > 0 && targetHeight > 0 && 
                (originalBitmap.getWidth() != targetWidth || originalBitmap.getHeight() != targetHeight)) {
                Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true);
                return scaledBitmap;
            }
            
            return originalBitmap.copy(originalBitmap.getConfig(), false);
            
        } catch (Exception e) {
            XposedBridge.log(TAG + " Error loading bitmap: " + e.getMessage());
            return createPlaceholderBitmap(targetWidth, targetHeight);
        }
    }
    
    /**
     * Calculate optimal sample size for bitmap loading
     */
    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        
        return inSampleSize;
    }
    
    /**
     * Create a placeholder bitmap when no still image is available
     */
    public static Bitmap createPlaceholderBitmap(int width, int height) {
        if (width <= 0) width = 1920;
        if (height <= 0) height = 1080;
        
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        // Fill with a gray color
        bitmap.eraseColor(0xFF808080);
        
        XposedBridge.log(TAG + " Created placeholder bitmap: " + width + "x" + height);
        return bitmap;
    }
    
    /**
     * Convert bitmap to JPEG byte array
     */
    public static byte[] bitmapToJpeg(Bitmap bitmap, int quality) {
        if (bitmap == null) return null;
        
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
            byte[] result = outputStream.toByteArray();
            outputStream.close();
            XposedBridge.log(TAG + " Converted bitmap to JPEG: " + result.length + " bytes");
            return result;
        } catch (Exception e) {
            XposedBridge.log(TAG + " Error converting bitmap to JPEG: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Create fake JPEG data from still image
     */
    public static byte[] createFakeJpegData(String videoPath, int width, int height) {
        Bitmap bitmap = loadStillImageBitmap(videoPath, width, height);
        if (bitmap == null) {
            XposedBridge.log(TAG + " No still image found, creating placeholder");
            bitmap = createPlaceholderBitmap(width, height);
        }
        
        byte[] jpegData = bitmapToJpeg(bitmap, 90);
        
        // Don't recycle if it's the cached bitmap
        if (bitmap != cachedStillBitmap) {
            bitmap.recycle();
        }
        
        return jpegData;
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
                if (i % 2 == 0 && j % 2 == 0 && uvIndex < uvSize) {
                    uPlane[uvIndex] = (byte) Math.max(0, Math.min(255, u));
                    vPlane[uvIndex] = (byte) Math.max(0, Math.min(255, v));
                    uvIndex++;
                }
            }
        }
        
        return new byte[][] {yPlane, uPlane, vPlane};
    }
    
    /**
     * Create fake YUV data from still image
     */
    public static byte[][] createFakeYuvData(String videoPath, int width, int height) {
        Bitmap bitmap = loadStillImageBitmap(videoPath, width, height);
        if (bitmap == null) {
            XposedBridge.log(TAG + " No still image found, creating placeholder for YUV");
            bitmap = createPlaceholderBitmap(width, height);
        }
        
        byte[][] yuvPlanes = bitmapToYuv420Planes(bitmap);
        
        // Don't recycle if it's the cached bitmap
        if (bitmap != cachedStillBitmap) {
            bitmap.recycle();
        }
        
        return yuvPlanes;
    }
    
    /**
     * Create fake NV21 data from still image
     */
    public static byte[] createFakeNV21Data(String videoPath, int width, int height) {
        Bitmap bitmap = loadStillImageBitmap(videoPath, width, height);
        if (bitmap == null) {
            XposedBridge.log(TAG + " No still image found, creating placeholder for NV21");
            bitmap = createPlaceholderBitmap(width, height);
        }
        
        byte[] nv21Data = bitmapToNV21(bitmap);
        
        // Don't recycle if it's the cached bitmap
        if (bitmap != cachedStillBitmap) {
            bitmap.recycle();
        }
        
        return nv21Data;
    }
    
    /**
     * Inject fake image into ImageReader by writing to file and simulating acquisition
     * This is a workaround since we can't directly write to ImageReader buffers
     * 
     * @param reader The target ImageReader
     * @param videoPath Path to the video directory containing still images
     * @param listener The OnImageAvailableListener to trigger
     * @param callbackHandler Handler for callbacks
     * @return true if injection was initiated, false otherwise
     */
    public static boolean injectFakeImage(ImageReader reader, String videoPath,
                                          ImageReader.OnImageAvailableListener listener,
                                          Handler callbackHandler) {
        if (reader == null || listener == null) {
            XposedBridge.log(TAG + " Cannot inject: reader or listener is null");
            return false;
        }
        
        int format = reader.getImageFormat();
        int width = reader.getWidth();
        int height = reader.getHeight();
        
        XposedBridge.log(TAG + " Attempting to inject fake image: " + width + "x" + height + 
                        " format: " + format + " (0x" + Integer.toHexString(format) + ")");
        
        // For JPEG format, we can't directly inject - the ImageReader needs actual camera data
        // Instead, we trigger the callback and let our hooked acquireLatestImage handle it
        if (format == ImageFormat.JPEG || format == ImageFormat.YUV_420_888) {
            Handler targetHandler = callbackHandler != null ? callbackHandler : getHandler();
            
            if (targetHandler != null) {
                targetHandler.post(() -> {
                    try {
                        XposedBridge.log(TAG + " Triggering onImageAvailable callback");
                        listener.onImageAvailable(reader);
                    } catch (Exception e) {
                        XposedBridge.log(TAG + " Error triggering callback: " + e.getMessage());
                    }
                });
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Cleanup resources
     */
    public static void cleanup() {
        if (fakeJpegReader != null) {
            try {
                fakeJpegReader.close();
            } catch (Exception e) {
                // Ignore
            }
            fakeJpegReader = null;
        }
        if (fakeYuvReader != null) {
            try {
                fakeYuvReader.close();
            } catch (Exception e) {
                // Ignore
            }
            fakeYuvReader = null;
        }
        if (handlerThread != null) {
            handlerThread.quitSafely();
            handlerThread = null;
            handler = null;
        }
        if (cachedStillBitmap != null && !cachedStillBitmap.isRecycled()) {
            cachedStillBitmap.recycle();
            cachedStillBitmap = null;
        }
        cachedStillPath = null;
    }
    
    /**
     * Clear the bitmap cache
     */
    public static void clearCache() {
        if (cachedStillBitmap != null && !cachedStillBitmap.isRecycled()) {
            cachedStillBitmap.recycle();
        }
        cachedStillBitmap = null;
        cachedStillPath = null;
        cachedStillLastModified = 0;
    }
}
