package com.img.crop.utils;

import android.os.Handler;
import android.os.Message;

import com.img.crop.glsrender.gl11.GLRoot;

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