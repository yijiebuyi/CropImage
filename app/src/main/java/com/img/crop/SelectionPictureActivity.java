package com.img.crop;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.annotation.Keep;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.img.crop.permissiongen.PermissionFail;
import com.img.crop.permissiongen.PermissionSuccess;
import com.img.crop.permissiongen.internal.PermissionUtil;
import com.img.crop.utils.FileUtil;

import java.io.File;
import java.net.URI;

/*
 * Copyright (C) 2017
 * 版权所有
 *
 * 功能描述：
 * 作者：huangyong
 * 创建时间：2018/1/4
 *
 * 修改人：
 * 修改描述：
 * 修改日期
 */
public class SelectionPictureActivity extends BaseSelectionPictureActivity implements
        BottomSheet.OnDialogCloseListener,
        AdapterView.OnItemClickListener {

    public static final int NEED_DELETE = 1 << 1;// 需要删除按钮
    public static final int NEED_LOOK = 1 << 2;// 需要预览
    public static final int NEED_COVER = 1 << 3; //需要设置封面

    public static final int RESULT_DELETE = RESULT_OK + 1;
    public static final int RESULT_LOOK = RESULT_OK + 2;
    public static final int RESULT_COVER = RESULT_OK + 3;

    private int mWidth = 300;
    private int mHeight = 300;

    private int mExtraAction;
    private boolean mNeedCrop;
    private BottomSheet mBottomSheet;
    private Activity mContext;
    private Handler mHandler;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        Intent intent = getIntent();
        if (intent != null) {
            mWidth = intent.getIntExtra(KEY_OUTPUT_X, 300);
            mHeight = intent.getIntExtra(KEY_OUTPUT_Y, 300);
            mExtraAction = intent.getIntExtra(KEY_EXTRA_ACTION, 0);
            mNeedCrop = intent.getBooleanExtra(NEED_CROP, false);
        }

        showCropImageDialog();
    }

    /**
     * 显示选择对话框
     */
    private void showCropImageDialog() {
        mBottomSheet = new BottomSheet(this);
        mBottomSheet.setTitleVisibility(View.GONE);

        ListView lv = new ListView(this);
        ActionAdapter adapter = new ActionAdapter(this, getActionsTxt(mExtraAction));
        lv.setAdapter(adapter);
        lv.setDivider(new ColorDrawable(getResources().getColor(R.color.hor_divide_line)));
        lv.setDividerHeight(1);
        lv.setOnItemClickListener(this);

        mBottomSheet.setContent(lv);
        mBottomSheet.setOnDismissListener(this);

        mBottomSheet.show();
    }

    /**
     * 获取对话框列表文案
     *
     * @param extraAction
     * @return
     */
    private String[] getActionsTxt(int extraAction) {
        String[] actionTxt = getResources().getStringArray(R.array.selects_pic);
        if ((extraAction & NEED_COVER) != 0 && (extraAction & NEED_LOOK) != 0 && (extraAction & NEED_DELETE) != 0) {
            actionTxt = getResources().getStringArray(R.array.selects_pic_pdc);
        } else if ((extraAction & NEED_LOOK) != 0 && (extraAction & NEED_DELETE) != 0) {
            actionTxt = getResources().getStringArray(R.array.selects_pic_pd);
        } else if ((extraAction & NEED_LOOK) != 0) {
            actionTxt = getResources().getStringArray(R.array.selects_pic_p);
        } else if ((extraAction & NEED_DELETE) != 0) {
            actionTxt = getResources().getStringArray(R.array.selects_pic_d);
        }

        return actionTxt;
    }

    /**
     * 图选择成功后，如果不需要图片，直接返回，否则打开裁剪图片页面
     *
     * @param file
     */
    private void setImgPathToResult(File file) {
        if (!mNeedCrop) {
            Intent intent = new Intent();
            intent.setData(Uri.fromFile(file));
            finishWhitData(RESULT_OK, intent);
            return;
        }

        if (file.exists()) {
            if (!UPLOAD_IMAGE_PATH.equals(file.getAbsolutePath())) {
                FileUtil.copyFiles(file.getAbsolutePath(), UPLOAD_IMAGE_PATH, true);
            }
            Intent intent = new Intent(CROP_ACTION, Uri.fromFile(file));
            intent.setClass(this, CropImageActivity.class);
            intent.putExtra(KEY_OUTPUT_X, mWidth);
            intent.putExtra(KEY_OUTPUT_Y, mHeight);
            startActivityForResult(intent, CROP_IMAGE_REQUEST_CODE);
        } else {
            finishWhitData(RESULT_CANCELED, null);
        }
    }

    /**
     * 失败
     */
    public void setFailure() {
        setResult(RESULT_CANCELED);
        dismissDialog();
        //finishWhitNoAnim();

        if (mHandler == null) {
            mHandler = new Handler();
        }
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finishWhitData(RESULT_CANCELED, null);
            }
        }, 330);
    }

    /**
     * 关闭对话框
     */
    private void dismissDialog() {
        if (mBottomSheet != null) {
            mBottomSheet.dismiss();
        }
    }

    /**
     * 取消对话框
     */
    private void cancelDialog() {
        if (mBottomSheet != null) {
            mBottomSheet.cancel();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        int eac = mExtraAction;
        if ((eac & NEED_COVER) != 0 && (eac & NEED_LOOK) != 0 && (eac & RESULT_DELETE) != 0) {
            //contain preview, delete, set cover
            switch (position) {
                case 0:
                    finishWhitData(RESULT_COVER, null);
                    break;
                case 1:
                    finishWhitData(RESULT_LOOK, null);
                    break;
                case 2:
                    pickerPhoto();
                    break;
                case 3:
                    takePhoto();
                    break;
                case 4:
                    finishWhitData(RESULT_DELETE, null);
                    break;
                case 5:
                    setFailure();
                    break;
            }
        } else if ((eac & NEED_LOOK) != 0 && (eac & RESULT_DELETE) != 0) {
            //contain preview, delete
            switch (position) {
                case 0:
                    finishWhitData(RESULT_LOOK, null);
                    break;
                case 1:
                    pickerPhoto();
                    break;
                case 2:
                    takePhoto();
                    break;
                case 3:
                    finishWhitData(RESULT_DELETE, null);
                    break;
                case 4:
                    setFailure();
                    break;
            }
        } else if ((eac & NEED_LOOK) != 0) {
            //contain preview
            switch (position) {
                case 0:
                    finishWhitData(RESULT_LOOK, null);
                    break;
                case 1:
                    pickerPhoto();
                    break;
                case 2:
                    takePhoto();
                    break;
                case 3:
                    setFailure();
                    break;
            }
        } else if ((eac & RESULT_DELETE) != 0) {
            //contain delete
            switch (position) {
                case 0:
                    pickerPhoto();
                    break;
                case 1:
                    takePhoto();
                    break;
                case 2:
                    finishWhitData(RESULT_DELETE, null);
                    break;
                case 3:
                    setFailure();
                    break;
            }
        } else {
            //default
            switch (position) {
                case 0:
                    pickerPhoto();
                    break;
                case 1:
                    takePhoto();
                    break;
                case 2:
                    setFailure();
                    break;
            }
        }

        dismissDialog();
    }

    @Override
    public void onCancel() {
        setFailure();
    }

    @Override
    public void onDismiss() {

    }

    @Keep
    @PermissionSuccess(requestCode = REQUEST_GALLERY_PERMISSION)
    public void getGallerySuccess() {
        String path = getDataColumnPermissionGranted(this, mUri, mSelection, mSelectionArgs);
        setImgPathToResult(new File(path));
    }

    @Keep
    @PermissionSuccess(requestCode = REQUEST_CAMERA_PERMISSION)
    public void getCameraSuccess() {
        enterCamera();
    }

    @PermissionFail(requestCode = REQUEST_GALLERY_PERMISSION)
    public void getGalleryFailure() {
        cancelDialog();
    }

    @PermissionFail(requestCode = REQUEST_CAMERA_PERMISSION)
    public void getCameraFailure() {
        cancelDialog();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case CHOOSE_IMAGE:
                if (resultCode == RESULT_OK && data != null) {
                    Uri imageUri = data.getData();
                    if (imageUri != null && ContentResolver.SCHEME_FILE.equalsIgnoreCase(imageUri.getScheme())) {
                        setImgPathToResult(new File(imageUri.getPath()));
                    } else if (imageUri != null && ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(imageUri.getScheme())) {
                        String[] filePathColumn = {MediaStore.MediaColumns.DATA};
                        Cursor cursor = getContentResolver().query(imageUri, filePathColumn, null, null, null);
                        if (cursor != null) {
                            cursor.moveToFirst();
                            int columnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
                            String picturePath = cursor.getString(columnIndex);
                            cursor.close();
                            if (picturePath == null) {
                                picturePath = getPath(mContext, imageUri);
                                if (!PermissionUtil.isOverMarshmallow()) {
                                    setImgPathToResult(new File(picturePath));
                                }
                            } else {
                                setImgPathToResult(new File(picturePath));
                            }
                        } else {
                            URI uri = URI.create(data.getData().toString());
                            setImgPathToResult(new File(uri));
                        }
                    } else {
                        finishWhitData(0, data);
                    }
                } else {
                    // 未选择图片
                    setFailure();
                }
                break;
            case TAKE_PHOTO: // 拍照
                if (resultCode == RESULT_OK) {
                    File file = new File(UPLOAD_IMAGE_PATH);
                    setImgPathToResult(file);
                } else {
                    // 未拍照
                    setFailure();
                }
                break;
            case CROP_IMAGE_REQUEST_CODE: // 裁剪结束
                if (resultCode == RESULT_OK && data != null) {
                    // 已裁剪成功
                    finishWhitData(RESULT_OK, data);
                } else {
                    // 未裁剪
                    setFailure();
                }
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }
    }

    private void finishWhitData(int resultCode, Intent data) {
        if (data != null) {
            setResult(resultCode, data);
        } else {
            setResult(resultCode);
        }

        finish();
    }
}
