package io.github.Subodhkr360.stylo2.haptics;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.oplus.ipemanager.sdk.ISdkAidlInterface;
import com.oplus.ipemanager.sdk.Vibration;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class StylusHapticsManager {
    private static final String TAG = "StyloHapticsManager";
    private static final String ACTION_PENCIL_SDK = "com.oplus.ipemanager.ACTION.PENCIL_SDK";
    private static final String PKG_IPE = "com.oplus.ipemanager";
    private static final String PREFS_NAME = "stylo_haptics_prefs";

    public static final String PREF_FUNCTIONAL_FEEDBACK = "pref_functional_feedback";
    public static final String PREF_HANDWRITING_FEEDBACK = "pref_handwriting_feedback";
    public static final String PREF_GLOBAL_MODE = "pref_global_mode";
    public static final String PREF_GLOBAL_PATTERN = "pref_global_pattern";
    public static final String PREF_GLOBAL_STRENGTH = "pref_global_strength";
    public static final String PREF_WHITELIST = "pref_whitelist";

    // Vibration Patterns (Native hardware waveforms)
    public static final int PATTERN_PENCIL = 0;       // Graphite paper texture
    public static final int PATTERN_ERASER = 1;       // Firm friction feel
    public static final int PATTERN_BALLPOINT = 2;    // Crisp roller tick
    public static final int PATTERN_FOUNTAIN = 3;     // Smooth fluid glide

    public static class AppProfile {
        public String packageName;
        public boolean enabled;
        public int pattern;    // 0..3
        public int strength;   // 1..5

        public AppProfile(String packageName, boolean enabled, int pattern, int strength) {
            this.packageName = packageName;
            this.enabled = enabled;
            this.pattern = pattern;
            this.strength = strength;
        }
    }

    public interface StateListener {
        void onConnectionStateChanged(boolean connected, String status);
    }

    private static StylusHapticsManager sInstance;

    private Context mAppContext;
    private SharedPreferences mPrefs;
    private ISdkAidlInterface mSdk;
    private boolean mIsBound = false;
    private boolean mIsDrawingVibrating = false;
    private int mCurrentVibrationType = -1;
    private int mCurrentStrength = -1;
    private String mCurrentForegroundApp = "";
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private StateListener mStateListener;

    private static final Set<String> KNOWN_DRAWING_APPS = new HashSet<>(Arrays.asList(
        "com.oneplus.notes", "com.coloros.note", "com.google.android.keep",
        "com.brakefield.painter", "com.adsk.sketchbook", "org.sketchbook",
        "com.microsoft.office.onenote", "com.samsung.android.app.notes",
        "com.taptap.hipaint", "com.taptap.artflow", "com.sonyericsson.sketch",
        "ai.ncert.physicswala", "xyz.penpencil.physicswala"
    ));

    public static synchronized StylusHapticsManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new StylusHapticsManager(context.getApplicationContext());
        }
        return sInstance;
    }

    public static synchronized StylusHapticsManager getInstance() {
        return sInstance;
    }

    private StylusHapticsManager(Context context) {
        mAppContext = context.getApplicationContext();
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        seedDefaultProfiles();
    }

    private void seedDefaultProfiles() {
        if (!mPrefs.contains(PREF_WHITELIST)) {
            Set<String> defaults = new HashSet<>();
            for (String pkg : KNOWN_DRAWING_APPS) {
                defaults.add(pkg);
                saveAppProfile(new AppProfile(pkg, true, PATTERN_PENCIL, 4));
            }
            defaults.add("com.agy");
            saveAppProfile(new AppProfile("com.agy", true, PATTERN_PENCIL, 5));
            mPrefs.edit().putStringSet(PREF_WHITELIST, defaults).apply();
        }
    }

    public boolean isSystemUiOrLauncher(String pkg) {
        if (pkg == null) return false;
        return pkg.contains("systemui") || pkg.contains("launcher") || pkg.contains("quicksearchbox");
    }

    public boolean isDrawingApp(String pkg) {
        if (pkg == null) return false;
        return KNOWN_DRAWING_APPS.contains(pkg) || pkg.contains("note") || pkg.contains("sketch") || pkg.contains("paint") || pkg.contains("draw");
    }

    public AppProfile getAppProfile(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return new AppProfile("", false, PATTERN_PENCIL, 4);
        }

        boolean isOwnApp = mAppContext.getPackageName().equals(packageName);
        if (isOwnApp) {
            return new AppProfile(packageName, false, PATTERN_PENCIL, 4);
        }
        boolean defaultEnabled = isDrawingApp(packageName) || "com.agy".equals(packageName);

        boolean enabled;
        if (mPrefs.contains("app_enabled_" + packageName)) {
            enabled = mPrefs.getBoolean("app_enabled_" + packageName, defaultEnabled);
        } else {
            Set<String> whitelist = mPrefs.getStringSet(PREF_WHITELIST, null);
            if (whitelist != null && whitelist.contains(packageName)) {
                enabled = true;
            } else {
                enabled = defaultEnabled;
            }
        }

        int defaultPattern = mPrefs.getInt(PREF_GLOBAL_PATTERN, PATTERN_PENCIL);
        int pattern = mPrefs.getInt("app_pattern_" + packageName, defaultPattern);
        int defaultStrength = mPrefs.getInt(PREF_GLOBAL_STRENGTH, 4);
        int strength = mPrefs.getInt("app_strength_" + packageName, defaultStrength);

        return new AppProfile(packageName, enabled, pattern, strength);
    }

    public void saveAppProfile(AppProfile profile) {
        if (profile == null || profile.packageName == null) return;
        mPrefs.edit()
            .putBoolean("app_enabled_" + profile.packageName, profile.enabled)
            .putInt("app_pattern_" + profile.packageName, profile.pattern)
            .putInt("app_strength_" + profile.packageName, profile.strength)
            .apply();

        Set<String> oldWhitelist = mPrefs.getStringSet(PREF_WHITELIST, new HashSet<String>());
        Set<String> newWhitelist = new HashSet<>(oldWhitelist);
        if (profile.enabled) {
            newWhitelist.add(profile.packageName);
        } else {
            newWhitelist.remove(profile.packageName);
        }
        mPrefs.edit().putStringSet(PREF_WHITELIST, newWhitelist).apply();

        if (profile.packageName.equals(mCurrentForegroundApp)) {
            applyProfile(profile);
            if (profile.enabled && mPrefs.getBoolean(PREF_HANDWRITING_FEEDBACK, true)) {
                startDrawingFeedback();
            } else {
                stopDrawingFeedback();
            }
        }
    }

    public void setStateListener(StateListener listener) {
        mStateListener = listener;
        notifyState();
    }

    private void notifyState() {
        if (mStateListener != null) {
            boolean ready = mIsBound && (mSdk != null);
            String status = ready ? "Connected" : "Disconnected";
            mStateListener.onConnectionStateChanged(ready, status);
        }
    }

    private final ServiceConnection mConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.i(TAG, "onServiceConnected: " + name);
            try {
                mSdk = ISdkAidlInterface.Stub.asInterface(binder);
                mIsBound = true;
                notifyState();
                // Sync initial hardware settings
                syncHardwareLevel(mPrefs.getInt(PREF_GLOBAL_STRENGTH, 4));
                setNativeVibrationType(getNativeType(mPrefs.getInt(PREF_GLOBAL_PATTERN, PATTERN_PENCIL)));
                if (mPrefs.getBoolean(PREF_HANDWRITING_FEEDBACK, true)) {
                    startDrawingFeedback();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error resolving ISdkAidlInterface", e);
                mSdk = null;
                mIsBound = false;
                notifyState();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "onServiceDisconnected: " + name);
            mSdk = null;
            mIsBound = false;
            mIsDrawingVibrating = false;
            notifyState();
        }
    };

    public synchronized void bind() {
        if (mIsBound && mSdk != null) return;
        Intent intent = new Intent(ACTION_PENCIL_SDK);
        intent.setPackage(PKG_IPE);
        try {
            boolean ok = mAppContext.bindService(intent, mConn, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT | Context.BIND_ABOVE_CLIENT);
            if (!ok && mStateListener != null) {
                mStateListener.onConnectionStateChanged(false, "Service Not Found");
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception on bind", e);
            if (mStateListener != null) {
                mStateListener.onConnectionStateChanged(false, "Bind Error: " + e.getMessage());
            }
        }
    }

    public synchronized void unbind() {
        if (!mIsBound) return;
        try {
            stopDrawingFeedback();
            mAppContext.unbindService(mConn);
        } catch (Exception ignored) {}
        mIsBound = false;
        mSdk = null;
        mIsDrawingVibrating = false;
        notifyState();
    }

    public boolean isConnected() {
        return mIsBound && (mSdk != null);
    }

    public int getNativeType(int pattern) {
        switch (pattern) {
            case PATTERN_PENCIL:
                return Vibration.PENCIL.ordinal();
            case PATTERN_ERASER:
                return Vibration.ERASER.ordinal();
            case PATTERN_BALLPOINT:
                return Vibration.BALLPEN.ordinal();
            case PATTERN_FOUNTAIN:
                return Vibration.PEN.ordinal();
            default:
                return Vibration.PENCIL.ordinal();
        }
    }

    public void applyProfile(AppProfile profile) {
        if (profile == null) return;
        int nativeType = getNativeType(profile.pattern);
        int targetStrength = Math.max(1, Math.min(5, profile.strength));
        setNativeVibrationType(nativeType);
        syncHardwareLevel(targetStrength);
    }

    public void setNativeVibrationType(int typeOrdinal) {
        mCurrentVibrationType = typeOrdinal;
        if (mSdk != null) {
            try {
                mSdk.setVibrationType(typeOrdinal);
            } catch (Exception e) {
                Log.e(TAG, "Failed setVibrationType", e);
            }
        }
        try {
            Uri uri = Uri.parse("content://com.oplus.ipemanager.provider/integer/local_config/sp_key_unic_write_vibration_type/" + typeOrdinal);
            ContentValues cv = new ContentValues();
            cv.put("sp_key_unic_write_vibration_type", typeOrdinal);
            mAppContext.getContentResolver().insert(uri, cv);
        } catch (Exception ignored) {}
    }

    public void syncHardwareLevel(final int level) {
        mCurrentStrength = level;
        try {
            Uri uri = Uri.parse("content://com.oplus.ipemanager.provider/integer/local_config/sp_key_unic_written_feedback_level/" + level);
            ContentValues cv = new ContentValues();
            cv.put("sp_key_unic_written_feedback_level", level);
            mAppContext.getContentResolver().insert(uri, cv);
        } catch (Exception ignored) {}
    }

    public void syncFunctionalFeedback(boolean enabled) {
        try {
            Uri uri = Uri.parse("content://com.oplus.ipemanager.provider/boolean/local_config/SP_KEY_UNIC_FUNCTION_FEEDBACK_STATE/" + enabled);
            ContentValues cv = new ContentValues();
            cv.put("SP_KEY_UNIC_FUNCTION_FEEDBACK_STATE", enabled);
            mAppContext.getContentResolver().insert(uri, cv);
        } catch (Exception ignored) {}
    }

    public void syncHandwritingFeedbackState(boolean enabled) {
        try {
            Uri uri = Uri.parse("content://com.oplus.ipemanager.provider/boolean/local_config/sp_key_unic_written_feedback_state/" + enabled);
            ContentValues cv = new ContentValues();
            cv.put("sp_key_unic_written_feedback_state", enabled);
            mAppContext.getContentResolver().insert(uri, cv);
        } catch (Exception ignored) {}
        if (enabled) {
            startDrawingFeedback();
        } else {
            stopDrawingFeedback();
        }
    }

    private volatile boolean mIsTestingFeedback = false;

    private synchronized void writeTouchpanelPencilControl(int val) {
        // Direct write to /proc/touchpanel/pencil_control (0666 world-writable)
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream("/proc/touchpanel/pencil_control")) {
            fos.write(String.valueOf(val).getBytes());
            fos.flush();
            return;
        } catch (Throwable t) {
            // direct write fallback
        }

        // Fallback to su if direct write blocked by SELinux
        String[] suCandidates = {"/system/bin/su", "/data/adb/ksu/bin/su", "su"};
        for (String suBin : suCandidates) {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{suBin, "-c", "echo " + val + " > /proc/touchpanel/pencil_control"});
                p.waitFor();
                return;
            } catch (Throwable ignored) {}
        }
    }

    public synchronized void startDrawingFeedback() {
        if (!mPrefs.getBoolean(PREF_HANDWRITING_FEEDBACK, true)) return;
        try {
            if (mSdk != null) {
                mSdk.startFeedBackVibration();
            }
            writeTouchpanelPencilControl(3);
            mIsDrawingVibrating = true;
        } catch (Exception e) {
            Log.e(TAG, "startDrawingFeedback failed", e);
        }
    }

    public synchronized void stopDrawingFeedback() {
        try {
            if (mSdk != null) {
                mSdk.stopFeedBackVibration();
            }
            writeTouchpanelPencilControl(2);
            mIsDrawingVibrating = false;
        } catch (Exception e) {
            Log.e(TAG, "stopDrawingFeedback failed", e);
        }
    }

    private Runnable mCancelTestRunnable;

    public synchronized void triggerTestFeedback(int durationMs) {
        mIsTestingFeedback = true;
        if (mSdk == null) {
            bind();
        }
        startDrawingFeedback();
        if (mCancelTestRunnable != null) {
            mHandler.removeCallbacks(mCancelTestRunnable);
        }
        mCancelTestRunnable = new Runnable() {
            @Override
            public void run() {
                mIsTestingFeedback = false;
                if (mAppContext.getPackageName().equals(mCurrentForegroundApp)) {
                    stopDrawingFeedback();
                }
            }
        };
        mHandler.postDelayed(mCancelTestRunnable, Math.max(1000, durationMs));
    }

    public synchronized void triggerPattern(int pattern, int strength) {
        int nativeType = getNativeType(pattern);
        setNativeVibrationType(nativeType);
        syncHardwareLevel(Math.max(1, Math.min(5, strength)));

        // Directly trigger the stylus linear motor so it vibrates immediately in hand!
        if (mSdk != null) {
            try {
                if (!mSdk.isDemoModeEnable()) {
                    mSdk.enableDemoMode(true);
                }
            } catch (Throwable ignored) {}
            try {
                mSdk.startVibration(nativeType);
            } catch (Throwable t) {
                try {
                    mSdk.startVibration(4); // FUNCTION_VIBRATION fallback
                } catch (Throwable ignored) {}
            }
        }

        // Keep drawing feedback armed so testing with pen on screen also vibrates
        triggerTestFeedback(2500);
    }

    public boolean isSystemOverlay(String pkg) {
        if (pkg == null) return false;
        return pkg.equals("com.android.systemui")
            || pkg.equals("android")
            || pkg.contains("screenshot")
            || pkg.contains("notification");
    }

    public boolean isLauncher(String pkg) {
        if (pkg == null) return false;
        return pkg.contains("launcher")
            || pkg.contains("assistantscreen")
            || pkg.contains("quickstep");
    }

    private String mActiveImePackage = null;

    public synchronized void updateActiveAppState(String topAppPackage, String imePackage) {
        mCurrentForegroundApp = topAppPackage;
        mActiveImePackage = imePackage;

        // If user is currently triggering or testing vibration, do not disarm!
        if (mIsTestingFeedback) {
            return;
        }

        boolean handwritingOn = mPrefs.getBoolean(PREF_HANDWRITING_FEEDBACK, true);
        if (!handwritingOn) {
            stopDrawingFeedback();
            return;
        }

        if (mAppContext.getPackageName().equals(topAppPackage)) {
            stopDrawingFeedback();
            return;
        }

        boolean globalMode = mPrefs.getBoolean(PREF_GLOBAL_MODE, false);

        if (globalMode) {
            // Global mode works unconditionally everywhere!
            AppProfile activeProfile = null;
            if (imePackage != null) {
                AppProfile imeP = getAppProfile(imePackage);
                if (imeP.enabled) activeProfile = imeP;
            }
            if (activeProfile == null && topAppPackage != null) {
                AppProfile appP = getAppProfile(topAppPackage);
                if (appP.enabled) activeProfile = appP;
            }
            if (activeProfile != null) {
                applyProfile(activeProfile);
            } else {
                int defPattern = mPrefs.getInt(PREF_GLOBAL_PATTERN, PATTERN_PENCIL);
                int defStrength = mPrefs.getInt(PREF_GLOBAL_STRENGTH, 4);
                applyProfile(new AppProfile("", true, defPattern, defStrength));
            }
            startDrawingFeedback();
            return;
        }

        // Per-App Profile Mode:
        // Priority 1: Check active IME (e.g. GboardWu dev.jason.com.google.android.inputmethod.latin)
        if (imePackage != null) {
            AppProfile imeProfile = getAppProfile(imePackage);
            if (imeProfile.enabled) {
                applyProfile(imeProfile);
                startDrawingFeedback();
                return;
            }
        }

        // Priority 2: Check foreground application
        if (topAppPackage != null && !isSystemOverlay(topAppPackage)) {
            mCurrentForegroundApp = topAppPackage;
            if (topAppPackage.equals(mAppContext.getPackageName())) {
                stopDrawingFeedback();
                return;
            }
            AppProfile appProfile = getAppProfile(topAppPackage);
            if (appProfile.enabled) {
                applyProfile(appProfile);
                startDrawingFeedback();
                return;
            }
        }

        // Neither enabled -> disarm haptics
        stopDrawingFeedback();
    }

    public synchronized void onForegroundAppChanged(String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        if (isSystemOverlay(packageName)) return;
        mCurrentForegroundApp = packageName;
        if (mAppContext.getPackageName().equals(packageName)) {
            stopDrawingFeedback();
            return;
        }
        updateActiveAppState(packageName, mActiveImePackage);
    }

    public Context getAppContext() {
        return mAppContext;
    }

    public SharedPreferences getPrefs() {
        return mPrefs;
    }
}
