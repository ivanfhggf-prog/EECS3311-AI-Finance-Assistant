package finance.command;

import java.util.*;

/** Log of executed commands, enabling undo (the benefit of the Command pattern). */
public class CommandHistory {
    private final Deque<Command> done = new ArrayDeque<>();
    private final List<String> log = new ArrayList<>();

    public void record(Command c, boolean ok) { log.add((ok ? "OK   " : "FAIL ") + c.describe()); if (ok) done.push(c); }
    public List<String> log() { return Collections.unmodifiableList(log); }

    public String undoLast() {
        while (!done.isEmpty()) {
            Command c = done.pop();
            if (c.undo()) { log.add("UNDO " + c.describe()); return "Undid: " + c.describe(); }
        }
        return "Nothing to undo.";
    }
}
