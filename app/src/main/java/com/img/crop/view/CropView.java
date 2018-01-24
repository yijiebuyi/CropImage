package com.img.crop.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.AttributeSet;

import com.img.crop.core.BitmapTileProvider;
import com.img.crop.core.GLCropView;
import com.img.crop.core.TileImageView;
import com.img.crop.glsrender.gl11.GLRootView;

/*
 * Copyright (C) 2017
 * 版权所有
 *
 * 功能描述：
 * 作者：huangyong
 * 创建时间：2018/1/24
 *
 * 修改人：
 * 修改描述：
 * 修改日期
 */
public class CropView extends GLRootView {
    private GLCropView mGLCropView;

    public CropView(Context context) {
        super(context);
        init(context);
    }

    public CropView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        mGLCropView = new GLCropView(context);
        setContentPane(mGLCropView);
    }

    public void onResume() {
        super.onResume();

        lockRenderThread();
        try {
            mGLCropView.resume();
        } finally {
            unlockRenderThread();
        }
    }

    public void onPause() {
        super.onPause();

        lockRenderThread();
        try {
            mGLCropView.pause();
        } finally {
            unlockRenderThread();
        }
    }

    public void setImageBitmap(Bitmap bmp) {
        setImageBitmap(bmp, 0);
    }

    public void setImageBitmap(Bitmap bmp, int rotation) {
        mGLCropView.setDataModel(new BitmapTileProvider(bmp, 512), rotation);
    }


    public void setDataModel(TileImageView.Model dataModel) {
        setDataModel(dataModel, 0);
    }

    public void setDataModel(TileImageView.Model dataModel, int rotation) {
        mGLCropView.setDataModel(dataModel, rotation);
    }

    public void setAspectRatio(float ratio) {
        mGLCropView.setAspectRatio(ratio);
    }

    public void setSpotlightRatio(float ratioX, float ratioY) {
        mGLCropView.setSpotlightRatio(ratioX, ratioY);
    }

    public float getAspectRatio() {
        return mGLCropView.getAspectRatio();
    }

    public void initializeHighlightRectangle() {
        mGLCropView.initializeHighlightRectangle();
    }

    public RectF getCropRectangle() {
        return mGLCropView.getCropRectangle();
    }

    public int getImageWidth() {
        return mGLCropView.getImageWidth();

    }

    public int getImageHeight() {
        return mGLCropView.getImageHeight();
    }

    public void setOnCropSizeChangeListener(GLCropView.OnCropSizeChangeListener listener) {
        mGLCropView.setOnCropSizeChangeListener(listener);
    }

    public void setCustomizeCropSize(int width, int height) {
        mGLCropView.setCustomizeCropSize(width, height);
    }

    public void rotateCropFrame() {
        mGLCropView.rotateCropFrame();
    }
}
