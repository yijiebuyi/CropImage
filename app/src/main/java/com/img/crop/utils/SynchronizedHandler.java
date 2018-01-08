package com.img.crop.utils;

import android.os.Handler;
import android.os.Message;

import com.img.crop.glsrender.gl11.GLRoot;

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
public class SynchronizedHandler extends Handler {

    private final GLRoot mRoot;

    public SynchronizedHandler(GLRoot root) {
        mRoot = root;
    }

    @Override
    public void dispatchMessage(Message message) {
        if (mRoot != null) {
            mRoot.lockRenderThread();
        }
        try {
            super.dispatchMessage(message);
        } finally {
            if (mRoot != null) {
                mRoot.unlockRenderThread();
            }
        }
    }
}