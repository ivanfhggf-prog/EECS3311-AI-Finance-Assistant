package finance.command;

/** Command pattern: a state-changing user action that can be executed, logged and undone. */
public interface Command {
    CommandResult execute();
    /** Reverses the action. Returns false if the command is not undoable. */
    default boolean undo() { return false; }
    String describe();
}
