package com.tianji.common.exceptions;
public class ConflictException extends CommonException {
    public ConflictException(String message) { super(409,message); }
    @Override public int getStatus() { return 409; }
}
