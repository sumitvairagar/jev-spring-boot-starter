package dev.jev.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import dev.jev.model.JevRequest;
import dev.jev.model.JevResponse;
import dev.jev.model.JevResponseDeserializer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP client for the TypeSafe Jev API ({@code POST /v1/systemone}).
 *
 * <p>Create via the builder:
 * <pre>{@code
 * JevClient client = JevClient.builder()
 *     .apiKey(System.getenv("TYPESAFE_API_KEY"))
 *     .build();
 *
 * JevResponse r = client.ask(
 *     JevRequest.builder()
 *         .state("Customer: first-time buyer | Tool: chargeCard | Amount: $5,000")
 *         .question("risk",        Question.score("Financial risk").level("Low").level("Medium").level("High"))
 *         .question("needs_human", Question.noul("A human should review before executing"))
 *         .build()
 * );
 *
 * double riskScore = r.score("risk").orElse(0);
 * }</pre>
 *
 * <p>This client is thread-safe and should be a singleton (reuse the underlying
 * {@link HttpClient} connection pool).
 */
public final class JevClient {

    static final String DEFAULT_BASE_URL = "https://api.typesafe.ai";
    private static final String SYSTEM_ONE_PATH = "/v1/systemone";

    private final String apiKey;
    private final String baseUrl;
    private final Duration timeout;
    private final HttpClient http;
    private final ObjectMapper mapper;

    private JevClient(Builder b) {
        this.apiKey  = b.apiKey;
        this.baseUrl = b.baseUrl;
        this.timeout = b.timeout;
        this.http    = HttpClient.newBuilder()
                .connectTimeout(b.timeout)
                .build();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(JevResponse.class, new JevResponseDeserializer());
        this.mapper  = new ObjectMapper().registerModule(module);
    }

    /**
     * Send a synchronous request to Jev and return the typed response.
     *
     * @throws JevException      if the API returns a non-2xx status
     * @throws JevIOException    if the HTTP request fails
     */
    public JevResponse ask(JevRequest request) {
        try {
            String body = mapper.writeValueAsString(request);
            HttpRequest httpRequest = buildRequest(body);
            HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            return parseResponse(response);
        } catch (JevException | JevIOException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JevIOException("Request interrupted", e);
        } catch (Exception e) {
            throw new JevIOException("Request failed", e);
        }
    }

    /**
     * Send an asynchronous request to Jev.
     */
    public CompletableFuture<JevResponse> askAsync(JevRequest request) {
        try {
            String body = mapper.writeValueAsString(request);
            HttpRequest httpRequest = buildRequest(body);
            return http.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                    .thenApply(this::parseResponse);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(new JevIOException("Failed to build request", e));
        }
    }

    private HttpRequest buildRequest(String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + SYSTEM_ONE_PATH))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(timeout)
                .build();
    }

    private JevResponse parseResponse(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new JevException(status, response.body());
        }
        try {
            return mapper.readValue(response.body(), JevResponse.class);
        } catch (IOException e) {
            throw new JevIOException("Failed to parse Jev response", e);
        }
    }

    public static Builder builder() { return new Builder(); }

    // -----------------------------------------------------------------------

    public static final class Builder {
        private String apiKey;
        private String baseUrl  = DEFAULT_BASE_URL;
        private Duration timeout = Duration.ofSeconds(10);

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** Override the API base URL (e.g. for testing or proxying). */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public JevClient build() {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException(
                    "Jev API key must not be blank. Set TYPESAFE_API_KEY or jev.api-key in application.yml.");
            }
            return new JevClient(this);
        }
    }

    // -----------------------------------------------------------------------
    // Exceptions
    // -----------------------------------------------------------------------

    /** Thrown when the Jev API returns a non-2xx HTTP status. */
    public static final class JevException extends RuntimeException {
        private final int statusCode;
        private final String responseBody;

        public JevException(int statusCode, String responseBody) {
            super("Jev API error " + statusCode + ": " + responseBody);
            this.statusCode   = statusCode;
            this.responseBody = responseBody;
        }

        public int getStatusCode()      { return statusCode; }
        public String getResponseBody() { return responseBody; }
    }

    /** Thrown when the HTTP request itself fails (network error, timeout, etc.). */
    public static final class JevIOException extends RuntimeException {
        public JevIOException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
