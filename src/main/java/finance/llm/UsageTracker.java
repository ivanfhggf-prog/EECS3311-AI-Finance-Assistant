package finance.llm;

import java.util.concurrent.atomic.AtomicLong;

/** Counts runtime LLM calls and tokens (reported by the 'usage' command / report). */
public final class UsageTracker {
    public static final AtomicLong calls = new AtomicLong(), inputTokens = new AtomicLong(), outputTokens = new AtomicLong();
    private UsageTracker() {}
    public static String summary() { return calls + " call(s), " + inputTokens + " input tokens, " + outputTokens + " output tokens"; }
}
