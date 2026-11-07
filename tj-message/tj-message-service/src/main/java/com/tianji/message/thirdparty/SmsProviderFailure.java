package com.tianji.message.thirdparty;

/** Only an explicit provider rejection is safe to retry. Transport failures are ambiguous. */
public final class SmsProviderFailure extends RuntimeException {
    private final boolean unknown;

    public SmsProviderFailure(String code, boolean unknown) {
        super(safeCode(code));
        this.unknown = unknown;
    }

    public boolean unknown() {
        return unknown;
    }

    public static String safeCode(String code) {
        return code != null && code.matches("[A-Za-z0-9_.-]{1,100}")
                ? code
                : "SMS_PROVIDER_FAILURE";
    }
}
