package finance.command;

import java.util.*;

public class CommandResult {
    public final boolean ok;
    public final String message;
    public final List<String> details;

    public CommandResult(boolean ok, String message, List<String> details) { this.ok = ok; this.message = message; this.details = details; }
    public static CommandResult ok(String m) { return new CommandResult(true, m, List.of()); }
    public static CommandResult ok(String m, List<String> d) { return new CommandResult(true, m, d); }
    public static CommandResult fail(String m) { return new CommandResult(false, m, List.of()); }

    @Override public String toString() {
        StringBuilder b = new StringBuilder(message);
        for (String d : details) b.append("\n  - ").append(d);
        return b.toString();
    }
}
