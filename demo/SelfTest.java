package demo;

import aegis.AegisEngine;
import baseline.BacktrackingEngine;
import common.RegexSyntaxException;

import java.util.Random;
import java.util.regex.Pattern;

/**
 * Correctness check. Both prototype engines must agree with java.util.regex (the reference) on:
 *   1. hand-written cases with known answers,
 *   2. patterns that must be rejected with a syntax error,
 *   3. thousands of randomly generated pattern/input pairs, for both matches() and find().
 *
 *   java -cp out demo.SelfTest        (exit code 0 = everything agreed)
 */
public final class SelfTest {
    private static int checks = 0;
    private static int failures = 0;
    private static int skipped = 0;

    private SelfTest() {}

    public static void main(String[] args) throws InterruptedException {
        Thread t = new Thread(null, SelfTest::runAll, "selftest", 1L << 28);
        t.start();
        t.join();
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void runAll() {
        handWritten();
        syntaxErrors();
        random(30_000, 42L);
        System.out.println();
        System.out.println("checks run     : " + checks);
        System.out.println("skipped (baseline hit its step limit): " + skipped);
        System.out.println("disagreements  : " + failures);
        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL");
    }

    // ---- 1. hand-written ---------------------------------------------------------------

    private static void expect(String pattern, String input, boolean matchesExpected, boolean findExpected) {
        Pattern jdk = Pattern.compile(pattern);
        boolean jm = jdk.matcher(input).matches();
        boolean jf = jdk.matcher(input).find();
        if (jm != matchesExpected || jf != findExpected) {
            fail("my expected answer disagrees with the JDK", pattern, input, "expected m=" + matchesExpected + " f="
                + findExpected + ", jdk m=" + jm + " f=" + jf);
        }
        compare(pattern, jdk, input);
    }

    private static void handWritten() {
        expect("abc", "abc", true, true);
        expect("abc", "xabcx", false, true);
        expect("a|b", "b", true, true);
        expect("(a|b)*abb", "babb", true, true);
        expect("(a|b)*abb", "babba", false, true);
        expect("a*", "", true, true);
        expect("a+", "", false, false);
        expect("a?", "", true, true);
        expect("a{2,3}", "aa", true, true);
        expect("a{2,3}", "aaaa", false, true);
        expect("a{2,}", "aaaa", true, true);
        expect("a{0}", "", true, true);
        expect("a{3}", "aa", false, false);
        expect("[a-c]+", "abcabc", true, true);
        expect("[^a-c]+", "xyz", true, true);
        expect("[^a-c]+", "abc", false, false);
        expect("[a-cx-z]+", "axbz", true, true);
        expect("[\\d_]+", "12_3", true, true);
        expect("\\d+", "12345", true, true);
        expect("\\d+", "12a45", false, true);
        expect("\\D+", "abc", true, true);
        expect("\\w+@\\w+\\.com", "bob@mail.com", true, true);
        expect("\\w+@\\w+\\.com", "bob@mail.org", false, false);
        expect(".*", "hello", true, true);
        expect(".", "\n", false, false);
        expect("a.c", "abc", true, true);
        expect("a.c", "a\nc", false, false);
        expect("(|a)b", "b", true, true);
        expect("(|a)b", "ab", true, true);
        expect("()", "", true, true);
        expect("a|", "", true, true);
        expect("\\.", "a.b", false, true);
        expect("\\s+", "a b", false, true);
        expect("\\(a\\)", "(a)", true, true);
        expect("(a+)+", "aaaaaaaaaaaab", false, true);
        expect("(a|aa)+", "aaaaaaaaaaaab", false, true);
        expect("(a*)*", "aaaaaab", false, true);
        expect("((ab)*c)+", "abcc", true, true);
        System.out.println("hand-written cases done");
    }

    // ---- 2. syntax errors ---------------------------------------------------------------

    private static void syntaxErrors() {
        String[] bad = {
            "(", "a)", ")", "*a", "+", "a**", "a+?", "a{2", "a{", "a{3,2}", "{2}", "[a", "[]", "[z-a]",
            "\\", "\\q", "\\1", "a|*", "a{1001}"
        };
        for (String p : bad) {
            checks++;
            boolean aegisRejects = rejects(() -> AegisEngine.compile(p));
            boolean baseRejects = rejects(() -> BacktrackingEngine.compile(p));
            if (!aegisRejects || !baseRejects) {
                fail("pattern should have been rejected", p, "", "aegis rejects=" + aegisRejects + ", baseline rejects=" + baseRejects);
            }
        }
        System.out.println("syntax-error cases done (" + bad.length + " patterns rejected by both engines)");
    }

    private static boolean rejects(Runnable r) {
        try {
            r.run();
            return false;
        } catch (RegexSyntaxException e) {
            return true;
        }
    }

    // ---- 3. random differential test -----------------------------------------------------

    private static void random(int patternCount, long seed) {
        Random rnd = new Random(seed);
        String alphabet = "abc\n";
        for (int i = 0; i < patternCount; i++) {
            String pattern = gen(rnd, 3);
            Pattern jdk = Pattern.compile(pattern);
            for (int k = 0; k < 10; k++) {
                int len = rnd.nextInt(8);
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < len; j++) {
                    sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
                }
                compare(pattern, jdk, sb.toString());
            }
        }
        System.out.println("random differential test done (" + patternCount + " patterns x 10 inputs, seed " + seed + ")");
    }

    /** Builds a random pattern that every engine here accepts (quantifiers are always applied to a parenthesised part). */
    private static String gen(Random r, int depth) {
        int k = r.nextInt(depth <= 0 ? 4 : 10);
        switch (k) {
            case 0:
            case 1:
                return String.valueOf((char) ('a' + r.nextInt(3)));
            case 2:
                return ".";
            case 3:
                return new String[] {"[ab]", "[^a]", "[a-c]", "[b-c]"}[r.nextInt(4)];
            case 4:
            case 5:
                return gen(r, depth - 1) + gen(r, depth - 1);
            case 6:
                return "(" + gen(r, depth - 1) + "|" + gen(r, depth - 1) + ")";
            case 7:
                return "(" + gen(r, depth - 1) + ")*";
            case 8:
                return "(" + gen(r, depth - 1) + ")+";
            default:
                return "(" + gen(r, depth - 1) + ")" + (r.nextBoolean() ? "?" : "{1,2}");
        }
    }

    // ---- comparison ----------------------------------------------------------------------

    private static void compare(String pattern, Pattern jdk, String input) {
        checks++;
        boolean jm = jdk.matcher(input).matches();
        boolean jf = jdk.matcher(input).find();

        AegisEngine aegis = AegisEngine.compile(pattern);
        boolean am = aegis.matches(input).matched();
        boolean af = aegis.find(input).matched();

        BacktrackingEngine base = BacktrackingEngine.compile(pattern);
        BacktrackingEngine.Result bm = base.matches(input);
        BacktrackingEngine.Result bf = base.find(input);
        if (bm.aborted() || bf.aborted()) {
            skipped++;
            bm = null;
        }

        if (am != jm || af != jf) {
            fail("AEGIS disagrees with the JDK", pattern, input,
                "jdk m=" + jm + " f=" + jf + ", aegis m=" + am + " f=" + af);
        }
        if (bm != null && (bm.matched() != jm || bf.matched() != jf)) {
            fail("baseline disagrees with the JDK", pattern, input,
                "jdk m=" + jm + " f=" + jf + ", baseline m=" + bm.matched() + " f=" + bf.matched());
        }
    }

    private static void fail(String what, String pattern, String input, String detail) {
        failures++;
        if (failures <= 10) {
            System.out.println("FAIL: " + what);
            System.out.println("      pattern=" + show(pattern) + " input=" + show(input));
            System.out.println("      " + detail);
        }
    }

    private static String show(String s) {
        return "\"" + s.replace("\n", "\\n") + "\"";
    }
}
