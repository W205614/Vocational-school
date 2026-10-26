package com.tianji.common.reliability;

import com.tianji.common.utils.ServiceIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ServiceIdentityTest {
    @TempDir Path directory;
    private final String current="synthetic-current-"+"a".repeat(40),previous="synthetic-previous-"+"b".repeat(40);
    private ServiceIdentity identity(Path file) {return new ServiceIdentity(Map.of("TJ_INTERNAL_AUTH_MODE","service-tokens","TJ_SERVICE_ID","education","TJ_SERVICE_TOKENS_FILE",file.toString(),"TJ_INTERNAL_TOKEN","ignored-global-"+"c".repeat(40)));}
    @Test void directedCredentialsCannotBeUsedAsAnotherCallerOrDestination() throws Exception {
        Path file=directory.resolve("identities.json");
        Files.writeString(file,"{\"outbound\":{\"support\":\""+current+"\"},\"inbound\":{\"gateway\":[\""+current+"\",\""+previous+"\"]}}");
        var identity=identity(file);assertTrue(identity.accepts("gateway",current));assertTrue(identity.accepts("gateway",previous));
        assertFalse(identity.accepts("support",current));assertFalse(identity.accepts(null,current));assertFalse(identity.accepts("gateway","ignored-global-"+"c".repeat(40)));
        assertEquals(new ServiceIdentity.Credential("education",current),identity.outgoing("http://app-support:25004/_modules/media/read"));
        assertThrows(IllegalStateException.class,()->identity.outgoing("http://app-commerce:25002/_modules/pay/orders"));
    }
    @Test void brokenOrMissingKeyringDoesNotReusePreviouslyValidCredentials() throws Exception {
        Path file=directory.resolve("identities.json");Files.writeString(file,"{\"outbound\":{},\"inbound\":{\"gateway\":[\""+current+"\"]}}");
        var identity=identity(file);assertTrue(identity.accepts("gateway",current));
        Files.writeString(file,"{}");assertThrows(IllegalStateException.class,()->identity.accepts("gateway",current));
        assertThrows(IllegalStateException.class,()->identity.outgoing("http://unknown.example.invalid/request"));
    }
    @Test void rotationReadsAtomicallyReplacedKeyring() throws Exception {
        Path file=directory.resolve("identities.json");Files.writeString(file,"{\"outbound\":{},\"inbound\":{\"gateway\":[\""+previous+"\"]}}");
        var identity=identity(file);assertTrue(identity.accepts("gateway",previous));
        Path next=directory.resolve("next.json");Files.writeString(next,"{\"outbound\":{},\"inbound\":{\"gateway\":[\""+current+"\"]}}");
        Files.move(next,file,StandardCopyOption.REPLACE_EXISTING);
        assertTrue(identity.accepts("gateway",current));assertFalse(identity.accepts("gateway",previous));
    }
    @Test void sharedModeRemainsExplicitAndMissingProductionConfigurationFailsClosed() {
        var local=new ServiceIdentity(Map.of("TJ_INTERNAL_TOKEN",current));assertTrue(local.accepts(null,current));assertEquals(current,local.outgoing("http://localhost/test").token());
        assertThrows(IllegalStateException.class,()->new ServiceIdentity(Map.of("TJ_INTERNAL_AUTH_MODE","service-tokens")));
        assertThrows(IllegalStateException.class,()->new ServiceIdentity(Map.of("TJ_INTERNAL_AUTH_MODE","typo")));
    }
}
