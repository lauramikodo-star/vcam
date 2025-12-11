package com.example.vcam;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XposedBridge;

/**
 * Video decoder that extracts frames from a video file and renders them to a Surface.
 * Modified from https://github.com/zhantong/Android-VideoToImages
 * 
 * CRITICAL FIXES:
 * - Graceful handling of surface destruction during playback
 * - Proper cleanup on errors
 * - Thread interruption handling
 */
public class VideoToFrames implements Runnable {
    private static final String TAG = "【VCAM】VideoToFrames";
    private static final boolean VERBOSE = false;
    private static final long DEFAULT_TIMEOUT_US = 10000;

    private static final int COLOR_FormatI420 = 1;
    private static final int COLOR_FormatNV21 = 2;

    private final int decodeColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;

    private LinkedBlockingQueue<byte[]> mQueue;
    private OutputImageFormat outputImageFormat;
    private AtomicBoolean stopDecode = new AtomicBoolean(false);
    private AtomicBoolean isDecoding = new AtomicBoolean(false);
    private AtomicBoolean surfaceValid = new AtomicBoolean(true);

    private String videoFilePath;
    private Throwable throwable;
    private Thread childThread;
    private Surface play_surf;
    
    // Target dimensions for output scaling (from ImageReader)
    private int targetWidth = 0;
    private int targetHeight = 0;
    // Actual video dimensions
    private int videoWidth = 0;
    private int videoHeight = 0;
    // Flag to use original video resolution
    private boolean useOriginalResolution = true;  // Default to true for better compatibility

    private Callback callback;
    
    // Error tracking
    private int consecutiveErrors = 0;
    private static final int MAX_CONSECUTIVE_ERRORS = 5;

    public interface Callback {
        void onFinishDecode();
        void onDecodeFrame(int index);
        default void onDecodeError(String message) {}
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setEnqueue(LinkedBlockingQueue<byte[]> queue) {
        mQueue = queue;
    }

    public void setSaveFrames(String dir, OutputImageFormat imageFormat) throws IOException {
        outputImageFormat = imageFormat;
    }

    public void set_surfcae(Surface player_surface) {
        if (player_surface != null) {
            play_surf = player_surface;
            surfaceValid.set(player_surface.isValid());
        }
    }
    
    /**
     * Set target dimensions for output
     * If both are 0 or negative, use original video resolution
     */
    public void setTargetDimensions(int width, int height) {
        this.targetWidth = width;
        this.targetHeight = height;
        if (width <= 0 || height <= 0) {
            useOriginalResolution = true;
        }
    }
    
    /**
     * Use original video resolution (don't override)
     */
    public void setUseOriginalResolution(boolean useOriginal) {
        this.useOriginalResolution = useOriginal;
    }
    
    /**
     * Get the original video dimensions
     */
    public int getVideoWidth() {
        return videoWidth;
    }
    
    public int getVideoHeight() {
        return videoHeight;
    }
    
    /**
     * Check if decoder is currently running
     */
    public boolean isDecoding() {
        return isDecoding.get() && !stopDecode.get();
    }

    /**
     * Stop the decoder gracefully
     */
    public void stopDecode() {
        XposedBridge.log(TAG + " stopDecode() called");
        stopDecode.set(true);
        isDecoding.set(false);
        surfaceValid.set(false);
        
        // Interrupt the thread if it's waiting
        if (childThread != null && childThread.isAlive()) {
            try {
                childThread.interrupt();
                // Give the thread a moment to clean up
                childThread.join(100);
            } catch (Exception e) {
                XposedBridge.log(TAG + " Error stopping decode thread: " + e.getMessage());
            }
        }
        childThread = null;
    }

    /**
     * Start decoding the video file
     */
    public void decode(String videoFilePath) throws Throwable {
        this.videoFilePath = videoFilePath;
        stopDecode.set(false);
        
        if (childThread == null || !childThread.isAlive()) {
            childThread = new Thread(this, "VCAM-Decoder");
            childThread.setPriority(Thread.NORM_PRIORITY - 1);  // Slightly lower priority
            childThread.start();
            
            if (throwable != null) {
                throw throwable;
            }
        }
    }

    @Override
    public void run() {
        try {
            videoDecode(videoFilePath);
        } catch (Throwable t) {
            throwable = t;
            XposedBridge.log(TAG + " Decode thread exception: " + t.getMessage());
        } finally {
            isDecoding.set(false);
        }
    }

    @SuppressLint("WrongConstant")
    public void videoDecode(String videoFilePath) {
        XposedBridge.log(TAG + " 开始解码: " + videoFilePath);
        isDecoding.set(true);
        consecutiveErrors = 0;
        
        MediaExtractor extractor = null;
        MediaCodec decoder = null;
        
        try {
            File videoFile = new File(videoFilePath);
            if (!videoFile.exists()) {
                XposedBridge.log(TAG + " Video file not found: " + videoFilePath);
                isDecoding.set(false);
                return;
            }
            
            extractor = new MediaExtractor();
            extractor.setDataSource(videoFilePath);
            int trackIndex = selectTrack(extractor);
            if (trackIndex < 0) {
                XposedBridge.log(TAG + " No video track found in " + videoFilePath);
                isDecoding.set(false);
                return;
            }
            
            extractor.selectTrack(trackIndex);
            MediaFormat mediaFormat = extractor.getTrackFormat(trackIndex);
            
            // Get original video dimensions
            videoWidth = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
            videoHeight = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
            XposedBridge.log(TAG + " Original video resolution: " + videoWidth + "x" + videoHeight);
            
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime);
            
            if (VERBOSE) {
                showSupportedColorFormat(decoder.getCodecInfo().getCapabilitiesForType(mime));
            }
            
            if (play_surf == null) {
                if (isColorFormatSupported(decodeColorFormat, decoder.getCodecInfo().getCapabilitiesForType(mime))) {
                    mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, decodeColorFormat);
                    XposedBridge.log(TAG + " Set decode color format to type " + decodeColorFormat);
                } else {
                    XposedBridge.log(TAG + " Color format " + decodeColorFormat + " not supported");
                }
            } else {
                // Use original video resolution when playing to surface
                // The surface/SurfaceTexture will handle scaling
                XposedBridge.log(TAG + " Using original video resolution: " + videoWidth + "x" + videoHeight);
            }
            
            // Decode and loop
            boolean firstLoop = true;
            while (!stopDecode.get() && !Thread.currentThread().isInterrupted() && 
                   consecutiveErrors < MAX_CONSECUTIVE_ERRORS) {
                
                // Check surface validity before each loop iteration
                if (play_surf != null && !play_surf.isValid()) {
                    XposedBridge.log(TAG + " Surface became invalid, stopping decode");
                    break;
                }
                
                try {
                    if (!firstLoop) {
                        // Reset for loop playback
                        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                        try {
                            decoder.flush();
                        } catch (IllegalStateException e) {
                            XposedBridge.log(TAG + " Decoder flush failed, recreating decoder");
                            decoder.release();
                            decoder = MediaCodec.createDecoderByType(mime);
                        }
                    }
                    
                    decodeFramesToImage(decoder, extractor, mediaFormat, firstLoop);
                    firstLoop = false;
                    consecutiveErrors = 0;  // Reset on successful decode cycle
                    
                } catch (IllegalStateException e) {
                    consecutiveErrors++;
                    XposedBridge.log(TAG + " Decode loop error (" + consecutiveErrors + "/" + MAX_CONSECUTIVE_ERRORS + "): " + e.getMessage());
                    
                    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                        XposedBridge.log(TAG + " Too many consecutive errors, stopping decode");
                        break;
                    }
                    
                    // Try to recover
                    try {
                        decoder.reset();
                        Thread.sleep(100);
                    } catch (Exception resetEx) {
                        XposedBridge.log(TAG + " Recovery failed: " + resetEx.getMessage());
                        break;
                    }
                }
            }
            
        } catch (IllegalStateException e) {
            XposedBridge.log(TAG + " IllegalStateException: " + e.getMessage());
        } catch (IOException e) {
            XposedBridge.log(TAG + " IOException: " + e.getMessage());
        } catch (Exception e) {
            XposedBridge.log(TAG + " Exception: " + e.toString());
        } finally {
            isDecoding.set(false);
            
            // Safe cleanup
            if (decoder != null) {
                try {
                    decoder.stop();
                } catch (Exception e) {
                    // Ignore - decoder may already be stopped
                }
                try {
                    decoder.release();
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
            
            if (extractor != null) {
                try {
                    extractor.release();
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
            
            XposedBridge.log(TAG + " Decode finished");
        }
    }

    private void showSupportedColorFormat(MediaCodecInfo.CodecCapabilities caps) {
        StringBuilder sb = new StringBuilder("Supported color formats: ");
        for (int c : caps.colorFormats) {
            sb.append(c).append(" ");
        }
        XposedBridge.log(TAG + " " + sb.toString());
    }

    private boolean isColorFormatSupported(int colorFormat, MediaCodecInfo.CodecCapabilities caps) {
        for (int c : caps.colorFormats) {
            if (c == colorFormat) {
                return true;
            }
        }
        return false;
    }

    private void decodeFramesToImage(MediaCodec decoder, MediaExtractor extractor, 
                                      MediaFormat mediaFormat, boolean needConfigure) {
        long startWhen = 0;
        boolean isFirstFrame = true;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        
        if (needConfigure) {
            try {
                decoder.configure(mediaFormat, play_surf, null, 0);
            } catch (IllegalStateException e) {
                XposedBridge.log(TAG + " Configure error, attempting reset: " + e.getMessage());
                try {
                    decoder.reset();
                    decoder.configure(mediaFormat, play_surf, null, 0);
                } catch (Exception e2) {
                    XposedBridge.log(TAG + " Reset and configure failed: " + e2.getMessage());
                    return;
                }
            } catch (IllegalArgumentException e) {
                XposedBridge.log(TAG + " Invalid surface or format: " + e.getMessage());
                return;
            }
        }
        
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        
        try {
            decoder.start();
        } catch (IllegalStateException e) {
            XposedBridge.log(TAG + " Decoder start error: " + e.getMessage());
            return;
        }
        
        final int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        final int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
        int outputFrameCount = 0;
        
        while (!sawOutputEOS && !stopDecode.get() && !Thread.currentThread().isInterrupted()) {
            
            // Check surface validity periodically
            if (play_surf != null && !play_surf.isValid()) {
                XposedBridge.log(TAG + " Surface invalid during decode");
                break;
            }
            
            // Feed input
            if (!sawInputEOS) {
                int inputBufferId = decoder.dequeueInputBuffer(DEFAULT_TIMEOUT_US);
                if (inputBufferId >= 0) {
                    ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
                    if (inputBuffer != null) {
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            sawInputEOS = true;
                        } else {
                            long presentationTimeUs = extractor.getSampleTime();
                            decoder.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0);
                            extractor.advance();
                        }
                    }
                }
            }
            
            // Get output
            int outputBufferId = decoder.dequeueOutputBuffer(info, DEFAULT_TIMEOUT_US);
            if (outputBufferId >= 0) {
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                }
                
                boolean doRender = (info.size != 0);
                if (doRender) {
                    outputFrameCount++;
                    
                    if (callback != null) {
                        callback.onDecodeFrame(outputFrameCount);
                    }
                    
                    if (isFirstFrame) {
                        startWhen = System.currentTimeMillis();
                        isFirstFrame = false;
                    }
                    
                    // Handle frame data for non-surface output
                    if (play_surf == null) {
                        try {
                            Image image = decoder.getOutputImage(outputBufferId);
                            if (image != null) {
                                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                                byte[] arr = new byte[buffer.remaining()];
                                buffer.get(arr);
                                
                                if (mQueue != null) {
                                    try {
                                        mQueue.put(arr);
                                    } catch (InterruptedException e) {
                                        XposedBridge.log(TAG + " Queue interrupted");
                                        break;
                                    }
                                }
                                
                                if (outputImageFormat != null) {
                                    HookMain.data_buffer = getDataFromImage(image, COLOR_FormatNV21);
                                }
                                
                                image.close();
                            }
                        } catch (Exception e) {
                            XposedBridge.log(TAG + " Error getting output image: " + e.getMessage());
                        }
                    }
                    
                    // Frame timing
                    long sleepTime = info.presentationTimeUs / 1000 - (System.currentTimeMillis() - startWhen);
                    if (sleepTime > 0) {
                        try {
                            Thread.sleep(Math.min(sleepTime, 100));  // Cap at 100ms
                        } catch (InterruptedException e) {
                            XposedBridge.log(TAG + " Sleep interrupted");
                            stopDecode.set(true);
                            break;
                        }
                    }
                    
                    // Release buffer - CRITICAL: Handle surface destruction gracefully
                    try {
                        decoder.releaseOutputBuffer(outputBufferId, play_surf != null && play_surf.isValid());
                    } catch (IllegalStateException e) {
                        XposedBridge.log(TAG + " Error releasing buffer (surface may be destroyed): " + e.getMessage());
                        // Surface was destroyed, stop gracefully
                        stopDecode.set(true);
                        break;
                    }
                }
            } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = decoder.getOutputFormat();
                XposedBridge.log(TAG + " Output format changed: " + newFormat);
            }
        }
        
        if (callback != null) {
            callback.onFinishDecode();
        }
    }

    private static int selectTrack(MediaExtractor extractor) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("video/")) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isImageFormatSupported(Image image) {
        int format = image.getFormat();
        switch (format) {
            case ImageFormat.YUV_420_888:
            case ImageFormat.NV21:
            case ImageFormat.YV12:
                return true;
        }
        return false;
    }

    private static byte[] getDataFromImage(Image image, int colorFormat) {
        if (colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21) {
            throw new IllegalArgumentException("Only COLOR_FormatI420 and COLOR_FormatNV21 are supported");
        }
        if (!isImageFormatSupported(image)) {
            throw new RuntimeException("Can't convert Image to byte array, format " + image.getFormat());
        }
        
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();
        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];
        
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
            
            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            
            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w;
                    buffer.get(data, channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
        }
        return data;
    }
}

enum OutputImageFormat {
    I420("I420"),
    NV21("NV21"),
    JPEG("JPEG");
    
    private final String friendlyName;

    OutputImageFormat(String friendlyName) {
        this.friendlyName = friendlyName;
    }

    @Override
    public String toString() {
        return friendlyName;
    }
}
