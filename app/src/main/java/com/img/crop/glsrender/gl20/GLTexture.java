package com.img.crop.glsrender.gl20;

import java.nio.ByteBuffer;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;

public class GLTexture {

	private int mTarget;
	private int mTexture;
	private int mWidth;
	private int mHeight;

	public GLTexture(int target, int texture) {
		mTarget = target;
		mTexture = texture;
		mWidth = 0;
		mHeight = 0;
	}

	public GLTexture(int target, int texture, int width, int height) {
		mTarget = target;
		mTexture = texture;
		mWidth = width;
		mHeight = height;
	}

	public static int createTexture() {
		int[] textures = new int[1];
		GLES20.glGenTextures(textures.length, textures, 0);
		checkGlError("createTextureId");
		return textures[0];
	}

	public static GLTexture createPhotoTexture(int width, int height) {
		int texture = createTexture();

		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
		checkGlError("glBindTexture");

		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
		checkGlError("glTexParameteri");

		GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
		checkGlError("glTexImage2D");
		
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
		
		GLTexture photo = new GLTexture(GLES20.GL_TEXTURE_2D, texture, width, height);
		return photo;
	}

	public static GLTexture createTextureFromBitmap(Bitmap bitmap) {
		if (bitmap == null) {
			return null;
		}
		int texture = createTexture();
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
		checkGlError("glBindTexture");

		GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
		checkGlError("texImage2D");

		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
		checkGlError("glTexParameteri");
		
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

		GLTexture photo = new GLTexture(GLES20.GL_TEXTURE_2D, texture, bitmap.getWidth(), bitmap.getHeight());
		return photo;
	}
	
	public Bitmap saveTexture(int x, int y, int width, int height) {
		int[] frame = new int[1];
        GLES20.glGenFramebuffers(1, frame, 0);
        checkGlError("glGenFramebuffers");
        
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frame[0]);
        checkGlError("glBindFramebuffer");
        
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, mTexture, 0);
        checkGlError("glFramebufferTexture2D");

        ByteBuffer buffer = ByteBuffer.allocate(width * height * 4);
        GLES20.glReadPixels(x, y, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
        checkGlError("glReadPixels");
        
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        checkGlError("glBindFramebuffer");
        GLES20.glDeleteFramebuffers(1, frame, 0);
        checkGlError("glDeleteFramebuffer");
        return bitmap;
	}
	
	public int[] getData() {
		int[] frame = new int[1];
        GLES20.glGenFramebuffers(1, frame, 0);
        checkGlError("glGenFramebuffers");
        
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frame[0]);
        checkGlError("glBindFramebuffer");
        
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, mTexture, 0);
        checkGlError("glFramebufferTexture2D");

        ByteBuffer buffer = ByteBuffer.allocate(mWidth * mHeight * 4);
        GLES20.glReadPixels(0, 0, mWidth, mHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
        checkGlError("glReadPixels");

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        checkGlError("glBindFramebuffer");
        GLES20.glDeleteFramebuffers(1, frame, 0);
        checkGlError("glDeleteFramebuffer");
        
        if (buffer != null) {
        	int[] data = new int[mWidth * mHeight];
        	if (data != null) {
        		buffer.asIntBuffer().get(data);
        		return data;
        	}
        }
        return null;
	}
	
	public Bitmap saveTexture() {
		return saveTexture(0, 0, mWidth, mHeight);
	}

	public int getTarget() {
		return mTarget;
	}

	public int getTexture() {
		return mTexture;
	}

	public int getWidth() {
		return mWidth;
	}

	public int getHeight() {
		return mHeight;
	}

	public void setWidth(int width) {
		mWidth = width;
	}

	public void setHeight(int height) {
		mHeight = height;
	}
	
	public boolean isRecyle() {
		return mTexture == 0;
	}
	
	public void recyle() {
		int[] textures = new int[1];
        textures[0] = mTexture;
        GLES20.glDeleteTextures(textures.length, textures, 0);
        checkGlError("glDeleteTextures");
        mTexture =  0;
	}

	public static void checkGlError(String op) {
		int error;
		while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
			throw new RuntimeException(op + ": glError " + error);
		}
	}
}
