package com.img.crop;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.img.crop.core.BitmapTileProvider;
import com.img.crop.core.CropView;
import com.img.crop.core.TileImageViewAdapter;
import com.img.crop.glsrender.gl11.BitmapScreenNail;
import com.img.crop.glsrender.gl11.GLRoot;
import com.img.crop.glsrender.gl11.GLRootView;
import com.img.crop.thdpool.Future;
import com.img.crop.thdpool.FutureListener;
import com.img.crop.thdpool.ThreadPool;
import com.img.crop.utils.BitmapUtils;
import com.img.crop.utils.CropBusiness;
import com.img.crop.utils.LocalImageRequest;
import com.img.crop.utils.SynchronizedHandler;
import com.img.crop.utils.Utils;

import java.io.File;

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
public abstract class BaseCropActivity extends FragmentActivity implements CropConstants {
    private final static String TAG = "crop";

    private static final int MAX_PIXEL_COUNT = 1000000 / 32; // according to TransactionTooLargeException

    //==========================State=========================================
    /**
     * 初始化
     */
    private static final int STATE_INIT = 0;
    /**
     * 已加载图片
     */
    private static final int STATE_LOADED = 1;
    /**
     * 保存裁剪图片中
     */
    private static final int STATE_SAVING = 2;


    //==========================Handler msg===================================
    /**
     * 加载大图
     */
    private static final int MSG_LARGE_BITMAP = 1;
    /**
     * 加载普通图片
     */
    private static final int MSG_BITMAP = 2;
    /**
     * 保存图片完成
     */
    private static final int MSG_SAVE_COMPLETE = 3;
    /**
     * 保存图片失败
     */
    private static final int MSG_SHOW_SAVE_ERROR = 4;
    /**
     * 取消对话框
     */
    private static final int MSG_CANCEL_DIALOG = 5;
    /**
     * SD卡不可用
     */
    private static final int MSG_SDCARD_NOT_AVAILABLE = 6;


    // crop aspect
    private static final int ASPECT_FREE = 0;
    private static final int ASPECT_1_1 = 1;
    private static final int ASPECT_3_2 = 2;
    private static final int ASPECT_4_3 = 3;
    private static final int ASPECT_15_9 = 4;
    private static final int ASPECT_16_9 = 5;
    private static final int ASPECT_16_10 = 6;
    private static final int ASPECT_CUSTOMIZE = 7;

    private static final int BACKUP_PIXEL_COUNT = 480000; // around 800x600

    private static final String KEY_STATE = "state";

    //==========================View / Container=================================
    /**
     * 顶部容器
     */
    protected RelativeLayout mTopContainer;
    /**
     * gl view
     */
    protected GLRootView mGLRootView;
    /**
     * 底部容器
     */
    protected RelativeLayout mBottomContainer;

    protected CropView mCropView;

    private String mCompressFormat = null;

    private int mState = STATE_INIT;
    private Handler mHandler;

    private boolean mFlippable = true;
    private boolean mConfirmOverwrite = false;

    private int mCurrentAspect;

    // We keep the following members so that we can free them

    // mBitmap is the unrotated bitmap we pass in to mCropView for detect faces.
    // mCropView is responsible for rotating it to the way that it is viewed by users.
    private Bitmap mBitmap;
    private BitmapRegionDecoder mRegionDecoder;
    private boolean mUseRegionDecoder = false;
    private BitmapScreenNail mBitmapScreenNail;

    private ProgressHUD mProgressDialog;
    private Future<BitmapRegionDecoder> mLoadTask;
    private Future<Bitmap> mLoadBitmapTask;
    private Future<Intent> mSaveTask;

    private int mCroppedWidth = 0;
    private int mCroppedHeight = 0;
    private Rect mSrcRect = new Rect();
    private Rect mDestRect = new Rect();

    private MediaItem mMediaItem;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (needFullScreen()) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        setContentView(R.layout.activity_base_crop);

        initView();
        initData();
    }

    @Override
    protected void onSaveInstanceState(Bundle saveState) {
        super.onSaveInstanceState(saveState);
        saveState.putInt(KEY_STATE, mState);
    }


    @Override
    protected void onResume() {
        super.onResume();

        mGLRootView.lockRenderThread();
        try {
            mCropView.resume();
            mGLRootView.onResume();

            switch (mState) {
                case STATE_INIT:
                    loadBitmap();
                    break;
                case STATE_SAVING:
                    saveCropBitmap();
                    break;
            }
        } finally {
            mGLRootView.unlockRenderThread();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onPause() {
        super.onPause();

        mGLRootView.onPause();
        mGLRootView.lockRenderThread();
        try {
            Future<BitmapRegionDecoder> loadTask = mLoadTask;
            if (loadTask != null && !loadTask.isDone()) {
                // load in progress, try to cancel it
                loadTask.cancel();
                loadTask.waitDone();
                dismissLoadingProgressDialog();
            }

            Future<Bitmap> loadBitmapTask = mLoadBitmapTask;
            if (loadBitmapTask != null && !loadBitmapTask.isDone()) {
                // load in progress, try to cancel it
                loadBitmapTask.cancel();
                loadBitmapTask.waitDone();
                dismissLoadingProgressDialog();
            }

            Future<Intent> saveTask = mSaveTask;
            if (saveTask != null && !saveTask.isDone()) {
                // save in progress, try to cancel it
                saveTask.cancel();
                saveTask.waitDone();
                dismissLoadingProgressDialog();
            }
            CropBusiness.clearInput(this);
            mCropView.pause();
        } finally {
            mGLRootView.unlockRenderThread();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBitmapScreenNail != null) {
            mBitmapScreenNail.recycle();
            mBitmapScreenNail = null;
        }
    }

    public GLRoot getGLRoot() {
        return mGLRootView;
    }

    /**
     * 是否需要全屏
     *
     * @return
     */
    protected boolean needFullScreen() {
        return true;
    }

    /**
     * 顶部Container View
     *
     * @return
     */
    protected View getTopContainerView() {
        return null;
    }

    /**
     * 底部Container View
     *
     * @return
     */
    protected View getBottomContainerView() {
        return null;
    }

    protected void initContainerViews(View topParent, View bottomParent) {
        //implement by subClass
    }

    protected void onViewsCreated() {

    }

    /**
     * 初始化view
     */
    private void initView() {
        mGLRootView = findViewById(R.id.gl_root_view);
        mTopContainer = findViewById(R.id.top_container);
        mBottomContainer = findViewById(R.id.bottom_container);

        View top = getTopContainerView();
        View bottom = getBottomContainerView();

        if (top != null) {
            mTopContainer.setVisibility(View.VISIBLE);
            mTopContainer.addView(top);
        }

        if (bottom != null) {
            mBottomContainer.setVisibility(View.VISIBLE);
            mBottomContainer.addView(bottom);
        }

        initContainerViews(mTopContainer, mBottomContainer);

        //add crop to gl root view
        mCropView = new CropView(this);
        mGLRootView.setContentPane(mCropView);

        onViewsCreated();
    }

    /**
     * 初始化数据
     */
    protected void initData() {
        setCompressFormat();
        setCropParameters();

        initHandler();
    }

    private void initHandler() {
        mHandler = new SynchronizedHandler(mGLRootView) {
            @Override
            public void handleMessage(Message msg) {
                onHandleMessage(msg);
            }
        };
    }

    private void setCompressFormat() {
        mCompressFormat = getIntent().getStringExtra(KEY_COMPRESS_FORMAT);
        if (mCompressFormat != null) {
            mCompressFormat.toLowerCase();
        }
    }

    private void setCropParameters() {
        Bundle extras = getIntent().getExtras();
        if (extras == null) {
            return;
        }
        int aspectX = extras.getInt(KEY_ASPECT_X, 0);
        int aspectY = extras.getInt(KEY_ASPECT_Y, 0);
        if (aspectX != 0 && aspectY != 0) {
            mFlippable = false;
            mCropView.setAspectRatio((float) aspectX / aspectY);
            mCurrentAspect = getAspect();
        } else {
            mFlippable = true;
        }

        float spotlightX = extras.getFloat(KEY_SPOTLIGHT_X, 0);
        float spotlightY = extras.getFloat(KEY_SPOTLIGHT_Y, 0);
        if (spotlightX != 0 && spotlightY != 0) {
            mCropView.setSpotlightRatio(spotlightX, spotlightY);
        }

        mConfirmOverwrite = extras.getBoolean(KEY_CONFIRM_OVERWRITE, false);
    }

    private int getAspect() {
        int index;
        float aspect = mCropView.getAspectRatio();
        if (aspect == 1) {
            index = ASPECT_1_1;
        } else if (aspect == 3f / 2f || aspect == 2f / 3f) {
            index = ASPECT_3_2;
        } else if (aspect == 4f / 3f || aspect == 3f / 4f) {
            index = ASPECT_4_3;
        } else if (aspect == 16f / 9f || aspect == 9f / 16f) {
            index = ASPECT_16_9;
        } else if (aspect == -1f) {
            index = ASPECT_FREE;
        } else {
            index = ASPECT_CUSTOMIZE;
        }
        return index;
    }

    /**
     * 获取图片信息
     *
     * @return
     */
    private MediaItem getMediaItemFromIntentData() {
        Uri uri = getIntent().getData();
        MediaItem item = new MediaItem();
        item.filePath = uri.getPath();
        int orientation = BitmapUtils.getOrientationFromPath(item.filePath);
        item.setRotation(orientation);
        return item;
    }

    /**
     * 处理handler msg
     *
     * @param message
     */
    protected void onHandleMessage(Message message) {
        switch (message.what) {
            case MSG_LARGE_BITMAP:
                dismissLoadingProgressDialog();
                onBitmapRegionDecoderAvailable((BitmapRegionDecoder) message.obj);
                break;
            case MSG_BITMAP:
                dismissLoadingProgressDialog();
                onBitmapAvailable((Bitmap) message.obj);
                break;
            case MSG_SHOW_SAVE_ERROR:
                dismissLoadingProgressDialog();
                Toast.makeText(BaseCropActivity.this, R.string.save_failure, Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED);
                finishActivityNoAnimation();
            case MSG_SDCARD_NOT_AVAILABLE:
                dismissLoadingProgressDialog();
                Toast.makeText(BaseCropActivity.this, R.string.sd_unavailable, Toast.LENGTH_SHORT).show();
                break;
            case MSG_SAVE_COMPLETE:
                dismissLoadingProgressDialog();
                Toast.makeText(BaseCropActivity.this, R.string.save_crop_succ, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK, (Intent) message.obj);
                finishActivityNoAnimation();
                break;
            case MSG_CANCEL_DIALOG:
                setResult(RESULT_CANCELED);
                finishActivityNoAnimation();
                break;
        }
    }

    /**
     * 加载图片
     */
    private void loadBitmap() {
        mMediaItem = getMediaItemFromIntentData();
        if (mMediaItem == null) {
            return;
        }

        showLoadingProgressDialog();
        boolean supportedByBitmapRegionDecoder = CropBusiness.isSupportRegionDecoder(mMediaItem.filePath);
        if (supportedByBitmapRegionDecoder) {
            mLoadTask = ThreadPool.getInstance().submit(
                    new ThreadPool.Job<BitmapRegionDecoder>() {
                        @Override
                        public BitmapRegionDecoder run(ThreadPool.JobContext jc) {
                            try {
                                return BitmapRegionDecoder.newInstance(mMediaItem.filePath, false);
                            } catch (Throwable t) {
                                Log.w(TAG, t);
                                return null;
                            }
                        }
                    },
                    new FutureListener<BitmapRegionDecoder>() {
                        public void onFutureDone(Future<BitmapRegionDecoder> future) {
                            mLoadTask = null;
                            BitmapRegionDecoder decoder = future.get();
                            if (future.isCancelled()) {
                                if (decoder != null) decoder.recycle();
                                return;
                            }
                            mHandler.sendMessage(mHandler.obtainMessage(MSG_LARGE_BITMAP, decoder));
                        }
                    });
        } else {
            mLoadBitmapTask = ThreadPool.getInstance().submit(
                    new ThreadPool.Job<Bitmap>() {
                        @Override
                        public Bitmap run(ThreadPool.JobContext jc) {
                            return new LocalImageRequest(mMediaItem.filePath, mMediaItem.getMimeType(), MediaItem.TYPE_THUMBNAIL).run(jc);
                        }
                    },
                    new FutureListener<Bitmap>() {
                        public void onFutureDone(Future<Bitmap> future) {
                            mLoadBitmapTask = null;
                            Bitmap bitmap = future.get();
                            if (future.isCancelled()) {
                                if (bitmap != null) bitmap.recycle();
                                return;
                            }
                            mHandler.sendMessage(mHandler.obtainMessage(MSG_BITMAP, bitmap));
                        }
                    });
        }
    }

    /**
     * 加载图片回调（区域解码）
     *
     * @param regionDecoder
     */
    private void onBitmapRegionDecoderAvailable(BitmapRegionDecoder regionDecoder) {
        if (regionDecoder == null) {
            Toast.makeText(this, R.string.load_bmp_failure, Toast.LENGTH_SHORT).show();
            finishActivityNoAnimation();
            return;
        }

        mRegionDecoder = regionDecoder;
        mUseRegionDecoder = true;
        mState = STATE_LOADED;

        BitmapFactory.Options options = new BitmapFactory.Options();
        int width = regionDecoder.getWidth();
        int height = regionDecoder.getHeight();
        options.inSampleSize = BitmapUtils.computeSampleSize(width, height, BitmapUtils.UNCONSTRAINED, BACKUP_PIXEL_COUNT);
        mBitmap = regionDecoder.decodeRegion(new Rect(0, 0, width, height), options);

        mBitmapScreenNail = new BitmapScreenNail(mBitmap);

        TileImageViewAdapter adapter = new TileImageViewAdapter();
        adapter.setScreenNail(mBitmapScreenNail, width, height);
        adapter.setRegionDecoder(regionDecoder);

        mCropView.setDataModel(adapter, mMediaItem.getRotation());
        mCropView.initializeHighlightRectangle();
    }

    /**
     * bitmap 加载图片回调（加载bitmap）
     *
     * @param bitmap
     */
    private void onBitmapAvailable(Bitmap bitmap) {
        if (bitmap == null) {
            Toast.makeText(this, R.string.load_bmp_failure, Toast.LENGTH_SHORT).show();
            finishActivityNoAnimation();
            return;
        }

        mUseRegionDecoder = false;
        mState = STATE_LOADED;

        mBitmap = bitmap;
        BitmapFactory.Options options = new BitmapFactory.Options();
        mCropView.setDataModel(new BitmapTileProvider(bitmap, 512), mMediaItem.getRotation());
        mCropView.initializeHighlightRectangle();
    }

    /**
     * 保存裁剪图片
     */
    protected void saveCropBitmap() {
        RectF cropRect = mCropView.getCropRectangle();
        if (cropRect == null) {
            return;
        }
        mState = STATE_SAVING;

        showLoadingProgressDialog();
        mSaveTask = ThreadPool.getInstance().submit(new SaveOutput(cropRect),
                new FutureListener<Intent>() {
                    public void onFutureDone(Future<Intent> future) {
                        mSaveTask = null;
                        if (future.isCancelled()) {
                            Log.i("aaa", "*********cancel**************");
                            return;
                        }

                        Intent intent = future.get();
                        Log.i("aaa", "=========succ======" + System.currentTimeMillis());
                        if (intent != null) {
                            if (intent.getData() == Uri.parse("sdcard_not_available")) {
                                mHandler.sendEmptyMessage(MSG_SDCARD_NOT_AVAILABLE);
                            } else {
                                mHandler.sendMessage(mHandler.obtainMessage(MSG_SAVE_COMPLETE, intent));
                            }
                        } else {
                            mHandler.sendEmptyMessage(MSG_SHOW_SAVE_ERROR);
                        }
                    }
                });
    }

    private class SaveOutput implements ThreadPool.Job<Intent> {
        private final RectF mCropRect;

        public SaveOutput(RectF cropRect) {
            mCropRect = cropRect;
        }

        public Intent run(final ThreadPool.JobContext jc) {
            Rect rect = CropBusiness.checkCropRect(mCropRect);
            Intent result = getIntent();
            result.putExtra(KEY_CROPPED_RECT, rect);

            String outputPath = result.getStringExtra(KEY_OUTPUT_PATH);
            String requestFormat = result.getStringExtra(KEY_COMPRESS_FORMAT);
            String fileExtension = CropBusiness.getFileExtension(requestFormat, mMediaItem);
            if (TextUtils.isEmpty(outputPath)) {
                outputPath = CropBusiness.generateCropOutputDir(BaseCropActivity.this, true, fileExtension);
                result.putExtra(KEY_OUTPUT_PATH, outputPath);
            }

            Bitmap cropped = getCroppedImage(rect, false);
            CropBusiness.saveMedia(jc, cropped, outputPath);
            result.setData(Uri.fromFile(new File(outputPath)));

            return result;
        }
    }

    private Bitmap getCroppedImage(Rect rect, boolean isScale) {
        Utils.assertTrue(rect.width() > 0 && rect.height() > 0);

        Bundle extras = getIntent().getExtras();
        // (outputX, outputY) = the width and height of the returning bitmap.
        int outputX = rect.width();
        int outputY = rect.height();
        if (extras != null) {
            int outputMaxX = extras.getInt(KEY_OUTPUT_MAX_X, 0);
            int outputMaxY = extras.getInt(KEY_OUTPUT_MAX_Y, 0);
            if (outputMaxX > 0 && outputMaxY > 0) {
                outputX = Math.min(outputX, outputMaxX);
                outputY = Math.min(outputY, outputMaxY);
            } else {
                outputX = extras.getInt(KEY_OUTPUT_X, outputX);
                outputY = extras.getInt(KEY_OUTPUT_Y, outputY);
            }
        }

        if (isScale && outputX * outputY > MAX_PIXEL_COUNT && (extras == null || !extras.getBoolean(KEY_RETURN_PATH_IF_TOO_LARGE, false))) {
            float scale = (float) Math.sqrt((float) MAX_PIXEL_COUNT / outputX / outputY);
            Log.w(TAG, "scale down the cropped image: " + scale);
            outputX = Math.round(scale * outputX);
            outputY = Math.round(scale * outputY);
        }

        // (rect.width() * scaleX, rect.height() * scaleY) =
        // the size of drawing area in output bitmap
        float scaleX = 1;
        float scaleY = 1;
        Rect dest = new Rect(0, 0, outputX, outputY);
        if (extras == null || extras.getBoolean(KEY_SCALE, true)) {
            scaleX = (float) outputX / rect.width();
            scaleY = (float) outputY / rect.height();
            if (extras != null && !extras.getBoolean(KEY_SCALE_UP_IF_NEEDED, false)) {
                if (scaleX > 1f) scaleX = 1;
                if (scaleY > 1f) scaleY = 1;
            }
        }

        // Keep the content in the center (or crop the content)
        int rectWidth = Math.round(rect.width() * scaleX);
        int rectHeight = Math.round(rect.height() * scaleY);
        dest.set(Math.round((outputX - rectWidth) / 2f),
                Math.round((outputY - rectHeight) / 2f),
                Math.round((outputX + rectWidth) / 2f),
                Math.round((outputY + rectHeight) / 2f));

        mCroppedWidth = outputX;
        mCroppedHeight = outputY;
        mSrcRect.set(rect);
        mDestRect.set(dest);

        try {
            if (mUseRegionDecoder) {
                int rotation = mMediaItem.getRotation();
                CropBusiness.rotateRectangle(rect, mCropView.getImageWidth(), mCropView.getImageHeight(), 360 - rotation);
                CropBusiness.rotateRectangle(dest, outputX, outputY, 360 - rotation);

                BitmapFactory.Options options = new BitmapFactory.Options();
                int sample = BitmapUtils.computeSampleSizeLarger(
                        Math.max(scaleX, scaleY));
                options.inSampleSize = sample;

                // The decoding result is what we want if
                //   1. The size of the decoded bitmap match the destination's size
                //   2. The destination covers the whole output bitmap
                //   3. No rotation
                if ((rect.width() / sample) == dest.width()
                        && (rect.height() / sample) == dest.height()
                        && (outputX == dest.width()) && (outputY == dest.height())
                        && rotation == 0) {
                    // To prevent concurrent access in GLThread
                    synchronized (mRegionDecoder) {
                        return mRegionDecoder.decodeRegion(rect, options);
                    }
                }

                Bitmap result;
                if (rotation == 90 || rotation == 270) {
                    result = Bitmap.createBitmap(
                            outputY, outputX, Bitmap.Config.ARGB_8888);
                } else {
                    result = Bitmap.createBitmap(
                            outputX, outputY, Bitmap.Config.ARGB_8888);
                }

                Canvas canvas = new Canvas(result);
                //CropBusiness.rotateCanvas(canvas, outputX, outputY, rotation);

                CropBusiness.drawInTiles(canvas, mRegionDecoder, rect, dest, sample);
                if (rotation != 0 && rotation != 360) {
                    result = BitmapUtils.rotateBitmap(result, rotation, true);
                }
                return result;
            } else {
                int rotation = mMediaItem.getRotation();
                CropBusiness.rotateRectangle(rect, mCropView.getImageWidth(), mCropView.getImageHeight(), 360 - rotation);
                CropBusiness.rotateRectangle(dest, outputX, outputY, 360 - rotation);
                Bitmap result = Bitmap.createBitmap(outputX, outputY, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(result);
                CropBusiness.rotateCanvas(canvas, outputX, outputY, rotation);
                canvas.drawBitmap(mBitmap, rect, dest, new Paint(Paint.FILTER_BITMAP_FLAG));
                return result;
            }
        } catch (OutOfMemoryError e) {
            return null;
        }
    }


    private void showLoadingProgressDialog() {
        mProgressDialog = ProgressHUD.create(this);
        mProgressDialog.setCancellable(true);
        mProgressDialog.show();
    }

    private void dismissLoadingProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
    }

    private void finishActivityNoAnimation() {
        finish();
        overridePendingTransition(0, 0);
    }

}
