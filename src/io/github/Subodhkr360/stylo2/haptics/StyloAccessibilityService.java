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
    private AirViewManager mAirViewManager;
    private AirCommandManager mAirCommandManager;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private static final long POLL_INTERVAL_MS = 250;

    private int mDockCheckCounter = 0;

    private final Runnable mPollRunnable = new Runnable() {
        @Override
        public void run() {
            evaluateActiveWindows();

            if (++mDockCheckCounter >= 4) {
                mDockCheckCounter = 0;
                if (mAirCommandManager != null && mAirCommandManager.isAttached()) {
                    if (mAirCommandManager.isStylusDockedOrDisconnected()) {
                        if (mAirCommandManager.isBubbleVisible() || mAirCommandManager.isExpanded()) {
                            mAirCommandManager.hideBubbleImmediately();
                        }
                    }
                }
            }

            mHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mManager = StylusHapticsManager.getInstance(this);
        mManager.bind();
        mAirViewManager = AirViewManager.getInstance(this);
        mAirViewManager.onServiceConnected(this);
        mAirViewManager.start();
        mAirCommandManager = AirCommandManager.getInstance(this);
        mAirCommandManager.onServiceConnected(this);
        Log.i(TAG, "StyloAccessibilityService onCreate");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "StyloAccessibilityService connected");
        if (mManager != null) {
            mManager.bind();
        }
        if (mAirViewManager != null) {
            mAirViewManager.onServiceConnected(this);
            mAirViewManager.start();
        }
        if (mAirCommandManager != null) {
            mAirCommandManager.onServiceConnected(this);
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

        mTopAppPackage = topAppPackage;
        mManager.updateActiveAppState(topAppPackage, imePackage);
    }

    private volatile String mTopAppPackage = null;

    public boolean isLauncherPackage(String pkg) {
        if (pkg == null) return false;
        String lower = pkg.toLowerCase();
        return lower.contains("launcher") || lower.contains("home") || lower.contains("quickstep")
                || lower.equals("com.android.systemui") || lower.equals("com.oplus.android.launcher");
    }

    public boolean isLauncherActive() {
        return isLauncherPackage(mTopAppPackage);
    }

    public android.graphics.Rect getTargetWindowBoundsAt(int x, int y) {
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null && !windows.isEmpty()) {
                AccessibilityWindowInfo bestWin = null;
                int maxLayer = -1;
                android.graphics.Rect winBounds = new android.graphics.Rect();
                for (AccessibilityWindowInfo win : windows) {
                    if (win == null) continue;
                    int type = win.getType();
                    if (type != AccessibilityWindowInfo.TYPE_APPLICATION && type != AccessibilityWindowInfo.TYPE_SYSTEM) {
                        continue;
                    }
                    win.getBoundsInScreen(winBounds);
                    if (winBounds.contains(x, y)) {
                        int layer = win.getLayer();
                        if (bestWin == null || layer > maxLayer) {
                            bestWin = win;
                            maxLayer = layer;
                        }
                    }
                }
                if (bestWin != null) {
                    android.graphics.Rect r = new android.graphics.Rect();
                    bestWin.getBoundsInScreen(r);
                    return r;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public AccessibilityNodeInfo findRootNodeAt(int x, int y) {
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null && !windows.isEmpty()) {
                AccessibilityWindowInfo bestWin = null;
                int maxLayer = -1;
                android.graphics.Rect winBounds = new android.graphics.Rect();
                for (AccessibilityWindowInfo win : windows) {
                    if (win == null) continue;
                    int type = win.getType();
                    if (type != AccessibilityWindowInfo.TYPE_APPLICATION && type != AccessibilityWindowInfo.TYPE_SYSTEM) {
                        continue;
                    }
                    win.getBoundsInScreen(winBounds);
                    if (winBounds.contains(x, y)) {
                        int layer = win.getLayer();
                        if (bestWin == null || layer > maxLayer) {
                            bestWin = win;
                            maxLayer = layer;
                        }
                    }
                }
                if (bestWin != null) {
                    AccessibilityNodeInfo r = bestWin.getRoot();
                    if (r != null) {
                        CharSequence pkg = r.getPackageName();
                        if (pkg != null && isLauncherPackage(pkg.toString())) {
                            r.recycle();
                            return null;
                        }
                        return r;
                    }
                }
            }
        } catch (Exception ignored) {}

        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                CharSequence pkg = root.getPackageName();
                if (pkg != null && isLauncherPackage(pkg.toString())) {
                    root.recycle();
                    return null;
                }
                return root;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public boolean canScrollAt(int x, int y, boolean scrollUp) {
        if (isLauncherActive()) return false;
        AccessibilityNodeInfo root = findRootNodeAt(x, y);
        if (root == null) return false;
        try {
            AccessibilityNodeInfo target = findScrollableChildAt(root, x, y);
            if (target != null) {
                int action = scrollUp ? 
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD : 
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
                boolean supported = false;
                List<AccessibilityNodeInfo.AccessibilityAction> actions = target.getActionList();
                if (actions != null) {
                    for (AccessibilityNodeInfo.AccessibilityAction a : actions) {
                        if (a.getId() == action) {
                            supported = true;
                            break;
                        }
                    }
                }
                if (!supported && target.isScrollable()) {
                    supported = true;
                }
                target.recycle();
                return supported;
            }
        } catch (Exception ignored) {
        } finally {
            root.recycle();
        }
        return false;
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "StyloAccessibilityService interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mPollRunnable);
        if (mAirViewManager != null) {
            mAirViewManager.stop();
        }
        if (mAirCommandManager != null) {
            mAirCommandManager.detachView();
        }
        Log.i(TAG, "StyloAccessibilityService onDestroy");
    }

    public long getRecommendedScrollInterval(float x, float y, long baseIntervalMs) {
        AccessibilityNodeInfo root = findRootNodeAt((int) x, (int) y);
        if (root == null) return baseIntervalMs;
        try {
            AccessibilityNodeInfo scrollTarget = findScrollableChildAt(root, (int) x, (int) y);
            if (scrollTarget != null) {
                CharSequence cls = scrollTarget.getClassName();
                scrollTarget.recycle();
                if (cls != null) {
                    String name = cls.toString();
                    if (name.contains("ScrollView") || name.contains("WebView") 
                            || name.contains("ListView") || name.contains("RecyclerView")
                            || name.contains("ViewPager")) {
                        return (long) (baseIntervalMs * 1.35f);
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            root.recycle();
        }
        return baseIntervalMs;
    }

    public void performSmoothAutoScroll(boolean scrollUp, float x, float y, int scrollSpeed) {
        if (isLauncherActive()) return;
        if (!canScrollAt((int) x, (int) y, scrollUp)) return;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                float density = getResources().getDisplayMetrics().density;
                float distancePx;
                long durationMs;
                switch (scrollSpeed) {
                    case 0: // Ultra Slow
                        distancePx = 28 * density;
                        durationMs = 120;
                        break;
                    case 1: // Slow
                        distancePx = 48 * density;
                        durationMs = 110;
                        break;
                    case 3: // Fast
                        distancePx = 110 * density;
                        durationMs = 90;
                        break;
                    case 4: // Very Fast
                        distancePx = 160 * density;
                        durationMs = 80;
                        break;
                    case 2: // Normal (OneUI 8.5 standard)
                    default:
                        distancePx = 72 * density;
                        durationMs = 100;
                        break;
                }

                android.graphics.Rect winBounds = getTargetWindowBoundsAt((int) x, (int) y);
                android.util.DisplayMetrics dm = getResources().getDisplayMetrics();

                float centerX = x;
                float centerY = dm.heightPixels / 2f;

                if (winBounds != null && winBounds.width() > 0 && winBounds.height() > 0) {
                    centerX = Math.max(winBounds.left + 36 * density, Math.min(winBounds.right - 36 * density, x));
                    centerY = winBounds.centerY();
                } else {
                    centerX = Math.max(36 * density, Math.min(dm.widthPixels - 36 * density, x));
                }

                float halfDist = distancePx / 2f;
                float startY;
                float endY;

                if (scrollUp) {
                    // Swiping downwards scrolls the content upwards (reveals content above)
                    startY = centerY - halfDist;
                    endY = centerY + halfDist;
                } else {
                    // Swiping upwards scrolls the content downwards (reveals content below)
                    startY = centerY + halfDist;
                    endY = centerY - halfDist;
                }

                if (winBounds != null) {
                    startY = Math.max(winBounds.top + 24 * density, Math.min(winBounds.bottom - 24 * density, startY));
                    endY = Math.max(winBounds.top + 24 * density, Math.min(winBounds.bottom - 24 * density, endY));
                } else {
                    startY = Math.max(32 * density, Math.min(dm.heightPixels - 32 * density, startY));
                    endY = Math.max(32 * density, Math.min(dm.heightPixels - 32 * density, endY));
                }

                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(centerX, startY);
                path.lineTo(centerX, endY);

                android.accessibilityservice.GestureDescription.Builder builder =
                        new android.accessibilityservice.GestureDescription.Builder();
                builder.addStroke(new android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, durationMs));

                boolean dispatched = dispatchGesture(builder.build(), null, null);
                if (dispatched) {
                    return;
                }
            } catch (Throwable t) {
                Log.w(TAG, "Gesture dispatch failed, falling back to node action: " + t.getMessage());
            }
        }
        performAutoScroll(scrollUp, x, y);
    }

    public void performAutoScroll(boolean scrollUp, float x, float y) {
        AccessibilityNodeInfo root = findRootNodeAt((int) x, (int) y);
        if (root != null) {
            try {
                AccessibilityNodeInfo scrollTarget = findScrollableChildAt(root, (int) x, (int) y);
                if (scrollTarget != null) {
                    scrollTarget.performAction(scrollUp ? 
                        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD : 
                        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                    scrollTarget.recycle();
                }
            } catch (Exception ignored) {
            } finally {
                root.recycle();
            }
        }
    }

    private AccessibilityNodeInfo findScrollableChildAt(AccessibilityNodeInfo node, int x, int y) {
        if (node == null) return null;
        android.graphics.Rect bounds = new android.graphics.Rect();
        node.getBoundsInScreen(bounds);
        if (!bounds.contains(x, y)) {
            return null;
        }

        CharSequence className = node.getClassName();
        if (className != null) {
            String cls = className.toString();
            if (cls.contains("Workspace") || cls.contains("CellLayout") || cls.contains("PagedView") || cls.contains("DragLayer")) {
                return null;
            }
        }

        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo target = findScrollableChildAt(child, x, y);
                if (target != null) {
                    child.recycle();
                    return target;
                }
                child.recycle();
            }
        }

        if (node.isScrollable()) {
            return AccessibilityNodeInfo.obtain(node);
        }
        return null;
    }
}
