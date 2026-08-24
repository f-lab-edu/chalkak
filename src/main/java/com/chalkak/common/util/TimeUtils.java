package com.chalkak.common.util;

import java.time.LocalDateTime;
import java.time.ZoneId;

public final class TimeUtils {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private TimeUtils() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(KST);
    }
}
