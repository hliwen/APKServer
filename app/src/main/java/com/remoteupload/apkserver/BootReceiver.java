package com.remoteupload.apkserver;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @SuppressLint("UnsafeIntentLaunch")
    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent == null) return;
        String action = intent.getAction();
        if (action == null) return;

        if (action.contains("android.intent.action.BOOT_COMPLETED") || action.equals("android.media.VOLUME_CHANGED_ACTION")) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setClass(context, MainActivity.class);
            context.startActivity(intent);
        }
    }
}
