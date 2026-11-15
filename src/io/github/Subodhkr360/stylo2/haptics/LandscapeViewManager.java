package io.github.Subodhkr360.stylo2.haptics;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

public class LandscapeViewManager {
    private static final String TAG = "LandscapeViewManager";
    public static final String PREF_LANDSCAPE_APPS = "landscape_compat_apps";
    public static final String PREF_LANDSCAPE_SHOW_SYSTEM = "pref_landscape_show_system";

    private static LandscapeViewManager sInstance;
    private final Context mContext;
    private final SharedPreferences mPrefs;
    private final Set<String> mEnabledApps = new HashSet<>();

    public static synchronized LandscapeViewManager getInstance(Context context) {
        if (sInstance == null && context != null) {
            sInstance = new LandscapeViewManager(context);
        }
        return sInstance;
    }

    private LandscapeViewManager(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = mContext.getSharedPreferences("stylo_haptics_prefs", Context.MODE_PRIVATE);
        loadSavedPackages();
        loadSystemOverridesInBackground();
    }

    private synchronized void loadSavedPackages() {
        Set<String> saved = mPrefs.getStringSet(PREF_LANDSCAPE_APPS, null);
        if (saved != null) {
            mEnabledApps.addAll(saved);
        }
    }

    public synchronized boolean isLandscapeCompatEnabled(String packageName) {
        return mEnabledApps.contains(packageName);
    }

    public synchronized void setLandscapeCompatEnabled(String packageName, boolean enabled) {
        if (packageName == null || packageName.isEmpty()) return;

        if (enabled) {
            mEnabledApps.add(packageName);
        } else {
            mEnabledApps.remove(packageName);
        }

        mPrefs.edit().putStringSet(PREF_LANDSCAPE_APPS, new HashSet<>(mEnabledApps)).apply();

        // Run compat command via root in background thread
        new Thread(() -> {
            applyCompatOverride(packageName, enabled);
        }).start();
    }

    private void applyCompatOverride(String packageName, boolean enable) {
        try {
            // Global WindowManager enforcement
            if (enable) {
                StylusHapticsManager.executeRoot(
                    "wm fixed-to-user-rotation enabled",
                    "wm set-ignore-orientation-request true",
                    "wm set-multi-window-config --supportsNonResizable 1",
                    "wm set-letterbox-style --isUserAppAspectRatioFullscreenEnabled 1",
                    "wm set-letterbox-style --isUserAppAspectRatioSettingsEnabled 1"
                );
            }

            String[] changes = {
                    "OVERRIDE_ANY_ORIENTATION",
                    "OVERRIDE_ANY_ORIENTATION_TO_USER",
                    "OVERRIDE_ENABLE_COMPAT_IGNORE_REQUESTED_ORIENTATION",
                    "OVERRIDE_USE_DISPLAY_LANDSCAPE_NATURAL_ORIENTATION",
                    "OVERRIDE_ENABLE_COMPAT_IGNORE_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED"
            };

            for (String change : changes) {
                String cmd = enable ?
                        "am compat enable " + change + " " + packageName :
                        "am compat reset " + change + " " + packageName;
                StylusHapticsManager.executeRoot(cmd);
            }
            Log.i(TAG, "Applied landscape compat " + (enable ? "ENABLE" : "RESET") + " for " + packageName);
        } catch (Throwable t) {
            Log.e(TAG, "Failed applying compat override for " + packageName, t);
        }
    }

    private void loadSystemOverridesInBackground() {
        new Thread(() -> {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "dumpsys platform_compat | grep 'OVERRIDE_ANY_ORIENTATION;'"});
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("packageOverrides={")) {
                        int start = line.indexOf("packageOverrides={") + "packageOverrides={".length();
                        int end = line.indexOf("}", start);
                        if (end > start) {
                            String mapContent = line.substring(start, end);
                            String[] pairs = mapContent.split(",");
                            synchronized (this) {
                                for (String pair : pairs) {
                                    String[] kv = pair.trim().split("=");
                                    if (kv.length == 2 && "true".equalsIgnoreCase(kv[1].trim())) {
                                        mEnabledApps.add(kv[0].trim());
                                    }
                                }
                                mPrefs.edit().putStringSet(PREF_LANDSCAPE_APPS, new HashSet<>(mEnabledApps)).apply();
                            }
                        }
                    }
                }
                reader.close();
                p.waitFor();
            } catch (Throwable ignored) {}
        }).start();
    }

    public synchronized void reapplyAllOverrides() {
        new Thread(() -> {
            for (String pkg : new HashSet<>(mEnabledApps)) {
                applyCompatOverride(pkg, true);
            }
        }).start();
    }
}
