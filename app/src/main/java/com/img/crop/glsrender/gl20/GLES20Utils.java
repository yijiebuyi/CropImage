package com.img.crop.glsrender.gl20;

import android.opengl.GLES20;

public class GLES20Utils {

	public static int createFramebuffer() {
		int[] framebuffers = new int[1];
		GLES20.glGenFramebuffers(1, framebuffers, 0);
        checkGlError("glGenFramebuffers");
        return framebuffers[0];
	}
	
	public static void deleteFramebuffer(int framebuffer) {
		int[] framebuffers = new int[1];
		framebuffers[0] = framebuffer;
		GLES20.glDeleteFramebuffers(1, framebuffers, 0);
        checkGlError("glDeleteFramebuffer");
	}
	
	public static void setFramebuffer(int framebuffer, int texture) {
		if (framebuffer != 0 && texture != 0) {
	        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
	        checkGlError("glBindFramebuffer");
	        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture, 0);
	        checkGlError("glFramebufferTexture2D");
	        
	        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
	        if(status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
	            throw new RuntimeException("Failed to initialize framebuffer object");
	        }
		} else {
			GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
	        checkGlError("glBindFramebuffer");
		}
	}
	
	public static void clear(float r, float g, float b, float a) {
		GLES20.glClearColor(r, g, b, a);
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
	}
	
	public static void checkGlError(String op) {
		int error;
		while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
			throw new RuntimeException(op + ": glError " + error);
		}
	}
}
