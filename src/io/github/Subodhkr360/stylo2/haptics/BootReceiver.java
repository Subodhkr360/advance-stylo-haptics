package io.github.Subodhkr360.stylo2.haptics;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "StyloBootReceiver";

    @Override
    public void onReceive(final Context context, Intent intent) {
        Log.i(TAG, "Boot received, auto-checking root and initializing Stylo Haptics");
        final StylusHapticsManager manager = StylusHapticsManager.getInstance(context);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    manager.applyRootSetup();
                    manager.bind();
                    LandscapeViewManager.getInstance(context).reapplyAllOverrides();
                } catch (Throwable t) {
                    Log.e(TAG, "Boot root setup error: " + t.getMessage());
                }
            }
        }).start();
    }
}
