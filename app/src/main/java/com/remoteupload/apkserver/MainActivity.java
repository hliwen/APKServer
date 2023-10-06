package com.remoteupload.apkserver;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import androidx.core.content.PermissionChecker;

import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.OwnCloudClientFactory;
import com.owncloud.android.lib.common.OwnCloudCredentialsFactory;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.resources.files.DownloadFileRemoteOperation;
import com.owncloud.android.lib.resources.files.UploadFileRemoteOperation;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.jahnen.libaums.core.UsbMassStorageDevice;
import me.jahnen.libaums.core.fs.FileSystem;
import me.jahnen.libaums.core.fs.UsbFile;
import me.jahnen.libaums.core.fs.UsbFileInputStream;
import me.jahnen.libaums.core.partition.Partition;

public class MainActivity extends Activity {

    private static final String TAG = "apkServerlog";
    private static final String GET_DEVICE_PERMISSION = "GET_DEVICE_PERMISSION";
    private static final String remoteApkPackageName = "com.example.nextclouddemo";
    private static final String serVerApkPackageName = "com.remoteupload.apkserver";
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
    private NetworkBroadcast networkBroadcast;
    private DeviceBroadcast deviceBroadcast;
    private ExecutorService initStoreUSBThreadExecutor;
    private int requestPermissionCount;


    private boolean netWorkonAvailableBroadcast;
    private String runCommand;
    private boolean installingAPK;
    private String phoneImei;
    private int deviceId = -1;
    private boolean deviceInit;


    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.e(TAG, "MainActivity onCreate: ");
        String[] value = haveNoPermissions(MainActivity.this);
        if (value.length > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(value, 111);
        }

        EventBus.getDefault().register(this);
        registerNetworkBroadcast();
        registerStoreUSBReceiver();
        initStoreUSBDevice();
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

    public static final String[] PERMISSIONS = {android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.READ_PHONE_STATE, Manifest.permission.GET_TASKS,

    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        unregisterReceiver(networkBroadcast);
        unregisterReceiver(deviceBroadcast);
    }

    @SuppressLint("SetTextI18n")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void receiveMqttMessage(String message) {
        if (message == null) return;

        message = message.trim();

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
            case uploadLogcat:
                uploadLogcat();
                break;
            case reInstallAPK:
                reInstallAPK();
                break;
        }
    }

    private void runShellCommander(String message) {
        runCommand = message;
        new Thread(new Runnable() {
            @Override
            public void run() {
                DataOutputStream dataOutputStream = null;
                try {
                    Process process = Runtime.getRuntime().exec("su");
                    dataOutputStream = new DataOutputStream(process.getOutputStream());
                    runCommand = runCommand.substring(shellcommand.length());
                    publishMessage("runShellCommander command:" + runCommand);
                    dataOutputStream.write(runCommand.getBytes(Charset.forName("utf-8")));
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

    private void checkUploadAPKState() {
        boolean uploadApkIsInstall = Utils.isAppInstalled(getApplicationContext(), remoteApkPackageName);
        int uploadApkVersionCode = Utils.getInstallVersionCode(getApplicationContext(), remoteApkPackageName);
        int serverApkVersionCode = Utils.getInstallVersionCode(getApplicationContext(), serVerApkPackageName);

        boolean isAppRunning = false;
        int uid = Utils.getPackageUid(getApplicationContext(), remoteApkPackageName);
        if (uid > 0) {
            boolean rstA = Utils.isAppRunning(getApplicationContext(), remoteApkPackageName);
            boolean rstB = Utils.isProcessRunning(getApplicationContext(), uid);
            if (rstA || rstB) {
                isAppRunning = true;
            }
        }

        String message = "apk是否已安装：" + uploadApkIsInstall;
        if (uploadApkIsInstall) {
            message = message + ";版本：" + uploadApkVersionCode;
        }
        message = message + ";是否正常运行：" + isAppRunning;
        message = message + ";守护Apk版本：" + serverApkVersionCode;
        Log.e(TAG, "checkUploadAPKState: message =" + message);
        publishMessage(message);
    }

    private void uploadLogcat() {
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

        OwnCloudClient ownCloudClient = OwnCloudClientFactory.createOwnCloudClient(Uri.parse("https://pandev.iothm.top:7010"), MainActivity.this, true);

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
                            publishMessage(listFile.getName() + "________日志上传成功 ：" + file.listFiles().length);
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

    private void reInstallAPK() {
        if (installingAPK) {
            return;
        }
        installingAPK = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String apkPath = Environment.getExternalStorageDirectory() + File.separator + "测试日志/app-release.apk";
                    File apkFile = new File(apkPath);
                    if (apkFile != null && apkFile.exists()) {
                        apkFile.delete();
                    }

                    OwnCloudClient ownCloudClient = OwnCloudClientFactory.createOwnCloudClient(Uri.parse("https://pandev.iothm.top:7010"), MainActivity.this, true);
                    if (ownCloudClient == null) {
                        publishMessage("连接服务器失败");
                        installingAPK = false;
                        return;
                    }

                    publishMessage("开始下载");
                    ownCloudClient.setCredentials(OwnCloudCredentialsFactory.newBasicCredentials("404085991@qq.com", "404085991@qq.com1234YGBH"));
                    DownloadFileRemoteOperation downloadFileRemoteOperation = new DownloadFileRemoteOperation("测试日志/app-release.apk", Environment.getExternalStorageDirectory() + File.separator);
                    RemoteOperationResult result = downloadFileRemoteOperation.execute(ownCloudClient);
                    Log.e(TAG, "reInstallAPK: result =" + result);
                    if (result.isSuccess()) {
                        publishMessage("开始安装apk");
                        boolean installResult = Utils.installSilent(apkPath);
                        if (installResult) {
                            publishMessage("安装成功");
                            startRemoteActivity();
                        } else {
                            publishMessage("安失败");
                        }
                    } else {
                        publishMessage("下载失败:" + result);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "getServiceVersion: Exception =" + e);
                }
                installingAPK = false;
            }
        }).start();
    }


    private void registerNetworkBroadcast() {
        networkBroadcast = new NetworkBroadcast();
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);

        registerReceiver(networkBroadcast, filter);
    }

    class NetworkBroadcast extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e(TAG, "onReceive:getAction = " + intent.getAction());
            if (intent.getAction() == null) return;
            if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                NetworkInfo info = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                if (info.getState().equals(NetworkInfo.State.DISCONNECTED)) {
                    Log.d(TAG, "NetWorkReceiver: 网络断开广播");
                    handler.removeMessages(msg_network_connect);
                    handler.removeMessages(msg_network_dissconnect);
                } else if (info.getState().equals(NetworkInfo.State.CONNECTED)) {
                    Log.d(TAG, "NetWorkReceiver: 网络连接广播");
                    handler.removeMessages(msg_network_connect);
                    handler.sendEmptyMessageDelayed(msg_network_connect, 2000);
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerStoreUSBReceiver() {
        deviceBroadcast = new DeviceBroadcast();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        intentFilter.addAction(GET_DEVICE_PERMISSION);
        registerReceiver(deviceBroadcast, intentFilter);
    }

    class DeviceBroadcast extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            switch (action) {
                case UsbManager.ACTION_USB_DEVICE_ATTACHED: {
                    UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (usbDevice == null) {
                        Log.e(TAG, "deviceBroadcast onReceive: action =" + action + ",usbDevice == null");
                        return;
                    }
                    Log.e(TAG, "deviceBroadcast onReceive: action =" + action + ", getProductName =" + usbDevice.getProductName());
                    if (Utils.isStroreUSBDevice(usbDevice.getProductName())) {
                        usbConnect(usbDevice);
                    }
                }
                break;
                case GET_DEVICE_PERMISSION: {
                    handler.removeMessages(msg_get_permission_timeout);
                    UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (usbDevice == null) {
                        Log.e(TAG, "deviceBroadcast onReceive: action =" + action + ",usbDevice == null");
                        return;
                    }
                    Log.e(TAG, "deviceBroadcast onReceive: action =" + action + ", getProductName =" + usbDevice.getProductName());
                    if (Utils.isStroreUSBDevice(usbDevice.getProductName())) {
                        usbConnect(usbDevice);
                    }
                }
                break;
                case UsbManager.ACTION_USB_DEVICE_DETACHED:
                    UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (usbDevice == null) {
                        Log.e(TAG, "ReceiverStoreUSB onReceive: action =" + action + ",usbDevice == null");
                        return;
                    }
                    Log.e(TAG, "ReceiverStoreUSB onReceive: action =" + action + ", getProductName =" + usbDevice.getProductName());
                    if (Utils.isStroreUSBDevice(usbDevice.getProductName()) && deviceId == usbDevice.getDeviceId()) {
                        requestPermissionCount = 0;
                        deviceId = -1;
                        deviceInit = false;
                        stopStoreUSBInitThreadExecutor();
                    }
                    break;
                default:
                    break;
            }
        }
    }

    public void initStoreUSBDevice() {
        Log.d(TAG, "initStoreUSBDevice: ");
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
            if (productName == null || !Utils.isStroreUSBDevice(productName)) {
                continue;
            }
            usbConnect(usbDevice);
            return;
        }
    }

    private synchronized void usbConnect(UsbDevice usbDevice) {
        Log.d(TAG, "usbConnect: start ...................... ");
        if (usbDevice == null) {
            return;
        }
        Log.d(TAG, "usbConnect 存储U盘设备接入:" + usbDevice.getProductName());
        stopStoreUSBInitThreadExecutor();
        initStoreUSBThreadExecutor = Executors.newSingleThreadExecutor();


        Log.d(TAG, "usbConnect: run11  start");

        initStoreUSBThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "usbConnect: run22  start");
                UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
                if (!usbManager.hasPermission(usbDevice)) {
                    requestPermissionCount++;
                    if (requestPermissionCount > 20) {
                        return;
                    }
                    handler.removeMessages(msg_get_permission_timeout);
                    handler.sendEmptyMessageDelayed(msg_get_permission_timeout, 3000);
                    Log.e(TAG, "usbConnect: 当前设备没有授权,productName :" + usbDevice.getProductName());
                    @SuppressLint("UnspecifiedImmutableFlag") PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(GET_DEVICE_PERMISSION), 0);
                    usbManager.requestPermission(usbDevice, pendingIntent);
                    return;
                }
                requestPermissionCount = 0;
                Log.d(TAG, "run: 33333");

                int interfaceCount = usbDevice.getInterfaceCount();
                Log.d(TAG, "run: 444 interfaceCount =" + interfaceCount);
                for (int i = 0; i < interfaceCount; i++) {
                    UsbInterface usbInterface = usbDevice.getInterface(i);
                    if (usbInterface == null) {
                        Log.d(TAG, "run: 555555");
                        continue;
                    }
                    Log.e(TAG, "run: 5555566666 getInterfaceClass =" + usbInterface.getInterfaceClass());
                    if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_MASS_STORAGE) {
                        UsbMassStorageDevice device = Utils.getUsbMass(usbDevice, getApplicationContext());
                        if (device == null) {
                            Log.d(TAG, "run: 6666666");
                            continue;
                        }
                        try {
                            Log.d(TAG, "run: aaaaaaaa");
                            device.init();
                            Log.d(TAG, "run: bbbbbbbb");
                        } catch (Exception e) {
                            Log.e(TAG, "usbConnect : device.init 设备初始化错误 " + e);
                            continue;
                        }

                        if (device.getPartitions().size() <= 0) {
                            Log.e(TAG, "usbConnect: " + "device.getPartitions().size() error, 无法获取到设备分区");
                            continue;
                        }
                        Partition partition = device.getPartitions().get(0);
                        FileSystem fileSystem = partition.getFileSystem();
                        UsbFile usbRootFolder = fileSystem.getRootDirectory();

                        try {
                            UsbFile[] usbFileList = usbRootFolder.listFiles();
                            for (UsbFile usbFileItem : usbFileList) {
                                if (usbFileItem.getName().contains(wifiConfigurationFileName)) {
                                    parseWifiConfiguration(usbFileItem);
                                } else if (usbFileItem.getName().contains(usbUpdateBin)) {
                                    parseBinAPK(usbFileItem, fileSystem);
                                }
                            }
                            deviceId = usbDevice.getDeviceId();
                            deviceInit = true;
                            Log.e(TAG, "run: deviceId =" + deviceId);
                        } catch (Exception e) {
                            Log.e(TAG, "usbConnect 111 Exception =" + e);
                        }
                        try {
                            device.close();
                        } catch (Exception e) {
                            Log.e(TAG, "run: 777 Exception =" + e);
                        }
                    }
                }
            }
        });
    }


    private void parseWifiConfiguration(UsbFile usbFileItem) {
        Log.d(TAG, "parseWifiConfiguration run: 找到配置文件 start");
        InputStream instream = null;
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
            Log.e(TAG, "parseWifiConfiguration: content =" + content);
            String[] data = content.split("\n");

            String wifiName = null;
            String pass = null;
            String SN = null;

            if (data != null) {
                for (String datum : data) {
                    if (datum == null) continue;
                    datum.trim();
                    if (datum.startsWith("wifi:")) {
                        try {
                            wifiName = datum.substring(5);
                        } catch (Exception e) {

                        }
                    } else if (datum.startsWith("pass:")) {
                        try {
                            pass = datum.substring(5);
                        } catch (Exception e) {

                        }
                    } else if (datum.startsWith("SN:")) {
                        try {
                            SN = datum.substring(3);
                        } catch (Exception e) {

                        }
                    }
                }
            }

            if (SN != null) {
                ProfileModel profileModel = new ProfileModel();
                profileModel.SN = SN;
                profileModel.wifi = wifiName;
                profileModel.pass = pass;
                saveProfileModel(profileModel);
                if (profileModel.wifi != null) {
                    WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    if (wifiManager != null) {
                        boolean wifiEnable = wifiManager.isWifiEnabled();
                        if (wifiEnable) {
                            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                            if (wifiInfo != null && wifiInfo.getSSID() != null && wifiInfo.getSSID().contains(profileModel.wifi)) {
                                return;
                            }
                            if (profileModel.pass == null) {
                                Utils.connectWifiNoPws(profileModel.wifi, wifiManager);
                            } else {
                                if (profileModel.pass.length() == 0) {
                                    Utils.connectWifiNoPws(profileModel.wifi, wifiManager);
                                } else {
                                    Utils.connectWifiPws(profileModel.wifi, profileModel.pass, wifiManager);
                                }
                            }
                        } else {
                            wifiManager.setWifiEnabled(true);
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

    private void parseBinAPK(UsbFile usbFileItem, FileSystem fileSystem) {
        Log.d(TAG, "parseBinAPK: ");
        String apkLocalPath = localUpdateDir + usbUpdateName;
        File apkLocalFile = new File(apkLocalPath);
        if (apkLocalFile.exists()) {
            apkLocalFile.delete();
        }

        FileOutputStream out = null;
        InputStream in = null;
        try {
            out = new FileOutputStream(apkLocalPath);
            in = new UsbFileInputStream(usbFileItem);
            int bytesRead = 0;
            byte[] buffer = new byte[fileSystem.getChunkSize()];
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
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
            int installVersionCode = Utils.getInstallVersionCode(getApplicationContext(), remoteApkPackageName);
            int apkFileVersionCode = Utils.getapkFileVersionCode(getApplicationContext(), apkLocalPath);
            Log.e(TAG, "onCreate: installVersionCode =" + installVersionCode + ",localVersionCode =" + apkFileVersionCode);
            if (apkFileVersionCode > installVersionCode) {
                publishMessage("开始安装apk");
                boolean installResult = Utils.installSilent(localUpdateDir + usbUpdateName);
                if (installResult) {
                    try {
                        usbFileItem.delete();
                    } catch (IOException e) {

                    }
                    publishMessage("安装成功");
                    startRemoteActivity();
                } else {
                    publishMessage("安失败");
                }
            }
        }
    }


    private synchronized void stopStoreUSBInitThreadExecutor() {
        Log.e(TAG, "stopStoreUSBInitThreadExecutor:start ");
        try {
            if (initStoreUSBThreadExecutor != null) {
                initStoreUSBThreadExecutor.shutdown();
            }
        } catch (Exception e) {
        }
        initStoreUSBThreadExecutor = null;

        Log.d(TAG, "stopStoreUSBInitThreadExecutor: end");
    }


    private void netWorkDissConnect() {
        Log.d(TAG, "netWorkDissConnect: ");
        netWorkonAvailableBroadcast = false;
    }

    private void netWorkConnect() {
        Log.d(TAG, "netWorkConnect: ");
        netWorkonAvailableBroadcast = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                int remote_version = Utils.getRemoteVersion(appVersionURL);
                while (remote_version == 0 && netWorkonAvailableBroadcast) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                    }
                    remote_version = Utils.getRemoteVersion(appVersionURL);
                }
                Log.d(TAG, "netWorkConnect: remote_version =" + remote_version);

                if (!Utils.isAppInstalled(getApplicationContext(), remoteApkPackageName)) {
                    downloadRemoteAPK(remote_version);
                }

                boolean cellular = true;
                ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                if (activeNetworkInfo != null && activeNetworkInfo.isConnected()) {
                    int type = activeNetworkInfo.getType();
                    Log.e(TAG, "initAddress run:  activeNetworkInfo.getType() =" + type);
                    if (type == ConnectivityManager.TYPE_WIFI) {
                        cellular = false;
                    }
                }

                String imei = null;
                if (cellular) {
                    imei = Utils.getPhoneImei(getApplicationContext());
                    Log.d(TAG, "run: imei= " + imei);
                    int checkCount = 0;
                    while (imei == null && checkCount < 20 && netWorkonAvailableBroadcast) {
                        checkCount++;
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException e) {

                        }
                        imei = Utils.getPhoneImei(getApplicationContext());
                        Log.d(TAG, "run:4444 imei= " + imei);
                    }
                    if (imei == null) {
                        ProfileModel profileModel = getProfileModel();
                        if (profileModel == null) {
                            return;
                        }
                        imei = profileModel.imei;
                        if (imei == null) {
                            imei = profileModel.SN;
                        }
                    }
                    if (imei != null) {
                        ProfileModel profileModel = new ProfileModel();
                        profileModel.imei = imei;
                        saveProfileModel(profileModel);
                    }
                    Log.d(TAG, "run111: imei= " + imei);
                } else {
                    ProfileModel profileModel = getProfileModel();
                    if (profileModel == null) {
                        imei = null;
                    } else {
                        imei = profileModel.SN;
                        if (imei == null) {
                            imei = profileModel.imei;
                        }
                    }

                    while (netWorkonAvailableBroadcast && imei == null) {
                        try {
                            Thread.sleep(3000);
                            profileModel = getProfileModel();
                            if (profileModel == null) {
                                imei = null;
                            } else {
                                imei = profileModel.SN;
                                if (imei == null) {
                                    imei = profileModel.imei;
                                }
                            }
                        } catch (Exception e) {

                        }
                    }
                }
                Log.e(TAG, "netWorkConnect: imei =" + imei);

                if (imei != null) {
                    phoneImei = imei;
                    Log.d(TAG, "开始连接mqtt");
                    MqttManager.getInstance().creatConnect("tcp://120.78.192.66:1883", "devices", "a1237891379", "" + imei + "AAA", "/camera/v1/device/" + imei + "AAA/android");
                    MqttManager.getInstance().subscribe("/camera/v2/device/" + imei + "AAA/android/send", 1);
                }
            }
        }).start();
    }


    private void saveProfileModel(ProfileModel profileModel) {
        Log.d(TAG, "saveProfileModel: profileModel =" + profileModel);
        if (profileModel == null) {
            return;
        }
        SharedPreferences.Editor editor = getSharedPreferences("Server", MODE_PRIVATE).edit();
        if (profileModel.imei != null) editor.putString("imei", profileModel.imei);
        if (profileModel.wifi != null) editor.putString("wifi", profileModel.wifi);
        if (profileModel.pass != null) editor.putString("pass", profileModel.pass);
        if (profileModel.SN != null) editor.putString("SN", profileModel.SN);

    }

    private ProfileModel getProfileModel() {

        SharedPreferences sharedPreferences = getSharedPreferences("Server", MODE_PRIVATE);
        ProfileModel profileModel = new ProfileModel();
        profileModel.imei = sharedPreferences.getString("imei", null);
        profileModel.wifi = sharedPreferences.getString("wifi", null);
        profileModel.pass = sharedPreferences.getString("pass", null);
        profileModel.SN = sharedPreferences.getString("SN", null);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            profileModel.SN = "202302050000001";
            profileModel.imei = "202302050000001";
        }

        Log.e(TAG, "getProfileModel: profileModel =" + profileModel);
        if (profileModel.imei == null && profileModel.SN == null) {
            return null;
        }
        return profileModel;
    }


    private void publishMessage(String message) {
        Log.d(TAG, "publishMessage: message =" + message);
        if (MqttManager.isConnected()) {
            MqttManager.getInstance().publish("/camera/v2/device/" + phoneImei + "AAA/android/receive", 1, message);
        } else {
            if (netWorkonAvailableBroadcast) {
                Message message1 = new Message();
                message1.what = msg_resend_mqtt;
                message1.obj = message;
                handler.sendMessageDelayed(message1, 1000);
                Log.d(TAG, "publishMessage: mqtt 未连接 重发 message =" + message);
            }
        }
    }


    private void downloadRemoteAPK(int remote_version) {
        Log.d(TAG, "downloadRemoteAPK: ");
        try {
            String apkPath = localUpdateDir + remote_version + "_" + appName;
            File apkFile = new File(apkPath);
            if (apkFile != null && apkFile.delete()) {
                apkFile.delete();
            }

            publishMessage("开始下载apk");
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
                publishMessage("开始安装apk");
                boolean installResult = Utils.installSilent(apkPath);
                if (installResult) {
                    publishMessage("安装成功");
                    startRemoteActivity();
                } else {
                    publishMessage("安失败");
                }
            } else {
                publishMessage("下载apk失败");
            }
        } catch (Exception e) {
            publishMessage("下载apk失败 " + e);
        }
    }


    private void startRemoteActivity() {
        Log.d(TAG, "startRemoteActivity: ");
        DataOutputStream dataOutputStream = null;
        try {
            Process process = Runtime.getRuntime().exec("su");
            dataOutputStream = new DataOutputStream(process.getOutputStream());
            String command = " am start -W -n com.example.nextclouddemo/com.example.nextclouddemo.MainActivity";
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


    private static final int msg_network_connect = 6;
    private static final int msg_network_dissconnect = 7;
    private static final int msg_resend_mqtt = 8;
    private static final int msg_get_permission_timeout = 9;

    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case msg_network_connect:
                    netWorkConnect();
                    break;
                case msg_network_dissconnect:
                    netWorkDissConnect();
                    break;
                case msg_resend_mqtt:
                    if (msg.obj != null) {
                        publishMessage((String) msg.obj);
                    }
                    break;
                case msg_get_permission_timeout:
                    initStoreUSBDevice();
                    break;
            }
        }
    };


}
