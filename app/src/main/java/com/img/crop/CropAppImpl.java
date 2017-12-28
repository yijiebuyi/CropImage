package com.img.crop;

import android.app.Application;
import android.content.Context;
import android.os.AsyncTask;

import com.img.crop.thdpool.ThreadPool;
import com.img.crop.utils.CropUtils;


public class CropAppImpl extends Application {
    private static final String DOWNLOAD_FOLDER = "download";
    private static final long DOWNLOAD_CAPACITY = 64 * 1024 * 1024; // 64M

    private Object mLock = new Object();
    private ThreadPool mThreadPool;

    @Override
    public void onCreate() {
        super.onCreate();
        initializeAsyncTask();
        CropUtils.initialize(this);
    }

    public Context getAndroidContext() {
        return this;
    }


    public synchronized ThreadPool getThreadPool() {
        if (mThreadPool == null) {
            mThreadPool = new ThreadPool();
        }
        return mThreadPool;
    }

    private void initializeAsyncTask() {
        // AsyncTask class needs to be loaded in UI thread.
        // So we load it here to comply the rule.
        try {
            Class.forName(AsyncTask.class.getName());
        } catch (ClassNotFoundException e) {
        }
    }
}
