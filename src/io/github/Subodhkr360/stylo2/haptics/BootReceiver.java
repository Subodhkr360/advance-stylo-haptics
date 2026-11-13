package io.github.Subodhkr360.stylo2.haptics;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "StyloBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "Boot received, initializing Stylo Haptics");
        StylusHapticsManager manager = StylusHapticsManager.getInstance(context);
        manager.bind();
    }
}
