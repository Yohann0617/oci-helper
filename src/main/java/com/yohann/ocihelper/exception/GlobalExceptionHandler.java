package com.yohann.ocihelper.exception;

import com.yohann.ocihelper.bean.ResponseData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author: Yohann
 * @date: 2024/3/30 19:09
 */
@Slf4j
@ControllerAdvice
@ResponseBody
public class GlobalExceptionHandler {

    @ExceptionHandler
    public ResponseData<String> unknownException(Exception e) {
        if (e instanceof OciException) {
            OciException be = (OciException) e;
            if (be.getCause() != null) {
                log.error("business exception:{}, original exception : ", be.getMessage(), be.getCause());
            }

            return ResponseData.errorData(be.getCode(), be.getMessage());
        } else {
            return ResponseData.errorData(-1, e.getLocalizedMessage());
        }
    }

}
