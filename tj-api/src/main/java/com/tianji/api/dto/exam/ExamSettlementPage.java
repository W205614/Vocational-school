package com.tianji.api.dto.exam;

import java.util.List;

/** A bounded scan of committed examination events. Later commits are handled as late events. */
public record ExamSettlementPage(String month, long upperBound, long cursor,
                                 List<String> eventIds, long pending, Long nextCursor) {
    public static final int PAGE_SIZE = 1000;
}
