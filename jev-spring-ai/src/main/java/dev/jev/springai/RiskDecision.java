package dev.jev.springai;

import dev.jev.model.JevResponse;

/**
 * Result of a Jev pre-flight risk check for a tool call.
 *
 * <p>Produced by {@link JevRiskAdvisor} before each tool executes. The advisor
 * uses this to decide whether to auto-approve, request human review, or block.
 */
public final class RiskDecision {

    /** Whether the tool call should proceed automatically. */
    public enum Verdict { APPROVE, REVIEW, BLOCK }

    private final Verdict verdict;
    private final double riskScore;       // 0.0–N (score index into risk levels)
    private final double needsHuman;      // 0.0–1.0 Jev noul probability
    private final double reversible;      // 0.0–1.0 Jev noul probability
    private final JevResponse rawResponse;
    private final String toolName;
    private final String toolInput;

    RiskDecision(
            Verdict verdict,
            double riskScore,
            double needsHuman,
            double reversible,
            JevResponse rawResponse,
            String toolName,
            String toolInput) {
        this.verdict      = verdict;
        this.riskScore    = riskScore;
        this.needsHuman   = needsHuman;
        this.reversible   = reversible;
        this.rawResponse  = rawResponse;
        this.toolName     = toolName;
        this.toolInput    = toolInput;
    }

    public Verdict getVerdict()          { return verdict; }
    public double getRiskScore()         { return riskScore; }
    public double getNeedsHuman()        { return needsHuman; }
    public double getReversible()        { return reversible; }
    public JevResponse getRawResponse()  { return rawResponse; }
    public String getToolName()          { return toolName; }
    public String getToolInput()         { return toolInput; }

    public boolean isApproved()  { return verdict == Verdict.APPROVE; }
    public boolean needsReview() { return verdict == Verdict.REVIEW; }
    public boolean isBlocked()   { return verdict == Verdict.BLOCK; }

    @Override
    public String toString() {
        return "RiskDecision{tool=%s, verdict=%s, risk=%.2f, needsHuman=%.2f, reversible=%.2f}"
                .formatted(toolName, verdict, riskScore, needsHuman, reversible);
    }
}
