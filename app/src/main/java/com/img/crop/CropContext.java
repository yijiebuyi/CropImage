package com.img.crop;

import android.content.Context;
import android.content.res.Resources;
import android.os.Looper;

import com.img.crop.thdpool.ThreadPool;

public interface CropContext {
	public Context getAndroidContext();

	public Looper getMainLooper();

	public Resources getResources();

	public ThreadPool getThreadPool();

	public static final int INTENT_IMAGE_MAIN = 0;
	public static final int INTENT_VIDEO_MAIN = 1;
	public static final int INTENT_CROP = 2;
	public static final int INTENT_ALBUM_PICKER = 3;
	public static final int INTENT_DIALOG_PICKER = 4;
	public static final int INTENT_ACTION_VIEW = 5;
	public static final int INTENT_ACTION_GET_CONTENT = 6;
	public static final int INTENT_VIEW = 7;
	public static final int INTENT_CAMERA_PHOTO = 8;
	public static final int INTENT_CAMERA_RECORD = 9;
	public static final int INTENT_CONTACT_GET_ICON = 10;
	public static final int INTENT_MMS_VIEW_PHOTO = 11;
	public static final int INTENT_EMAIL_VIEW_PHOTO = 12;
	public static final int INTENT_NOTEPAER_VIEW_PHOTO = 13;
	public static final int INTENT_CAMERA_VIEW = 14;

	public static final int INTENT_FILETER_ALL = 0;
	public static final int INTENT_FILETER_IMAGE = 1;
	public static final int INTENT_FILETER_VIDEO = 2;
}
