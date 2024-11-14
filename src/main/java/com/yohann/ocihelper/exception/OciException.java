package com.yohann.ocihelper.exception;

import com.yohann.ocihelper.enums.ErrorEnum;

/**
 * <p>
 * OciException
 * </p >
 *
 * @author yohann
 * @since 2024/11/7 18:57
 */
public class OciException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final int code;

    public OciException(ErrorEnum error) {
        super(error.getMessage());
        this.code = error.getCode();
    }

    public OciException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public OciException(int code, String msg, Exception e) {
        super(msg, e);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
