package com.tianji.auth.security;

import com.tianji.auth.domain.po.LoginRecord;
import com.tianji.auth.service.impl.LoginRecordServiceImpl;
import com.tianji.common.autoconfigure.reliability.OperationStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.json.JsonMapper;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginRecordAdmissionTest {
    private LoginRecord record() {
        var record=new LoginRecord();record.setUserId(42L);record.setLoginTime(LocalDateTime.of(2026,10,27,10,0));
        record.setLoginDate(record.getLoginTime().toLocalDate());record.setCellPhone("13800010001");record.setIpv4("127.0.0.1");return record;
    }
    @Test void unavailableDurableAdmissionFailsInsteadOfSilentlyDroppingTheRecord() {
        var operations=mock(OperationStore.class);var failure=new DataAccessResourceFailureException("injected database outage");
        when(operations.submit(anyLong(),anyString(),anyString(),any())).thenThrow(failure);
        var service=new LoginRecordServiceImpl(operations,JsonMapper.builder().build());
        assertSame(failure,assertThrows(DataAccessResourceFailureException.class,()->service.saveAsync(record())));
    }
    @Test void admissionCapturesTheOriginalRecordWithoutReusingAnInsertId() {
        var operations=mock(OperationStore.class);var service=new LoginRecordServiceImpl(operations,JsonMapper.builder().build());
        var record=record();record.setId(123L);service.saveAsync(record);record.setCellPhone("13800019999");
        var snapshot=ArgumentCaptor.forClass(Object.class);
        verify(operations).submit(eq(42L),eq("AUTH_LOGIN_RECORD"),anyString(),snapshot.capture());
        var saved=assertInstanceOf(LoginRecord.class,snapshot.getValue());
        assertEquals("13800010001",saved.getCellPhone());assertNull(saved.getId());assertEquals(record.getLoginTime(),saved.getLoginTime());
    }
    @Test void deliveryOutsideTheOperationTransactionIsRejected() {
        var service=new LoginRecordServiceImpl(mock(OperationStore.class),JsonMapper.builder().build());
        assertThrows(IllegalStateException.class,()->service.execute("unclaimed",42,"{}"));
    }
}
