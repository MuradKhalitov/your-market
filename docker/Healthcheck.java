import java.net.HttpURLConnection;
import java.net.URI;

public final class Healthcheck {

    private static final String HEALTH_URL = "http://localhost:8080/actuator/health";

    private Healthcheck() {
    }

    public static void main(String[] args) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(HEALTH_URL).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2_000);
            connection.setReadTimeout(2_000);

            int status = connection.getResponseCode();
            connection.disconnect();
            if (status < 200 || status >= 400) {
                System.exit(1);
            }
        } catch (Exception exception) {
            System.exit(1);
        }
    }
}
