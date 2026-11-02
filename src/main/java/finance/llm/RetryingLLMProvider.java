package finance.llm;

/** Decorator: adds retry with backoff around any LLMProvider (failure recovery, no change to callers). */
public class RetryingLLMProvider implements LLMProvider {
    private final LLMProvider inner;
    private final int attempts;
    private final long backoffMs;

    public RetryingLLMProvider(LLMProvider inner, int attempts, long backoffMs) { this.inner = inner; this.attempts = attempts; this.backoffMs = backoffMs; }

    @Override public String name() { return inner.name() + " +retry"; }

    @Override public String generate(Prompt p) throws LLMException {
        LLMException last = null;
        for (int i = 1; i <= attempts; i++) {
            try { return inner.generate(p); }
            catch (LLMException e) {
                last = e;
                if (e.getMessage() != null && e.getMessage().startsWith("No LLM API key")) throw e; // retrying cannot help
                if (i < attempts) {
                    try { Thread.sleep(backoffMs * i); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw e; }
                }
            }
        }
        throw new LLMException("LLM failed after " + attempts + " attempts: " + last.getMessage(), last);
    }
}
