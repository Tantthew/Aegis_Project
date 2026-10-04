package baseline;

import common.Node;
import common.Parser;

import java.util.List;

/**
 * Baseline prototype: a conventional backtracking regex engine, the same design family as
 * java.util.regex, Python's re and Perl. It is the plain textbook version: unlike the JDK engine
 * it has none of the extra optimisations (such as memoising some loops), so it shows the raw
 * behaviour of the technique.
 *
 * It uses the SAME lexer, parser and AST as AEGIS. Only the matching strategy differs:
 * it walks the tree directly, commits to one choice at a time, and when a later part fails it
 * returns to the most recent choice and tries the next alternative.
 *
 * It is written in continuation-passing style. m(node, i, k) means: "match 'node' at position i,
 * and for every way that works, call k with the position where it ended; stop as soon as k says true."
 * That is exactly what lets it undo a choice, and also what makes it exponential on patterns such as (a+)+.
 *
 * Because that can run for a very long time, every run has a step limit. If it is exceeded, the result
 * is flagged 'aborted' instead of hanging the program.
 */
public final class BacktrackingEngine {
    public static final long DEFAULT_STEP_LIMIT = 20_000_000L;

    /**
     * @param matched  the answer (false if aborted)
     * @param steps    how many times the matcher was entered
     * @param aborted  true if the step limit or the stack was exhausted before an answer was found
     * @param reason   why it was aborted, or null
     */
    public record Result(boolean matched, long steps, boolean aborted, String reason) {}

    private interface Cont {
        boolean run(int pos);
    }

    private static final class Abort extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Abort(String reason) {
            super(reason, null, false, false); // no stack trace, cheap to throw
        }
    }

    private final String pattern;
    private final Node ast;
    private final long stepLimit;

    private BacktrackingEngine(String pattern, Node ast, long stepLimit) {
        this.pattern = pattern;
        this.ast = ast;
        this.stepLimit = stepLimit;
    }

    public static BacktrackingEngine compile(String pattern) {
        return compile(pattern, DEFAULT_STEP_LIMIT);
    }

    /** Compiles a pattern. Throws common.RegexSyntaxException if it is malformed or unsupported. */
    public static BacktrackingEngine compile(String pattern, long stepLimit) {
        return new BacktrackingEngine(pattern, Parser.parse(pattern), stepLimit);
    }

    public String pattern() {
        return pattern;
    }

    public Node ast() {
        return ast;
    }

    /** Full match, like Matcher.matches(). */
    public Result matches(String input) {
        return run(input, false);
    }

    /** True if any substring matches, like Matcher.find(). */
    public Result find(String input) {
        return run(input, true);
    }

    private Result run(String input, boolean search) {
        Matcher m = new Matcher(input);
        try {
            boolean ok;
            if (search) {
                Cont anywhere = end -> true;
                ok = false;
                for (int start = 0; start <= input.length() && !ok; start++) {
                    ok = m.match(ast, start, anywhere);
                }
            } else {
                Cont mustReachEnd = end -> end == input.length();
                ok = m.match(ast, 0, mustReachEnd);
            }
            return new Result(ok, m.steps, false, null);
        } catch (Abort a) {
            return new Result(false, m.steps, true, a.getMessage());
        } catch (StackOverflowError so) {
            return new Result(false, m.steps, true, "stack overflow");
        }
    }

    private final class Matcher {
        private final CharSequence in;
        long steps = 0;

        Matcher(CharSequence in) {
            this.in = in;
        }

        private void tick() {
            if (++steps > stepLimit) {
                throw new Abort("step limit of " + stepLimit + " exceeded");
            }
        }

        boolean match(Node n, int i, Cont k) {
            tick();
            if (n instanceof Node.Empty) {
                return k.run(i);
            }
            if (n instanceof Node.Lit l) {
                return i < in.length() && l.set().matches(in.charAt(i)) && k.run(i + 1);
            }
            if (n instanceof Node.Cat c) {
                return cat(c.parts(), 0, i, k);
            }
            if (n instanceof Node.Alt a) {
                for (Node option : a.options()) {
                    if (match(option, i, k)) {
                        return true;
                    }
                    // option failed: fall through and try the next one (this is the backtracking)
                }
                return false;
            }
            if (n instanceof Node.Opt o) {
                return match(o.body(), i, k) || k.run(i);
            }
            if (n instanceof Node.Star s) {
                return star(s.body(), i, k);
            }
            if (n instanceof Node.Plus p) {
                return match(p.body(), i, j -> star(p.body(), j, k));
            }
            throw new IllegalStateException("Unknown node " + n);
        }

        private boolean cat(List<Node> parts, int idx, int i, Cont k) {
            if (idx == parts.size()) {
                return k.run(i);
            }
            return match(parts.get(idx), i, j -> cat(parts, idx + 1, j, k));
        }

        /** Greedy: try one more repetition first, and only if that fails, stop here. */
        private boolean star(Node body, int i, Cont k) {
            tick();
            // j != i stops an endless loop when the body can match the empty string
            return match(body, i, j -> j != i && star(body, j, k)) || k.run(i);
        }
    }
}
