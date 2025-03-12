package com.yohann.ocihelper.bean;

/**
 * @author: Yohann
 * @date: 2024/3/30 18:59
 */
public class ResponseData<T> {
    private boolean success;
    private int code = 200;
    private T data;
    private String msg;

    public boolean isSuccess() {
        return this.success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getCode() {
        return this.code;
    }

    public void setCode(int code) {
        this.code = code;
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

    public ResponseData(boolean success, int code, T data, String msg) {
        this.success = success;
        this.code = code;
        this.data = data;
        this.msg = msg;
    }

    public ResponseData(boolean success, T data, String msg) {
        this.success = success;
        this.data = data;
        this.msg = msg;
    }

    public static <T> ResponseData<T> successData() {
        return new ResponseData(true, 200, null, "请求成功");
    }

    public static <T> ResponseData<T> successData(T data) {
        return new ResponseData(true, 200, data, "请求成功");
    }

    public static <T> ResponseData<T> successData(T data, String msg) {
        return new ResponseData(true, 200, data, msg);
    }

    public static <T> ResponseData<T> successData(String msg) {
        return new ResponseData(true, 200,null, msg);
    }

    public static <T> ResponseData<T> errorData(String msg) {
        return new ResponseData(false,-1, null, msg);
    }

    public static <T> ResponseData<T> errorData(int code, String msg) {
        return new ResponseData(false, code, null, msg);
    }

    public static <T> ResponseData<T> errorData(int code) {
        return new ResponseData(false, code, null, "请求失败");
    }
}

