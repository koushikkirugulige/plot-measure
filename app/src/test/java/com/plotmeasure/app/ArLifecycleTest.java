package com.plotmeasure.app;

import static org.junit.Assert.*;
import org.junit.Test;

public class ArLifecycleTest {
    private static final class Camera implements ArLifecycle.Driver {
        int starts, stops; boolean available = true;
        public boolean start() { starts++; return available; }
        public void stop() { stops++; }
    }
    private final Camera camera = new Camera();
    private final ArLifecycle lifecycle = new ArLifecycle(camera);
    @Test public void cameraRunsOnlyInForegroundAndStopsOnceWhenLeaving() {
        lifecycle.retry(); assertEquals(0, camera.starts);
        lifecycle.foreground(true); lifecycle.retry(); assertEquals(1, camera.starts);
        lifecycle.foreground(false); lifecycle.foreground(false);
        assertEquals(1, camera.stops); assertFalse(lifecycle.running());
    }
    @Test public void manualPauseSurvivesLockAndUnlockUntilExplicitResume() {
        lifecycle.foreground(true); lifecycle.paused(true);
        lifecycle.foreground(false); lifecycle.foreground(true);
        assertEquals(1, camera.starts); assertFalse(lifecycle.running());
        lifecycle.paused(false); assertEquals(2, camera.starts); assertTrue(lifecycle.running());
    }
    @Test public void restoredManualPauseDoesNotStartCameraOnForeground() {
        lifecycle.paused(true); lifecycle.foreground(true); lifecycle.busy(true); lifecycle.busy(false);
        assertEquals(0, camera.starts); lifecycle.paused(false); assertEquals(1, camera.starts);
    }
    @Test public void photoExportDoesNotRestartCameraBehindFilePicker() {
        lifecycle.foreground(true); lifecycle.busy(true); lifecycle.foreground(false);
        lifecycle.busy(false); assertEquals(1, camera.starts); assertFalse(lifecycle.running());
        lifecycle.foreground(true); assertTrue(lifecycle.running());
    }
    @Test public void returningFromPickerWaitsForExportBeforeRestarting() {
        lifecycle.foreground(true); lifecycle.busy(true); lifecycle.foreground(false); lifecycle.foreground(true);
        assertEquals(1, camera.starts); lifecycle.busy(false); assertEquals(2, camera.starts);
    }
    @Test public void failureAndDestructionStopCameraAndPreventRestart() {
        lifecycle.foreground(true); lifecycle.fail(); lifecycle.retry();
        assertEquals(1, camera.stops); assertFalse(lifecycle.running());
        lifecycle.destroy(); lifecycle.foreground(true); assertEquals(1, camera.starts);
    }
    @Test public void permissionOrArInstallCanBeRetriedWithoutUnmatchedStop() {
        camera.available = false; lifecycle.foreground(true); lifecycle.foreground(false);
        assertEquals(0, camera.stops);
        camera.available = true; lifecycle.foreground(true); assertTrue(lifecycle.running());
    }
}
