package dev.jev.autoconfigure;

import dev.jev.client.JevClient;
import dev.jev.springai.JevRiskAdvisor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Jev.
 *
 * <h2>Bean registration order</h2>
 * <ol>
 *   <li>{@link JevClient} — registered if {@code jev.api-key} is set and
 *       {@code jev.enabled=true} (default). User-declared bean takes precedence
 *       via {@code @ConditionalOnMissingBean}.</li>
 *   <li>{@link JevRiskAdvisor} — registered if a {@link JevClient} bean is
 *       present and {@code jev.risk-advisor.enabled=true} (default). Wire the
 *       advisor into your {@code ChatClient} to enable pre-flight risk scoring
 *       on every tool call.</li>
 * </ol>
 *
 * <h2>Minimal setup</h2>
 * <pre>{@code
 * # application.yml
 * jev:
 *   api-key: ${TYPESAFE_API_KEY}
 * }</pre>
 *
 * <h2>Disable everything</h2>
 * <pre>{@code
 * jev:
 *   enabled: false
 * }</pre>
 */
@AutoConfiguration
@EnableConfigurationProperties(JevProperties.class)
@ConditionalOnProperty(prefix = "jev", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JevAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "jev", name = "api-key")
    public JevClient jevClient(JevProperties properties) {
        JevClient.Builder builder = JevClient.builder()
                .apiKey(properties.getApiKey())
                .timeout(properties.getTimeout());
        if (properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()) {
            builder.baseUrl(properties.getBaseUrl());
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JevClient.class)
    @ConditionalOnProperty(prefix = "jev.risk-advisor", name = "enabled", havingValue = "true", matchIfMissing = true)
    public JevRiskAdvisor jevRiskAdvisor(JevClient jevClient, JevProperties properties) {
        JevProperties.RiskAdvisor cfg = properties.getRiskAdvisor();
        return JevRiskAdvisor.builder()
                .jev(jevClient)
                .reviewThreshold(cfg.getReviewThreshold())
                .blockThreshold(cfg.getBlockThreshold())
                .build();
    }
}
