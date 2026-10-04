package demo;

import baseline.BacktrackingEngine;
import common.RegexSyntaxException;

import java.util.Arrays;

/**
 * Shows the conventional backtracking engine on the same patterns as AegisDemo.
 *
 *   java -cp out demo.BaselineDemo                       built-in walkthrough
 *   java -cp out demo.BaselineDemo "pattern" in1 in2 ... your own pattern and inputs
 */
public final class BaselineDemo {
    private BaselineDemo() {}

    public static void main(String[] args) throws InterruptedException {
        // deep recursion is part of this design, so run on a thread with a large stack
        Thread t = new Thread(null, () -> run(args), "baseline-demo", 1L << 29);
        t.start();
        t.join();
    }

    private static void run(String[] args) {
        if (args.length >= 2) {
            show(args[0], Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        System.out.println("##### Baseline prototype: same front end, backtracking matcher #####\n");
        show("(a|b)*abb", "aabb", "abab");
        show("[a-z]+@\\d{2,3}", "tantan@123", "tantan@1");

        System.out.println("=== The same pattern, growing hostile input ===");
        System.out.println("pattern: (a+)+   input: n letters 'a' followed by 'b'   (step limit "
            + BacktrackingEngine.DEFAULT_STEP_LIMIT + ")");
        BacktrackingEngine engine = BacktrackingEngine.compile("(a+)+");
        for (int n : new int[] {10, 14, 18, 20, 22, 24, 40}) {
            BacktrackingEngine.Result r = engine.matches("a".repeat(n) + "b");
            System.out.printf("  n=%-3d  %s%n", n, describe(r));
        }
        System.out.println("Each extra letter roughly doubles the work, until the step limit stops it.");
        System.out.println("Without the limit this would hang the program, which is how ReDoS takes a server down.\n");
    }

    private static void show(String pattern, String... inputs) {
        System.out.println("Pattern: " + pattern);
        try {
            BacktrackingEngine engine = BacktrackingEngine.compile(pattern);
            for (String input : inputs) {
                System.out.println("  \"" + input + "\" -> " + describe(engine.matches(input)));
            }
        } catch (RegexSyntaxException ex) {
            System.out.println("  Syntax error: " + ex.getMessage());
        }
        System.out.println();
    }

    static String describe(BacktrackingEngine.Result r) {
        if (r.aborted()) {
            return "ABORTED after " + r.steps() + " steps (" + r.reason() + ")";
        }
        return (r.matched() ? "MATCH" : "NO MATCH") + "  (steps=" + r.steps() + ")";
    }
}
