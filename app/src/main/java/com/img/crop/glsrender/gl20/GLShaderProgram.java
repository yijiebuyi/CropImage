package com.img.crop.glsrender.gl20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import android.opengl.GLES20;

public class GLShaderProgram {

	private static final int FLOAT_SIZE_BYTES = 4;
	public static final float[] POS_VERTICES = { -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f };
	public static final float[] TEX_VERTICES = {  0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f };
	
	protected int mProgram;
	protected int mPosCoordHandle;
	protected int mTexCoordHandle;
	protected FloatBuffer mPosVertices;
	protected FloatBuffer mTexVertices;
	protected boolean mIsRendering = false;
	
	public GLShaderProgram(String vs, String fs) {
		mProgram = createProgram(vs, fs);
		if (mProgram != 0) {
			mPosCoordHandle = GLES20.glGetAttribLocation(mProgram, "a_position");
			mTexCoordHandle = GLES20.glGetAttribLocation(mProgram, "a_texcoord");
		}
	}
	
	public void setPosVertices(float[] posVertices) {
		if (posVertices != null) {
			mPosVertices = createVerticesBuffer(posVertices, FLOAT_SIZE_BYTES);
		}
	}
	
	public void setTexVertices(float[] texVertices) {
		if (texVertices != null) {
			mTexVertices = createVerticesBuffer(texVertices, FLOAT_SIZE_BYTES);
		}
	}
	
	public void setViewport(int x, int y, int width, int height) {
		if (mProgram != 0 && mIsRendering) {
			GLES20.glViewport(x, y, width, height);
			checkGlError("glViewport");
		}
	}
	
	public void setRenderTarget(String name, int index, int texture, int target) {
		if (mProgram != 0 && mIsRendering) {
			GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + index);
			checkGlError("glActiveTexture");
			GLES20.glBindTexture(target, texture);
			checkGlError("glBindTexture");
			int varHandle = GLES20.glGetUniformLocation(mProgram, name);
			GLES20.glUniform1i(varHandle, index);
		}
	}
	
	public void setHostValue(String variableName, float obj) {
		if (mProgram != 0 && variableName != null && variableName != "") {
			int varHandle = GLES20.glGetUniformLocation(mProgram, variableName);
			GLES20.glUniform1f(varHandle, obj);
		}
	}

	public boolean beginScene() {
		if (mProgram != 0) {
			GLES20.glUseProgram(mProgram);
			checkGlError("glUseProgram");

			if (mPosVertices == null) {
				mPosVertices = createVerticesBuffer(POS_VERTICES, FLOAT_SIZE_BYTES);
			}
			GLES20.glVertexAttribPointer(mPosCoordHandle, 2, GLES20.GL_FLOAT, false, 0, mPosVertices);
			GLES20.glEnableVertexAttribArray(mPosCoordHandle);
			checkGlError("vertex attribute setup");
			
			mIsRendering = true;
			mIsRendering = true;
		}
		return mIsRendering;
	}
	
	public void endScene() {
		if (mIsRendering) {
			if (mTexVertices == null) {
				mTexVertices = createVerticesBuffer(TEX_VERTICES, FLOAT_SIZE_BYTES);
			}
			GLES20.glVertexAttribPointer(mTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, mTexVertices);
			GLES20.glEnableVertexAttribArray(mTexCoordHandle);
			checkGlError("vertex attribute setup");
			
			GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
			checkGlError("glDrawArrays");
			mIsRendering = false;
		}
	}
	
	public void release() {
		if (mProgram != 0) {
			GLES20.glDeleteProgram(mProgram);
			checkGlError("glDeleteProgram");
			mProgram = 0;
		}
	}
	
	protected static int loadShader(int shaderType, String source) {
		int shader = GLES20.glCreateShader(shaderType);
		if (shader != 0) {
			GLES20.glShaderSource(shader, source);
			GLES20.glCompileShader(shader);
			int[] compiled = new int[1];
			GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
			if (compiled[0] == 0) {
				String info = GLES20.glGetShaderInfoLog(shader);
				GLES20.glDeleteShader(shader);
				shader = 0;
				throw new RuntimeException("Could not compile shader " + shaderType + ":" + info);
			}
		}
		return shader;
	}

	protected static int createProgram(String vs, String fs) {
		int vertexShaderHandle = loadShader(GLES20.GL_VERTEX_SHADER, vs);
		int fragmentShaderHandle = loadShader(GLES20.GL_FRAGMENT_SHADER, fs);
		int program = GLES20.glCreateProgram();
		if (program != 0) {
			GLES20.glAttachShader(program, vertexShaderHandle);
			checkGlError("glAttachShader vertexShaderHandle");
			GLES20.glAttachShader(program, fragmentShaderHandle);
			checkGlError("glAttachShader fragmentShaderHandle");
			GLES20.glLinkProgram(program);
			int[] linkStatus = new int[1];
			GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
			if (linkStatus[0] != GLES20.GL_TRUE) {
				String info = GLES20.glGetProgramInfoLog(program);
				GLES20.glDeleteProgram(program);
				program = 0;
				throw new RuntimeException("Could not link program: " + info);
			}
		}
		return program;
	}

	protected static FloatBuffer createVerticesBuffer(float[] vertices, int bytes) {
		FloatBuffer buffer = ByteBuffer.allocateDirect(vertices.length * bytes)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		buffer.put(vertices).position(0);
		return buffer;
	}

	protected static void checkGlError(String op) {
		int error;
		while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
			throw new RuntimeException(op + ": glError " + error);
		}
	}
}
