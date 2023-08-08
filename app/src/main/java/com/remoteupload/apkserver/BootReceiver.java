package com.remoteupload.apkserver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "apkServerlog";
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.e(TAG, "onReceive: "+intent.getAction() );
        Intent serviceIntent = new Intent(context, ObserverServer.class);
        context.startService(serviceIntent);
    }
}
