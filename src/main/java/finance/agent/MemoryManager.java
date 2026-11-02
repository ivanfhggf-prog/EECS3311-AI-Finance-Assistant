package finance.agent;

import finance.domain.Category;
import java.util.*;

/** Agent memory: user corrections (reused by categorisation), dismissed flags, rejected merges, conversation turns. */
public class MemoryManager {
    private final Map<String, Category> corrections = new LinkedHashMap<>();
    private final Set<String> dismissedMerchants = new HashSet<>();
    private final Set<String> rejectedMerges = new HashSet<>();
    private final Deque<String[]> turns = new ArrayDeque<>();
    private static final int MAX_TURNS = 6;

    public static String key(String merchant) { return merchant == null ? "" : merchant.toLowerCase().replaceAll("[^a-z0-9]", ""); }

    public void rememberCorrection(String merchant, Category c) { corrections.put(key(merchant), c); }
    public void forgetCorrection(String merchant) { corrections.remove(key(merchant)); }
    public Optional<Category> lookup(String merchant) { return Optional.ofNullable(corrections.get(key(merchant))); }
    public int correctionCount() { return corrections.size(); }

    public void dismissMerchant(String merchant) { dismissedMerchants.add(key(merchant)); }
    public boolean isDismissed(String merchant) { return dismissedMerchants.contains(key(merchant)); }

    public void rejectMerge(String a, String b) { rejectedMerges.add(pair(a, b)); }
    public boolean isMergeRejected(String a, String b) { return rejectedMerges.contains(pair(a, b)); }
    private static String pair(String a, String b) { String x = key(a), y = key(b); return x.compareTo(y) < 0 ? x + "|" + y : y + "|" + x; }

    /** Stores one question/answer exchange (bounded). */
    public void store(String question, String answer) {
        turns.addLast(new String[]{question, answer});
        while (turns.size() > MAX_TURNS) turns.removeFirst();
    }

    /** Recent conversation as prompt context. */
    public String retrieve() {
        if (turns.isEmpty()) return "(no previous questions)";
        StringBuilder b = new StringBuilder();
        for (String[] t : turns) b.append("User: ").append(t[0]).append("\nAssistant: ").append(t[1]).append("\n");
        return b.toString();
    }

    public int turnCount() { return turns.size(); }
}
