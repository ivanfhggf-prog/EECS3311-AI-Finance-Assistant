package finance.tools;

import finance.domain.*;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/** Deterministic statistics shared by the analysis tools (and unit-testable on their own). */
public final class SpendingStats {
    private SpendingStats() {}

    public static Map<YearMonth, Map<Category, BigDecimal>> monthlyByCategory(List<Transaction> txs) {
        Map<YearMonth, Map<Category, BigDecimal>> m = new TreeMap<>();
        for (Transaction t : txs) {
            if (!t.isExpense() || !t.getCategory().isSpending()) continue;
            m.computeIfAbsent(t.getMonth(), k -> new EnumMap<>(Category.class)).merge(t.getCategory(), t.spent(), BigDecimal::add);
        }
        return m;
    }

    /** Average spent amount of other expenses in the same category; null if fewer than 3 comparables. */
    public static Double categoryAverageExcluding(List<Transaction> all, Transaction t) {
        List<Transaction> others = all.stream().filter(o -> o.isExpense() && o != t && o.getCategory() == t.getCategory()).collect(Collectors.toList());
        if (others.size() < 3) return null;
        return others.stream().mapToDouble(o -> o.spent().doubleValue()).average().orElse(0);
    }

    /**
     * Median expense amount across non-recurring merchants; used as a fallback baseline when a category has too
     * few comparable expenses (e.g. the first electronics purchase). Regular bills such as rent are excluded.
     */
    public static Double fallbackMedian(List<Transaction> all) {
        Map<String, List<Transaction>> byMerchant = new HashMap<>();
        for (Transaction t : all) if (t.isExpense()) byMerchant.computeIfAbsent(normalizeMerchant(t.getMerchant()), k -> new ArrayList<>()).add(t);
        List<Double> amounts = new ArrayList<>();
        for (List<Transaction> g : byMerchant.values()) if (frequency(g) == null) for (Transaction t : g) amounts.add(t.spent().doubleValue());
        if (amounts.size() < 5) return null;
        Collections.sort(amounts);
        return amounts.get(amounts.size() / 2);
    }

    public static String normalizeMerchant(String m) {
        String s = m.toLowerCase().replaceAll("\\.(com|ca|net|org)\\b", "").replaceAll("[^a-z]", "");
        return s;
    }

    /** Detected recurrence interval label (weekly/biweekly/monthly/yearly) or null if irregular. */
    public static String frequency(List<Transaction> group) {
        if (group.size() < 3) return null;
        List<Transaction> s = group.stream().sorted(Comparator.comparing(Transaction::getDate)).collect(Collectors.toList());
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < s.size(); i++) gaps.add(ChronoUnit.DAYS.between(s.get(i - 1).getDate(), s.get(i).getDate()));
        Collections.sort(gaps);
        long med = gaps.get(gaps.size() / 2);
        String label; long center;
        if (Math.abs(med - 7) <= 1) { label = "weekly"; center = 7; }
        else if (Math.abs(med - 14) <= 2) { label = "biweekly"; center = 14; }
        else if (Math.abs(med - 30) <= 3) { label = "monthly"; center = 30; }
        else if (Math.abs(med - 365) <= 10) { label = "yearly"; center = 365; }
        else return null;
        long tol = Math.max(2, Math.round(center * 0.12));
        for (long g : gaps) if (Math.abs(g - center) > tol) return null;
        double[] a = s.stream().mapToDouble(t -> t.spent().doubleValue()).sorted().toArray();
        double medAmt = a[a.length / 2];
        for (double x : a) if (Math.abs(x - medAmt) > medAmt * 0.10) return null;
        return label;
    }
}
