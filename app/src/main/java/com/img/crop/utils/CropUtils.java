package com.img.crop.utils;


import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import com.img.crop.R;

import java.lang.reflect.Method;
import java.util.Arrays;

public class CropUtils {
    private static final String TAG = "CropUtils";
    private static float sPixelDensity = -1f;

    public static void initialize(Context context) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(metrics);
        sPixelDensity = metrics.density;
        Resources r = context.getResources();
    }

    public static float[] intColorToFloatARGBArray(int from) {
        return new float[]{
                Color.alpha(from) / 255f,
                Color.red(from) / 255f,
                Color.green(from) / 255f,
                Color.blue(from) / 255f
        };
    }

    public static float dpToPixel(float dp) {
        return sPixelDensity * dp;
    }

    public static int dpToPixel(int dp) {
        return Math.round(dpToPixel((float) dp));
    }

    public static int meterToPixel(float meter) {
        // 1 meter = 39.37 inches, 1 inch = 160 dp.
        return Math.round(dpToPixel(meter * 39.37f * 160));
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

    // Below are used the detect using database in the render thread. It only
    // works most of the time, but that's ok because it's for debugging only.

    private static volatile Thread sCurrentThread;
    private static volatile boolean sWarned;

    public static void setRenderThread() {
        sCurrentThread = Thread.currentThread();
    }

    public static void assertNotInRenderThread() {
        if (!sWarned) {
            if (Thread.currentThread() == sCurrentThread) {
                sWarned = true;
                Log.w(TAG, new Throwable("Should not do this in render thread"));
            }
        }
    }

    private static final double RAD_PER_DEG = Math.PI / 180.0;
    private static final double EARTH_RADIUS_METERS = 6367000.0;

    public static double fastDistanceMeters(double latRad1, double lngRad1,
                                            double latRad2, double lngRad2) {
        if ((Math.abs(latRad1 - latRad2) > RAD_PER_DEG)
                || (Math.abs(lngRad1 - lngRad2) > RAD_PER_DEG)) {
            return accurateDistanceMeters(latRad1, lngRad1, latRad2, lngRad2);
        }
        // Approximate sin(x) = x.
        double sineLat = (latRad1 - latRad2);

        // Approximate sin(x) = x.
        double sineLng = (lngRad1 - lngRad2);

        // Approximate cos(lat1) * cos(lat2) using
        // cos((lat1 + lat2)/2) ^ 2
        double cosTerms = Math.cos((latRad1 + latRad2) / 2.0);
        cosTerms = cosTerms * cosTerms;
        double trigTerm = sineLat * sineLat + cosTerms * sineLng * sineLng;
        trigTerm = Math.sqrt(trigTerm);

        // Approximate arcsin(x) = x
        return EARTH_RADIUS_METERS * trigTerm;
    }

    public static double accurateDistanceMeters(double lat1, double lng1,
                                                double lat2, double lng2) {
        double dlat = Math.sin(0.5 * (lat2 - lat1));
        double dlng = Math.sin(0.5 * (lng2 - lng1));
        double x = dlat * dlat + dlng * dlng * Math.cos(lat1) * Math.cos(lat2);
        return (2 * Math.atan2(Math.sqrt(x), Math.sqrt(Math.max(0.0,
                1.0 - x)))) * EARTH_RADIUS_METERS;
    }


    public static final double toMile(double meter) {
        return meter / 1609;
    }

    public static void startCameraActivity(Context context) {
        Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // This will only occur if Camera was disabled while Gallery is open
            // since we cache our availability check. Just abort the attempt.
            Log.e(TAG, "Camera activity previously detected but cannot be found", e);
        }
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

