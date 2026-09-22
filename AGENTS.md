# jev-spring-boot-starter — Agent Instructions

Spring Boot auto-configuration and Spring AI advisor for TypeSafe Jev.
Three modules: `jev-client` (HTTP client), `jev-spring-ai` (advisor), `jev-spring-boot-starter` (auto-config).
Published to Maven Central as `io.github.sumitvairagar:jev-spring-boot-starter:0.1.0`.

---

## Commands

```bash
# Build and test all modules
mvn test

# Test a single module
mvn test -pl jev-client
mvn test -pl jev-spring-ai
mvn test -pl jev-spring-boot-starter

# Full verify (runs integration tests if any)
mvn verify

# Publish to Maven Central (requires GPG key + Sonatype credentials)
# Sonatype login: GitLab account at central.sonatype.com
# Then manually release the bundle at https://central.sonatype.com/publishing
mvn -Prelease clean deploy
```

---

## Module structure

```
jev-client/                         # Zero-dependency Java HTTP client
  src/main/java/dev/jev/
    client/JevClient.java           # Main client — sync + async ask()
    model/Question.java             # Sealed hierarchy: Noul, Choice, Score
    model/JevRequest.java           # Request builder
    model/JevResponse.java          # Typed response: noul(), score(), choice()
    model/JevResponseDeserializer.java  # Jackson deserializer for polymorphic answers

jev-spring-ai/                      # Spring AI CallAdvisor
  src/main/java/dev/jev/springai/
    JevRiskAdvisor.java             # Intercepts tool calls, scores risk with Jev
    RiskDecision.java               # APPROVE / REVIEW / BLOCK verdict + scores

jev-spring-boot-starter/            # Spring Boot auto-configuration
  src/main/java/dev/jev/autoconfigure/
    JevAutoConfiguration.java       # @AutoConfiguration — wires JevClient + JevRiskAdvisor
    JevProperties.java              # Binds jev.* properties from application.yml
  src/main/resources/META-INF/spring/
    org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

---

## Stack

- Java 17 (source/target)
- Spring Boot 4.0.2
- Spring AI 2.0.0 (`spring-ai-client-chat` artifact for the advisor)
- Jackson 2.18.2 (only runtime dependency in `jev-client`)
- JUnit 5.11.4 + AssertJ 3.26.3 for tests
- Maven with `maven-compiler-plugin` — `<parameters>true</parameters>` is required (Spring parameter name binding)

---

## Conventions

- Package root: `dev.jev.*`
- All public APIs use builders — never public constructors with many args
- `JevClient` is thread-safe and should be a singleton (reuses `HttpClient` connection pool)
- `JevRiskAdvisor` is stateless — safe to share across threads
- Validation happens in builders before any network call (`IllegalStateException` on bad input)
- Fail-open: if Jev is unavailable, `JevRiskAdvisor` logs a warning and approves the tool call
- Tests use `ApplicationContextRunner` for auto-configuration tests — never start a full Spring context
- No Lombok, no MapStruct — plain Java records and builders only

---

## What NOT to do

- Do not bump Spring Boot or Spring AI versions without checking compatibility (Boot 4.0.x requires Spring Framework 7.x)
- Do not add dependencies to `jev-client` — it must stay zero-dependency (only Jackson)
- Do not change the `groupId` (`io.github.sumitvairagar`) — Maven Central coordinates are permanent
- Do not modify the `release` Maven profile — it controls GPG signing and Central publishing
- Do not auto-publish to Maven Central (`autoPublish=false` is intentional — manual release required)
- Do not add `@Slf4j` or any logging framework — use `java.util.logging.Logger` for consistency with JDK

---

## API key for testing

Set `TYPESAFE_API_KEY` environment variable. Get a key at https://console.typesafe.ai.
For unit tests, use `MockRestServiceServer` or mock `JevClient` directly — never call the real API in tests.

---

## Related projects

- [Sagacity](https://github.com/sagacity-ai/sagacity) — SAGA compensation for Spring AI. Wire `JevRiskAdvisor.onReview()` to `sagacity.requestApproval()`.
- [agent-workflows](https://github.com/sumitvairagar/agent-workflows) — Verifiable workflow engine for Java agents. Jev is the risk-scoring layer, agent-workflows is the execution layer.
