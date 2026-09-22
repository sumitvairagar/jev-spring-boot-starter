package dev.jev.autoconfigure;

import dev.jev.client.JevClient;
import dev.jev.springai.JevRiskAdvisor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class JevAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JevAutoConfiguration.class));

    @Test
    void no_beans_when_api_key_missing() {
        runner.run(ctx -> {
            assertThat(ctx).doesNotHaveBean(JevClient.class);
            assertThat(ctx).doesNotHaveBean(JevRiskAdvisor.class);
        });
    }

    @Test
    void jev_client_registered_when_api_key_set() {
        runner.withPropertyValues("jev.api-key=test-key")
                .run(ctx -> assertThat(ctx).hasSingleBean(JevClient.class));
    }

    @Test
    void risk_advisor_registered_when_client_present() {
        runner.withPropertyValues("jev.api-key=test-key")
                .run(ctx -> assertThat(ctx).hasSingleBean(JevRiskAdvisor.class));
    }

    @Test
    void risk_advisor_absent_when_disabled() {
        runner.withPropertyValues("jev.api-key=test-key", "jev.risk-advisor.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(JevRiskAdvisor.class));
    }

    @Test
    void all_beans_absent_when_jev_disabled() {
        runner.withPropertyValues("jev.api-key=test-key", "jev.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(JevClient.class);
                    assertThat(ctx).doesNotHaveBean(JevRiskAdvisor.class);
                });
    }

    @Test
    void user_declared_client_takes_precedence() {
        runner.withPropertyValues("jev.api-key=test-key")
                .withBean(JevClient.class, () -> JevClient.builder().apiKey("custom-key").build())
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(JevClient.class);
                    // the user bean — not the auto-configured one
                });
    }

    @Test
    void custom_thresholds_applied() {
        runner.withPropertyValues(
                        "jev.api-key=test-key",
                        "jev.risk-advisor.review-threshold=0.5",
                        "jev.risk-advisor.block-threshold=0.85")
                .run(ctx -> assertThat(ctx).hasSingleBean(JevRiskAdvisor.class));
    }
}
