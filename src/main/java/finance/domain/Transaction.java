package finance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/** One bank transaction. Negative amount = money out, positive = money in. */
public class Transaction {
    private final String id;
    private final LocalDate date;
    private final BigDecimal amount;
    private final String merchant;
    private final String description;
    private Category category = Category.UNCATEGORIZED;
    private boolean needsReview = true;
    private boolean dismissedFlag;

    public Transaction(String id, LocalDate date, BigDecimal amount, String merchant, String description) {
        this.id = id; this.date = date; this.amount = amount; this.merchant = merchant; this.description = description;
    }

    public String getId() { return id; }
    public LocalDate getDate() { return date; }
    public BigDecimal getAmount() { return amount; }
    public String getMerchant() { return merchant; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public boolean needsReview() { return needsReview; }
    public boolean isDismissedFlag() { return dismissedFlag; }
    public void dismissFlag() { dismissedFlag = true; }
    public YearMonth getMonth() { return YearMonth.from(date); }
    public boolean isExpense() { return amount.signum() < 0; }
    public BigDecimal spent() { return amount.abs(); }

    public void setCategory(Category c, boolean needsReview) { this.category = c; this.needsReview = needsReview; }

    /** Key used to detect duplicates on import. */
    public String dedupeKey() {
        return date + "|" + amount.toPlainString() + "|" + merchant.toLowerCase() + "|" + description.toLowerCase();
    }

    @Override public String toString() {
        return String.format("%s  %s  %10s  %-22s %-14s%s", id, date, amount.toPlainString(), merchant, category, needsReview ? " [review]" : "");
    }
}
