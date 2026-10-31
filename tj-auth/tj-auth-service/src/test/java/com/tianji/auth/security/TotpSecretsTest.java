package com.tianji.auth.security;
import com.tianji.auth.service.impl.TotpSecrets;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class TotpSecretsTest {
    private static final String RFC_SECRET="GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    // Frozen pre-version AES-GCM ciphertext for user 42 and the zero-byte fixture key.
    private static final String LEGACY="AAECAwQFBgcICQoLz45pFkezza+FIWGxrEyUXRMp8t74082giJLXX9x6RT5tDEhdAaMfZaO8NLnmxiqV";
    @Test void matchesRfc6238Sha1Vectors(){
        long[] times={59,1111111109L,1111111111L,1234567890L,2000000000L,20000000000L};
        String[] expected={"94287082","07081804","14050471","89005924","69279037","65353130"};
        for(int i=0;i<times.length;i++)assertEquals(expected[i],TotpSecrets.code(RFC_SECRET,times[i]/30,8));
    }
    @Test void limitsWindowRejectsMalformedAndReplay(){
        long at=1234567890L,step=at/30;String code=TotpSecrets.code(RFC_SECRET,step,6);
        assertEquals(step,TotpSecrets.matchingStep(RFC_SECRET,code,at,-1));
        assertEquals(-1,TotpSecrets.matchingStep(RFC_SECRET,code,at,step));
        assertEquals(-1,TotpSecrets.matchingStep(RFC_SECRET,code,at+90,-1));
        assertEquals(-1,TotpSecrets.matchingStep(RFC_SECRET,"12x456",at,-1));
    }
    @Test void ciphertextIsRandomizedBoundToAccountAndAuthenticated(){
        var secrets=new TotpSecrets(Base64.getEncoder().encodeToString(new byte[32]));
        String first=secrets.encrypt(42,RFC_SECRET),second=secrets.encrypt(42,RFC_SECRET);
        assertNotEquals(first,second);assertFalse(first.contains(RFC_SECRET));assertEquals(RFC_SECRET,secrets.decrypt(42,first));
        assertThrows(IllegalStateException.class,()->secrets.decrypt(43,first));
        String prefix="v1."+secrets.activeKeyId()+".";assertTrue(first.startsWith(prefix));
        byte[] changed=Base64.getDecoder().decode(first.substring(prefix.length()));changed[changed.length-1]^=1;
        assertThrows(IllegalStateException.class,()->secrets.decrypt(42,prefix+Base64.getEncoder().encodeToString(changed)));
        assertThrows(IllegalArgumentException.class,()->new TotpSecrets("short"));
    }
    @Test void overlappingKeysReadLegacyAndVersionedSecretsButOnlyWriteTheActiveVersion(){
        String oldKey=Base64.getEncoder().encodeToString(new byte[32]);byte[] bytes=new byte[32];java.util.Arrays.fill(bytes,(byte)7);
        String newKey=Base64.getEncoder().encodeToString(bytes);var previous=new TotpSecrets(oldKey);
        var overlap=new TotpSecrets(newKey,Map.of(previous.activeKeyId(),oldKey),previous.activeKeyId());
        assertEquals(RFC_SECRET,overlap.decrypt(42,LEGACY));
        assertEquals(RFC_SECRET,overlap.decrypt(42,previous.encrypt(42,RFC_SECRET)));
        String rotated=overlap.reencrypt(42,LEGACY);assertTrue(rotated.startsWith("v1."+overlap.activeKeyId()+"."));
        assertEquals(RFC_SECRET,new TotpSecrets(newKey).decrypt(42,rotated));assertEquals(rotated,overlap.reencrypt(42,rotated));
        assertThrows(IllegalStateException.class,()->previous.decrypt(42,rotated));
        assertThrows(IllegalStateException.class,()->overlap.decrypt(42,"v2"+rotated.substring(2)));
        assertThrows(IllegalStateException.class,()->overlap.decrypt(42,rotated.replace(overlap.activeKeyId(),previous.activeKeyId())));
        assertThrows(IllegalArgumentException.class,()->new TotpSecrets(newKey,Map.of("wrong-id",oldKey),null));
        assertThrows(IllegalArgumentException.class,()->new TotpSecrets(newKey,Map.of(),previous.activeKeyId()));
        assertThrows(IllegalArgumentException.class,()->new TotpSecrets(oldKey,Map.of(previous.activeKeyId(),oldKey),null));
        assertThrows(IllegalArgumentException.class,()->new TotpSecrets(newKey,Map.of("a",oldKey,"b",oldKey,"c",oldKey),null));
    }
}
