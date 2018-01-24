package com.img.crop.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;

import com.img.crop.core.BitmapTileProvider;
import com.img.crop.core.GLImageView;
import com.img.crop.core.TileImageView;
import com.img.crop.glsrender.gl11.GLRootView;

/**
 * Created by Administrator on 2018/1/10.
 */
public class PhotoView extends GLRootView {
    private GLImageView mImageView;

    public PhotoView(Context context) {
        super(context);
        init(context);
    }

    public PhotoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        mImageView = new GLImageView(context);
        setContentPane(mImageView);
    }

    public void onResume() {
        super.onResume();

        lockRenderThread();
        try {
            mImageView.resume();
        } finally {
            unlockRenderThread();
        }
    }

    public void onPause() {
        super.onPause();

        lockRenderThread();

        try {
            mImageView.pause();
        } finally {
            unlockRenderThread();
        }
    }

    public void setImageBitmap(Bitmap bmp) {
        setImageBitmap(bmp, 0);
    }

    public void setImageBitmap(Bitmap bmp, int rotation) {
        mImageView.setDataModel(new BitmapTileProvider(bmp, 512), rotation);
    }


    public void setDataModel(TileImageView.Model dataModel) {
        setDataModel(dataModel, 0);
    }

    public void setDataModel(TileImageView.Model dataModel, int rotation) {
        mImageView.setDataModel(dataModel, rotation);
    }

}
