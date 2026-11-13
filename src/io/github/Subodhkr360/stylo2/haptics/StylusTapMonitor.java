package io.github.Subodhkr360.stylo2.haptics;

public class StylusTapMonitor {
    private static StylusTapMonitor sInstance;

    public static synchronized StylusTapMonitor getInstance() {
        if (sInstance == null) {
            sInstance = new StylusTapMonitor();
        }
        return sInstance;
    }

    private StylusTapMonitor() {}

    public synchronized void start() {}

    public synchronized void stop() {}
}
