package com.zhixuetang.auth.security;

import com.zhixuetang.auth.service.impl.RefreshCookieCipher;
import com.zhixuetang.common.exceptions.UnauthorizedException;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RefreshCookieCipherTest {
    private final RefreshCookieCipher cipher = new RefreshCookieCipher("synthetic-private-seed-01234567890123456789");

    @Test
    void storesAuthenticatedCiphertextWithFreshNonceAndRoundTrips() {
        String first = cipher.encrypt("signed.jwt.refresh");
        assertEquals("signed.jwt.refresh", cipher.decrypt(first));
        assertNotEquals(first, cipher.encrypt("signed.jwt.refresh"));
        assertFalse(new String(Base64.getUrlDecoder().decode(first.substring(3)),
                java.nio.charset.StandardCharsets.ISO_8859_1).contains("signed.jwt.refresh"));
    }

    @Test
    void rejectsTamperingWrongKeysAndOldUnencryptedCookies() {
        String encrypted = cipher.encrypt("signed.jwt.refresh");
        byte[] changed = Base64.getUrlDecoder().decode(encrypted.substring(3));
        changed[changed.length - 1] ^= 1;
        assertThrows(UnauthorizedException.class, () -> cipher.decrypt("v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(changed)));
        var other = new RefreshCookieCipher("different-private-seed-01234567890123456789");
        assertThrows(UnauthorizedException.class, () -> other.decrypt(encrypted));
        for (String invalid : new String[] {"signed.jwt.refresh", "v1.", "v2." + encrypted, "v1.invalid", "x".repeat(4097)})
            assertThrows(UnauthorizedException.class, () -> cipher.decrypt(invalid));
    }
}
