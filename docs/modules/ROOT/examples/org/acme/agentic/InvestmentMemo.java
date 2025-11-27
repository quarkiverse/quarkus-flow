package org.acme.agentic;

import java.util.List;

/**
 * Output returned by the {@link InvestmentAnalystAgent}.
 */
public record InvestmentMemo(String summary, String stance, List<String> keyRisks) {
}
