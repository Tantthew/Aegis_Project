package aegis;

import common.CharSet;

import java.util.List;

/**
 * A nondeterministic finite automaton built with Thompson's construction.
 *
 * Only three kinds of state exist:
 *   CHAR  : consumes one input character if it belongs to 'set', then moves to out1
 *   SPLIT : consumes nothing, moves to both out1 and out2 (an epsilon branch)
 *   MATCH : accepting state
 */
public final class Nfa {
    public static final int CHAR = 0;
    public static final int SPLIT = 1;
    public static final int MATCH = 2;

    static final class State {
        final int id;
        final int kind;
        CharSet set;
        State out1;
        State out2;

        State(int id, int kind) {
            this.id = id;
            this.kind = kind;
        }
    }

    private final State[] states;
    private final State start;

    Nfa(List<State> list, State start) {
        this.states = list.toArray(new State[0]);
        this.start = start;
    }

    State[] states() {
        return states;
    }

    State start() {
        return start;
    }

    public int size() {
        return states.length;
    }

    /** Human readable listing of every state, for the presentation. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (State s : states) {
            sb.append("  q").append(s.id).append(s == start ? " (start)" : "").append(": ");
            switch (s.kind) {
                case CHAR -> sb.append("read ").append(s.set).append(" -> q").append(s.out1.id);
                case SPLIT -> sb.append("split -> q").append(s.out1.id).append(" | q").append(s.out2.id);
                default -> sb.append("ACCEPT");
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
