package com.tianji.common.exceptions;
public class TooManyRequestsException extends CommonException {
 public TooManyRequestsException(String message){super(429,message);}
 @Override public int getStatus(){return 429;}
}
