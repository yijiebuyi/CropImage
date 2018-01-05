package com.img.crop.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.media.FaceDetector;
import android.os.Handler;
import android.os.Message;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.animation.DecelerateInterpolator;

import com.img.crop.CropImageActivity;
import com.img.crop.R;
import com.img.crop.glsrender.anim.Animation;
import com.img.crop.glsrender.anim.AnimationTime;
import com.img.crop.glsrender.gl11.GLCanvas;
import com.img.crop.glsrender.gl11.GLPaint;
import com.img.crop.glsrender.gl11.GLRoot;
import com.img.crop.glsrender.gl11.GLView;
import com.img.crop.glsrender.gl11.NinePatchTexture;
import com.img.crop.utils.SynchronizedHandler;
import com.img.crop.utils.Utils;

import java.util.ArrayList;

import javax.microedition.khronos.opengles.GL11;

public class CropView extends GLView {
    private static final String TAG = "CropView";

    private static final int FACE_PIXEL_COUNT = 120000; // around 400x300

    private static final int COLOR_OUTLINE = 0xFF008AFF;
    private static final int COLOR_FACE_OUTLINE = 0xFF000000;

    private static final float OUTLINE_WIDTH = 3f;

    private static final int SIZE_UNKNOWN = -1;
    private static final int TOUCH_TOLERANCE = 30;

    private static final float MIN_TOUCHMODE_SIZE = 16f;
    private static final float MIN_SELECTION_LENGTH = 1f;
    public static final float UNSPECIFIED = -1f;

    private static final int MAX_FACE_COUNT = 3;
    private static final float FACE_EYE_RATIO = 2f;

    private static final int ANIMATION_DURATION = 1250;

    private static final int MOVE_LEFT = 1;
    private static final int MOVE_TOP = 2;
    private static final int MOVE_RIGHT = 4;
    private static final int MOVE_BOTTOM = 8;
    private static final int MOVE_BLOCK = 16;

    private static final int ONTOUCH_LEFT = 1;
    private static final int ONTOUCH_RIGHT = 2;
    private static final int ONTOUCH_TOP = 4;
    private static final int ONTOUCH_BOTTOM = 8;
    private static final int ONTOUCH_NOTHING = 16;

    private static final float MAX_SELECTION_RATIO = 0.8f;
    private static final float MIN_SELECTION_RATIO = 0.4f;
    private static final float SELECTION_RATIO = 0.60f;
    private static final int ANIMATION_TRIGGER = 64;

    private static final int MSG_UPDATE_FACES = 1;
    private static final int CROP_FRAME_MINSIZE = 64;

    //如小尺寸图片[如32x32以下尺寸的图片]裁剪时，有时会出现裁剪框离边缘有较小的缝隙。
    //MAX_SCALE值设置越大，缝隙越明显。[图片绘制/高亮框/裁剪素材绘制的算法不一致导致，根本原因：精度丢失]
    private static final float MAX_SCALE = 100.0f;

    private float mAspectRatio = UNSPECIFIED;
    private float mSpotlightRatioX = 0;
    private float mSpotlightRatioY = 0;

    private Handler mMainHandler;

    private FaceHighlightView mFaceDetectionView;
    private HighlightRectangle mHighlightRectangle;
    private TileImageView mImageView;
    private AnimationController mAnimation = new AnimationController();

    private int mImageWidth = SIZE_UNKNOWN;
    private int mImageHeight = SIZE_UNKNOWN;

    private Context mContext;

    private GLPaint mPaint = new GLPaint();
    private GLPaint mFacePaint = new GLPaint();

    private int mImageRotation;
    private int mTouchEdges = ONTOUCH_NOTHING;

    private RectF mTempRect;
    private RectF mTempOutRect = new RectF();

    // Multi-finger operation parameters
    private float mTempScale = 1.0f;
    private float mCurrMultiCenterX;
    private float mCurrMultiCenterY;
    private float mCurrMultiScale = 1.0f;
    private float mTotalScale = 1.0f;
    private ScaleGestureDetector mScaleDetector;
    private boolean mTwoFinger = false;
    private float mFirstFingerStartX;
    private float mFirstFingerStartY;
    private float mSecondFingerStartX;
    private float mSecondFingerStartY;

    // private TextView mCropSizeText;
    private NinePatchTexture mCropFrame;
    private NinePatchTexture mNormalFrame;
    private NinePatchTexture mMinSizeFrame;
    private boolean mFlippable = true;
    private boolean mMultiPoint;
    private boolean mIsMoveEdges = false;
    private boolean mIsRotateAction = false;

    private int mCustomizeCropWidth;
    private int mCustomizeCropHeight;
    private RectF mCurrentHighlightRect = new RectF();

    public CropView(Context context, GLRoot glRoot) {
        mContext = context;
        mImageView = new TileImageView(context);
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        //mFaceDetectionView = new FaceHighlightView();
        mHighlightRectangle = new HighlightRectangle();

        addComponent(mImageView);
        //addComponent(mFaceDetectionView);
        addComponent(mHighlightRectangle);

        mHighlightRectangle.setVisibility(GLView.INVISIBLE);

        mPaint.setColor(COLOR_OUTLINE);
        mPaint.setLineWidth(OUTLINE_WIDTH);

        mFacePaint.setColor(COLOR_FACE_OUTLINE);
        mFacePaint.setLineWidth(OUTLINE_WIDTH);

        mMainHandler = new SynchronizedHandler(glRoot) {
            @Override
            public void handleMessage(Message message) {
                Utils.assertTrue(message.what == MSG_UPDATE_FACES);
                ((DetectFaceTask) message.obj).updateFaces();
            }
        };

        mTempRect = new RectF();
        //mCropSizeText = (TextView)((Activity)mActivity).findViewById(R.id.cropimage_res);
        //mNormalFrame = new NinePatchTexture(activity.getAndroidContext(), R.drawable.crop_frame);
        mNormalFrame = new NinePatchTexture(context, R.drawable.crop_frame_big);
        mCropFrame = mNormalFrame;
    }

    public void setAspectRatio(float ratio) {
        if (ratio == mAspectRatio)
            return;
        mAspectRatio = ratio;
        if (mAspectRatio != UNSPECIFIED) {
            float centerX = mHighlightRectangle.mHighlightRect.centerX();
            float centerY = mHighlightRectangle.mHighlightRect.centerY();
            RectF dstRect = mHighlightRectangle.mHighlightRect;
            float targetRatio = ratio * mImageHeight / mImageWidth;
            mTempRect.set(0, 0, targetRatio, 1f);
            RectF srcRect = mTempRect;
            Utils.fitRectFInto(srcRect, dstRect, dstRect);
            dstRect.offset(centerX - dstRect.centerX(), centerY - dstRect.centerY());
            mHighlightRectangle.updateFrame();
            updateCropSizeText();
        }
    }

    public float getAspectRatio() {
        return mAspectRatio;
    }

    public void setSpotlightRatio(float ratioX, float ratioY) {
        mSpotlightRatioX = ratioX;
        mSpotlightRatioY = ratioY;
    }

    @Override
    public void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int height = b - t;

        //mFaceDetectionView.layout(0, 0, width, height);
        mHighlightRectangle.layout(0, 0, width, height);
        mImageView.layout(0, 0, width, height);
        if (mImageHeight != SIZE_UNKNOWN) {
            mAnimation.initialize();
            mHighlightRectangle.updateFrame();
            if (mHighlightRectangle.getVisibility() == GLView.VISIBLE) {
                mAnimation.parkNow(
                        mHighlightRectangle.mHighlightRect);
            }
        }

        /*CropBottomBar bottomBar = ((CropImageActivity)mActivity).getBottomBar();
        if (bottomBar != null) {
            bottomBar.layout(0, b - CropUtils.CROP_BOTTOMBAR_HEIGHT, r, b);
        }*/
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
                if (mHighlightRectangle != null) {
                    mHighlightRectangle.updateFrame();
                }

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

    public RectF getCropRectangle() {
        if (mHighlightRectangle.getVisibility() == GLView.INVISIBLE) return null;
        RectF rect = mHighlightRectangle.mHighlightRect;
        RectF result = new RectF(rect.left * mImageWidth, rect.top * mImageHeight,
                rect.right * mImageWidth, rect.bottom * mImageHeight);
        return result;
    }

    public int getImageWidth() {
        return mImageWidth;
    }

    public int getImageHeight() {
        return mImageHeight;
    }

    private class FaceHighlightView extends GLView {
        private static final int INDEX_NONE = -1;
        private ArrayList<RectF> mFaces = new ArrayList<RectF>();
        private RectF mRect = new RectF();
        private int mPressedFaceIndex = INDEX_NONE;

        public void addFace(RectF faceRect) {
            mFaces.add(faceRect);
            invalidate();
        }

        private void renderFace(GLCanvas canvas, RectF face, boolean pressed) {
            //TODO
            GL11 gl = canvas.getGLInstance();
            if (pressed) {
                gl.glEnable(GL11.GL_STENCIL_TEST);
                gl.glClear(GL11.GL_STENCIL_BUFFER_BIT);
                gl.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
                gl.glStencilFunc(GL11.GL_ALWAYS, 1, 1);
            }

            RectF r = mAnimation.mapRect(face, mRect);
            canvas.fillRect(r.left, r.top, r.width(), r.height(), Color.TRANSPARENT);
            // canvas.drawRect(r.left, r.top, r.width(), r.height(), mFacePaint);

            if (pressed) {
                gl.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            }
        }

        @Override
        protected void renderBackground(GLCanvas canvas) {
            ArrayList<RectF> faces = mFaces;
            for (int i = 0, n = faces.size(); i < n; ++i) {
                renderFace(canvas, faces.get(i), i == mPressedFaceIndex);
            }

            //TODO
            GL11 gl = canvas.getGLInstance();
            if (mPressedFaceIndex != INDEX_NONE) {
                gl.glStencilFunc(GL11.GL_NOTEQUAL, 1, 1);
                canvas.fillRect(0, 0, getWidth(), getHeight(), 0x66000000);
                gl.glDisable(GL11.GL_STENCIL_TEST);
            }
        }

        private void setPressedFace(int index) {
            if (mPressedFaceIndex == index) return;
            mPressedFaceIndex = index;
            invalidate();
        }

        private int getFaceIndexByPosition(float x, float y) {
            ArrayList<RectF> faces = mFaces;
            for (int i = 0, n = faces.size(); i < n; ++i) {
                RectF r = mAnimation.mapRect(faces.get(i), mRect);
                if (r.contains(x, y)) return i;
            }
            return INDEX_NONE;
        }

        @Override
        protected boolean onTouch(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE: {
                    setPressedFace(getFaceIndexByPosition(x, y));
                    break;
                }
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP: {
                    int index = mPressedFaceIndex;
                    setPressedFace(INDEX_NONE);
                    if (index != INDEX_NONE) {
                        mHighlightRectangle.setRectangle(mFaces.get(index));
                        mHighlightRectangle.setVisibility(GLView.VISIBLE);
                        setVisibility(GLView.INVISIBLE);
                    }
                }
            }
            return true;
        }
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

        public void startParkingAnimation(RectF highlight) {
            RectF r = mAnimation.mapRect(highlight, new RectF());
            int width = getWidth();
            int height = getHeight();

            float wr = r.width() / width;
            float hr = r.height() / height;
            final int d = ANIMATION_TRIGGER;
            if (wr >= MIN_SELECTION_RATIO && wr < MAX_SELECTION_RATIO
                    && hr >= MIN_SELECTION_RATIO && hr < MAX_SELECTION_RATIO
                    && r.left >= d && r.right < width - d
                    && r.top >= d && r.bottom < height - d) return;

            //mStartX = mCurrentX;
            //mStartY = mCurrentY;
            //mStartScale = mCurrentScale;

            mStartX = mCurrMultiCenterX;
            mStartY = mCurrMultiCenterY;
            mStartScale = mCurrMultiScale;

            calculateTarget(highlight);
            start();
        }

        //start the animation of roll_back
        public void startRollbackingAnimation(RectF highlight) {
            mStartX = mCurrMultiCenterX;
            mStartY = mCurrMultiCenterY;
            mStartScale = mCurrMultiScale;

            float width = getWidth();
            float height = getHeight();

            float scale = mCurrentScale;
            float centerX = mImageWidth * (highlight.left + highlight.right) * 0.5f;
            float centerY = mImageHeight * (highlight.top + highlight.bottom) * 0.5f;

            if (mImageWidth * scale > width) {
                float limitX = width * 0.5f / scale;
                centerX = (highlight.left + highlight.right) * mImageWidth / 2.0f;
                centerX = Utils.clamp(centerX, limitX, mImageWidth - limitX);
            } else {
                centerX = mImageWidth / 2.0f;
            }
            if (mImageHeight * scale > height) {
                float limitY = height * 0.5f / scale;
                centerY = (highlight.top + highlight.bottom) * mImageHeight / 2.0f;
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

        public void parkNow(RectF highlight) {
            calculateTarget(highlight);
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

        public RectF mapRect(RectF input, RectF output) {
            float offsetX = getWidth() * 0.5f;
            float offsetY = getHeight() * 0.5f;
            float x = mCurrentX;
            float y = mCurrentY;
            float s = mCurrentScale;

            output.set(
                    offsetX + (input.left * mImageWidth - x) * s,
                    offsetY + (input.top * mImageHeight - y) * s,
                    offsetX + (input.right * mImageWidth - x) * s,
                    offsetY + (input.bottom * mImageHeight - y) * s);
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

        private void calculateTarget(RectF highlight) {
            float width = getWidth();
            float height = getHeight();

            if (mImageWidth != SIZE_UNKNOWN) {
                float minScale = Math.min(width / mImageWidth, height / mImageHeight);
                float scale = Utils.clamp(SELECTION_RATIO * Math.min(
                        width / (highlight.width() * mImageWidth),
                        height / (highlight.height() * mImageHeight)), minScale, MAX_SCALE);
                float centerX = mImageWidth * (highlight.left + highlight.right) * 0.5f;
                float centerY = mImageHeight * (highlight.top + highlight.bottom) * 0.5f;

                if (mImageWidth * scale > width) {
                    float limitX = width * 0.5f / scale;
                    centerX = (highlight.left + highlight.right) * mImageWidth / 2.0f;
                    centerX = Utils.clamp(centerX, limitX, mImageWidth - limitX);
                } else {
                    centerX = mImageWidth / 2.0f;
                }
                if (mImageHeight * scale > height) {
                    float limitY = height * 0.5f / scale;
                    centerY = (highlight.top + highlight.bottom) * mImageHeight / 2.0f;
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

    private class HighlightRectangle extends GLView {
        private RectF mHighlightRect = new RectF(0.25f, 0.25f, 0.75f, 0.75f);
        private RectF mTempRect = new RectF();
        private PointF mTempPoint = new PointF();

        private int mMovingEdges = 0;
        private float mReferenceX;
        private float mReferenceY;
        private GestureDetector mGestureDetector;

        public HighlightRectangle() {

            mGestureDetector = new GestureDetector(mContext, new GestureDetector.SimpleOnGestureListener() {

                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    mIsMoveEdges = false;

                    if (mTouchEdges != ONTOUCH_NOTHING) {
                        float rectLeft = mHighlightRectangle.mHighlightRect.left;
                        float rectRight = mHighlightRectangle.mHighlightRect.right;
                        float rectTop = mHighlightRectangle.mHighlightRect.top;
                        float rectBottom = mHighlightRectangle.mHighlightRect.bottom;
                        float moveLength;
                        float minWith = MIN_SELECTION_LENGTH / mImageWidth;
                        float minHeight = MIN_SELECTION_LENGTH / mImageHeight;
                        RectF outRect = new RectF();
                        mAnimation.mapRect(mHighlightRectangle.mHighlightRect, outRect);
                        switch (mTouchEdges) {
                            case ONTOUCH_LEFT: {
                                if (e.getX() < outRect.left) {
                                    moveLength = -1f / (float) mImageWidth;
                                } else if (e.getX() > outRect.left + outRect.width()) {
                                    moveLength = 1f / (float) mImageWidth;
                                } else {
                                    return false;
                                }
                                rectLeft = Utils.clamp(rectLeft + moveLength, 0, rectRight - minWith);
                            }
                            break;

                            case ONTOUCH_RIGHT: {
                                if (e.getX() < outRect.left) {
                                    moveLength = -1f / (float) mImageWidth;
                                } else if (e.getX() > outRect.left + outRect.width()) {
                                    moveLength = 1f / (float) mImageWidth;
                                } else {
                                    return false;
                                }
                                rectRight = Utils.clamp(rectRight + moveLength, rectLeft + minWith, 1);
                            }
                            break;

                            case ONTOUCH_TOP: {
                                if (e.getY() < outRect.top) {
                                    moveLength = -1f / (float) mImageHeight;
                                } else if (e.getY() > outRect.top + outRect.height()) {
                                    moveLength = 1f / (float) mImageHeight;
                                } else {
                                    return false;
                                }
                                rectTop = Utils.clamp(rectTop + moveLength, 0, rectBottom - minHeight);
                            }
                            break;

                            case ONTOUCH_BOTTOM: {
                                if (e.getY() < outRect.top) {
                                    moveLength = -1f / (float) mImageHeight;
                                } else if (e.getY() > outRect.top + outRect.height()) {
                                    moveLength = 1f / (float) mImageHeight;
                                } else {
                                    return false;
                                }
                                rectBottom = Utils.clamp(rectBottom + moveLength, rectTop + minHeight, 1);
                            }
                            break;
                        }
                        if (Math.round((rectBottom - rectTop) * mImageHeight) < MIN_SELECTION_LENGTH
                                || Math.round((rectRight - rectLeft) * mImageWidth) < MIN_SELECTION_LENGTH) {
                            return false;
                        }
                        mHighlightRectangle.mHighlightRect.set(rectLeft, rectTop, rectRight, rectBottom);
                        updateFrame();
                        updateCropSizeText();
                        return false;
                    }

                    return false;
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    mIsRotateAction = true;
                    return super.onDoubleTap(e);
                }

                public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                    if ((mMovingEdges & MOVE_RIGHT) == 0 && (mMovingEdges & MOVE_LEFT) == 0
                            && (mMovingEdges & MOVE_TOP) == 0 && (mMovingEdges & MOVE_BOTTOM) == 0) {
                        float downX = e1.getX();
                        float downY = e1.getY();
                        float moveX = e2.getX();
                        float moveY = e2.getY();

                        float yLength = Math.abs(moveY - downY);
                        float xLength = Math.abs(moveX - downX);
                        RectF outRect = new RectF();
                        mAnimation.mapRect(mHighlightRectangle.mHighlightRect, outRect);
                        if (yLength / xLength >= Math.tan(35 * Math.PI / 180)) {
                            if (Math.sqrt(yLength * yLength + xLength * xLength) >= 300f) {
                                if (downX < outRect.left && moveX < outRect.left) {
                                    mTouchEdges = ONTOUCH_LEFT;
                                } else if (downX > outRect.left + outRect.width() && moveX > outRect.left + outRect.width()) {
                                    mTouchEdges = ONTOUCH_RIGHT;
                                }
                            }
                        } else {
                            if (Math.sqrt(yLength * yLength + xLength * xLength) >= 300f) {
                                if (downY > outRect.top + outRect.width() && moveY > outRect.top + outRect.width()) {
                                    mTouchEdges = ONTOUCH_BOTTOM;
                                } else if (downY < outRect.top && moveY < outRect.top) {
                                    mTouchEdges = ONTOUCH_TOP;
                                }
                            }
                        }
                    }
                    return false;
                }
            });
        }

        public void setInitRectangle() {
            float targetRatio = mAspectRatio == UNSPECIFIED
                    ? 1f
                    : mAspectRatio * mImageHeight / mImageWidth;
            float w = mImageWidth == 1 ? 0.5f : SELECTION_RATIO / 2f;
            float h = mImageHeight == 1 ? 0.5f : SELECTION_RATIO / 2f;
            if (targetRatio > 1) {
                h = w / targetRatio;
            } else {
                w = h * targetRatio;
            }
            mHighlightRect.set(0.5f - w, 0.5f - h, 0.5f + w, 0.5f + h);
            //updateFrame();
            updateCropSizeText();
        }

        public void setRectangle(RectF faceRect) {
            mHighlightRect.set(faceRect);
            mAnimation.startParkingAnimation(faceRect);
            invalidate();
        }

        private float mathRoundValue(float value1, float value2, float scale) {
            float diff;
            diff = value1 - value2;
            diff = Math.round(diff * scale);
            if (Math.abs(diff) > 0f) {
                diff = diff / scale;
            }
            return diff;
        }

        private void moveEdges(MotionEvent event) {
            float scale = mAnimation.getScale();
            float dx = (event.getX() - mReferenceX) / scale / mImageWidth;
            float dy = (event.getY() - mReferenceY) / scale / mImageHeight;
            mReferenceX = event.getX();
            mReferenceY = event.getY();
            RectF r = mHighlightRect;

            if ((mMovingEdges & MOVE_BLOCK) != 0) {
                mIsMoveEdges = true;
                dx = Utils.clamp(dx, -r.left, 1 - r.right);
                dy = Utils.clamp(dy, -r.top, 1 - r.bottom);
                r.offset(dx, dy);
                mTouchEdges = ONTOUCH_NOTHING;
            } else {
                mIsMoveEdges = true;
                PointF point = mTempPoint;
                point.set(mReferenceX, mReferenceY);
                mAnimation.inverseMapPoint(point);
                float left = r.left + MIN_SELECTION_LENGTH / mImageWidth;
                float right = r.right - MIN_SELECTION_LENGTH / mImageWidth;
                float top = r.top + MIN_SELECTION_LENGTH / mImageHeight;
                float bottom = r.bottom - MIN_SELECTION_LENGTH / mImageHeight;
                float diff;
                if ((mMovingEdges & MOVE_RIGHT) != 0) {
                    float tempRight = Utils.clamp(point.x, left, 1f);
                    if (tempRight != left) {
                        diff = mathRoundValue(tempRight, right, mImageWidth);
                        if (diff != 0) {
                            r.right = Utils.clamp(r.right + diff, left, 1f);
                        }
                    } else {
                        r.right = tempRight;
                    }
                    mTouchEdges = ONTOUCH_NOTHING;
                }
                if ((mMovingEdges & MOVE_LEFT) != 0) {
                    float tempLeft = Utils.clamp(point.x, 0, right);
                    if (tempLeft != right) {
                        diff = mathRoundValue(tempLeft, left, mImageWidth);
                        ;
                        if (diff != 0) {
                            r.left = Utils.clamp(r.left + diff, 0, right);
                        }
                    } else {
                        r.left = tempLeft;
                    }

                    mTouchEdges = ONTOUCH_NOTHING;
                }
                if ((mMovingEdges & MOVE_TOP) != 0) {
                    float tempTop = Utils.clamp(point.y, 0, bottom);
                    if (tempTop != bottom) {
                        diff = mathRoundValue(tempTop, top, mImageHeight);
                        if (diff != 0) {
                            r.top = Utils.clamp(r.top + diff, 0, bottom);
                        }
                    } else {
                        r.top = tempTop;
                    }

                    mTouchEdges = ONTOUCH_NOTHING;
                }
                if ((mMovingEdges & MOVE_BOTTOM) != 0) {
                    float tempBottom = Utils.clamp(point.y, top, 1f);
                    if (tempBottom != top) {
                        diff = mathRoundValue(tempBottom, bottom, mImageHeight);
                        if (diff != 0) {
                            r.bottom = Utils.clamp(r.bottom + diff, top, 1f);
                        }
                    } else {
                        r.bottom = tempBottom;
                    }
                    mTouchEdges = ONTOUCH_NOTHING;
                }
                if (mAspectRatio != UNSPECIFIED) {
                    float targetRatio = mAspectRatio * mImageHeight / mImageWidth;
                    if (r.width() / r.height() > targetRatio) {
                        float height = r.width() / targetRatio;
                        if ((mMovingEdges & MOVE_BOTTOM) != 0) {
                            r.bottom = Utils.clamp(r.top + height, top, 1f);
                        } else if ((mMovingEdges & MOVE_TOP) != 0) {
                            r.top = Utils.clamp(r.bottom - height, 0, bottom);
                        }
                    } else {
                        float width = r.height() * targetRatio;
                        if ((mMovingEdges & MOVE_LEFT) != 0) {
                            r.left = Utils.clamp(r.right - width, 0, right);
                        } else if ((mMovingEdges & MOVE_RIGHT) != 0) {
                            r.right = Utils.clamp(r.left + width, left, 1f);
                        }
                    }
                    if (r.width() / r.height() > targetRatio) {
                        float width = r.height() * targetRatio;
                        if ((mMovingEdges & MOVE_LEFT) != 0) {
                            r.left = Utils.clamp(r.right - width, 0, right);
                        } else if ((mMovingEdges & MOVE_RIGHT) != 0) {
                            r.right = Utils.clamp(r.left + width, left, 1f);
                        }
                    } else {
                        float height = r.width() / targetRatio;
                        if ((mMovingEdges & MOVE_BOTTOM) != 0) {
                            r.bottom = Utils.clamp(r.top + height, top, 1f);
                        } else if ((mMovingEdges & MOVE_TOP) != 0) {
                            r.top = Utils.clamp(r.bottom - height, 0, bottom);
                        }
                    }
                }

                updateFrame();
                updateCropSizeText();
            }
            invalidate();
        }

        public void updateFrame() {
            RectF rect = mAnimation.mapRect(mHighlightRect, mTempRect);
            if (rect.width() <= CROP_FRAME_MINSIZE || rect.height() <= CROP_FRAME_MINSIZE) {
                if (mMinSizeFrame == null) {
                    mMinSizeFrame = new NinePatchTexture(mContext, R.drawable.crop_frame_small);
                }
                mCropFrame = mMinSizeFrame;
            } else {
                mCropFrame = mNormalFrame;
            }
        }

        private void setMovingEdges(MotionEvent event) {
            RectF r = mAnimation.mapRect(mHighlightRect, mTempRect);
            float x = event.getX();
            float y = event.getY();

            int moveFrameTolerance = TOUCH_TOLERANCE;
            if (r.width() - moveFrameTolerance * 2 <= moveFrameTolerance || r.height() - moveFrameTolerance * 2 <= moveFrameTolerance) {
                moveFrameTolerance = 0;
            }

            if (x > r.left + moveFrameTolerance && x < r.right - moveFrameTolerance
                    && y > r.top + moveFrameTolerance && y < r.bottom - moveFrameTolerance) {
                mMovingEdges = MOVE_BLOCK;
                mTouchEdges = ONTOUCH_NOTHING;
                return;
            }

            boolean inVerticalRange = (r.top - TOUCH_TOLERANCE) <= y
                    && y <= (r.bottom + TOUCH_TOLERANCE);
            boolean inHorizontalRange = (r.left - TOUCH_TOLERANCE) <= x
                    && x <= (r.right + TOUCH_TOLERANCE);

            if (inVerticalRange) {
                boolean left = Math.abs(x - r.left) <= TOUCH_TOLERANCE;
                boolean right = Math.abs(x - r.right) <= TOUCH_TOLERANCE;
                if (left && right) {
                    left = Math.abs(x - r.left) < Math.abs(x - r.right);
                    right = !left;
                }
                if (left) mMovingEdges |= MOVE_LEFT;
                if (right) mMovingEdges |= MOVE_RIGHT;
                if (mAspectRatio != UNSPECIFIED && inHorizontalRange) {
                    mMovingEdges |= (y >
                            (r.top + r.bottom) / 2) ? MOVE_BOTTOM : MOVE_TOP;
                }
            }
            if (inHorizontalRange) {
                boolean top = Math.abs(y - r.top) <= TOUCH_TOLERANCE;
                boolean bottom = Math.abs(y - r.bottom) <= TOUCH_TOLERANCE;
                if (top && bottom) {
                    top = Math.abs(y - r.top) < Math.abs(y - r.bottom);
                    bottom = !top;
                }
                if (top) mMovingEdges |= MOVE_TOP;
                if (bottom) mMovingEdges |= MOVE_BOTTOM;
                if (mAspectRatio != UNSPECIFIED && inVerticalRange) {
                    mMovingEdges |= (x >
                            (r.left + r.right) / 2) ? MOVE_RIGHT : MOVE_LEFT;
                }
            }
        }

        @Override
        protected boolean onTouch(MotionEvent event) {
            // multipoint event is not allowed
            if (event.getPointerCount() > 1) {
                mMultiPoint = true;
                if (event.getPointerCount() == 2) {
                    mTwoFinger = true;
                }
            }

            mGestureDetector.onTouchEvent(event);
            mScaleDetector.onTouchEvent(event);

            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN: {
                    if (!mMultiPoint) {
                        mReferenceX = event.getX();
                        mReferenceY = event.getY();
                        setMovingEdges(event);
                        invalidate();
                    }
                    return true;
                }
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (mTwoFinger && event.getPointerCount() > 1) {
                        mFirstFingerStartX = event.getX(0);
                        mFirstFingerStartY = event.getY(0);
                        mSecondFingerStartX = event.getX(1);
                        mSecondFingerStartY = event.getY(1);
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!mMultiPoint) {
                        moveEdges(event);
                    } else {
                        if (mTwoFinger && event.getPointerCount() > 1) {
                            movePicturePosition(event);
                            invalidate();
                        }
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP: {
                    if (mMultiPoint) {
                        if (event.getPointerCount() <= 1) {
                            mMultiPoint = false;
                            mTwoFinger = false;
                            mMovingEdges = 0;
                            mAnimation.startRollbackingAnimation(mHighlightRect);
                            invalidate();
                        }
                    } else {
                        mIsMoveEdges = false;
                        mMovingEdges = 0;
                        if (mTouchEdges == ONTOUCH_NOTHING) {
                            float left = mHighlightRect.left * mImageWidth;
                            float integerLeft = Math.round(left);
                            float diffLeft = integerLeft - left;

                            float top = mHighlightRect.top * mImageHeight;
                            float integerTop = Math.round(top);
                            float diffTop = integerTop - top;
                            mHighlightRect.offset(diffLeft / mImageWidth, diffTop / mImageHeight);
                        }
                        if (!mIsRotateAction) {
                            mAnimation.startParkingAnimation(mHighlightRect);
                        }
                        mIsRotateAction = false;
                        invalidate();
                    }
                    return true;
                }
            }
            return true;
        }

        @Override
        protected void renderBackground(GLCanvas canvas) {
            RectF r = mAnimation.mapRect(mHighlightRect, mTempRect);
            //r.set(r.left - 1, r.top - 1, r.right + 1, r.bottom + 1);
            mCurrentHighlightRect.set(r.left, r.top, r.right, r.bottom);
            drawHighlightRectangle(canvas, r);
            int inset = mContext.getResources().getDimensionPixelSize(R.dimen.cropimage_crop_frame_padding);
            if (r.width() <= CROP_FRAME_MINSIZE || r.height() <= CROP_FRAME_MINSIZE) {
                inset = 2;
            }
            mCropFrame.draw(canvas, Math.round(r.left) - inset, Math.round(r.top) - inset, Math.round(r.width()) + inset * 2, Math.round(r.height()) + inset * 2);

            //drawCropFrame(canvas, r);
            /* float centerY = (r.top + r.bottom) / 2;
            float centerX = (r.left + r.right) / 2;
            boolean notMoving = mMovingEdges == 0;
            if ((mMovingEdges & MOVE_RIGHT) != 0 || notMoving) {
                mArrow.draw(canvas,
                        Math.round(r.right - mArrow.getWidth() / 2),
                        Math.round(centerY - mArrow.getHeight() / 2));
            }
            if ((mMovingEdges & MOVE_LEFT) != 0 || notMoving) {
                mArrow.draw(canvas,
                        Math.round(r.left - mArrow.getWidth() / 2),
                        Math.round(centerY - mArrow.getHeight() / 2));
            }
            if ((mMovingEdges & MOVE_TOP) != 0 || notMoving) {
                mArrow.draw(canvas,
                        Math.round(centerX - mArrow.getWidth() / 2),
                        Math.round(r.top - mArrow.getHeight() / 2));
            }
            if ((mMovingEdges & MOVE_BOTTOM) != 0 || notMoving) {
                mArrow.draw(canvas,
                        Math.round(centerX - mArrow.getWidth() / 2),
                        Math.round(r.bottom - mArrow.getHeight() / 2));
            }*/
        }

        private void drawHighlightRectangle(GLCanvas canvas, RectF r) {
            //TODO
            GL11 gl = canvas.getGLInstance();
            gl.glLineWidth(1.0f);
            gl.glEnable(GL11.GL_LINE_SMOOTH);

            gl.glEnable(GL11.GL_STENCIL_TEST);
            gl.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            gl.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
            gl.glStencilFunc(GL11.GL_ALWAYS, 1, 1);

            if (mSpotlightRatioX == 0 || mSpotlightRatioY == 0) {
                canvas.fillRect(r.left, r.top, r.width(), r.height(), Color.TRANSPARENT);
                //canvas.drawRect(r.left, r.top, r.width(), r.height(), mPaint);
            } else {
                float sx = r.width() * mSpotlightRatioX;
                float sy = r.height() * mSpotlightRatioY;
                float cx = r.centerX();
                float cy = r.centerY();

                canvas.fillRect(cx - sx / 2, cy - sy / 2, sx, sy, Color.TRANSPARENT);
                canvas.drawRect(cx - sx / 2, cy - sy / 2, sx, sy, mPaint);
                canvas.drawRect(r.left, r.top, r.width(), r.height(), mPaint);

                gl.glStencilFunc(GL11.GL_NOTEQUAL, 1, 1);
                gl.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);

                canvas.drawRect(cx - sy / 2, cy - sx / 2, sy, sx, mPaint);
                canvas.fillRect(cx - sy / 2, cy - sx / 2, sy, sx, Color.TRANSPARENT);
                canvas.fillRect(r.left, r.top, r.width(), r.height(), 0x80000000);
            }

            gl.glStencilFunc(GL11.GL_NOTEQUAL, 1, 1);
            gl.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);

            RectF out = mTempOutRect;
            if (mMultiPoint) {
                mapHighlightRectOnScaling(out);
            } else {
                mAnimation.mapRect(new RectF(0, 0, 1, 1), out);
            }
            int offsetX = (getWidth() - Math.round(out.width())) / 2;
            int offsetY = (getHeight() - Math.round(out.height())) / 2;
            canvas.fillRect(Math.max(0, offsetX), Math.max(0, offsetY), Math.min(Math.round(out.width()), getWidth()), Math.min(Math.round(out.height()), getHeight()), 0x50000000);

            gl.glDisable(GL11.GL_STENCIL_TEST);
        }

    }

    private class DetectFaceTask extends Thread {
        private final FaceDetector.Face[] mFaces = new FaceDetector.Face[MAX_FACE_COUNT];
        private final Bitmap mFaceBitmap;
        private int mFaceCount;

        public DetectFaceTask(Bitmap bitmap) {
            mFaceBitmap = bitmap;
            setName("face-detect");
        }

        @Override
        public void run() {
            Bitmap bitmap = mFaceBitmap;
            FaceDetector detector = new FaceDetector(
                    bitmap.getWidth(), bitmap.getHeight(), MAX_FACE_COUNT);
            mFaceCount = detector.findFaces(bitmap, mFaces);
            mMainHandler.sendMessage(
                    mMainHandler.obtainMessage(MSG_UPDATE_FACES, this));
        }

        private RectF getFaceRect(FaceDetector.Face face) {
            PointF point = new PointF();
            face.getMidPoint(point);

            int width = mFaceBitmap.getWidth();
            int height = mFaceBitmap.getHeight();
            float rx = face.eyesDistance() * FACE_EYE_RATIO;
            float ry = rx;
            float aspect = mAspectRatio;
            if (aspect != UNSPECIFIED) {
                if (aspect > 1) {
                    rx = ry * aspect;
                } else {
                    ry = rx / aspect;
                }
            }

            RectF r = new RectF(
                    point.x - rx, point.y - ry, point.x + rx, point.y + ry);
            r.intersect(0, 0, width, height);

            if (aspect != UNSPECIFIED) {
                if (r.width() / r.height() > aspect) {
                    float w = r.height() * aspect;
                    r.left = (r.left + r.right - w) * 0.5f;
                    r.right = r.left + w;
                } else {
                    float h = r.width() / aspect;
                    r.top = (r.top + r.bottom - h) * 0.5f;
                    r.bottom = r.top + h;
                }
            }

            r.left /= width;
            r.right /= width;
            r.top /= height;
            r.bottom /= height;
            return r;
        }

        public void updateFaces() {
            /*if (mFaceCount > 1) {
                for (int i = 0, n = mFaceCount; i < n; ++i) {
                    mFaceDetectionView.addFace(getFaceRect(mFaces[i]));
                }
                mFaceDetectionView.setVisibility(GLView.VISIBLE);
                //SlideNotice.makeNotice(mActivity.getAndroidContext(), mActivity.getAndroidContext().getResources().getString(R.string.multiface_crop_help), SlideNotice.NOTICE_TYPE_SUCCESS, SlideNotice.LENGTH_SHORT).show();
            } else if (mFaceCount == 1) {
                mFaceDetectionView.setVisibility(GLView.INVISIBLE);
                mHighlightRectangle.setRectangle(getFaceRect(mFaces[0]));
                mHighlightRectangle.setVisibility(GLView.VISIBLE);
            } else if (mFaceCount == 0) {
                mHighlightRectangle.setInitRectangle();
                mHighlightRectangle.setVisibility(GLView.VISIBLE);
            }*/

            mHighlightRectangle.setInitRectangle();
            mHighlightRectangle.setVisibility(GLView.VISIBLE);
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
        mHighlightRectangle.updateFrame();
    }

    public void detectFaces(Bitmap bitmap) {
        int rotation = mImageRotation;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scale = (float) Math.sqrt((float) FACE_PIXEL_COUNT / (width * height));

        // faceBitmap is a correctly rotated bitmap, as viewed by a user.
        Bitmap faceBitmap;
        if (((rotation / 90) & 1) == 0) {
            int w = (Math.round(width * scale) & ~1); // must be even
            int h = Math.round(height * scale);
            faceBitmap = Bitmap.createBitmap(w, h, Config.RGB_565);
            Canvas canvas = new Canvas(faceBitmap);
            canvas.rotate(rotation, w / 2, h / 2);
            canvas.scale((float) w / width, (float) h / height);
            canvas.drawBitmap(bitmap, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
        } else {
            int w = (Math.round(height * scale) & ~1); // must be even
            int h = Math.round(width * scale);
            faceBitmap = Bitmap.createBitmap(w, h, Config.RGB_565);
            Canvas canvas = new Canvas(faceBitmap);
            canvas.translate(w / 2, h / 2);
            canvas.rotate(rotation);
            canvas.translate(-h / 2, -w / 2);
            canvas.scale((float) w / height, (float) h / width);
            canvas.drawBitmap(bitmap, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
        }
        new DetectFaceTask(faceBitmap).start();
    }

    public void initializeHighlightRectangle() {
        mHighlightRectangle.setInitRectangle();
        mHighlightRectangle.setVisibility(GLView.VISIBLE);
    }

    public void resume() {
        mImageView.prepareTextures();
    }

    public void pause() {
        mImageView.freeTextures();
    }

    private void updateCropSizeText() {
        if (mContext instanceof CropImageActivity) {
            ((CropImageActivity) mContext).updateCropSize(getCropSizeString());
        }
    }

    public void setFlippable(boolean flippable) {
        mFlippable = flippable;
    }

    public String getCropSizeString() {
        RectF rect = mHighlightRectangle.mHighlightRect;
        int l = Math.round(rect.left * mImageWidth);
        int t = Math.round(rect.top * mImageHeight);
        int r = Math.round(rect.right * mImageWidth);
        int b = Math.round(rect.bottom * mImageHeight);
        int width = r - l;
        int height = b - t;

        if (width < MIN_SELECTION_LENGTH)
            width = (int) MIN_SELECTION_LENGTH;
        if (height < MIN_SELECTION_LENGTH)
            height = (int) MIN_SELECTION_LENGTH;
        return width + " x " + height;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        private boolean mOnScale = false;

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scale = detector.getScaleFactor();
            if (canZoomable()) {
                mOnScale = true;
                updateHighlightRectangle((float) (scale), mHighlightRectangle.mHighlightRect);
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
                updateHighlightRectangle(mTempScale);
                updateAnimationInfo(mCurrMultiCenterX, mCurrMultiCenterY, mTempScale);
            }
            invalidate();
            super.onScaleEnd(detector);
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

    // update the highlight when scaling by two-fingers
    public void updateHighlightRectangle(float scale, RectF highlight) {
        float width = getWidth();
        float height = getHeight();
        float minScale = Math.min(width / mImageWidth, height / mImageHeight);
        mTempScale = Utils.clamp(scale * mTotalScale, minScale, MAX_SCALE);
        float limitX = width * 0.5f / mTempScale;
        float limitY = height * 0.5f / mTempScale;

        // if zoom out, update the center coordinate of picture and highlight frame
        RectF r = mHighlightRectangle.mHighlightRect;
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

            // update the highlight frame
            mAnimation.mapRect(r, tempRect);
            float offsetX = getWidth() * 0.5f;
            float offsetY = getHeight() * 0.5f;

            float x = mCurrMultiCenterX;
            float y = mCurrMultiCenterY;
            float s = mAnimation.getScale();

            int imageTop = Math.round(height * 0.5f - mCurrMultiCenterY * mTempScale);
            int imageBottom = Math.round(height * 0.5f + (mImageHeight - mCurrMultiCenterY) * mTempScale);
            int imageLeft = Math.round(width * 0.5f - mCurrMultiCenterX * mTempScale);
            int imageRight = Math.round(width * 0.5f + (mImageWidth - mCurrMultiCenterX) * mTempScale);

            int left = Math.round(tempRect.left);
            int top = Math.round(tempRect.top);
            int right = Math.round(tempRect.right);
            int bottom = Math.round(tempRect.bottom);

            int oldWidth = right - left;
            int oldHeight = bottom - top;

            if (left < imageLeft) {
                right = right + (imageLeft - left);
                if (right > imageRight) {
                    right = imageRight;
                }
                left = imageLeft;
                if (right - left <= 0) {
                    right = left + 1;
                }
            }

            if (right > imageRight) {
                left = left - (right - imageRight);
                if (left < imageLeft) {
                    left = imageLeft;
                }
                right = imageRight;
                if (right - left <= 0) {
                    left = right - 1;
                }
            }

            if (top < imageTop) {
                bottom = bottom + (imageTop - top);
                if (bottom > imageBottom) {
                    bottom = imageBottom;
                }
                top = imageTop;
                if (bottom - top <= 0) {
                    bottom = top + 1;
                }
            }

            if (bottom > imageBottom) {
                top = top - (bottom - imageBottom);
                if (top < imageTop) {
                    top = imageTop;
                }
                bottom = imageBottom;
                if (bottom - top <= 0) {
                    top = bottom - 1;
                }
            }

            float delta = 0;

            boolean widthChanged = ((right - left) - oldWidth) != 0 ? true : false;
            boolean heightChanged = ((bottom - top) - oldHeight) != 0 ? true : false;

            float le = tempRect.left;
            float to = tempRect.top;
            float ri = tempRect.right;
            float bo = tempRect.bottom;

            if (mAspectRatio != UNSPECIFIED) {
                if (widthChanged && !heightChanged) {
                    delta = oldWidth - (ri - le);
                    delta = delta / mAspectRatio;

                    to = to + delta / 2.0f;
                    bo = bo - (delta - delta / 2.0f);
                }

                if (!widthChanged && heightChanged) {
                    delta = oldHeight - (bo - to);
                    delta = delta * mAspectRatio;

                    le = le + delta / 2.0f;
                    ri = ri - (delta - delta / 2.0f);
                }

                if (widthChanged && heightChanged) {
                    //never be run
                }

            }

            r.left = Utils.clamp(((left - offsetX) / s + x) / mImageWidth, 0, 1.0f);
            r.right = Utils.clamp(((right - offsetX) / s + x) / mImageWidth, 0, 1.0f);
            r.top = Utils.clamp(((top - offsetY) / s + y) / mImageHeight, 0, 1.0f);
            r.bottom = Utils.clamp(((bottom - offsetY) / s + y) / mImageHeight, 0, 1.0f);

        }

        mAnimation.mCurrentX = mCurrMultiCenterX;
        mAnimation.mCurrentY = mCurrMultiCenterY;
        mCurrMultiScale = mTempScale;

        mAnimation.mapRect(r, tempRect);
        updateCropSizeText(mCurrMultiScale, tempRect.width(), tempRect.height());

    }

    //update the highlight when scale end by two-fingers
    private void updateHighlightRectangle(float scale) {
        float minScale = Math.min((float) getWidth() / mImageWidth, (float) getHeight()
                / mImageHeight);
        scale = Utils.clamp(scale, minScale, MAX_SCALE);
        RectF r = mHighlightRectangle.mHighlightRect;
        RectF tempRect = new RectF();
        mAnimation.mapRect(r, tempRect);

        float offsetX = getWidth() * 0.5f;
        float offsetY = getHeight() * 0.5f;

        float x = mAnimation.mCurrentX;
        float y = mAnimation.mCurrentY;

        r.left = Utils.clamp(((tempRect.left - offsetX) / (scale) + x) / mImageWidth, 0, 1.0f);
        r.right = Utils.clamp(((tempRect.right - offsetX) / (scale) + x) / mImageWidth, 0, 1.0f);
        r.top = Utils.clamp(((tempRect.top - offsetY) / (scale) + y) / mImageHeight, 0, 1.0f);
        r.bottom = Utils.clamp(((tempRect.bottom - offsetY) / (scale) + y) / mImageHeight, 0, 1.0f);

        updateCropSizeText();
    }

    //update the position of the highlight rectangle and picture when moving by two-fingers
    private void updatePosition(float deltaX, float deltaY) {
        float currScale = mAnimation.mCurrentScale;
        RectF r = mHighlightRectangle.mHighlightRect;
        RectF tempRect = new RectF();
        mAnimation.mapRect(r, tempRect);
        float offsetX = getWidth() * 0.5f;
        float offsetY = getHeight() * 0.5f;

        float limitX = Math.round(getWidth() * 0.5f / currScale);
        float limitY = Math.round(getHeight() * 0.5f / currScale);
        float width = getWidth();
        float height = getHeight();

        //update the position of picture
        if (mImageWidth * currScale > width) {
            mCurrMultiCenterX = Utils.clamp(mCurrMultiCenterX - deltaX / currScale, limitX, mImageWidth - limitX);
        } else {
            mCurrMultiCenterX = mImageWidth / 2.0f;
        }

        if (mImageHeight * currScale > height) {
            mCurrMultiCenterY = Utils.clamp(mCurrMultiCenterY - deltaY / currScale, limitY, mImageHeight - limitY);
        } else {
            mCurrMultiCenterY = mImageHeight / 2.0f;
        }

        //update the highlight rectangle of picture
        r.left = Utils.clamp(((tempRect.left - offsetX) / (currScale) + mCurrMultiCenterX) / mImageWidth, 0, 1.0f);
        r.right = Utils.clamp(((tempRect.right - offsetX) / (currScale) + mCurrMultiCenterX) / mImageWidth, 0, 1.0f);
        r.top = Utils.clamp(((tempRect.top - offsetY) / (currScale) + mCurrMultiCenterY) / mImageHeight, 0, 1.0f);
        r.bottom = Utils.clamp(((tempRect.bottom - offsetY) / (currScale) + mCurrMultiCenterY) / mImageHeight, 0, 1.0f);

        updateAnimationInfo(mCurrMultiCenterX, mCurrMultiCenterY, currScale);
    }

    private boolean canZoomable() {
        float width = getWidth();
        float height = getHeight();
        if (mImageWidth * MAX_SCALE < (width - 1) && mImageHeight * MAX_SCALE < (height - 1)) {
            return false;
        }
        return true;
    }

    //update the text of crop_size when scaling by two-fingers
    private void updateCropSizeText(float scale, float rectWidth, float rectHeight) {
        int width = Math.round(rectWidth / scale);
        int height = Math.round(rectHeight / scale);
        if (width < MIN_SELECTION_LENGTH)
            width = (int) MIN_SELECTION_LENGTH;
        if (height < MIN_SELECTION_LENGTH)
            height = (int) MIN_SELECTION_LENGTH;
        if (mContext instanceof CropImageActivity) {
            ((CropImageActivity) mContext).updateCropSize(width + " x " + height);
        }
    }

    // move the position of picture by two-fingers
    private void movePicturePosition(MotionEvent event) {
        float firDeltaX = event.getX(0) - mFirstFingerStartX;
        float firDeltaY = event.getY(0) - mFirstFingerStartY;
        float secDeltaX = event.getX(1) - mSecondFingerStartX;
        float secDeltaY = event.getY(1) - mSecondFingerStartY;
        float deltaX = (firDeltaX + secDeltaX) / 2.0f;
        float deltaY = (firDeltaY + secDeltaY) / 2.0f;
        updatePosition(deltaX, deltaY);
        mFirstFingerStartX = event.getX(0);
        mFirstFingerStartY = event.getY(0);
        mSecondFingerStartX = event.getX(1);
        mSecondFingerStartY = event.getY(1);
    }

    public int getmImageWidth() {
        return mImageWidth;
    }

    public int getmImageHeight() {
        return mImageHeight;
    }

    public void setCustomizeCropSize(int width, int height) {
        mCustomizeCropWidth = width;
        mCustomizeCropHeight = height;
        //calculate the new highlight
        float ratio = (float) width / height;
        mAspectRatio = ratio;
        if (mAspectRatio != UNSPECIFIED) {
            float rectFW = (float) width / mImageWidth;
            float rectFH = (float) height / mImageHeight;

            float srcCenterX = mHighlightRectangle.mHighlightRect.centerX();
            float srcCenterY = mHighlightRectangle.mHighlightRect.centerY();
            RectF srcRect = mHighlightRectangle.mHighlightRect;

            float newCenterX = srcCenterX;
            float newCenterY = srcCenterY;

            //compute the center of the highlight
            if (rectFW / 2.0f > srcCenterX) {
                newCenterX = rectFW / 2.0f;
            }
            if (rectFW / 2.0f > 1.0f - srcCenterX) {
                newCenterX = 1.0f - rectFW / 2.0f;
            }

            if (rectFH / 2.0f > srcCenterY) {
                newCenterY = rectFH / 2.0f;
            }
            if (rectFH / 2.0f > 1.0f - srcCenterY) {
                newCenterY = 1.0f - rectFH / 2.0f;
            }

            srcRect.set(newCenterX - rectFW / 2.0f, newCenterY - rectFH / 2.0f,
                    newCenterX + rectFW / 2.0f, newCenterY + rectFH / 2.0f);
            mHighlightRectangle.updateFrame();
            updateCropSizeText();
            mAnimation.startParkingAnimation(srcRect);
        }
    }


    public void rotateCropFrame() {
        /*if (mAspectRatio != 1) {
            RectF highLightRect = mHighlightRectangle.mHighlightRect;
            mTempRect.set(highLightRect);
            mTempRect.inset(highLightRect.width() / 4f, highLightRect.height() / 4f);
                float centerX = highLightRect.centerX();
                float centerY = highLightRect.centerY();
                float heightBy2 = highLightRect.width() * mImageWidth / (mImageHeight * 2f);
                float widthBy2 = highLightRect.height() * mImageHeight / (mImageWidth * 2f);
                float l, r, t, b;
                l = Math.max(centerX - widthBy2, 0);
                r = Math.min(centerX + widthBy2, 1);
                t = Math.max(centerY - heightBy2, 0);
                b = Math.min(centerY + heightBy2, 1);
                
                highLightRect.set(l, t, r, b);
                mAspectRatio = 1f / mAspectRatio;
                
                float targetRatio = mAspectRatio * mImageHeight / mImageWidth;
                if (mAspectRatio != UNSPECIFIED && targetRatio != (r - l) / (b - t)) {
                    mTempRect.set(0, 0, targetRatio, 1);
                    centerX = highLightRect.centerX();
                    centerY = highLightRect.centerY();
                    Utils.fitRectFInto(mTempRect, highLightRect, highLightRect);
                    highLightRect.offset(centerX - highLightRect.centerX(), centerY - highLightRect.centerY());
                }
                updateCropSizeText(); 
            }*/

        RectF highLightRect = mHighlightRectangle.mHighlightRect;
        float centerX = highLightRect.centerX();
        float centerY = highLightRect.centerY();

        float heightBy2 = (highLightRect.width() * mImageWidth) / (mImageHeight * 2f);
        float widthBy2 = (highLightRect.height() * mImageHeight) / (mImageWidth * 2f);

        float l, r, t, b;
        l = centerX - widthBy2;
        r = centerX + widthBy2;
        t = centerY - heightBy2;
        b = centerY + heightBy2;

        float rotatedCenterX = centerX;
        float rotatedCenterY = centerY;

        if (r - l > 1.0f) {
            l = 0.0f;
            r = 1.0f;
            rotatedCenterX = 0.5f;
        } else {
            if (r > 1.0f) {
                //move left
                rotatedCenterX -= r - 1.0f;
            }

            if (l < 0.0f) {
                //move right
                rotatedCenterX -= l;
            }
        }

        if (b - t > 1.0f) {
            t = 0.0f;
            b = 1.0f;
            rotatedCenterY = 0.5f;
        } else {
            if (b > 1.0f) {
                //move up
                rotatedCenterY -= b - 1.0f;
            }

            if (t < 0.0f) {
                //move down
                rotatedCenterY -= t;
            }
        }

        l = rotatedCenterX - (r - l) / 2.0f;
        r = rotatedCenterX + (r - l) / 2.0f;
        t = rotatedCenterY - (b - t) / 2.0f;
        b = rotatedCenterY + (b - t) / 2.0f;
        highLightRect.set(l, t, r, b);
        updateCropSizeText();

        invalidate();
    }

    public RectF mapHighlightRectOnScaling(RectF output) {
        float offsetX = getWidth() * 0.5f;
        float offsetY = getHeight() * 0.5f;
        float x = mCurrMultiCenterX;
        float y = mCurrMultiCenterY;
        float s = mCurrMultiScale;

        output.set(
                offsetX + (-x) * s,
                offsetY + (-y) * s,
                offsetX + (mImageWidth - x) * s,
                offsetY + (mImageHeight - y) * s);
        return output;
    }

}