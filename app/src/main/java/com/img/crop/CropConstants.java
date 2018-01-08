package com.img.crop;

import android.os.Environment;

/*
 * Copyright (C) 2017
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
    /**
     * 选择图片后是否需要对图片进行裁剪
     */
    public static final String NEED_CROP = "need-crop";
    /**
     * 裁剪模式
     */
    public static final String CROP_MODE = "crop-mode";
    /**
     * 选择图片的动作
     */
    public static final String KEY_SELECTION_PIC_ACTION = "selection-pic-action";
    /**
     * 裁剪后返回的数据
     */
    public static final String KEY_RETURN_DATA = "return-data";
    /**
     * 裁剪后返回的图片对应的rect
     */
    public static final String KEY_CROPPED_RECT = "cropped-rect";
    /**
     * 待裁剪图片的比例值X方向
     */
    public static final String KEY_ASPECT_X = "aspectX";
    /**
     * 待裁剪图片的比例值Y方向
     */
    public static final String KEY_ASPECT_Y = "aspectY";
    public static final String KEY_SPOTLIGHT_X = "spotlightX";
    public static final String KEY_SPOTLIGHT_Y = "spotlightY";
    /**
     * 裁剪图片输出宽
     */
    public static final String KEY_OUTPUT_X = "outputX";
    /**
     * 裁剪图片输出高
     */
    public static final String KEY_OUTPUT_Y = "outputY";
    /**
     * 输出图片的路径
     */
    public static final String KEY_OUTPUT_PATH = "output-path";
    /**
     * 输出图片尺寸的最大宽
     */
    public static final String KEY_OUTPUT_MAX_X = "outputMaxX";
    /**
     * 输出图片尺寸的最大高
     */
    public static final String KEY_OUTPUT_MAX_Y = "outputMaxY";
    public static final String KEY_SCALE = "scale";
    public static final String KEY_DATA = "data";
    /**
     * 当设置输出裁剪尺寸时(KEY_OUTPUT_X, KEY_OUTPUT_Y), 当实际裁剪的尺寸小于输出裁剪尺寸，
     * 若KEY_SCALE_UP_IF_NEEDED设置为true，保持输出裁剪尺寸和平铺在画布内；否则实际裁剪尺寸
     * 显示在输出裁剪画布内，不足的用黑色填充。
     */
    public static final String KEY_SCALE_UP_IF_NEEDED = "scaleUpIfNeeded";
    /**
     * 输出图片的压缩格式
     */
    public static final String KEY_OUTPUT_FORMAT = "outputFormat";
    /**
     * 是否需要人脸识别
     */
    public static final String KEY_NO_FACE_DETECTION = "noFaceDetection";
    public static final String KEY_RETURN_PATH_IF_TOO_LARGE = "return-path-if-too-large";
    public static final String KEY_FILE_PATH = "filePath";
    public static final String KEY_CONFIRM_OVERWRITE = "confirm-overwrite";
    public static final String KEY_COMPRESS_FORMAT = "compress-format";

    public static final int CHOOSE_IMAGE = 1; // 选择图片
    public static final int TAKE_PHOTO = 2; // 拍照
    public static final int CROP_IMAGE_REQUEST_CODE = 3; // 裁剪


    public static final int MODE_SIMPLE_CROP = 1;
    public static final int MODE_ENHANCE_CROP = 2;
}
