package com.img.crop.utils;


import com.img.crop.utils.Utils;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;

public class InterruptableOutputStream extends OutputStream {

    private static final int MAX_WRITE_BYTES = 4096;

    private OutputStream mOutputStream;
    private volatile boolean mIsInterrupted = false;

    public InterruptableOutputStream(OutputStream outputStream) {
        mOutputStream = Utils.checkNotNull(outputStream);
    }

    @Override
    public void write(int oneByte) throws IOException {
        if (mIsInterrupted) throw new InterruptedIOException();
        mOutputStream.write(oneByte);
    }

    @Override
    public void write(byte[] buffer, int offset, int count) throws IOException {
        int end = offset + count;
        while (offset < end) {
            if (mIsInterrupted) throw new InterruptedIOException();
            int bytesCount = Math.min(MAX_WRITE_BYTES, end - offset);
            mOutputStream.write(buffer, offset, bytesCount);
            offset += bytesCount;
        }
    }

    @Override
    public void close() throws IOException {
        mOutputStream.close();
    }

    @Override
    public void flush() throws IOException {
        if (mIsInterrupted) throw new InterruptedIOException();
        mOutputStream.flush();
    }

    public void interrupt() {
        mIsInterrupted = true;
    }
}

