package com.plotmeasure.app;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Draws the ARCore camera texture; all methods run on the GL thread. */
final class CameraBackground {
    private final FloatBuffer vertices = buffer(new float[]{-1, -1, 1, -1, -1, 1, 1, 1});
    private final FloatBuffer uv = buffer(new float[8]);
    private int program, position, textureCoordinate, sampler;
    int texture;

    void create() {
        int[] names = new int[1];
        GLES20.glGenTextures(1, names, 0);
        texture = names[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        int vertex = compile(GLES20.GL_VERTEX_SHADER,
            "attribute vec2 p; attribute vec2 uv; varying vec2 v; void main(){gl_Position=vec4(p,0.,1.); v=uv;}");
        int fragment = compile(GLES20.GL_FRAGMENT_SHADER,
            "#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES camera; varying vec2 v; void main(){gl_FragColor=texture2D(camera,v);}");
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertex); GLES20.glAttachShader(program, fragment);
        GLES20.glLinkProgram(program);
        int[] ok = new int[1]; GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0);
        GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment);
        if (ok[0] == 0) throw new IllegalStateException("Camera shader link: " + GLES20.glGetProgramInfoLog(program));
        position = GLES20.glGetAttribLocation(program, "p");
        textureCoordinate = GLES20.glGetAttribLocation(program, "uv");
        sampler = GLES20.glGetUniformLocation(program, "camera");
    }
    void draw(Frame frame) {
        if (frame.getTimestamp() == 0) return;
        vertices.position(0); uv.position(0);
        frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, vertices,
            Coordinates2d.TEXTURE_NORMALIZED, uv);
        vertices.position(0); uv.position(0);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture);
        GLES20.glUniform1i(sampler, 0);
        GLES20.glEnableVertexAttribArray(position); GLES20.glEnableVertexAttribArray(textureCoordinate);
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, vertices);
        GLES20.glVertexAttribPointer(textureCoordinate, 2, GLES20.GL_FLOAT, false, 0, uv);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(position); GLES20.glDisableVertexAttribArray(textureCoordinate);
    }
    private static FloatBuffer buffer(float[] values) {
        FloatBuffer result = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        result.put(values).position(0); return result;
    }
    private static int compile(int type, String source) {
        int shader = GLES20.glCreateShader(type); GLES20.glShaderSource(shader, source); GLES20.glCompileShader(shader);
        int[] ok = new int[1]; GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("Camera shader compile: " + GLES20.glGetShaderInfoLog(shader));
        return shader;
    }
}
