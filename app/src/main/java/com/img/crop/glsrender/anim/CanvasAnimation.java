package com.img.crop.glsrender.anim;


import com.img.crop.glsrender.gl11.GLCanvas;

public abstract class CanvasAnimation extends Animation {

    public abstract int getCanvasSaveFlags();
    public abstract void apply(GLCanvas canvas);
}

