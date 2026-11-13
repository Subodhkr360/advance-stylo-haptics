package io.github.Subodhkr360.stylo2.haptics;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.List;

public class StyloAccessibilityService extends AccessibilityService {
    private static final String TAG = "StyloAccessibility";
    private StylusHapticsManager mManager;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private static final long POLL_INTERVAL_MS = 250;

    private final Runnable mPollRunnable = new Runnable() {
        @Override
        public void run() {
            evaluateActiveWindows();
            mHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mManager = StylusHapticsManager.getInstance(this);
        mManager.bind();
        Log.i(TAG, "StyloAccessibilityService onCreate");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "StyloAccessibilityService connected");
        if (mManager != null) {
            mManager.bind();
        }
        mHandler.removeCallbacks(mPollRunnable);
        mHandler.post(mPollRunnable);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || mManager == null) return;
        int eventType = event.getEventType();
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED 
                || eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                || eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            evaluateActiveWindows();
        }
    }

    public synchronized void evaluateActiveWindows() {
        if (mManager == null) return;

        String imePackage = null;
        String topAppPackage = null;

        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null && !windows.isEmpty()) {
                for (AccessibilityWindowInfo win : windows) {
                    if (win == null) continue;
                    int type = win.getType();
                    if (type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                        AccessibilityNodeInfo root = win.getRoot();
                        if (root != null) {
                            CharSequence p = root.getPackageName();
                            if (p != null) imePackage = p.toString();
                            root.recycle();
                        }
                    } else if (type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                        if (win.isFocused() || win.isActive()) {
                            AccessibilityNodeInfo root = win.getRoot();
                            if (root != null) {
                                CharSequence p = root.getPackageName();
                                if (p != null) topAppPackage = p.toString();
                                root.recycle();
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        if (topAppPackage == null) {
            try {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    CharSequence p = root.getPackageName();
                    if (p != null) topAppPackage = p.toString();
                    root.recycle();
                }
            } catch (Exception ignored) {}
        }

        mManager.updateActiveAppState(topAppPackage, imePackage);
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "StyloAccessibilityService interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mPollRunnable);
        Log.i(TAG, "StyloAccessibilityService onDestroy");
    }
}
