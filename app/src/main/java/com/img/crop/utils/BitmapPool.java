package com.img.crop.utils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import android.graphics.Bitmap;

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
public class BitmapPool {
    private ArrayList<WeakReference<Bitmap>> mPool;

    private static BitmapPool sPool[] = new BitmapPool[]{
            new BitmapPool(), new BitmapPool()};

    // Construct a BitmapPool which caches bitmap with any size;
    public BitmapPool() {
        mPool = new ArrayList<WeakReference<Bitmap>>();
    }

    // Get a Bitmap from the pool with the specified size.
    public synchronized Bitmap getBitmap(int width, int height) {
        for (int i = mPool.size() - 1; i >= 0; --i) {
            WeakReference<Bitmap> ref = mPool.get(i);
            if (ref.get() != null) {
                Bitmap bitmap = ref.get();
                if (bitmap.getWidth() == width && bitmap.getHeight() == height) {
                    mPool.remove(i);
                    return bitmap;
                }
            } else {
                mPool.remove(ref);
            }
        }
        return null;
    }

    // Put a Bitmap into the pool, if the Bitmap has a proper size. Otherwise
    // the Bitmap will be recycled. If the pool is full, an old Bitmap will be
    // recycled.
    public synchronized void recycle(Bitmap bitmap) {
        if (bitmap == null) return;

        synchronized (this) {
            for (int i = mPool.size() - 1; i >= 0; --i) {
                WeakReference<Bitmap> ref = mPool.get(i);
                if (ref.get() == null) {
                    mPool.remove(i);
                }
            }
            mPool.add(new WeakReference<Bitmap>(bitmap));
        }
    }


    public static BitmapPool getPool(int type) {
        return sPool[type];
    }

    public synchronized void clear() {
    }

    public void clearAll() {
        mPool.clear();
    }

    public synchronized void release() {
        for (int i = mPool.size() - 1; i >= 0; --i) {
            WeakReference<Bitmap> ref = mPool.get(i);
            if (ref.get() != null) {
                Bitmap bitmap = ref.get();
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
        }

        clearAll();
    }

    public static void setRecycle(boolean recycle) {
    }

}
