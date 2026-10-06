package com.tianji.common.exceptions;
public class ServiceUnavailableException extends CommonException{
 public ServiceUnavailableException(String message){super(message);}
 @Override public int getStatus(){return 503;}
}
