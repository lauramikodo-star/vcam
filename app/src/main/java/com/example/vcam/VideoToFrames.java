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

import com.example.vcam.HookMain;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

import de.robv.android.xposed.XposedBridge;

//以下代码修改自 https://github.com/zhantong/Android-VideoToImages
public class VideoToFrames implements Runnable {
    private static final String TAG = "VideoToFrames";
    private static final boolean VERBOSE = false;
    private static final long DEFAULT_TIMEOUT_US = 10000;

    private static final int COLOR_FormatI420 = 1;
    private static final int COLOR_FormatNV21 = 2;


    private final int decodeColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;

    private LinkedBlockingQueue<byte[]> mQueue;
    private OutputImageFormat outputImageFormat;
    private volatile boolean stopDecode = false;
    private volatile boolean isDecoding = false;

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
    private boolean useOriginalResolution = false;

    private Callback callback;

    public interface Callback {
        void onFinishDecode();

        void onDecodeFrame(int index);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setEnqueue(LinkedBlockingQueue<byte[]> queue) {
        mQueue = queue;
    }

    //设置输出位置，没啥用
    public void setSaveFrames(String dir, OutputImageFormat imageFormat) throws IOException {
        outputImageFormat = imageFormat;

    }

    public void set_surfcae(Surface player_surface) {
        if (player_surface != null) {
            play_surf = player_surface;
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
     * Check if decoder is currently running
     */
    public boolean isDecoding() {
        return isDecoding && !stopDecode;
    }

    public void stopDecode() {
        stopDecode = true;
        isDecoding = false;
        // Interrupt the thread if it's waiting
        if (childThread != null && childThread.isAlive()) {
            try {
                childThread.interrupt();
            } catch (Exception e) {
                XposedBridge.log("【VCAM】Error interrupting decode thread: " + e.getMessage());
            }
        }
    }

    public void decode(String videoFilePath) throws Throwable {
        this.videoFilePath = videoFilePath;
        if (childThread == null) {
            childThread = new Thread(this, "decode");
            childThread.start();
            if (throwable != null) {
                throw throwable;
            }
        }
    }

    public void run() {
        try {
            videoDecode(videoFilePath);
        } catch (Throwable t) {
            throwable = t;
        }
    }

    @SuppressLint("WrongConstant")
    public void videoDecode(String videoFilePath) throws IOException {
        XposedBridge.log("【VCAM】【decoder】开始解码");
        isDecoding = true;
        MediaExtractor extractor = null;
        MediaCodec decoder = null;
        try {
            File videoFile = new File(videoFilePath);
            if (!videoFile.exists()) {
                XposedBridge.log("【VCAM】【decoder】Video file not found: " + videoFilePath);
                isDecoding = false;
                return;
            }
            
            extractor = new MediaExtractor();
            extractor.setDataSource(videoFilePath);
            int trackIndex = selectTrack(extractor);
            if (trackIndex < 0) {
                XposedBridge.log("【VCAM】【decoder】No video track found in " + videoFilePath);
                isDecoding = false;
                return;
            }
            extractor.selectTrack(trackIndex);
            MediaFormat mediaFormat = extractor.getTrackFormat(trackIndex);
            
            // Get original video dimensions
            videoWidth = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
            videoHeight = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
            XposedBridge.log("【VCAM】【decoder】Original video resolution: " + videoWidth + "x" + videoHeight);
            
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime);
            showSupportedColorFormat(decoder.getCodecInfo().getCapabilitiesForType(mime));
            
            if (play_surf == null) {
                if (isColorFormatSupported(decodeColorFormat, decoder.getCodecInfo().getCapabilitiesForType(mime))) {
                    mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, decodeColorFormat);
                    XposedBridge.log("【VCAM】【decoder】set decode color format to type " + decodeColorFormat);
                } else {
                    Log.i(TAG, "unable to set decode color format, color format type " + decodeColorFormat + " not supported");
                    XposedBridge.log("【VCAM】【decoder】unable to set decode color format, color format type " + decodeColorFormat + " not supported");
                }
            } else {
                // CRITICAL FIX: Use original video resolution when playing to surface
                // Don't override resolution as it can cause aspect ratio issues
                // The surface/SurfaceTexture will handle scaling
                if (!useOriginalResolution && targetWidth > 0 && targetHeight > 0) {
                    // Only override if explicitly requested AND dimensions match aspect ratio somewhat
                    float videoAspect = (float) videoWidth / videoHeight;
                    float targetAspect = (float) targetWidth / targetHeight;
                    
                    // If aspect ratios are very different, use original video resolution
                    // to avoid severe stretching/compression
                    if (Math.abs(videoAspect - targetAspect) > 0.5f) {
                        XposedBridge.log("【VCAM】【decoder】Aspect ratio mismatch (video: " + videoAspect + 
                                        ", target: " + targetAspect + ") - using original resolution");
                        // Don't override - let the video play at original resolution
                        // The surface will scale it appropriately
                    } else {
                        // Aspect ratios are similar enough, can use target dimensions
                        mediaFormat.setInteger(MediaFormat.KEY_WIDTH, targetWidth);
                        mediaFormat.setInteger(MediaFormat.KEY_HEIGHT, targetHeight);
                        XposedBridge.log("【VCAM】【decoder】Using target resolution: " + targetWidth + "x" + targetHeight);
                    }
                } else {
                    XposedBridge.log("【VCAM】【decoder】Using original video resolution: " + videoWidth + "x" + videoHeight);
                }
            }
            
            decodeFramesToImage(decoder, extractor, mediaFormat);
            
            // Safely stop decoder before looping
            try {
                decoder.stop();
            } catch (IllegalStateException e) {
                XposedBridge.log("【VCAM】【decoder】Error stopping decoder: " + e.getMessage());
            }
            
            // Loop playback
            while (!stopDecode && !Thread.currentThread().isInterrupted()) {
                try {
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                    decodeFramesToImage(decoder, extractor, mediaFormat);
                    decoder.stop();
                } catch (IllegalStateException e) {
                    // Decoder may have been released or is in invalid state
                    XposedBridge.log("【VCAM】【decoder】Loop decode error: " + e.getMessage());
                    break;
                }
            }
        } catch (IllegalStateException e) {
            XposedBridge.log("【VCAM】[videofile] IllegalStateException: " + e.getMessage());
            // Don't rethrow - just log and exit gracefully
        } catch (Exception e) {
            XposedBridge.log("【VCAM】[videofile] " + e.toString());
        } finally {
            isDecoding = false;
            if (decoder != null) {
                try {
                    decoder.stop();
                    decoder.release();
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
                decoder = null;
            }
            if (extractor != null) {
                try {
                    extractor.release();
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
                extractor = null;
            }
        }
    }

    private void showSupportedColorFormat(MediaCodecInfo.CodecCapabilities caps) {
        System.out.print("supported color format: ");
        for (int c : caps.colorFormats) {
            System.out.print(c + "\t");
        }
        System.out.println();
    }

    private boolean isColorFormatSupported(int colorFormat, MediaCodecInfo.CodecCapabilities caps) {
        for (int c : caps.colorFormats) {
            if (c == colorFormat) {
                return true;
            }
        }
        return false;
    }

    private void decodeFramesToImage(MediaCodec decoder, MediaExtractor extractor, MediaFormat mediaFormat) {
        boolean is_first = false;
        long startWhen = 0;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        
        try {
            decoder.configure(mediaFormat, play_surf, null, 0);
        } catch (IllegalStateException e) {
            XposedBridge.log("【VCAM】【decoder】Configure error: " + e.getMessage());
            // Try to reset decoder
            try {
                decoder.reset();
                decoder.configure(mediaFormat, play_surf, null, 0);
            } catch (Exception e2) {
                XposedBridge.log("【VCAM】【decoder】Reset failed: " + e2.getMessage());
                return;
            }
        } catch (IllegalArgumentException e) {
            XposedBridge.log("【VCAM】【decoder】Invalid surface or format: " + e.getMessage());
            return;
        }
        
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        
        try {
            decoder.start();
        } catch (IllegalStateException e) {
            XposedBridge.log("【VCAM】【decoder】Start error: " + e.getMessage());
            return;
        }
        
        final int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        final int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
        int outputFrameCount = 0;
        
        while (!sawOutputEOS && !stopDecode && !Thread.currentThread().isInterrupted()) {
            if (!sawInputEOS) {
                int inputBufferId = decoder.dequeueInputBuffer(DEFAULT_TIMEOUT_US);
                if (inputBufferId >= 0) {
                    ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
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
                    if (!is_first) {
                        startWhen = System.currentTimeMillis();
                        is_first = true;
                    }
                    if (play_surf == null) {
                        Image image = decoder.getOutputImage(outputBufferId);
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] arr = new byte[buffer.remaining()];
                        buffer.get(arr);
                        if (mQueue != null) {
                            try {
                                mQueue.put(arr);
                            } catch (InterruptedException e) {
                                XposedBridge.log("【VCAM】" + e.toString());
                            }
                        }
                        if (outputImageFormat != null) {
                            HookMain.data_buffer = getDataFromImage(image, COLOR_FormatNV21);
                        }
                        image.close();
                    }
                    long sleepTime = info.presentationTimeUs / 1000 - (System.currentTimeMillis() - startWhen);
                    if (sleepTime > 0) {
                        try {
                            Thread.sleep(Math.min(sleepTime, 100)); // Cap sleep time to 100ms max
                        } catch (InterruptedException e) {
                            XposedBridge.log("【VCAM】Decode thread interrupted");
                            stopDecode = true;
                            break;
                        }
                    }
                    
                    try {
                        decoder.releaseOutputBuffer(outputBufferId, true);
                    } catch (IllegalStateException e) {
                        XposedBridge.log("【VCAM】【decoder】Error releasing buffer: " + e.getMessage());
                        break;
                    }
                }
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
            if (mime.startsWith("video/")) {
                if (VERBOSE) {
                    Log.d(TAG, "Extractor selected track " + i + " (" + mime + "): " + format);
                }
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
            throw new IllegalArgumentException("only support COLOR_FormatI420 " + "and COLOR_FormatNV21");
        }
        if (!isImageFormatSupported(image)) {
            throw new RuntimeException("can't convert Image to byte array, format " + image.getFormat());
        }
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();
        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];
        if (VERBOSE) Log.v(TAG, "get data from " + planes.length + " planes");
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
            if (VERBOSE) {
                Log.v(TAG, "pixelStride " + pixelStride);
                Log.v(TAG, "rowStride " + rowStride);
                Log.v(TAG, "width " + width);
                Log.v(TAG, "height " + height);
                Log.v(TAG, "buffer size " + buffer.remaining());
            }
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
            if (VERBOSE) Log.v(TAG, "Finished reading data from plane " + i);
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

    public String toString() {
        return friendlyName;
    }
}



