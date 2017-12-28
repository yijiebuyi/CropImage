package com.img.crop;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;

public class ProgressHUD {

    private ProgressDialog progressDialog;

    private ProgressHUD(Context context) {
        progressDialog = new ProgressDialog(context);

    }

    /**
     * 创建 ProgressHUD
     *
     * @param context
     * @return
     */
    public static ProgressHUD create(Context context) {
        return new ProgressHUD(context).setCancellable(false);
    }

    public ProgressHUD setCancellable(boolean isCancellable) {
        progressDialog.setCancelable(isCancellable);
        return this;
    }


    public ProgressHUD show() {
        if (!isShowing()) {
            progressDialog.show();
        }
        return this;
    }

    public boolean isShowing() {
        return progressDialog != null && progressDialog.isShowing();
    }

    public void dismiss() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }


    private class ProgressDialog extends Dialog {

        public ProgressDialog(Context context) {
            super(context);
        }


        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            requestWindowFeature(Window.FEATURE_NO_TITLE);
            LoadingView loadingView = new LoadingView(getContext());
            setContentView(loadingView);

            Window window = getWindow();
            window.setBackgroundDrawable(new ColorDrawable(0));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.dimAmount = 0.3f;
            layoutParams.gravity = Gravity.CENTER;
            window.setAttributes(layoutParams);

            setCanceledOnTouchOutside(false);
        }

    }
}
