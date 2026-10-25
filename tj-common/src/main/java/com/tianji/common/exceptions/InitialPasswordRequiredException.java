package com.tianji.common.exceptions;
/** A valid temporary credential may only enter password setup, never a session. */
public class InitialPasswordRequiredException extends CommonException {
    public InitialPasswordRequiredException(){super(428,"请先修改临时密码后重新登录");}
    @Override public int getStatus(){return 428;}
}
