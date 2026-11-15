package io.github.Subodhkr360.stylo2.haptics;

import android.accessibilityservice.AccessibilityService;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class AirCommandManager {
    private static final String TAG = "AirCommandManager";

    public static final String PREF_AIR_COMMAND_ENABLED = "pref_air_command_enabled";
    public static final String PREF_AIR_COMMAND_POS_Y_RATIO = "pref_air_command_pos_y_ratio";
    public static final String PREF_AIR_COMMAND_IS_RIGHT = "pref_air_command_is_right";
    public static final String PREF_AIR_COMMAND_TOOLS = "pref_air_command_tools";
    public static final String PREF_AIR_COMMAND_SHOW_LABELS = "pref_air_command_show_labels";

    public static final String DEFAULT_TOOLS = "laser_pointer,quick_note,capture,circle_to_search,settings";

    private static AirCommandManager sInstance;

    private final Context mContext;
    private final SharedPreferences mPrefs;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private WindowManager mWindowManager;
    private StyloAccessibilityService mService;

    private boolean mEnabled = true;
    private boolean mShowLabels = true;
    private float mPosYRatio = 0.42f;
    private boolean mIsRightSide = true;

    private FrameLayout mRootContainer;
    private FrameLayout mBubbleView;
    private FrameLayout mScrimView;
    private AirCommandArcView mArcMenuView;
    private WindowManager.LayoutParams mLayoutParams;
    private boolean mIsAttached = false;
    private boolean mIsExpanded = false;

    private int mTouchSlop;
    private float mDensity;
    private int mBubbleSizePx;

    private boolean mIsPenActive = false;
    private static final long INACTIVITY_TIMEOUT_MS = 4000;

    private final Runnable mAutoHideRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIsExpanded) return;
            mIsPenActive = false;
            if (mBubbleView != null && mBubbleView.getVisibility() == View.VISIBLE) {
                mBubbleView.animate()
                        .alpha(0.0f)
                        .setDuration(260)
                        .withEndAction(() -> {
                            if (!mIsPenActive && !mIsExpanded && mBubbleView != null) {
                                mBubbleView.setVisibility(View.GONE);
                            }
                        })
                        .start();
            }
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefListener =
            (sharedPreferences, key) -> {
                if (PREF_AIR_COMMAND_ENABLED.equals(key) || PREF_AIR_COMMAND_TOOLS.equals(key)
                        || PREF_AIR_COMMAND_SHOW_LABELS.equals(key)) {
                    mHandler.post(this::loadPreferencesAndSync);
                }
            };

    public static synchronized AirCommandManager getInstance(Context context) {
        if (sInstance == null && context != null) {
            sInstance = new AirCommandManager(context);
        }
        return sInstance;
    }

    public static synchronized AirCommandManager getInstance() {
        return sInstance;
    }

    private AirCommandManager(Context context) {
        mContext = context.getApplicationContext();
        if (context instanceof StyloAccessibilityService) {
            mService = (StyloAccessibilityService) context;
        }
        mPrefs = mContext.getSharedPreferences("stylo_haptics_prefs", Context.MODE_PRIVATE);
        mPrefs.registerOnSharedPreferenceChangeListener(mPrefListener);
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);

        mDensity = mContext.getResources().getDisplayMetrics().density;
        mBubbleSizePx = (int) (40 * mDensity); // Small 40dp refined bubble matching aircmd.jpg
        mTouchSlop = ViewConfiguration.get(mContext).getScaledTouchSlop();

        loadPreferences();
    }

    public void onServiceConnected(StyloAccessibilityService service) {
        mService = service;
        mWindowManager = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        loadPreferencesAndSync();
    }

    public void loadPreferences() {
        mEnabled = mPrefs.getBoolean(PREF_AIR_COMMAND_ENABLED, true);
        mShowLabels = mPrefs.getBoolean(PREF_AIR_COMMAND_SHOW_LABELS, true);
        mPosYRatio = mPrefs.getFloat(PREF_AIR_COMMAND_POS_Y_RATIO, 0.42f);
        mIsRightSide = mPrefs.getBoolean(PREF_AIR_COMMAND_IS_RIGHT, true);
    }

    public void loadPreferencesAndSync() {
        loadPreferences();
        if (mEnabled) {
            attachView();
        } else {
            detachView();
        }
    }

    private boolean mIsReceiverRegistered = false;

    private final BroadcastReceiver mBtReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if ("io.github.Subodhkr360.stylo2.action.EXPAND_AIR_COMMAND".equals(action)) {
                expandMenu();
            } else if ("io.github.Subodhkr360.stylo2.action.COLLAPSE_AIR_COMMAND".equals(action)) {
                collapseMenu();
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)
                    || "com.oplus.ipemanager.action.CONNECT_PENCIL".equals(action)) {
                if (!isStylusDockedOrDisconnected()) {
                    onStylusActivity();
                } else {
                    hideBubbleImmediately();
                }
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)
                    || "com.oplus.ipemanager.action.DISCONNECT_PENCIL".equals(action)) {
                hideBubbleImmediately();
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                    hideBubbleImmediately();
                }
            } else if ("com.oplus.ipemanager.action.BATTERY_NOTIFY".equals(action)
                    || "com.oplus.ipemanager.action.PENCIL_STATUS_CHANGE".equals(action)) {
                int charging = intent.getIntExtra("is_charging", intent.getIntExtra("charging", -1));
                int attached = intent.getIntExtra("attached", intent.getIntExtra("is_attached", -1));
                if (charging == 1 || attached == 1 || isStylusDocked()) {
                    Log.i(TAG, "Stylus docked/charging broadcast received, hiding Air Command bubble");
                    hideBubbleImmediately();
                }
            }
        }
    };

    public boolean isStylusDocked() {
        try {
            java.io.File presentFile = new java.io.File("/sys/devices/virtual/oplus_wireless/pencil/present");
            if (presentFile.exists() && presentFile.canRead()) {
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(presentFile));
                String line = br.readLine();
                br.close();
                if (line != null && "1".equals(line.trim())) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        try {
            java.io.File txFile = new java.io.File("/sys/devices/virtual/oplus_wireless/pencil/tx_status");
            if (txFile.exists() && txFile.canRead()) {
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(txFile));
                String line = br.readLine();
                br.close();
                if (line != null) {
                    String status = line.trim().toLowerCase();
                    if (status.contains("connect") || status.contains("charg")) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    public boolean isStylusDisconnected() {
        try {
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            if (bt == null || !bt.isEnabled()) {
                return true;
            }
            Set<BluetoothDevice> bonded = bt.getBondedDevices();
            if (bonded == null || bonded.isEmpty()) {
                return true;
            }
            boolean foundConnectedStylo = false;
            for (BluetoothDevice dev : bonded) {
                String name = dev.getName();
                if (name != null && (name.contains("Stylo") || name.contains("Stylus") || name.contains("OPN2401"))) {
                    try {
                        java.lang.reflect.Method m = BluetoothDevice.class.getMethod("isConnected");
                        boolean connected = (Boolean) m.invoke(dev);
                        if (connected) {
                            foundConnectedStylo = true;
                            break;
                        }
                    } catch (Throwable t) {
                        foundConnectedStylo = true;
                        break;
                    }
                }
            }
            return !foundConnectedStylo;
        } catch (Throwable ignored) {}
        return false;
    }

    public boolean isStylusDockedOrDisconnected() {
        return isStylusDocked() || isStylusDisconnected();
    }

    public boolean isAttached() {
        return mIsAttached;
    }

    public boolean isExpanded() {
        return mIsExpanded;
    }

    public boolean isBubbleVisible() {
        return mBubbleView != null && mBubbleView.getVisibility() == View.VISIBLE && mBubbleView.getAlpha() > 0.05f;
    }

    public void onStylusActivity() {
        if (!mEnabled || mIsExpanded) return;

        if (isStylusDockedOrDisconnected()) {
            hideBubbleImmediately();
            return;
        }

        mIsPenActive = true;
        mHandler.removeCallbacks(mAutoHideRunnable);

        if (mBubbleView != null) {
            if (mBubbleView.getVisibility() != View.VISIBLE) {
                mBubbleView.setVisibility(View.VISIBLE);
                mBubbleView.setAlpha(0f);
            }
            mBubbleView.animate().alpha(0.60f).setDuration(180).start();
        }

        mHandler.postDelayed(mAutoHideRunnable, INACTIVITY_TIMEOUT_MS);
    }

    public void hideBubbleImmediately() {
        mHandler.removeCallbacks(mAutoHideRunnable);
        mIsPenActive = false;
        if (mIsExpanded) {
            collapseMenu();
        }
        if (mBubbleView != null) {
            mBubbleView.animate().cancel();
            mBubbleView.setVisibility(View.GONE);
            mBubbleView.setAlpha(0f);
        }
    }

    private void attachView() {
        if (mIsAttached || mWindowManager == null) return;

        createViews();

        DisplayMetrics dm = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getRealMetrics(dm);
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;

        int edgeMargin = (int) (8 * mDensity);
        int posX = mIsRightSide ? (screenW - mBubbleSizePx - edgeMargin) : edgeMargin;
        int posY = (int) (screenH * mPosYRatio);

        mLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
        );
        mLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        mLayoutParams.x = posX;
        mLayoutParams.y = posY;

        try {
            mWindowManager.addView(mRootContainer, mLayoutParams);
            mIsAttached = true;
            Log.i(TAG, "AirCommand floating bubble attached successfully");

            if (!isStylusDockedOrDisconnected()) {
                mBubbleView.setVisibility(View.VISIBLE);
                mBubbleView.setAlpha(0.60f);
                mHandler.removeCallbacks(mAutoHideRunnable);
                mHandler.postDelayed(mAutoHideRunnable, INACTIVITY_TIMEOUT_MS);
            } else {
                mBubbleView.setVisibility(View.GONE);
                mBubbleView.setAlpha(0f);
            }

            if (!mIsReceiverRegistered) {
                IntentFilter filter = new IntentFilter();
                filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
                filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
                filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
                filter.addAction("com.oplus.ipemanager.action.BATTERY_NOTIFY");
                filter.addAction("com.oplus.ipemanager.action.PENCIL_STATUS_CHANGE");
                filter.addAction("com.oplus.ipemanager.action.CONNECT_PENCIL");
                filter.addAction("com.oplus.ipemanager.action.DISCONNECT_PENCIL");
                filter.addAction("io.github.Subodhkr360.stylo2.action.EXPAND_AIR_COMMAND");
                filter.addAction("io.github.Subodhkr360.stylo2.action.COLLAPSE_AIR_COMMAND");
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        mContext.registerReceiver(mBtReceiver, filter, Context.RECEIVER_EXPORTED);
                    } else {
                        mContext.registerReceiver(mBtReceiver, filter);
                    }
                    mIsReceiverRegistered = true;
                } catch (Throwable ignored) {}
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to add AirCommand view", e);
        }
    }

    public void detachView() {
        if (!mIsAttached || mWindowManager == null || mRootContainer == null) return;
        mHandler.removeCallbacks(mAutoHideRunnable);
        if (mIsReceiverRegistered) {
            try {
                mContext.unregisterReceiver(mBtReceiver);
            } catch (Throwable ignored) {}
            mIsReceiverRegistered = false;
        }
        try {
            mWindowManager.removeView(mRootContainer);
        } catch (Exception ignored) {}
        mIsAttached = false;
        mIsExpanded = false;
        Log.i(TAG, "AirCommand floating bubble detached");
    }

    private void createViews() {
        if (mRootContainer != null) return;

        mRootContainer = new FrameLayout(mContext);
        mRootContainer.setClipChildren(false);
        mRootContainer.setClipToPadding(false);

        // Fullscreen translucent touchable scrim (hidden when collapsed)
        mScrimView = new FrameLayout(mContext);
        mScrimView.setBackgroundColor(Color.parseColor("#77000000"));
        mScrimView.setVisibility(View.GONE);
        mScrimView.setClickable(true);
        mScrimView.setFocusable(true);
        mScrimView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                collapseMenu();
            }
            return true;
        });
        mRootContainer.addView(mScrimView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Floating Pen Bubble - 40dp semi-transparent disc
        mBubbleView = new FrameLayout(mContext);
        mBubbleView.setBackgroundResource(R.drawable.bg_air_command_bubble);
        mBubbleView.setAlpha(0.0f); // Sleek translucent alpha matching aircmd.jpg (revealed on pen hover/touch)
        mBubbleView.setVisibility(View.GONE);

        ImageView iconView = new ImageView(mContext);
        iconView.setImageResource(R.drawable.ic_air_command_pen);
        int padding = (int) (9.5f * mDensity);
        iconView.setPadding(padding, padding, padding, padding);
        mBubbleView.addView(iconView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        FrameLayout.LayoutParams bubbleParams = new FrameLayout.LayoutParams(mBubbleSizePx, mBubbleSizePx);
        mRootContainer.addView(mBubbleView, bubbleParams);

        setupBubbleTouchListener();

        // Rotary Arc Menu View
        mArcMenuView = new AirCommandArcView(mContext);
        mArcMenuView.setVisibility(View.GONE);
        mRootContainer.addView(mArcMenuView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void setupBubbleTouchListener() {
        mBubbleView.setOnTouchListener(new View.OnTouchListener() {
            private float initialTouchX, initialTouchY;
            private int initialLayoutX, initialLayoutY;
            private boolean isDragging = false;
            private long downTime = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mIsExpanded) return false;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downTime = System.currentTimeMillis();
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        initialLayoutX = mLayoutParams.x;
                        initialLayoutY = mLayoutParams.y;
                        isDragging = false;
                        mHandler.removeCallbacks(mAutoHideRunnable);
                        mBubbleView.setAlpha(0.95f);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        if (!isDragging && (Math.hypot(dx, dy) > mTouchSlop)) {
                            isDragging = true;
                        }
                        if (isDragging && mIsAttached) {
                            mLayoutParams.y = (int) (initialLayoutY + dy);
                            mLayoutParams.x = (int) (initialLayoutX + dx);
                            mWindowManager.updateViewLayout(mRootContainer, mLayoutParams);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mBubbleView.setAlpha(0.60f);
                        mHandler.postDelayed(mAutoHideRunnable, INACTIVITY_TIMEOUT_MS);
                        long duration = System.currentTimeMillis() - downTime;
                        float totalMove = (float) Math.hypot(event.getRawX() - initialTouchX, event.getRawY() - initialTouchY);

                        if (!isDragging && totalMove < mTouchSlop && duration < 350) {
                            expandMenu();
                        } else if (isDragging) {
                            snapBubbleToEdge();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void snapBubbleToEdge() {
        DisplayMetrics dm = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getRealMetrics(dm);
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;

        int edgeMargin = (int) (8 * mDensity);
        int currentX = mLayoutParams.x;
        int currentY = Math.max((int) (60 * mDensity), Math.min(screenH - mBubbleSizePx - (int) (80 * mDensity), mLayoutParams.y));

        mIsRightSide = (currentX + mBubbleSizePx / 2) >= (screenW / 2);
        int finalX = mIsRightSide ? (screenW - mBubbleSizePx - edgeMargin) : edgeMargin;
        float yRatio = (float) currentY / (float) screenH;

        mLayoutParams.x = finalX;
        mLayoutParams.y = currentY;
        if (mIsAttached) {
            mWindowManager.updateViewLayout(mRootContainer, mLayoutParams);
        }

        mPrefs.edit()
                .putFloat(PREF_AIR_COMMAND_POS_Y_RATIO, yRatio)
                .putBoolean(PREF_AIR_COMMAND_IS_RIGHT, mIsRightSide)
                .apply();
        mPosYRatio = yRatio;
    }

    public void expandMenu() {
        if (!mIsAttached || mIsExpanded) return;
        mIsExpanded = true;
        mHandler.removeCallbacks(mAutoHideRunnable);

        DisplayMetrics dm = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getRealMetrics(dm);
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;

        int bubbleCenterX = mLayoutParams.x + mBubbleSizePx / 2;
        int bubbleCenterY = mLayoutParams.y + mBubbleSizePx / 2;

        List<AirCommandArcView.ToolModel> tools = buildToolModels();

        mBubbleView.setVisibility(View.GONE);
        mScrimView.setAlpha(0f);
        mScrimView.setVisibility(View.VISIBLE);
        mScrimView.animate().alpha(1.0f).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        mArcMenuView.setVisibility(View.VISIBLE);

        mLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        mLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        mLayoutParams.x = 0;
        mLayoutParams.y = 0;
        mWindowManager.updateViewLayout(mRootContainer, mLayoutParams);

        mArcMenuView.setup(tools, mIsRightSide, bubbleCenterX, bubbleCenterY, screenW, screenH,
                mShowLabels, this::handleToolClick, this::finishCollapseMenu);
    }

    public void collapseMenu() {
        if (!mIsAttached || !mIsExpanded) return;

        if (mScrimView != null && mScrimView.getVisibility() == View.VISIBLE) {
            mScrimView.animate().alpha(0f).setDuration(180).setInterpolator(new android.view.animation.AccelerateInterpolator()).start();
        }

        if (mArcMenuView != null && mArcMenuView.getVisibility() == View.VISIBLE) {
            mArcMenuView.collapseWithAnimation(this::finishCollapseMenu);
        } else {
            finishCollapseMenu();
        }
    }

    private void finishCollapseMenu() {
        mIsExpanded = false;

        DisplayMetrics dm = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getRealMetrics(dm);
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;

        mScrimView.setVisibility(View.GONE);
        mScrimView.setAlpha(0f);
        mArcMenuView.setVisibility(View.GONE);

        if (!isStylusDockedOrDisconnected()) {
            mBubbleView.setVisibility(View.VISIBLE);
            mBubbleView.setAlpha(0.60f);
            mHandler.removeCallbacks(mAutoHideRunnable);
            mHandler.postDelayed(mAutoHideRunnable, INACTIVITY_TIMEOUT_MS);
        } else {
            mBubbleView.setVisibility(View.GONE);
            mBubbleView.setAlpha(0f);
        }

        int edgeMargin = (int) (8 * mDensity);
        int posX = mIsRightSide ? (screenW - mBubbleSizePx - edgeMargin) : edgeMargin;
        int posY = (int) (screenH * mPosYRatio);

        mLayoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        mLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        mLayoutParams.x = posX;
        mLayoutParams.y = posY;
        mWindowManager.updateViewLayout(mRootContainer, mLayoutParams);
    }

    private List<AirCommandArcView.ToolModel> buildToolModels() {
        List<AirCommandArcView.ToolModel> list = new ArrayList<>();
        String toolsPref = mPrefs.getString(PREF_AIR_COMMAND_TOOLS, DEFAULT_TOOLS);
        List<String> toolList = Arrays.asList(toolsPref.split(","));
        PackageManager pm = mContext.getPackageManager();

        for (String item : toolList) {
            String toolId = item.trim();
            if (toolId.isEmpty()) continue;

            if ("laser_pointer".equals(toolId)) {
                boolean active = AirViewManager.getInstance() != null && AirViewManager.getInstance().isLaserMode();
                list.add(new AirCommandArcView.ToolModel(toolId, active ? "Laser pointer (On)" : "Laser pointer",
                        R.drawable.ic_tool_laser, R.drawable.bg_tool_circle_red));
            } else if ("quick_note".equals(toolId)) {
                list.add(new AirCommandArcView.ToolModel(toolId, "Quick note",
                        R.drawable.ic_tool_note, R.drawable.bg_tool_circle_orange));
            } else if ("capture".equals(toolId)) {
                list.add(new AirCommandArcView.ToolModel(toolId, "Capture",
                        R.drawable.ic_tool_screenshot, R.drawable.bg_tool_circle_teal));
            } else if ("circle_to_search".equals(toolId)) {
                list.add(new AirCommandArcView.ToolModel(toolId, "Circle to Search",
                        R.drawable.ic_tool_search, R.drawable.bg_tool_circle_google_blue));
            } else if ("settings".equals(toolId)) {
                list.add(new AirCommandArcView.ToolModel(toolId, "Stylo settings",
                        R.drawable.ic_tool_settings, R.drawable.bg_tool_circle_purple));
            } else if (toolId.startsWith("app:")) {
                String pkg = toolId.substring(4);
                try {
                    ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                    CharSequence label = pm.getApplicationLabel(appInfo);
                    Drawable icon = pm.getApplicationIcon(appInfo);
                    list.add(new AirCommandArcView.ToolModel(toolId, label.toString(), icon));
                } catch (Exception ignored) {}
            }
        }
        return list;
    }

    private void handleToolClick(String toolId) {
        Log.i(TAG, "handleToolClick: " + toolId);
        if ("laser_pointer".equals(toolId)) {
            toggleLaserMode();
        } else if ("quick_note".equals(toolId)) {
            launchQuickNote();
        } else if ("capture".equals(toolId)) {
            launchCapture();
        } else if ("circle_to_search".equals(toolId)) {
            launchCircleToSearch();
        } else if ("settings".equals(toolId)) {
            Intent intent = new Intent(mContext, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            mContext.startActivity(intent);
        } else if (toolId.startsWith("app:")) {
            String pkg = toolId.substring(4);
            launchPackage(pkg);
        }
    }

    private void toggleLaserMode() {
        AirViewManager airMgr = AirViewManager.getInstance(mContext);
        if (airMgr != null) {
            boolean current = airMgr.isLaserMode();
            boolean target = !current;
            airMgr.setLaserMode(target);
            Toast.makeText(mContext, target ? "Laser pointer on" : "Laser pointer off", Toast.LENGTH_SHORT).show();
            // Update local_config in background if available
            new Thread(() -> {
                StylusHapticsManager.executeRoot("sed -i 's/ipe_laser_mode\" value=\"[a-z]*\"/ipe_laser_mode\" value=\"" + target + "\"/g' /data/user/0/com.oplus.ipemanager/shared_prefs/local_config.xml");
            }).start();
        }
    }

    private void launchCapture() {
        mHandler.postDelayed(() -> {
            if (mService != null) {
                mService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT);
            } else {
                new Thread(() -> {
                    StylusHapticsManager.executeRoot("input keyevent 120"); // KEYCODE_SYSRQ
                }).start();
            }
        }, 120);
    }

    private void launchQuickNote() {
        mHandler.postDelayed(() -> {
            launchInFloatingWindow(mContext, "quick_note", null);
        }, 100);
    }

    private void launchCircleToSearch() {
        mHandler.postDelayed(() -> {
            new Thread(() -> {
                // Execute native contextual search service call (entrypoint 1: navigation pill hold)
                boolean ok = StylusHapticsManager.executeRoot("service call contextual_search 3 i32 1 i32 0");
                if (!ok) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_ASSIST);
                        intent.setPackage("com.google.android.googlequicksearchbox");
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivity(intent);
                    } catch (Throwable t) {
                        try {
                            Intent generic = new Intent(Intent.ACTION_ASSIST);
                            generic.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            mContext.startActivity(generic);
                        } catch (Throwable ignored) {}
                    }
                }
            }).start();
        }, 120);
    }

    private void launchPackage(String pkg) {
        launchInFloatingWindow(mContext, pkg, null);
    }

    public static void launchInFloatingWindow(Context context, String pkg, String action) {
        // 1. In-process reflection with OplusZoomWindowManager
        try {
            Intent intent;
            if (action != null && !action.isEmpty() && !action.equals("null")) {
                intent = new Intent(action);
                intent.setPackage(pkg);
            } else {
                intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
            }
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Class<?> clazz = Class.forName("com.oplus.zoomwindow.OplusZoomWindowManager");
                Object instance = clazz.getMethod("getInstance").invoke(null);
                Bundle bundle = new Bundle();
                bundle.putInt("start_style", 27); // FLAG_ON_START_FLOATWINDOW_FROM_DESKTOP
                bundle.putInt("window_type", 1); // WINDOW_TYPE_ZOOM
                Method m = clazz.getMethod("startZoomWindow", Intent.class, Bundle.class, int.class, String.class);
                int res = (int) m.invoke(instance, intent, bundle, 0, "com.coloros.smartsidebar");
                if (res == 0) {
                    return;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "In-process startZoomWindow returned non-zero or threw: " + t.getMessage());
        }

        // 2. Elevated execution via launch_zoom.dex
        new Thread(() -> {
            try {
                File dexFile = new File(context.getFilesDir(), "launch_zoom.dex");
                if (!dexFile.exists() || dexFile.length() == 0) {
                    extractAsset(context, "launch_zoom.dex", dexFile);
                }
                String dexPath = dexFile.getAbsolutePath();
                if (!dexFile.exists() || dexFile.length() == 0) {
                    // Check fallback system path
                    File alt = new File("/data/user/0/com.agy/files/launch_zoom.dex");
                    if (alt.exists()) {
                        dexPath = alt.getAbsolutePath();
                    }
                }
                String cmd = "CLASSPATH=" + dexPath + ":/my_stock/app/SmartSideBar/SmartSideBar.apk:/system/framework/framework.jar:/system/framework/oplus-framework.jar app_process "
                        + context.getFilesDir().getAbsolutePath() + " LaunchZoom " + pkg + (action != null ? " " + action : " null");
                boolean ok = StylusHapticsManager.executeRoot(cmd);
                if (!ok) {
                    // Fallback to standard launch
                    fallbackLaunch(context, pkg, action);
                }
            } catch (Throwable t) {
                Log.e(TAG, "Elevated launch failed", t);
                fallbackLaunch(context, pkg, action);
            }
        }).start();
    }

    private static void fallbackLaunch(Context context, String pkg, String action) {
        try {
            Intent fallback;
            if (action != null && !action.isEmpty() && !action.equals("null")) {
                fallback = new Intent(action);
                fallback.setPackage(pkg);
            } else {
                fallback = context.getPackageManager().getLaunchIntentForPackage(pkg);
            }
            if (fallback != null) {
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
            }
        } catch (Throwable ignored) {}
    }

    private static void extractAsset(Context context, String assetName, File outFile) {
        try {
            InputStream in = context.getAssets().open(assetName);
            FileOutputStream out = new FileOutputStream(outFile);
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.flush();
            out.close();
            in.close();
            outFile.setReadable(true, false);
        } catch (Throwable ignored) {}
    }
}
