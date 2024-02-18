package com.remoteupload.apkserver;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;

import me.jahnen.libaums.core.UsbMassStorageDevice;

public class Utils {

	public static boolean isAppInstalled(Context context, String packageName) {
		try {
			context.getPackageManager().getApplicationInfo(packageName, 0);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public static int getapkFileVersionCode(Context context, String apkPath) {

		try {
			PackageManager pm = context.getPackageManager();
			PackageInfo pkgInfo = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES);
			if (pkgInfo != null) {
				return pkgInfo.versionCode; // 得到版本信息;
			}
		} catch (Exception e) {

		}
		return 0;
	}

	public static String getapkFileVersionName(Context context, String apkPath) {


		try {
			PackageManager pm = context.getPackageManager();
			PackageInfo pkgInfo = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES);
			if (pkgInfo != null) {
				return pkgInfo.versionName;
			}
		} catch (Exception e) {

		}
		return "0";
	}

	public static int getInstallVersionCode(Context context, String packageName) {
		try {
			PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName, 0);
			return packageInfo.versionCode;
		} catch (PackageManager.NameNotFoundException e) {
			e.printStackTrace();
		}
		return 0;
	}

	public static String getInstallVersionName(Context context, String packageName) {
		try {
			PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName, 0);
			return packageInfo.versionName;
		} catch (PackageManager.NameNotFoundException e) {
			e.printStackTrace();
		}
		return "0";
	}


	private static String phoneImei;

	@SuppressLint("HardwareIds")
	public static String getPhoneImei(Context context) {
		if (phoneImei != null && !TextUtils.isEmpty(phoneImei)) {
			return phoneImei;
		}

		try {
			TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
			phoneImei = telephonyManager.getDeviceId();
		} catch (Exception | Error e) {
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			phoneImei = "202302050000001";
		}
		return phoneImei;
	}

	public static boolean isAppRunning(Context context, String packageName) {
		try {
			ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
			List<ActivityManager.RunningTaskInfo> list = am.getRunningTasks(100);
			if (list.size() <= 0) {
				return false;
			}
			for (ActivityManager.RunningTaskInfo info : list) {
				if (info.baseActivity.getPackageName().equals(packageName)) {
					return true;
				}
			}
		} catch (Exception e) {

		}
		return false;
	}

	public static int getPackageUid(Context context, String packageName) {
		try {
			ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(packageName, 0);
			if (applicationInfo != null) {
				return applicationInfo.uid;
			}
		} catch (Exception e) {
			return -1;
		}
		return -1;
	}

	public static boolean isProcessRunning(Context context, int uid) {
		try {
			ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
			List<ActivityManager.RunningServiceInfo> runningServiceInfos = am.getRunningServices(200);
			if (runningServiceInfos.size() > 0) {
				for (ActivityManager.RunningServiceInfo appProcess : runningServiceInfos) {
					if (uid == appProcess.uid) {
						return true;
					}
				}
			}
		} catch (Exception e) {

		}
		return false;
	}

	public static boolean isStroreUSBDevice(String deviceName) {
		if (deviceName == null) {
			return false;
		}
		return deviceName.contains("USB Storage");
	}

	public static UsbMassStorageDevice getUsbMass(UsbDevice usbDevice, Context context) {
		UsbMassStorageDevice[] storageDevices = UsbMassStorageDevice.getMassStorageDevices(context);
		for (UsbMassStorageDevice device : storageDevices) {
			if (usbDevice.equals(device.getUsbDevice())) {
				return device;
			}
		}
		return null;
	}

	public static int getRemoteVersion(String appVersionURL) {
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
			JSONObject jsonObject = new JSONObject(content);
			JSONArray jsonArray = new JSONArray(jsonObject.getString("data"));
			jsonObject = new JSONObject(jsonArray.getString(0));
			String version = jsonObject.getString("version");
			return Integer.parseInt(version);

		} catch (Exception e) {

		}
		return 0;
	}

	public static boolean installSilent(String path) {
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
			} else {

			}
		} catch (Exception e) {

		} finally {
			try {
				if (dataOutputStream != null) {
					dataOutputStream.close();
				}
				if (es != null) {
					es.close();
				}
			} catch (IOException e) {

			}
		}
		return installResult;
	}


	public static void uninstallApk() {
		DataOutputStream dataOutputStream = null;
		try {
			Process process = Runtime.getRuntime().exec("su");
			dataOutputStream = new DataOutputStream(process.getOutputStream());
			String runCommand = "pm uninstall com.example.nextclouddemo";
			dataOutputStream.write(runCommand.getBytes(Charset.forName("utf-8")));
			dataOutputStream.flush();
		} catch (Exception e) {

		} finally {
			try {
				if (dataOutputStream != null) {
					dataOutputStream.close();
				}
			} catch (Exception e) {

			}
		}
	}

	/**
	 * 有密码连接
	 * @param ssid
	 * @param pws
	 */
	public static void connectWifiPws(String ssid, String pws, WifiManager wifiManager) {
		wifiManager.disableNetwork(wifiManager.getConnectionInfo().getNetworkId());
		int netId = wifiManager.addNetwork(getWifiConfig(ssid, pws, true, wifiManager));
		boolean enableNetwork = wifiManager.enableNetwork(netId, true);

	}

	/**
	 * 无密码连接
	 * @param ssid
	 */
	public static void connectWifiNoPws(String ssid, WifiManager wifiManager) {
		wifiManager.disableNetwork(wifiManager.getConnectionInfo().getNetworkId());
		int netId = wifiManager.addNetwork(getWifiConfig(ssid, "", false, wifiManager));
		wifiManager.enableNetwork(netId, true);
	}

	/**
	 * wifi设置
	 * @param ssid
	 * @param pws
	 * @param isHasPws
	 */
	public static WifiConfiguration getWifiConfig(String ssid, String pws, boolean isHasPws, WifiManager wifiManager) {

		WifiConfiguration config = new WifiConfiguration();
		config.allowedAuthAlgorithms.clear();
		config.allowedGroupCiphers.clear();
		config.allowedKeyManagement.clear();
		config.allowedPairwiseCiphers.clear();
		config.allowedProtocols.clear();
		config.SSID = "\"" + ssid + "\"";

		WifiConfiguration tempConfig = isExist(ssid, wifiManager);
		if (tempConfig != null) {
			wifiManager.removeNetwork(tempConfig.networkId);
		}
		if (isHasPws) {
			config.preSharedKey = "\"" + pws + "\"";
			config.hiddenSSID = true;
			config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
			config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
			config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
			config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
			config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
			config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
			config.status = WifiConfiguration.Status.ENABLED;
		} else {
			config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
		}
		return config;
	}

	/**
	 * 得到配置好的网络连接
	 * @param ssid
	 * @return
	 */
	public static WifiConfiguration isExist(String ssid, WifiManager wifiManager) {
		@SuppressLint("MissingPermission") List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
		for (WifiConfiguration config : configs) {

			if (config.SSID.equals("\"" + ssid + "\"")) {

				return config;
			}
		}
		return null;
	}
}
