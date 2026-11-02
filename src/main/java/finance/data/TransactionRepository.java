package finance.data;

import finance.domain.*;
import java.time.YearMonth;
import java.util.*;

/** Storage abstraction for transactions, budgets and goals. */
public interface TransactionRepository {
    void save(Transaction t);
    Optional<Transaction> findById(String id);
    boolean existsDuplicate(Transaction t);
    List<Transaction> findAll();
    List<Transaction> findByMonth(YearMonth month);
    List<Transaction> findUncategorized();
    Set<YearMonth> months();
    Optional<Budget> findBudget(YearMonth month);
    void saveBudget(Budget b);
    void deleteBudget(YearMonth month);
    List<SavingsGoal> goals();
    void saveGoal(SavingsGoal g);
    String nextId();
}
