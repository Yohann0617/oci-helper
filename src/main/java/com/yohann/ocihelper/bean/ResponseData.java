package com.yohann.ocihelper.bean;

/**
 * @author: Yohann
 * @date: 2024/3/30 18:59
 */
public class ResponseData<T> {
    private boolean success;
    private int errorCode;
    private T data;
    private String msg;

    public boolean isSuccess() {
        return this.success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getErrorCode() {
        return this.errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public T getData() {
        return this.data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getMsg() {
        return this.msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public ResponseData() {
    }

    public ResponseData(boolean success) {
        this.success = success;
    }

    public ResponseData(boolean success, int errorCode, T data, String msg) {
        this.success = success;
        this.errorCode = errorCode;
        this.data = data;
        this.msg = msg;
    }

    public ResponseData(boolean success, T data, String msg) {
        this.success = success;
        this.data = data;
        this.msg = msg;
    }

    public static <T> ResponseData<T> successData() {
        return new ResponseData(true, 0, (Object)null, (String)null);
    }

    public static <T> ResponseData<T> successData(T data) {
        return new ResponseData(true, 0, data, (String)null);
    }

    public static <T> ResponseData<T> successData(T data, String msg) {
        return new ResponseData(true, 0, data, msg);
    }

    public static <T> ResponseData<T> successData(String msg) {
        return new ResponseData(true, (Object)null, msg);
    }

    public static <T> ResponseData<T> errorData(String msg) {
        return new ResponseData(false, (Object)null, msg);
    }

    public static <T> ResponseData<T> errorData(int errorCode, String msg) {
        return new ResponseData(false, errorCode, (Object)null, msg);
    }

    public static <T> ResponseData<T> errorData(int errorCode) {
        return new ResponseData(false, errorCode, (Object)null, (String)null);
    }
}

