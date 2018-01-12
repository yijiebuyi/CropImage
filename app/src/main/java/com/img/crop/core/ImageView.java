package com.img.crop.core;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.animation.DecelerateInterpolator;

import com.img.crop.R;
import com.img.crop.glsrender.anim.Animation;
import com.img.crop.glsrender.anim.AnimationTime;
import com.img.crop.glsrender.gl11.GLCanvas;
import com.img.crop.glsrender.gl11.GLPaint;
import com.img.crop.glsrender.gl11.GLView;
import com.img.crop.utils.Utils;

import javax.microedition.khronos.opengles.GL11;

/**
 * Created by Administrator on 2018/1/10.
 */

public class ImageView extends GLView {
    private final String TAG = "CropView";

    private static final int COLOR_OUTLINE = 0xFF008AFF;
    private static final float OUTLINE_WIDTH = 3f;

    private static final int SIZE_UNKNOWN = -1;

    private static final int TOUCH_TOLERANCE = 30;
    private static final float MIN_TOUCHMODE_SIZE = 16f;
    private static final int ANIMATION_DURATION = 600;
    private static final int ANIMATION_TRIGGER = 64;

    //MAX_SCALE值设置越大，缝隙越明显。图片绘制
    private final float MAX_SCALE = 100.0f;
    private float MIN_SCALE = 1.0f;

    private int mImageRotation;

    private TileImageView mImageView;
    private AnimationController mAnimation = new AnimationController();

    private GLPaint mPaint = new GLPaint();

    // Multi-finger operation parameters
    private float mTempScale = 1.0f;
    private float mCurrMultiCenterX;
    private float mCurrMultiCenterY;
    private float mCurrMultiScale = 1.0f;
    private float mTotalScale = 1.0f;
    private ScaleGestureDetector mScaleDetector;
    private GestureDetector mGestureDetector;

    private int mImageWidth = SIZE_UNKNOWN;
    private int mImageHeight = SIZE_UNKNOWN;
    private float mSpotlightRatioX = 0;
    private float mSpotlightRatioY = 0;

    private RectF mTempRect;
    private boolean mOnScale = false;

    private Context mContext;

    public ImageView(Context context) {
        mContext = context;

        mImageView = new TileImageView(context);
        addComponent(mImageView);

        mPaint.setColor(COLOR_OUTLINE);
        mPaint.setLineWidth(OUTLINE_WIDTH);
        mTempRect = new RectF();

        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        mGestureDetector = new GestureDetector(mContext, new GestureListener());
    }

    public void setSpotlightRatio(float ratioX, float ratioY) {
        mSpotlightRatioX = ratioX;
        mSpotlightRatioY = ratioY;
    }

    @Override
    public void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int height = b - t;

        mImageView.layout(0, 0, width, height);
        if (mImageHeight != SIZE_UNKNOWN) {
            initialize();
        }
    }

    private boolean setImageViewPosition(float centerX, float centerY, float scale) {
        float inverseX = mImageWidth - centerX;
        float inverseY = mImageHeight - centerY;
        TileImageView t = mImageView;
        int rotation = mImageRotation;
        switch (rotation) {
            case 0:
                return t.setPosition(centerX, centerY, scale, 0);
            case 90:
                return t.setPosition(centerY, inverseX, scale, 90);
            case 180:
                return t.setPosition(inverseX, inverseY, scale, 180);
            case 270:
                return t.setPosition(inverseY, centerX, scale, 270);
            default:
                throw new IllegalArgumentException(String.valueOf(rotation));
        }
    }

    @Override
    public void render(GLCanvas canvas) {
        AnimationController a = mAnimation;
        if (a != null && a.isActive()) {
            a.calculate(AnimationTime.get());
            mCurrMultiCenterX = a.getCenterX();
            mCurrMultiCenterY = a.getCenterY();
            mCurrMultiScale = a.getScale();
            invalidate();
        }

        setImageViewPosition(mCurrMultiCenterX, mCurrMultiCenterY, mCurrMultiScale);
        if (mCurrMultiScale > 2f) {
            mImageView.setChangeTextureFilter(GL11.GL_NEAREST);
        } else {
            mImageView.setChangeTextureFilter(GL11.GL_LINEAR);
        }

        super.render(canvas);
    }

    @Override
    public void renderBackground(GLCanvas canvas) {
        int bg = mContext.getResources().getColor(R.color.photo_view_background);
        canvas.clearBuffer(new float[]{Color.alpha(bg) / 255.0f, Color.red(bg) / 255.0f, Color.green(bg) / 255.0f, Color.blue(bg) / 255.0f});
    }

    public int getImageWidth() {
        return mImageWidth;
    }

    public int getImageHeight() {
        return mImageHeight;
    }

    private class AnimationController extends Animation {
        private float mCenterX;
        private float mCenterY;
        private float mCurrentScale;

        private float mStartX;
        private float mStartY;
        private float mStartScale;

        private float mTargetX;
        private float mTargetY;
        private float mTargetScale;

        public AnimationController() {
            setDuration(ANIMATION_DURATION);
            setInterpolator(new DecelerateInterpolator(4));
        }

        public void startAnimation(float centerX, float centerY, float scale) {
            forceStop();
            reset();

            mTargetX = centerX;
            mTargetY = centerY;
            mTargetScale = scale;

            start();
            invalidate();
        }

        public void inverseMapPoint(PointF point) {
            float s = mCurrentScale;
            point.x = Utils.clamp(((point.x - getWidth() * 0.5f) / s
                    + mCenterX) / mImageWidth, 0, 1);
            point.y = Utils.clamp(((point.y - getHeight() * 0.5f) / s
                    + mCenterY) / mImageHeight, 0, 1);
        }

        public RectF mapRect(RectF output) {
            float offsetX = getWidth() * 0.5f;
            float offsetY = getHeight() * 0.5f;
            float x = mCenterX;
            float y = mCenterY;
            float s = mCurrentScale;

            Log.i("aaa", "mCenterX==" + mCenterX + "   mCenterY=" + mCenterY + "   mCurrentScale=" + mCurrentScale);

            output.set(
                    offsetX + (-x) * s,
                    offsetY + (-y) * s,
                    offsetX + (mImageWidth - x) * s,
                    offsetY + (mImageHeight - y) * s);
            return output;
        }

        public void reset() {
            mCenterX = mStartX = mCurrMultiCenterX;
            mCenterY = mStartY = mCurrMultiCenterY;
            mCurrentScale = mStartScale = mCurrMultiScale;
        }

        @Override
        protected void onCalculate(float progress) {
            mCenterX = mStartX + (mTargetX - mStartX) * progress;
            mCenterY = mStartY + (mTargetY - mStartY) * progress;
            mCurrentScale = mStartScale + (mTargetScale - mStartScale) * progress;

            if (mCenterX == mTargetX && mCenterY == mTargetY
                    && mCurrentScale == mTargetScale) {
                forceStop();
            }
        }

        public float getCenterX() {
            return mCenterX;
        }

        public float getCenterY() {
            return mCenterY;
        }

        public float getScale() {
            return mCurrentScale;
        }

    }

    public void setDataModel(TileImageView.Model dataModel, int rotation) {
        if (((rotation / 90) & 0x01) != 0) {
            mImageWidth = dataModel.getImageHeight();
            mImageHeight = dataModel.getImageWidth();
        } else {
            mImageWidth = dataModel.getImageWidth();
            mImageHeight = dataModel.getImageHeight();
        }

        mImageRotation = rotation;

        mImageView.setModel(dataModel);
        initialize();
    }

    public void resume() {
        mImageView.prepareTextures();
    }

    public void pause() {
        mImageView.freeTextures();
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scale = detector.getScaleFactor();
            onScaling(scale);
            invalidate();
            return super.onScale(detector);
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return super.onScaleBegin(detector);
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            updateTotalScale(mTempScale);
            invalidate();
            super.onScaleEnd(detector);
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            onMoving(distanceX, distanceY);
            invalidate();
            return super.onScroll(e1, e2, distanceX, distanceY);
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            return super.onFling(e1, e2, velocityX, velocityY);
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return super.onDown(e);
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            return super.onDoubleTap(e);
        }
    }


    private void updateTotalScale(float scale) {
        float minScale = Math.min((float) getWidth() / mImageWidth, (float) getHeight() / mImageHeight);
        mTotalScale = Utils.clamp(scale, minScale, MAX_SCALE);
    }

    private boolean canZoomable() {
        float width = getWidth();
        float height = getHeight();
        if (mImageWidth * MAX_SCALE < (width - 1) && mImageHeight * MAX_SCALE < (height - 1)) {
            return false;
        }
        return true;
    }

    @Override
    protected boolean onTouch(MotionEvent event) {
        mGestureDetector.onTouchEvent(event);
        mScaleDetector.onTouchEvent(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (mAnimation != null) {
                    mAnimation.forceStop();
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                startRollBackAnimIfNeed();
                break;
        }

        return true;
    }

    // update the highlight when scaling by two-fingers
    private void onScaling(float scale) {
        mTempScale = scale * mTotalScale;
        mCurrMultiScale = mTempScale;
    }


    private void onMoving(float delX, float delY) {
        delX = delX / mCurrMultiScale;
        delY = delY / mCurrMultiScale;
        mCurrMultiCenterX += delX;
        mCurrMultiCenterY += delY;
    }

    private void initialize() {
        int viewWidth = getWidth();
        int viewHeight = getHeight();

        mCurrMultiCenterX = mImageWidth / 2.0f;
        mCurrMultiCenterY = mImageHeight / 2.0f;
        mCurrMultiScale = Math.min(MAX_SCALE, Math.min((float) viewWidth / mImageWidth, (float) viewHeight / mImageHeight));

        MIN_SCALE = Math.min((float) viewWidth / mImageWidth, (float) viewHeight / mImageHeight);
        // initialize the total scale
        updateTotalScale(mCurrMultiScale);
    }

    private void startRollBackAnimIfNeed() {
        if (mAnimation == null) {
            return;
        }

        float targetScale = mCurrMultiScale;
        float targetCurrentX = mCurrMultiCenterX;
        float targetCurrentY = mCurrMultiCenterY;

        boolean needAnim = false;
        if ((mCurrMultiScale < MIN_SCALE || mCurrMultiScale > MAX_SCALE)) {
            needAnim = true;
            targetScale = mCurrMultiScale < MIN_SCALE ? MIN_SCALE : MAX_SCALE;
        }

        mAnimation.reset();
        mAnimation.mapRect(mTempRect);
        int width = getWidth();
        int height = getHeight();
        if (mTempRect.left > 0 || mTempRect.right < width) {
            needAnim = true;
            float limitX = (width / 2) / mTotalScale;
            if (mImageWidth * mTotalScale > width) {
                targetCurrentX = Utils.clamp(mCurrMultiCenterX, limitX, mImageWidth - limitX);
            } else {
                targetCurrentX = mImageWidth / 2.0f;
            }
        }

        mAnimation.reset();
        mAnimation.mapRect(mTempRect);
        if (mTempRect.top > 0 || mTempRect.bottom < getHeight()) {
            needAnim = true;
            float limitY = (height / 2) / mTotalScale;
            if (mImageHeight * mTotalScale > height) {
                targetCurrentY = Utils.clamp(mCurrMultiCenterY, limitY, mImageHeight - limitY);
            } else {
                targetCurrentY = mImageHeight / 2.0f;
            }
        }

        if (needAnim) {
            mAnimation.startAnimation(targetCurrentX, targetCurrentY, targetScale);
        }
    }
}
