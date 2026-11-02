package finance.command;

import finance.data.TransactionRepository;
import finance.domain.Transaction;
import finance.util.Csv;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/** F01: parses a CSV (date,amount,merchant,description), skips duplicates, reports bad rows with line numbers. */
public class ImportTransactionsCommand implements Command {
    private final Path file;
    private final TransactionRepository repo;
    private final List<Transaction> imported = new ArrayList<>();

    public ImportTransactionsCommand(Path file, TransactionRepository repo) { this.file = file; this.repo = repo; }

    @Override public CommandResult execute() {
        List<String> lines;
        try { lines = Files.readAllLines(file); }
        catch (IOException | RuntimeException e) { return CommandResult.fail("Cannot read file '" + file + "': " + e.getMessage()); }
        if (lines.isEmpty()) return CommandResult.fail("File is empty.");
        List<String> problems = new ArrayList<>();
        int dup = 0;
        int start = 0;
        List<String> header = Csv.parseLine(lines.get(0).toLowerCase());
        int iDate = header.indexOf("date"), iAmt = header.indexOf("amount"), iMer = header.indexOf("merchant"), iDes = header.indexOf("description");
        if (iDate >= 0 && iAmt >= 0 && iMer >= 0) start = 1; else { iDate = 0; iAmt = 1; iMer = 2; iDes = 3; }
        for (int n = start; n < lines.size(); n++) {
            String line = lines.get(n);
            if (line.isBlank()) continue;
            try {
                List<String> f = Csv.parseLine(line);
                String d = f.get(iDate), a = f.get(iAmt).replace("$", "").replace(" ", ""), m = f.get(iMer);
                String desc = iDes >= 0 && iDes < f.size() ? f.get(iDes) : "";
                if (d.isEmpty() || a.isEmpty() || m.isEmpty()) throw new IllegalArgumentException("missing required field");
                Transaction t = new Transaction(repo.nextId(), LocalDate.parse(d), new BigDecimal(a), m, desc);
                if (repo.existsDuplicate(t)) { dup++; continue; }
                repo.save(t);
                imported.add(t);
            } catch (RuntimeException e) {
                problems.add("line " + (n + 1) + ": " + (e instanceof IndexOutOfBoundsException ? "too few columns" : e.getMessage()));
            }
        }
        String msg = String.format("Imported %d transaction(s); %d duplicate(s) skipped; %d bad row(s).", imported.size(), dup, problems.size());
        return new CommandResult(true, msg, problems);
    }

    public List<Transaction> getImported() { return imported; }
    @Override public String describe() { return "Import " + file.getFileName(); }
}
