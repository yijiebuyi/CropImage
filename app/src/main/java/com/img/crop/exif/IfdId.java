package com.img.crop.exif;

public interface IfdId {
    public static final int TYPE_IFD_0 = 0;
    public static final int TYPE_IFD_1 = 1;
    public static final int TYPE_IFD_EXIF = 2;
    public static final int TYPE_IFD_INTEROPERABILITY = 3;
    public static final int TYPE_IFD_GPS = 4;
    /* This is use in ExifData to allocate enough IfdData */
    static final int TYPE_IFD_COUNT = 5;
}
