package com.img.crop.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

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
public class BitmapUtils {
    private static final String TAG = "BitmapUtils";
    private static final int DEFAULT_JPEG_QUALITY = 90;
    public static final int UNCONSTRAINED = -1;
    public static final float MAX_RATIO = 15f / 9f;
    public static final int HW_MIN_PIXEL = 10 * 1000 * 1000;

    private BitmapUtils() {
    }

    /*
     * Compute the sample size as a function of minSideLength and
     * maxNumOfPixels. minSideLength is used to specify that minimal width or
     * height of a bitmap. maxNumOfPixels is used to specify the maximal size in
     * pixels that is tolerable in terms of memory usage.
     *
     * The function returns a sample size based on the constraints. Both size
     * and minSideLength can be passed in as UNCONSTRAINED, which indicates no
     * care of the corresponding constraint. The functions prefers returning a
     * sample size that generates a smaller bitmap, unless minSideLength =
     * UNCONSTRAINED.
     *
     * Also, the function rounds up the sample size to a power of 2 or multiple
     * of 8 because BitmapFactory only honors sample size this way. For example,
     * BitmapFactory downsamples an image by 2 even though the request is 3. So
     * we round up the sample size to avoid OOM.
     */
    public static int computeSampleSize(int width, int height, int minSideLength, int maxNumOfPixels) {
        int initialSize = computeInitialSampleSize(width, height,
                minSideLength, maxNumOfPixels);

        return initialSize <= 8
                ? nextPowerOf2(initialSize)
                : (initialSize + 7) / 8 * 8;
    }

    private static int computeInitialSampleSize(int w, int h, int minSideLength, int maxNumOfPixels) {
        if (maxNumOfPixels == UNCONSTRAINED && minSideLength == UNCONSTRAINED)
            return 1;

        int lowerBound = (maxNumOfPixels == UNCONSTRAINED) ? 1 : (int) Math
                .ceil(Math.sqrt((float) (w * h) / maxNumOfPixels));

        if (minSideLength == UNCONSTRAINED) {
            return lowerBound;
        } else {
            int sampleSize = Math.min(w / minSideLength, h / minSideLength);
            return Math.max(sampleSize, lowerBound);
        }
    }

    // This computes a sample size which makes the longer side at least
    // minSideLength long. If that's not possible, return 1.
    public static int computeSampleSizeLarger(int w, int h, int minSideLength) {
        int initialSize = Math.max(w / minSideLength, h / minSideLength);
        if (initialSize <= 1)
            return 1;

        return initialSize <= 8
                ? prevPowerOf2(initialSize)
                : initialSize / 8 * 8;
    }

    // Find the min x that 1 / x >= scale
    public static int computeSampleSizeLarger(float scale) {
        int initialSize = (int) Math.floor(1f / scale);
        if (initialSize <= 1)
            return 1;

        return initialSize <= 8
                ? prevPowerOf2(initialSize)
                : initialSize / 8 * 8;
    }

    // Find the max x that 1 / x <= scale.
    public static int computeSampleSize(float scale) {
        assertTrue(scale > 0);
        int initialSize = Math.max(1, (int) Math.ceil(1 / scale));
        return initialSize <= 8
                ? nextPowerOf2(initialSize)
                : (initialSize + 7) / 8 * 8;
    }

    public static Bitmap resizeBitmapByScale(Bitmap bitmap, float scale, boolean recycle) {
        int width = Math.round(bitmap.getWidth() * scale);
        int height = Math.round(bitmap.getHeight() * scale);
        if (width == bitmap.getWidth() && height == bitmap.getHeight())
            return bitmap;
        Bitmap target = Bitmap.createBitmap(width, height, getConfig(bitmap));
        Canvas canvas = new Canvas(target);
        canvas.scale(scale, scale);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        if (recycle)
            bitmap.recycle();
        return target;
    }

    private static Bitmap.Config getConfig(Bitmap bitmap) {
        Bitmap.Config config = bitmap.getConfig();
        if (config == null) {
            config = Bitmap.Config.ARGB_8888;
        }
        return config;
    }

    public static Bitmap resizeDownBySideLength(Bitmap bitmap, int maxLength, boolean recycle) {
        int srcWidth = bitmap.getWidth();
        int srcHeight = bitmap.getHeight();
        float scale = Math.min((float) maxLength / srcWidth, (float) maxLength
                / srcHeight);
        if (scale >= 1.0f)
            return bitmap;
        return resizeBitmapByScale(bitmap, scale, recycle);
    }

    public static Bitmap resize(Bitmap bitmap, int size, boolean recycle) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w == size && h == size)
            return bitmap;

        float scale = (float) size / Math.min(w, h);
        int width = Math.round(scale * w);
        int height = Math.round(scale * h);
        Bitmap target = Bitmap.createBitmap(width, height, getConfig(bitmap));
        Canvas canvas = new Canvas(target);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        canvas.drawBitmap(bitmap, new Rect(0, 0, w, h), new Rect(0, 0, width,
                height), paint);
        if (recycle)
            bitmap.recycle();
        return target;
    }

    public static Bitmap resize(Bitmap bitmap, int width, int height, boolean recycle) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w == width && h == height)
            return bitmap;

        float scaleWidth = (float) width / w;
        float scaleHeight = (float) height / h;
        width = Math.round(scaleWidth * w);
        height = Math.round(scaleHeight * h);
        Bitmap target = Bitmap.createBitmap(width, height, getConfig(bitmap));
        Canvas canvas = new Canvas(target);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        canvas.drawBitmap(bitmap, new Rect(0, 0, w, h), new Rect(0, 0, width,
                height), paint);
        if (recycle)
            bitmap.recycle();
        return target;
    }

    public static Bitmap resizeAndCropCenter(Bitmap bitmap, int size, boolean recycle) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w == size && h == size)
            return bitmap;

        // scale the image so that the shorter side equals to the target;
        // the longer side will be center-cropped.
        float scale = (float) size / Math.min(w, h);

        Bitmap target = Bitmap.createBitmap(size, size, getConfig(bitmap));
        int width = Math.round(scale * bitmap.getWidth());
        int height = Math.round(scale * bitmap.getHeight());
        if (target == null)
            return bitmap;
        Canvas canvas = new Canvas(target);
        canvas.translate((size - width) / 2f, (size - height) / 2f);
        canvas.scale(scale, scale);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        if (recycle)
            bitmap.recycle();
        return target;
    }

    public static Bitmap resizeAndCropCenter(Bitmap bitmap, int width, int height, boolean recycle) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        if (w == width && h == height)
            return bitmap;

        // scale the image so that the shorter side equals to the target;
        // the longer side will be center-cropped.
        float scaleWidth = (float) width / w;
        float scaleHeight = (float) height / h;

        Bitmap target = Bitmap.createBitmap(width, height, getConfig(bitmap));
        int tmpWidth = Math.round(scaleWidth * bitmap.getWidth());
        int tmpHeight = Math.round(scaleHeight * bitmap.getHeight());
        if (target == null)
            return bitmap;
        Canvas canvas = new Canvas(target);
        canvas.translate((width - tmpWidth) / 2f, (height - tmpHeight) / 2f);
        canvas.scale(scaleWidth, scaleHeight);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        if (recycle)
            bitmap.recycle();
        return target;
    }

    public static Bitmap resizeAndCropBySideLength(Bitmap bitmap, int maxLength, boolean recycle, boolean isVideo) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width == maxLength && height == maxLength)
            return bitmap;

        int w = width;
        int h = height;

        final float ratio = (float) w / (float) h;
        if (ratio > MAX_RATIO) {
            w = Math.round(h * MAX_RATIO);
        } else if (ratio < (1f / MAX_RATIO)) {
            h = Math.round(w * MAX_RATIO);
        }

        float scale = (float) maxLength / (isVideo ? Math.max(w, h) : h);

        int sw = Math.round(scale * w);
        int sh = Math.round(scale * h);

        Bitmap target = Bitmap.createBitmap(sw, sh, getConfig(bitmap));

        Canvas canvas = new Canvas(target);
        int dx = -Math.round((width * scale - sw) * 0.5f);
        int dy = -Math.round((height * scale - sh) * 0.5f);
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        if (recycle)
            bitmap.recycle();
        return target;
    }

    public static void recycleSilently(Bitmap bitmap) {
        if (bitmap == null)
            return;
        try {
            bitmap.recycle();
        } catch (Throwable t) {
            Log.w(TAG, "unable recycle bitmap", t);
        }
    }

    public static Bitmap rotateBitmap(Bitmap source, int rotation, boolean recycle) {
        if (rotation == 0)
            return source;
        int w = source.getWidth();
        int h = source.getHeight();
        Matrix m = new Matrix();
        m.postRotate(rotation);
        Bitmap bitmap = Bitmap.createBitmap(source, 0, 0, w, h, m, true);
        if (recycle)
            source.recycle();
        return bitmap;
    }

    public static byte[] compressToBytes(Bitmap bitmap, CompressFormat format) {
        return compressToBytes(bitmap, format, DEFAULT_JPEG_QUALITY);
    }

    public static byte[] compressToBytes(Bitmap bitmap, CompressFormat format, int quality) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(65536);
        bitmap.compress(format, quality, baos);
        return baos.toByteArray();
    }

    public static boolean isSupportedByRegionDecoder(String mimeType) {
        if (mimeType == null)
            return false;
        mimeType = mimeType.toLowerCase(Locale.getDefault());
        return mimeType.startsWith("image/")
                && (!mimeType.equals("image/gif") && !mimeType.endsWith("bmp")
                && !mimeType.endsWith("tif") && !mimeType
                .endsWith("tiff"));
    }

    public static boolean isRotationSupported(String mimeType) {
        if (mimeType == null)
            return false;
        mimeType = mimeType.toLowerCase(Locale.getDefault());
        return mimeType.equals("image/jpeg") || mimeType.equals("image/jpg");
    }

    // Returns the next power of two.
    // Returns the input if it is already power of 2.
    // Throws IllegalArgumentException if the input is <= 0 or
    // the answer overflows.
    public static int nextPowerOf2(int n) {
        if (n <= 0 || n > (1 << 30))
            throw new IllegalArgumentException("n is invalid: " + n);
        n -= 1;
        n |= n >> 16;
        n |= n >> 8;
        n |= n >> 4;
        n |= n >> 2;
        n |= n >> 1;
        return n + 1;
    }

    // Returns the previous power of two.
    // Returns the input if it is already power of 2.
    // Throws IllegalArgumentException if the input is <= 0
    public static int prevPowerOf2(int n) {
        if (n <= 0)
            throw new IllegalArgumentException();
        return Integer.highestOneBit(n);
    }

    // Throws AssertionError if the input is false.
    public static void assertTrue(boolean cond) {
        if (!cond) {
            throw new AssertionError();
        }
    }

    /**
     * 根据URI查询
     *
     * @param uri 文件URI或者、数据库URI
     */
    public static int getOrientation(Context context, Uri uri) {
        if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            return getOrientationFromPath(uri.getPath());
        }

        return getOrientationFromDatabase(context, uri);
    }

    /**
     * 直接从文件中读取（只是针对于JPEG，JPG图片格式）
     */
    public static int getOrientationFromPath(String path) {
        int orientation = 0;
        try {
            ExifInterface EXIF = new ExifInterface(path);
            int ori = EXIF.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
            switch (ori) {
                case ExifInterface.ORIENTATION_UNDEFINED:
                case ExifInterface.ORIENTATION_NORMAL:
                    orientation = 0;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    orientation = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    orientation = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    orientation = 270;
                    break;
                default:
                    break;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return orientation;
    }

    /**
     * 从数据库查询
     */
    public static int getOrientationFromDatabase(Context context, Uri uri) {
        int orientation = 0;
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri,
                    new String[]{MediaStore.Images.ImageColumns.ORIENTATION},
                    null, null, null);
            if (cursor != null && cursor.moveToNext()) {
                orientation = cursor.getInt(0);
            }
        } catch (SQLiteException e) {
            return ExifInterface.ORIENTATION_UNDEFINED;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return orientation;
    }

    public static Bitmap base64ToBitmap(String base64) {
        byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        return bitmap;
    }

    /**
     * 根据传入参数压缩图片
     *
     * @param filePath 图片文件路径
     * @param wh       宽，高数组
     * @param degree   旋转角度
     */
    public static Bitmap createNewBitmapAndCompressByFile(String filePath, int wh[], int degree) {
        int offset = 100;
        File file = new File(filePath);
        long fileSize = file.length();

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true; // 为true里只读图片的信息，如果长宽，返回的bitmap为null
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inDither = false;
        /**
         * 计算图片尺寸
         */
        BitmapFactory.decodeFile(filePath, options);
        int bmpheight = options.outWidth;
        int bmpWidth = options.outHeight;
        int inSampleSize = bmpheight / wh[1] > bmpWidth / wh[0] ? bmpheight
                / wh[1] : bmpWidth / wh[0];
        if (inSampleSize > 1)
            options.inSampleSize = inSampleSize;// 设置缩放比例
        options.inJustDecodeBounds = false;

        InputStream is = null;
        try {
            is = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            return null;
        }
        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeStream(is, null, options);
            int normalDegree = getOrientationFromPath(filePath);
            if (degree != 0 || normalDegree != 0) {
                bitmap = rotateBitmap(bitmap, degree == 0 ? normalDegree
                        : (degree + normalDegree) % 360, true);
            }
        } catch (OutOfMemoryError e) {
            System.gc();
            bitmap = null;
        }
        if (offset == 100)
            return bitmap;// 缩小质量
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(CompressFormat.JPEG, offset, baos);
        byte[] buffer = baos.toByteArray();
        options = null;
        if (buffer.length >= fileSize)
            return bitmap;
        return BitmapFactory.decodeByteArray(buffer, 0, buffer.length);
    }

    //saves the lossless compression jpg image to the file
    public static String saveImage(Bitmap bitmap, String destPath, int quality) {
        return saveImage(bitmap, destPath, quality, true, false);
    }

    public static String saveImage(Bitmap bitmap, String destPath, int quality, boolean recycle) {
        return saveImage(bitmap, destPath, quality, recycle, false);
    }

    public static String saveImage(Bitmap bitmap, String destPath, int quality, boolean recycle, boolean updateTime) {
        if (bitmap == null) {
            return null;
        }

        try {
            FileUtil.deleteFile(destPath);
            if (FileUtil.createFile(destPath)) {
                FileOutputStream out = new FileOutputStream(destPath);
                if (bitmap.compress(CompressFormat.JPEG, quality, out)) {
                    out.flush();
                    out.close();
                    out = null;
                }

                if (out != null) {
                    out.close();
                }
            }
            if (recycle) {
                bitmap.recycle();
                bitmap = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return destPath;
    }

    public static String savePngImage(Bitmap bitmap, String destPath, int quality, boolean recycle) {
        if (bitmap == null) {
            return null;
        }

        try {
            FileUtil.deleteFile(destPath);
            if (FileUtil.createFile(destPath)) {
                FileOutputStream out = new FileOutputStream(destPath);
                if (bitmap.compress(CompressFormat.PNG, quality, out)) {
                    out.flush();
                    out.close();
                    out = null;
                }

                if (out != null) {
                    out.close();
                }
            }
            if (recycle) {
                bitmap.recycle();
                bitmap = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return destPath;
    }

    public static boolean savePngImageToGallery(Context context, Bitmap bmp, File fileDir, String fileName, int quality) {
        // 首先保存图片
        String destPath = fileDir + "/" + fileName;
        String savedPath = savePngImage(bmp, destPath, quality, false);
        if (savedPath == null) {
            return false;
        }

        // 最后通知图库更新
        context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + savedPath)));
        return true;
    }

    public static boolean saveImageToGallery(Context context, Bitmap bmp, File fileDir, String fileName, int quality, boolean updateTime) {
        // 首先保存图片
        String destPath = fileDir + "/" + fileName;
        String savedPath = saveImage(bmp, destPath, quality, false, true);
        if (savedPath == null) {
            return false;
        }

        // 最后通知图库更新
        context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + savedPath)));
        return true;
    }

    //saves the lossless compression bmp image to the file
    public static File saveBMPImage(Context context, Bitmap bitmap, File oldFile) {
        File tempFile = null;
        FileOutputStream fos = null;
        File newFile = new File(oldFile.getAbsolutePath());
        if (bitmap == null)
            return null;

        int nBmpWidth = bitmap.getWidth();
        int nBmpHeight = bitmap.getHeight();
        //image buffer data size
        int bufferSize = nBmpHeight * (nBmpWidth * 3 + nBmpWidth % 4);
        try {
            String filename = oldFile.getAbsolutePath() + System.currentTimeMillis();
            tempFile = new File(filename);
            if (!tempFile.exists()) {
                tempFile.createNewFile();
            }
            fos = new FileOutputStream(filename);
            //file header of bmp
            int bfType = 0x4d42;
            long bfSize = 14 + 40 + bufferSize;
            int bfReserved1 = 0;
            int bfReserved2 = 0;
            long bfOffBits = 14 + 40;
            //save file header
            writeWord(fos, bfType);
            writeDword(fos, bfSize);
            writeWord(fos, bfReserved1);
            writeWord(fos, bfReserved2);
            writeDword(fos, bfOffBits);
            //info header of bmp
            long biSize = 40L;
            long biWidth = nBmpWidth;
            long biHeight = nBmpHeight;
            int biPlanes = 1;
            int biBitCount = 24;
            long biCompression = 0L;
            long biSizeImage = 0L;
            long biXpelsPerMeter = 0L;
            long biYPelsPerMeter = 0L;
            long biClrUsed = 0L;
            long biClrImportant = 0L;
            //save info header of bmp
            writeDword(fos, biSize);
            writeLong(fos, biWidth);
            writeLong(fos, biHeight);
            writeWord(fos, biPlanes);
            writeWord(fos, biBitCount);
            writeDword(fos, biCompression);
            writeDword(fos, biSizeImage);
            writeLong(fos, biXpelsPerMeter);
            writeLong(fos, biYPelsPerMeter);
            writeDword(fos, biClrUsed);
            writeDword(fos, biClrImportant);
            //scans bmp pixel
            byte bmpData[] = new byte[bufferSize];
            int wWidth = (nBmpWidth * 3 + nBmpWidth % 4);
            for (int nCol = 0, nRealCol = nBmpHeight - 1; nCol < nBmpHeight; ++nCol, --nRealCol) {
                for (int wRow = 0, wByteIdex = 0; wRow < nBmpWidth; wRow++, wByteIdex += 3) {
                    int clr = bitmap.getPixel(wRow, nCol);
                    bmpData[nRealCol * wWidth + wByteIdex] = (byte) Color.blue(clr);
                    bmpData[nRealCol * wWidth + wByteIdex + 1] = (byte) Color.green(clr);
                    bmpData[nRealCol * wWidth + wByteIdex + 2] = (byte) Color.red(clr);
                }
            }

            fos.write(bmpData);
            fos.flush();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if (oldFile != null && oldFile.exists()) {
            //oldFile.delete();
            FileUtil.deleteFile(oldFile.getAbsolutePath());
        }
        tempFile.renameTo(newFile);
        return newFile;
    }

    private static void writeWord(FileOutputStream stream, int value) throws IOException {
        byte[] b = new byte[2];
        b[0] = (byte) (value & 0xff);
        b[1] = (byte) (value >> 8 & 0xff);
        stream.write(b);
    }


    private static void writeDword(FileOutputStream stream, long value) throws IOException {
        byte[] b = new byte[4];
        b[0] = (byte) (value & 0xff);
        b[1] = (byte) (value >> 8 & 0xff);
        b[2] = (byte) (value >> 16 & 0xff);
        b[3] = (byte) (value >> 24 & 0xff);
        stream.write(b);
    }

    private static void writeLong(FileOutputStream stream, long value) throws IOException {
        byte[] b = new byte[4];
        b[0] = (byte) (value & 0xff);
        b[1] = (byte) (value >> 8 & 0xff);
        b[2] = (byte) (value >> 16 & 0xff);
        b[3] = (byte) (value >> 24 & 0xff);
        stream.write(b);
    }

    public static Drawable getDrawableWithText(Context context, Bitmap icon, String text, int resColor, int resSize) {
        // 初始化画布
        Canvas canvas = new Canvas(icon);
        // 拷贝图片
        Paint iconPaint = new Paint();
        iconPaint.setDither(true);// 防抖动
        iconPaint.setFilterBitmap(true);// 用来对Bitmap进行滤波处理
        Rect src = new Rect(0, 0, icon.getWidth(), icon.getHeight());
        Rect dst = new Rect(0, 0, icon.getWidth(), icon.getHeight());
        canvas.drawBitmap(icon, src, dst, iconPaint);

        Paint textPaint = new Paint();
        textPaint.setColor(context.getResources().getColor(resColor));
        textPaint.setTextSize(context.getResources().getDimension(resSize));
        int len = (int) textPaint.measureText(text);
        Paint.FontMetricsInt fontMetrics = textPaint.getFontMetricsInt();
        int baseline = (dst.height() - fontMetrics.bottom - fontMetrics.top) / 2;
        canvas.drawText(text, icon.getWidth() / 2 - len / 2, baseline, textPaint);
        return new BitmapDrawable(context.getResources(), icon);
    }

    public static Bitmap getBitmap(Context context, int resId) {
        if (resId > 0) {
            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resId);
            return bitmap.copy(Bitmap.Config.ARGB_8888, true);
        }
        return null;
    }

    public static Bitmap drawableToBitmap(Drawable drawable) {
        // 取 drawable 的长宽
        int w = drawable.getIntrinsicWidth();
        int h = drawable.getIntrinsicHeight();

        // 取 drawable 的颜色格式
        Bitmap.Config config = drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888
                : Bitmap.Config.RGB_565;
        // 建立对应 bitmap
        Bitmap bitmap = Bitmap.createBitmap(w, h, config);
        // 建立对应 bitmap 的画布
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, w, h);
        // 把 drawable 内容画到画布中
        drawable.draw(canvas);
        return bitmap;
    }

    private static File getSourcePhotoDirectory(Context context, Uri source) {
        final File[] dir = new File[1];
        if (ContentResolver.SCHEME_FILE.equals(source.getScheme())) {
            return new File(source.getPath());
        }
        querySource(context, new String[]{MediaStore.Images.ImageColumns.DATA}, source, new ContentResolverQueryCallback() {

            @Override
            public void onCursorResult(Cursor cursor) {
                dir[0] = cursor != null ? new File(cursor.getString(0)) : null;
            }
        });
        return dir[0];
    }

    public static void querySource(Context context, String[] projection, Uri source, ContentResolverQueryCallback callback) {
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = null;
        try {
            if (ContentResolver.SCHEME_CONTENT.equals(source.getScheme())) {
                cursor = contentResolver.query(source, projection, null, null, null);
            } else if ((ContentResolver.SCHEME_FILE).equals(source.getScheme())) {
                cursor = contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, MediaStore.Images.ImageColumns.DATA + " = ? ", new String[]{source.getPath()}, null);
            }
            if ((cursor != null) && cursor.moveToNext()) {
                callback.onCursorResult(cursor);
            } else {
                callback.onCursorResult(null);
            }
        } catch (Exception e) {
            // Ignore error for lacking the data column from the source.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public static Point getImageSize(Context context, Uri uri) {
        if (uri == null) {
            Point p = new Point();
            return p;
        }

        if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            return getImageSize(uri.getPath());
        } else if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            String path = getSourcePhotoDirectory(context, uri).getAbsolutePath();
            return getImageSize(path);
        }

        return new Point();

    }

    public static Point getImageSize(String filePath) {
        Point p = new Point();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);
        p.set(options.outWidth, options.outHeight);
        return p;
    }

    private interface ContentResolverQueryCallback {

        void onCursorResult(Cursor cursor);
    }

    public static byte[] bmpToByteArray(final Bitmap bmp, final boolean needRecycle) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bmp.compress(CompressFormat.PNG, 100, output);
        if (needRecycle) {
            bmp.recycle();
        }

        byte[] result = output.toByteArray();
        try {
            output.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    public static byte[] bmpToByteArray(final Bitmap bmp, CompressFormat format, int quality, final boolean needRecycle) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bmp.compress(format, quality, output);
        if (needRecycle) {
            bmp.recycle();
        }

        byte[] result = output.toByteArray();
        try {
            output.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    public static String downloadImage(Bitmap bitmap, String dir, String suffix, int quality) {
        if (bitmap == null)
            return null;
        String path = FileUtil.mkdirs(dir);
        String time = String.valueOf(System.currentTimeMillis());
        StringBuilder sb = new StringBuilder();
        sb.append(path);
        sb.append(time);
        sb.append(suffix);
        return BitmapUtils.saveImage(bitmap, sb.toString(), quality, false);
    }
}
