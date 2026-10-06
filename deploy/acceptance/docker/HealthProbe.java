import java.net.URI;
import java.net.http.*;
import java.time.Duration;
public class HealthProbe {
    public static void main(String[] args) {
        try {
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+System.getenv("APP_PORT")+"/actuator/health/readiness"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            int status=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
                    .send(request,HttpResponse.BodyHandlers.discarding()).statusCode();
            System.exit(status==200?0:1);
        } catch(Exception failure) { System.exit(1); }
    }
}
