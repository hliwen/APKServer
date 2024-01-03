package com.remoteupload.apkserver.serialport.message;


import com.remoteupload.apkserver.serialport.util.TimeUtil;

/**
 * 收到的日志
 */

public class RecvMessage implements IMessage {
    

    private String message;

    public RecvMessage(String command) {
        this.message = command;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public boolean isToSend() {
        return false;
    }
}
