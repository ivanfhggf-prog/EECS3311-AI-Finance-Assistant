package finance.domain;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;

/** Monthly budget. Subject of the Observer pattern. */
public class Budget {
    private final YearMonth month;
    private final Map<Category, BigDecimal> limits = new EnumMap<>(Category.class);
    private final Map<Category, BigDecimal> spent = new EnumMap<>(Category.class);
    private final List<BudgetObserver> observers = new ArrayList<>();

    public Budget(YearMonth month) { this.month = month; }

    public YearMonth getMonth() { return month; }

    public void addObserver(BudgetObserver o) { if (!observers.contains(o)) observers.add(o); }
    public void removeObserver(BudgetObserver o) { observers.remove(o); }

    public void notifyObservers() { for (BudgetObserver o : new ArrayList<>(observers)) o.update(this); }

    public void setLimits(Map<Category, BigDecimal> newLimits) {
        limits.clear();
        limits.putAll(newLimits);
        notifyObservers();
    }

    /** Replaces the spent-per-category figures (computed by the controller) and notifies observers. */
    public void updateSpending(Map<Category, BigDecimal> totals) {
        spent.clear();
        spent.putAll(totals);
        notifyObservers();
    }

    public Map<Category, BigDecimal> getLimits() { return Collections.unmodifiableMap(limits); }
    public BigDecimal getSpent(Category c) { return spent.getOrDefault(c, BigDecimal.ZERO); }
    public BigDecimal getRemaining(Category c) { return limits.getOrDefault(c, BigDecimal.ZERO).subtract(getSpent(c)); }
    public boolean isOver(Category c) { return limits.containsKey(c) && getSpent(c).compareTo(limits.get(c)) > 0; }
    public BigDecimal totalLimit() { return limits.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add); }
}
