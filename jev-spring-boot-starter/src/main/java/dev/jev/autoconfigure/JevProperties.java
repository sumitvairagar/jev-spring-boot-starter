package dev.jev.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for Jev.
 *
 * <pre>{@code
 * # application.yml
 * jev:
 *   api-key: ${TYPESAFE_API_KEY}      # required
 *   base-url: https://api.typesafe.ai # optional override
 *   timeout: 10s                      # optional, default 10s
 *   enabled: true                     # set false to disable all beans
 *   risk-advisor:
 *     enabled: true                   # set false to skip the advisor bean
 *     review-threshold: 0.6           # needs_human above this → onReview callback
 *     block-threshold: 0.9            # needs_human above this + irreversible → onBlock callback
 * }</pre>
 */
@ConfigurationProperties(prefix = "jev")
public class JevProperties {

    /** TypeSafe API key. Required unless {@code jev.enabled=false}. */
    private String apiKey;

    /** API base URL. Defaults to {@code https://api.typesafe.ai}. */
    private String baseUrl;

    /** HTTP request timeout. Defaults to 10 seconds. */
    private Duration timeout = Duration.ofSeconds(10);

    /** Set {@code false} to disable all Jev beans. Default: {@code true}. */
    private boolean enabled = true;

    private final RiskAdvisor riskAdvisor = new RiskAdvisor();

    public String getApiKey()             { return apiKey; }
    public void setApiKey(String apiKey)  { this.apiKey = apiKey; }

    public String getBaseUrl()            { return baseUrl; }
    public void setBaseUrl(String url)    { this.baseUrl = url; }

    public Duration getTimeout()          { return timeout; }
    public void setTimeout(Duration t)    { this.timeout = t; }

    public boolean isEnabled()            { return enabled; }
    public void setEnabled(boolean e)     { this.enabled = e; }

    public RiskAdvisor getRiskAdvisor()   { return riskAdvisor; }

    // -----------------------------------------------------------------------

    public static class RiskAdvisor {
        private boolean enabled         = true;
        private double  reviewThreshold = 0.6;
        private double  blockThreshold  = 0.9;

        public boolean isEnabled()                    { return enabled; }
        public void setEnabled(boolean e)             { this.enabled = e; }

        public double getReviewThreshold()            { return reviewThreshold; }
        public void setReviewThreshold(double t)      { this.reviewThreshold = t; }

        public double getBlockThreshold()             { return blockThreshold; }
        public void setBlockThreshold(double t)       { this.blockThreshold = t; }
    }
}
