package com.img.crop;

import android.content.Context;
import android.content.res.Resources;
import android.os.Looper;

import com.img.crop.thdpool.ThreadPool;

public interface CropContext {
    public Context getAndroidContext();

    public Resources getResources();

    public ThreadPool getThreadPool();
}
