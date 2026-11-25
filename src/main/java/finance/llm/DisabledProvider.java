package finance.llm;

/** Strategy used for "-LLMAPI none": every AI step reports that AI is switched off; deterministic features still work. */
public class DisabledProvider implements LLMProvider {
    @Override public String name() { return "none (AI switched off with -LLMAPI none)"; }
    @Override public String generate(Prompt prompt) throws LLMException {
        throw new LLMException("AI is switched off (-LLMAPI none). Run with -LLMAPI claude and an API key in .env to use AI features.");
    }
}
