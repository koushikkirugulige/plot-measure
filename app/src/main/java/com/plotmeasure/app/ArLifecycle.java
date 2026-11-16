package com.plotmeasure.app;

/** Main-thread owner of whether the camera is allowed to run. Driver preserves pause/resume order. */
final class ArLifecycle {
    interface Driver { boolean start(); void stop(); }
    private final Driver driver;
    private boolean foreground, paused, busy, failed, running;
    ArLifecycle(Driver driver) { this.driver = driver; }
    boolean foreground() { return foreground; }
    boolean paused() { return paused; }
    boolean busy() { return busy; }
    boolean failed() { return failed; }
    boolean running() { return running; }
    void foreground(boolean value) { foreground = value; retry(); }
    void paused(boolean value) { paused = value; retry(); }
    void busy(boolean value) { busy = value; retry(); }
    void fail() { failed = true; retry(); }
    void destroy() { foreground = false; failed = true; retry(); }
    void retry() {
        boolean wanted = foreground && !paused && !busy && !failed;
        if (wanted && !running) running = driver.start();
        else if (!wanted && running) { running = false; driver.stop(); }
    }
}
