package com.remoteupload.apkserver;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.core.content.PermissionChecker;

import java.util.ArrayList;

public class MainActivity extends Activity {

    private static final String TAG = "apkServerlog";

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.e(TAG, "MainActivity onCreate: ");
        String[] value = haveNoPermissions(MainActivity.this);
        if (value.length > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(value, 111);
        }

        Intent serviceIntent = new Intent(MainActivity.this, ObserverServer.class);
        startService(serviceIntent);
    }

    public static String[] haveNoPermissions(Context mActivity) {
        ArrayList<String> haveNo = new ArrayList<>();
        for (String permission : PERMISSIONS) {
            if (PermissionChecker.checkPermission(mActivity, permission, Binder.getCallingPid(), Binder.getCallingUid(), mActivity.getPackageName()) != PermissionChecker.PERMISSION_GRANTED) {
                haveNo.add(permission);
            }
        }

        return haveNo.toArray(new String[haveNo.size()]);
    }

    public static final String[] PERMISSIONS = {
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.QUERY_ALL_PACKAGES,
            android.Manifest.permission.PACKAGE_USAGE_STATS,
            Manifest.permission.GET_TASKS,

    };
}
