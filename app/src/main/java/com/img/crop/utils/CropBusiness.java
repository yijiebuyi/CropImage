package com.img.crop.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import com.img.crop.MediaItem;
import com.img.crop.R;
import com.img.crop.exif.ExifData;
import com.img.crop.exif.ExifOutputStream;
import com.img.crop.thdpool.ThreadPool;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;

/*
 * Copyright (C) 2017
 * 版权所有
 *
 * 功能描述：
 * 作者：huangyong
 * 创建时间：2018/1/5
 *
 * 修改人：
 * 修改描述：
 * 修改日期
 */
public class CropBusiness {
    private static final String TAG = "CropBusiness";
    private static final int TILE_SIZE = 512;
    public static final int DEFAULT_COMPRESS_QUALITY = 95;

    /**
     * 是否支持区域解码
     *
     * @param path
     * @return
     */
    public static boolean isSupportRegionDecoder(String path) {
        if (TextUtils.isEmpty(path)) {
            return false;
        }

        String extension = getExtensionName(path);
        if (TextUtils.isEmpty(extension)) {
            return false;
        }

        return extension.equalsIgnoreCase("jpg")
                || extension.equalsIgnoreCase("jpeg")
                || extension.equalsIgnoreCase("png");
    }

    public static Rect checkCropRect(RectF cropRect) {
        int left = Math.round(cropRect.left);
        int top = Math.round(cropRect.top);
        int right = Math.round(cropRect.right);
        int bottom = Math.round(cropRect.bottom);
        if (left == right) {
            left = left - 1;
            if (left < 0) {
                left = 0;
                right = 1;
            }
        }

        if (top == bottom) {
            top = top - 1;
            if (top < 0) {
                top = 0;
                bottom = 1;
            }
        }

        return new Rect(left, top, right, bottom);
    }

    public static String generateCameraPicPath(Context context) {
        String dir = context.getExternalCacheDir().getAbsolutePath() + "/camera";
        File file = new File(dir);
        if (!file.exists()) {
            file.mkdirs();
        }

        return dir + "/" + "img.jpg";
    }

    public static String generateCropOutputDir(Context context, boolean overWrite, String suffix) {
        String dir = context.getExternalCacheDir().getAbsolutePath() + "/crop";
        File file = new File(dir);
        if (!file.exists()) {
            file.mkdirs();
        }

        if (overWrite) {
            return dir + "/" + "out_img" + "." + suffix;
        } else {
            return dir + "/" + "out_img" + System.currentTimeMillis() + "." + suffix;
        }
    }

    public static String getFileExtension(String requestFormat, MediaItem mediaItem) {
        String outputFormat = (requestFormat == null)
                ? getExtensionName(mediaItem.filePath)
                : requestFormat;

        outputFormat = outputFormat.toLowerCase();
        return (outputFormat.equals("png") || outputFormat.equals("gif"))
                ? "png" // We don't support gif compression.
                : "jpg";
    }

    /**
     * 旋转画布
     *
     * @param canvas
     * @param width
     * @param height
     * @param rotation
     */
    public static void rotateCanvas(Canvas canvas, int width, int height, int rotation) {
        canvas.translate(width / 2, height / 2);
        canvas.rotate(rotation);
        if (((rotation / 90) & 0x01) == 0) {
            canvas.translate(-width / 2, -height / 2);
        } else {
            canvas.translate(-height / 2, -width / 2);
        }
    }

    /**
     * 旋转矩形
     *
     * @param rect
     * @param width
     * @param height
     * @param rotation
     */
    public static void rotateRectangle(Rect rect, int width, int height, int rotation) {
        if (rotation == 0 || rotation == 360) {
            return;
        }

        int w = rect.width();
        int h = rect.height();
        switch (rotation) {
            case 90: {
                rect.top = rect.left;
                rect.left = height - rect.bottom;
                rect.right = rect.left + h;
                rect.bottom = rect.top + w;
                return;
            }
            case 180: {
                rect.left = width - rect.right;
                rect.top = height - rect.bottom;
                rect.right = rect.left + w;
                rect.bottom = rect.top + h;
                return;
            }
            case 270: {
                rect.left = rect.top;
                rect.top = width - rect.right;
                rect.right = rect.left + h;
                rect.bottom = rect.top + w;
                return;
            }
            default:
                throw new AssertionError();
        }
    }

    public static void drawInTiles(Canvas canvas, BitmapRegionDecoder decoder, Rect rect, Rect dest, int sample) {
        int tileSize = TILE_SIZE * sample;
        Rect tileRect = new Rect();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = sample;
        canvas.translate(dest.left, dest.top);
        canvas.scale((float) sample * dest.width() / rect.width(),
                (float) sample * dest.height() / rect.height());
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        for (int tx = rect.left, x = 0;
             tx < rect.right; tx += tileSize, x += TILE_SIZE) {
            for (int ty = rect.top, y = 0;
                 ty < rect.bottom; ty += tileSize, y += TILE_SIZE) {
                tileRect.set(tx, ty, tx + tileSize, ty + tileSize);
                if (tileRect.intersect(rect)) {
                    Bitmap bitmap;

                    // To prevent concurrent access in GLThread
                    synchronized (decoder) {
                        bitmap = decoder.decodeRegion(tileRect, options);
                    }
                    canvas.drawBitmap(bitmap, x, y, paint);
                    bitmap.recycle();
                }
            }
        }
    }

    public static File saveMedia(ThreadPool.JobContext jc, Bitmap cropped, String filePath, ExifData exifData) {
        // Try file-1.jpg, file-2.jpg, ... until we find a filename
        // which does not exist yet.
        File save = new File(filePath);
        File candidate = new File(filePath + "_temp");
        String fileExtension = getExtensionName(filePath);
        if (TextUtils.isEmpty(fileExtension)) {
            fileExtension = "jpg";
        }

        try {
            if (!candidate.exists()) {
                candidate.createNewFile();
            }
        } catch (IOException e) {
            Log.e(TAG, "fail to create new file: " + candidate.getAbsolutePath(), e);
            return null;
        }
        if (!candidate.exists() || !candidate.isFile()) {
            throw new RuntimeException("cannot create file: " + filePath);
        }

        candidate.setReadable(true, false);
        candidate.setWritable(true, false);

        try {
            FileOutputStream fos = new FileOutputStream(candidate);
            try {
                if (exifData != null) {
                    ExifOutputStream eos = new ExifOutputStream(fos);
                    eos.setExifData(exifData);
                    saveBitmapToOutputStream(jc, cropped, convertExtensionToCompressFormat(fileExtension), eos);
                } else {
                    saveBitmapToOutputStream(jc, cropped, convertExtensionToCompressFormat(fileExtension), fos);
                }

                candidate.renameTo(save);
                candidate.delete();
            } finally {
                fos.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "fail to save image: " + candidate.getAbsolutePath(), e);
            candidate.delete();
            return null;
        }

        if (jc.isCancelled()) {
            candidate.delete();
            return null;
        }

        return candidate;
    }

    private static boolean saveBitmapToOutputStream(ThreadPool.JobContext jc, Bitmap bitmap, Bitmap.CompressFormat format, OutputStream os) {
        // We wrap the OutputStream so that it can be interrupted.
        try {
            if (bitmap != null) {
                if (!bitmap.compress(format, DEFAULT_COMPRESS_QUALITY, os)) {
                    return false;
                }
            }
            return !jc.isCancelled();
        } finally {
            jc.setCancelListener(null);
            Utils.closeSilently(os);
        }
    }

    private static boolean saveBitmapToUri(ThreadPool.JobContext jc, Context context, Bitmap bitmap, Uri uri, String compressFormat) {
        try {
            return saveBitmapToOutputStream(jc, bitmap, convertExtensionToCompressFormat(compressFormat),
                    context.getContentResolver().openOutputStream(uri));
        } catch (FileNotFoundException e) {
            Log.w(TAG, "cannot write output", e);
        }
        return true;
    }

    private static Bitmap.CompressFormat convertExtensionToCompressFormat(String extension) {
        return extension.equalsIgnoreCase("png")
                ? Bitmap.CompressFormat.PNG
                : Bitmap.CompressFormat.JPEG;
    }

    public static String getImageTitle(String filePath) {
        String fileName = getFileNameWithEx(filePath);
        return fileName == null ? "" : getFileNameNoEx(fileName);
    }

    public static String getFileNameWithEx(String filePath) {
        char separatorChar = System.getProperty("file.separator", "/").charAt(0);
        if (filePath != null && filePath.length() > 0) {
            int index = filePath.lastIndexOf(separatorChar);
            if (index > -1 && index < (filePath.length()))
                return filePath.substring((index + 1), filePath.length());
        }
        return filePath;
    }

    public static String getFileNameNoEx(String filename) {
        if ((filename != null) && (filename.length() > 0)) {
            int dot = filename.lastIndexOf('.');
            if ((dot > -1) && (dot < (filename.length()))) {
                return filename.substring(0, dot);
            }
        }
        return filename;
    }

    public static String getExtensionName(String filename) {
        if ((filename != null) && (filename.length() > 0)) {
            int dot = filename.lastIndexOf('.');
            if ((dot > -1) && (dot < (filename.length() - 1))) {
                return filename.substring(dot + 1);
            }
        }
        return filename;
    }


    public static float dpToPixel(Context context, float dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return density * dp;
    }

    public static int dpToPixel(Context context, int dp) {
        return Math.round(dpToPixel(context, (float) dp));
    }

    public static int meterToPixel(Context context, float meter) {
        // 1 meter = 39.37 inches, 1 inch = 160 dp.
        return Math.round(dpToPixel(context, meter * 39.37f * 160));
    }

    public static byte[] getBytes(String in) {
        byte[] result = new byte[in.length() * 2];
        int output = 0;
        for (char ch : in.toCharArray()) {
            result[output++] = (byte) (ch & 0xFF);
            result[output++] = (byte) (ch >> 8);
        }
        return result;
    }

    public static final double toMile(double meter) {
        return meter / 1609;
    }

    public static void setViewPointMatrix(
            float matrix[], float x, float y, float z) {
        // The matrix is
        // -z,  0,  x,  0
        //  0, -z,  y,  0
        //  0,  0,  1,  0
        //  0,  0,  1, -z
        Arrays.fill(matrix, 0, 16, 0);
        matrix[0] = matrix[5] = matrix[15] = -z;
        matrix[8] = x;
        matrix[9] = y;
        matrix[10] = matrix[11] = 1;
    }

    public static int getBucketId(String path) {
        return path.toLowerCase().hashCode();
    }

    // Returns a (localized) string for the given duration (in seconds).
    public static String formatDuration(final Context context, int duration) {
        int h = duration / 3600;
        int m = (duration - h * 3600) / 60;
        int s = duration - (h * 3600 + m * 60);
        String durationValue;
        if (h == 0) {
            durationValue = String.format(context.getString(R.string.details_ms), m, s);
        } else {
            durationValue = String.format(context.getString(R.string.details_hms), h, m, s);
        }
        return durationValue;
    }

    public static boolean hasSpaceForSize(long size) {
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            return false;
        }

        String path = Environment.getExternalStorageDirectory().getPath();
        try {
            StatFs stat = new StatFs(path);
            return stat.getAvailableBlocks() * (long) stat.getBlockSize() > size;
        } catch (Exception e) {
            Log.i(TAG, "Fail to access external storage", e);
        }
        return false;
    }

    public static boolean isHighResolution(Context context) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager)
                context.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(metrics);
        return metrics.heightPixels > 2048 || metrics.widthPixels > 2048;
    }


    public static void clearInput(Context context) {
        InputMethodManager imm = (InputMethodManager)
                context.getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        try {
            Method method = InputMethodManager.class.getDeclaredMethod("finishInputLocked");
            method.setAccessible(true);
            method.invoke(imm);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
