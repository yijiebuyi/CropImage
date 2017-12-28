package com.img.crop.glsrender.gl11;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.img.crop.utils.Utils;

public class ResourceTexture extends UploadedTexture {

    protected final Context mContext;
    protected final int mResId;

    public ResourceTexture(Context context, int resId) {
        mContext = Utils.checkNotNull(context);
        mResId = resId;
        setOpaque(false);
    }

    @Override
    protected Bitmap onGetBitmap() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeResource(
                mContext.getResources(), mResId, options);
    }

    @Override
    protected void onFreeBitmap(Bitmap bitmap) {
        if (!inFinalizer()) {
            bitmap.recycle();
        }
    }
}
