package com.img.crop;

/*
 * 版权所有
 *
 * 功能描述：
 * 作者：huangyong
 * 创建时间：2017/12/27
 *
 * 修改人：
 * 修改描述：
 * 修改日期
 */
public class MediaItem {
    public final static int TYPE_THUMBNAIL = 0;
    public final static int TYPE_MICROTHUMBNAIL = 1;

    private String mimeType;
    private int rotation;

    public String filePath;

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public int getRotation() {
        return rotation;
    }

    public void setRotation(int rotation) {
        this.rotation = rotation;
    }
}
