package com.remoteupload.apkserver.serialport;

import android.util.Log;

/**
 * 串口设备
 */
public class Device {

    private String path;
    private String baudrate;

    public Device(String path, String baudrate) {
        this.path = path;
        this.baudrate = baudrate;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        Log.d("fadsfasd", "setPath: path =" + path);
        this.path = path;
    }

    public String getBaudrate() {
        return baudrate;
    }

    public void setBaudrate(String baudrate) {
        this.baudrate = baudrate;
    }

    @Override
    public String toString() {
        return "Device{" + "path='" + path + '\'' + ", baudrate='" + baudrate + '\'' + '}';
    }
}
