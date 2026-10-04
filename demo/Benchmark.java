package demo;

import aegis.AegisEngine;
import baseline.BacktrackingEngine;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Side-by-side comparison on the same pattern and input:
 *   AEGIS (NFA simulation)  vs  baseline backtracker  vs  java.util.regex (the real JDK engine).
 *
 *   java -cp out demo.Benchmark
 *
 * Notes for reading the table:
 *  - "steps" are counted differently (AEGIS: NFA state operations, baseline: matcher calls), so compare how each
 *    column GROWS with n, not the absolute numbers between columns.
 *  - Once an engine gives up on a case, it is skipped for larger n so the run stays short.
 *  - java.util.regex is stopped after a time limit by a CharSequence that throws when the deadline passes.
 *  - Timings are rough: a simple single-JVM measurement, not a JMH benchmark.
 */
public final class Benchmark {
    private static final long JAVA_TIMEOUT_MS = 2000;

    private record Case(String title, String pattern, int[] sizes, IntFunction<String> input) {}

    private record Timed<T>(T value, long nanos) {}

    private static final class Timeout extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Timeout() {
            super("timeout", null, false, false);
        }
    }

    /** Wraps a string and throws once a deadline passes, so a runaway regex can be stopped. */
    private static final class DeadlineSeq implements CharSequence {
        private final String s;
        private final long deadline;
        private int calls;

        DeadlineSeq(String s, long deadline) {
            this.s = s;
            this.deadline = deadline;
        }

        @Override public int length() { return s.length(); }

        @Override public char charAt(int i) {
            if ((++calls & 0xFFF) == 0 && System.nanoTime() > deadline) {
                throw new Timeout();
            }
            return s.charAt(i);
        }

        @Override public CharSequence subSequence(int a, int b) { return s.subSequence(a, b); }

        @Override public String toString() { return s; }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread t = new Thread(null, Benchmark::runAll, "bench", 1L << 29); // big stack: recursion is not the point here
        t.start();
        t.join();
    }

    private static void runAll() {
        int[] evilSizes = {10, 15, 20, 25, 30, 1000, 100000};
        // every hostile input is n letters 'a' followed by '!', which can never complete a match
        IntFunction<String> aThenBang = n -> "a".repeat(n) + "!";
        List<Case> cases = List.of(
            new Case("Nested quantifier (the JDK avoids the exponential case but is still slow on long input)", "(a+)+", evilSizes, aThenBang),
            new Case("Overlapping alternation (the JDK handles this well)", "(a|aa)+", evilSizes, aThenBang),
            new Case("Triple nesting (the JDK time grows exponentially here)", "((a+)+)+b", evilSizes, aThenBang),
            new Case("Repeated greedy wildcard (the JDK slows down sharply here)", "(.*a){12}", evilSizes, aThenBang),
            new Case("Ordinary, safe pattern (email-like)", "[a-z]+@[a-z]+\\.com",
                new int[] {1000, 10000, 100000}, n -> "a".repeat(n) + "@" + "b".repeat(n) + ".com"));

        System.out.println("Comparison: AEGIS (NFA simulation) vs baseline backtracker vs java.util.regex");
        System.out.println("JDK " + System.getProperty("java.version") + ", baseline step limit "
            + BacktrackingEngine.DEFAULT_STEP_LIMIT + ", java.util.regex time limit " + JAVA_TIMEOUT_MS + " ms\n");

        for (Case c : cases) {
            runCase(c);
        }
        System.out.println("Reading the results:");
        System.out.println("  - The baseline backtracker blows up on every hostile pattern: it is a plain backtracking engine.");
        System.out.println("  - java.util.regex is also a backtracking engine, but the JDK adds optimisations (memoisation of some");
        System.out.println("    loops), so it survives the simplest shapes and still fails on others. It is not safe in general.");
        System.out.println("  - AEGIS does the same amount of work per input character for every pattern above.");
        System.out.println("  - On an ordinary pattern all three are fast (milliseconds even at 200,000 characters). Which one is");
        System.out.println("    ahead changes from row to row, so no speed claim is made for that case; timings here are rough.");
    }

    private static void runCase(Case c) {
        System.out.println("=== " + c.title() + ":  " + c.pattern() + " ===");
        System.out.printf("%-8s | %-26s | %-34s | %s%n", "length", "AEGIS (steps, time)", "Baseline (steps, time)", "java.util.regex");
        System.out.println("-".repeat(96));

        AegisEngine aegis = AegisEngine.compile(c.pattern());
        BacktrackingEngine base = BacktrackingEngine.compile(c.pattern());
        Pattern jdk = Pattern.compile(c.pattern());

        // warm-up so JIT compilation does not distort the first row
        aegis.matches("aab");
        base.matches("aab");
        jdk.matcher("aab").matches();

        boolean baseDead = false;
        boolean jdkDead = false;
        for (int n : c.sizes()) {
            String input = c.input().apply(n);

            Timed<AegisEngine.Result> ar = measure(() -> aegis.matches(input));
            String aCell = String.format("%,d steps, %s", ar.value().steps(), ms(ar.nanos()));

            String bCell;
            if (baseDead) {
                bCell = "skipped";
            } else {
                Timed<BacktrackingEngine.Result> br = measure(() -> base.matches(input));
                if (br.value().aborted()) {
                    baseDead = true;
                    bCell = "gave up (" + br.value().reason().replace(" exceeded", "") + ")";
                } else {
                    bCell = String.format("%,d steps, %s", br.value().steps(), ms(br.nanos()));
                }
            }

            String jCell;
            if (jdkDead) {
                jCell = "skipped";
            } else {
                try {
                    Timed<Boolean> jr = measure(() ->
                        jdk.matcher(new DeadlineSeq(input, System.nanoTime() + JAVA_TIMEOUT_MS * 1_000_000L)).matches());
                    jCell = ms(jr.nanos());
                } catch (Timeout e) {
                    jdkDead = true;
                    jCell = "gave up (> " + JAVA_TIMEOUT_MS + " ms)";
                } catch (StackOverflowError e) {
                    jdkDead = true;
                    jCell = "stack overflow";
                }
            }
            System.out.printf("%-8d | %-26s | %-34s | %s%n", input.length(), aCell, bCell, jCell);
        }
        System.out.println();
    }

    /** Runs once; if that was quick, repeats a few times and keeps the best time. */
    private static <T> Timed<T> measure(Supplier<T> s) {
        long t0 = System.nanoTime();
        T value = s.get();
        long best = System.nanoTime() - t0;
        if (best < 20_000_000L) {
            for (int i = 0; i < 4; i++) {
                t0 = System.nanoTime();
                s.get();
                best = Math.min(best, System.nanoTime() - t0);
            }
        }
        return new Timed<>(value, best);
    }

    private static String ms(long nanos) {
        return String.format("%.2f ms", nanos / 1_000_000.0);
    }
}
