package com.img.crop.glsrender.anim;

import android.os.SystemClock;

public class AnimationTime {
    private static volatile long sTime;

    // Sets current time as the animation time.
    public static void update() {
        sTime = SystemClock.uptimeMillis();
    }

    // Returns the animation time.
    public static long get() {
        return sTime;
    }

    public static long startTime() {
        sTime = SystemClock.uptimeMillis();
        return sTime;
    }
}