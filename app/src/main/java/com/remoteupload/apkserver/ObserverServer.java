package com.remoteupload.apkserver;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

public class ObserverServer extends Service {

    private static final String TAG = "apkServerlog";
    WIFIConnectBroadcast wifiConnectBroadcast;
    static String appName = "RemoteUpload.apk";
    static String downloadPathDir = "/storage/emulated/0/Download/";
    public static final String appVersionURL = "https://www.iothm.top:12443/v2/app/autoUpdate/V3/version/latest";
    public static final String appDowloadURL = "https://www.iothm.top:12443/v2/app/autoUpdate/V3/version/";

    class WIFIConnectBroadcast extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e(TAG, "onReceive: " + intent.getAction());
            if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (info.getState().equals(NetworkInfo.State.CONNECTED)) {
                    if (info.getType() == ConnectivityManager.TYPE_WIFI) {
                        Log.d(TAG, "onReceive: 连接wifi ");
                        netWorkConnect();
                    }
                }
            }
        }
    }


    private boolean doning;

    private void netWorkConnect() {
        Log.d(TAG, "netWorkConnect: ");
        if (doning) return;
        doning = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!isAppInstalled(getApplicationContext(), "com.example.nextclouddemo")) {
                    getServiceVersion();
                }
                doning = false;
            }
        }).start();

    }


    @Override
    public void onCreate() {
        super.onCreate();
        Log.e(TAG, "onCreate: ");

        wifiConnectBroadcast = new WIFIConnectBroadcast();
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(wifiConnectBroadcast, filter);

        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest.Builder request = new NetworkRequest.Builder();
        request.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        request.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);

        NetworkRequest build = request.build();
        connectivityManager.requestNetwork(build, new ConnectivityManager.NetworkCallback() {
            public void onAvailable(Network network) {
                Log.d(TAG, "onAvailable: ");
                netWorkConnect();
            }

            @Override
            public void onLost(Network network) {
                super.onLost(network);
                Log.e(TAG, "Network  onLost: ");
            }
        });

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: ");
        super.onDestroy();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind: ");
        return super.onUnbind(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind: ");
        return null;
    }


    private boolean isAppInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private int getServiceVersion() {

        int servierVersion = 0;
        try {
            URL url = new URL(appVersionURL);
            HttpURLConnection urlcon = (HttpURLConnection) url.openConnection();
            int ResponseCode = urlcon.getResponseCode();
            if (ResponseCode != 200) {
                return 0;
            }

            InputStream inputStream = urlcon.getInputStream();
            InputStreamReader isr = new InputStreamReader(inputStream, "UTF-8");
            BufferedReader reader = new BufferedReader(isr);
            String line;
            StringBuffer buffer = new StringBuffer();
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }
            String content = buffer.toString();
            Log.d(TAG, "run:  nccontent = " + content);
            JSONObject jsonObject = new JSONObject(content);
            JSONArray jsonArray = new JSONArray(jsonObject.getString("data"));
            jsonObject = new JSONObject(jsonArray.getString(0));
            String version = jsonObject.getString("version");
            servierVersion = Integer.parseInt(version);

            File apkFile = new File(downloadPathDir + servierVersion + "_" + appName);
            if (apkFile == null || !apkFile.exists()) {
                boolean downloadSucced = startDownloadApp(appDowloadURL, servierVersion);
                Log.d(TAG, "run: startDownloadApp downloadSucced =" + downloadSucced);

                if (downloadSucced) {
                    installSilent(downloadPathDir + servierVersion + "_" + appName);
                }
            } else {
                installSilent(downloadPathDir + servierVersion + "_" + appName);
            }
        } catch (Exception e) {
            Log.e(TAG, "getServiceVersion: Exception =" + e);
        }
        Log.e(TAG, "getServiceVersion: servierVersion =" + servierVersion);
        return servierVersion;
    }

    public void installSilent(String path) {
        BufferedReader es = null;
        DataOutputStream os = null;
        try {
            Process process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            String command = "pm install -r " + path + "\n";
            os.write(command.getBytes(Charset.forName("utf-8")));
            os.flush();
            os.writeBytes("exit\n");
            os.flush();

            process.waitFor();
            es = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String line;
            StringBuilder builder = new StringBuilder();
            while ((line = es.readLine()) != null) {
                builder.append(line);
            }
            if (!builder.toString().contains("Failure")) {
                startActivity();
            }
        } catch (Exception e) {
            Log.e(TAG, "installSilent Exception =" + e);
        } finally {
            try {
                if (os != null) {
                    os.close();
                }
                if (es != null) {
                    es.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "installSilent IOException =" + e);
            }
        }
    }

    private void startActivity() {
        DataOutputStream localDataOutputStream = null;
        try {
            Runtime runtime = Runtime.getRuntime();
            Process process = runtime.exec("su");
            OutputStream localOutputStream = process.getOutputStream();
            localDataOutputStream = new DataOutputStream(localOutputStream);

            String command = "sleep 5 && am start -W -n com.example.nextclouddemo/com.example.nextclouddemo.MainActivity";
            localDataOutputStream.write(command.getBytes(Charset.forName("utf-8")));
            localDataOutputStream.flush();
        } catch (Exception e) {

        } finally {
            try {
                if (localDataOutputStream != null) {
                    localDataOutputStream.close();
                }

            } catch (IOException e) {

            }
        }
    }

    private boolean startDownloadApp(String downloadURL, int servierVersion) {
        Log.e(TAG, "startDownloadApp: servierVersion =" + servierVersion);
        boolean downloadSucced = false;
        File apkFile = new File(downloadPathDir + servierVersion + "_" + appName + "_tpm");
        if (apkFile != null && apkFile.exists()) {
            apkFile.delete();
        }
        apkFile = new File(downloadPathDir + servierVersion + "_" + appName + "_tpm");
        try {
            URL downloadurl = new URL(downloadURL);
            HttpURLConnection connection = (HttpURLConnection) downloadurl.openConnection();
            int ResponseCode = connection.getResponseCode();
            if (ResponseCode == 200 || ResponseCode == 206) {
                InputStream downloadInputStream = connection.getInputStream();
                FileOutputStream downloadFileOutputStream = new FileOutputStream(apkFile);
                byte[] buffer = new byte[2048 * 8];
                int lenght;
                while ((lenght = downloadInputStream.read(buffer)) != -1) {
                    downloadFileOutputStream.write(buffer, 0, lenght);
                }
                downloadFileOutputStream.flush();
                downloadInputStream.close();
                downloadFileOutputStream.close();
                downloadSucced = true;

                File apkFileA = new File(downloadPathDir + servierVersion + "_" + appName);
                apkFile.renameTo(apkFileA);

            } else {

            }
        } catch (Exception e) {

            Log.d(TAG, "startDownloadApp: e =" + e);
        }
        return downloadSucced;
    }

}
