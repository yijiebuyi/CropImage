package com.img.crop;

import android.os.Environment;

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
public interface CropConstants {
    public static final String CROP_ACTION = "com.img.crop.Action";
    public static final String NEED_CROP = "need-crop";

    public static final String KEY_EXTRA_ACTION = "extra-action";
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

    public static final String CROP_NEVER = "never_crop";
    public static final String UPLOAD_COMPRESS = "upload_compress"; //上传的图片是否需要压缩

    public static final int CHOOSE_IMAGE = 1; // 选择图片
    public static final int TAKE_PHOTO = 2; // 拍照
    public static final int CROP_IMAGE_REQUEST_CODE = 3; // 裁剪

    public static final int SELECT_PIC_REQUEST = 2000;    // 剪切图片的requestcode
    public static final String UPLOAD_IMAGE_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/crop/image_upload.jpg"; // 选择图片地址

    public static final String CROP_IMAGE_PATH_TAG = "cropImagePath"; // 返回图片intent

    public static final String CROP_IMAGE_PATH = "/crop/crop_image"; // 剪切后的图片地址
}
