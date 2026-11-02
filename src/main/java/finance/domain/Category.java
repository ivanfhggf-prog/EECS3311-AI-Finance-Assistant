package finance.domain;

/** Spending categories. UNCATEGORIZED marks transactions needing manual review (F02/F03). */
public enum Category {
    GROCERIES, DINING, RENT, UTILITIES, TRANSPORT, ENTERTAINMENT, SHOPPING, HEALTH, SUBSCRIPTIONS, INCOME, SAVINGS, OTHER, UNCATEGORIZED;

    /** Parses free text (case-insensitive); returns null if unrecognised. */
    public static Category parse(String s) {
        if (s == null) return null;
        String t = s.trim().toUpperCase().replace(' ', '_');
        for (Category c : values()) if (c.name().equals(t)) return c;
        return null;
    }

    public boolean isSpending() { return this != INCOME && this != SAVINGS; }

    public static String listForPrompt() {
        StringBuilder b = new StringBuilder();
        for (Category c : values()) if (c != UNCATEGORIZED) b.append(b.length() > 0 ? ", " : "").append(c.name());
        return b.toString();
    }
}
