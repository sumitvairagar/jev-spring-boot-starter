package dev.jev.springai;

import dev.jev.client.JevClient;
import dev.jev.model.JevRequest;
import dev.jev.model.JevResponse;
import dev.jev.model.Question;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Spring AI {@link CallAdvisor} that uses Jev to score the risk of each
 * tool call <em>before</em> the agent executes it.
 *
 * <p>After the model responds with tool calls, this advisor intercepts each
 * call, asks Jev three questions in one parallel request (&lt;100ms), and
 * routes based on the result:
 *
 * <ul>
 *   <li><strong>APPROVE</strong> — risk is low, tool executes automatically.</li>
 *   <li><strong>REVIEW</strong> — risk is elevated, the {@code onReview} callback
 *       fires. Default: log a warning and proceed. Override to trigger Sagacity's
 *       approval gate, send a Slack alert, etc.</li>
 *   <li><strong>BLOCK</strong> — risk is high and the action is irreversible.
 *       Default: throw {@link BlockedByJevException}. Override to handle differently.</li>
 * </ul>
 *
 * <h2>Minimal usage</h2>
 * <pre>{@code
 * // Declared automatically by jev-spring-boot-starter when jev.api-key is set.
 * // Wire into ChatClient:
 * ChatClient.builder(chatModel)
 *     .defaultAdvisors(jevRiskAdvisor)
 *     .build();
 * }</pre>
 *
 * <h2>With Sagacity approval gate</h2>
 * <pre>{@code
 * JevRiskAdvisor.builder()
 *     .jev(jevClient)
 *     .onReview(decision -> sagacity.requestApproval(decision.getToolName()))
 *     .build();
 * }</pre>
 */
public final class JevRiskAdvisor implements CallAdvisor {

    private static final Logger LOG = Logger.getLogger(JevRiskAdvisor.class.getName());

    static final String RISK_QUESTION    = "risk_level";
    static final String HUMAN_QUESTION   = "needs_human";
    static final String REVERSE_QUESTION = "reversible";

    private final JevClient jev;
    private final double reviewThreshold;
    private final double blockThreshold;
    private final Consumer<RiskDecision> onReview;
    private final Consumer<RiskDecision> onBlock;
    private final int order;

    private JevRiskAdvisor(Builder b) {
        this.jev             = b.jev;
        this.reviewThreshold = b.reviewThreshold;
        this.blockThreshold  = b.blockThreshold;
        this.onReview        = b.onReview;
        this.onBlock         = b.onBlock;
        this.order           = b.order;
    }

    @Override
    public String getName() { return "JevRiskAdvisor"; }

    @Override
    public int getOrder() { return order; }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        // Let the model respond first, then intercept any tool calls it wants to make
        ChatClientResponse response = chain.nextCall(request);

        List<AssistantMessage.ToolCall> toolCalls = extractToolCalls(response);
        if (toolCalls.isEmpty()) return response;

        for (AssistantMessage.ToolCall toolCall : toolCalls) {
            RiskDecision decision = score(toolCall);
            LOG.fine(() -> "Jev pre-flight: " + decision);

            switch (decision.getVerdict()) {
                case APPROVE -> { /* proceed */ }
                case REVIEW  -> onReview.accept(decision);
                case BLOCK   -> onBlock.accept(decision);
            }
        }

        return response;
    }

    private RiskDecision score(AssistantMessage.ToolCall toolCall) {
        String state = "Tool: %s | Arguments: %s".formatted(toolCall.name(), toolCall.arguments());

        JevRequest jevRequest = JevRequest.builder()
                .state(state)
                .question(RISK_QUESTION,    Question.score("How risky is this tool call?")
                                                    .level("Low").level("Medium").level("High"))
                .question(HUMAN_QUESTION,   Question.noul("A human should review this before it executes"))
                .question(REVERSE_QUESTION, Question.noul("This action can be undone if it turns out to be wrong"))
                .build();

        JevResponse jevResponse;
        try {
            jevResponse = jev.ask(jevRequest);
        } catch (Exception e) {
            // Jev unavailable — fail open (approve) with a warning so the agent isn't blocked
            LOG.warning("Jev risk scoring failed for tool " + toolCall.name() + ": " + e.getMessage()
                        + " — approving by default (fail-open)");
            return new RiskDecision(RiskDecision.Verdict.APPROVE, 0, 0, 1,
                    new JevResponse(), toolCall.name(), toolCall.arguments());
        }

        double riskScore  = jevResponse.score(RISK_QUESTION).orElse(0.0);
        double needsHuman = jevResponse.noul(HUMAN_QUESTION).orElse(0.0);
        double reversible = jevResponse.noul(REVERSE_QUESTION).orElse(1.0);

        RiskDecision.Verdict verdict;
        if (needsHuman >= blockThreshold && reversible < 0.3) {
            verdict = RiskDecision.Verdict.BLOCK;
        } else if (needsHuman >= reviewThreshold) {
            verdict = RiskDecision.Verdict.REVIEW;
        } else {
            verdict = RiskDecision.Verdict.APPROVE;
        }

        return new RiskDecision(verdict, riskScore, needsHuman, reversible,
                jevResponse, toolCall.name(), toolCall.arguments());
    }

    private List<AssistantMessage.ToolCall> extractToolCalls(ChatClientResponse response) {
        try {
            ChatResponse chatResponse = response.chatResponse();
            if (chatResponse == null) return List.of();
            Generation result = chatResponse.getResult();
            if (result == null) return List.of();
            AssistantMessage msg = result.getOutput();
            if (msg == null) return List.of();
            List<AssistantMessage.ToolCall> calls = msg.getToolCalls();
            return calls != null ? calls : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    public static Builder builder() { return new Builder(); }

    // -----------------------------------------------------------------------

    public static final class Builder {
        private JevClient              jev;
        private double                 reviewThreshold = 0.6;
        private double                 blockThreshold  = 0.9;
        private Consumer<RiskDecision> onReview        = d -> LOG.warning("Jev REVIEW: " + d);
        private Consumer<RiskDecision> onBlock         = d -> { throw new BlockedByJevException(d); };
        private int                    order           = 100;

        public Builder jev(JevClient jev)                           { this.jev = jev; return this; }
        public Builder reviewThreshold(double reviewThreshold)      { this.reviewThreshold = reviewThreshold; return this; }
        public Builder blockThreshold(double blockThreshold)        { this.blockThreshold = blockThreshold; return this; }
        public Builder onReview(Consumer<RiskDecision> onReview)    { this.onReview = onReview; return this; }
        public Builder onBlock(Consumer<RiskDecision> onBlock)      { this.onBlock = onBlock; return this; }
        public Builder order(int order)                             { this.order = order; return this; }

        public JevRiskAdvisor build() {
            if (jev == null) throw new IllegalStateException("JevClient must be set");
            return new JevRiskAdvisor(this);
        }
    }

    // -----------------------------------------------------------------------

    /** Thrown when a tool call is blocked by the Jev risk advisor. */
    public static final class BlockedByJevException extends RuntimeException {
        private final RiskDecision decision;

        public BlockedByJevException(RiskDecision decision) {
            super("Tool call blocked by Jev risk advisor: " + decision);
            this.decision = decision;
        }

        public RiskDecision getDecision() { return decision; }
    }
}
