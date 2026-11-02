package finance.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public class SavingsGoal {
    private final String name;
    private BigDecimal target;
    private LocalDate targetDate;
    private BigDecimal contributed = BigDecimal.ZERO;
    private final LocalDate created;

    public SavingsGoal(String name, BigDecimal target, LocalDate targetDate, LocalDate created) {
        this.name = name; this.target = target; this.targetDate = targetDate; this.created = created;
    }

    public String getName() { return name; }
    public BigDecimal getTarget() { return target; }
    public LocalDate getTargetDate() { return targetDate; }
    public LocalDate getCreated() { return created; }
    public BigDecimal getContributed() { return contributed; }

    public void setTarget(BigDecimal amount, LocalDate date) { this.target = amount; this.targetDate = date; }
    public void contribute(BigDecimal amount) { contributed = contributed.add(amount); }

    /** Progress 0..100 given the extra amount saved through SAVINGS transactions. */
    public double progressPercent(BigDecimal fromTransactions) {
        BigDecimal total = contributed.add(fromTransactions);
        return Math.min(100.0, total.multiply(BigDecimal.valueOf(100)).divide(target, 2, RoundingMode.HALF_UP).doubleValue());
    }
}
