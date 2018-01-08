package com.img.crop;

import android.graphics.Color;
import android.graphics.RectF;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.img.crop.core.CropView;
import com.img.crop.core.DialogCustomizedCropRatio;

/*
 * Copyright (C) 2017
 * 版权所有
 *
 * 功能描述：多功能的裁剪。支持任意尺寸的裁剪
 * 作者：huangyong
 * 创建时间：2018/1/8
 *
 * 修改人：
 * 修改描述：
 * 修改日期
 */
public class EnhanceCropActivity extends BaseCropActivity implements View.OnClickListener,
        CropView.OnCropSizeChangeListener {
    private LinearLayout mUspeifedRatio;
    private LinearLayout mOneOneRatio;
    private LinearLayout mThreeTwoRatio;
    private LinearLayout mFourThreeRatio;
    private LinearLayout mSixteenNineRatio;
    private LinearLayout mCustomRatio;

    private ImageView mUnspecifiedRatioIcon;
    private ImageView mOneOneRatioIcon;
    private ImageView mThreeTwoRatioIcon;
    private ImageView mFourThreeRatioIcon;
    private ImageView mSixteenNineRatioIcon;
    private ImageView mCustomRatioIcon;

    private TextView mUnspecifiedRatioText;
    private TextView mOneOneRatioText;
    private TextView mThreeTwoRatioText;
    private TextView mFourThreeRatioText;
    private TextView mSixteenNineRatioText;
    private TextView mCustomRatioText;

    private TextView mCropRatioResult;
    private TextView mSaveCrop;
    private TextView mRotate;

    private int mIconHoverColor = Color.argb(255, 49, 164, 229);
    private int mTextColor;

    @Override
    protected View getBottomContainerView() {
        return LayoutInflater.from(this).inflate(R.layout.layout_enhance_crop, null);
    }

    @Override
    protected void initContainerViews(View topParent, View bottomParent) {
        if (bottomParent != null) {
            mIconHoverColor = getResources().getColor(R.color.crop_blue);
            mTextColor = getResources().getColor(R.color.crop_image_ratio_color);

            mCropRatioResult = (TextView) findViewById(R.id.crop_ratio_result);
            mSaveCrop = (TextView) findViewById(R.id.save);
            mRotate = (TextView) findViewById(R.id.rotate);

            mUspeifedRatio = (LinearLayout) findViewById(R.id.unspecified_ratio);
            mOneOneRatio = (LinearLayout) findViewById(R.id.one_one_ratio);
            mThreeTwoRatio = (LinearLayout) findViewById(R.id.three_two_ratio);
            mFourThreeRatio = (LinearLayout) findViewById(R.id.four_three_ratio);
            mSixteenNineRatio = (LinearLayout) findViewById(R.id.sixteen_nine_ratio);
            mCustomRatio = (LinearLayout) findViewById(R.id.custom_ratio);

            mUnspecifiedRatioIcon = (ImageView) findViewById(R.id.unspecified_ratio_icon);
            mOneOneRatioIcon = (ImageView) findViewById(R.id.one_one_ratio_icon);
            mThreeTwoRatioIcon = (ImageView) findViewById(R.id.three_two_ratio_icon);
            mFourThreeRatioIcon = (ImageView) findViewById(R.id.four_three_ratio_icon);
            mSixteenNineRatioIcon = (ImageView) findViewById(R.id.sixteen_nine_ratio_icon);
            mCustomRatioIcon = (ImageView) findViewById(R.id.custom_ratio_icon);

            mUnspecifiedRatioText = (TextView) findViewById(R.id.unspecified_ratio_text);
            mOneOneRatioText = (TextView) findViewById(R.id.one_one_ratio_text);
            mThreeTwoRatioText = (TextView) findViewById(R.id.three_two_ratio_text);
            mFourThreeRatioText = (TextView) findViewById(R.id.four_three_ratio_text);
            mSixteenNineRatioText = (TextView) findViewById(R.id.sixteen_nine_ratio_text);
            mCustomRatioText = (TextView) findViewById(R.id.custom_ratio_text);

            //The default selection
            mUnspecifiedRatioIcon.setSelected(true);
            mUnspecifiedRatioText.setTextColor(mIconHoverColor);

            setViewClickListener();
        }
    }

    @Override
    protected void onViewsCreated() {
        if (mCropView != null) {
            mCropView.setOnCropSizeChangeListener(this);
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.unspecified_ratio
                || id == R.id.one_one_ratio
                || id == R.id.three_two_ratio
                || id == R.id.four_three_ratio
                || id == R.id.sixteen_nine_ratio
                || id == R.id.custom_ratio) {
            //裁剪比例
            float aspectRatio = 1.0f;
            RectF r = mCropView.getCropRectangle();
            final int w = Math.round(r.right) - Math.round(r.left);
            final int h = Math.round(r.bottom) - Math.round(r.top);
            switchCorpRatioState(id);
            switch (id) {
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
                    DialogCustomizedCropRatio mDialog = new DialogCustomizedCropRatio(EnhanceCropActivity.this);
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

            if (id != R.id.custom_ratio) {
                mCropView.setAspectRatio(aspectRatio);
                mCropView.invalidate();
            }
        } else {
            switch (id) {
                case R.id.save:
                    saveCropBitmap();
                    break;
                case R.id.rotate:
                    mCropView.rotateCropFrame();
                    mCropView.setAspectRatio(1.0f / mCropView.getAspectRatio());
                    break;
            }
        }
    }

    private void setViewClickListener() {
        mUspeifedRatio.setOnClickListener(this);
        mOneOneRatio.setOnClickListener(this);
        mThreeTwoRatio.setOnClickListener(this);
        mFourThreeRatio.setOnClickListener(this);
        mSixteenNineRatio.setOnClickListener(this);
        mCustomRatio.setOnClickListener(this);

        mSaveCrop.setOnClickListener(this);
        mRotate.setOnClickListener(this);
    }

    private void switchCorpRatioState(int which) {
        mUnspecifiedRatioIcon.setSelected(false);
        mOneOneRatioIcon.setSelected(false);
        mThreeTwoRatioIcon.setSelected(false);
        mFourThreeRatioIcon.setSelected(false);
        mSixteenNineRatioIcon.setSelected(false);
        mCustomRatioIcon.setSelected(false);

        mUnspecifiedRatioText.setTextColor(mTextColor);
        mOneOneRatioText.setTextColor(mTextColor);
        mThreeTwoRatioText.setTextColor(mTextColor);
        mFourThreeRatioText.setTextColor(mTextColor);
        mSixteenNineRatioText.setTextColor(mTextColor);
        mCustomRatioText.setTextColor(mTextColor);

        switch (which) {
            case R.id.unspecified_ratio:
            case -1:
                mUnspecifiedRatioIcon.setSelected(true);
                mUnspecifiedRatioText.setTextColor(mIconHoverColor);
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

    public void updateCropSize(String str) {
        if (mCropRatioResult != null) {
            mCropRatioResult.setText(str);
        }
    }

    @Override
    public void onCropSizeChanged(int left, int top, int right, int bottom, int w, int h) {
        updateCropSize(w + "x" + h);
    }
}
