package com.img.crop;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

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
public class ActionAdapter extends BaseAdapter {
    private String[] mActions;
    private Context mContext;

    public ActionAdapter(Context context, String[] actions) {
        mContext = context;
        mActions = actions;
    }

    @Override
    public int getCount() {
        return mActions == null ? 0 : mActions.length;
    }

    @Override
    public Object getItem(int position) {
        return mActions == null ? null : mActions[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.crop_img_action_item, null);
        }

        ((TextView) convertView).setText(mActions[position]);
        return convertView;
    }
}
