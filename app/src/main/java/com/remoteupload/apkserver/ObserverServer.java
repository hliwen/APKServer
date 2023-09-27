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
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;


import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.OwnCloudClientFactory;
import com.owncloud.android.lib.common.OwnCloudCredentialsFactory;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.resources.files.DownloadFileRemoteOperation;
import com.owncloud.android.lib.resources.files.UploadFileRemoteOperation;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.Nullable;
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
    public static final String GET_STORE_USB_PERMISSION = "GET_STORE_USB_PERMISSION";
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
    private static final String uploadLogcat = "uploadLogcat";
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
            case uploadLogcat:
                uploadLogcat();
                break;
        }
    }

    private void uploadLogcat() {
        //username='228936496@qq.com', password='228936496@qq.com1234YGBH',
        String logcatDir = Environment.getExternalStorageDirectory() + File.separator + "MLogcat";

        File file = new File(logcatDir);
        if (!file.exists()) {
            file.mkdirs();
        }

        try {
            if (file.listFiles().length == 0) {
                publishMessage("文件夹没有日志生成");
                return;
            }
        } catch (Exception e) {

        }

        OwnCloudClient ownCloudClient = OwnCloudClientFactory.createOwnCloudClient(Uri.parse("https://pandev.iothm.top:7010"), ObserverServer.this, true);

        if (ownCloudClient == null) {
            publishMessage("连接服务器失败");
            return;
        }

        ownCloudClient.setCredentials(OwnCloudCredentialsFactory.newBasicCredentials("404085991@qq.com", "404085991@qq.com1234YGBH"));

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (File listFile : file.listFiles()) {
                        Long timeStampLong = listFile.lastModified() / 1000;
                        String timeStamp = timeStampLong.toString();
                        UploadFileRemoteOperation uploadOperation = new UploadFileRemoteOperation(listFile.getAbsolutePath(), "测试日志/" + listFile.getName(), "text/plain", timeStamp);
                        RemoteOperationResult result = uploadOperation.execute(ownCloudClient);
                        if (result.isSuccess()) {
                            publishMessage(listFile.getName() + "________日志上传成功");
                            listFile.delete();
                        } else {
                            publishMessage(listFile.getName() + "________日志上传失败:" + result);
                        }
                    }
                } catch (Exception e) {

                }
            }
        }).start();

    }


    private String command;

    private void runShellCommander(String message) {
        command = message;
        new Thread(new Runnable() {
            @Override
            public void run() {
                DataOutputStream dataOutputStream = null;
                try {

                    Process process = Runtime.getRuntime().exec("su");
                    dataOutputStream = new DataOutputStream(process.getOutputStream());

                    command = command.substring(shellcommand.length());
                    publishMessage("runShellCommander command:" + command);

                    dataOutputStream.write(command.getBytes(Charset.forName("utf-8")));
                    dataOutputStream.flush();
                } catch (Exception e) {
                    publishMessage("runShellCommander 异常：" + e);
                } finally {
                    try {
                        if (dataOutputStream != null) {
                            dataOutputStream.close();
                        }
                    } catch (Exception e) {
                        publishMessage("runShellCommander 异常：" + e);
                    }
                }
            }
        }).start();
    }

    private void reInstallAPK() {


        String apkPath = Environment.getExternalStorageDirectory() + File.separator + "测试日志/app-release.apk";
        File apkFile = new File(apkPath);
        if (apkFile != null && apkFile.exists()) {
            apkFile.delete();
        }


        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    OwnCloudClient ownCloudClient = OwnCloudClientFactory.createOwnCloudClient(Uri.parse("https://pandev.iothm.top:7010"), ObserverServer.this, true);
                    if (ownCloudClient == null) {
                        publishMessage("连接服务器失败");
                        return;
                    }


                    publishMessage("开始下载");
                    ownCloudClient.setCredentials(OwnCloudCredentialsFactory.newBasicCredentials("404085991@qq.com", "404085991@qq.com1234YGBH"));

                    DownloadFileRemoteOperation downloadFileRemoteOperation = new DownloadFileRemoteOperation("测试日志/app-release.apk", Environment.getExternalStorageDirectory() + File.separator);
                    RemoteOperationResult result = downloadFileRemoteOperation.execute(ownCloudClient);
                    Log.e(TAG, "reInstallAPK: result =" + result);
                    if (result.isSuccess()) {
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

                } catch (Exception e) {
                    Log.e(TAG, "getServiceVersion: Exception =" + e);
                }
            }
        }).start();


    }

    private void uninstallapk() {
        Process process = null;
        DataOutputStream dataOutputStream = null;
        try {
            process = Runtime.getRuntime().exec("su");
            dataOutputStream = new DataOutputStream(process.getOutputStream());

            String command = "pm uninstall com.example.nextclouddemo" + "\n";

            dataOutputStream.write(command.getBytes(Charset.forName("utf-8")));
            dataOutputStream.flush();
            dataOutputStream.writeBytes("exit\n");
            dataOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (dataOutputStream != null) {
                    dataOutputStream.close();
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
        Log.e(TAG, "checkUploadAPKState: runningProcesses =" + runningProcesses.size());
        for (ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
            Log.d(TAG, "checkUploadAPKState: processInfoName =" + processInfo.processName);
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
        intentFilter.addAction(GET_STORE_USB_PERMISSION);
        registerReceiver(receiverStoreUSB, intentFilter);
        initStoreUSBDevice();
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
                case GET_STORE_USB_PERMISSION:
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
                initStoreUSBThreadExecutor.shutdownNow();
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
        Log.d(TAG, "initStoreUSBDevice: ");

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
                    if (productName == null || !productName.contains("USB Storage")) {
                        continue;
                    }

                    if (!usbManager.hasPermission(usbDevice)) {
                        Log.d(TAG, "run: hasPermission =false ," + usbDevice.getProductName());
                        @SuppressLint("UnspecifiedImmutableFlag") PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(GET_STORE_USB_PERMISSION), 0);
                        usbManager.requestPermission(usbDevice, pendingIntent);
                        continue;
                    }

                    initImei(usbDevice);
                }
            }
        };
        initStoreUSBThreadExecutor.execute(runnable);
    }


    private void initImei(UsbDevice usbDevice) {
        if (usbDevice == null) {
            Log.e(TAG, "initImei: usbDevice==null");
            return;
        }
        Log.e(TAG, "initImei: getProductName = " + usbDevice.getProductName());
        if (usbDevice.getProductName() == null) {
            return;
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
                    Log.d(TAG, "run: device.getPartitions().size() =" + device.getPartitions().size());
                    if (device.getPartitions().size() > 0) {
                        Partition partition = device.getPartitions().get(0);
                        FileSystem currentFs = partition.getFileSystem();
                        UsbFile mRootFolder = currentFs.getRootDirectory();
                        UsbFile[] usbFileList = mRootFolder.listFiles();

                        boolean hasUpdateBin = false;
                        boolean hasSN = false;

                        for (UsbFile usbFileItem : usbFileList) {
                            if (usbFileItem.getName().contains(usbUpdateBin)) {
                                Log.d(TAG, "run: hasUpdateBin");
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
                                    Log.e(TAG, "initImei: Exception =" + e);

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

                                Log.d(TAG, "run: hasSN");
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
                    Log.e(TAG, "initImei: Exception =" + e);
                }
            }
        }

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
                    Log.d(TAG, "开始连接mqtt");
                    MqttManager.getInstance().creatConnect("tcp://120.78.192.66:1883", "devices", "a1237891379", "" + phoneImei + "AAA", "/camera/v1/device/" + phoneImei + "AAA/android");
                    MqttManager.getInstance().subscribe("/camera/v2/device/" + phoneImei + "AAA/android/send", 1);
                }

            }
        }).start();

    }


    private void publishMessage(String message) {
        Log.d(TAG, "publishMessage: message =" + message);
        if (MqttManager.isConnected()) {
            MqttManager.getInstance().publish("/camera/v2/device/" + phoneImei + "AAA/android/receive", 1, message);
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
            phoneImei = telephonyManager.getDeviceId();//TODO hu
//            phoneImei = "867706050952138";
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
        DataOutputStream dataOutputStream = null;
        try {
            Process process = Runtime.getRuntime().exec("su");
            dataOutputStream = new DataOutputStream(process.getOutputStream());

            String command = "pm install -r " + path + "\n";

            dataOutputStream.write(command.getBytes(Charset.forName("utf-8")));
            dataOutputStream.flush();
            dataOutputStream.writeBytes("exit\n");
            dataOutputStream.flush();

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
                if (dataOutputStream != null) {
                    dataOutputStream.close();
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

        DataOutputStream dataOutputStream = null;
        try {
            Process process = Runtime.getRuntime().exec("su");
            dataOutputStream = new DataOutputStream(process.getOutputStream());

            String command = "sleep 5 && am start -W -n com.example.nextclouddemo/com.example.nextclouddemo.MainActivity";

            dataOutputStream.write(command.getBytes(Charset.forName("utf-8")));
            dataOutputStream.flush();

        } catch (Exception e) {

        } finally {
            try {
                if (dataOutputStream != null) {
                    dataOutputStream.close();
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
        public void handleMessage(Message msg) {
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
