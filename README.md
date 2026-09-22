# jev-spring-boot-starter

Spring Boot auto-configuration and Spring AI advisor for [TypeSafe Jev](https://typesafe.ai) — the first Java integration that goes beyond a raw HTTP client.

> **Jev** is TypeSafe AI's System One model. Instead of generating text, it answers typed questions about program state and returns typed decisions with probabilities in <100ms. 100x cheaper than frontier LLMs for decision tasks.

---

## What this adds

| Module | What it does |
|--------|-------------|
| `jev-client` | Zero-dependency Java HTTP client for `POST /v1/systemone` |
| `jev-spring-ai` | Spring AI `CallAdvisor` that scores every tool call with Jev before it executes |
| `jev-spring-boot-starter` | Spring Boot auto-configuration — zero config, just add an API key |

---

## Quickstart

**1. Add the starter:**
```xml
<dependency>
    <groupId>io.github.sumitvairagar</groupId>
    <artifactId>jev-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

**2. Set your API key:**
```yaml
# application.yml
jev:
  api-key: ${TYPESAFE_API_KEY}
```

**3. Wire the advisor into your ChatClient:**
```java
@Autowired JevRiskAdvisor jevRiskAdvisor;

ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(jevRiskAdvisor)
    .build();
```

That's it. Every tool call the agent makes now gets a Jev pre-flight check.

---

## How the risk advisor works

Before each tool executes, the advisor sends a parallel Jev request with three questions:

```
state: "Tool: chargeCard | Arguments: {amount: $5000, customer: cust_abc}"

questions:
  risk_level:   Score  — "How risky is this tool call?"    [Low, Medium, High]
  needs_human:  Noul   — "A human should review this"       → 0.0–1.0
  reversible:   Noul   — "This action can be undone"        → 0.0–1.0
```

The answers route the call:

| Condition | Verdict | Default action |
|-----------|---------|----------------|
| `needs_human < 0.6` | APPROVE | Tool executes |
| `needs_human ≥ 0.6` | REVIEW | `onReview` callback fires, tool still executes |
| `needs_human ≥ 0.9` AND `reversible < 0.3` | BLOCK | `onBlock` callback fires (throws by default) |

---

## With Sagacity approval gates

```java
@Bean
JevRiskAdvisor jevRiskAdvisor(JevClient jevClient, SagacityTemplate sagacity) {
    return JevRiskAdvisor.builder()
        .jev(jevClient)
        .onReview(decision ->
            sagacity.requestApproval(decision.getToolName(), decision.getToolInput()))
        .build();
}
```

High-risk tool calls automatically pause and wait for human approval. Low-risk calls auto-proceed. No hardcoded rules — Jev decides.

---

## Using the client directly

```java
JevClient client = JevClient.builder()
    .apiKey(System.getenv("TYPESAFE_API_KEY"))
    .build();

JevResponse response = client.ask(
    JevRequest.builder()
        .state("I want a refund immediately, this is the third time this happened")
        .question("urgent",      Question.noul("Is this urgent?"))
        .question("frustration", Question.score("How frustrated?")
                                         .level("Calm").level("Frustrated").level("Very angry"))
        .question("dept",        Question.choice("Which team?")
                                         .option("billing", "Payment issues")
                                         .option("technical", "Bugs"))
        .build()
);

double urgency    = response.noul("urgent").orElse(0);       // e.g. 0.97
double frustration = response.score("frustration").orElse(0); // e.g. 2.0 (Very angry)
String dept       = response.choice("dept").orElse("unknown"); // e.g. "billing"
```

---

## Configuration

```yaml
jev:
  api-key: ${TYPESAFE_API_KEY}    # required
  base-url: https://api.typesafe.ai  # optional override
  timeout: 10s                    # optional, default 10s
  enabled: true                   # set false to disable all beans
  risk-advisor:
    enabled: true                 # set false to skip the advisor bean
    review-threshold: 0.6         # needs_human above this → onReview
    block-threshold: 0.9          # needs_human above this + irreversible → onBlock
```

---

## Requirements

- Java 17+
- Spring Boot 4.0.x
- Spring AI 2.0.x
- TypeSafe API key from [console.typesafe.ai](https://console.typesafe.ai)

---

## Related

- [Sagacity](https://github.com/sagacity-ai/sagacity) — SAGA compensation for Spring AI agents. Wire `jevRiskAdvisor.onReview()` to `sagacity.requestApproval()` to get human-in-the-loop before irreversible tool calls + automatic rollback if something goes wrong after.
- [TypeSafe docs](https://docs.typesafe.ai)
- [jev-ultrafast](https://github.com/browser-use/jev-ultrafast) — browser agent using Jev for DOM decisions

---

## License

Apache 2.0
