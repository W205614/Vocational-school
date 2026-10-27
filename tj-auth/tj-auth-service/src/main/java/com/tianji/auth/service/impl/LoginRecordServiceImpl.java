package com.tianji.auth.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tianji.auth.domain.po.LoginRecord;
import com.tianji.auth.mapper.LoginRecordMapper;
import com.tianji.auth.service.ILoginRecordService;
import com.tianji.common.autoconfigure.reliability.OperationHandler;
import com.tianji.common.autoconfigure.reliability.OperationStore;
import com.tianji.common.utils.WebUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * <p>
 * 登录信息记录表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-07-12
 */
@Service
public class LoginRecordServiceImpl extends ServiceImpl<LoginRecordMapper, LoginRecord> implements ILoginRecordService, OperationHandler {
    private final OperationStore operations;
    private final JsonMapper json;

    public LoginRecordServiceImpl(OperationStore operations, JsonMapper json) {
        this.operations = operations;
        this.json = json;
    }

    @Override
    public void saveAsync(LoginRecord record) {
        if(record.getUserId()==null || record.getUserId()<=0 || record.getLoginTime()==null)
            throw new IllegalArgumentException("A login record requires its verified owner and time");
        // Admission is durable before returning. The existing Spring-managed,
        // bounded operation executor only holds work that can be claimed again.
        LoginRecord snapshot=json.readValue(json.writeValueAsString(record),LoginRecord.class);
        snapshot.setId(null);
        operations.submit(snapshot.getUserId(),kind(),UUID.randomUUID().toString(),snapshot);
    }

    @Override public String kind() {return "AUTH_LOGIN_RECORD";}

    @Override public Object execute(String operationId,long userId,String payload) {
        // OperationStore commits this insert and SUCCEEDED under the same lease
        // and transaction. A retry after an uncertain commit cannot insert twice.
        if(!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Login record delivery requires the operation transaction");
        LoginRecord record=json.readValue(payload,LoginRecord.class);
        if(!Objects.equals(record.getUserId(),userId))throw new IllegalArgumentException("Login record owner differs");
        record.setId(null);
        if(!save(record))throw new IllegalStateException("Login record insert was not acknowledged");
        return Map.of("recorded",true);
    }

    @Override
    public void loginSuccess(String cellphone, Long userId) {
        LoginRecord record = new LoginRecord();
        LocalDateTime now = LocalDateTime.now();
        record.setLoginTime(now);
        record.setLoginDate(now.toLocalDate());
        record.setUserId(userId);
        record.setCellPhone(cellphone);
        record.setIpv4(WebUtils.getRemoteAddr());
        saveAsync(record);
    }
}
