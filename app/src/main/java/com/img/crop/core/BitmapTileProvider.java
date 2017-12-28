package com.img.crop.core;

import java.util.ArrayList;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;

import com.img.crop.glsrender.gl11.BitmapScreenNail;
import com.img.crop.utils.BitmapUtils;
import com.img.crop.glsrender.gl11.ScreenNail;

public class BitmapTileProvider implements TileImageView.Model {
    private final ScreenNail mScreenNail;
    private final Bitmap[] mMipmaps;
    private final Config mConfig;
    private final int mImageWidth;
    private final int mImageHeight;

    private boolean mRecycled = false;

    public BitmapTileProvider(Bitmap bitmap, int maxBackupSize) {
        mImageWidth = bitmap.getWidth();
        mImageHeight = bitmap.getHeight();
        ArrayList<Bitmap> list = new ArrayList<Bitmap>();
        list.add(bitmap);
        while (bitmap.getWidth() > maxBackupSize
                || bitmap.getHeight() > maxBackupSize) {
            bitmap = BitmapUtils.resizeBitmapByScale(bitmap, 0.5f, false);
            list.add(bitmap);
        }

        mScreenNail = new BitmapScreenNail(list.remove(list.size() - 1));
        mMipmaps = list.toArray(new Bitmap[list.size()]);
        mConfig = Config.ARGB_8888;
    }

    @Override
    public ScreenNail getScreenNail() {
        return mScreenNail;
    }

    @Override
    public int getImageHeight() {
        return mImageHeight;
    }

    @Override
    public int getImageWidth() {
        return mImageWidth;
    }

    @Override
    public int getLevelCount() {
        return mMipmaps.length;
    }

    @Override
    public Bitmap getTile(int level, int x, int y, int tileSize,
            int borderSize, TileBitmapPool pool) {
        x >>= level;
        y >>= level;
        int size = tileSize + 2 * borderSize;

        Bitmap result = pool == null ? null : pool.getBitmap();
        if (result == null) {
            result = Bitmap.createBitmap(size, size, mConfig);
        } else {
            result.eraseColor(0);
        }

        Bitmap mipmap = mMipmaps[level];
        Canvas canvas = new Canvas(result);
        int offsetX = -x + borderSize;
        int offsetY = -y + borderSize;
        canvas.drawBitmap(mipmap, offsetX, offsetY, null);
        return result;
    }

    public void recycle() {
        if (mRecycled) return;
        mRecycled = true;
        for (Bitmap bitmap : mMipmaps) {
            BitmapUtils.recycleSilently(bitmap);
        }
        if (mScreenNail != null) {
            mScreenNail.recycle();
        }
    }
}
