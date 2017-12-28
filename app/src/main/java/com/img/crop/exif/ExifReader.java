package com.img.crop.exif;

import java.io.IOException;
import java.io.InputStream;

public class ExifReader {
    /**
     * Parses the inputStream and  and returns the EXIF data in an {@link ExifData}.
     * @throws ExifInvalidFormatException
     * @throws IOException
     */
    public ExifData read(InputStream inputStream) throws ExifInvalidFormatException,
            IOException {
        ExifParser parser = ExifParser.parse(inputStream);
        ExifData exifData = new ExifData(parser.getByteOrder());

        int event = parser.next();
        while (event != ExifParser.EVENT_END) {
            switch (event) {
                case ExifParser.EVENT_START_OF_IFD:
                    exifData.addIfdData(new IfdData(parser.getCurrentIfd()));
                    break;
                case ExifParser.EVENT_NEW_TAG:
                    ExifTag tag = parser.getTag();
                    if (!tag.hasValue()) {
                        parser.registerForTagValue(tag);
                    } else {
                        exifData.getIfdData(tag.getIfd()).setTag(tag);
                    }
                    break;
                case ExifParser.EVENT_VALUE_OF_REGISTERED_TAG:
                    tag = parser.getTag();
                    if (tag.getDataType() == ExifTag.TYPE_UNDEFINED) {
                        byte[] buf = new byte[tag.getComponentCount()];
                        parser.read(buf);
                        tag.setValue(buf);
                    }
                    exifData.getIfdData(tag.getIfd()).setTag(tag);
                    break;
                case ExifParser.EVENT_COMPRESSED_IMAGE:
                    byte buf[] = new byte[parser.getCompressedImageSize()];
                    parser.read(buf);
                    exifData.setCompressedThumbnail(buf);
                    break;
                case ExifParser.EVENT_UNCOMPRESSED_STRIP:
                    buf = new byte[parser.getStripSize()];
                    parser.read(buf);
                    exifData.setStripBytes(parser.getStripIndex(), buf);
                    break;
            }
            event = parser.next();
        }
        return exifData;
    }
}
