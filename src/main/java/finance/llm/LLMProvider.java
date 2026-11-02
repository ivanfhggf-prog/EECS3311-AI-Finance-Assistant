package finance.llm;

/** Strategy interface: any model/provider can be plugged in without changing the callers. */
public interface LLMProvider {
    String generate(Prompt prompt) throws LLMException;
    String name();
}
