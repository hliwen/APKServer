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
import android.serialport.SerialPortFinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.content.PermissionChecker;

import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.OwnCloudClientFactory;
import com.owncloud.android.lib.common.OwnCloudCredentialsFactory;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.resources.files.DownloadFileRemoteOperation;
import com.owncloud.android.lib.resources.files.UploadFileRemoteOperation;
import com.remoteupload.apkserver.serialport.Device;
import com.remoteupload.apkserver.serialport.SerialPortManager;
import com.remoteupload.apkserver.serialport.message.IMessage;
import com.remoteupload.apkserver.serialport.util.ToastUtil;

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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.jahnen.libaums.core.UsbMassStorageDevice;
import me.jahnen.libaums.core.fs.FileSystem;
import me.jahnen.libaums.core.fs.UsbFile;
import me.jahnen.libaums.core.fs.UsbFileInputStream;
import me.jahnen.libaums.core.partition.Partition;

public class MainActivity extends Activity {

    private static final String TAG = "remotelog_Serverlog";
    private static final String BroadcastIntent = "Initing_USB";
    private static final String sendBroadcastToServer = "sendBroadcastToServer";
    private static final String checkServerUSBOpenration = "checkServerUSBOpenration";
    private static final String openUploadApk = "openUploadApk";
    private static final String BroadcastInitingUSB = "BroadcastInitingUSB";

    private static final String GET_DEVICE_PERMISSION = "GET_DEVICE_PERMISSION";
    private static final String uploadApkPackageName = "com.example.nextclouddemo";
    private static final String serVerApkPackageName = "com.remoteupload.apkserver";
    static String logcatDirName = "MLogcat";
    static String CameraPath = "CameraPath";
    static String CameraUploadPath = "CameraUploadPath";
    static String remoteDirName = "测试日志";
    static String remoteApkName = "app-release.apk";
    static String remoteURL = "https://pandev.iothm.top:7010";
    static String remoteUserName = "404085991@qq.com";
    static String remoteUserPass = "404085991@qq.com1234YGBH";
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
    private static final String resetData = "resetData";
    private static final String exitUploadApp = "exitUploadApp";
    private static final String enterDebug = "enterDebug";
    private static final String exitDebug = "exitDebug";
    private static final String FormatFlagBroadcast = "FormatFlagBroadcast";
    private static final String OpenCameraDevice = "OpenCameraDevice";
    private static final String CloseCameraDevice = "CloseCameraDevice";
    private static final String deviceBlock = "deviceBlock";
    private static final String networkState = "networkState";
    private static final String AppState = "AppState";
    private static final String openUploadApp = "openUploadApp";


    private static final String CheckAppStateAction = "CheckAppStateAction";
    private static final String ResponseAppStateAction = "ResponseAppStateAction";
    private static final String Exit_UploadAPP_Action = "Exit_UploadAPP_Action";
    private static final String Enter_UploadAPP_Debug_Model = "Enter_UploadAPP_Debug_Model";
    private static final String Exit_UploadAPP_Debug_Model = "Exit_UploadAPP_Debug_Model";


    private MyBroadcast myBroadcast;
    private ExecutorService initStoreUSBThreadExecutor;
    private int requestPermissionCount;
    private boolean netWorkonAvailableBroadcast;
    private boolean netWorkAvailable;
    private String runCommand;
    private boolean installingAPK;
    private String phoneImei;
    private int deviceId = -1;

    private boolean formatDeviceFlag;

    public static final String[] PERMISSIONS = {android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.READ_PHONE_STATE, Manifest.permission.GET_TASKS};

    private boolean mOpened;

    private TextView receiveDataText;

    private boolean remoteAppIsRuning;

    private RxTimer checkAppStateRxTimer;
    private RxTimer heatbeatRxTimer;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        receiveDataText = findViewById(R.id.receiveDataText);

        Log.e(TAG, "MainActivity onCreate: ");

        formatDeviceFlag = false;
        if (getFormatDeviceFlag()) {
            saveFormatDeviceFlag(false);
            formatDeviceFlag = true;
        }

        String[] value = haveNoPermissions(MainActivity.this);
        if (value.length > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(value, 111);
        }

        sendBroadcastForUploadApk(false, 1);
        EventBus.getDefault().register(this);
        registerMyReceiver();
        initStoreUSBDevice();

        sendBroadcast(new Intent(openUploadApk));

        checkAppStateRxTimer = new RxTimer();
        checkAppStateRxTimer.interval(10000, new RxTimer.RxAction() {
            @Override
            public void action(long number) {
                if (Utils.isAppInstalled(getApplicationContext(), uploadApkPackageName) && !remoteAppIsRuning) {
                    startRemoteActivity();
                }
            }
        });

        heatbeatRxTimer = new RxTimer();
        heatbeatRxTimer.interval(5000, new RxTimer.RxAction() {
            @Override
            public void action(long number) {
                handler.sendEmptyMessageDelayed(msg_heatbeat_timeout, HEATBEAT_TIME);
                sendOrderedBroadcast(new Intent(CheckAppStateAction), null);
            }
        });
        initSerialPort();
    }

    private void initSerialPort() {
        SerialPortFinder serialPortFinder = new SerialPortFinder();
        String[] mDevices = serialPortFinder.getAllDevicesPath();
        if (mDevices == null || mDevices.length == 0) {
            ToastUtil.showOne(this, "没有找到串口");

        } else {
            int index = -1;
            for (int i = 0; i < mDevices.length; i++) {
                String portName = mDevices[i];
                if (TextUtils.isEmpty(portName)) continue;
                if (portName.toLowerCase().contains("ttys2")) {
                    index = i;
                    break;
                }
            }

            if (index < 0) {
                ToastUtil.showOne(this, "打开串口失败");
                return;
            }


            Device mDevice = new Device(mDevices[index], "115200");
            mOpened = SerialPortManager.instance().open(mDevice) != null;
            if (mOpened) {
                ToastUtil.showOne(this, "成功打开串口");
            } else {
                ToastUtil.showOne(this, "打开串口失败");
            }
        }
    }

    private void sendData(String text) {
        ToastUtil.showOne(this, "发送数据:" + text);
        SerialPortManager.instance().sendCommand(text);
    }

    private String messageTextString;

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(IMessage message) {
        if (message == null) return;
        if (TextUtils.isEmpty(message.getMessage())) return;

        messageTextString = messageTextString + "\n" + message.getMessage() + "\n";
        receiveDataText.setText(message.getMessage());

        switch (message.getMessage().replaceAll("\\r|\\n", "")) {
            case networkState:
                if (netWorkAvailable) {
                    sendData("networkState1");
                } else if (netWorkonAvailableBroadcast) {
                    sendData("networkState2");
                } else {
                    sendData("networkState3");
                }
                break;
            case AppState:
                if (uploadApkIsRuning()) {
                    sendData("AppState1");
                } else if (!Utils.isAppInstalled(getApplicationContext(), uploadApkPackageName)) {
                    sendData("AppState2");
                } else {
                    sendData("AppState3");
                }
                break;
            case openUploadApp:
                startRemoteActivity();
                break;
        }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);

        unregisterReceiver(myBroadcast);
        if (checkAppStateRxTimer != null) checkAppStateRxTimer.cancel();
        if (heatbeatRxTimer != null) heatbeatRxTimer.cancel();
    }


    private void saveFormatDeviceFlag(boolean format) {
        publishMessage("saveFormatDeviceFlag: format =" + format);
        SharedPreferences.Editor editor = getSharedPreferences("Server", MODE_PRIVATE).edit();
        editor.putBoolean("format", format);
        editor.apply();
    }

    private boolean getFormatDeviceFlag() {
        SharedPreferences sharedPreferences = getSharedPreferences("Server", MODE_PRIVATE);
        boolean format = sharedPreferences.getBoolean("format", false);
        publishMessage("getFormatDeviceFlag: format =" + format);
        return format;
    }

    @SuppressLint("SetTextI18n")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void receiveMqttMessage(String message) {
        if (message == null) return;
        message = message.trim();
        if (message.contains("mqttConnected")) {
            return;
        }
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
            case resetData:
                resetData();
                break;
            case reInstallAPK:
                reInstallAPK();
                break;
            case openUploadApk:
                sendBroadcast(new Intent(openUploadApk));
                break;

            case OpenCameraDevice:
                sendOrderedBroadcast(new Intent(OpenCameraDevice), null);
                break;
            case CloseCameraDevice:
                sendOrderedBroadcast(new Intent(CloseCameraDevice), null);
                break;
            case deviceBlock:
                getDeviceBlockList();
                break;
            case exitUploadApp:
                sendOrderedBroadcast(new Intent(Exit_UploadAPP_Action), null);
                break;
            case enterDebug:
                sendOrderedBroadcast(new Intent(Enter_UploadAPP_Debug_Model), null);
                break;
            case exitDebug:
                sendOrderedBroadcast(new Intent(Exit_UploadAPP_Debug_Model), null);
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
        boolean uploadApkIsInstall = Utils.isAppInstalled(getApplicationContext(), uploadApkPackageName);
        int serverApkVersionCode = Utils.getInstallVersionCode(getApplicationContext(), serVerApkPackageName);
        String serverApkVersionName = Utils.getInstallVersionName(getApplicationContext(), serVerApkPackageName);


        String message = "apk是否已安装：" + uploadApkIsInstall;
        if (uploadApkIsInstall) {
            int uploadApkVersionCode = Utils.getInstallVersionCode(getApplicationContext(), uploadApkPackageName);
            String uploadApkVersionName = Utils.getInstallVersionName(getApplicationContext(), uploadApkPackageName);
            message = message + ";版本：" + uploadApkVersionCode + "," + uploadApkVersionName;
        }
        message = message + ";是否正常运行：" + uploadApkIsRuning() + "," + remoteAppIsRuning;
        message = message + ";守护Apk版本：" + serverApkVersionCode + "," + serverApkVersionName;
        Log.e(TAG, "checkUploadAPKState: message =" + message);
        publishMessage(message);
    }

    private boolean uploadApkIsRuning() {
        boolean isAppRunning = false;
        int uid = Utils.getPackageUid(getApplicationContext(), uploadApkPackageName);
        if (uid > 0) {
            boolean rstA = Utils.isAppRunning(getApplicationContext(), uploadApkPackageName);
            boolean rstB = Utils.isProcessRunning(getApplicationContext(), uid);
            if (rstA || rstB) {
                isAppRunning = true;
            }
        }
        return isAppRunning;
    }

    private void uploadLogcat() {
        String logcatDir = Environment.getExternalStorageDirectory() + File.separator + logcatDirName;
        File file = new File(logcatDir);
        if (!file.exists()) {
            file.mkdirs();
            publishMessage("文件夹没有日志生成");
            return;
        }

        try {
            if (file.listFiles().length == 0) {
                publishMessage("文件夹没有日志生成");
                return;
            }
        } catch (Exception e) {

        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    OwnCloudClient ownCloudClient = OwnCloudClientFactory.createOwnCloudClient(Uri.parse(remoteURL), MainActivity.this, true);

                    if (ownCloudClient == null) {
                        publishMessage("连接服务器失败");
                        return;
                    }

                    ownCloudClient.setCredentials(OwnCloudCredentialsFactory.newBasicCredentials(remoteUserName, remoteUserPass));

                    for (File listFile : file.listFiles()) {
                        Long timeStampLong = listFile.lastModified() / 1000;
                        String timeStamp = timeStampLong.toString();
                        UploadFileRemoteOperation uploadOperation = new UploadFileRemoteOperation(listFile.getAbsolutePath(), remoteDirName + File.separator + listFile.getName(), "text/plain", timeStamp);
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


    private void resetData() {
        {
            String dir = Environment.getExternalStorageDirectory() + File.separator + logcatDirName;
            File file = new File(dir);
            if (file.exists()) {
                try {
                    for (File listFile : file.listFiles()) {
                        listFile.delete();
                    }
                } catch (Exception e) {

                }
            }
        }

        {
            String dir = Environment.getExternalStorageDirectory() + File.separator + CameraPath;
            File file = new File(dir);
            if (file.exists()) {
                try {
                    for (File listFile : file.listFiles()) {
                        listFile.delete();
                    }
                } catch (Exception e) {

                }
            }
        }
        {
            String dir = Environment.getExternalStorageDirectory() + File.separator + CameraUploadPath;
            File file = new File(dir);
            if (file.exists()) {
                try {
                    for (File listFile : file.listFiles()) {
                        listFile.delete();
                    }
                } catch (Exception e) {

                }
            }
        }

        {
            String filePath = Environment.getExternalStorageDirectory() + File.separator + "remotePictureList.txt";
            File file = new File(filePath);
            if (file.exists()) {
                file.delete();
            }
        }

        {
            String filePath = Environment.getExternalStorageDirectory() + File.separator + "usbPictureList.txt";
            File file = new File(filePath);
            if (file.exists()) {
                file.delete();
            }
        }


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
                    String apkPath = Environment.getExternalStorageDirectory() + File.separator + remoteDirName + File.separator + remoteApkName;
                    File apkFile = new File(apkPath);
                    if (apkFile != null && apkFile.exists()) {
                        apkFile.delete();
                    }

                    OwnCloudClient ownCloudClient = OwnCloudClientFactory.createOwnCloudClient(Uri.parse(remoteURL), MainActivity.this, true);
                    if (ownCloudClient == null) {
                        publishMessage("连接服务器失败");
                        installingAPK = false;
                        return;
                    }

                    publishMessage("开始下载");
                    ownCloudClient.setCredentials(OwnCloudCredentialsFactory.newBasicCredentials(remoteUserName, remoteUserPass));
                    DownloadFileRemoteOperation downloadFileRemoteOperation = new DownloadFileRemoteOperation(remoteDirName + File.separator + remoteApkName, Environment.getExternalStorageDirectory() + File.separator);
                    RemoteOperationResult result = downloadFileRemoteOperation.execute(ownCloudClient);
                    Log.e(TAG, "reInstallAPK: 下载 result =" + result);
                    if (result.isSuccess()) {
                        int apkFileVersionCode = Utils.getapkFileVersionCode(getApplicationContext(), apkPath);
                        String apkFileVersionName = Utils.getapkFileVersionName(getApplicationContext(), apkPath);
                        publishMessage("开始安装apk 版本：" + apkFileVersionCode + "," + apkFileVersionName);
                        boolean installResult = Utils.installSilent(apkPath);
                        if (installResult) {
                            publishMessage("安装成功");
                            startRemoteActivity();
                        } else {
                            publishMessage("安装失败,尝试重新安装");
                            Utils.uninstallApk();
                            try {
                                Thread.sleep(3000);
                            } catch (Exception e) {
                            }
                            installResult = Utils.installSilent(apkPath);
                            if (installResult) {
                                publishMessage("安装成功");
                                startRemoteActivity();
                            } else {
                                publishMessage("安装失败,尝试重新安装也失败了");
                            }
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


    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerMyReceiver() {
        myBroadcast = new MyBroadcast();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        intentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        intentFilter.addAction(GET_DEVICE_PERMISSION);
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        intentFilter.addAction(sendBroadcastToServer);
        intentFilter.addAction(FormatFlagBroadcast);
        intentFilter.addAction(checkServerUSBOpenration);
        intentFilter.addAction(ResponseAppStateAction);

        registerReceiver(myBroadcast, intentFilter);
    }

    public void initStoreUSBDevice() {

        if (formatDeviceFlag) {
            return;
        }

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


    class MyBroadcast extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            switch (action) {
                case UsbManager.ACTION_USB_DEVICE_DETACHED: {

                    if (formatDeviceFlag) {
                        return;
                    }
                    UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (usbDevice == null) {
                        Log.e(TAG, "ACTION_USB_DEVICE_DETACHED usbDevice == null");
                        return;
                    }
                    Log.e(TAG, "ACTION_USB_DEVICE_DETACHED getProductName =" + usbDevice.getProductName());
                    if (Utils.isStroreUSBDevice(usbDevice.getProductName()) && deviceId == usbDevice.getDeviceId()) {
                        requestPermissionCount = 0;
                        deviceId = -1;
                        stopStoreUSBInitThreadExecutor();
                    }
                }
                break;
                case UsbManager.ACTION_USB_DEVICE_ATTACHED: {

                    if (formatDeviceFlag) {
                        return;
                    }
                    UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (usbDevice == null) {
                        Log.e(TAG, "ACTION_USB_DEVICE_ATTACHED usbDevice == null");
                        return;
                    }
                    Log.e(TAG, "ACTION_USB_DEVICE_ATTACHED getProductName =" + usbDevice.getProductName());
                    if (Utils.isStroreUSBDevice(usbDevice.getProductName())) {
                        usbConnect(usbDevice);
                    }
                }
                break;
                case GET_DEVICE_PERMISSION: {

                    if (formatDeviceFlag) {
                        return;
                    }
                    handler.removeMessages(msg_get_permission_timeout);
                    UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (usbDevice == null) {
                        Log.e(TAG, "GET_DEVICE_PERMISSION usbDevice == null");
                        return;
                    }
                    Log.e(TAG, "GET_DEVICE_PERMISSION getProductName =" + usbDevice.getProductName());
                    if (Utils.isStroreUSBDevice(usbDevice.getProductName())) {
                        usbConnect(usbDevice);
                    }
                }
                break;

                case ConnectivityManager.CONNECTIVITY_ACTION: {
                    NetworkInfo info = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                    if (info.getState().equals(NetworkInfo.State.DISCONNECTED)) {
                        Log.d(TAG, "网络断开广播");
                        handler.removeMessages(msg_network_connect);
                        netWorkonAvailableBroadcast = false;
                        netWorkAvailable = false;
                    } else if (info.getState().equals(NetworkInfo.State.CONNECTED)) {
                        Log.d(TAG, "网络连接广播");
                        handler.removeMessages(msg_network_connect);
                        handler.sendEmptyMessageDelayed(msg_network_connect, 2000);
                    }
                }
                break;
                case sendBroadcastToServer:
                    publishMessage(intent.getStringExtra("fucntion"));
                    break;
                case checkServerUSBOpenration:
                    sendBroadcastForUploadApk(isInitingUSB, 8);
                    break;
                case FormatFlagBroadcast:
                    saveFormatDeviceFlag(true);
                    break;
                case ResponseAppStateAction:
                    remoteAppIsRuning = true;
                    handler.removeMessages(msg_heatbeat_timeout);
                    break;

                default:
                    break;
            }
        }
    }


    private boolean isInitingUSB;

    private synchronized void usbConnect(UsbDevice usbDevice) {

        if (formatDeviceFlag) {
            return;
        }
        Log.d(TAG, "usbConnect: start ...................... ");
        if (usbDevice == null) {
            return;
        }
        sendBroadcastForUploadApk(true, 2);
        Log.d(TAG, "usbConnect 存储U盘设备接入:" + usbDevice.getProductName());
        stopStoreUSBInitThreadExecutor();
        initStoreUSBThreadExecutor = Executors.newSingleThreadExecutor();
        initStoreUSBThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
                if (!usbManager.hasPermission(usbDevice)) {
                    requestPermissionCount++;
                    if (requestPermissionCount > 20) {
                        sendBroadcastForUploadApk(false, 3);
                        Log.d(TAG, "usbConnect: 0000");
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
                int interfaceCount = usbDevice.getInterfaceCount();
                Log.d(TAG, "usbConnect interfaceCount =" + interfaceCount);
                for (int i = 0; i < interfaceCount; i++) {
                    UsbInterface usbInterface = usbDevice.getInterface(i);
                    if (usbInterface == null) {
                        Log.d(TAG, "usbConnect: 1111");
                        continue;
                    }
                    Log.e(TAG, "usbConnect getInterfaceClass =" + usbInterface.getInterfaceClass());
                    if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_MASS_STORAGE) {
                        UsbMassStorageDevice device = Utils.getUsbMass(usbDevice, getApplicationContext());
                        if (device == null) {
                            Log.d(TAG, "usbConnect 22222");
                            continue;
                        }
                        try {
                            Log.d(TAG, "usbConnect: init start.................");
                            device.init();
                            Log.d(TAG, "usbConnect: init end.................");
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
                                    sendBroadcastForUploadApk(true, 4);
                                    parseWifiConfiguration(usbFileItem);
                                } else if (usbFileItem.getName().contains(usbUpdateBin)) {
                                    sendBroadcastForUploadApk(true, 5);
                                    parseBinAPK(usbFileItem, fileSystem);
                                }
                            }
                            deviceId = usbDevice.getDeviceId();
                            Log.e(TAG, "run: deviceId =" + deviceId);
                        } catch (Exception e) {
                            Log.e(TAG, "usbConnect for file list Exception =" + e);
                        }
                        try {
                            device.close();
                        } catch (Exception e) {
                            Log.e(TAG, "run: device.close Exception =" + e);
                        }
                        sendBroadcastForUploadApk(false, 6);
                    }
                }
                sendBroadcastForUploadApk(false, 7);
            }
        });
    }


    private void sendBroadcastForUploadApk(boolean initing, int position) {
        isInitingUSB = initing;
        try {
            Intent intent = new Intent(BroadcastIntent);
            intent.putExtra(BroadcastInitingUSB, initing);
            intent.putExtra("position", position);
            sendOrderedBroadcast(intent, null);
        } catch (Exception e) {

        }
    }

    private void parseWifiConfiguration(UsbFile usbFileItem) {
        Log.d(TAG, "parseWifiConfiguration start .............");
        InputStream instream = null;
        try {
            String content = "";
            instream = new UsbFileInputStream(usbFileItem);
            if (instream != null) {
                InputStreamReader inputreader = new InputStreamReader(instream, "GBK");
                BufferedReader buffreader = new BufferedReader(inputreader);
                String line = "";
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
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                            if (wifiManager != null) {
                                boolean wifiEnable = wifiManager.isWifiEnabled();
                                if (wifiEnable) {
                                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                                    if (wifiInfo != null && wifiInfo.getSSID() != null && wifiInfo.getSSID().contains(profileModel.wifi)) {
                                        return;
                                    }
                                    if (profileModel.pass == null || profileModel.pass.length() == 0) {
                                        Utils.connectWifiNoPws(profileModel.wifi, wifiManager);
                                    } else {
                                        Utils.connectWifiPws(profileModel.wifi, profileModel.pass, wifiManager);
                                    }
                                } else {
                                    wifiManager.setWifiEnabled(true);
                                }
                            }
                        }
                    }).start();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "parseWifiConfiguration Exception =" + e);
        } finally {
            try {
                if (instream != null) {
                    instream.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "parseWifiConfiguration:finally IOException =" + e);
            }
        }
        Log.d(TAG, "parseWifiConfiguration end .............");
    }

    private void parseBinAPK(UsbFile usbFileItem, FileSystem fileSystem) {
        Log.d(TAG, "parseBinAPK: start ..............");
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
            Log.e(TAG, "parseBinAPK: 11 Exception =" + e);
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
                Log.e(TAG, "parseBinAPK: 22 Exception =" + e);
            }
        }
        apkLocalFile = new File(apkLocalPath);

        if (apkLocalFile != null && apkLocalFile.exists()) {
            int installVersionCode = Utils.getInstallVersionCode(getApplicationContext(), uploadApkPackageName);
            int apkFileVersionCode = Utils.getapkFileVersionCode(getApplicationContext(), apkLocalPath);
            Log.e(TAG, "parseBinAPK: installVersionCode =" + installVersionCode + ",localVersionCode =" + apkFileVersionCode);
            if (apkFileVersionCode > installVersionCode) {
                publishMessage("开始安装apk");
                boolean installResult = Utils.installSilent(localUpdateDir + usbUpdateName);
                if (installResult) {
                    try {
                        usbFileItem.delete();
                    } catch (Exception e) {

                    }
                    publishMessage("安装成功");
                    startRemoteActivity();
                } else {
                    publishMessage("安装失败");
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
                netWorkAvailable = true;
                Log.d(TAG, "netWorkConnect: remote_version =" + remote_version);

                if (!Utils.isAppInstalled(getApplicationContext(), uploadApkPackageName)) {
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
                    Log.d(TAG, "getPhoneImei: imei= " + imei);
                    int checkCount = 0;
                    while (imei == null && checkCount < 20 && netWorkonAvailableBroadcast) {
                        checkCount++;
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException e) {

                        }
                        imei = Utils.getPhoneImei(getApplicationContext());
                        Log.d(TAG, "while getPhoneImei imei= " + imei);
                    }
                    if (imei == null) {
                        ProfileModel profileModel = getProfileModel();
                        if (profileModel == null) {
                            Log.d(TAG, "run: 111111111111");
                            return;
                        }
                        imei = profileModel.imei;
                        if (imei == null) {
                            imei = profileModel.SN;
                        }
                    }

                    if (imei == null) {
                        Log.d(TAG, "run: 22222222222");
                        return;
                    }

                    ProfileModel profileModel = new ProfileModel();
                    profileModel.imei = imei;
                    saveProfileModel(profileModel);
                } else {


                    ProfileModel profileModel = getProfileModel();
                    if (profileModel != null) {
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
        if (profileModel.imei != null) {
            editor.putString("imei", profileModel.imei);
        }
        if (profileModel.wifi != null) {
            editor.putString("wifi", profileModel.wifi);
        }
        if (profileModel.pass != null) {
            editor.putString("pass", profileModel.pass);
        }
        if (profileModel.SN != null) {
            editor.putString("SN", profileModel.SN);
        }
        editor.apply();
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

        if (message == null) return;

        if (MqttManager.isConnected()) {
            MqttManager.getInstance().publish("/camera/v2/device/" + phoneImei + "AAA/android/receive", 1, message);
        } else {
            if (netWorkonAvailableBroadcast && phoneImei != null) {
                Message message1 = new Message();
                message1.what = msg_resend_mqtt;
                message1.obj = message;
                handler.sendMessageDelayed(message1, 1000);
                Log.d(TAG, "publishMessage: mqtt 未连接 重发 message =" + message);
            }
        }
    }

    private void downloadRemoteAPK(int remote_version) {
        Log.d(TAG, "downloadRemoteAPK: remote_version =" + remote_version);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return;
        }
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
                    publishMessage("安装失败");
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


    private static final int HEATBEAT_TIME = 3000;

    private static final int msg_network_connect = 1;
    private static final int msg_resend_mqtt = 2;
    private static final int msg_get_permission_timeout = 3;
    private static final int msg_heatbeat_timeout = 4;


    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case msg_network_connect:
                    netWorkConnect();
                    break;
                case msg_resend_mqtt:
                    if (msg.obj != null) {
                        publishMessage((String) msg.obj);
                    }
                    break;
                case msg_get_permission_timeout:
                    sendBroadcastForUploadApk(false, 9);
                    initStoreUSBDevice();
                    break;
                case msg_heatbeat_timeout:
                    remoteAppIsRuning = false;
                    break;
            }
        }
    };

    public void getDeviceBlockList() {
        publishMessage("查找节点开始");
        List<String> devBlock = new ArrayList<>();
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("ls /dev/block/");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Log.d(TAG, "getDeviceBlockList:  block =" + line);
                if (line.startsWith("sd")) {
                    if (!devBlock.contains(line.trim())) {
                        devBlock.add(line.trim());
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            publishMessage("找节点异常：" + e);
        }

        for (String block : devBlock) {
            publishMessage("节点：block =" + block);
        }

        if (devBlock.size() == 0) {
            publishMessage("没找到节点");
        } else {
            publishMessage("查找节点结束");
        }
    }
}
