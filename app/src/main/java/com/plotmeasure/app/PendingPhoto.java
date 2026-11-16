package com.plotmeasure.app;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/** Owns one in-memory PNG until it is cancelled or handed to the export worker. No disk cache. */
final class PendingPhoto {
    private byte[] png;
    void prepare(byte[] bytes) { discard(); png = bytes; }
    byte[] take() { byte[] result = png; png = null; return result; }
    void discard() { if (png != null) Arrays.fill(png, (byte) 0); png = null; }
    static void writeAndErase(byte[] bytes, OutputStream output) throws IOException {
        try { output.write(bytes); }
        finally { Arrays.fill(bytes, (byte) 0); }
    }
}
