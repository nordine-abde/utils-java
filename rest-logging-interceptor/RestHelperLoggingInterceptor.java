
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Slf4j
public class RestHelperLoggingInterceptor implements ClientHttpRequestInterceptor {

    @Value("${restHelper.logging.verbose:false}")
    private boolean verbose;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        traceRequest(request, body);
        ClientHttpResponse response = execution.execute(request, body);
        traceResponse(response);
        return response;
    }

    private void traceRequest(HttpRequest request, byte[] body) throws IOException {
        log.debug("===========================request begin================================================");
        log.debug("URI         : {}", request.getURI());
        if (verbose) {
            log.debug("Method      : {}", request.getMethod());
            log.debug("Headers     : {}", request.getHeaders());
        }
        log.debug("Request body: {}", new String(body, StandardCharsets.UTF_8));
        log.debug("==========================request end================================================");
    }

    private void traceResponse(ClientHttpResponse response) throws IOException {
        StringBuilder inputStringBuilder = new StringBuilder();
        BufferedReader bufferedReader;
        try {
            bufferedReader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8));
            String line = bufferedReader.readLine();
            while (line != null) {
                inputStringBuilder.append(line);
                inputStringBuilder.append('\n');
                line = bufferedReader.readLine();
            }
        } catch (Exception e) {
            log.debug("CANNOT READ RESPONSE BODY");
            return;
        }

        log.debug("============================response begin==========================================");
        if (verbose) {
            log.debug("Status text  : {}", response.getStatusText());
            log.debug("Headers      : {}", response.getHeaders());
        }
        log.debug("Status code  : {}", response.getStatusCode());
        log.debug("Response body: {}", inputStringBuilder);
        log.debug("=======================response end=================================================");
    }

}