package finance.data;

import finance.domain.*;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

public class InMemoryTransactionRepository implements TransactionRepository {
    private final Map<String, Transaction> byId = new LinkedHashMap<>();
    private final Set<String> keys = new HashSet<>();
    private final Map<YearMonth, Budget> budgets = new TreeMap<>();
    private final List<SavingsGoal> goals = new ArrayList<>();
    private int seq = 1;

    @Override public void save(Transaction t) { byId.put(t.getId(), t); keys.add(t.dedupeKey()); }
    @Override public Optional<Transaction> findById(String id) { return Optional.ofNullable(byId.get(id)); }
    @Override public boolean existsDuplicate(Transaction t) { return keys.contains(t.dedupeKey()); }
    @Override public List<Transaction> findAll() {
        return byId.values().stream().sorted(Comparator.comparing(Transaction::getDate).thenComparing(Transaction::getId)).collect(Collectors.toList());
    }
    @Override public List<Transaction> findByMonth(YearMonth m) {
        return findAll().stream().filter(t -> t.getMonth().equals(m)).collect(Collectors.toList());
    }
    @Override public List<Transaction> findUncategorized() {
        return findAll().stream().filter(t -> t.getCategory() == Category.UNCATEGORIZED).collect(Collectors.toList());
    }
    @Override public Set<YearMonth> months() {
        return findAll().stream().map(Transaction::getMonth).collect(Collectors.toCollection(TreeSet::new));
    }
    @Override public Optional<Budget> findBudget(YearMonth m) { return Optional.ofNullable(budgets.get(m)); }
    @Override public void saveBudget(Budget b) { budgets.put(b.getMonth(), b); }
    @Override public void deleteBudget(YearMonth m) { budgets.remove(m); }
    @Override public List<SavingsGoal> goals() { return goals; }
    @Override public void saveGoal(SavingsGoal g) {
        goals.removeIf(x -> x.getName().equalsIgnoreCase(g.getName()));
        goals.add(g);
    }
    @Override public String nextId() { return String.format("T%04d", seq++); }
}
