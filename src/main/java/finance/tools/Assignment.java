package finance.tools;

import finance.domain.Category;

public class Assignment {
    public final String txId; public final Category category; public final boolean needsReview; public final String source;
    public Assignment(String txId, Category category, boolean needsReview, String source) { this.txId = txId; this.category = category; this.needsReview = needsReview; this.source = source; }
}
