package com.tianji.compact;
import com.tianji.common.utils.ServiceIdentity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Map;
import static org.mockito.Mockito.*;

class DirectedModuleGuardTest {
    @TempDir Path temporary;
    private static final String TOKEN="fixture-reviewed-peer-token-0123456789abcdef";
    private ModuleGuard guard()throws Exception{
        Path file=temporary.resolve("identity.json");Files.writeString(file,"{\"outbound\":{},\"inbound\":{\"support\":[\""+TOKEN+"\"],\"gateway\":[\""+TOKEN+"\"]}}");
        return new ModuleGuard(25001,new ServiceIdentity(Map.of("TJ_INTERNAL_AUTH_MODE","service-tokens","TJ_SERVICE_ID","identity","TJ_SERVICE_TOKENS_FILE",file.toString())));
    }
    private HttpServletRequest request(String caller,String method,String path,int port,String depth){
        var request=mock(HttpServletRequest.class);when(request.getRequestURI()).thenReturn(path);when(request.getMethod()).thenReturn(method);when(request.getLocalPort()).thenReturn(port);when(request.getHeader("X-TJ-Service")).thenReturn(caller);when(request.getHeader("X-Internal-Token")).thenReturn(TOKEN);when(request.getHeader("X-TJ-Call-Depth")).thenReturn(depth);when(request.getHeader("user-info")).thenReturn("42");when(request.getHeader("user-role")).thenReturn("1");return request;
    }
    @Test void validSupportCredentialCannotImpersonateAdministratorThroughEitherPort()throws Exception{
        for(int port:new int[]{24001,25001})for(String[] endpoint:new String[][]{{"GET","/_modules/user/account-privacy/requests"},{"PUT","/_modules/user/users/42/password/default"},{"POST","/_modules/user/api/v2/admin/user/account-privacy/deletion"}}){
            var request=request("support",endpoint[0],endpoint[1],port,port==25001?"1":null);var response=mock(HttpServletResponse.class);var next=mock(FilterChain.class);guard().doFilter(request,response,next);verify(response).setStatus(403);verifyNoInteractions(next);
        }
    }
    @Test void legitimatePeerRpcAndGatewayAdminTargetPassAfterCredentialCheck()throws Exception{
        for(var request:new HttpServletRequest[]{request("support","GET","/_modules/user/users/list",25001,"1"),request("gateway","PUT","/_modules/user/users/42/password/default",24001,null),request("gateway","GET","/_modules/auth/internal/v2/sessions/fixture/validate",24001,null)}){
            var response=mock(HttpServletResponse.class);var next=mock(FilterChain.class);guard().doFilter(request,response,next);verify(next).doFilter(request,response);
        }
    }
    @Test void pathTraversalCannotTurnHealthExemptionIntoMetricsAccess()throws Exception{
        var request=request(null,"GET","/actuator/health/../prometheus",24001,null);var response=mock(HttpServletResponse.class);var next=mock(FilterChain.class);guard().doFilter(request,response,next);verify(response).setStatus(403);verifyNoInteractions(next);
    }
}
