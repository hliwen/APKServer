package com.remoteupload.apkserver;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
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
import java.util.List;
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
    private static final String remoteApkPackageName = "com.example.nextclouddemo";
    WIFIConnectBroadcast wifiConnectBroadcast;
    static String appName = "remoteUpload.apk";
    static String usbUpdateName = "update.apk";
    static String usbUpdateBin = "update.bin";
    static String wifiConfigurationFileName = "wifiConfiguration";
    static String localUpdateDir = "/storage/emulated/0/Download/";
    public static final String appVersionURL = "https://www.iothm.top:12443/v2/app/autoUpdate/V3/version/latest";
    public static final String appDowloadURL = "https://www.iothm.top:12443/v2/app/autoUpdate/V3/version/";


    private static final String checkDevieNum = "checkDevieNum";
    private static final String checkUploadAPKState = "checkUploadAPKState";
    private static final String reInstallAPK = "reInstallAPK";
    private static final String shellcommand = "shellcommand:";
    private ReceiverStoreUSB receiverStoreUSB;
    private ExecutorService initStoreUSBThreadExecutor;
    private boolean initingNetwork;
    private boolean netWorkonAvailable;
    private String phoneImei;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.e(TAG, "ObserverServer onCreate: ");
        EventBus.getDefault().register(this);
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
                handler.removeMessages(msg_network_connect);
                handler.sendEmptyMessageDelayed(msg_network_connect, 2000);
            }

            @Override
            public void onLost(Network network) {
                super.onLost(network);
                Log.e(TAG, "Network  onLost: ");
                handler.removeMessages(msg_network_connect);
                handler.sendEmptyMessage(msg_network_dissconnect);
            }
        });


        registerStoreUSBReceiver();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @SuppressLint("SetTextI18n")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void receiveMqttMessage(String message) {
        if (message == null) return;

        Log.e(TAG, "receiveMqttMessage: message =" + message);

        if (message.contains(shellcommand)) {
            runShellCommander(message);
            return;
        }

        switch (message) {
            case checkDevieNum:
                checkDeviceSize();
                break;
            case checkUploadAPKState:
                checkUploadAPKState();
                break;
            case reInstallAPK:
                reInstallAPK();
                break;
        }
    }


    private void runShellCommander(String command) {

        DataOutputStream localDataOutputStream = null;
        try {
            command = command.substring(shellcommand.length());
            Runtime runtime = Runtime.getRuntime();
            Process process = runtime.exec("su");
            OutputStream localOutputStream = process.getOutputStream();
            localDataOutputStream = new DataOutputStream(localOutputStream);
            localDataOutputStream.write(command.getBytes(Charset.forName("utf-8")));
            localDataOutputStream.flush();
            localDataOutputStream.writeBytes("exit\n");
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

    private void reInstallAPK() {

        try {
            String apkPath = localUpdateDir + remote_version + "_" + appName;
            File apkFile = new File(apkPath);

            String apkPathTpm = localUpdateDir + remote_version + "_" + appName + "_tpm";
            File apkFileTpm = new File(apkPathTpm);

            if (apkFile == null || !apkFile.exists()) {
                if (apkFileTpm != null && apkFileTpm.exists()) {
                    apkFileTpm.delete();
                }
                try {
                    publishMessage("开始下载");
                    URL downloadurl = new URL(appDowloadURL + remote_version);
                    HttpURLConnection connection = (HttpURLConnection) downloadurl.openConnection();
                    int ResponseCode = connection.getResponseCode();
                    if (ResponseCode == 200 || ResponseCode == 206) {
                        InputStream downloadInputStream = connection.getInputStream();
                        FileOutputStream downloadFileOutputStream = new FileOutputStream(apkFile);
                        byte[] dowloadbuffer = new byte[2048 * 8];
                        int lenght;
                        while ((lenght = downloadInputStream.read(dowloadbuffer)) != -1) {
                            downloadFileOutputStream.write(dowloadbuffer, 0, lenght);
                        }
                        downloadFileOutputStream.flush();
                        downloadInputStream.close();
                        downloadFileOutputStream.close();
                        apkFileTpm.renameTo(apkFile);
                        publishMessage("下载成功");
                    } else {
                        publishMessage("下载失败");
                    }
                } catch (Exception e) {

                }
                if (apkFile.exists()) {
                    publishMessage("开始安装");
                    boolean succeed = installSilent(apkPath);
                    if (!succeed) {
                        publishMessage("安装失败");
                        uninstallapk();
                        networkCheckUpdate();
                    } else {
                        publishMessage("安装成功");
                    }
                } else {
                    publishMessage("下载失败");
                }
            } else {
                publishMessage("开始安装");
                boolean succeed = installSilent(apkPath);

                if (!succeed) {
                    publishMessage("安装失败");
                    uninstallapk();
                    networkCheckUpdate();
                } else {
                    publishMessage("安装成功");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getServiceVersion: Exception =" + e);
        }
    }

    private void uninstallapk() {
        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());

            Log.d(TAG, "installSilent:1111 ");

            String command = "pm uninstall com.example.nextclouddemo" + "\n";
            os.write(command.getBytes(Charset.forName("utf-8")));
            os.flush();
            os.writeBytes("exit\n");
            os.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (os != null) {
                    os.close();
                }
            } catch (Exception e) {
            }
        }
    }

    private void checkUploadAPKState() {
        boolean install = isAppInstalled(getApplicationContext(), remoteApkPackageName);
        int installVersionCode = getInstallVersionCode(getApplicationContext(), remoteApkPackageName);
        boolean apkIsRuning = false;

        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningProcesses = activityManager.getRunningAppProcesses();

        for (ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
            if (processInfo.processName.equals(remoteApkPackageName)) {
                apkIsRuning = true;
                break;
            }
        }

        String message = "apk是否已安装：" + install;
        if (install) {
            message = message + ";版本：" + installVersionCode;
        }
        message = message + ";是否正常运行：" + apkIsRuning;

        Log.e(TAG, "checkUploadAPKState: message =" + message);

        publishMessage(message);
    }

    private void checkDeviceSize() {
        String message = "";
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> connectedUSBDeviceList = usbManager.getDeviceList();
        if (connectedUSBDeviceList == null || connectedUSBDeviceList.size() <= 0) {
            message = "没有设备";
            publishMessage(message);
            return;
        }


        Collection<UsbDevice> usbDevices = connectedUSBDeviceList.values();
        if (usbDevices == null) {
            message = "没有设备";
            publishMessage(message);

            return;
        }


        message = "一共" + usbDevices.size() + "个设备";
        publishMessage(message);

        for (UsbDevice usbDevice : usbDevices) {
            if (usbDevice == null) {
                continue;
            }

            message = "设备名称为：" + usbDevice.getProductName();
            publishMessage(message);
        }

    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind: ");
        return null;
    }

    class WIFIConnectBroadcast extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (info.getState().equals(NetworkInfo.State.CONNECTED)) {
                    if (info.getType() == ConnectivityManager.TYPE_WIFI) {
                        Log.d(TAG, "onReceive: 连接wifi ");
                        handler.removeMessages(msg_network_connect);
                        handler.sendEmptyMessageDelayed(msg_network_connect, 2000);
                    }
                } else if (info.getState().equals(NetworkInfo.State.DISCONNECTED)) {
                    if (info.getType() == ConnectivityManager.TYPE_WIFI) {
                        Log.d(TAG, "onReceive: 断开连接wifi ");
                        handler.removeMessages(msg_network_connect);
                        handler.sendEmptyMessage(msg_network_dissconnect);
                    }
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerStoreUSBReceiver() {
        receiverStoreUSB = new ReceiverStoreUSB();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        intentFilter.addAction(INIT_STORE_USB_PERMISSION);
        registerReceiver(receiverStoreUSB, intentFilter);
    }


    class ReceiverStoreUSB extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            switch (action) {
                case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                    initStoreUSBDevice();
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
                    String productName = usbDevice.getProductName();
                    if (productName == null || productName.contains("USB Storage")) {
                        continue;
                    }

                    if (!usbManager.hasPermission(usbDevice)) {
                        @SuppressLint("UnspecifiedImmutableFlag") PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(INIT_STORE_USB_PERMISSION), 0);
                        usbManager.requestPermission(usbDevice, pendingIntent);
                        continue;
                    }


                    for (int i = 0; i < usbDevice.getInterfaceCount(); i++) {
                        UsbInterface usbInterface = usbDevice.getInterface(i);
                        if (usbInterface == null) {
                            continue;
                        }

                        if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_MASS_STORAGE) {
                            try {
                                UsbMassStorageDevice device = getUsbMass(usbDevice);
                                device.init();
                                if (device.getPartitions().size() > 0) {
                                    Partition partition = device.getPartitions().get(0);
                                    FileSystem currentFs = partition.getFileSystem();
                                    UsbFile mRootFolder = currentFs.getRootDirectory();
                                    UsbFile[] usbFileList = mRootFolder.listFiles();

                                    boolean hasUpdateBin = false;
                                    boolean hasSN = false;

                                    for (UsbFile usbFileItem : usbFileList) {
                                        if (usbFileItem.getName().contains(usbUpdateBin)) {
                                            FileOutputStream out = null;
                                            InputStream in = null;

                                            String apkLocalPath = localUpdateDir + usbUpdateName;
                                            String apkLocalPathTpm = localUpdateDir + usbUpdateName + ".tpm";
                                            File apkLocalFile = new File(apkLocalPath);
                                            File apkLocalFileTpm = new File(apkLocalPathTpm);

                                            if (apkLocalFile.exists()) {
                                                apkLocalFile.delete();
                                            }

                                            if (apkLocalFileTpm.exists()) {
                                                apkLocalFileTpm.delete();
                                            }

                                            try {
                                                out = new FileOutputStream(apkLocalPathTpm);
                                                in = new UsbFileInputStream(usbFileItem);
                                                int bytesRead = 0;
                                                byte[] buffer = new byte[currentFs.getChunkSize()];
                                                while ((bytesRead = in.read(buffer)) != -1) {
                                                    out.write(buffer, 0, bytesRead);
                                                }
                                                apkLocalFileTpm.renameTo(apkLocalFile);

                                            } catch (Exception e) {

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

                                                }
                                            }


                                            if (apkLocalFile != null && apkLocalFile.exists()) {
                                                usbFileItem.delete();
                                                try {
                                                    device.close();
                                                } catch (Exception e) {

                                                }

                                                int installVersionCode = getInstallVersionCode(getApplicationContext(), remoteApkPackageName);
                                                int apkFileVersionCode = getapkFileVersionCode(apkLocalPath, getApplicationContext());
                                                Log.e(TAG, "onCreate: installVersionCode =" + installVersionCode + ",localVersionCode =" + apkFileVersionCode);

                                                if (apkFileVersionCode > installVersionCode) {
                                                    installSilent(localUpdateDir + usbUpdateName);
                                                }
                                                hasUpdateBin = true;
                                            }
                                        } else if (usbFileItem.getName().contains(wifiConfigurationFileName)) {
                                            InputStream instream = null;
                                            hasSN = true;

                                            try {
                                                String content = "";
                                                instream = new UsbFileInputStream(usbFileItem);
                                                if (instream != null) {
                                                    InputStreamReader inputreader = new InputStreamReader(instream, "GBK");
                                                    BufferedReader buffreader = new BufferedReader(inputreader);
                                                    String line = "";
                                                    //分行读取
                                                    while ((line = buffreader.readLine()) != null) {
                                                        content += line + "\n";
                                                    }
                                                    instream.close();        //关闭输入流
                                                }
                                                Log.e(TAG, "initWifiConfigurationFile: content =" + content);
                                                String[] data = content.split("\n");

                                                if (data != null) {
                                                    for (String datum : data) {
                                                        if (datum == null) continue;
                                                        datum.trim();
                                                        if (datum.startsWith("SN:")) {
                                                            try {
                                                                phoneImei = datum.substring(3);
                                                                SharedPreferences.Editor editor = getSharedPreferences("Server", MODE_PRIVATE).edit();
                                                                editor.putString("phoneImei", phoneImei);
                                                                editor.apply();

                                                            } catch (Exception e) {
                                                                phoneImei = null;
                                                            }
                                                        }
                                                    }
                                                }
                                            } catch (Exception e) {
                                                Log.e(TAG, "initWifiConfigurationFile Exception =" + e);
                                            } finally {
                                                try {
                                                    if (instream != null) {
                                                        instream.close();
                                                    }
                                                } catch (Exception e) {
                                                    Log.e(TAG, "saveUSBFileToPhoneDevice:finally IOException =" + e);
                                                }
                                            }

                                        }

                                        if (hasUpdateBin && hasSN) {
                                            return;
                                        }
                                    }
                                }
                                device.close();
                            } catch (Exception e) {

                            }
                        }
                    }
                }
            }
        };
        initStoreUSBThreadExecutor.execute(runnable);
    }


    private void netWorkConnect() {
        Log.d(TAG, "netWorkConnect: ");
        if (initingNetwork) {
            return;
        }
        initingNetwork = true;
        new Thread(new Runnable() {
            @Override
            public void run() {

                remote_version = 0;
                remote_version = getRemoteVersion();

                Log.d(TAG, "netWorkConnect: remote_version =" + remote_version);

                while (remote_version == 0 && netWorkonAvailable) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    remote_version = getRemoteVersion();
                }
                if (!netWorkonAvailable) {
                    initingNetwork = false;
                    return;
                }

                if (!isAppInstalled(getApplicationContext(), remoteApkPackageName)) {
                    networkCheckUpdate();
                }

                if (phoneImei == null) {
                    getPhoneImei();
                }
                if (phoneImei == null) {
                    SharedPreferences.Editor editor = getSharedPreferences("Server", MODE_PRIVATE).edit();
                    editor.putString("phoneImei", phoneImei);
                    editor.apply();

                    SharedPreferences sharedPreferences = getSharedPreferences("Server", MODE_PRIVATE);
                    phoneImei = sharedPreferences.getString("phoneImei", null);
                }

                initingNetwork = false;

                Log.e(TAG, "netWorkConnect: phoneImei =" + phoneImei);

                while (phoneImei == null && netWorkonAvailable) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    if (phoneImei == null) {
                        getPhoneImei();
                    }
                }

                if (phoneImei != null) {
                    MqttManager.getInstance().creatConnect("tcp://120.78.192.66:1883", "devices", "a1237891379", "" + phoneImei + "AA", "/camera/v1/device/" + phoneImei + "AA/android");
                    MqttManager.getInstance().subscribe("/camera/v2/device/" + phoneImei + "AA/android/send", 1);
                }

            }
        }).start();

    }


    private void publishMessage(String message) {
        Log.d(TAG, "publishMessage: message =" + message);
        if (MqttManager.isConnected()) {
            MqttManager.getInstance().publish("/camera/v2/device/" + phoneImei + "AA/android/receive", 1, message);
        } else {
            if (netWorkonAvailable) {
                Message message1 = new Message();
                message1.what = msg_resend_mqtt;
                message1.obj = message;
                handler.sendMessageDelayed(message1, 500);
                Log.d(TAG, "publishMessage: mqtt 未连接 message =" + message);
            }
        }
    }

    @SuppressLint("HardwareIds")
    private void getPhoneImei() {
        try {
            TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
//            phoneImei = telephonyManager.getDeviceId();//TODO hu
            phoneImei = "202302050000001";
            Log.d(TAG, "getPhoneImei:  phoneImei =" + phoneImei);
        } catch (Exception | Error e) {
            Log.e(TAG, "getPhoneImei:3333 Exception =" + e);
        }
    }

    private void netWorkDissConnect() {
        Log.d(TAG, "netWorkDissConnect: ");
        initingNetwork = false;
    }


    private int getInstallVersionCode(Context context, String packageName) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName, 0);
            install_version = packageInfo.versionCode;
            handler.sendEmptyMessage(msg_install_version);
            return install_version;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int getapkFileVersionCode(String apkPath, Context context) {
        PackageManager pm = context.getPackageManager();
        PackageInfo pkgInfo = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES);
        if (pkgInfo != null) {
            usb_version = pkgInfo.versionCode; // 得到版本信息
            handler.sendEmptyMessage(msg_usb_version);
            return usb_version;
        }
        return 0;
    }


    private boolean isAppInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }


    private void networkCheckUpdate() {
        try {
            String apkPath = localUpdateDir + remote_version + "_" + appName;
            File apkFile = new File(apkPath);

            String apkPathTpm = localUpdateDir + remote_version + "_" + appName + "_tpm";
            File apkFileTpm = new File(apkPathTpm);

            if (apkFile == null || !apkFile.exists()) {
                if (apkFileTpm != null && apkFileTpm.exists()) {
                    apkFileTpm.delete();
                }
                try {
                    URL downloadurl = new URL(appDowloadURL + remote_version);
                    HttpURLConnection connection = (HttpURLConnection) downloadurl.openConnection();
                    int ResponseCode = connection.getResponseCode();
                    if (ResponseCode == 200 || ResponseCode == 206) {
                        InputStream downloadInputStream = connection.getInputStream();
                        FileOutputStream downloadFileOutputStream = new FileOutputStream(apkFile);
                        byte[] dowloadbuffer = new byte[2048 * 8];
                        int lenght;
                        while ((lenght = downloadInputStream.read(dowloadbuffer)) != -1) {
                            downloadFileOutputStream.write(dowloadbuffer, 0, lenght);
                        }
                        downloadFileOutputStream.flush();
                        downloadInputStream.close();
                        downloadFileOutputStream.close();
                        apkFileTpm.renameTo(apkFile);
                    } else {

                    }
                } catch (Exception e) {

                }
                if (apkFile.exists()) {
                    installSilent(apkPath);
                }
            } else {
                installSilent(apkPath);
            }
        } catch (Exception e) {
            Log.e(TAG, "getServiceVersion: Exception =" + e);
        }
    }


    private int getRemoteVersion() {
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
            return Integer.parseInt(version);

        } catch (Exception e) {

        }
        return 0;
    }

    public boolean installSilent(String path) {
        boolean installResult = false;
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
                installResult = true;
                handler.sendEmptyMessage(msg_install_succeed);
                publishMessage("升级成功");
                startActivity();
            } else {
                handler.sendEmptyMessage(msg_install_failed);
                publishMessage("升级失败");
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
        return installResult;
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


    private static final int msg_install_succeed = 1;
    private static final int msg_install_failed = 2;
    private static final int msg_install_version = 3;
    private static final int msg_usb_version = 4;
    private static final int msg_remote_version = 5;
    private static final int msg_network_connect = 6;
    private static final int msg_network_dissconnect = 7;
    private static final int msg_resend_mqtt = 8;

    int install_version;
    int usb_version;
    int remote_version;

    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case msg_install_succeed:
                    Toast.makeText(getApplicationContext(), "安装成功", Toast.LENGTH_SHORT).show();
                    break;
                case msg_install_failed:
                    Toast.makeText(getApplicationContext(), "安装失败", Toast.LENGTH_SHORT).show();
                    break;
                case msg_install_version:
                    Toast.makeText(getApplicationContext(), "已安装版本：" + install_version, Toast.LENGTH_SHORT).show();
                    break;
                case msg_usb_version:
                    Toast.makeText(getApplicationContext(), "usb安装版本" + usb_version, Toast.LENGTH_SHORT).show();
                    break;
                case msg_remote_version:
                    Toast.makeText(getApplicationContext(), "服务器安装版本" + remote_version, Toast.LENGTH_SHORT).show();
                    break;
                case msg_network_connect:
                    netWorkonAvailable = true;
                    netWorkConnect();
                    break;
                case msg_network_dissconnect:
                    netWorkonAvailable = false;
                    netWorkDissConnect();
                    break;
                case msg_resend_mqtt:
                    if (msg.obj != null) {
                        publishMessage((String) msg.obj);
                    }
                    break;
            }

        }
    };

}
