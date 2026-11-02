package finance.agent;

import java.util.*;

/** A mutable plan: steps run in order, and a step's observation may abort the plan or insert new steps. */
public class Plan {
    public interface Action { Outcome run(PlanContext pc); }

    public static class Step {
        public final String name; public final Action action;
        public Step(String name, Action a) { this.name = name; this.action = a; }
    }

    public static class Outcome {
        public final boolean stop; public final Response response; public final List<Step> insert;
        private Outcome(boolean s, Response r, List<Step> i) { stop = s; response = r; insert = i; }
        public static Outcome next() { return new Outcome(false, null, List.of()); }
        public static Outcome stop(Response r) { return new Outcome(true, r, List.of()); }
        public static Outcome insert(Step... s) { return new Outcome(false, null, List.of(s)); }
    }

    private final Deque<Step> steps = new ArrayDeque<>();
    public Plan add(String name, Action a) { steps.addLast(new Step(name, a)); return this; }
    boolean isEmpty() { return steps.isEmpty(); }
    Step pop() { return steps.pollFirst(); }
    void pushFront(List<Step> s) { for (int i = s.size() - 1; i >= 0; i--) steps.addFirst(s.get(i)); }
}
