package android.media;

import android.graphics.Rect;
import java.nio.ByteBuffer;

public class FakeImage extends Image {
    private final int mFormat;
    private final int mWidth;
    private final int mHeight;
    private final long mTimestamp;
    private final Plane[] mPlanes;
    private Rect mCropRect;

    public FakeImage(int width, int height, int format, byte[] jpegData, long timestamp) {
        mWidth = width;
        mHeight = height;
        mFormat = format;
        mTimestamp = timestamp;
        mPlanes = new Plane[1];
        mPlanes[0] = new FakePlane(jpegData, 0, 0);
        mCropRect = new Rect(0, 0, width, height);
    }

    public FakeImage(int width, int height, int format, byte[][] yuvData, long timestamp) {
        mWidth = width;
        mHeight = height;
        mFormat = format;
        mTimestamp = timestamp;
        mPlanes = new Plane[3];
        // Y plane
        mPlanes[0] = new FakePlane(yuvData[0], 1, width);
        // U plane (pixel stride 1 or 2 depending on format, usually 1 for tight packing in our fake)
        mPlanes[1] = new FakePlane(yuvData[1], 1, width / 2);
        // V plane
        mPlanes[2] = new FakePlane(yuvData[2], 1, width / 2);
        mCropRect = new Rect(0, 0, width, height);
    }

    @Override
    public int getFormat() {
        return mFormat;
    }

    @Override
    public int getWidth() {
        return mWidth;
    }

    @Override
    public int getHeight() {
        return mHeight;
    }

    @Override
    public long getTimestamp() {
        return mTimestamp;
    }

    @Override
    public Plane[] getPlanes() {
        return mPlanes;
    }

    @Override
    public void close() {
        // No-op for our fake image as byte arrays are managed by GC
    }

    @Override
    public Rect getCropRect() {
        return mCropRect;
    }

    @Override
    public void setCropRect(Rect cropRect) {
        if (cropRect != null) {
            cropRect.intersect(0, 0, mWidth, mHeight);
            mCropRect = cropRect;
        } else {
            mCropRect = new Rect(0, 0, mWidth, mHeight);
        }
    }

    // Inner class for Plane
    private static class FakePlane extends Image.Plane {
        private final ByteBuffer mBuffer;
        private final int mPixelStride;
        private final int mRowStride;

        public FakePlane(byte[] data, int pixelStride, int rowStride) {
            mBuffer = ByteBuffer.wrap(data);
            mPixelStride = pixelStride;
            mRowStride = rowStride;
        }

        @Override
        public int getRowStride() {
            return mRowStride;
        }

        @Override
        public int getPixelStride() {
            return mPixelStride;
        }

        @Override
        public ByteBuffer getBuffer() {
            return mBuffer;
        }
    }
}
