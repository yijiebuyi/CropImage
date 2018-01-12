package com.img.crop.glsrender.anim;

import android.view.animation.Interpolator;

import com.img.crop.utils.Utils;

abstract public class Animation {
    private static final long ANIMATION_START = -1;
    private static final long NO_ANIMATION = -2;

    private long mStartTime = NO_ANIMATION;
    private int mDuration;
    private Interpolator mInterpolator;
    private AnimListener mAnimListener;

    public void setInterpolator(Interpolator interpolator) {
        mInterpolator = interpolator;
    }

    public void setAnimListener(AnimListener listener) {
        mAnimListener = listener;
    }

    public void setDuration(int duration) {
        mDuration = duration;
    }

    public void start() {
        mStartTime = ANIMATION_START;
        if (mAnimListener != null) {
            mAnimListener.onStart();
        }
    }

    public void setStartTime(long time) {
        mStartTime = time;
    }

    public boolean isActive() {
        return mStartTime != NO_ANIMATION;
    }

    public void forceStop() {
        mStartTime = NO_ANIMATION;
    }

    public boolean calculate(long currentTimeMillis) {
        if (mStartTime == NO_ANIMATION) {
            if (mAnimListener != null) {
                mAnimListener.onStop();
            }
            return false;
        }

        if (mStartTime == ANIMATION_START) {
            mStartTime = currentTimeMillis;
        }
        int elapse = (int) (currentTimeMillis - mStartTime);
        float x = Utils.clamp((float) elapse / mDuration, 0f, 1f);
        Interpolator i = mInterpolator;
        onCalculate(i != null ? i.getInterpolation(x) : x);
        if (elapse >= mDuration) {
            mStartTime = NO_ANIMATION;
            if (mAnimListener != null) {
                mAnimListener.onEnd();
            }
        }

        return mStartTime != NO_ANIMATION;
    }

    public void reset() {
    }

    abstract protected void onCalculate(float progress);

}
