package com.img.crop.glsrender.gl11;

import android.graphics.Bitmap;
import android.graphics.RectF;

public class BitmapScreenNail implements ScreenNail {
    private final BitmapTexture mBitmapTexture;

    public BitmapScreenNail(Bitmap bitmap) {
        mBitmapTexture = new BitmapTexture(bitmap);
    }

    @Override
    public int getWidth() {
        return mBitmapTexture.getWidth();
    }

    @Override
    public int getHeight() {
        return mBitmapTexture.getHeight();
    }

    @Override
    public void draw(GLCanvas canvas, int x, int y, int width, int height) {
        mBitmapTexture.draw(canvas, x, y, width, height);
    }

    @Override
    public void noDraw() {
    }

    @Override
    public void recycle() {
        mBitmapTexture.recycle();
    }

    @Override
    public void draw(GLCanvas canvas, RectF source, RectF dest) {
        canvas.drawTexture(mBitmapTexture, source, dest);
    }

	@Override
	public boolean isAnimating() {
		return false;
	}
	
	@Override
	public boolean isCamera() {
		return false;
	}
}
