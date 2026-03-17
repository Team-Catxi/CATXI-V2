package com.project.catxi.common.api.handler;

import com.project.catxi.common.api.error.ErrorCode;
import com.project.catxi.common.api.exception.CatxiException;

public class ReportExceptionHandler extends CatxiException {
    public ReportExceptionHandler(ErrorCode errorCode) {
        super(errorCode);
    }
}
