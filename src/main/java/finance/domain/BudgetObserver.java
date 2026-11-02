package finance.domain;

/** Observer role (Observer pattern): notified whenever a Budget's limits or tracked spending change. */
public interface BudgetObserver {
    void update(Budget budget);
}
