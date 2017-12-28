package com.img.crop.glsrender.gl11;

import android.content.Context;
import android.graphics.Matrix;
import android.view.View;

import com.img.crop.glsrender.anim.CanvasAnimation;

public interface GLRoot {

    // Listener will be called when GL is idle AND before each frame.
    // Mainly used for uploading textures.
    public static interface OnGLIdleListener {
        public boolean onGLIdle(
                GLCanvas canvas, boolean renderRequested);
    }

    public void addOnGLIdleListener(OnGLIdleListener listener);
    public void registerLaunchedAnimation(CanvasAnimation animation);
    public void requestRenderForced();
    public void requestRender();
    public void requestLayoutContentPane();

    public void lockRenderThread();
    public void unlockRenderThread();

    public void setContentPane(GLView content);
    public void setOrientationSource(OrientationSource source);
    public int getDisplayRotation();
    public int getCompensation();
    public Matrix getCompensationMatrix();
    public void freeze();
    public void unfreeze();
    public void setLightsOutMode(boolean enabled);
    
    public View getView();
    public Context getContext();
}
