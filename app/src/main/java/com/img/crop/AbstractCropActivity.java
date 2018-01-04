package com.img.crop;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import com.img.crop.utils.BitmapPool;
import com.img.crop.glsrender.gl11.GLRoot;
import com.img.crop.glsrender.gl11.GLRootView;
import com.img.crop.thdpool.ThreadPool;
import com.img.crop.utils.CropUtils;
import com.img.crop.utils.Utils;

public class AbstractCropActivity extends FragmentActivity implements CropContext {
    private GLRootView mGLRootView;

    @Override
    protected void onCreate(Bundle arg0) {
        super.onCreate(arg0);
        getWindow().setBackgroundDrawable(null);

        Configuration cfg = getResources().getConfiguration();
        float fontScale = cfg.fontScale;
        fontScale = Utils.clamp(fontScale, Utils.FONT_SCALE_NORMAL, Utils.FONT_SCALE_BIG);
        Utils.sFontScale = fontScale;
        Utils.sLocale = cfg.locale;
    }

    @Override
    public void setContentView(int resId) {
        super.setContentView(resId);
        mGLRootView = (GLRootView) findViewById(R.id.gl_root_view);
    }

    public GLRoot getGLRoot() {
        return mGLRootView;
    }

    @Override
    protected void onPause() {
        if (mGLRootView != null) {
            mGLRootView.onPause();
        }
        if (mGLRootView != null) {
            mGLRootView.lockRenderThread();
        }

        try {
            if (mGLRootView != null) {
                mGLRootView.unlockRenderThread();
            }
        } finally {

        }

        CropUtils.clearInput(this);
        super.onPause();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        if (mGLRootView != null) {
            mGLRootView.lockRenderThread();
        }
        try {
            if (mGLRootView != null) {
                mGLRootView.unlockRenderThread();
            }
        } finally {

        }
        if (mGLRootView != null) {
            mGLRootView.onResume();
        }
        super.onResume();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    public Context getAndroidContext() {
        return this;
    }

    @Override
    public ThreadPool getThreadPool() {
        Context ap = getApplication();
        if (ap instanceof CropAppImpl) {
            return ((CropAppImpl) ap).getThreadPool();
        }

        return null;
    }

    private static void clearBitmapPool(BitmapPool pool) {
        if (pool != null)
            pool.clear();
    }

}
