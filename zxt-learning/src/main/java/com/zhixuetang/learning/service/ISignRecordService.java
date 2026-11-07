package com.zhixuetang.learning.service;

import com.zhixuetang.learning.domain.vo.SignRecordVO;
import com.zhixuetang.learning.domain.vo.SignResultVO;

public interface ISignRecordService {
    SignResultVO addSignRecords();

    SignRecordVO querySignRecords();
}
