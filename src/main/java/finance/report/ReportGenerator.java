package finance.report;

import finance.domain.*;

import finance.util.Csv;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/** F13 export (CSV / TXT / PDF) and formatting of generated summaries (F10). Deterministic. */
public class ReportGenerator {

    public String transactionsCsv(List<Transaction> txs) {
        StringBuilder b = new StringBuilder("id,date,amount,merchant,description,category\n");
        for (Transaction t : txs) b.append(t.getId()).append(',').append(t.getDate()).append(',').append(t.getAmount().toPlainString()).append(',')
                .append(Csv.escape(t.getMerchant())).append(',').append(Csv.escape(t.getDescription())).append(',').append(t.getCategory()).append('\n');
        return b.toString();
    }

    public String transactionsText(List<Transaction> txs, LocalDate from, LocalDate to) {
        StringBuilder b = new StringBuilder("TRANSACTION REPORT " + from + " to " + to + "\n\n");
        Map<Category, BigDecimal> totals = new EnumMap<>(Category.class);
        BigDecimal income = BigDecimal.ZERO, spend = BigDecimal.ZERO, saved = BigDecimal.ZERO;   // savings transfers are not spending
        for (Transaction t : txs) {
            b.append(t).append('\n');
            if (t.getAmount().signum() >= 0) income = income.add(t.getAmount());
            else if (t.getCategory() == Category.SAVINGS) saved = saved.add(t.spent());
            else { spend = spend.add(t.spent()); totals.merge(t.getCategory(), t.spent(), BigDecimal::add); }
        }
        b.append("\nIncome: $").append(income).append("\nSpending: $").append(spend).append("\nSaved (transfers to savings): $").append(saved).append("\n\nSpending by category:\n");
        totals.forEach((c, v) -> b.append(String.format("  %-14s $%s%n", c, v)));
        return b.toString();
    }

    /** Formats a monthly summary: deterministic figures first, then the AI narrative, labelled partial when needed. */
    public String formatSummary(java.time.YearMonth month, List<Transaction> txs, Budget budget, String narrative, boolean partial) {
        BigDecimal income = BigDecimal.ZERO, spend = BigDecimal.ZERO;
        Map<Category, BigDecimal> cat = new EnumMap<>(Category.class);
        BigDecimal saved = BigDecimal.ZERO;   // transfers to savings are not spending
        for (Transaction t : txs) {
            if (t.getAmount().signum() >= 0) income = income.add(t.getAmount());
            else if (t.getCategory() == Category.SAVINGS) saved = saved.add(t.spent());
            else { spend = spend.add(t.spent()); cat.merge(t.getCategory(), t.spent(), BigDecimal::add); }
        }
        StringBuilder b = new StringBuilder("MONTHLY SUMMARY " + month + (partial ? "  (PARTIAL - month not complete)" : "") + "\n");
        b.append("Income: $").append(income).append("   Spending: $").append(spend).append("   Saved: $").append(saved).append("   Net after spending: $").append(income.subtract(spend)).append('\n');
        cat.forEach((c, v) -> b.append(String.format("  %-14s $%s%s%n", c, v, budget != null && budget.getLimits().containsKey(c) ? " / limit $" + budget.getLimits().get(c) + (v.compareTo(budget.getLimits().get(c)) > 0 ? "  OVER" : "") : "")));
        b.append('\n').append(narrative).append('\n');
        return b.toString();
    }

    public Path export(String content, Path out, String format) throws IOException {
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        if (format.equalsIgnoreCase("pdf")) Files.write(out, SimplePdf.fromText(content));
        else Files.writeString(out, content, StandardCharsets.UTF_8);
        return out;
    }
}
