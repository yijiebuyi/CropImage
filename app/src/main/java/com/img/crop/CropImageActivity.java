package com.img.crop;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.img.crop.core.BitmapTileProvider;
import com.img.crop.core.CropView;
import com.img.crop.core.DialogCustomizedCropRatio;
import com.img.crop.core.TileImageViewAdapter;
import com.img.crop.exif.ExifData;
import com.img.crop.exif.ExifOutputStream;
import com.img.crop.glsrender.gl11.BitmapScreenNail;
import com.img.crop.glsrender.gl11.GLRoot;
import com.img.crop.thdpool.Future;
import com.img.crop.thdpool.FutureListener;
import com.img.crop.thdpool.ThreadPool.Job;
import com.img.crop.thdpool.ThreadPool.JobContext;
import com.img.crop.utils.BitmapUtils;
import com.img.crop.utils.CropUtils;
import com.img.crop.utils.LocalImageRequest;
import com.img.crop.utils.SynchronizedHandler;
import com.img.crop.utils.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class CropImageActivity extends AbstractCropActivity {
    private static final String TAG = "CropImageActivity";

    private static final int MAX_PIXEL_COUNT = 1000000 / 32; // according to TransactionTooLargeException
    private static final int MAX_FILE_INDEX = 1000;
    private static final int TILE_SIZE = 512;
    private static final int BACKUP_PIXEL_COUNT = 480000; // around 800x600

    private static final int MSG_LARGE_BITMAP = 1;
    private static final int MSG_BITMAP = 2;
    private static final int MSG_SAVE_COMPLETE = 3;
    private static final int MSG_SHOW_SAVE_ERROR = 4;
    private static final int MSG_CANCEL_DIALOG = 5;
    private static final int MSG_SDCARD_NOT_AVAILABLE = 6;

    private static final int MAX_BACKUP_IMAGE_SIZE = 320;
    public static final int DEFAULT_COMPRESS_QUALITY = 95;
    private static final String TIME_STAMP_NAME = "'IMG'_yyyyMMdd_HHmmss";

    // Change these to Images.Media.WIDTH/HEIGHT after they are unhidden.
    private static final String WIDTH = "width";
    private static final String HEIGHT = "height";

    public static final String KEY_RETURN_DATA = "return-data";
    public static final String KEY_CROPPED_RECT = "cropped-rect";
    public static final String KEY_ASPECT_X = "aspectX";
    public static final String KEY_ASPECT_Y = "aspectY";
    public static final String KEY_SPOTLIGHT_X = "spotlightX";
    public static final String KEY_SPOTLIGHT_Y = "spotlightY";
    public static final String KEY_OUTPUT_X = "outputX";
    public static final String KEY_OUTPUT_Y = "outputY";
    public static final String KEY_OUTPUT_MAX_X = "outputMaxX";
    public static final String KEY_OUTPUT_MAX_Y = "outputMaxY";
    public static final String KEY_SCALE = "scale";
    public static final String KEY_DATA = "data";
    public static final String KEY_SCALE_UP_IF_NEEDED = "scaleUpIfNeeded";
    public static final String KEY_OUTPUT_FORMAT = "outputFormat";
    public static final String KEY_NO_FACE_DETECTION = "noFaceDetection";
    public static final String KEY_RETURN_PATH_IF_TOO_LARGE = "return-path-if-too-large";
    public static final String KEY_FILE_PATH = "filePath";
    public static final String KEY_CONFIRM_OVERWRITE = "confirm-overwrite";
    public static final String KEY_COMPRESS_FORMAT = "compress-format";

    private static final int ASPECT_FREE = 0;
    private static final int ASPECT_1_1 = 1;
    private static final int ASPECT_3_2 = 2;
    private static final int ASPECT_4_3 = 3;
    private static final int ASPECT_15_9 = 4;
    private static final int ASPECT_16_9 = 5;
    private static final int ASPECT_16_10 = 6;
    private static final int ASPECT_CUSTOMIZE = 7;
    public static final String KEY_SHOW_WHEN_LOCKED = "showWhenLocked";

    private static final String KEY_STATE = "state";

    private static final int STATE_INIT = 0;
    private static final int STATE_LOADED = 1;
    private static final int STATE_SAVING = 2;

    //private static final int MENU_SAVE_ID = 0;
    public static final File DOWNLOAD_BUCKET = new File(
            Environment.getExternalStorageDirectory(), "DOWNLOAD");

    public static final String CROP_ACTION = "com.android.camera.action.CROP";
    private static final Uri SDCARD_NOT_AVIALABLE = Uri.parse("sdcard_not_available");

    private int mState = STATE_INIT;

    private CropView mCropView;

    private boolean mDoFaceDetection = false;

    private Handler mMainHandler;

    // We keep the following members so that we can free them

    // mBitmap is the unrotated bitmap we pass in to mCropView for detect faces.
    // mCropView is responsible for rotating it to the way that it is viewed by users.
    private Bitmap mBitmap;
    private BitmapTileProvider mBitmapTileProvider;
    private BitmapRegionDecoder mRegionDecoder;
    private Bitmap mBitmapInIntent;
    private boolean mUseRegionDecoder = false;
    private BitmapScreenNail mBitmapScreenNail;

    private ProgressHUD mProgressDialog;
    private Future<BitmapRegionDecoder> mLoadTask;
    private Future<Bitmap> mLoadBitmapTask;
    private Future<Intent> mSaveTask;

    private MediaItem mMediaItem;

    private LinearLayout mUspeifedRatio;
    private LinearLayout mOneOneRatio;
    private LinearLayout mThreeTwoRatio;
    private LinearLayout mFourThreeRatio;
    private LinearLayout mSixteenNineRatio;
    private LinearLayout mCustomRatio;

    private ImageView mUspeifedRatioIcon;
    private ImageView mOneOneRatioIcon;
    private ImageView mThreeTwoRatioIcon;
    private ImageView mFourThreeRatioIcon;
    private ImageView mSixteenNineRatioIcon;
    private ImageView mCustomRatioIcon;

    private TextView mUspeifedRatioText;
    private TextView mOneOneRatioText;
    private TextView mThreeTwoRatioText;
    private TextView mFourThreeRatioText;
    private TextView mSixteenNineRatioText;
    private TextView mCustomRatioText;

    private TextView mCropRatioResult;
    private TextView mSaveCrop;
    private TextView mRotate;

    private int mCurrentAspect;
    private boolean mOverwrite;
    private boolean mConfirmOverwrite = false;
    private AlertDialog.Builder mConfirmDlg;

    private int mCroppedWidth = 0;
    private int mCroppedHeight = 0;
    private Rect mSrcRect = new Rect();
    private Rect mDestRect = new Rect();
    public final static int WORKING_COMPRESS_STORAGE = 4096;
    private String mCompressFormat = null;
    private boolean mFlippable = true;
    private int mIconHoverColor = Color.argb(255, 49, 164, 229);
    private int mTextColor;
    private View mCropActionView;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        //screen portrait
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        // Initialize UI
        initUI();

        mCropView = new CropView(this);
        getGLRoot().setContentPane(mCropView);

        mMainHandler = new SynchronizedHandler(getGLRoot()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_LARGE_BITMAP: {
                        dismissLoadingProgressDialog();
                        onBitmapRegionDecoderAvailable((BitmapRegionDecoder) message.obj);
                        break;
                    }
                    case MSG_BITMAP: {
                        dismissLoadingProgressDialog();
                        onBitmapAvailable((Bitmap) message.obj);
                        break;
                    }
                    case MSG_SHOW_SAVE_ERROR: {
                        dismissLoadingProgressDialog();
                        Toast.makeText(CropImageActivity.this, "保存失败", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_CANCELED);
                        finishActivityNoAnimation();
                    }
                    case MSG_SDCARD_NOT_AVAILABLE: {
                        dismissLoadingProgressDialog();
                        Toast.makeText(CropImageActivity.this, "SD卡不可用", Toast.LENGTH_SHORT).show();
                        break;
                    }
                    case MSG_SAVE_COMPLETE: {
                        dismissLoadingProgressDialog();
                        Toast.makeText(CropImageActivity.this, "保存裁剪成功", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK, (Intent) message.obj);
                        finishActivityNoAnimation();
                        break;
                    }
                    case MSG_CANCEL_DIALOG: {
                        setResult(RESULT_CANCELED);
                        finishActivityNoAnimation();
                        break;
                    }
                }
            }
        };

        setCropParameters();
        mCurrentAspect = getAspect();

        mConfirmDlg = new AlertDialog.Builder(this);
        mConfirmDlg.setTitle(getString(R.string.if_remain_original_file));
        mConfirmDlg.setNegativeButton(getString(R.string.cancel), null);
        mConfirmDlg.setNeutralButton(getString(R.string.overwrite), new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                setOverwrite(true);
                onSaveClicked();

            }
        });
        mConfirmDlg.setPositiveButton(getString(R.string.save_as), new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                setOverwrite(false);
                onSaveClicked();
            }
        });

        mIntentType.mType = INTENT_CROP;
        mIntentType.mMimeType = INTENT_FILETER_IMAGE;

        mCompressFormat = getIntent().getStringExtra(KEY_COMPRESS_FORMAT);
        if (mCompressFormat != null) {
            mCompressFormat.toLowerCase();
        }

    }

    @Override
    protected void onSaveInstanceState(Bundle saveState) {
        super.onSaveInstanceState(saveState); //TODO
        saveState.putInt(KEY_STATE, mState);
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onBackPressed() {
        finishActivityNoAnimation();
    }

    private void initUI() {
        mIconHoverColor = getResources().getColor(R.color.crop_blue);
        mTextColor = getResources().getColor(R.color.crop_image_ratio_color);

        setContentView(R.layout.activity_crop_image);

        mCropRatioResult = (TextView) findViewById(R.id.crop_ratio_result);
        mSaveCrop = (TextView) findViewById(R.id.save);
        mRotate = (TextView) findViewById(R.id.rotate);

        mCropActionView = (LinearLayout) findViewById(R.id.crop_view_action);
        mUspeifedRatio = (LinearLayout) findViewById(R.id.unspecified_ratio);
        mOneOneRatio = (LinearLayout) findViewById(R.id.one_one_ratio);
        mThreeTwoRatio = (LinearLayout) findViewById(R.id.three_two_ratio);
        mFourThreeRatio = (LinearLayout) findViewById(R.id.four_three_ratio);
        mSixteenNineRatio = (LinearLayout) findViewById(R.id.sixteen_nine_ratio);
        mCustomRatio = (LinearLayout) findViewById(R.id.custom_ratio);
        setViewClickListener();

        mUspeifedRatioIcon = (ImageView) findViewById(R.id.unspecified_ratio_icon);
        mOneOneRatioIcon = (ImageView) findViewById(R.id.one_one_ratio_icon);
        mThreeTwoRatioIcon = (ImageView) findViewById(R.id.three_two_ratio_icon);
        mFourThreeRatioIcon = (ImageView) findViewById(R.id.four_three_ratio_icon);
        mSixteenNineRatioIcon = (ImageView) findViewById(R.id.sixteen_nine_ratio_icon);
        mCustomRatioIcon = (ImageView) findViewById(R.id.custom_ratio_icon);

        mUspeifedRatioText = (TextView) findViewById(R.id.unspecified_ratio_text);
        mOneOneRatioText = (TextView) findViewById(R.id.one_one_ratio_text);
        mThreeTwoRatioText = (TextView) findViewById(R.id.three_two_ratio_text);
        mFourThreeRatioText = (TextView) findViewById(R.id.four_three_ratio_text);
        mSixteenNineRatioText = (TextView) findViewById(R.id.sixteen_nine_ratio_text);
        mCustomRatioText = (TextView) findViewById(R.id.custom_ratio_text);

        //The default selection
        mUspeifedRatioIcon.setSelected(true);
        mUspeifedRatioText.setTextColor(mIconHoverColor);

        mSaveCrop.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onSaveClicked();
            }
        });
        mRotate.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mCropView.rotateCropFrame();
                mCropView.setAspectRatio(1.0f / mCropView.getAspectRatio());
            }
        });
    }

    private void setViewClickListener() {
        OnClickListener listener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                int which = v.getId();
                float aspectRatio = 1.0f;
                RectF r = mCropView.getCropRectangle();
                final int w = Math.round(r.right) - Math.round(r.left);
                final int h = Math.round(r.bottom) - Math.round(r.top);
                switchCorpRatioState(which);
                switch (which) {
                    case R.id.unspecified_ratio:
                        aspectRatio = CropView.UNSPECIFIED;
                        break;
                    case R.id.one_one_ratio:
                        aspectRatio = 1;
                        break;
                    case R.id.three_two_ratio:
                        aspectRatio = 3.0f / 2.0f;
                        break;
                    case R.id.four_three_ratio:
                        aspectRatio = 4.0f / 3.0f;
                        break;
                    case R.id.sixteen_nine_ratio:
                        aspectRatio = 16.0f / 9.0f;
                        break;
                    case R.id.custom_ratio:
                        DialogCustomizedCropRatio mDialog = new DialogCustomizedCropRatio(CropImageActivity.this);
                        mDialog.setDefaultCropImageSize(w, h);
                        mDialog.setImageSize(mCropView.getImageWidth(), mCropView.getImageHeight());
                        mDialog.setmICustomizedCropSizeListener(new DialogCustomizedCropRatio.ICustomizedCropSizeListener() {
                            @Override
                            public void cropSize(int cropWidth, int cropHeight) {
                                mCropView.setCustomizeCropSize(cropWidth, cropHeight);
                                mCropView.invalidate();
                            }
                        });
                        mDialog.show();
                        aspectRatio = 1.0f;
                        break;

                }

                if (which != R.id.custom_ratio) {
                    mCropView.setAspectRatio(aspectRatio);
                    mCropView.invalidate();
                }

            }
        };

        mUspeifedRatio.setOnClickListener(listener);
        mOneOneRatio.setOnClickListener(listener);
        mThreeTwoRatio.setOnClickListener(listener);
        mFourThreeRatio.setOnClickListener(listener);
        mSixteenNineRatio.setOnClickListener(listener);
        mCustomRatio.setOnClickListener(listener);
    }

    private void switchCorpRatioState(int which) {
        mUspeifedRatioIcon.setSelected(false);
        mOneOneRatioIcon.setSelected(false);
        mThreeTwoRatioIcon.setSelected(false);
        mFourThreeRatioIcon.setSelected(false);
        mSixteenNineRatioIcon.setSelected(false);
        mCustomRatioIcon.setSelected(false);

        mUspeifedRatioText.setTextColor(mTextColor);
        mOneOneRatioText.setTextColor(mTextColor);
        mThreeTwoRatioText.setTextColor(mTextColor);
        mFourThreeRatioText.setTextColor(mTextColor);
        mSixteenNineRatioText.setTextColor(mTextColor);
        mCustomRatioText.setTextColor(mTextColor);

        switch (which) {
            case R.id.unspecified_ratio:
            case -1:
                mUspeifedRatioIcon.setSelected(true);
                mUspeifedRatioText.setTextColor(mIconHoverColor);
                break;
            case R.id.one_one_ratio:
                mOneOneRatioIcon.setSelected(true);
                mOneOneRatioText.setTextColor(mIconHoverColor);
                break;
            case R.id.three_two_ratio:
                mThreeTwoRatioIcon.setSelected(true);
                mThreeTwoRatioText.setTextColor(mIconHoverColor);
                break;
            case R.id.four_three_ratio:
                mFourThreeRatioIcon.setSelected(true);
                mFourThreeRatioText.setTextColor(mIconHoverColor);
                break;
            case R.id.sixteen_nine_ratio:
                mSixteenNineRatioIcon.setSelected(true);
                mSixteenNineRatioText.setTextColor(mIconHoverColor);
                break;
            case R.id.custom_ratio:
                mCustomRatioIcon.setSelected(true);
                mCustomRatioText.setTextColor(mIconHoverColor);
                break;
        }
    }

    private class SaveOutput implements Job<Intent> {
        private final RectF mCropRect;

        public SaveOutput(RectF cropRect) {
            mCropRect = cropRect;
        }

        public Intent run(final JobContext jc) {
            Rect rect = getCropRect(mCropRect);
            Bundle extra = getIntent().getExtras();

            Intent result = new Intent();
            result.putExtra(KEY_CROPPED_RECT, rect);
            Bitmap cropped = null;
            boolean outputted = false;
            extra = new Bundle();
            extra.putBoolean(KEY_RETURN_DATA, true);
            extra.putBoolean(KEY_RETURN_PATH_IF_TOO_LARGE, true);
            if (extra != null) {
                Uri uri = (Uri) extra.getParcelable(MediaStore.EXTRA_OUTPUT);
                uri = Uri.fromFile(new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/crop_.jpg"));
                if (uri != null) {
                    if (jc.isCancelled()) {
                        return null;
                    }
                    outputted = true;
                    cropped = getCroppedImage(rect, false);
                    if (!saveBitmapToUri(jc, cropped, uri)) return null;
                }
            }
            return result;
        }
    }

    private Rect getCropRect(RectF cropRect) {
        int left = Math.round(cropRect.left);
        int top = Math.round(cropRect.top);
        int right = Math.round(cropRect.right);
        int bottom = Math.round(cropRect.bottom);
        if (left == right) {
            left = left - 1;
            if (left < 0) {
                left = 0;
                right = 1;
            }
        }

        if (top == bottom) {
            top = top - 1;
            if (top < 0) {
                top = 0;
                bottom = 1;
            }
        }

        return new Rect(left, top, right, bottom);
    }

    public static String determineCompressFormat(Object obj) {
        String compressFormat = "JPEG";
        if (obj instanceof MediaItem) {
            String mime = ((MediaItem) obj).getMimeType();
            if (mime.contains("png") || mime.contains("gif")) {
                // Set the compress format to PNG for png and gif images
                // because they may contain alpha values.
                compressFormat = "PNG";
            }
        }
        return compressFormat;
    }


    private File saveMedia(
            JobContext jc, Bitmap cropped, File directory, String filename, ExifData exifData) {
        // Try file-1.jpg, file-2.jpg, ... until we find a filename
        // which does not exist yet.
        File candidate = null;
        String fileExtension = getFileExtension();
        try {
            if (!mOverwrite) {
                for (int i = 1; i < MAX_FILE_INDEX; ++i) {
                    String num = String.format("-%03d.", i);
                    candidate = new File(directory, filename + num + fileExtension);
                    if (candidate.createNewFile())
                        break;
                }
            } else {
                candidate = new File(directory, filename + "." + fileExtension);
                if (!candidate.exists())
                    candidate.createNewFile();
            }
        } catch (IOException e) {
            Log.e(TAG, "fail to create new file: " + candidate.getAbsolutePath(), e);
            return null;
        }
        if (!candidate.exists() || !candidate.isFile()) {
            throw new RuntimeException("cannot create file: " + filename);
        }

        candidate.setReadable(true, false);
        candidate.setWritable(true, false);

        try {
            FileOutputStream fos = new FileOutputStream(candidate);
            try {
                if (exifData != null) {
                    ExifOutputStream eos = new ExifOutputStream(fos);
                    eos.setExifData(exifData);
                    saveBitmapToOutputStream(jc, cropped,
                            convertExtensionToCompressFormat(fileExtension), eos);
                } else {
                    saveBitmapToOutputStream(jc, cropped,
                            convertExtensionToCompressFormat(fileExtension), fos);
                }
            } finally {
                fos.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "fail to save image: "
                    + candidate.getAbsolutePath(), e);
            candidate.delete();
            return null;
        }

        if (jc.isCancelled()) {
            candidate.delete();
            return null;
        }

        return candidate;
    }

    private boolean saveBitmapToOutputStream(
            JobContext jc, Bitmap bitmap, CompressFormat format, OutputStream os) {
        // We wrap the OutputStream so that it can be interrupted.
        try {
            if (bitmap != null) {
                if (!bitmap.compress(format, DEFAULT_COMPRESS_QUALITY, os)) {
                    return false;
                }
            }
            return !jc.isCancelled();
        } finally {
            jc.setCancelListener(null);
            Utils.closeSilently(os);
        }
    }

    private CompressFormat convertExtensionToCompressFormat(String extension) {
        return extension.equals("png")
                ? CompressFormat.PNG
                : CompressFormat.JPEG;
    }

    private String getFileExtension() {
        String requestFormat = getIntent().getStringExtra(KEY_OUTPUT_FORMAT);
        String outputFormat = (requestFormat == null)
                ? determineCompressFormat(mMediaItem)
                : requestFormat;

        outputFormat = outputFormat.toLowerCase();
        return (outputFormat.equals("png") || outputFormat.equals("gif"))
                ? "png" // We don't support gif compression.
                : "jpg";
    }

    public void onSaveClicked() {
        RectF cropRect = mCropView.getCropRectangle();
        if (cropRect == null) {
            return;
        }
        mState = STATE_SAVING;

        showLoadingProgressDialog();
        mSaveTask = getThreadPool().submit(new SaveOutput(cropRect),
                new FutureListener<Intent>() {
                    public void onFutureDone(Future<Intent> future) {
                        mSaveTask = null;
                        if (future.isCancelled()) {
                            return;
                        }

                        Intent intent = future.get();
                        if (intent != null) {
                            if (intent.getData() == SDCARD_NOT_AVIALABLE) {
                                mMainHandler.sendEmptyMessage(MSG_SDCARD_NOT_AVAILABLE);
                            } else {
                                mMainHandler.sendMessage(mMainHandler.obtainMessage(
                                        MSG_SAVE_COMPLETE, intent));
                            }
                        } else {
                            mMainHandler.sendEmptyMessage(MSG_SHOW_SAVE_ERROR);
                        }
                    }
                });
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
            if (extras == null || !extras.getBoolean(
                    KEY_SCALE_UP_IF_NEEDED, false)) {
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
            if (mBitmapInIntent != null) {
                Bitmap source = mBitmapInIntent;
                Bitmap result = Bitmap.createBitmap(
                        outputX, outputY, Config.ARGB_8888);
                Canvas canvas = new Canvas(result);
                canvas.drawBitmap(source, rect, dest, null);
                return result;
            }

            if (mUseRegionDecoder) {
                int rotation = mMediaItem.getFullImageRotation();
                rotateRectangle(rect, mCropView.getImageWidth(),
                        mCropView.getImageHeight(), 360 - rotation);
                rotateRectangle(dest, outputX, outputY, 360 - rotation);

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
                            outputY, outputX, Config.ARGB_8888);
                } else {
                    result = Bitmap.createBitmap(
                            outputX, outputY, Config.ARGB_8888);
                }

                Canvas canvas = new Canvas(result);
                // rotateCanvas(canvas, outputX, outputY, rotation);

                drawInTiles(canvas, mRegionDecoder, rect, dest, sample);
                if (rotation != 0 && rotation != 360) {
                    result = BitmapUtils.rotateBitmap(result, rotation, true);
                }
                return result;
            } else {
                int rotation = mMediaItem.getRotation();
                rotateRectangle(rect, mCropView.getImageWidth(),
                        mCropView.getImageHeight(), 360 - rotation);
                rotateRectangle(dest, outputX, outputY, 360 - rotation);
                Bitmap result = Bitmap.createBitmap(outputX, outputY, Config.ARGB_8888);
                Canvas canvas = new Canvas(result);
                rotateCanvas(canvas, outputX, outputY, rotation);
                canvas.drawBitmap(mBitmap,
                        rect, dest, new Paint(Paint.FILTER_BITMAP_FLAG));
                return result;
            }
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    public static void rotateCanvas(
            Canvas canvas, int width, int height, int rotation) {
        canvas.translate(width / 2, height / 2);
        canvas.rotate(rotation);
        if (((rotation / 90) & 0x01) == 0) {
            canvas.translate(-width / 2, -height / 2);
        } else {
            canvas.translate(-height / 2, -width / 2);
        }
    }

    public static void rotateRectangle(
            Rect rect, int width, int height, int rotation) {
        if (rotation == 0 || rotation == 360) return;

        int w = rect.width();
        int h = rect.height();
        switch (rotation) {
            case 90: {
                rect.top = rect.left;
                rect.left = height - rect.bottom;
                rect.right = rect.left + h;
                rect.bottom = rect.top + w;
                return;
            }
            case 180: {
                rect.left = width - rect.right;
                rect.top = height - rect.bottom;
                rect.right = rect.left + w;
                rect.bottom = rect.top + h;
                return;
            }
            case 270: {
                rect.left = rect.top;
                rect.top = width - rect.right;
                rect.right = rect.left + h;
                rect.bottom = rect.top + w;
                return;
            }
            default:
                throw new AssertionError();
        }
    }

    private void drawInTiles(Canvas canvas,
                             BitmapRegionDecoder decoder, Rect rect, Rect dest, int sample) {
        int tileSize = TILE_SIZE * sample;
        Rect tileRect = new Rect();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Config.ARGB_8888;
        options.inSampleSize = sample;
        canvas.translate(dest.left, dest.top);
        canvas.scale((float) sample * dest.width() / rect.width(),
                (float) sample * dest.height() / rect.height());
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        for (int tx = rect.left, x = 0;
             tx < rect.right; tx += tileSize, x += TILE_SIZE) {
            for (int ty = rect.top, y = 0;
                 ty < rect.bottom; ty += tileSize, y += TILE_SIZE) {
                tileRect.set(tx, ty, tx + tileSize, ty + tileSize);
                if (tileRect.intersect(rect)) {
                    Bitmap bitmap;

                    // To prevent concurrent access in GLThread
                    synchronized (decoder) {
                        bitmap = decoder.decodeRegion(tileRect, options);
                    }
                    canvas.drawBitmap(bitmap, x, y, paint);
                    bitmap.recycle();
                }
            }
        }
    }

    private void onBitmapRegionDecoderAvailable(
            BitmapRegionDecoder regionDecoder) {

        if (regionDecoder == null) {
            //SlideNotice.makeNotice(this, getResources().getString(R.string.fail_to_load_image), SlideNotice.NOTICE_TYPE_FAILURE, SlideNotice.LENGTH_SHORT).show();
            finishActivityNoAnimation();
            return;
        }
        mRegionDecoder = regionDecoder;
        mUseRegionDecoder = true;
        mState = STATE_LOADED;

        BitmapFactory.Options options = new BitmapFactory.Options();
        int width = regionDecoder.getWidth();
        int height = regionDecoder.getHeight();
        options.inSampleSize = BitmapUtils.computeSampleSize(width, height,
                BitmapUtils.UNCONSTRAINED, BACKUP_PIXEL_COUNT);
        mBitmap = regionDecoder.decodeRegion(
                new Rect(0, 0, width, height), options);

        mBitmapScreenNail = new BitmapScreenNail(mBitmap);

        TileImageViewAdapter adapter = new TileImageViewAdapter();
        adapter.setScreenNail(mBitmapScreenNail, width, height);
        adapter.setRegionDecoder(regionDecoder);

        mCropView.setDataModel(adapter, mMediaItem.getFullImageRotation());

        if (mDoFaceDetection) {
            mCropView.detectFaces(mBitmap);
        } else {
            mCropView.initializeHighlightRectangle();
        }
    }

    private void onBitmapAvailable(Bitmap bitmap) {
        if (bitmap == null) {
            //SlideNotice.makeNotice(this, getString(R.string.fail_to_load_image), SlideNotice.NOTICE_TYPE_FAILURE, SlideNotice.LENGTH_SHORT).show();
            finishActivityNoAnimation();
            return;
        }
        mUseRegionDecoder = false;
        mState = STATE_LOADED;

        mBitmap = bitmap;
        BitmapFactory.Options options = new BitmapFactory.Options();
        mCropView.setDataModel(new BitmapTileProvider(bitmap, 512),
                mMediaItem.getRotation());
        if (mDoFaceDetection) {
            mCropView.detectFaces(bitmap);
        } else {
            mCropView.initializeHighlightRectangle();
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
            mCropView.setFlippable(mFlippable);
            initFixedAspect();
            if (mCropActionView != null) {
                mCropActionView.setVisibility(View.GONE);
            }
        } else {
            mFlippable = true;
        }

        mCropRatioResult.setText(mCropView.getCropSizeString());

        float spotlightX = extras.getFloat(KEY_SPOTLIGHT_X, 0);
        float spotlightY = extras.getFloat(KEY_SPOTLIGHT_Y, 0);
        if (spotlightX != 0 && spotlightY != 0) {
            mCropView.setSpotlightRatio(spotlightX, spotlightY);
        }

        mConfirmOverwrite = extras.getBoolean(KEY_CONFIRM_OVERWRITE, false);
    }

    private void initializeData() {
        Bundle extras = getIntent().getExtras();

        if (extras != null) {
            if (extras.containsKey(KEY_NO_FACE_DETECTION)) {
                mDoFaceDetection = !extras.getBoolean(KEY_NO_FACE_DETECTION);
            }

            mBitmapInIntent = extras.getParcelable(KEY_DATA);

            if (mBitmapInIntent != null) {
                mBitmapTileProvider =
                        new BitmapTileProvider(mBitmapInIntent, MAX_BACKUP_IMAGE_SIZE);
                mCropView.setDataModel(mBitmapTileProvider, 0);
                if (mDoFaceDetection) {
                    mCropView.detectFaces(mBitmapInIntent);
                } else {
                    mCropView.initializeHighlightRectangle();
                }
                mState = STATE_LOADED;
                return;
            }
        }

        showLoadingProgressDialog();

        mMediaItem = getMediaItemFromIntentData();
        if (mMediaItem == null) return;

        boolean supportedByBitmapRegionDecoder = true;
        //TODO
        //(mMediaItem.getSupportedOperations() & MediaItem.SUPPORT_FULL_IMAGE) != 0;
        if (supportedByBitmapRegionDecoder) {
            mLoadTask = getThreadPool().submit(new LoadDataTask(mMediaItem),
                    new FutureListener<BitmapRegionDecoder>() {
                        public void onFutureDone(Future<BitmapRegionDecoder> future) {
                            mLoadTask = null;
                            BitmapRegionDecoder decoder = future.get();
                            if (future.isCancelled()) {
                                if (decoder != null) decoder.recycle();
                                return;
                            }
                            mMainHandler.sendMessage(mMainHandler.obtainMessage(
                                    MSG_LARGE_BITMAP, decoder));
                        }
                    });
        } else {
            mLoadBitmapTask = getThreadPool().submit(new LoadBitmapDataTask(mMediaItem),
                    new FutureListener<Bitmap>() {
                        public void onFutureDone(Future<Bitmap> future) {
                            mLoadBitmapTask = null;
                            Bitmap bitmap = future.get();
                            if (future.isCancelled()) {
                                if (bitmap != null) bitmap.recycle();
                                return;
                            }
                            mMainHandler.sendMessage(mMainHandler.obtainMessage(
                                    MSG_BITMAP, bitmap));
                        }
                    });
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (mState == STATE_INIT) {
            initializeData();
        } else if (mState == STATE_SAVING) {
            onSaveClicked();
        }

        // TODO: consider to do it in GLView system
        GLRoot root = getGLRoot();
        root.lockRenderThread();
        try {
            mCropView.resume();
        } finally {
            root.unlockRenderThread();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        dismissLoadingProgressDialog();

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
        GLRoot root = getGLRoot();
        root.lockRenderThread();
        try {
            mCropView.pause();
        } finally {
            root.unlockRenderThread();
        }
        CropUtils.clearInput(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBitmapScreenNail != null) {
            mBitmapScreenNail.recycle();
            mBitmapScreenNail = null;
        }
    }

    private MediaItem getMediaItemFromIntentData() {
        //Uri uri = getIntent().getData();
        //TODO
        MediaItem item = new MediaItem();
        item.filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/test.jpg";
        item.setMimeType("image/jpg");
        return item;
    }

    private class LoadDataTask implements Job<BitmapRegionDecoder> {
        MediaItem mItem;

        public LoadDataTask(MediaItem item) {
            mItem = item;
        }

        public BitmapRegionDecoder run(JobContext jc) {
            try {
                return BitmapRegionDecoder.newInstance(mItem.filePath, false);
            } catch (Throwable t) {
                Log.w(TAG, t);
                return null;
            }
        }
    }

    private class LoadBitmapDataTask implements Job<Bitmap> {
        MediaItem mItem;

        public LoadBitmapDataTask(MediaItem item) {
            mItem = item;
        }

        public Bitmap run(JobContext jc) {
            return new LocalImageRequest(mItem.filePath, mItem.getMimeType(), MediaItem.TYPE_THUMBNAIL).run(jc);
        }
    }

    public void setOverwrite(boolean overwrite) {
        mOverwrite = overwrite;
    }

    private boolean saveOutputByFilePath(JobContext jc, Bitmap cropped, Intent intent) {
        if (mMediaItem != null) {
            File directory = new File(Environment.getExternalStorageDirectory().getAbsolutePath());
            String filename = "crop_" + System.currentTimeMillis() + ".jpg";
            File output = saveMedia(jc, cropped, directory, filename, null);
            if (output == null) return false;

            intent.putExtra(KEY_FILE_PATH, output.getAbsolutePath());
            return true;
        }
        return false;
    }

    private boolean saveBitmapToUri(JobContext jc, Bitmap bitmap, Uri uri) {
        try {
            return saveBitmapToOutputStream(jc, bitmap,
                    convertExtensionToCompressFormat(mCompressFormat != null && mCompressFormat.length() > 0 ?
                            mCompressFormat : getFileExtension()),
                    getContentResolver().openOutputStream(uri));
        } catch (FileNotFoundException e) {
            Log.w(TAG, "cannot write output", e);
        }
        return true;
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

    private void initFixedAspect() {
        mCurrentAspect = getAspect();
    }

    public void updateCropSize(String str) {
        if (mCropRatioResult != null)
            mCropRatioResult.setText(str);
    }

    private void finishActivityNoAnimation() {
        finish();
        overridePendingTransition(0, 0);
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
}
