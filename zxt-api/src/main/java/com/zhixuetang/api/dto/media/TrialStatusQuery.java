package com.zhixuetang.api.dto.media;

import java.util.Set;

public record TrialStatusQuery(Set<Long> mediaIds, int seconds) {}
