package com.img.crop;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ToggleButton;

import static com.img.crop.CropConstants.CROP_ACTION;

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
public class MainActivity extends Activity {
    ImageView mImageView;
    ToggleButton mCropSwitcher;
    RadioGroup mCropModeBtn;
    RadioButton mSimpleCropBtn;
    RadioButton mEnhanceCropBtn;


    int mCropMode = CropConstants.MODE_SIMPLE_CROP;
    boolean mNeedCrop =  true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        mImageView = (ImageView) findViewById(R.id.img_view);
        mCropSwitcher = (ToggleButton) findViewById(R.id.crop_switcher);
        mCropModeBtn = (RadioGroup) findViewById(R.id.crop_mode);
        mSimpleCropBtn = (RadioButton) findViewById(R.id.simple_crop);
        mEnhanceCropBtn = (RadioButton) findViewById(R.id.enhance_crop);

        mSimpleCropBtn.setChecked(true);

        findViewById(R.id.select_pic).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, SelectionPictureActivity.class);
                i.putExtra(CropConstants.NEED_CROP, mNeedCrop);
                i.putExtra(CropConstants.CROP_MODE, mCropMode);
                startActivityForResult(i, 1024);
            }
        });


        mCropSwitcher.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mNeedCrop = isChecked;
                mCropModeBtn.setVisibility(isChecked ? View.VISIBLE : View.INVISIBLE);
            }
        });

        mCropModeBtn.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.simple_crop:
                        mCropMode = CropConstants.MODE_SIMPLE_CROP;
                        break;
                    case R.id.enhance_crop:
                        mCropMode = CropConstants.MODE_ENHANCE_CROP;
                        break;
                }
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (data != null) {
            switch (requestCode) {
                case 1024:
                    Uri uri = data.getData();
                    if (uri != null) {
                        //Bitmap bitmap = BitmapFactory.decodeFile(uri.getPath());
                        //mImageView.setImageBitmap(bitmap);


                        Intent intent = new Intent(CROP_ACTION, uri);
                        intent.setClass(this, ImageViewActivity.class);
                        startActivity(intent);
                    }
                    break;
            }
        }
    }
}
