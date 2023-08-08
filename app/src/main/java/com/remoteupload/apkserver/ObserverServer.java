package com.remoteupload.apkserver;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.jahnen.libaums.core.UsbMassStorageDevice;
import me.jahnen.libaums.core.fs.FileSystem;
import me.jahnen.libaums.core.fs.UsbFile;
import me.jahnen.libaums.core.fs.UsbFileInputStream;
import me.jahnen.libaums.core.partition.Partition;

public class ObserverServer extends Service {
    public static final String INIT_STORE_USB_PERMISSION = "INIT_STORE_USB_PERMISSION";
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

    ReceiverStoreUSB receiverStoreUSB;

    class ReceiverStoreUSB extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            switch (action) {
                case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                    usbConnect(intent.getParcelableExtra(UsbManager.EXTRA_DEVICE));
                    break;
                case INIT_STORE_USB_PERMISSION:
                    Log.d(TAG, "StoreUSBReceiver onReceive: INIT_STORE_USB_PERMISSION");
                    initStoreUSBDevice();
                    break;
                default:
                    break;
            }
        }
    }

    private void usbConnect(UsbDevice usbDevice) {
        if (usbDevice == null) {
            return;
        }
        initStoreUSBDevice();
    }

    private ExecutorService initStoreUSBThreadExecutor;

    private void stopStoreUSBInitThreadExecutor() {
        Log.e(TAG, "stopStoreUSBInitThreadExecutor: ");
        try {
            if (initStoreUSBThreadExecutor != null) {
                initStoreUSBThreadExecutor.shutdown();
            }
        } catch (Exception e) {
        }
        initStoreUSBThreadExecutor = null;
    }

    private UsbMassStorageDevice getUsbMass(UsbDevice usbDevice) {
        UsbMassStorageDevice[] storageDevices = UsbMassStorageDevice.getMassStorageDevices(getApplicationContext());
        for (UsbMassStorageDevice device : storageDevices) {
            if (usbDevice.equals(device.getUsbDevice())) {
                return device;
            }
        }
        return null;
    }

    public void initStoreUSBDevice() {

        stopStoreUSBInitThreadExecutor();
        initStoreUSBThreadExecutor = Executors.newSingleThreadExecutor();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

                HashMap<String, UsbDevice> connectedUSBDeviceList = usbManager.getDeviceList();
                if (connectedUSBDeviceList == null || connectedUSBDeviceList.size() <= 0) {

                    return;
                }

                Collection<UsbDevice> usbDevices = connectedUSBDeviceList.values();
                if (usbDevices == null) {

                    return;
                }


                for (UsbDevice usbDevice : usbDevices) {
                    if (usbDevice == null) {
                        continue;
                    }
                    if (!usbManager.hasPermission(usbDevice)) {
                        @SuppressLint("UnspecifiedImmutableFlag") PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(INIT_STORE_USB_PERMISSION), 0);
                        usbManager.requestPermission(usbDevice, pendingIntent);
                        continue;
                    }

                    int interfaceCount = usbDevice.getInterfaceCount();
                    for (int i = 0; i < interfaceCount; i++) {
                        UsbInterface usbInterface = usbDevice.getInterface(i);
                        if (usbInterface == null) {
                            continue;
                        }
                        int interfaceClass = usbInterface.getInterfaceClass();

                        if (interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE) {
                            Log.e(TAG, "initStoreUSBDevice: 当前设设备为U盘");
                            UsbMassStorageDevice device = getUsbMass(usbDevice);
                            initDevice(device);
                        }
                    }
                }
            }
        };
        initStoreUSBThreadExecutor.execute(runnable);
    }

    private void initDevice(UsbMassStorageDevice device) {
        if (device == null) {
            return;
        }
        try {
            device.init();
        } catch (Exception e) {
            return;
        }

        if (device.getPartitions().size() <= 0) {
            return;
        }
        Partition partition = device.getPartitions().get(0);
        FileSystem currentFs = partition.getFileSystem();
        UsbFile mRootFolder = currentFs.getRootDirectory();

        try {
            UsbFile[] usbFileList = mRootFolder.listFiles();
            for (UsbFile usbFileItem : usbFileList) {
                if (usbFileItem.getName().contains(appName)) {
                    FileOutputStream out = null;
                    InputStream in = null;
                    String apkPath = null;
                    File apkFile = null;
                    try {
                        apkPath = downloadPathDir + appName;
                        apkFile = new File(apkPath);

                        if (apkFile.exists()) {
                            apkFile.delete();
                        }
                        out = new FileOutputStream(apkPath);
                        in = new UsbFileInputStream(usbFileItem);
                        int bytesRead = 0;
                        byte[] buffer = new byte[currentFs.getChunkSize()];
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "downloadUSBCameraPictureToTFCard: Exception =" + e);
                    } finally {
                        try {
                            if (out != null) {
                                out.flush();
                                out.close();
                            }

                            if (in != null) {
                                in.close();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "downloadUSBCameraPictureToTFCard : 11111 Exception =" + e);
                        }
                    }

                    apkFile = new File(apkPath);
                    if (apkFile.exists()) {
                        installSilent(apkPath);
                        device.close();
                        return;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "run: initDevice Exception =" + e);
        }

    }


    private void registerStoreUSBReceiver() {
        receiverStoreUSB = new ReceiverStoreUSB();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        intentFilter.addAction(INIT_STORE_USB_PERMISSION);
        registerReceiver(receiverStoreUSB, intentFilter);
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


        if (!isAppInstalled(getApplicationContext(), "com.example.nextclouddemo")) {
            registerStoreUSBReceiver();
        } else {
            int installVersionCode = getRemoteUploadInfo(getApplicationContext(), "com.example.nextclouddemo");
            int localVersionCode = getapkFileVersionCode(downloadPathDir + appName, getApplicationContext());
            Log.e(TAG, "onCreate: installVersionCode =" + installVersionCode + ",localVersionCode =" + localVersionCode);
        }
    }

    public int getapkFileVersionCode(String absPath, Context context) {
        Log.e(TAG, "apkInfo: absPath =" + absPath);
        PackageManager pm = context.getPackageManager();
        PackageInfo pkgInfo = pm.getPackageArchiveInfo(absPath, PackageManager.GET_ACTIVITIES);
        Log.e(TAG, "apkInfo: pkgInfo =" + pkgInfo);
        if (pkgInfo != null) {
            ApplicationInfo appInfo = pkgInfo.applicationInfo;
            /* 必须加这两句，不然下面icon获取是default icon而不是应用包的icon */
            appInfo.sourceDir = absPath;
            appInfo.publicSourceDir = absPath;
            String appName = pm.getApplicationLabel(appInfo).toString();// 得到应用名
            String packageName = appInfo.packageName; // 得到包名
            String version = pkgInfo.versionName; // 得到版本信息
            int versionCode = pkgInfo.versionCode; // 得到版本信息
            return versionCode;
        }
        return 0;
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

    private int getRemoteUploadInfo(Context context, String packageName) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName, 0);
            String versionName = packageInfo.versionName;
            int versionCode = packageInfo.versionCode;
            // 打印版本号
            Log.d("AppVersion", "Version Name: " + versionName);
            Log.d("AppVersion", "Version Code: " + versionCode);
            return versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return 0;
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

        if (receiverStoreUSB != null) {
            unregisterReceiver(receiverStoreUSB);
        }

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
