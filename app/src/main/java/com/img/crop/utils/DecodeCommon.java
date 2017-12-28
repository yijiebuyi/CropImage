package com.img.crop.utils;

import java.io.FileDescriptor;
import java.io.FileInputStream;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.os.Build;
import android.util.Log;

import com.img.crop.MediaItem;
import com.img.crop.thdpool.ThreadPool.JobContext;
import com.img.crop.thdpool.ThreadPool.CancelListener;


public class DecodeCommon {
	private static final String TAG = "DecodeCommon";
	/**
     * Decodes the bitmap from the given byte array if the image size is larger than the given
     * requirement.
     *
     * Note: The returned image may be resized down. However, both width and height must be
     * larger than the <code>targetSize</code>.
     */
    public static Bitmap decodeIfBigEnough(JobContext jc, byte[] data, final Options options, int targetSize) {
        jc.setCancelListener(new CancelListener() {
			@Override
			public void onCancel() {
				options.requestCancelDecode();
			}
        });

        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (jc.isCancelled()) return null;
        if (options.outWidth < targetSize || options.outHeight < targetSize) {
            return null;
        }
        options.inSampleSize = BitmapUtils.computeSampleSizeLarger(
                options.outWidth, options.outHeight, targetSize);
        options.inJustDecodeBounds = false;
        setOptionsMutable(options);

        return ensureGLCompatibleBitmap(
                BitmapFactory.decodeByteArray(data, 0, data.length, options));
    }
    
    public static Bitmap ensureGLCompatibleBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.getConfig() != null) return bitmap;
        Bitmap newBitmap = bitmap.copy(Config.ARGB_8888, false);
        bitmap.recycle();
        return newBitmap;
    }
    
    @TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
    public static void setOptionsMutable(Options options) {
        if (ApiHelper.HAS_OPTIONS_IN_MUTABLE) options.inMutable = true;
    }
    
    public static Bitmap decodeThumbnail(JobContext jc, String filePath, Options options, int targetSize, int type) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(filePath);
            FileDescriptor fd = fis.getFD();
            return decodeThumbnail(jc, fd, options, targetSize, type);
        } catch (Exception ex) {
            Log.w(TAG, ex);
            return null;
        } finally {
            Utils.closeSilently(fis);
        }
    }
    
    public static Bitmap decodeThumbnail(JobContext jc, FileDescriptor fd, final Options options, int targetSize, int type) {
        jc.setCancelListener(new CancelListener() {
			@Override
			public void onCancel() {
				options.requestCancelDecode();
			}
        });

        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFileDescriptor(fd, null, options);
        if (jc.isCancelled()) return null;

        int w = options.outWidth;
        int h = options.outHeight;

        if (type == MediaItem.TYPE_MICROTHUMBNAIL) {
            // We center-crop the original image as it's micro thumbnail. In this case,
            // we want to make sure the shorter side >= "targetSize".
            float scale = (float) targetSize / Math.min(w, h);
            options.inSampleSize = BitmapUtils.computeSampleSizeLarger(scale);

            // For an extremely wide image, e.g. 300x30000, we may got OOM when decoding
            // it for TYPE_MICROTHUMBNAIL. So we add a max number of pixels limit here.
            final int MAX_PIXEL_COUNT = 640000; // 400 x 1600
            if ((w / options.inSampleSize) * (h / options.inSampleSize) > MAX_PIXEL_COUNT) {
                options.inSampleSize = BitmapUtils.computeSampleSize(
                        (float) Math.sqrt((float) MAX_PIXEL_COUNT / (w * h)));
            }
        } else {
            // For screen nail, we only want to keep the longer side >= targetSize.
            float scale = (float) targetSize / Math.max(w, h);
            options.inSampleSize = BitmapUtils.computeSampleSizeLarger(scale);
        }

        options.inJustDecodeBounds = false;
        setOptionsMutable(options);

        Bitmap result = BitmapFactory.decodeFileDescriptor(fd, null, options);
        if (result == null) return null;

        // We need to resize down if the decoder does not support inSampleSize
        // (For example, GIF images)
        float scale = (float) targetSize / (type == MediaItem.TYPE_MICROTHUMBNAIL
                ? Math.min(result.getWidth(), result.getHeight())
                : Math.max(result.getWidth(), result.getHeight()));

        if (scale <= 0.5) result = BitmapUtils.resizeBitmapByScale(result, scale, true);
        return ensureGLCompatibleBitmap(result);
    }
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static Bitmap decode(JobContext jc, byte[] data, int offset, int length, Options options, BitmapPool pool) {
        if (pool == null) {
            return decode(jc, data, offset, length, options);
        }

        if (options == null) options = new Options();
        if (options.inSampleSize < 1) options.inSampleSize = 1;
        options.inPreferredConfig = Config.ARGB_8888;
        options.inBitmap = (options.inSampleSize == 1)
                ? findCachedBitmap(pool, jc, data, offset, length, options) : null;
        try {
            Bitmap bitmap = decode(jc, data, offset, length, options);
            if (options.inBitmap != null && options.inBitmap != bitmap) {
                pool.recycle(options.inBitmap);
                options.inBitmap = null;
            }
            return bitmap;
        } catch (IllegalArgumentException e) {
            if (options.inBitmap == null) throw e;

            Log.w(TAG, "decode fail with a given bitmap, try decode to a new bitmap");
            pool.recycle(options.inBitmap);
            options.inBitmap = null;
            return decode(jc, data, offset, length, options);
        }
    }
    
    public static Bitmap decode(JobContext jc, byte[] bytes, int offset, int length, final Options options) {
    	jc.setCancelListener(new CancelListener() {
			@Override
			public void onCancel() {
				options.requestCancelDecode();
			}
        });
        setOptionsMutable(options);
        return ensureGLCompatibleBitmap(
                BitmapFactory.decodeByteArray(bytes, offset, length, options));
    }
    
    private static Bitmap findCachedBitmap(BitmapPool pool, JobContext jc, byte[] data, int offset, int length, Options options) {
        decodeBounds(jc, data, offset, length, options);
        return pool.getBitmap(options.outWidth, options.outHeight);
    }
    
    public static void decodeBounds(JobContext jc, byte[] bytes, int offset, int length, final Options options) {
        Utils.assertTrue(options != null);
        options.inJustDecodeBounds = true;
        jc.setCancelListener(new CancelListener() {
			@Override
			public void onCancel() {
				options.requestCancelDecode();
			}
        });
        BitmapFactory.decodeByteArray(bytes, offset, length, options);
        options.inJustDecodeBounds = false;
    }
}
