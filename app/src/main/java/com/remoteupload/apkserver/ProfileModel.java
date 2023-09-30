package com.remoteupload.apkserver;


public class ProfileModel {

    public String imei;
    public String wifi;
    public String pass;
    public String SN;

    public ProfileModel() {
        imei = null;
        wifi = null;
        pass = null;
        SN = null;
    }

    @Override
    public String toString() {
        return "imei ="+imei+",wifi="+wifi+",pass="+pass+",SN ="+SN;
    }
}
