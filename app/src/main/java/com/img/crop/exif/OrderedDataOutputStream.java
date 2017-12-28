package com.img.crop.exif;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

class OrderedDataOutputStream extends FilterOutputStream {
    private final ByteBuffer mByteBuffer = ByteBuffer.allocate(4);

    public OrderedDataOutputStream(OutputStream out) {
        super(out);
    }

    public void setByteOrder(ByteOrder order) {
        mByteBuffer.order(order);
    }

    public void writeShort(short value) throws IOException {
        mByteBuffer.rewind();
        mByteBuffer.putShort(value);
        out.write(mByteBuffer.array(), 0, 2);
     }

    public void writeInt(int value) throws IOException {
        mByteBuffer.rewind();
        mByteBuffer.putInt(value);
        out.write(mByteBuffer.array());
    }

    public void writeRational(Rational rational) throws IOException {
        writeInt((int) rational.getNominator());
        writeInt((int) rational.getDenominator());
    }
}
