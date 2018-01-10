package com.img.crop;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.img.crop.core.GlImageView;
import com.img.crop.core.TileImageViewAdapter;
import com.img.crop.glsrender.gl11.BitmapScreenNail;
import com.img.crop.thdpool.Future;
import com.img.crop.thdpool.FutureListener;
import com.img.crop.thdpool.ThreadPool;
import com.img.crop.utils.BitmapUtils;
import com.img.crop.utils.CropBusiness;
import com.img.crop.utils.LocalImageRequest;
import com.img.crop.utils.SynchronizedHandler;

/**
 * Created by Administrator on 2018/1/10.
 */

public class ImageViewActivity extends Activity {
    private final static String TAG = "Image";

    //==========================State=========================================
    /**
     * 初始化
     */
    private static final int STATE_INIT = 0;
    /**
     * 已加载图片
     */
    private static final int STATE_LOADED = 1;


    //==========================Handler msg===================================
    /**
     * 加载大图
     */
    private static final int MSG_LARGE_BITMAP = 1;
    /**
     * 加载普通图片
     */
    private static final int MSG_BITMAP = 2;

    private static final int BACKUP_PIXEL_COUNT = 480000; // around 800x600

    private static final String KEY_STATE = "state";

    protected GlImageView mGLImageView;

    private int mState = STATE_INIT;
    private Handler mHandler;


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

    private MediaItem mMediaItem;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mGLImageView = new GlImageView(this);
        setContentView(mGLImageView);

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
        switch (mState) {
            case STATE_INIT:
                loadBitmap();
                break;
        }
        mGLImageView.onResume();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mGLImageView.onPause();

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
        } finally {

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

    /**
     * 初始化数据
     */
    protected void initData() {
        initHandler();
    }

    private void initHandler() {
        mHandler = new SynchronizedHandler(mGLImageView) {
            @Override
            public void handleMessage(Message msg) {
                onHandleMessage(msg);
            }
        };
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

        mGLImageView.setDataModel(adapter, mMediaItem.getRotation());
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
        mGLImageView.setImageBitmap(bitmap, mMediaItem.getRotation());
    }


    private void showLoadingProgressDialog() {
        mProgressDialog = ProgressHUD.create(this);
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
