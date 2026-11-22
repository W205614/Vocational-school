package com.zhixuetang.trade.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

public record OrderRefundProgressVO(Long id, Integer status, Integer amount, LocalDateTime appliedAt,
                                    List<OrderProgressNodeVO> nodes) {}
