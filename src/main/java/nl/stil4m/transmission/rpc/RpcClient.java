package nl.stil4m.transmission.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.google.common.base.Strings;
import nl.stil4m.transmission.http.InvalidResponseStatus;
import nl.stil4m.transmission.http.RequestExecutor;
import nl.stil4m.transmission.http.RequestExecutorException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class RpcClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcClient.class);
    private static final Integer STATUS_OK = 200;

    private final RpcConfiguration configuration;
    private final ObjectMapper objectMapper;
    private final Map<String, String> headers;
    private final DefaultHttpClient defaultHttpClient = new DefaultHttpClient();

    private final RequestExecutor requestExecutor;

    public RpcClient(RpcConfiguration configuration, ObjectMapper objectMapper) {
        this.requestExecutor = new RequestExecutor(objectMapper, configuration, defaultHttpClient);
        this.configuration = configuration;
        this.objectMapper = objectMapper;
        headers = new HashMap<>();
    }

    public <T, V> void execute(RpcCommand<T, V> command, Map<String, String> h) throws RpcException {
        try {
            executeCommandInner(command, h);
        } catch (RequestExecutorException | IOException e) {
            throw new RpcException(e);
        } catch (InvalidResponseStatus e) {
            LOGGER.trace("Failed execute command. Now run setup and try again", e);
            setup();
            try {
                executeCommandInner(command, h);
            } catch (Exception | RequestExecutorException | InvalidResponseStatus inner) {
                throw new RpcException(inner);
            }
        }
    }

    private <T, V> void executeCommandInner(RpcCommand<T, V> command, Map<String, String> h) throws RequestExecutorException, InvalidResponseStatus, IOException, RpcException {
        requestExecutor.removeAllHeaders();
        for (Map.Entry<String, String> entry : h.entrySet()) {
            requestExecutor.configureHeader(entry.getKey(), entry.getValue());
        }

        RpcRequest<T> request = command.buildRequestPayload();
        RpcResponse<V> response = requestExecutor.execute(request, RpcResponse.class, STATUS_OK);

        Map args = (Map) response.getArguments();
        String stringValue = objectMapper.writeValueAsString(args);
        response.setArguments((V) objectMapper.readValue(stringValue, command.getArgumentsObject()));
        if (!command.getTag().equals(response.getTag())) {
            throw new RpcException(String.format("Invalid response tag. Expected %d, but got %d", command.getTag(), request.getTag()));
        }
        command.setResponse(response);

        if (!"success".equals(response.getResult())) {
            throw new RpcException("Rpc Command Failed: " + response.getResult(), command);
        }
    }

    private void setup() throws RpcException {
        try {
            HttpPost httpPost = createPost();
            HttpResponse result = defaultHttpClient.execute(httpPost);
            putSessionHeader(result);
            putAuthenticationHeader();
            EntityUtils.consume(result.getEntity());
        } catch (IOException e) {
            throw new RpcException(e);
        }
    }

    private void putAuthenticationHeader() {
        if (!Strings.isNullOrEmpty(configuration.getUsername()) || !Strings.isNullOrEmpty(configuration.getPassword())) {
            headers.put("Authorization", getBasicAuthenticationEncoding(configuration.getUsername(), configuration.getPassword()));
        }
    }

    protected HttpPost createPost() {
        HttpPost httpPost = new HttpPost(configuration.getHost());

        if (!Strings.isNullOrEmpty(configuration.getUsername()) || !Strings.isNullOrEmpty(configuration.getPassword())) {
            httpPost.addHeader("Authorization", getBasicAuthenticationEncoding(configuration.getUsername(), configuration.getPassword()));
        }

        return httpPost;
    }

    private String getBasicAuthenticationEncoding(String username, String password) {
        String userPassword = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(userPassword.getBytes());
    }

    protected DefaultHttpClient getClient() {
        return defaultHttpClient;
    }

    public void executeWithHeaders(RpcCommand command) throws RpcException {
        execute(command, headers);
    }

    private void putSessionHeader(HttpResponse result) {
        headers.put("X-Transmission-Session-Id", result.getFirstHeader("X-Transmission-Session-Id").getValue());
    }
}
