package com.tianji.common.autoconfigure.reliability;
/** Handlers only perform local database work inside the operation transaction. */
public interface OperationHandler {
    String kind();
    Object execute(String operationId, long userId, String payload);
}
