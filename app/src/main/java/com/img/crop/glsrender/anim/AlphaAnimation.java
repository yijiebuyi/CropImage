package com.img.crop.glsrender.anim;


import com.img.crop.glsrender.gl11.GLCanvas;
import com.img.crop.utils.Utils;

public class AlphaAnimation extends CanvasAnimation {
    private final float mStartAlpha;
    private final float mEndAlpha;
    private float mCurrentAlpha;

    public AlphaAnimation(float from, float to) {
        mStartAlpha = from;
        mEndAlpha = to;
        mCurrentAlpha = from;
    }

    @Override
    public void apply(GLCanvas canvas) {
        canvas.multiplyAlpha(mCurrentAlpha);
    }

    @Override
    public int getCanvasSaveFlags() {
        return GLCanvas.SAVE_FLAG_ALPHA;
    }

    @Override
    protected void onCalculate(float progress) {
        mCurrentAlpha = Utils.clamp(mStartAlpha
                + (mEndAlpha - mStartAlpha) * progress, 0f, 1f);
    }
    
    @Override
	public void reset() {
		mCurrentAlpha = mStartAlpha;
	}
}
