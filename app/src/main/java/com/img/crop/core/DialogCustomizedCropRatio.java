package com.img.crop.core;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.img.crop.R;

public class DialogCustomizedCropRatio {
    private Context mContext;
    private EditText mCropWidthEdit;
    private EditText mCropHeightEdit;
    private ImageView mCropRatioLockBtn;
    private boolean isRatioLocked = false;

    private int mImageW;
    private int mImageH;
    private int mNewWidth;
    private int mNewHeight;
    private int mDefaultImageW;
    private int mDefaultImageH;

    private AlertDialog mDialog;
    private ICustomizedCropSizeListener mICustomizedCropSizeListener;

    public DialogCustomizedCropRatio(Context context) {
        mContext = context;
    }

    public void show() {
        loadDialogView();
        mCropWidthEdit.requestFocus();
        //AlertDialogProxy.setAutoShowSoftInput(mDialog, true);
        mDialog.show();
    }

    public void hide() {
        mDialog.hide();
    }

    private void loadDialogView() {
        LayoutInflater layoutInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View contentView = layoutInflater.inflate(R.layout.dialog_customize_crop_layout, null);

        mCropWidthEdit = (EditText) contentView.findViewById(R.id.cropsize_edit_width);
        mCropWidthEdit.setText(mDefaultImageW + "");
        mCropWidthEdit.setHint(mDefaultImageW + "");
        //EditTextProxy.setEllipsisSmall(mCropWidthEdit, true);
        mCropWidthEdit.requestFocus();
        mCropWidthEdit.setSelectAllOnFocus(true);
        mCropWidthEdit.addTextChangedListener(mWidthEditWatcher);

        mCropHeightEdit = (EditText) contentView.findViewById(R.id.cropsize_edit_height);
        mCropHeightEdit.setText(mDefaultImageH + "");
        mCropHeightEdit.setHint(mDefaultImageH + "");
        //EditTextProxy.setEllipsisSmall(mCropHeightEdit, true);
        mCropHeightEdit.requestFocus();
        mCropHeightEdit.setSelectAllOnFocus(true);
        mCropHeightEdit.addTextChangedListener(mHeightEditWatcher);
        
        mCropRatioLockBtn = (ImageView)contentView.findViewById(R.id.lock_crop_ratio);
        showRatioLockIcon();
        mCropRatioLockBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isRatioLocked = isRatioLocked ? false : true;
                showRatioLockIcon();
            }
        });

        mDialog = new AlertDialog.Builder((Activity) mContext)
                .setView(contentView)
                .setNegativeButton(mContext.getString(R.string.cancel), null)
                .setPositiveButton(mContext.getString(R.string.confirm), mPositiveBtnClick)
                .create();

        mDialog.setCanceledOnTouchOutside(false);
        //AlertDialogProxy.setButtonClickDismiss(mDialog, DialogInterface.BUTTON_NEGATIVE, true);
        //AlertDialogProxy.setButtonClickDismiss(mDialog, DialogInterface.BUTTON_POSITIVE, false);
    }
    
    private void showRatioLockIcon() {
        if(isRatioLocked) {
            mCropRatioLockBtn.setImageResource(R.drawable.btn_lock_on);
        } else {
            mCropRatioLockBtn.setImageResource(R.drawable.btn_lock_off);
        }
    }

    private TextWatcher mWidthEditWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count,
                                      int after) {
        }
        @Override
        public void onTextChanged(CharSequence s, int start, int before,
                                  int count) {
            if (!mCropWidthEdit.hasFocus()) {
                return;
            }
            try {
                mNewWidth = Integer.parseInt(s.toString());
                if (isRatioLocked) {
                    if (mNewWidth <= mImageW) {
                        mNewHeight = mDefaultImageH * mNewWidth / mDefaultImageW;
                        mCropHeightEdit.setText(Long.toString(mNewHeight));
                    }
                }
            } catch (Exception e) {
                return;
            }
        }
        @Override
        public void afterTextChanged(Editable s) {
            
        }
    };

    private TextWatcher mHeightEditWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count,
                                      int after) {
        }
        @Override
        public void onTextChanged(CharSequence s, int start, int before,
                                  int count) {
            if (!mCropHeightEdit.hasFocus()) {
                return;
            }
            try {
                mNewHeight = Integer.parseInt(s.toString());
                if (isRatioLocked) {
                    if (mNewHeight <= mImageH) {
                        mNewWidth = mDefaultImageW * mNewHeight / mDefaultImageH;
                        mCropWidthEdit.setText(Long.toString(mNewWidth));
                    }
                }
            } catch (Exception e) {
                return;
            }
        }
        @Override
        public void afterTextChanged(Editable s) {
        }
    };

    private DialogInterface.OnClickListener mPositiveBtnClick = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (mICustomizedCropSizeListener != null) {
                if (mNewWidth < 1) {
                    showToast(R.string.crop_width_unacceptable_minsize,"");
                    return;
                }
                if (mNewHeight < 1) {
                    showToast(R.string.crop_height_unacceptable_minsize,"");
                    return;
                }
                if (mNewWidth > mImageW) {
                    showToast(R.string.crop_width_exceed,mImageW + "");
                    return;
                }
                if (mNewHeight > mImageH) {
                    showToast(R.string.crop_height_exceed,mImageH + "");
                    return;
                }
                mICustomizedCropSizeListener.cropSize(mNewWidth, mNewHeight);
                mDialog.dismiss();
            }
        }
    };

    public int getmNewWidth() {
        return mNewWidth;
    }

    public int getmNewHeight() {
        return mNewHeight;
    }

    public void setDefaultCropImageSize(int w, int h) {
        mNewWidth = mDefaultImageW = w;
        mNewHeight = mDefaultImageH = h;
    }

    public void setImageSize(int w, int h) {
        mImageW = w;
        mImageH = h;
    }

    public void setmICustomizedCropSizeListener(
            ICustomizedCropSizeListener mICustomizedCropSizeListener) {
        this.mICustomizedCropSizeListener = mICustomizedCropSizeListener;
    }

    public interface ICustomizedCropSizeListener {
        public void cropSize(int cropWidth, int cropHeight);
    }
    
    private void showToast(int messageId, String tip) {
        Toast.makeText(mContext, mContext.getResources().getString(messageId, tip), Toast.LENGTH_SHORT).show();
        //SlideNotice.makeNotice(mContext, mContext.getResources().getString(messageId, tip), SlideNotice.NOTICE_TYPE_FAILURE, SlideNotice.LENGTH_SHORT).show();
    }
}