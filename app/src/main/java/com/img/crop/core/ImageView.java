package com.img.crop.core;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.support.v4.app.NavUtils;
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
import com.img.crop.glsrender.gl11.NinePatchTexture;
import com.img.crop.utils.Utils;

import javax.microedition.khronos.opengles.GL11;

/**
 * Created by Administrator on 2018/1/10.
 */

public class ImageView extends GLView {
    private static final String TAG = "CropView";

    private static final int COLOR_OUTLINE = 0xFF008AFF;
    private static final float OUTLINE_WIDTH = 3f;

    private static final int SIZE_UNKNOWN = -1;
    private static final int TOUCH_TOLERANCE = 30;

    private static final float MIN_TOUCHMODE_SIZE = 16f;
    public static final float UNSPECIFIED = -1f;

    private static final int ANIMATION_DURATION = 1250;

    private static final int ANIMATION_TRIGGER = 64;
    private static final int CROP_FRAME_MIN_SIZE = 64;

    //MAX_SCALE值设置越大，缝隙越明显。图片绘制
    private static final float MAX_SCALE = 100.0f;

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
    private boolean mTwoFinger = false;
    private float mFirstFingerStartX;
    private float mFirstFingerStartY;
    private float mSecondFingerStartX;
    private float mSecondFingerStartY;
    private ScaleGestureDetector mScaleDetector;
    private GestureDetector mGestureDetector;

    // private TextView mCropSizeText;
    private NinePatchTexture mMinSizeFrame;
    private boolean mMultiPoint;
    private boolean mIsMoveEdges = false;
    private boolean mIsRotateAction = false;

    private int mCustomizeCropWidth;
    private int mCustomizeCropHeight;
    private int mImageWidth = SIZE_UNKNOWN;
    private int mImageHeight = SIZE_UNKNOWN;
    private float mSpotlightRatioX = 0;
    private float mSpotlightRatioY = 0;

    private RectF mCurrentHighlightRect = new RectF();
    private RectF mTempRect;
    private RectF mTempOutRect = new RectF();

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
            mAnimation.initialize();
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
        float scale = 1.0f;
        AnimationController a = mAnimation;
        if (mMultiPoint) {
            setImageViewPosition(mCurrMultiCenterX, mCurrMultiCenterY, mCurrMultiScale);
            scale = mCurrMultiScale;
        } else {
            if (a.calculate(AnimationTime.get())) {
                invalidate();
            }
            setImageViewPosition(a.getCenterX(), a.getCenterY(), a.getScale());
            scale = a.getScale();
        }

        if (scale > 2f) {
            mImageView.setChangeTextureFilter(GL11.GL_NEAREST);
        } else {
            mImageView.setChangeTextureFilter(GL11.GL_LINEAR);
        }

        super.render(canvas);
    }

    @Override
    public void renderBackground(GLCanvas canvas) {
        int bg = mContext.getResources().getColor(R.color.crop_background);
        canvas.clearBuffer(new float[]{Color.alpha(bg) / 255.0f, Color.red(bg) / 255.0f, Color.green(bg) / 255.0f, Color.blue(bg) / 255.0f});
    }

    public int getImageWidth() {
        return mImageWidth;
    }

    public int getImageHeight() {
        return mImageHeight;
    }

    private class AnimationController extends Animation {
        private float mCurrentX;
        private float mCurrentY;
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

        public void initialize() {
            mCurrentX = mImageWidth / 2.0f;
            mCurrentY = mImageHeight / 2.0f;
            mCurrentScale = Math.min(MAX_SCALE, Math.min(
                    (float) getWidth() / mImageWidth,
                    (float) getHeight() / mImageHeight));

            // initialize the total scale
            mCurrMultiCenterX = mCurrentX;
            mCurrMultiCenterY = mCurrentY;
            mCurrMultiScale = mCurrentScale;
            updateTotalScale(mCurrentScale);
        }

        public void startParkingAnimation() {
            //mStartX = mCurrentX;
            //mStartY = mCurrentY;
            //mStartScale = mCurrentScale;

            mStartX = mCurrMultiCenterX;
            mStartY = mCurrMultiCenterY;
            mStartScale = mCurrMultiScale;

            calculateTarget();
            start();
        }

        //start the animation of roll_back
        public void startRollbackingAnimation() {
            mStartX = mCurrMultiCenterX;
            mStartY = mCurrMultiCenterY;
            mStartScale = mCurrMultiScale;

            float width = getWidth();
            float height = getHeight();

            float scale = mCurrentScale;
            float centerX = mImageWidth * 0.5f;
            float centerY = mImageHeight * 0.5f;

            if (mImageWidth * scale > width) {
                float limitX = width * 0.5f / scale;
                centerX = mImageWidth / 2.0f;
                centerX = Utils.clamp(centerX, limitX, mImageWidth - limitX);
            } else {
                centerX = mImageWidth / 2.0f;
            }
            if (mImageHeight * scale > height) {
                float limitY = height * 0.5f / scale;
                centerY = mImageHeight / 2.0f;
                centerY = Utils.clamp(centerY, limitY, mImageHeight - limitY);
            } else {
                centerY = mImageHeight / 2.0f;
            }
            mCurrMultiCenterX = mTargetX = centerX;
            mCurrMultiCenterY = mTargetY = centerY;
            mCurrMultiScale = mTargetScale = scale;

            updateTotalScale(mTargetScale);
            start();
        }

        public void parkNow() {
            calculateTarget();
            forceStop();
            mStartX = mCurrentX = mTargetX;
            mStartY = mCurrentY = mTargetY;
            mStartScale = mCurrentScale = mTargetScale;
        }

        public void inverseMapPoint(PointF point) {
            float s = mCurrentScale;
            point.x = Utils.clamp(((point.x - getWidth() * 0.5f) / s
                    + mCurrentX) / mImageWidth, 0, 1);
            point.y = Utils.clamp(((point.y - getHeight() * 0.5f) / s
                    + mCurrentY) / mImageHeight, 0, 1);
        }

        public RectF mapRect(RectF output) {
            float offsetX = getWidth() * 0.5f;
            float offsetY = getHeight() * 0.5f;
            float x = mCurrentX;
            float y = mCurrentY;
            float s = mCurrentScale;

            output.set(
                    offsetX + (-x) * s,
                    offsetY + (-y) * s,
                    offsetX + (mImageWidth - x) * s,
                    offsetY + (mImageHeight - y) * s);
            return output;
        }

        @Override
        protected void onCalculate(float progress) {
            mCurrentX = mStartX + (mTargetX - mStartX) * progress;
            mCurrentY = mStartY + (mTargetY - mStartY) * progress;
            mCurrentScale = mStartScale + (mTargetScale - mStartScale) * progress;

            if (mCurrentX == mTargetX && mCurrentY == mTargetY
                    && mCurrentScale == mTargetScale) forceStop();
        }

        public float getCenterX() {
            return mCurrentX;
        }

        public float getCenterY() {
            return mCurrentY;
        }

        public float getScale() {
            return mCurrentScale;
        }

        private void calculateTarget() {
            float width = getWidth();
            float height = getHeight();

            if (mImageWidth != SIZE_UNKNOWN) {
                float minScale = Math.min(width / mImageWidth, height / mImageHeight);
                float scale = Utils.clamp(Math.min(width / mImageWidth, height / mImageHeight), minScale, MAX_SCALE);
                float centerX = mImageWidth / 2.0f;
                float centerY = mImageHeight / 2.0f;

                if (mImageWidth * scale > width) {
                    float limitX = width * 0.5f / scale;
                    centerX = mImageWidth / 2.0f;
                    centerX = Utils.clamp(centerX, limitX, mImageWidth - limitX);
                } else {
                    centerX = mImageWidth / 2.0f;
                }
                if (mImageHeight * scale > height) {
                    float limitY = height * 0.5f / scale;
                    centerY = mImageHeight / 2.0f;
                    centerY = Utils.clamp(centerY, limitY, mImageHeight - limitY);
                } else {
                    centerY = mImageHeight / 2.0f;
                }
                mTargetX = centerX;
                mTargetY = centerY;
                mTargetScale = scale;

                mCurrMultiCenterX = centerX;
                mCurrMultiCenterY = centerY;
                mCurrMultiScale = scale;
                updateTotalScale(mTargetScale);
            }
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
        mAnimation.initialize();
    }

    public void resume() {
        mImageView.prepareTextures();
    }

    public void pause() {
        mImageView.freeTextures();
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        private boolean mOnScale = false;

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scale = detector.getScaleFactor();
            if (canZoomable()) {
                mOnScale = true;
                updateHighlightRectangle(scale);
                if (mAnimation != null) {
                    mAnimation.startParkingAnimation();
                }
            }
            invalidate();
            return super.onScale(detector);
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mOnScale = false;
            return super.onScaleBegin(detector);
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            if (canZoomable() && mOnScale) {
                updateTotalScale(mTempScale);
                updateAnimationInfo(mCurrMultiCenterX, mCurrMultiCenterY, mTempScale);
            }
            invalidate();
            super.onScaleEnd(detector);
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
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

    private void updateAnimationInfo(float centerX, float centerY, float scale) {
        mAnimation.mTargetX = mAnimation.mStartX = mAnimation.mCurrentX = centerX;
        mAnimation.mTargetY = mAnimation.mStartY = mAnimation.mCurrentY = centerY;
        mAnimation.mTargetScale = mAnimation.mStartScale = mAnimation.mCurrentScale = scale;
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
        return true;
    }


    // update the highlight when scaling by two-fingers
    public void updateHighlightRectangle(float scale) {
        float width = getWidth();
        float height = getHeight();
        float minScale = Math.min(width / mImageWidth, height / mImageHeight);
        mTempScale = Utils.clamp(scale * mTotalScale, minScale, MAX_SCALE);
        float limitX = width * 0.5f / mTempScale;
        float limitY = height * 0.5f / mTempScale;

        // if zoom out, update the center coordinate of picture and highlight frame
        RectF tempRect = new RectF();

        if (scale < 1.0f) {
            if (mImageWidth * mTempScale > width) {
                mCurrMultiCenterX = Utils.clamp(mCurrMultiCenterX, limitX, mImageWidth - limitX);
            } else {
                mCurrMultiCenterX = mImageWidth / 2.0f;
            }

            if (mImageHeight * mTempScale > height) {
                mCurrMultiCenterY = Utils.clamp(mCurrMultiCenterY, limitY, mImageHeight - limitY);
            } else {
                mCurrMultiCenterY = mImageHeight / 2.0f;
            }
        }

        mAnimation.mCurrentX = mCurrMultiCenterX;
        mAnimation.mCurrentY = mCurrMultiCenterY;
        mCurrMultiScale = mTempScale;

        mAnimation.mapRect(tempRect);
    }
}
