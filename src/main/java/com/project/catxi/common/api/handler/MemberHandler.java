package com.project.catxi.common.api.handler;

import com.project.catxi.common.api.error.ErrorCode;
import com.project.catxi.common.api.exception.CatxiException;

public class MemberHandler extends CatxiException {
  public MemberHandler(ErrorCode errorCode) {
    super(errorCode);
  }
}
