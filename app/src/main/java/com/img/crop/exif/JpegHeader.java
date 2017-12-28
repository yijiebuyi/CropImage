package com.img.crop.exif;

public class JpegHeader {
	public static final short SOI =  (short) 0xFFD8;
    public static final short APP1 = (short) 0xFFE1;
    public static final short APP0 = (short) 0xFFE0;
    public static final short EOI = (short) 0xFFD9;

    /**
     *  SOF (start of frame). All value between SOF0 and SOF15 is SOF marker except for DHT, JPG,
     *  and DAC marker.
     */
    public static final short SOF0 = (short) 0xFFC0;
    public static final short SOF15 = (short) 0xFFCF;
    public static final short DHT = (short) 0xFFC4;
    public static final short JPG = (short) 0xFFC8;
    public static final short DAC = (short) 0xFFCC;

    public static final boolean isSofMarker(short marker) {
        return marker >= SOF0 && marker <= SOF15 && marker != DHT && marker != JPG
                && marker != DAC;
    }
}
