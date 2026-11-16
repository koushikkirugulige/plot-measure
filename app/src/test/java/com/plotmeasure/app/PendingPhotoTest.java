package com.plotmeasure.app;

import static org.junit.Assert.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.junit.Test;

public class PendingPhotoTest {
    @Test public void cancellingDiscardsPhotoAndErasesOwnedBuffer() {
        PendingPhoto p = new PendingPhoto(); byte[] bytes = {1,2,3}; p.prepare(bytes); p.discard();
        assertNull(p.take()); assertArrayEquals(new byte[3], bytes);
    }
    @Test public void newCaptureDiscardsOldPhoto() {
        PendingPhoto p = new PendingPhoto(); byte[] old = {1,2}; byte[] next = {3,4};
        p.prepare(old); p.prepare(next); assertArrayEquals(new byte[2], old);
        assertSame(next, p.take()); assertNull(p.take());
    }
    @Test public void exportTransfersBytesAndErasesThemAfterWriting() throws Exception {
        PendingPhoto p = new PendingPhoto(); p.prepare(new byte[]{1,2,3}); byte[] owned = p.take();
        p.discard(); ByteArrayOutputStream out = new ByteArrayOutputStream(); PendingPhoto.writeAndErase(owned, out);
        assertArrayEquals(new byte[]{1,2,3}, out.toByteArray()); assertArrayEquals(new byte[3], owned);
    }
    @Test public void failedExportAlsoErasesBuffer() {
        byte[] bytes = {1,2,3};
        OutputStream failure = new OutputStream() { public void write(int value) throws IOException { throw new IOException("provider unavailable"); } };
        assertThrows(IOException.class, () -> PendingPhoto.writeAndErase(bytes, failure));
        assertArrayEquals(new byte[3], bytes);
    }
}
