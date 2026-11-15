package io.github.Subodhkr360.stylo2.haptics;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.WindowManager;

import java.io.InputStream;

public class AirViewManager {
    private static final String TAG = "AirViewManager";

    public static final String PREF_AIR_VIEW_ENABLED = "pref_air_view_enabled";
    public static final String PREF_AIR_VIEW_SCROLL_ENABLED = "pref_air_view_scroll_enabled";
    public static final String PREF_AIR_VIEW_EDGE_ZONE_DP = "pref_air_view_edge_zone_dp";
    public static final String PREF_AIR_VIEW_POINTER_SIZE_DP = "pref_air_view_pointer_size_dp";
    public static final String PREF_AIR_VIEW_SCROLL_SPEED = "pref_air_view_scroll_speed";

    private static final float RAW_MAX_X = 21199.0f;
    private static final float RAW_MAX_Y = 29999.0f;
    private static final long HOVER_TIMEOUT_MS = 140;
    private static final long HOVERSCROLL_DELAY_MS = 280;

    private static AirViewManager sInstance;

    public static final String ACTION_AIR_VIEW_PREFS_CHANGED = "io.github.Subodhkr360.stylo2.haptics.AIR_VIEW_PREFS_CHANGED";

    private final Context mContext;
    private StyloAccessibilityService mService;
    private final SharedPreferences mPrefs;
    private Handler mMainHandler = new Handler(Looper.getMainLooper());
    private WindowManager mWindowManager;

    private android.os.HandlerThread mCheckThread;
    private Handler mCheckHandler;

    private AirViewOverlayView mOverlayView;
    private WindowManager.LayoutParams mOverlayParams;
    private boolean mIsOverlayAttached = false;

    private volatile boolean mRunning = false;
    private Thread mReaderThread;

    private boolean mAirViewEnabled = true;
    private boolean mAutoScrollEnabled = false;
    private int mEdgeZoneDp = 64;
    private int mPointerSizeDp = 4;

    private boolean mIsHovering = false;
    private boolean mAutoScrollScheduled = false;
    private boolean mAutoScrollUp = false;
    private float mScrollTargetX = 0f;
    private float mScrollTargetY = 0f;

    private int mActiveScrollState = AirViewOverlayView.STATE_HOVER_DOT;
    private boolean mIsZoneCandidate = false;
    private boolean mCandidateScrollUp = false;

    private int mScrollSpeed = 1;
    private long mScrollIntervalMs = 135;

    private volatile int mLatestRawX;
    private volatile int mLatestRawY;
    private volatile int mLatestDistance;
    private volatile int mLatestTouch;
    private final java.util.concurrent.atomic.AtomicBoolean mInputPending = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final Runnable mInputSyncRunnable = new Runnable() {
        @Override
        public void run() {
            mInputPending.set(false);
            onInputSync(mLatestRawX, mLatestRawY, mLatestDistance, mLatestTouch);
        }
    };

    private final Runnable mHoverTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIsHovering) {
                mIsHovering = false;
                cancelAutoScroll();
                if (mOverlayView != null) {
                    mOverlayView.updatePointer(-100f, -100f, AirViewOverlayView.STATE_IDLE);
                }
            }
        }
    };

    private final Runnable mScrollPulseRunnable = new Runnable() {
        @Override
        public void run() {
            if (mAutoScrollScheduled && mService != null) {
                mService.performSmoothAutoScroll(mAutoScrollUp, mScrollTargetX, mScrollTargetY, mScrollSpeed);
                mMainHandler.postDelayed(this, mScrollIntervalMs);
            }
        }
    };

    private final Runnable mScrollDwellRunnable = new Runnable() {
        @Override
        public void run() {
            if (mAutoScrollScheduled) {
                mMainHandler.post(mScrollPulseRunnable);
            }
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            mMainHandler.post(() -> loadPreferences());
        }
    };

    public static synchronized AirViewManager getInstance(Context context) {
        if (sInstance == null && context != null) {
            sInstance = new AirViewManager(context);
        }
        return sInstance;
    }

    public static synchronized AirViewManager getInstance() {
        return sInstance;
    }

    public void setService(StyloAccessibilityService service) {
        mService = service;
    }

    private AirViewManager(Context context) {
        mContext = context.getApplicationContext();
        if (context instanceof StyloAccessibilityService) {
            mService = (StyloAccessibilityService) context;
        }
        mCheckThread = new android.os.HandlerThread("AirView-ScrollCheck");
        mCheckThread.start();
        mCheckHandler = new Handler(mCheckThread.getLooper());

        mPrefs = mContext.getSharedPreferences("stylo_haptics_prefs", Context.MODE_PRIVATE);
        mPrefs.registerOnSharedPreferenceChangeListener(mPrefListener);
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        loadPreferences();
    }

    public void loadPreferences() {
        mAirViewEnabled = mPrefs.getBoolean(PREF_AIR_VIEW_ENABLED, true);
        mAutoScrollEnabled = mPrefs.getBoolean(PREF_AIR_VIEW_SCROLL_ENABLED, false);
        mEdgeZoneDp = mPrefs.getInt(PREF_AIR_VIEW_EDGE_ZONE_DP, 64);
        mPointerSizeDp = mPrefs.getInt(PREF_AIR_VIEW_POINTER_SIZE_DP, 4);
        mScrollSpeed = mPrefs.getInt(PREF_AIR_VIEW_SCROLL_SPEED, 1);
        updateScrollInterval();

        if (mOverlayView != null) {
            mOverlayView.setPointerSizeDp(mPointerSizeDp);
            if (!mAirViewEnabled) {
                mOverlayView.hideImmediately();
            }
        }
    }

    public void setPointerSizeDp(int sizeDp) {
        mPointerSizeDp = sizeDp;
        if (mOverlayView != null) {
            mOverlayView.setPointerSizeDp(sizeDp);
        }
    }

    public void setScrollSpeed(int speed) {
        mScrollSpeed = speed;
        updateScrollInterval();
    }

    private void updateScrollInterval() {
        switch (mScrollSpeed) {
            case 0: mScrollIntervalMs = 160; break; // Ultra Slow (calm glide)
            case 1: mScrollIntervalMs = 135; break; // Slow (reading pace)
            case 3: mScrollIntervalMs = 95; break;  // Fast (rapid skim)
            case 4: mScrollIntervalMs = 80; break;  // Very Fast (high speed continuous glide)
            case 2: default: mScrollIntervalMs = 115; break; // Normal (smooth OneUI 8.5 pace)
        }
    }


    public void onServiceConnected(StyloAccessibilityService service) {
        mService = service;
        mWindowManager = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        attachOverlay();
    }

    public void onServiceConnected() {
        attachOverlay();
    }

    public void start() {
        if (mRunning) return;
        mRunning = true;
        if (mCheckThread == null || !mCheckThread.isAlive()) {
            mCheckThread = new android.os.HandlerThread("AirView-ScrollCheck");
            mCheckThread.start();
            mCheckHandler = new Handler(mCheckThread.getLooper());
        }
        loadPreferences();
        attachOverlay();
        startInputReader();
        Log.i(TAG, "AirViewManager started");
    }

    public void stop() {
        mRunning = false;
        mMainHandler.removeCallbacksAndMessages(null);
        cancelAutoScroll();
        if (mCheckThread != null) {
            mCheckThread.quitSafely();
            mCheckThread = null;
            mCheckHandler = null;
        }
        detachOverlay();
        if (mReaderThread != null) {
            mReaderThread.interrupt();
            mReaderThread = null;
        }
        Log.i(TAG, "AirViewManager stopped");
    }

    private void attachOverlay() {
        mMainHandler.post(() -> {
            if (mIsOverlayAttached || mService == null) return;
            try {
                if (mWindowManager == null) {
                    mWindowManager = (WindowManager) mService.getSystemService(Context.WINDOW_SERVICE);
                }
                if (mWindowManager == null) return;

                if (mOverlayView == null) {
                    mOverlayView = new AirViewOverlayView(mService);
                }
                mOverlayView.setPointerSizeDp(mPointerSizeDp);

                mOverlayParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT
                );
                mOverlayParams.gravity = Gravity.TOP | Gravity.LEFT;
                mWindowManager.addView(mOverlayView, mOverlayParams);
                mIsOverlayAttached = true;
                Log.i(TAG, "AirView overlay attached successfully to AccessibilityService window");
            } catch (Exception e) {
                Log.e(TAG, "Failed to attach AirView overlay", e);
            }
        });
    }

    private void detachOverlay() {
        mMainHandler.post(() -> {
            if (!mIsOverlayAttached || mOverlayView == null) return;
            try {
                mWindowManager.removeView(mOverlayView);
            } catch (Exception ignored) {}
            mOverlayView = null;
            mIsOverlayAttached = false;
        });
    }

    private void startInputReader() {
        mReaderThread = new Thread(() -> {
            Process p = null;
            InputStream in = null;
            try {
                // 1. Direct /dev/input/event8 file access (fastest & in-process)
                try {
                    java.io.File devFile = new java.io.File("/dev/input/event8");
                    in = new java.io.FileInputStream(devFile);
                    Log.i(TAG, "Successfully opened /dev/input/event8 directly via FileInputStream");
                } catch (Throwable t) {
                    Log.d(TAG, "Direct event8 open failed (" + t.getMessage() + "), falling back to su process");
                    in = null;
                }

                // 2. Fallback: su process
                if (in == null) {
                    String[] candidates = {"/system/bin/su", "/system/xbin/su", "/data/adb/ksu/bin/su", "/data/adb/ap/bin/su", "/data/adb/magisk/su", "su"};
                    for (String suBin : candidates) {
                        try {
                            p = Runtime.getRuntime().exec(new String[]{suBin, "-c", "cat /dev/input/event8"});
                            Thread.sleep(50);
                            if (p.isAlive()) {
                                in = p.getInputStream();
                                Log.i(TAG, "Successfully attached to /dev/input/event8 via " + suBin);
                                break;
                            }
                        } catch (Throwable ignored) {}
                    }
                }

                if (in == null) {
                    Log.e(TAG, "Failed to spawn su process for input reader");
                    return;
                }

                byte[] buf = new byte[24 * 16];
                int bytesRead;

                int rawX = 0;
                int rawY = 0;
                int distance = 0;
                int touch = 0;

                while (mRunning && (bytesRead = in.read(buf)) != -1) {
                    for (int i = 0; i + 24 <= bytesRead; i += 24) {
                        int type = (buf[i + 16] & 0xFF) | ((buf[i + 17] & 0xFF) << 8);
                        int code = (buf[i + 18] & 0xFF) | ((buf[i + 19] & 0xFF) << 8);
                        int val = (buf[i + 20] & 0xFF) | ((buf[i + 21] & 0xFF) << 8)
                                | ((buf[i + 22] & 0xFF) << 16) | ((buf[i + 23] & 0xFF) << 24);

                        if (type == 0x0003) { // EV_ABS
                            if (code == 0x0000) rawX = val;       // ABS_X
                            else if (code == 0x0001) rawY = val;  // ABS_Y
                            else if (code == 0x0019) distance = val; // ABS_DISTANCE (0/1)
                        } else if (type == 0x0001) { // EV_KEY
                            if (code == 0x014a) touch = val;      // BTN_TOUCH (0/1)
                        } else if (type == 0x0000) { // EV_SYN
                            mLatestRawX = rawX;
                            mLatestRawY = rawY;
                            mLatestDistance = distance;
                            mLatestTouch = touch;
                            if (mInputPending.compareAndSet(false, true)) {
                                mMainHandler.post(mInputSyncRunnable);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (mRunning) {
                    Log.e(TAG, "Input reader loop error", e);
                }
            } finally {
                if (in != null) try { in.close(); } catch (Exception ignored) {}
                if (p != null) p.destroy();
            }
        }, "AirViewInputReader");
        mReaderThread.start();
    }

    private void onInputSync(int rawX, int rawY, int distance, int touch) {
        if (touch == 1 || distance == 1) {
            // Notify AirCommand of stylus activity for auto-hide / auto-reveal
            AirCommandManager acMgr = AirCommandManager.getInstance();
            if (acMgr != null) {
                acMgr.onStylusActivity();
            }
        }

        if (!mAirViewEnabled || mOverlayView == null) return;

        // Reset hover timeout watchdog
        mMainHandler.removeCallbacks(mHoverTimeoutRunnable);
        mMainHandler.postDelayed(mHoverTimeoutRunnable, HOVER_TIMEOUT_MS);

        if (touch == 1) {
            // Stylus is physically touching screen (drawing/writing/clicking)
            // Immediately dismiss hover cursor to avoid obscuring pen tip
            mIsHovering = false;
            cancelAutoScroll();
            mOverlayView.hideImmediately();
            return;
        }

        if (distance == 1) {
            // Stylus is hovering in air
            mIsHovering = true;

            DisplayMetrics dm = new DisplayMetrics();
            mWindowManager.getDefaultDisplay().getRealMetrics(dm);
            int screenW = dm.widthPixels;
            int screenH = dm.heightPixels;
            int rotation = mWindowManager.getDefaultDisplay().getRotation();

            float normX = Math.max(0f, Math.min(1f, rawX / RAW_MAX_X));
            float normY = Math.max(0f, Math.min(1f, rawY / RAW_MAX_Y));

            float screenX;
            float screenY;

            switch (rotation) {
                case Surface.ROTATION_90:
                    screenX = normY * screenW;
                    screenY = (1.0f - normX) * screenH;
                    break;
                case Surface.ROTATION_270:
                    screenX = (1.0f - normY) * screenW;
                    screenY = normX * screenH;
                    break;
                case Surface.ROTATION_180:
                    screenX = (1.0f - normX) * screenW;
                    screenY = (1.0f - normY) * screenH;
                    break;
                case Surface.ROTATION_0:
                default:
                    screenX = normX * screenW;
                    screenY = normY * screenH;
                    break;
            }

            float density = dm.density;
            float edgeZonePx = mEdgeZoneDp * density;
            float topMarginPx = 48 * density; // Status bar & cutout protection
            float bottomMarginPx = 96 * density; // Navigation bar, gesture pill & tablet taskbar dock protection

            boolean launcherActive = (mService != null && mService.isLauncherActive());
            boolean inTopZone = !launcherActive && (screenY >= topMarginPx && screenY <= (topMarginPx + edgeZonePx));
            boolean inBottomZone = !launcherActive && (screenY >= (screenH - bottomMarginPx - edgeZonePx) && screenY <= (screenH - bottomMarginPx));

            if (mAutoScrollEnabled && (inTopZone || inBottomZone)) {
                boolean scrollUp = inTopZone;
                mScrollTargetX = screenX;
                mScrollTargetY = screenY;

                if (!mIsZoneCandidate || mCandidateScrollUp != scrollUp) {
                    mIsZoneCandidate = true;
                    mCandidateScrollUp = scrollUp;
                    mActiveScrollState = AirViewOverlayView.STATE_HOVER_DOT;
                    mMainHandler.removeCallbacks(mScrollPulseRunnable);
                    if (mCheckHandler != null) {
                        mCheckHandler.removeCallbacksAndMessages(null);
                    }

                    final float checkX = screenX;
                    final float checkY = screenY;
                    final boolean checkUp = scrollUp;

                    if (mCheckHandler != null) {
                        mCheckHandler.postDelayed(() -> {
                            if (!mIsZoneCandidate || mCandidateScrollUp != checkUp) return;
                            boolean canScroll = (mService != null && mService.canScrollAt((int) checkX, (int) checkY, checkUp));
                            mMainHandler.post(() -> {
                                if (mIsHovering && mIsZoneCandidate && mCandidateScrollUp == checkUp) {
                                    if (canScroll) {
                                        mActiveScrollState = checkUp ? AirViewOverlayView.STATE_SCROLL_UP : AirViewOverlayView.STATE_SCROLL_DOWN;
                                        mAutoScrollScheduled = true;
                                        mAutoScrollUp = checkUp;
                                        mMainHandler.post(mScrollPulseRunnable);
                                    } else {
                                        mActiveScrollState = AirViewOverlayView.STATE_HOVER_DOT;
                                        cancelAutoScroll();
                                    }
                                }
                            });
                        }, 160);
                    }
                }
            } else {
                if (mIsZoneCandidate) {
                    cancelAutoScroll();
                }
            }

            int state = mActiveScrollState;
            mOverlayView.setPointerSizeDp(mPointerSizeDp);
            mOverlayView.updatePointer(screenX, screenY, state);
        }
    }

    public void setLaserMode(boolean enabled) {
        if (mOverlayView != null) {
            mOverlayView.setLaserMode(enabled);
        }
    }

    public boolean isLaserMode() {
        return mOverlayView != null && mOverlayView.isLaserMode();
    }

    private void cancelAutoScroll() {
        mIsZoneCandidate = false;
        mAutoScrollScheduled = false;
        mActiveScrollState = AirViewOverlayView.STATE_HOVER_DOT;
        if (mCheckHandler != null) {
            mCheckHandler.removeCallbacksAndMessages(null);
        }
        mMainHandler.removeCallbacks(mScrollDwellRunnable);
        mMainHandler.removeCallbacks(mScrollPulseRunnable);
    }
}
