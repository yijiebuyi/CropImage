package com.img.crop;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.annotation.Keep;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.img.crop.permissiongen.PermissionFail;
import com.img.crop.permissiongen.PermissionGen;
import com.img.crop.permissiongen.PermissionSuccess;
import com.img.crop.permissiongen.internal.PermissionUtil;
import com.img.crop.utils.FileUtil;

import java.io.File;
import java.net.URI;

/*
 * Copyright (C) 2017 重庆呼我出行网络科技有限公司
 * 版权所有
 *
 * 功能描述：
 * 作者：huangyong
 * 创建时间：2017/12/29
 *
 * 修改人：
 * 修改描述：
 * 修改日期
 */
public class CropMainActivity extends Activity implements BottomSheet.OnDialogCloseListener, AdapterView.OnItemClickListener, CropConstants {
    private static final int REQUEST_GALLERY_PERMISSION = 1000;
    private static final int REQUEST_CAMERA_PERMISSION = 10001;

    public static final int NEED_DELETE = 1 << 1;// 需要删除按钮
    public static final int NEED_LOOK = 1 << 2;// 需要预览
    public static final int NEED_COVER = 1 << 3; //需要设置封面

    public static final int RESULT_DELETE = RESULT_OK + 1;
    public static final int RESULT_LOOK = RESULT_OK + 2;
    public static final int RESULT_COVER = RESULT_OK + 3;

    private int mWidth = 300;
    private int mHeight = 300;

    private Uri mUri;
    private String mSelection;
    private String[] mSelectionArgs;
    private Dialog mDialog;


    private int mExtraAction;
    private BottomSheet mBottomSheet;
    private Activity mContext;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        if (getIntent() != null) {
            mWidth = getIntent().getIntExtra(KEY_OUTPUT_X, 300);
            mHeight = getIntent().getIntExtra(KEY_OUTPUT_Y, 300);
            mExtraAction = getIntent().getIntExtra(KEY_EXTRA_ACTION, 0);
        }

        showCropImageDialog();
    }

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

    private String[] getActionsTxt(int extraAction) {
        String[] actionTxt = getResources().getStringArray(R.array.selects_pic);
        if ((extraAction | NEED_COVER) != 0 && (extraAction | NEED_LOOK) != 0 && (extraAction | RESULT_DELETE) != 0) {
            actionTxt = getResources().getStringArray(R.array.selects_pic_pdc);
        } else if ((extraAction | NEED_LOOK) != 0 && (extraAction | RESULT_DELETE) != 0) {
            actionTxt = getResources().getStringArray(R.array.selects_pic_pd);
        } else if ((extraAction | NEED_LOOK) != 0) {
            actionTxt = getResources().getStringArray(R.array.selects_pic_p);
        } else if ((extraAction | RESULT_DELETE) != 0) {
            actionTxt = getResources().getStringArray(R.array.selects_pic_d);
        }

        return actionTxt;
    }


    @Override
    public void onCancel() {
        setFailure();
    }

    @Override
    public void onDismiss() {

    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        int eac = mExtraAction;
        if ((eac | NEED_COVER) != 0 && (eac | NEED_LOOK) != 0 && (eac | RESULT_DELETE) != 0) {
            switch (position) {
                case 0:
                    setResult(RESULT_COVER);
                    finish();
                    break;
                case 1:
                    setResult(RESULT_LOOK);
                    finish();
                    break;
                case 2:
                    pickerPhoto();
                    break;
                case 3:
                    takePhoto();
                    break;
                case 4:
                    setResult(RESULT_DELETE);
                    finish();
                    break;
                case 5:
                    setFailure();
                    break;
            }
            //contain preview, delete, set cover
        } else if ((eac | NEED_LOOK) != 0 && (eac | RESULT_DELETE) != 0) {
            //contain preview, delete
            switch (position) {
                case 0:
                    setResult(RESULT_LOOK);
                    finish();
                    break;
                case 1:
                    pickerPhoto();
                    break;
                case 2:
                    takePhoto();
                    break;
                case 3:
                    setResult(RESULT_DELETE);
                    finish();
                    break;
                case 4:
                    setFailure();
                    break;
            }
        } else if ((eac | NEED_LOOK) != 0) {
            //contain preview
            switch (position) {
                case 0:
                    setResult(RESULT_LOOK);
                    finish();
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
        } else if ((eac | RESULT_DELETE) != 0) {
            //contain delete
            switch (position) {
                case 0:
                    pickerPhoto();
                    break;
                case 1:
                    takePhoto();
                    break;
                case 2:
                    setResult(RESULT_DELETE);
                    finish();
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
        mBottomSheet.dismiss();
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
                        finish();
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
                    setResult(RESULT_OK, data);
                    this.finish();
                } else {
                    // 未裁剪
                    setFailure();
                }
                break;
        }
    }


    private void pickerPhoto() {
        Intent intent = new Intent(
                Intent.ACTION_GET_CONTENT,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, CHOOSE_IMAGE);
        } else {
            intent = new Intent(
                    Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(intent, CHOOSE_IMAGE);
            } else {
                //如手机没有找到默认打开图片的应用(比如用户没有安装图库)，让用户自己选择打开图片的应用程序
                intent = new Intent(
                        Intent.ACTION_GET_CONTENT,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                intent.setType("*/*");
                startActivityForResult(intent, CHOOSE_IMAGE);
            }
        }

        if (mDialog != null) {
            mDialog.dismiss();
        }
    }

    /**
     * 拍照
     */
    private void takePhoto() {
        try {
            if (PermissionUtil.isOverMarshmallow()) {
                String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.CAMERA};
                PermissionGen.needPermission(mContext, REQUEST_CAMERA_PERMISSION, permissions);
            } else {
                enterCamera();
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        if (mDialog != null) {
            mDialog.dismiss();
        }
    }

    @TargetApi(19)
    public String getPath(final Context context, final Uri uri) {
        final boolean isKitKat = Build.VERSION.SDK_INT >= 19; //Build.VERSION_CODES.KITKAT
        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
                // TODO handle non-primary volumes
            }

            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                return getDataColumn(context, contentUri, null, null);
            }

            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{split[1]};

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        } else if (ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme())) {
            // MediaStore (and general)
            // Return the remote address
            if (isGooglePhotosUri(uri)) {
                return uri.getLastPathSegment();
            }

            return getDataColumn(context, uri, null, null);
        } else if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(uri.getScheme())) {
            // File
            return uri.getPath();
        }

        return null;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for MediaStore Uris, and other
     * file-based ContentProviders.
     *
     * @param context       The context.
     * @param uri           The Uri to query.
     * @param selection     (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public String getDataColumn(Context context, Uri uri, String selection,
                                String[] selectionArgs) {
        if (!PermissionUtil.isOverMarshmallow()) {
            Cursor cursor = null;
            final String column = "_data";
            final String[] projection = {column};
            try {
                cursor = getContentResolver().query(uri, projection, selection, selectionArgs,
                        null);
                if (cursor != null && cursor.moveToFirst()) {
                    final int index = cursor.getColumnIndexOrThrow(column);
                    return cursor.getString(index);
                }
            } finally {
                if (cursor != null)
                    cursor.close();
            }
            return null;
        } else {
            mUri = uri;
            mSelection = selection;
            mSelectionArgs = selectionArgs;

            PermissionGen.needPermission(mContext, REQUEST_GALLERY_PERMISSION,
                    Manifest.permission.READ_EXTERNAL_STORAGE);
            return null;

        }
    }

    @Keep
    @PermissionSuccess(requestCode = REQUEST_GALLERY_PERMISSION)
    public void getGallerySuccess() {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};
        String path = null;
        try {
            cursor = getContentResolver().query(mUri, projection, mSelection, mSelectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                path = cursor.getString(index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }

        setImgPathToResult(new File(path));
    }

    @Keep
    @PermissionSuccess(requestCode = REQUEST_CAMERA_PERMISSION)
    public void getCameraSuccess() {
        enterCamera();
    }

    @PermissionFail(requestCode = REQUEST_GALLERY_PERMISSION)
    public void getGalleryFailure() {
        if (mDialog != null) {
            mDialog.cancel();
        }
    }

    @PermissionFail(requestCode = REQUEST_CAMERA_PERMISSION)
    public void getCameraFailure() {
        if (mDialog != null) {
            mDialog.cancel();
        }
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

    private void setImgPathToResult(File file) {
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
            finish();
        }
    }

    private void enterCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File outFile = new File(UPLOAD_IMAGE_PATH);
        File parent = outFile.getParentFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(outFile));
        startActivityForResult(intent, TAKE_PHOTO);
    }

    public void setFailure() {
        setResult(RESULT_CANCELED);
        finish();
    }
}
