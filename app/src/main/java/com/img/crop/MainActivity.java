package com.img.crop;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.ImageView;

import com.img.crop.core.TileImageView;

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
public class MainActivity extends Activity {
    ImageView mTileImageView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        mTileImageView = (ImageView) findViewById(R.id.img_view);

        findViewById(R.id.select_pic).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, SelectionPictureActivity.class);
                i.putExtra(CropConstants.NEED_CROP, true);
                startActivityForResult(i, 1024);
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
                        Bitmap bitmap = BitmapFactory.decodeFile(uri.getPath());
                        mTileImageView.setImageBitmap(bitmap);
                    }
                    break;
            }
        }
    }
}
