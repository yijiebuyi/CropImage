package com.img.crop;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.View;
import com.img.crop.utils.CropUtils;

public class LoadingView extends View {
    private final static int DEFAULT_SIZE = 70;
    private final static int MSG_ANIM = 100;
    private final static int DURATION = 800; //800ms
    private final static int REFRESH_INTERVAL = 20; //20ms

    private final static int FRAME_COUNT = DURATION / REFRESH_INTERVAL;
    private final static int HALF_FRAME_COUNT = FRAME_COUNT / 2;

    private int mSize = DEFAULT_SIZE;
    private int mCircleWidth = DEFAULT_SIZE / 2;
    private int mCircleHeight = DEFAULT_SIZE / 2;
    private float mRadius = DEFAULT_SIZE / 4.0f;
    private float mMinX = DEFAULT_SIZE / 4.0f;
    ;
    private float mMaxX = DEFAULT_SIZE * 3 / 4.0f;
    private float mDistance = DEFAULT_SIZE / 2;
    private Handler mHandler;
    private int mOffsetX;
    private int mOffsetY;

    private int mOrangeColor;
    private int mBlueColor;
    private int mCurrFrame;

    private Paint mPaint;

    public LoadingView(Context context) {
        this(context, 0);
    }

    public LoadingView(Context context, int size) {
        super(context);
        init(size);
    }

    public LoadingView(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.load_attr);
        int lsize = a.getDimensionPixelSize(R.styleable.load_attr_lsize, -1);
        a.recycle();

        init(lsize);
    }

    public LoadingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.load_attr);
        int lsize = a.getDimensionPixelSize(R.styleable.load_attr_lsize, -1);
        a.recycle();

        init(lsize);
    }

    @TargetApi(21)
    public LoadingView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.load_attr);
        int lsize = a.getDimensionPixelSize(R.styleable.load_attr_lsize, -1);
        a.recycle();

        init(lsize);
    }

    private void init(int size) {
        if (size <= 0) {
            mSize = CropUtils.dpToPixel(26);
        } else {
            mSize = size;
        }

        mCircleWidth = mSize / 2;
        mCircleHeight = mSize / 2;
        mRadius = mSize / 4.0f;
        mDistance = mSize / 2.0f;
        mMinX = mSize / 4.0f;
        mMaxX = (mSize * 3) / 4.0f;

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStyle(Paint.Style.FILL);

        mOrangeColor = getResources().getColor(R.color.loading_orange);
        mBlueColor = getResources().getColor(R.color.loading_blue);

        createHandler();
    }

    public void setSize(int size) {
        if (size <= 0) {
            mSize = CropUtils.dpToPixel(35);
        } else {
            mSize = size;
        }

        mCircleWidth = mSize / 2;
        mCircleHeight = mSize / 2;
        mRadius = mSize / 4.0f;
        mDistance = mSize / 2.0f;
        mMinX = mSize / 4.0f;
        mMaxX = (mSize * 3) / 4.0f;
        invalidate();
    }

    public void pause() {
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            setVisibility(GONE);
        }
    }

    public void resume() {
        mCurrFrame = 0;
        setVisibility(VISIBLE);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int horPadding = getPaddingLeft() + getPaddingRight();
        int verPadding = getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(mSize + horPadding, mSize + verPadding);
        mOffsetX = horPadding / 2;
        mOffsetY = verPadding / 2;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.translate(mOffsetX, mOffsetY);
        if (mCurrFrame <= HALF_FRAME_COUNT) {
            //first draw blue circle, then draw orange circle.
            //Blue circle moves from left to right. Orange moves from the right to the left.
            drawCircle(canvas, mMinX + mDistance * ((float) mCurrFrame / HALF_FRAME_COUNT), mBlueColor);
            drawCircle(canvas, mMaxX - mDistance * ((float) mCurrFrame / HALF_FRAME_COUNT), mOrangeColor);
        } else if (mCurrFrame > HALF_FRAME_COUNT) {
            //first draw orange circle, then draw blue circle.
            //Orange circle moves from left to right. Blue moves from the right to the left.
            drawCircle(canvas, mMinX + mDistance * ((float) (mCurrFrame - HALF_FRAME_COUNT) / HALF_FRAME_COUNT), mOrangeColor);
            drawCircle(canvas, mMaxX - mDistance * ((float) (mCurrFrame - HALF_FRAME_COUNT) / HALF_FRAME_COUNT), mBlueColor);
        }
        canvas.restore();

        if (mHandler == null) {
            createHandler();
        }
        mHandler.sendEmptyMessageDelayed(MSG_ANIM, REFRESH_INTERVAL);
    }


    public void stop() {
        if (mHandler != null) {
            mHandler.removeMessages(MSG_ANIM);
            mHandler = null;
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mHandler != null) {
            mHandler.removeMessages(MSG_ANIM);
            mHandler = null;
        }
    }

    private void drawCircle(Canvas canvas, float offsetX, int color) {
        float y = mSize / 2.0f;
        mPaint.setColor(color);
        canvas.drawCircle(offsetX, y, mRadius, mPaint);
    }

    private void createHandler() {
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MSG_ANIM) {
                    mCurrFrame++;
                    if (mCurrFrame > FRAME_COUNT) {
                        mCurrFrame = 1;
                    }
                    postInvalidate();
                }
            }
        };
    }
}
