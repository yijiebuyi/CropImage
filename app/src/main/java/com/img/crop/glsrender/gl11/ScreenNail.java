package com.img.crop.glsrender.gl11;

import android.graphics.RectF;

public interface ScreenNail {
    public int getWidth();
    public int getHeight();
    public void draw(GLCanvas canvas, int x, int y, int width, int height);

    // We do not need to draw this ScreenNail in this frame.
    public void noDraw();

    // This ScreenNail will not be used anymore. Release related resources.
    public void recycle();

    // This is only used by TileImageView to back up the tiles not yet loaded.
    public void draw(GLCanvas canvas, RectF source, RectF dest);
    
    public boolean isAnimating();
    
    public boolean isCamera();
}
