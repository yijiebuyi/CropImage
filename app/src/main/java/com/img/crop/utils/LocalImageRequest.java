package com.img.crop.utils;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.util.Log;

import com.img.crop.MediaItem;
import com.img.crop.thdpool.ThreadPool;

public class LocalImageRequest implements ThreadPool.Job<Bitmap> {

	private static final String TAG = "ImageCacheRequest";

	private String mLocalFilePath;
    private int mType;
    private int mTargetSize;
    private boolean mIsVideo;
    private String mMimeType;

    public LocalImageRequest(String path, String mimeType, int type) {
        mLocalFilePath = path;
        mType = type;
        mTargetSize = 960;
        mIsVideo = false;
        mMimeType = mimeType;
    }

    @Override
    public Bitmap run(ThreadPool.JobContext jc) {
        Bitmap bitmap = onDecodeOriginal(jc, mType);
        if (jc.isCancelled()) return null;

        if (bitmap == null) {
            return null;
        }

        if (mType == MediaItem.TYPE_MICROTHUMBNAIL) {
        	if (MediaItem.KEEP_RATIO) {
        		bitmap = BitmapUtils.resizeAndCropBySideLength(bitmap, mTargetSize, true, mIsVideo);
        	} else {
        		bitmap = BitmapUtils.resizeAndCropCenter(bitmap, mTargetSize, true);
        	}
        } else {
            bitmap = BitmapUtils.resizeDownBySideLength(bitmap, mTargetSize, true);
        }
        if (jc.isCancelled()) return null;

        CompressFormat format = CompressFormat.JPEG;
        if ("image/png".equals(mMimeType)) {
        	format = CompressFormat.PNG;
        }
        
        byte[] array = BitmapUtils.compressToBytes(bitmap, format);
        if (jc.isCancelled()) return null;

        return bitmap;
    }

    public Bitmap onDecodeOriginal(ThreadPool.JobContext jc, int type) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        //TODO ====================
        int targetSize = 960;

        // try to decode from JPEG EXIF
        if (type == MediaItem.TYPE_MICROTHUMBNAIL) {
            ExifInterface exif = null;
            byte [] thumbData = null;
            try {
                exif = new ExifInterface(mLocalFilePath);
                if (exif != null) {
                    thumbData = exif.getThumbnail();
                }
            } catch (Throwable t) {
                Log.w(TAG, "fail to get exif thumb", t);
            }
            if (thumbData != null) {
                Bitmap bitmap = DecodeUtils.decodeIfBigEnough(
                        jc, thumbData, options, targetSize);
                if (bitmap != null) return bitmap;
            }
        }

        return DecodeUtils.decodeThumbnail(jc, mLocalFilePath, options, targetSize, type);
    };

}
