package aegis;

import common.Node;
import common.Parser;

import java.util.ArrayList;
import java.util.List;

/**
 * AEGIS prototype: lexer -> parser -> AST -> NFA -> simulation.
 *
 * Matching never backtracks. It keeps the SET of NFA states the automaton could currently be in and
 * advances the whole set one input character at a time. The set can never hold more than m states
 * (m = NFA size), so total work is at most O(m * n) for an input of length n, whatever the pattern.
 *
 * Semantics: matches() is a full match (like Matcher.matches()), find() asks whether any substring
 * matches (like Matcher.find()). Both return only true/false, no capture groups.
 */
public final class AegisEngine {

    /**
     * @param matched    the answer
     * @param steps      NFA state operations performed (closure visits plus character transitions)
     * @param peakActive the largest number of NFA states that were active at once
     */
    public record Result(boolean matched, long steps, int peakActive) {}

    private final String pattern;
    private final Node ast;
    private final Nfa nfa;

    private AegisEngine(String pattern, Node ast, Nfa nfa) {
        this.pattern = pattern;
        this.ast = ast;
        this.nfa = nfa;
    }

    /** Compiles a pattern. Throws common.RegexSyntaxException if it is malformed or unsupported. */
    public static AegisEngine compile(String pattern) {
        Node ast = Parser.parse(pattern);
        Nfa nfa = NfaBuilder.build(ast);
        return new AegisEngine(pattern, ast, nfa);
    }

    public String pattern() {
        return pattern;
    }

    public Node ast() {
        return ast;
    }

    public Nfa nfa() {
        return nfa;
    }

    public Result matches(String input) {
        return simulate(input, false, null);
    }

    public Result find(String input) {
        return simulate(input, true, null);
    }

    /** Runs a full match and returns, line by line, which NFA states were active after each character. */
    public List<String> trace(String input) {
        List<String> lines = new ArrayList<>();
        simulate(input, false, lines);
        return lines;
    }

    // ---------------------------------------------------------------------------------------

    /** Scratch space for one simulation run. */
    private static final class Sim {
        final Nfa.State[] states;
        final int[] mark;   // mark[id] == gen means "already in the current set"
        final int[] stack;
        int gen = 1;
        long steps = 0;

        Sim(Nfa nfa) {
            this.states = nfa.states();
            this.mark = new int[states.length];
            this.stack = new int[states.length];
        }

        /**
         * Adds 'from' and everything reachable from it through SPLIT states (the epsilon closure)
         * to 'list'. Only CHAR and MATCH states are stored, because only those matter for the next step.
         * Each state is visited at most once per generation, so epsilon loops such as (a*)* are harmless.
         */
        int add(Nfa.State from, int[] list, int count) {
            int sp = 0;
            if (mark[from.id] != gen) {
                mark[from.id] = gen;
                stack[sp++] = from.id;
            }
            while (sp > 0) {
                Nfa.State s = states[stack[--sp]];
                steps++;
                if (s.kind == Nfa.SPLIT) {
                    if (mark[s.out2.id] != gen) {
                        mark[s.out2.id] = gen;
                        stack[sp++] = s.out2.id;
                    }
                    if (mark[s.out1.id] != gen) {
                        mark[s.out1.id] = gen;
                        stack[sp++] = s.out1.id;
                    }
                } else {
                    list[count++] = s.id;
                }
            }
            return count;
        }

        boolean hasMatch(int[] list, int count) {
            for (int i = 0; i < count; i++) {
                if (states[list[i]].kind == Nfa.MATCH) {
                    return true;
                }
            }
            return false;
        }
    }

    private Result simulate(CharSequence in, boolean search, List<String> trace) {
        Sim sim = new Sim(nfa);
        int size = sim.states.length;
        int[] cur = new int[size];
        int[] nxt = new int[size];

        int curN = sim.add(nfa.start(), cur, 0);
        int peak = curN;
        if (trace != null) {
            trace.add(describe("start", cur, curN, sim));
        }
        if (search && sim.hasMatch(cur, curN)) {
            return new Result(true, sim.steps, peak);
        }

        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            sim.gen++;
            int nxtN = 0;
            for (int k = 0; k < curN; k++) {
                Nfa.State s = sim.states[cur[k]];
                if (s.kind == Nfa.CHAR && s.set.matches(c)) {
                    sim.steps++;
                    nxtN = sim.add(s.out1, nxt, nxtN);
                }
            }
            if (search) {
                nxtN = sim.add(nfa.start(), nxt, nxtN); // a new match attempt may begin at every position
            }
            int[] tmp = cur;
            cur = nxt;
            nxt = tmp;
            curN = nxtN;
            peak = Math.max(peak, curN);
            if (trace != null) {
                trace.add(describe("read " + quote(c), cur, curN, sim));
            }
            if (search && sim.hasMatch(cur, curN)) {
                return new Result(true, sim.steps, peak);
            }
            if (!search && curN == 0) {
                return new Result(false, sim.steps, peak); // no state alive, can never match
            }
        }
        return new Result(sim.hasMatch(cur, curN), sim.steps, peak);
    }

    private static String quote(char c) {
        if (c == '\n') return "'\\n'";
        if (c == '\t') return "'\\t'";
        if (c == '\r') return "'\\r'";
        return "'" + c + "'";
    }

    private static String describe(String label, int[] list, int count, Sim sim) {
        StringBuilder sb = new StringBuilder(label).append("  ->  active {");
        boolean accepting = false;
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('q').append(list[i]);
            if (sim.states[list[i]].kind == Nfa.MATCH) {
                accepting = true;
            }
        }
        sb.append('}');
        if (accepting) {
            sb.append("   <- contains the ACCEPT state");
        }
        if (count == 0) {
            sb.append("   <- no state alive, match is impossible");
        }
        return sb.toString();
    }
}
