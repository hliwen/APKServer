package com.remoteupload.apkserver.serialport;

import android.os.SystemClock;

import com.licheedev.hwutils.ByteUtil;
import com.licheedev.myutils.LogPlus;
import com.remoteupload.apkserver.serialport.message.LogManager;
import com.remoteupload.apkserver.serialport.message.RecvMessage;


import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 读串口线程
 */
public class SerialReadThread extends Thread {

    private static final String TAG = "SerialReadThread";

    private BufferedInputStream mInputStream;
    private String subString = "";

    public SerialReadThread(InputStream is) {
        mInputStream = new BufferedInputStream(is);
    }

    @Override
    public void run() {
        byte[] received = new byte[1024];
        int size;

        LogPlus.e("开始读线程");

        while (true) {

            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            try {

                int available = mInputStream.available();

                if (available > 0) {
                    size = mInputStream.read(received);
                    if (size > 0) {
                        onDataReceive(received, size);
                    }
                } else {
                    // 暂停一点时间，免得一直循环造成CPU占用率过高
                    SystemClock.sleep(1);
                }
            } catch (IOException e) {
                LogPlus.e("读取数据失败", e);
            }
            //Thread.yield();
        }

        LogPlus.e("结束读进程");
    }

    /**
     * 处理获取到的数据
     *
     * @param received
     * @param size
     */
    private void onDataReceive(byte[] received, int size) {
        byte[] temp = new byte[size];
        System.arraycopy(received, 0, temp, 0, size);
        String message = new String(temp, StandardCharsets.UTF_8);

        if (message == null) {
            return;
        }
        if (message.endsWith("#")) {
            subString = subString + message;
            subString = subString.substring(0, subString.length() - 1);
            LogManager.instance().post(new RecvMessage(subString));
            subString = "";
        } else {
            subString = subString + message;
        }

    }

    /**
     * 停止读线程
     */
    public void close() {

        try {
            mInputStream.close();
        } catch (IOException e) {
            LogPlus.e("异常", e);
        } finally {
            super.interrupt();
        }
    }
}
