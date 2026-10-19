import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/*
 * ======================================================================================
 *  AEGIS SHOWCASE  (CS200 Principles of Programming Languages, Group 3)
 *  A linear-time, ReDoS-resistant regular expression engine, plus a normal
 *  "backtracking" engine to compare it against. Everything is in this ONE file.
 * ======================================================================================
 *
 *  HOW TO RUN (pick one)
 *    - IntelliJ / VS Code / Eclipse: open this file and press the Run button. That is all.
 *    - Command line, in the folder that contains this file:
 *          javac AegisShowcase.java
 *          java AegisShowcase
 *
 *  OPTIONAL ARGUMENTS (IntelliJ: Run > Edit Configurations > Program arguments)
 *    gui                      open the window (Home, Try It, Step-by-Step, Under Attack)
 *    (none)                   the full guided tour, parts 1 to 5, in order
 *    pause                    the full tour, but wait for Enter between parts (good for presenting)
 *    3                        only one part (1 to 5)
 *    "ab*c" abbc ac           try your own pattern: the first value is the pattern,
 *                             every value after it is an input to test
 *
 *  WHERE THINGS ARE IN THIS FILE (search for the heading)
 *    PRESENTATION      class AegisShowcase: the text and tables you see on screen
 *    CODE PART A       shared front end: Lexer, Parser, syntax tree (used by both engines)
 *    CODE PART B       AEGIS: NfaBuilder (Thompson construction) and AegisEngine (simulation)
 *    CODE PART C       BacktrackingEngine: the normal engine used for comparison
 *    CODE PART D       AegisGui: the optional window (Swing, nothing to install)
 *
 *  Requires Java 17 or newer.
 */
public class AegisShowcase {

    // ======================================================================================
    //  PRESENTATION
    // ======================================================================================

    static final int WIDTH = 76;
    static boolean pauseBetweenParts = false;
    /** Set to true if you want a plain Run (no arguments) to open the window instead of the text tour. */
    static final boolean GUI_BY_DEFAULT = true;

    static final BufferedReader STDIN = new BufferedReader(new InputStreamReader(System.in));

    public static void main(String[] args) throws InterruptedException {
        // The normal engine is recursive, so run everything on a thread with a large stack.
        Thread t = new Thread(null, () -> start(args), "aegis-showcase", 1L << 28);
        t.start();
        t.join();
    }

    static void start(String[] args) {
        List<String> a = new ArrayList<>(Arrays.asList(args));
        if (!a.isEmpty() && a.get(0).equals("pause")) {
            pauseBetweenParts = true;
            a.remove(0);
        }
        if ((a.size() == 1 && a.get(0).equalsIgnoreCase("gui")) || (a.isEmpty() && GUI_BY_DEFAULT)) {
            AegisGui.open();
        } else if (a.isEmpty()) {
            tour();
        } else if (a.size() == 1 && a.get(0).matches("[1-5]")) {
            part(Integer.parseInt(a.get(0)));
        } else if (a.size() >= 2) {
            tryOwnPattern(a.get(0), a.subList(1, a.size()));
        } else {
            out("Usage:");
            out("  gui                     open the window");
            out("  (no arguments)          full guided tour");
            out("  pause                   full tour, wait for Enter between parts");
            out("  3                       a single part (1 to 5)");
            out("  \"ab*c\" abbc ac          your own pattern, then one or more inputs");
        }
    }

    static void tour() {
        intro();
        pause();
        for (int i = 1; i <= 4; i++) {
            part(i);
            pause();
        }
        part(5);
    }

    static void part(int n) {
        switch (n) {
            case 1 -> part1HowItWorks();
            case 2 -> part2UnderAttack();
            case 3 -> part3InputFilter();
            case 4 -> part4Correctness();
            case 5 -> part5Summary();
            default -> out("No such part: " + n);
        }
    }

    // ---- intro ---------------------------------------------------------------------------

    static void intro() {
        banner("AEGIS: a regex engine that cannot be frozen by a bad pattern");
        out("  CS200 Principles of Programming Languages, Group 3 (prototype)");

        sub("What is a regular expression engine?");
        para("A regular expression (regex) is a short pattern, such as [a-z]+@[a-z]+\\.com, that "
            + "describes a set of strings. A regex engine is the program that checks whether a piece "
            + "of text fits a pattern. Websites use them to validate forms, search logs and block "
            + "malicious requests.");

        sub("The problem");
        para("Most engines (Java, Python, Perl) work by trial and error. They try one path through "
            + "the pattern, and if it fails they back up and try another. For some patterns the "
            + "number of attempts can double, or worse, with every extra character, so one short "
            + "input can freeze a server. This is called ReDoS (Regular expression Denial of "
            + "Service). In July 2019 a "
            + "single regex rule in Cloudflare's firewall caused a worldwide outage this way.");

        sub("What AEGIS does differently");
        para("AEGIS never backs up. It turns the pattern into a small state machine and follows "
            + "every possible path at the same time, one input character at a time. The work per "
            + "character has a fixed upper limit, so the total time grows in a straight line with the "
            + "length of the text, whatever the pattern is.");

        sub("What you will see");
        out("  Part 1  How AEGIS works, stage by stage");
        out("  Part 2  AEGIS against a normal engine under attack");
        out("  Part 3  A real use: a rule-based input filter");
        out("  Part 4  Proof that the answers are correct");
        out("  Part 5  Summary, limits and where the code lives");
    }

    // ---- part 1 --------------------------------------------------------------------------

    static void part1HowItWorks() {
        banner("PART 1: HOW AEGIS WORKS, STAGE BY STAGE");
        para("We follow one pattern from text to answer. The pattern is (a|b)*abb, which means: "
            + "any mix of the letters a and b, followed by the letters abb.");

        String pattern = "(a|b)*abb";
        AegisEngine engine = AegisEngine.compile(pattern);

        showStages(engine);

        sub("Stage 4: SIMULATION, input \"aabb\"");
        para("The input is read one character at a time. After each character we list which "
            + "states are active, written as q7:'a', meaning \"state q7 is waiting to read an a\". "
            + "Several states are active at once. That is the whole trick: instead of guessing one "
            + "path and backing up, AEGIS follows all paths together.");
        out("");
        showTrace(engine, "aabb");
        out("");
        para("Result: the ACCEPT state (q0) is active after the last character, so \"aabb\" "
            + "matches.");

        sub("Same pattern, input \"abab\"");
        AegisEngine.Result r = engine.matches("abab");
        para("Result: " + (r.matched() ? "MATCH" : "NO MATCH") + ". The input ends in \"bab\", "
            + "not \"abb\", so the ACCEPT state never becomes active.");

        sub("Bad patterns are caught by the parser");
        for (String bad : new String[] {"(ab", "*a", "[z-a]"}) {
            try {
                AegisEngine.compile(bad);
            } catch (RegexSyntaxException e) {
                out("  " + String.format("%-6s", bad) + " -> rejected: " + e.getMessage());
            }
        }

        sub("Takeaway");
        para("The number of active states can never be larger than the number of states in the "
            + "machine. So the work for each character is bounded, and there is nothing to back up "
            + "to. That is why AEGIS runs in time proportional to the length of the text.");
    }

    /** Stages 1 to 3 of the pipeline, with a one-line explanation of each. */
    static void showStages(AegisEngine engine) {
        sub("Stage 1: LEXER");
        para("Reads the pattern character by character and groups the characters into tokens "
            + "(a literal, an operator, a bracket).");
        para(Lexer.tokenize(engine.pattern()).toString());

        sub("Stage 2: PARSER");
        para("Checks that the tokens follow the regex grammar and builds a tree that shows how the "
            + "pieces nest. This tree is called the abstract syntax tree (AST).");
        for (String line : AstPrinter.print(engine.ast()).split("\n")) {
            out("  " + line);
        }

        sub("Stage 3: NFA (Thompson's construction)");
        para("Turns the tree into a state machine. Each line is one state. \"read X -> qN\" means: "
            + "if the next character is X, go to qN. \"split\" means: fork into two paths without "
            + "reading anything. ACCEPT is the winning state. This machine has "
            + engine.nfa().size() + " states.");
        out(engine.nfa().describe().stripTrailing());
    }

    static void showTrace(AegisEngine engine, String input) {
        for (String line : engine.trace(input)) {
            out("  " + line);
        }
    }

    // ---- part 2 --------------------------------------------------------------------------

    /** Used to stop java.util.regex when it runs too long. */
    static final class Timeout extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Timeout() {
            super("timeout", null, false, false);
        }
    }

    static final class DeadlineSeq implements CharSequence {
        private final String s;
        private final long deadline;
        private int calls;

        DeadlineSeq(String s, long deadlineNanos) {
            this.s = s;
            this.deadline = deadlineNanos;
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

    static void part2UnderAttack() {
        banner("PART 2: AEGIS AGAINST A NORMAL ENGINE UNDER ATTACK");
        para("We give three engines the same pattern and the same hostile input: AEGIS, our "
            + "baseline backtracking engine (the normal approach), and Java's own built-in regex.");
        out("");
        out("  Pattern : ((a+)+)+b");
        out("  Input   : n letters 'a' followed by '!'");
        para("The input can never match, because the pattern needs a 'b'. A backtracking engine "
            + "cannot say \"no\" until it has tried every way of splitting the a's into groups, and "
            + "the number of ways grows exponentially (for this pattern it roughly triples with "
            + "every extra letter). AEGIS has no such problem.");

        final long baselineLimit = BacktrackingEngine.DEFAULT_STEP_LIMIT;
        final long javaLimitMs = 1500;
        String pattern = "((a+)+)+b";
        AegisEngine aegis = AegisEngine.compile(pattern);
        BacktrackingEngine base = BacktrackingEngine.compile(pattern);
        Pattern jdk = Pattern.compile(pattern);

        out("");
        out(String.format("  %-9s %-14s %-26s %s", "letters", "AEGIS work", "Baseline work", "Java regex time"));
        out("  " + "-".repeat(72));

        boolean baseDead = false;
        boolean jdkDead = false;
        int baseGaveUpAt = -1;
        int jdkGaveUpAt = -1;
        double lastAegisMs = 0;
        int lastN = 0;
        for (int n : new int[] {5, 10, 15, 20, 25, 30, 1000, 100000}) {
            String input = "a".repeat(n) + "!";

            long t0 = System.nanoTime();
            AegisEngine.Result ar = aegis.matches(input);
            lastAegisMs = (System.nanoTime() - t0) / 1e6;
            lastN = n;

            String b;
            if (baseDead) {
                b = "(not tried)";
            } else {
                BacktrackingEngine.Result br = base.matches(input);
                if (br.aborted()) {
                    baseDead = true;
                    baseGaveUpAt = n;
                    b = "GAVE UP after " + String.format("%,d", baselineLimit);
                } else {
                    b = String.format("%,d", br.steps());
                }
            }

            String j;
            if (jdkDead) {
                j = "(not tried)";
            } else {
                long start = System.nanoTime();
                try {
                    jdk.matcher(new DeadlineSeq(input, start + javaLimitMs * 1_000_000L)).matches();
                    double ms = (System.nanoTime() - start) / 1e6;
                    j = ms < 0.1 ? "under 0.1 ms" : String.format("%.1f ms", ms);
                } catch (Timeout | StackOverflowError e) {
                    jdkDead = true;
                    jdkGaveUpAt = n;
                    j = "GAVE UP after " + javaLimitMs + " ms";
                }
            }
            out(String.format("  %-9s %-14s %-26s %s", String.format("%,d", n),
                String.format("%,d", ar.steps()), b, j));
        }

        sub("How to read this table");
        bullet("\"Work\" counts the small steps an engine performs. Fewer is better. The two engines "
            + "count slightly differently, so compare how each column GROWS, not the raw numbers "
            + "against each other.");
        bullet("Baseline work grows exponentially: about three times more work for every extra "
            + "letter, until it gives up. This is the blow-up behind ReDoS.");
        bullet("Java's built-in regex is also a backtracking engine, with extra optimisations. "
            + (jdkGaveUpAt > 0
                ? "It copes with small inputs but gave up at " + jdkGaveUpAt + " letters on this pattern."
                : "On this Java version it coped with every size tried here, but other patterns can "
                    + "still defeat it."));
        bullet("AEGIS work grows by the same amount for every extra letter (a straight line), and "
            + "it stays that way even at 100,000 letters.");

        sub("Takeaway");
        para("AEGIS finished the " + String.format("%,d", lastN) + "-letter input in about "
            + String.format("%.0f", Math.max(1, lastAegisMs)) + " ms. "
            + (baseGaveUpAt > 0
                ? "The backtracking engine gave up at " + baseGaveUpAt + " letters. "
                : "")
            + "The pattern and the input are the same for all three, only the matching strategy "
            + "is different.");
    }

    // ---- part 3 --------------------------------------------------------------------------

    record Rule(String name, String about, String regex, boolean search, String note) {}

    record FilterCase(Rule rule, String input, String shown) {}

    static void part3InputFilter() {
        banner("PART 3: A REAL USE, A RULE-BASED INPUT FILTER");
        para("A common job for a regex engine is checking user input: is this a valid username? "
            + "does this message contain attack text? Below is a small input filter with four "
            + "rules. Every case is run through AEGIS and through the normal (backtracking) engine.");

        Rule username = new Rule("username", "3 to 16 letters, digits or underscore",
            "[a-zA-Z0-9_]{3,16}", false, null);
        Rule email = new Rule("email", "name@site.com, .org, .net or .ph",
            "[a-zA-Z0-9._]+@[a-zA-Z0-9]+\\.(com|org|net|ph)", false, null);
        Rule sql = new Rule("sql-scan", "flag SQL-injection style text anywhere in the input",
            "(DROP|DELETE)\\s+TABLE|UNION\\s+SELECT|'\\s*OR\\s+1\\s*=\\s*1", true, null);
        Rule legacy = new Rule("legacy-id", "staff ID such as admin2024@corp.example",
            "([a-zA-Z0-9]+)*@corp\\.example", false,
            "looks harmless, but ( )* around a + is a classic ReDoS shape");

        List<FilterCase> cases = List.of(
            new FilterCase(username, "tantan_01", null),
            new FilterCase(username, "bad name!", null),
            new FilterCase(email, "student@school.ph", null),
            new FilterCase(sql, "SELECT name FROM users WHERE id = 5", null),
            new FilterCase(sql, "5; DROP   TABLE users", null),
            new FilterCase(legacy, "admin2024@corp.example", null),
            new FilterCase(legacy, "a".repeat(30) + "!", "30 letters 'a' followed by '!'  (an attack input)"));

        final long limit = 5_000_000L;
        boolean enginesAgree = true;
        int number = 0;
        for (FilterCase c : cases) {
            number++;
            Rule r = c.rule();
            AegisEngine a = AegisEngine.compile(r.regex());
            BacktrackingEngine b = BacktrackingEngine.compile(r.regex(), limit);
            AegisEngine.Result ar = r.search() ? a.find(c.input()) : a.matches(c.input());
            BacktrackingEngine.Result br = r.search() ? b.find(c.input()) : b.matches(c.input());
            if (!br.aborted() && br.matched() != ar.matched()) {
                enginesAgree = false;
            }

            out("");
            out("  Case " + number + " of " + cases.size());
            out("    Rule      " + r.name() + ": " + r.about());
            out("    Pattern   " + r.regex());
            if (r.note() != null) {
                out("    Note      " + r.note());
            }
            out("    Input     " + (c.shown() != null ? c.shown() : "\"" + c.input() + "\""));
            out("    Decision  " + decision(r, ar.matched()));
            out("    Work      AEGIS " + String.format("%,d", ar.steps()) + "   |   Baseline "
                + (br.aborted() ? "GAVE UP after " + String.format("%,d", limit) : String.format("%,d", br.steps())));
        }

        sub("How to read this");
        bullet("Cases 1 to 6 are normal traffic. "
            + (enginesAgree
                ? "Both engines reach the same decision in every case where the baseline finishes. "
                : "WARNING: the engines disagreed on at least one case. ")
            + "On these, the baseline usually does less work (for example 30 against 216 in case "
            + "1), because it follows a single path while AEGIS keeps track of all paths at once. "
            + "In case 4 the baseline does more, because a search has to restart at every position "
            + "in the text. Either way the numbers are small.");
        bullet("Case 7 is an attack on the legacy rule. The baseline never finishes (it was stopped "
            + "after " + String.format("%,d", limit) + " steps). AEGIS answers in a few hundred.");

        sub("Takeaway");
        para("The price of AEGIS is a little extra work on ordinary input. The reward is a "
            + "guarantee: no input, however it is crafted, can make a rule run for longer than "
            + "(machine size) x (text length). A validator built on a backtracking engine would "
            + "have a worker stuck on case 7.");
    }

    static String decision(Rule r, boolean matched) {
        if (r.search()) {
            return matched ? "BLOCK (suspicious text found)" : "ALLOW";
        }
        return matched ? "ACCEPT (valid)" : "REJECT (invalid)";
    }

    // ---- part 4 --------------------------------------------------------------------------

    static void part4Correctness() {
        banner("PART 4: PROOF THAT THE ANSWERS ARE CORRECT");
        para("Being fast is useless if the answers are wrong. Here Java's built-in regex acts as "
            + "the referee. For every test, AEGIS and the baseline engine must give the same yes or "
            + "no answer as Java, both for \"does the whole text match\" and for \"does any part of "
            + "the text match\".");
        out("");
        Checks c = new Checks();
        c.handWritten();
        out(String.format("  %-38s %,9d test cases", "Hand-written, known answers", c.hand));
        c.syntaxErrors();
        out(String.format("  %-38s %,9d test cases", "Bad patterns that must be rejected", c.syntax));
        c.randomTests(30_000, 42L);
        out(String.format("  %-38s %,9d test cases", "Random (30,000 patterns x 10 inputs)", c.random));

        out("");
        out("  AEGIS disagreed with Java:     " + c.aegisBad + " times");
        out("  Baseline disagreed with Java:  " + c.baseBad + " times");
        if (c.skipped > 0) {
            out("  (" + c.skipped + " baseline checks skipped because it hit its step limit)");
        }
        for (String f : c.firstFailures) {
            out("  FAIL: " + f);
        }
        out("");
        out("  RESULT: " + (c.aegisBad + c.baseBad == 0 ? "PASS" : "FAIL"));

        sub("Takeaway");
        para("Both engines accept the same patterns and give the same answers as Java. They "
            + "differ only in how long they can take.");
    }

    /** Correctness checks against java.util.regex. */
    static final class Checks {
        int hand, syntax, random, aegisBad, baseBad, skipped;
        final List<String> firstFailures = new ArrayList<>();

        void expect(String pattern, String input, boolean matchesExpected, boolean findExpected) {
            Pattern jdk = Pattern.compile(pattern);
            if (jdk.matcher(input).matches() != matchesExpected || jdk.matcher(input).find() != findExpected) {
                note("test expectation is wrong for " + show(pattern) + " on " + show(input));
                aegisBad++;
            }
            hand++;
            compare(pattern, jdk, input);
        }

        void handWritten() {
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
            expect("\\^a", "^a", true, true);
            expect("a\\$", "a$", true, true);
            expect("[$^]+", "$^$", true, true);
            expect("(a+)+", "aaaaaaaaaaaab", false, true);
            expect("(a|aa)+", "aaaaaaaaaaaab", false, true);
            expect("(a*)*", "aaaaaab", false, true);
            expect("((ab)*c)+", "abcc", true, true);
        }

        void syntaxErrors() {
            String[] bad = {"(", "a)", ")", "*a", "+", "a**", "a+?", "a{2", "a{", "a{3,2}", "{2}", "[a",
                "[]", "[z-a]", "\\", "\\q", "\\1", "a|*", "a{1001}", "^a", "a$", "(a+)+$", "[a[b]]",
                "[a&&b]", "(?:a)", "\\b"};
            for (String p : bad) {
                syntax++;
                boolean aegisRejects = rejects(() -> AegisEngine.compile(p));
                boolean baseRejects = rejects(() -> BacktrackingEngine.compile(p));
                if (!aegisRejects) {
                    aegisBad++;
                    note("AEGIS accepted bad pattern " + show(p));
                }
                if (!baseRejects) {
                    baseBad++;
                    note("baseline accepted bad pattern " + show(p));
                }
            }
        }

        boolean rejects(Runnable r) {
            try {
                r.run();
                return false;
            } catch (RegexSyntaxException e) {
                return true;
            }
        }

        void randomTests(int patternCount, long seed) {
            Random rnd = new Random(seed);
            String alphabet = "abc\n";
            for (int i = 0; i < patternCount; i++) {
                String pattern = randomPattern(rnd, 3);
                Pattern jdk = Pattern.compile(pattern);
                for (int k = 0; k < 10; k++) {
                    int len = rnd.nextInt(8);
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < len; j++) {
                        sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
                    }
                    random++;
                    compare(pattern, jdk, sb.toString());
                }
            }
        }

        /** Random pattern that every engine here accepts (quantifiers only follow a parenthesised part). */
        String randomPattern(Random r, int depth) {
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
                    return randomPattern(r, depth - 1) + randomPattern(r, depth - 1);
                case 6:
                    return "(" + randomPattern(r, depth - 1) + "|" + randomPattern(r, depth - 1) + ")";
                case 7:
                    return "(" + randomPattern(r, depth - 1) + ")*";
                case 8:
                    return "(" + randomPattern(r, depth - 1) + ")+";
                default:
                    return "(" + randomPattern(r, depth - 1) + ")" + (r.nextBoolean() ? "?" : "{1,2}");
            }
        }

        void compare(String pattern, Pattern jdk, String input) {
            boolean jm = jdk.matcher(input).matches();
            boolean jf = jdk.matcher(input).find();

            AegisEngine aegis = AegisEngine.compile(pattern);
            boolean am = aegis.matches(input).matched();
            boolean af = aegis.find(input).matched();
            if (am != jm || af != jf) {
                aegisBad++;
                note("AEGIS differs from Java on " + show(pattern) + " with input " + show(input));
            }

            BacktrackingEngine base = BacktrackingEngine.compile(pattern);
            BacktrackingEngine.Result bm = base.matches(input);
            BacktrackingEngine.Result bf = base.find(input);
            if (bm.aborted() || bf.aborted()) {
                skipped++;
            } else if (bm.matched() != jm || bf.matched() != jf) {
                baseBad++;
                note("baseline differs from Java on " + show(pattern) + " with input " + show(input));
            }
        }

        void note(String s) {
            if (firstFailures.size() < 5) {
                firstFailures.add(s);
            }
        }

        static String show(String s) {
            return "\"" + s.replace("\n", "\\n") + "\"";
        }
    }

    // ---- part 5 --------------------------------------------------------------------------

    static void part5Summary() {
        banner("PART 5: SUMMARY, LIMITS AND WHERE THE CODE LIVES");

        sub("What AEGIS is for");
        para("Checking text against patterns when the text, or the pattern itself, might come from "
            + "someone you do not trust: web application firewalls, form validation, log filtering. "
            + "In those places a predictable running time matters more than a little extra speed.");

        sub("What it guarantees");
        para("Matching work is at most (number of NFA states) x (length of the text). This holds "
            + "for every pattern the engine accepts. It is the same idea used by Google's RE2.");

        sub("What this prototype supports");
        para("Literals, the wildcard '.', escapes (\\d \\w \\s and escaped punctuation), character "
            + "classes ([abc] [a-z] [^0-9]), groups, alternation |, and the quantifiers * + ? {n} "
            + "{n,} {n,m}. It answers yes or no: does the whole text match, or does any part of it "
            + "match.");

        sub("What it deliberately does not do");
        para("Backreferences, look-around, lazy quantifiers, capture groups that return text, "
            + "nested classes, and the anchors ^ and $. Backreferences and look-around cannot be "
            + "done in linear time with automata, which is why RE2 leaves them out too. Anything "
            + "unsupported is rejected with a clear error instead of being silently treated "
            + "differently. (To match a literal ^ or $, escape it: \\^ or \\$.)");

        sub("Honest notes about the comparison");
        bullet("On ordinary input AEGIS does more bookkeeping than a simple engine (see Part 3). The "
            + "benefit is the worst case, not the average case.");
        bullet("Java's built-in regex already defends against some patterns, but not all (Part 2).");
        bullet("\"Work\" counts and timings are rough single-run measurements, not a formal benchmark.");

        sub("Where each stage lives in this file");
        out("  Lexical analysis ............ class Lexer");
        out("  Parsing and syntax tree ..... class Parser, interface Node");
        out("  Thompson construction ....... class NfaBuilder");
        out("  Linear-time simulation ...... class AegisEngine (simulate, Sim.add)");
        out("  Normal engine for comparison  class BacktrackingEngine");

        sub("Try it yourself");
        out("  Run with program arguments, for example:");
        out("      \"(a|b)*abb\" aabb abab");
        para("The first value is the pattern, the rest are inputs. You will see every stage and "
            + "the answers from AEGIS, the baseline engine and Java's own regex.");
    }

    // ---- try your own pattern ------------------------------------------------------------

    static void tryOwnPattern(String pattern, List<String> inputs) {
        banner("YOUR PATTERN: " + pattern);
        AegisEngine engine;
        try {
            engine = AegisEngine.compile(pattern);
        } catch (RegexSyntaxException e) {
            out("  Syntax error: " + e.getMessage());
            return;
        }
        showStages(engine);

        BacktrackingEngine base = BacktrackingEngine.compile(pattern);
        Pattern jdk = null;
        try {
            jdk = Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            out("  (Java's regex rejects this pattern, so there is no Java answer to compare.)");
        }

        for (String input : inputs) {
            sub("Input \"" + input + "\"");
            if (input.length() <= 40) {
                showTrace(engine, input);
            } else {
                out("  (input is long, so the step-by-step trace is skipped)");
            }
            AegisEngine.Result ar = engine.matches(input);
            BacktrackingEngine.Result br = base.matches(input);
            out("");
            out("  AEGIS     : " + (ar.matched() ? "MATCH" : "NO MATCH") + "   (work "
                + String.format("%,d", ar.steps()) + ")");
            out("  Baseline  : " + (br.aborted() ? "GAVE UP (" + br.reason() + ")"
                : (br.matched() ? "MATCH" : "NO MATCH") + "   (work " + String.format("%,d", br.steps()) + ")"));
            if (jdk != null) {
                out("  Java regex: " + (jdk.matcher(input).matches() ? "MATCH" : "NO MATCH"));
            }
        }
    }

    // ---- small output helpers ------------------------------------------------------------

    static void out(String s) {
        System.out.println(s);
    }

    static void banner(String title) {
        out("");
        out("=".repeat(WIDTH));
        out(title);
        out("=".repeat(WIDTH));
    }

    static void sub(String title) {
        out("");
        out("--- " + title + " " + "-".repeat(Math.max(3, WIDTH - title.length() - 5)));
    }

    static void para(String text) {
        wrap(text, "  ", "  ");
    }

    static void bullet(String text) {
        wrap(text, "  - ", "    ");
    }

    static void wrap(String text, String first, String rest) {
        StringBuilder line = new StringBuilder(first);
        boolean empty = true;
        for (String word : text.split(" ")) {
            if (!empty && line.length() + 1 + word.length() > WIDTH) {
                out(line.toString());
                line = new StringBuilder(rest);
                empty = true;
            }
            if (!empty) {
                line.append(' ');
            }
            line.append(word);
            empty = false;
        }
        out(line.toString());
    }

    static void pause() {
        if (!pauseBetweenParts) {
            return;
        }
        System.out.print("\n  [ Press Enter to continue ] ");
        System.out.flush();
        try {
            if (STDIN.readLine() == null) {
                pauseBetweenParts = false;
            }
        } catch (IOException e) {
            pauseBetweenParts = false;
        }
        out("");
    }
}

// ==========================================================================================
//  CODE PART A: SHARED FRONT END
//  Both engines start here. The pattern text becomes tokens (Lexer), then a tree (Parser).
// ==========================================================================================

/** Thrown when a pattern is malformed or uses a feature this prototype does not support. */
class RegexSyntaxException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    RegexSyntaxException(String message, int position) {
        super(message + " (at index " + position + ")");
    }
}

/**
 * A set of characters that one input symbol can be matched against: stored as ranges plus a
 * negation flag, so '.', [a-z], [^0-9], \d, \w and \s are all the same kind of object.
 */
final class CharSet {
    private final int[] ranges; // lo0, hi0, lo1, hi1, ...
    private final boolean negated;
    private final String label;

    private CharSet(int[] ranges, boolean negated, String label) {
        this.ranges = ranges;
        this.negated = negated;
        this.label = label;
    }

    static CharSet single(char c) {
        return new CharSet(new int[] {c, c}, false, quote(c));
    }

    /** '.' means any character except line terminators (the same default as java.util.regex). */
    static CharSet dot() {
        return new CharSet(new int[] {10, 10, 13, 13, 0x85, 0x85, 0x2028, 0x2029}, true, ".");
    }

    static CharSet digit() {
        return new CharSet(new int[] {'0', '9'}, false, "\\d");
    }

    static CharSet word() {
        return new CharSet(new int[] {'a', 'z', 'A', 'Z', '0', '9', '_', '_'}, false, "\\w");
    }

    static CharSet space() {
        return new CharSet(new int[] {' ', ' ', 9, 13}, false, "\\s");
    }

    static CharSet ofRanges(int[] ranges, boolean negated, String label) {
        return new CharSet(ranges.clone(), negated, label);
    }

    CharSet negate(String newLabel) {
        return new CharSet(ranges, !negated, newLabel);
    }

    int[] ranges() {
        return ranges.clone();
    }

    boolean matches(char c) {
        boolean inside = false;
        for (int i = 0; i < ranges.length; i += 2) {
            if (c >= ranges[i] && c <= ranges[i + 1]) {
                inside = true;
                break;
            }
        }
        return inside != negated;
    }

    @Override
    public String toString() {
        return label;
    }

    static String quote(char c) {
        switch (c) {
            case '\n': return "'\\n'";
            case '\t': return "'\\t'";
            case '\r': return "'\\r'";
            case '\f': return "'\\f'";
            default:
                if (c < 32 || c > 126) {
                    return String.format("'\\u%04X'", (int) c);
                }
                return "'" + c + "'";
        }
    }
}

/** One lexical unit of a pattern. */
final class Token {
    enum Type { CHAR, LPAREN, RPAREN, PIPE, STAR, PLUS, QMARK, REPEAT, END }

    final Type type;
    final CharSet set; // only for CHAR
    final int min;     // only for REPEAT
    final int max;     // only for REPEAT, -1 means unbounded
    final int pos;     // index in the pattern, used in error messages

    private Token(Type type, CharSet set, int min, int max, int pos) {
        this.type = type;
        this.set = set;
        this.min = min;
        this.max = max;
        this.pos = pos;
    }

    static Token of(Type type, int pos) {
        return new Token(type, null, 0, 0, pos);
    }

    static Token chars(CharSet set, int pos) {
        return new Token(Type.CHAR, set, 0, 0, pos);
    }

    static Token repeat(int min, int max, int pos) {
        return new Token(Type.REPEAT, null, min, max, pos);
    }

    @Override
    public String toString() {
        switch (type) {
            case CHAR: return "CHAR(" + set + ")";
            case REPEAT: return "REPEAT{" + min + "," + (max < 0 ? "" : String.valueOf(max)) + "}";
            default: return type.name();
        }
    }
}

/**
 * Stage 1, LEXER: turns the pattern text into a list of tokens.
 *
 * Supported: literals, '.', escapes (\d \D \w \W \s \S \n \t \r \f and escaped punctuation),
 * classes [abc] [a-z] [^0-9], groups ( ), alternation |, and quantifiers * + ? {n} {n,} {n,m}.
 * Anything else (anchors, backreferences, look-around, lazy quantifiers) is rejected with an error.
 */
final class Lexer {
    static final int MAX_REPEAT = 1000;

    private Lexer() {}

    static List<Token> tokenize(String p) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        int n = p.length();
        while (i < n) {
            char c = p.charAt(i);
            switch (c) {
                case '(' -> { out.add(Token.of(Token.Type.LPAREN, i)); i++; }
                case ')' -> { out.add(Token.of(Token.Type.RPAREN, i)); i++; }
                case '|' -> { out.add(Token.of(Token.Type.PIPE, i)); i++; }
                case '*' -> { out.add(Token.of(Token.Type.STAR, i)); i++; }
                case '+' -> { out.add(Token.of(Token.Type.PLUS, i)); i++; }
                case '?' -> { out.add(Token.of(Token.Type.QMARK, i)); i++; }
                case '.' -> { out.add(Token.chars(CharSet.dot(), i)); i++; }
                case '^', '$' -> throw new RegexSyntaxException("Anchor " + c
                    + " is not supported in this prototype (write \\" + c + " for a literal " + c + ")", i);
                case '[' -> i = lexClass(p, i, out);
                case '\\' -> i = lexEscape(p, i, out);
                case '{' -> i = lexRepeat(p, i, out);
                default -> { out.add(Token.chars(CharSet.single(c), i)); i++; }
            }
        }
        out.add(Token.of(Token.Type.END, n));
        return out;
    }

    private static int lexEscape(String p, int i, List<Token> out) {
        if (i + 1 >= p.length()) {
            throw new RegexSyntaxException("Trailing backslash", i);
        }
        char e = p.charAt(i + 1);
        switch (e) {
            case 'd' -> out.add(Token.chars(CharSet.digit(), i));
            case 'D' -> out.add(Token.chars(CharSet.digit().negate("\\D"), i));
            case 'w' -> out.add(Token.chars(CharSet.word(), i));
            case 'W' -> out.add(Token.chars(CharSet.word().negate("\\W"), i));
            case 's' -> out.add(Token.chars(CharSet.space(), i));
            case 'S' -> out.add(Token.chars(CharSet.space().negate("\\S"), i));
            default -> out.add(Token.chars(CharSet.single(literalEscape(e, i)), i));
        }
        return i + 2;
    }

    /** The character an escape stands for when it is not a class like \d. */
    private static char literalEscape(char e, int pos) {
        switch (e) {
            case 'n': return '\n';
            case 't': return '\t';
            case 'r': return '\r';
            case 'f': return '\f';
            default:
                if (Character.isLetterOrDigit(e)) {
                    throw new RegexSyntaxException(
                        "Unsupported escape \\" + e + " (backreferences, anchors and similar are not implemented)", pos);
                }
                return e; // escaped punctuation such as \. \* \( \\ \[
        }
    }

    private static int lexClass(String p, int start, List<Token> out) {
        int n = p.length();
        int j = start + 1;
        boolean negated = false;
        if (j < n && p.charAt(j) == '^') {
            negated = true;
            j++;
        }
        List<Integer> ranges = new ArrayList<>();
        int items = 0;
        while (true) {
            if (j >= n) {
                throw new RegexSyntaxException("Unclosed character class", start);
            }
            char c = p.charAt(j);
            if (c == ']') {
                if (items == 0) {
                    throw new RegexSyntaxException("Empty character class", start);
                }
                j++;
                break;
            }
            if (c == '[') {
                throw new RegexSyntaxException("Nested character classes are not supported", j);
            }
            if (c == '&' && j + 1 < n && p.charAt(j + 1) == '&') {
                throw new RegexSyntaxException("Class intersection && is not supported", j);
            }
            int lo;
            if (c == '\\') {
                if (j + 1 >= n) {
                    throw new RegexSyntaxException("Trailing backslash", j);
                }
                char e = p.charAt(j + 1);
                if (e == 'd' || e == 'w' || e == 's') {
                    CharSet cs = e == 'd' ? CharSet.digit() : e == 'w' ? CharSet.word() : CharSet.space();
                    for (int r : cs.ranges()) {
                        ranges.add(r);
                    }
                    items++;
                    j += 2;
                    continue;
                }
                if (e == 'D' || e == 'W' || e == 'S') {
                    throw new RegexSyntaxException("\\" + e + " is not supported inside a character class", j);
                }
                lo = literalEscape(e, j);
                j += 2;
            } else {
                lo = c;
                j++;
            }
            int hi = lo;
            if (j + 1 < n && p.charAt(j) == '-' && p.charAt(j + 1) != ']') {
                j++; // skip '-'
                char hc = p.charAt(j);
                if (hc == '[') {
                    throw new RegexSyntaxException("Nested character classes are not supported", j);
                }
                if (hc == '\\') {
                    if (j + 1 >= n) {
                        throw new RegexSyntaxException("Trailing backslash", j);
                    }
                    char e = p.charAt(j + 1);
                    if (Character.isLetterOrDigit(e) && "ntrf".indexOf(e) < 0) {
                        throw new RegexSyntaxException("Class escape cannot end a range", j);
                    }
                    hi = literalEscape(e, j);
                    j += 2;
                } else {
                    hi = hc;
                    j++;
                }
                if (hi < lo) {
                    throw new RegexSyntaxException("Illegal character range in class", start);
                }
            }
            ranges.add(lo);
            ranges.add(hi);
            items++;
        }
        int[] arr = new int[ranges.size()];
        for (int k = 0; k < arr.length; k++) {
            arr[k] = ranges.get(k);
        }
        out.add(Token.chars(CharSet.ofRanges(arr, negated, p.substring(start, j)), start));
        return j;
    }

    private static int lexRepeat(String p, int start, List<Token> out) {
        int n = p.length();
        int j = start + 1;
        int[] a = readInt(p, j);
        if (a == null) {
            throw new RegexSyntaxException("Illegal repetition, expected {n}, {n,} or {n,m}", start);
        }
        int min = a[0];
        j = a[1];
        int max = min;
        if (j < n && p.charAt(j) == ',') {
            j++;
            int[] b = readInt(p, j);
            if (b == null) {
                max = -1;
            } else {
                max = b[0];
                j = b[1];
            }
        }
        if (j >= n || p.charAt(j) != '}') {
            throw new RegexSyntaxException("Unclosed repetition, expected '}'", start);
        }
        j++;
        if (max != -1 && max < min) {
            throw new RegexSyntaxException("Illegal repetition range", start);
        }
        if (min > MAX_REPEAT || max > MAX_REPEAT) {
            throw new RegexSyntaxException("Repetition count too large (limit " + MAX_REPEAT + ")", start);
        }
        out.add(Token.repeat(min, max, start));
        return j;
    }

    /** Reads a run of digits; returns {value, indexAfterDigits}, or null if there is no digit. */
    private static int[] readInt(String p, int j) {
        int n = p.length();
        if (j >= n || !Character.isDigit(p.charAt(j))) {
            return null;
        }
        long v = 0;
        while (j < n && Character.isDigit(p.charAt(j))) {
            v = Math.min(v * 10 + (p.charAt(j) - '0'), 1_000_000L);
            j++;
        }
        return new int[] {(int) v, j};
    }
}

/**
 * The abstract syntax tree (AST) of a pattern. Immutable, so one node can be shared safely
 * (for example when a{2,3} is expanded into several copies of 'a').
 */
sealed interface Node permits Node.Empty, Node.Lit, Node.Cat, Node.Alt, Node.Star, Node.Plus, Node.Opt {
    record Empty() implements Node {}                  // matches the empty string
    record Lit(CharSet set) implements Node {}         // one character from a set
    record Cat(List<Node> parts) implements Node {}    // concatenation: parts in order
    record Alt(List<Node> options) implements Node {}  // alternation: any one of the options
    record Star(Node body) implements Node {}          // zero or more
    record Plus(Node body) implements Node {}          // one or more
    record Opt(Node body) implements Node {}           // zero or one
}

/**
 * Stage 2, PARSER: recursive descent, tokens to AST. Grammar, lowest to highest precedence:
 *   regex  := cat ( '|' cat )*
 *   cat    := repeat*                 (may be empty)
 *   repeat := atom quantifier?
 *   atom   := CHAR | '(' regex ')'
 * Stacked quantifiers such as a** or a+? (lazy or possessive in other engines) are rejected, so the
 * meaning is never silently different.
 */
final class Parser {
    private final List<Token> toks;
    private int p = 0;

    private Parser(List<Token> toks) {
        this.toks = toks;
    }

    static Node parse(String pattern) {
        return new Parser(Lexer.tokenize(pattern)).parseAll();
    }

    private Token peek() {
        return toks.get(p);
    }

    private Token next() {
        return toks.get(p++);
    }

    private Node parseAll() {
        Node n = parseAlt();
        Token t = peek();
        if (t.type != Token.Type.END) {
            throw new RegexSyntaxException("Unmatched ')'", t.pos);
        }
        return n;
    }

    private Node parseAlt() {
        List<Node> options = new ArrayList<>();
        options.add(parseCat());
        while (peek().type == Token.Type.PIPE) {
            next();
            options.add(parseCat());
        }
        return options.size() == 1 ? options.get(0) : new Node.Alt(options);
    }

    private Node parseCat() {
        List<Node> parts = new ArrayList<>();
        while (true) {
            Token.Type t = peek().type;
            if (t == Token.Type.END || t == Token.Type.RPAREN || t == Token.Type.PIPE) {
                break;
            }
            parts.add(parseRepeat());
        }
        if (parts.isEmpty()) {
            return new Node.Empty();
        }
        return parts.size() == 1 ? parts.get(0) : new Node.Cat(parts);
    }

    private Node parseRepeat() {
        Node atom = parseAtom();
        Token q = peek();
        if (isQuantifier(q)) {
            next();
            atom = applyQuantifier(atom, q);
            Token again = peek();
            if (isQuantifier(again)) {
                throw new RegexSyntaxException(
                    "Stacked quantifier is not supported (lazy and possessive quantifiers are not implemented); "
                        + "wrap the repeated part in parentheses", again.pos);
            }
        }
        return atom;
    }

    private Node parseAtom() {
        Token t = next();
        switch (t.type) {
            case CHAR:
                return new Node.Lit(t.set);
            case LPAREN: {
                Node inner = parseAlt();
                if (peek().type != Token.Type.RPAREN) {
                    throw new RegexSyntaxException("Unclosed '('", t.pos);
                }
                next();
                return inner;
            }
            case STAR:
            case PLUS:
            case QMARK:
            case REPEAT:
                throw new RegexSyntaxException("Dangling quantifier with nothing to repeat", t.pos);
            default:
                throw new RegexSyntaxException("Unexpected " + t.type, t.pos);
        }
    }

    private static boolean isQuantifier(Token t) {
        return t.type == Token.Type.STAR || t.type == Token.Type.PLUS
            || t.type == Token.Type.QMARK || t.type == Token.Type.REPEAT;
    }

    private static Node applyQuantifier(Node atom, Token q) {
        switch (q.type) {
            case STAR: return new Node.Star(atom);
            case PLUS: return new Node.Plus(atom);
            case QMARK: return new Node.Opt(atom);
            default: return expand(atom, q.min, q.max);
        }
    }

    /** a{n,m} becomes n copies of a followed by (m-n) optional copies; a{n,} ends with a star. */
    private static Node expand(Node atom, int min, int max) {
        List<Node> parts = new ArrayList<>();
        for (int i = 0; i < min; i++) {
            parts.add(atom);
        }
        if (max < 0) {
            parts.add(new Node.Star(atom));
        } else {
            for (int i = 0; i < max - min; i++) {
                parts.add(new Node.Opt(atom));
            }
        }
        if (parts.isEmpty()) {
            return new Node.Empty();
        }
        return parts.size() == 1 ? parts.get(0) : new Node.Cat(parts);
    }
}

/** Draws an AST as an indented tree. */
final class AstPrinter {
    private AstPrinter() {}

    static String print(Node root) {
        StringBuilder sb = new StringBuilder();
        walk(root, 0, sb);
        return sb.toString();
    }

    private static void walk(Node n, int depth, StringBuilder sb) {
        String pad = "  ".repeat(depth);
        if (n instanceof Node.Empty) {
            sb.append(pad).append("Empty\n");
        } else if (n instanceof Node.Lit l) {
            sb.append(pad).append("Literal ").append(l.set()).append('\n');
        } else if (n instanceof Node.Cat c) {
            sb.append(pad).append("Concatenation (one after another)\n");
            for (Node part : c.parts()) {
                walk(part, depth + 1, sb);
            }
        } else if (n instanceof Node.Alt a) {
            sb.append(pad).append("Alternation (either one)\n");
            for (Node o : a.options()) {
                walk(o, depth + 1, sb);
            }
        } else if (n instanceof Node.Star s) {
            sb.append(pad).append("Star (zero or more of)\n");
            walk(s.body(), depth + 1, sb);
        } else if (n instanceof Node.Plus p) {
            sb.append(pad).append("Plus (one or more of)\n");
            walk(p.body(), depth + 1, sb);
        } else if (n instanceof Node.Opt o) {
            sb.append(pad).append("Optional (zero or one of)\n");
            walk(o.body(), depth + 1, sb);
        }
    }
}

// ==========================================================================================
//  CODE PART B: AEGIS
//  Stage 3: the tree becomes an NFA (NfaBuilder).
//  Stage 4: the NFA is run over the input without ever backing up (AegisEngine).
// ==========================================================================================

/**
 * A nondeterministic finite automaton built with Thompson's construction. Only three kinds of state:
 *   CHAR  : reads one character if it belongs to 'set', then moves to out1
 *   SPLIT : reads nothing, moves to both out1 and out2 (a fork)
 *   MATCH : the accepting state
 */
final class Nfa {
    static final int CHAR = 0;
    static final int SPLIT = 1;
    static final int MATCH = 2;

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

    int size() {
        return states.length;
    }

    String describe() {
        StringBuilder sb = new StringBuilder();
        for (State s : states) {
            sb.append("  q").append(s.id).append(s == start ? " (start)" : "").append(": ");
            switch (s.kind) {
                case CHAR -> sb.append("read ").append(s.set).append(" -> q").append(s.out1.id);
                case SPLIT -> sb.append("split -> q").append(s.out1.id).append(" and q").append(s.out2.id);
                default -> sb.append("ACCEPT");
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}

/**
 * Stage 3: AST to NFA (Thompson's construction). build(node, next) returns the entry state of a
 * machine for 'node' whose exit leads to 'next'. The tree is converted from the back to the front:
 *   Literal       one CHAR state pointing at next
 *   Concatenation build the last part first, then each earlier part pointing at the one after it
 *   Alternation   SPLIT states whose branches each end at next
 *   Optional      SPLIT to (body or next)
 *   Star          SPLIT to (body or next), and the body loops back to the SPLIT
 *   Plus          like Star, but entered through the body, so it must run at least once
 * The result has only a few states per pattern symbol, which is what keeps matching linear.
 */
final class NfaBuilder {
    private static final int MAX_STATES = 500_000;

    private final List<Nfa.State> states = new ArrayList<>();

    private NfaBuilder() {}

    static Nfa build(Node ast) {
        NfaBuilder b = new NfaBuilder();
        Nfa.State match = b.newState(Nfa.MATCH);
        Nfa.State start = b.build(ast, match);
        return new Nfa(b.states, start);
    }

    private Nfa.State newState(int kind) {
        if (states.size() >= MAX_STATES) {
            throw new RegexSyntaxException("Pattern is too large (more than " + MAX_STATES + " NFA states)", 0);
        }
        Nfa.State s = new Nfa.State(states.size(), kind);
        states.add(s);
        return s;
    }

    private Nfa.State build(Node n, Nfa.State next) {
        if (n instanceof Node.Empty) {
            return next;
        }
        if (n instanceof Node.Lit l) {
            Nfa.State s = newState(Nfa.CHAR);
            s.set = l.set();
            s.out1 = next;
            return s;
        }
        if (n instanceof Node.Cat c) {
            Nfa.State cur = next;
            for (int i = c.parts().size() - 1; i >= 0; i--) {
                cur = build(c.parts().get(i), cur);
            }
            return cur;
        }
        if (n instanceof Node.Alt a) {
            List<Node> opts = a.options();
            Nfa.State cur = build(opts.get(opts.size() - 1), next);
            for (int i = opts.size() - 2; i >= 0; i--) {
                Nfa.State split = newState(Nfa.SPLIT);
                split.out1 = build(opts.get(i), next);
                split.out2 = cur;
                cur = split;
            }
            return cur;
        }
        if (n instanceof Node.Opt o) {
            Nfa.State split = newState(Nfa.SPLIT);
            split.out1 = build(o.body(), next);
            split.out2 = next;
            return split;
        }
        if (n instanceof Node.Star s) {
            Nfa.State split = newState(Nfa.SPLIT);
            split.out1 = build(s.body(), split); // the body loops back to the split
            split.out2 = next;
            return split;
        }
        if (n instanceof Node.Plus p) {
            Nfa.State split = newState(Nfa.SPLIT);
            Nfa.State body = build(p.body(), split); // after the body: go again, or leave
            split.out1 = body;
            split.out2 = next;
            return body;
        }
        throw new IllegalStateException("Unknown node " + n);
    }
}

/**
 * AEGIS: lexer, parser, AST, NFA, simulation.
 *
 * Matching never backtracks. It keeps the SET of NFA states the machine could be in and advances
 * the whole set one input character at a time. The set can never hold more than m states (m = NFA
 * size), so total work is at most O(m x n) for an input of length n, whatever the pattern.
 *
 * matches() is a full match (like Matcher.matches()); find() asks whether any substring matches
 * (like Matcher.find()). Both return only true or false.
 */
final class AegisEngine {

    /** matched = the answer; steps = NFA state operations performed; peakActive = most states active at once. */
    record Result(boolean matched, long steps, int peakActive) {}

    private final String pattern;
    private final Node ast;
    private final Nfa nfa;

    private AegisEngine(String pattern, Node ast, Nfa nfa) {
        this.pattern = pattern;
        this.ast = ast;
        this.nfa = nfa;
    }

    /** Throws RegexSyntaxException if the pattern is malformed or unsupported. */
    static AegisEngine compile(String pattern) {
        Node ast = Parser.parse(pattern);
        return new AegisEngine(pattern, ast, NfaBuilder.build(ast));
    }

    String pattern() {
        return pattern;
    }

    Node ast() {
        return ast;
    }

    Nfa nfa() {
        return nfa;
    }

    Result matches(String input) {
        return simulate(input, false, null, null);
    }

    Result find(String input) {
        return simulate(input, true, null, null);
    }

    /** A full match, returning one readable line per step showing which states were active. */
    List<String> trace(String input) {
        List<String> lines = new ArrayList<>();
        simulate(input, false, lines, null);
        return lines;
    }

    /**
     * Same run as trace(), but returns the active state numbers instead of text (used by the window).
     * Entry 0 is "before reading anything", entry k is "after reading k characters". The list is
     * shorter than input.length() + 1 if every state died early.
     */
    List<int[]> traceStates(String input) {
        List<int[]> snapshots = new ArrayList<>();
        simulate(input, false, null, snapshots);
        return snapshots;
    }

    /** Scratch space for one run. */
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
         * Adds 'from' and everything reachable from it through SPLIT states (the "epsilon closure")
         * to 'list'. Only CHAR and MATCH states are stored, because only they matter for the next
         * step. Each state is visited at most once per generation, so loops such as (a*)* are harmless.
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

    private Result simulate(CharSequence in, boolean search, List<String> trace, List<int[]> snaps) {
        Sim sim = new Sim(nfa);
        int size = sim.states.length;
        int[] cur = new int[size];
        int[] nxt = new int[size];

        int curN = sim.add(nfa.start(), cur, 0);
        int peak = curN;
        if (trace != null) {
            trace.add(describe("start", cur, curN, sim));
        }
        if (snaps != null) {
            snaps.add(Arrays.copyOf(cur, curN));
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
                trace.add(describe("read " + CharSet.quote(c), cur, curN, sim));
            }
            if (snaps != null) {
                snaps.add(Arrays.copyOf(cur, curN));
            }
            if (search && sim.hasMatch(cur, curN)) {
                return new Result(true, sim.steps, peak);
            }
            if (!search && curN == 0) {
                return new Result(false, sim.steps, peak); // no state alive, so it can never match
            }
        }
        return new Result(sim.hasMatch(cur, curN), sim.steps, peak);
    }

    private static String describe(String label, int[] list, int count, Sim sim) {
        StringBuilder sb = new StringBuilder(String.format("%-10s active: ", label));
        boolean accepting = false;
        for (int i = 0; i < count; i++) {
            Nfa.State s = sim.states[list[i]];
            if (i > 0) {
                sb.append("  ");
            }
            sb.append('q').append(s.id).append(':');
            if (s.kind == Nfa.MATCH) {
                sb.append("ACCEPT");
                accepting = true;
            } else {
                sb.append(s.set);
            }
        }
        if (count == 0) {
            sb.append("(none) <- no path left, cannot match");
        } else if (accepting) {
            sb.append("   <- ACCEPT is active");
        }
        return sb.toString();
    }
}

// ==========================================================================================
//  CODE PART C: BASELINE (the normal engine, for comparison)
// ==========================================================================================

/**
 * A conventional backtracking regex engine, the same design family as java.util.regex, Python's re
 * and Perl. It is the plain textbook version: unlike the JDK engine it has none of the extra
 * optimisations (such as memoising some loops), so it shows the raw behaviour of the technique.
 *
 * It uses the SAME lexer, parser and AST as AEGIS. Only the matching differs: it walks the tree
 * directly, commits to one choice at a time, and when a later part fails it returns to the most
 * recent choice and tries the next one.
 *
 * It is written in continuation-passing style: match(node, i, k) means "match 'node' at position i,
 * and for every way that works, call k with the position where it ended; stop as soon as k says
 * true". That is what lets it undo a choice, and also what makes it exponential on patterns such as
 * ((a+)+)+b. Every run has a step limit so a bad case reports "gave up" instead of hanging.
 */
final class BacktrackingEngine {
    static final long DEFAULT_STEP_LIMIT = 20_000_000L;

    /** steps = how many times the matcher was entered; aborted = step limit or stack was exhausted. */
    record Result(boolean matched, long steps, boolean aborted, String reason) {}

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

    static BacktrackingEngine compile(String pattern) {
        return compile(pattern, DEFAULT_STEP_LIMIT);
    }

    /** Throws RegexSyntaxException if the pattern is malformed or unsupported. */
    static BacktrackingEngine compile(String pattern, long stepLimit) {
        return new BacktrackingEngine(pattern, Parser.parse(pattern), stepLimit);
    }

    String pattern() {
        return pattern;
    }

    Result matches(String input) {
        return run(input, false);
    }

    Result find(String input) {
        return run(input, true);
    }

    private Result run(String input, boolean search) {
        Run m = new Run(input);
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

    private final class Run {
        private final CharSequence in;
        long steps = 0;

        Run(CharSequence in) {
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
                    // this option failed: fall through and try the next one (this is the backtracking)
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

// ==========================================================================================
//  CODE PART D: THE WINDOW (optional)
// ==========================================================================================

/**
 * A small Swing window on top of the engines above. Swing ships with Java, so nothing extra is
 * needed. Open it with the program argument "gui". Pages (the buttons at the top right):
 *   Home           what AEGIS is and how to use the window
 *   Try It         pick or type a pattern and an input, see the answer from three engines
 *   Step-by-Step   watch AEGIS read the input one character at a time
 *   Under Attack   the ((a+)+)+b attack from Part 2, with bars
 * Nothing here changes how the engines work: it only calls them and draws the results.
 * The "Animations" tick box in the bottom right turns the slow reveals off.
 */
final class AegisGui {

    // ---- look and feel ----------------------------------------------------------------------

    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 14);
    private static final Font MONO_BIG = new Font(Font.MONOSPACED, Font.BOLD, 16);
    private static final Color BG = new Color(0xF4F6F9);
    private static final Color NAVY = new Color(0x0D2B45);
    private static final Color ACCENT = new Color(0x64B5F6);
    private static final Color TEXT = new Color(0x1F2933);
    private static final Color MUTED = new Color(0x5F6B7A);
    private static final Color LINE = new Color(0xD5DBE3);
    private static final Color NEUTRAL_BG = new Color(0xEEF1F5);
    private static final Color BLUE = new Color(0x1565C0);
    private static final Color BLUE_BG = new Color(0xE3F2FD);
    private static final Color GREEN = new Color(0x2E7D32);
    private static final Color GREEN_BG = new Color(0xE8F5E9);
    private static final Color RED = new Color(0xC62828);
    private static final Color RED_BG = new Color(0xFDECEA);
    private static final Color AMBER = new Color(0x9A5B00);
    private static final Color AMBER_BG = new Color(0xFFF4E0);

    private static final long JAVA_LIMIT_MS = 1500;
    private static final int MAX_STEP_INPUT = 30;
    private static final int MIN_BUSY_MS = 450;
    private static final String ATTACK_PATTERN = "((a+)+)+b";
    private static final int[] SIZES = {5, 10, 15, 20, 25, 30, 1000, 100000};
    private static final int OK = 0;
    private static final int GAVE_UP = 1;
    private static final int SKIPPED = 2;

    private static final String HOME = "home";
    private static final String TRY = "try";
    private static final String STEP = "step";
    private static final String ATTACK = "attack";

    private static final String CUSTOM_HELP = "Type any pattern. Supported: | ( ) * + ? {n,m} [a-z] [^a-z] . "
        + "\\d \\w \\s  (no anchors or backreferences)";

    /** A ready-made pattern for the dropdown, with a plain-English meaning and some inputs to try. */
    private record Preset(String name, String pattern, String about, String[] samples) {}

    private static final Preset[] PRESETS = {
        new Preset("Custom (type your own below)", "", CUSTOM_HELP, new String[0]),
        new Preset("Ends in abb", "(a|b)*abb",
            "Means: any mix of the letters a and b, ending in abb.",
            new String[] {"aabb", "babb", "abababb", "abb"}),
        new Preset("Phone number", "\\d{3}-\\d{4}",
            "Means: three digits, a dash, then four digits.",
            new String[] {"555-1234", "123-4567", "800-0000"}),
        new Preset("Simple email", "[a-z]+@[a-z]+\\.com",
            "Means: lowercase letters, an @, lowercase letters, then .com",
            new String[] {"ana@mail.com", "juan@school.com", "maria@example.com"}),
        new Preset("Date (YYYY-MM-DD)", "\\d{4}-\\d{2}-\\d{2}",
            "Means: four digits, dash, two digits, dash, two digits.",
            new String[] {"2026-10-19", "2025-01-31", "1999-12-25"}),
        new Preset("Hex color", "#[0-9a-fA-F]{6}",
            "Means: a # followed by exactly six hex digits (0-9, a-f).",
            new String[] {"#FF00AA", "#1a2b3c", "#000000"}),
        new Preset("Number (with optional decimals)", "-?\\d+(\\.\\d+)?",
            "Means: an optional minus, digits, and optionally a dot with more digits.",
            new String[] {"42", "-3.14", "007", "0.5"}),
        new Preset("Color or colour", "colou?rs?",
            "Means: color or colour, with an optional s at the end.",
            new String[] {"color", "colour", "colors", "colours"}),
        new Preset("Evil pattern (ReDoS attack)", "((a+)+)+b",
            "Matches a's followed by b. To see it freeze normal engines, end the input with ! instead "
            + "of b, for example aaaaaaaaaaaaaaaaaaaaaa!",
            new String[] {"aaab", "ab", "aaaaaaaaaaaaaaab"}),
    };

    private static final String[] MODES = {"Whole input must match", "Find a match anywhere inside"};

    /** Sets the look of the whole window. Called once before the window is built. */
    static void setup() {
        UIManager.put("swing.boldMetal", Boolean.FALSE);
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
            // the default look is fine
        }
        for (Object key : Collections.list(UIManager.getDefaults().keys())) {
            if (UIManager.get(key) instanceof FontUIResource f) {
                UIManager.put(key, new FontUIResource(f.deriveFont(Font.PLAIN, 13f)));
            }
        }
        UIManager.put("Panel.background", BG);
        UIManager.put("CheckBox.background", BG);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("CheckBox.foreground", TEXT);
        UIManager.put("ToolTip.background", new Color(0xFFFBE6));
        UIManager.put("ToolTip.foreground", TEXT);
    }

    /** Opens the window. Called from AegisShowcase when the argument is "gui". */
    static void open() {
        setup();
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("AEGIS: a regex engine that cannot be frozen");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(new AegisGui().build());
            frame.setSize(1020, 720);
            frame.setMinimumSize(new Dimension(880, 640));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    /** The normal engine is recursive, so heavy work runs on a thread with a large stack. */
    private static void background(Runnable work) {
        Thread t = new Thread(null, work, "aegis-gui-worker", 1L << 28);
        t.setDaemon(true);
        t.start();
    }

    // ---- state ------------------------------------------------------------------------------

    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);
    private final Map<String, FadePanel> pageById = new HashMap<>();
    private final List<NavBtn> navButtons = new ArrayList<>();
    private final Strip strip = new Strip();
    private final JLabel helpLabel = new JLabel(" ");
    private final JCheckBox animations = new JCheckBox("Animations", true);
    private JPanel session;
    private String current = HOME;
    private String pageHelp = "";
    private boolean adjusting;
    private int runToken;

    // shared pattern / input bar
    private final JComboBox<String> presetCombo = new JComboBox<>(presetNames());
    private final JTextField patternField = new JTextField(30);
    private final JLabel caption = new JLabel(" ");
    private final JComboBox<String> inputCombo = new JComboBox<>(PRESETS[1].samples());
    private final JComboBox<String> modeCombo = new JComboBox<>(MODES);
    private final Btn runBtn = new Btn("Run", true);

    // Try It
    private final JLabel banner = new JLabel("Press Run", SwingConstants.CENTER);
    private final JLabel bannerSub = new JLabel(" ", SwingConstants.CENTER);
    private final JPanel bannerBox = new JPanel(new GridLayout(2, 1));
    private final DefaultTableModel resultModel = readOnlyModel("Engine", "Answer", "Work / time");
    private final JTextArea inside = new JTextArea();
    private Color bannerFg = MUTED;
    private Color bannerBg = NEUTRAL_BG;
    private javax.swing.Timer bannerTimer;
    private javax.swing.Timer rowsTimer;

    // Step-by-Step
    private final JLabel stepNote = new JLabel(" ");
    private final JLabel stepTitle = new JLabel(" ", SwingConstants.CENTER);
    private final JPanel charsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 4));
    private final JTextArea explain = text("");
    private final Bar stepProgress = new Bar();
    private final JLabel statesTitle = new JLabel(" ");
    private final JPanel statesGrid = new JPanel(new GridLayout(0, 3, 8, 8));
    private final JLabel verdict = new JLabel(" ", SwingConstants.CENTER);
    private final Btn firstBtn = new Btn("<< First", false);
    private final Btn backBtn = new Btn("< Back", false);
    private final Btn nextBtn = new Btn("Next >", true);
    private final Btn lastBtn = new Btn("Last >>", false);
    private final Btn playBtn = new Btn("Play", false);
    private final JComboBox<String> speedCombo = new JComboBox<>(new String[] {"Slow", "Normal", "Fast"});
    private final javax.swing.Timer playTimer = new javax.swing.Timer(900, e -> playTick());
    private javax.swing.Timer chipTimer;
    private AegisEngine stepEngine;
    private List<int[]> snaps = new ArrayList<>();
    private String stepText = "";
    private int stepIndex;
    private boolean stepReady;

    // Under Attack
    private final AegisEngine attackAegis = AegisEngine.compile(ATTACK_PATTERN);
    private final BacktrackingEngine attackBase = BacktrackingEngine.compile(ATTACK_PATTERN);
    private final Pattern attackJdk = Pattern.compile(ATTACK_PATTERN);
    private final JComboBox<String> sizeCombo = new JComboBox<>(sizeNames());
    private final Btn attackRun = new Btn("Run", true);
    private final Btn sweepRun = new Btn("Run all sizes", false);
    private final Btn clearBtn = new Btn("Clear table", false);
    private final Bar aegisBar = new Bar();
    private final Bar baseBar = new Bar();
    private final Bar javaBar = new Bar();
    private final JLabel aegisInfo = new JLabel("Press Run");
    private final JLabel baseInfo = new JLabel("Press Run");
    private final JLabel javaInfo = new JLabel("Press Run");
    private final JTextArea summary = text("Choose a number of letters and press Run. Try 15 letters, "
        + "then 30, to watch the normal engine fall over while AEGIS stays calm.");
    private final DefaultTableModel attackModel =
        readOnlyModel("Letters", "AEGIS work", "Baseline work", "Java regex time");
    private final JTable attackTable = new JTable(attackModel);

    // ---- building the window ----------------------------------------------------------------

    JComponent build() {
        JPanel root = new JPanel(new BorderLayout());

        JPanel top = new JPanel(new BorderLayout());
        top.add(buildHeader(), BorderLayout.NORTH);
        JPanel under = new JPanel(new BorderLayout());
        under.add(strip, BorderLayout.NORTH);
        session = buildSession();
        under.add(session, BorderLayout.CENTER);
        top.add(under, BorderLayout.CENTER);
        root.add(top, BorderLayout.NORTH);

        addPage(HOME, buildHomePage());
        addPage(TRY, buildTryPage());
        addPage(STEP, buildStepPage());
        addPage(ATTACK, buildAttackPage());
        root.add(pages, BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);

        installKeys(root);
        applyPreset(PRESETS[1], false);
        adjusting = true;
        presetCombo.setSelectedIndex(1);
        adjusting = false;
        loadSteps(patternField.getText(), currentInput(), false);
        showPage(HOME);
        return root;
    }

    private void addPage(String id, JComponent content) {
        FadePanel page = new FadePanel(new BorderLayout());
        page.setBorder(BorderFactory.createEmptyBorder(14, 18, 12, 18));
        page.add(content, BorderLayout.CENTER);
        pageById.put(id, page);
        pages.add(page, id);
    }

    private void showPage(String id) {
        current = id;
        cards.show(pages, id);
        for (NavBtn b : navButtons) {
            b.setActive(b.page.equals(id));
        }
        session.setVisible(id.equals(TRY) || id.equals(STEP));
        if (!id.equals(STEP)) {
            stopPlay();
        }
        switch (id) {
            case HOME -> pageHelp = "Welcome! Pick a card below to begin, or press \"Start with an example\".";
            case TRY -> pageHelp = "Choose a common pattern (or type your own), pick an input, then press Run.";
            case STEP -> pageHelp = "Press Next to read one character at a time, or Play to watch it. "
                + "The Left and Right arrow keys work too.";
            default -> pageHelp = "Choose a number of letters and press Run, or Run all sizes to see where "
                + "each engine gives up.";
        }
        helpLabel.setText(pageHelp);
        pageById.get(id).fadeIn();
    }

    private JComponent buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(NAVY);
        p.setBorder(BorderFactory.createEmptyBorder(10, 18, 0, 12));

        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setOpaque(false);
        JLabel title = new JLabel("AEGIS");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JLabel sub = new JLabel("A regex engine that never backs up");
        sub.setForeground(new Color(0xB8CCE0));
        titles.add(title);
        titles.add(sub);
        titles.add(Box.createVerticalStrut(8));
        p.add(titles, BorderLayout.WEST);

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        nav.setOpaque(false);
        String[][] items = {{"Home", HOME, "The start page: what AEGIS is and how to use this window"},
            {"Try It", TRY, "Test a pattern against some text"},
            {"Step-by-Step", STEP, "Watch AEGIS read the text one character at a time"},
            {"Under Attack", ATTACK, "Race AEGIS against a normal engine on a pattern built to freeze it"}};
        for (String[] it : items) {
            NavBtn b = new NavBtn(it[0], it[1]);
            b.addActionListener(e -> showPage(b.page));
            help(b, it[2]);
            navButtons.add(b);
            nav.add(b);
        }
        JPanel navWrap = new JPanel(new BorderLayout());
        navWrap.setOpaque(false);
        navWrap.add(nav, BorderLayout.SOUTH);
        p.add(navWrap, BorderLayout.EAST);
        return p;
    }

    private JComponent buildFooter() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, LINE),
            BorderFactory.createEmptyBorder(6, 18, 6, 14)));
        helpLabel.setForeground(MUTED);
        p.add(helpLabel, BorderLayout.CENTER);
        animations.addActionListener(e -> Anim.enabled = animations.isSelected());
        help(animations, "Turn off the slow fade-ins and loading pauses (useful when you just want answers fast)");
        p.add(animations, BorderLayout.EAST);
        return p;
    }

    // ---- the shared pattern / input bar -----------------------------------------------------

    private static String[] presetNames() {
        String[] names = new String[PRESETS.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = PRESETS[i].name();
        }
        return names;
    }

    private static String[] sizeNames() {
        String[] names = new String[SIZES.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = fmt(SIZES[i]) + " letters";
        }
        return names;
    }

    private JPanel buildSession() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE),
            BorderFactory.createEmptyBorder(8, 18, 8, 18)));

        patternField.setFont(MONO);
        setFieldBorder(false);
        inputCombo.setEditable(true);
        ((JTextField) inputCombo.getEditor().getEditorComponent()).setFont(MONO);
        presetCombo.setBackground(Color.WHITE);
        modeCombo.setBackground(Color.WHITE);
        inputCombo.setBackground(Color.WHITE);
        caption.setForeground(MUTED);
        caption.setFont(caption.getFont().deriveFont(12f));

        help(presetCombo, "Pick a ready-made pattern. It fills in the pattern and some inputs to try.");
        help(patternField, "The regular expression. You can edit it freely. Press Enter to run.");
        help(inputCombo, "Pick a sample (every sample matches the pattern) or type your own. Change a character to see a NO MATCH.");
        help(modeCombo, "Whole input: the pattern must fit all of the text. Find: it may fit any part of it.");
        help(runBtn, "Run the pattern on the input with all three engines");

        presetCombo.addActionListener(e -> {
            if (adjusting) {
                return;
            }
            int i = presetCombo.getSelectedIndex();
            if (i > 0) {
                applyPreset(PRESETS[i], true);
            } else {
                caption.setText(CUSTOM_HELP);
            }
        });
        patternField.getDocument().addDocumentListener(onChange(() -> {
            if (!adjusting) {
                setFieldBorder(false);
                adjusting = true;
                presetCombo.setSelectedIndex(0);
                caption.setText(CUSTOM_HELP);
                adjusting = false;
            }
        }));
        patternField.addActionListener(e -> runSession());
        inputCombo.addActionListener(e -> {
            if (!adjusting) {
                runSession();
            }
        });
        modeCombo.addActionListener(e -> runSession());
        runBtn.addActionListener(e -> runSession());

        p.add(label("Common pattern"), cell(0, 0, 0, GridBagConstraints.NONE));
        p.add(presetCombo, cell(1, 0, 1, GridBagConstraints.HORIZONTAL));
        p.add(label("Match mode"), cell(2, 0, 0, GridBagConstraints.NONE));
        p.add(modeCombo, cell(3, 0, 0, GridBagConstraints.HORIZONTAL));
        p.add(label("Pattern"), cell(0, 1, 0, GridBagConstraints.NONE));
        GridBagConstraints wide = cell(1, 1, 1, GridBagConstraints.HORIZONTAL);
        wide.gridwidth = 3;
        p.add(patternField, wide);
        GridBagConstraints cap = cell(1, 2, 1, GridBagConstraints.HORIZONTAL);
        cap.gridwidth = 3;
        p.add(caption, cap);
        p.add(label("Input"), cell(0, 3, 0, GridBagConstraints.NONE));
        p.add(inputCombo, cell(1, 3, 1, GridBagConstraints.HORIZONTAL));
        GridBagConstraints run = cell(2, 3, 0, GridBagConstraints.HORIZONTAL);
        run.gridwidth = 2;
        p.add(runBtn, run);
        return p;
    }

    private void applyPreset(Preset preset, boolean run) {
        adjusting = true;
        patternField.setText(preset.pattern());
        caption.setText(preset.about());
        inputCombo.setModel(new DefaultComboBoxModel<>(preset.samples()));
        if (preset.samples().length > 0) {
            inputCombo.setSelectedIndex(0);
        }
        setFieldBorder(false);
        adjusting = false;
        if (run) {
            runSession();
        }
    }

    private String currentInput() {
        Object o = inputCombo.getEditor().getItem();
        return o == null ? "" : o.toString();
    }

    private void setFieldBorder(boolean error) {
        patternField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(error ? RED : LINE, error ? 2 : 1),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)));
    }

    // ==========================================================================================
    //  HOME
    // ==========================================================================================

    private JComponent buildHomePage() {
        JLabel welcome = new JLabel("Welcome to AEGIS");
        welcome.setFont(welcome.getFont().deriveFont(Font.BOLD, 28f));
        JTextArea lead = text("AEGIS checks text against a pattern without ever backing up, so a badly "
            + "written pattern cannot freeze it. This window lets you try it, watch it think, and race "
            + "it against a normal engine.");
        lead.setFont(lead.getFont().deriveFont(14f));
        Btn start = new Btn("Start with an example", true);
        help(start, "Loads the pattern (a|b)*abb and runs it on the Try It page");
        start.addActionListener(e -> {
            presetCombo.setSelectedIndex(0);
            adjusting = true;
            presetCombo.setSelectedIndex(1);
            adjusting = false;
            applyPreset(PRESETS[1], true);
            showPage(TRY);
        });
        JPanel startRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        startRow.setOpaque(false);
        startRow.add(start);

        JPanel hero = new JPanel(new BorderLayout(0, 8));
        hero.setOpaque(false);
        hero.add(welcome, BorderLayout.NORTH);
        hero.add(lead, BorderLayout.CENTER);
        hero.add(startRow, BorderLayout.SOUTH);

        JPanel tiles = new JPanel(new GridLayout(1, 3, 14, 0));
        tiles.setOpaque(false);
        tiles.add(tile(1, "Try It", "Pick a common pattern or type your own, choose some text, and press "
            + "Run. You get MATCH or NO MATCH, plus how a normal engine and Java's own regex compare.",
            "Open Try It", TRY));
        tiles.add(tile(2, "Step-by-Step", "Watch AEGIS read the text one character at a time and see every "
            + "path it follows at once. Use Next, or press Play and let it run.",
            "Open Step-by-Step", STEP));
        tiles.add(tile(3, "Under Attack", "Race AEGIS against a normal backtracking engine on a pattern "
            + "built to freeze it. See who finishes, and who gives up.",
            "Open Under Attack", ATTACK));
        JPanel basics = card(new BorderLayout(0, 6));
        basics.add(heading("New to regular expressions?"), BorderLayout.NORTH);
        basics.add(text("A regular expression (regex) is a short pattern that describes a set of texts. "
            + "For example, \\d{3}-\\d{4} describes phone numbers like 555-1234. A regex engine checks "
            + "whether some text fits a pattern. Most engines work by trial and error and can take "
            + "forever on certain patterns, which is how a single bad rule once took down Cloudflare "
            + "in 2019. AEGIS follows all possibilities together, so its work grows in a straight "
            + "line with the length of the text."), BorderLayout.CENTER);

        JPanel lower = new JPanel(new BorderLayout(0, 16));
        lower.setOpaque(false);
        lower.add(tiles, BorderLayout.NORTH);
        JPanel lowest = new JPanel(new BorderLayout());
        lowest.setOpaque(false);
        lowest.add(basics, BorderLayout.NORTH);
        lower.add(lowest, BorderLayout.CENTER);

        JPanel page = new JPanel(new BorderLayout(0, 18));
        page.setOpaque(false);
        page.add(hero, BorderLayout.NORTH);
        page.add(lower, BorderLayout.CENTER);
        return page;
    }

    private JPanel tile(int number, String title, String body, String button, String target) {
        JPanel p = card(new BorderLayout(0, 10));
        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        head.setOpaque(false);
        head.add(new Badge(number));
        head.add(heading(title));
        Btn open = new Btn(button, false);
        open.addActionListener(e -> showPage(target));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.add(open);
        p.add(head, BorderLayout.NORTH);
        p.add(text(body), BorderLayout.CENTER);
        p.add(row, BorderLayout.SOUTH);
        return p;
    }

    // ==========================================================================================
    //  TRY IT
    // ==========================================================================================

    private JComponent buildTryPage() {
        banner.setFont(banner.getFont().deriveFont(Font.BOLD, 30f));
        bannerSub.setForeground(MUTED);
        bannerBox.add(banner);
        bannerBox.add(bannerSub);
        bannerBox.setBorder(BorderFactory.createEmptyBorder(14, 10, 14, 10));
        applyBanner(MUTED, NEUTRAL_BG);

        JTable table = new JTable(resultModel);
        styleTable(table);
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        table.getColumnModel().getColumn(1).setPreferredWidth(90);
        table.getColumnModel().getColumn(2).setPreferredWidth(210);
        JScrollPane tableScroll = scroll(table);
        tableScroll.setPreferredSize(new Dimension(100, 28 * 4 + 4));

        JTextArea note = text("Work counts small internal steps. The engines count slightly differently, "
            + "so compare how the numbers GROW (see Under Attack), not the raw numbers.");
        note.setForeground(MUTED);
        note.setFont(note.getFont().deriveFont(12f));
        Btn watch = new Btn("Watch this step by step >", false);
        help(watch, "Open the Step-by-Step page for this pattern and input");
        watch.addActionListener(e -> showPage(STEP));
        JPanel watchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        watchRow.setOpaque(false);
        watchRow.add(watch);

        JPanel tableCard = card(new BorderLayout(0, 8));
        tableCard.add(heading("How each engine answered"), BorderLayout.NORTH);
        JPanel mid = new JPanel(new BorderLayout(0, 6));
        mid.setOpaque(false);
        mid.add(tableScroll, BorderLayout.NORTH);
        mid.add(note, BorderLayout.CENTER);
        tableCard.add(mid, BorderLayout.CENTER);
        tableCard.add(watchRow, BorderLayout.SOUTH);
        tableCard.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),
            BorderFactory.createEmptyBorder(12, 14, 12, 14)));

        JPanel leftStack = new JPanel(new BorderLayout(0, 12));
        leftStack.setOpaque(false);
        leftStack.add(bannerBox, BorderLayout.NORTH);
        leftStack.add(tableCard, BorderLayout.CENTER);
        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.add(leftStack, BorderLayout.NORTH);

        inside.setEditable(false);
        inside.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        inside.setLineWrap(true);
        inside.setWrapStyleWord(true);
        inside.setBorder(null);
        JScrollPane insideScroll = scroll(inside);
        insideScroll.setBorder(null);
        JPanel insideCard = card(new BorderLayout(0, 8));
        JPanel insideHead = new JPanel(new BorderLayout());
        insideHead.setOpaque(false);
        insideHead.add(heading("Inside AEGIS"), BorderLayout.NORTH);
        insideHead.add(muted("How the pattern is read, in three stages"), BorderLayout.SOUTH);
        insideCard.add(insideHead, BorderLayout.NORTH);
        insideCard.add(insideScroll, BorderLayout.CENTER);

        JPanel page = new JPanel(new GridLayout(1, 2, 14, 0));
        page.setOpaque(false);
        page.add(left);
        page.add(insideCard);
        return page;
    }

    /** Everything the Try It page shows, worked out off the screen thread. */
    private record TryOutcome(String error, boolean aegisMatched, long aegisSteps, double aegisMs,
                              BacktrackingEngine.Result base, String javaAnswer, String javaTime,
                              String insideText) {}

    private void runSession() {
        final String pattern = patternField.getText();
        final String input = currentInput();
        final boolean find = modeCombo.getSelectedIndex() == 1;
        final int token = ++runToken;
        final long t0 = System.currentTimeMillis();

        if (rowsTimer != null) {
            rowsTimer.stop();
        }
        resultModel.setRowCount(0);
        inside.setText("");
        setBanner("Working...", "Reading the pattern and the input", MUTED, NEUTRAL_BG, false);
        loadSteps(pattern, input, find);
        strip.work(+1);
        background(() -> {
            TryOutcome r;
            try {
                r = computeTry(pattern, input, find);
            } catch (RuntimeException e) {
                r = new TryOutcome("Could not run: " + e, false, 0, 0, null, "", "", "");
            }
            final TryOutcome result = r;
            long wait = Anim.enabled ? Math.max(0, MIN_BUSY_MS - (System.currentTimeMillis() - t0)) : 0;
            SwingUtilities.invokeLater(() -> {
                javax.swing.Timer t = new javax.swing.Timer((int) wait, e -> {
                    strip.work(-1);
                    if (token == runToken) {
                        showTry(result);
                    }
                });
                t.setRepeats(false);
                t.start();
            });
        });
    }

    private static TryOutcome computeTry(String pattern, String input, boolean find) {
        AegisEngine engine;
        try {
            engine = AegisEngine.compile(pattern);
        } catch (RegexSyntaxException e) {
            return new TryOutcome(e.getMessage(), false, 0, 0, null, "", "", "");
        }
        long t0 = System.nanoTime();
        AegisEngine.Result a = find ? engine.find(input) : engine.matches(input);
        double aegisMs = (System.nanoTime() - t0) / 1e6;

        BacktrackingEngine base = BacktrackingEngine.compile(pattern);
        BacktrackingEngine.Result b = find ? base.find(input) : base.matches(input);

        String javaAnswer;
        String javaTime;
        try {
            Pattern jdk = Pattern.compile(pattern);
            long start = System.nanoTime();
            try {
                Matcher m = jdk.matcher(new AegisShowcase.DeadlineSeq(input, start + JAVA_LIMIT_MS * 1_000_000L));
                boolean ok = find ? m.find() : m.matches();
                javaAnswer = ok ? "MATCH" : "NO MATCH";
                javaTime = ms((System.nanoTime() - start) / 1e6);
            } catch (AegisShowcase.Timeout | StackOverflowError e) {
                javaAnswer = "GAVE UP";
                javaTime = "stopped after " + JAVA_LIMIT_MS + " ms";
            }
        } catch (PatternSyntaxException e) {
            javaAnswer = "n/a";
            javaTime = "Java rejects this pattern";
        }

        String insideText = "1. LEXER: pattern -> tokens\n  " + Lexer.tokenize(pattern)
            + "\n\n2. PARSER: tokens -> syntax tree\n"
            + indent(AstPrinter.print(engine.ast()))
            + "\n\n3. NFA: tree -> state machine (" + engine.nfa().size() + " states)\n"
            + engine.nfa().describe().stripTrailing();
        return new TryOutcome(null, a.matched(), a.steps(), aegisMs, b, javaAnswer, javaTime, insideText);
    }

    private void showTry(TryOutcome r) {
        resultModel.setRowCount(0);
        if (r.error() != null) {
            setBanner("Syntax error", r.error(), AMBER, AMBER_BG, true);
            setFieldBorder(true);
            inside.setText("");
            return;
        }
        setFieldBorder(false);
        if (r.aegisMatched()) {
            setBanner("MATCH", "The input fits the pattern", GREEN, GREEN_BG, true);
        } else {
            setBanner("NO MATCH", "The input does not fit the pattern", RED, RED_BG, true);
        }
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[] {"AEGIS", r.aegisMatched() ? "MATCH" : "NO MATCH",
            fmt(r.aegisSteps()) + " steps (" + ms(r.aegisMs()) + ")"});
        BacktrackingEngine.Result b = r.base();
        if (b.aborted()) {
            rows.add(new Object[] {"Baseline", "GAVE UP", b.reason().startsWith("step limit")
                ? "stopped after " + fmt(BacktrackingEngine.DEFAULT_STEP_LIMIT) + " steps" : b.reason()});
        } else {
            rows.add(new Object[] {"Baseline", b.matched() ? "MATCH" : "NO MATCH", fmt(b.steps()) + " steps"});
        }
        rows.add(new Object[] {"Java regex", r.javaAnswer(), r.javaTime()});
        revealRows(rows);
        inside.setText(r.insideText());
        inside.setCaretPosition(0);
    }

    /** Adds the rows one after another so the answers appear gradually. */
    private void revealRows(List<Object[]> rows) {
        if (!Anim.enabled) {
            rows.forEach(resultModel::addRow);
            return;
        }
        final int[] next = {0};
        rowsTimer = new javax.swing.Timer(280, e -> {
            resultModel.addRow(rows.get(next[0]++));
            if (next[0] >= rows.size()) {
                ((javax.swing.Timer) e.getSource()).stop();
            }
        });
        rowsTimer.setInitialDelay(150);
        rowsTimer.start();
    }

    private void setBanner(String big, String small, Color fg, Color bg, boolean animate) {
        banner.setText(big);
        bannerSub.setText(small);
        final Color fg0 = bannerFg;
        final Color bg0 = bannerBg;
        if (bannerTimer != null) {
            bannerTimer.stop();
        }
        bannerFg = fg;
        bannerBg = bg;
        if (animate) {
            bannerTimer = Anim.run(400, t -> applyBanner(Anim.mix(fg0, fg, t), Anim.mix(bg0, bg, t)), null);
        } else {
            applyBanner(fg, bg);
        }
    }

    private void applyBanner(Color fg, Color bg) {
        banner.setForeground(fg);
        bannerSub.setForeground(fg == MUTED ? MUTED : fg);
        bannerBox.setBackground(bg);
    }

    // ==========================================================================================
    //  STEP-BY-STEP
    // ==========================================================================================

    private JComponent buildStepPage() {
        JLabel intro = muted("AEGIS follows every possible path at the same time. Read the input one "
            + "character at a time and watch which states stay active.");
        stepNote.setForeground(AMBER);
        JPanel introBox = new JPanel(new GridLayout(0, 1, 0, 2));
        introBox.setOpaque(false);
        introBox.add(intro);
        introBox.add(stepNote);

        stepTitle.setFont(stepTitle.getFont().deriveFont(Font.BOLD, 15f));
        charsPanel.setOpaque(false);
        explain.setForeground(MUTED);
        JPanel under = new JPanel(new BorderLayout(0, 8));
        under.setOpaque(false);
        under.add(explain, BorderLayout.NORTH);
        under.add(stepProgress, BorderLayout.SOUTH);
        JPanel reading = card(new BorderLayout(0, 4));
        reading.add(stepTitle, BorderLayout.NORTH);
        reading.add(charsPanel, BorderLayout.CENTER);
        reading.add(under, BorderLayout.SOUTH);

        statesTitle.setFont(statesTitle.getFont().deriveFont(Font.BOLD, 14f));
        JLabel legend = muted("blue = waiting to read a character,  green = ACCEPT (a match)");
        legend.setFont(legend.getFont().deriveFont(12f));
        JPanel statesHead = new JPanel(new BorderLayout());
        statesHead.setOpaque(false);
        statesHead.add(statesTitle, BorderLayout.WEST);
        statesHead.add(legend, BorderLayout.EAST);
        statesGrid.setOpaque(false);
        JPanel gridWrap = new JPanel(new BorderLayout());
        gridWrap.setOpaque(false);
        gridWrap.add(statesGrid, BorderLayout.NORTH);
        JScrollPane gridScroll = new JScrollPane(gridWrap);
        gridScroll.setBorder(null);
        gridScroll.setOpaque(false);
        gridScroll.getViewport().setOpaque(false);
        JPanel statesCard = card(new BorderLayout(0, 10));
        statesCard.add(statesHead, BorderLayout.NORTH);
        statesCard.add(gridScroll, BorderLayout.CENTER);

        verdict.setOpaque(true);
        verdict.setFont(verdict.getFont().deriveFont(Font.BOLD, 18f));
        verdict.setBorder(BorderFactory.createEmptyBorder(9, 8, 9, 8));
        verdict.setBackground(BG);

        firstBtn.addActionListener(e -> goTo(0));
        backBtn.addActionListener(e -> goTo(stepIndex - 1));
        nextBtn.addActionListener(e -> goTo(stepIndex + 1));
        lastBtn.addActionListener(e -> goTo(snaps.size() - 1));
        playBtn.addActionListener(e -> togglePlay());
        speedCombo.setSelectedIndex(1);
        speedCombo.setBackground(Color.WHITE);
        speedCombo.addActionListener(e -> playTimer.setDelay(new int[] {1500, 900, 450}[speedCombo.getSelectedIndex()]));
        help(firstBtn, "Go back to the start, before any character is read");
        help(backBtn, "Go back one character");
        help(nextBtn, "Read the next character");
        help(lastBtn, "Jump to the end and see the final answer");
        help(playBtn, "Step through automatically. Press again to pause.");
        help(speedCombo, "How fast Play moves from one character to the next");

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        nav.setOpaque(false);
        nav.add(firstBtn);
        nav.add(backBtn);
        nav.add(nextBtn);
        nav.add(lastBtn);
        nav.add(Box.createHorizontalStrut(14));
        nav.add(playBtn);
        nav.add(speedCombo);
        JPanel bottom = new JPanel(new BorderLayout(0, 10));
        bottom.setOpaque(false);
        bottom.add(verdict, BorderLayout.NORTH);
        bottom.add(nav, BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);
        center.add(reading, BorderLayout.NORTH);
        center.add(statesCard, BorderLayout.CENTER);
        JPanel page = new JPanel(new BorderLayout(0, 10));
        page.setOpaque(false);
        page.add(introBox, BorderLayout.NORTH);
        page.add(center, BorderLayout.CENTER);
        page.add(bottom, BorderLayout.SOUTH);
        return page;
    }

    private void loadSteps(String pattern, String input, boolean find) {
        stopPlay();
        stepReady = false;
        stepNote.setText(find ? "Note: Step-by-Step always checks the whole input, even though the match "
            + "mode is set to \"find\"." : " ");
        try {
            stepEngine = AegisEngine.compile(pattern);
        } catch (RegexSyntaxException e) {
            stepMessage("Syntax error: " + e.getMessage());
            return;
        }
        if (input.length() > MAX_STEP_INPUT) {
            stepMessage("The input has " + input.length() + " characters. Step-by-Step shows up to "
                + MAX_STEP_INPUT + " so every character fits on screen. Use a shorter input.");
            return;
        }
        stepText = input;
        snaps = stepEngine.traceStates(input);
        stepIndex = 0;
        stepReady = true;
        renderStep();
    }

    /** Shows a message instead of a trace (bad pattern, input too long). */
    private void stepMessage(String message) {
        stepTitle.setText(" ");
        charsPanel.removeAll();
        statesGrid.removeAll();
        explain.setText(message);
        explain.setForeground(AMBER);
        statesTitle.setText("Active states");
        verdict.setText(" ");
        verdict.setBackground(BG);
        stepProgress.animateTo(0, BLUE);
        updateNav();
        stepRefresh();
    }

    private void goTo(int index) {
        if (!stepReady) {
            return;
        }
        stepIndex = Math.max(0, Math.min(snaps.size() - 1, index));
        renderStep();
    }

    private void renderStep() {
        final int last = snaps.size() - 1;
        int[] active = snaps.get(stepIndex).clone();
        Arrays.sort(active);
        Nfa.State[] states = stepEngine.nfa().states();

        explain.setForeground(MUTED);
        if (stepIndex == 0) {
            stepTitle.setText("Start: nothing has been read yet");
            explain.setText("AEGIS activates the starting state, plus every state it can reach without "
                + "reading a character (through \"split\" states). " + countText(active.length));
        } else {
            char c = stepText.charAt(stepIndex - 1);
            stepTitle.setText("Read " + CharSet.quote(c) + "   (character " + stepIndex + " of "
                + stepText.length() + ")");
            explain.setText("Every active state that was waiting for " + CharSet.quote(c)
                + " moved forward. The other states dropped out. " + countText(active.length));
        }
        stepProgress.animateTo(last == 0 ? 1 : (double) stepIndex / Math.max(1, stepText.length()), BLUE);

        // the input as boxes: read, current, not yet read
        charsPanel.removeAll();
        if (stepText.isEmpty()) {
            charsPanel.add(muted("(empty input)"));
        }
        JLabel currentBox = null;
        for (int j = 0; j < stepText.length(); j++) {
            char ch = stepText.charAt(j);
            JLabel box = new JLabel(ch == ' ' ? "\u2423" : String.valueOf(ch), SwingConstants.CENTER);
            box.setFont(MONO_BIG);
            box.setOpaque(true);
            box.setPreferredSize(new Dimension(30, 38));
            if (j == stepIndex - 1) {
                currentBox = box;
                box.setBackground(BLUE);
                box.setForeground(Color.WHITE);
                box.setBorder(BorderFactory.createLineBorder(BLUE, 1));
            } else if (j < stepIndex - 1) {
                box.setBackground(NEUTRAL_BG);
                box.setForeground(MUTED);
                box.setBorder(BorderFactory.createLineBorder(LINE, 1));
            } else {
                box.setBackground(Color.WHITE);
                box.setForeground(TEXT);
                box.setBorder(BorderFactory.createLineBorder(MUTED, 1));
            }
            charsPanel.add(box);
        }

        // the active states as chips, fading in one after another
        statesGrid.removeAll();
        boolean accepting = false;
        final List<JLabel> chips = new ArrayList<>();
        final List<Color> chipBg = new ArrayList<>();
        final List<Color> chipFg = new ArrayList<>();
        for (int id : active) {
            Nfa.State s = states[id];
            JLabel chip = new JLabel();
            chip.setOpaque(true);
            chip.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            chip.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)));
            if (s.kind == Nfa.MATCH) {
                accepting = true;
                chip.setText("q" + id + "   ACCEPT");
                chipBg.add(GREEN_BG);
                chipFg.add(GREEN);
            } else {
                chip.setText("q" + id + "   waits for " + s.set);
                chipBg.add(BLUE_BG);
                chipFg.add(TEXT);
            }
            chips.add(chip);
            statesGrid.add(chip);
        }
        if (active.length == 0) {
            JLabel none = new JLabel("No active states: every path has died");
            none.setForeground(RED);
            statesGrid.add(none);
        }
        statesTitle.setText("Active states: " + active.length);

        // the final answer, only on the last step
        final Color vFg;
        final Color vBg;
        if (stepIndex == last) {
            boolean fullyRead = last == stepText.length();
            if (fullyRead && accepting) {
                verdict.setText("MATCH: ACCEPT is active after the last character");
                vFg = GREEN;
                vBg = GREEN_BG;
            } else if (fullyRead) {
                verdict.setText("NO MATCH: ACCEPT is not active after the last character");
                vFg = RED;
                vBg = RED_BG;
            } else {
                verdict.setText("NO MATCH: no path is left, so the rest of the input cannot save it");
                vFg = RED;
                vBg = RED_BG;
            }
        } else {
            verdict.setText(" ");
            vFg = TEXT;
            vBg = BG;
        }
        updateNav();

        final JLabel cur = currentBox;
        if (chipTimer != null) {
            chipTimer.stop();
        }
        chipTimer = Anim.run(300, t -> {
            int n = chips.size();
            double stagger = 0.4 / Math.max(1, n);
            for (int i = 0; i < n; i++) {
                double local = Math.max(0, Math.min(1, (t - i * stagger) / 0.6));
                chips.get(i).setBackground(Anim.mix(Color.WHITE, chipBg.get(i), local));
                chips.get(i).setForeground(Anim.mix(Color.WHITE, chipFg.get(i), local));
            }
            if (cur != null) {
                cur.setBackground(Anim.mix(Color.WHITE, BLUE, t));
                cur.setForeground(Anim.mix(TEXT, Color.WHITE, t));
            }
            verdict.setForeground(vFg);
            verdict.setBackground(Anim.mix(BG, vBg, t));
        }, null);
        stepRefresh();
    }

    private static String countText(int n) {
        if (n == 0) {
            return "No state is active, so this input can never match.";
        }
        return n + (n == 1 ? " state is" : " states are") + " now active.";
    }

    private void updateNav() {
        int last = snaps.size() - 1;
        firstBtn.setEnabled(stepReady && stepIndex > 0);
        backBtn.setEnabled(stepReady && stepIndex > 0);
        nextBtn.setEnabled(stepReady && stepIndex < last);
        lastBtn.setEnabled(stepReady && stepIndex < last);
        playBtn.setEnabled(stepReady);
    }

    private void stepRefresh() {
        charsPanel.revalidate();
        charsPanel.repaint();
        statesGrid.revalidate();
        statesGrid.repaint();
    }

    private void togglePlay() {
        if (playTimer.isRunning()) {
            stopPlay();
            return;
        }
        if (stepIndex >= snaps.size() - 1) {
            goTo(0);
        }
        playBtn.setText("Pause");
        playTimer.start();
    }

    private void playTick() {
        if (!stepReady || stepIndex >= snaps.size() - 1) {
            stopPlay();
            return;
        }
        goTo(stepIndex + 1);
        if (stepIndex >= snaps.size() - 1) {
            stopPlay();
        }
    }

    private void stopPlay() {
        playTimer.stop();
        playBtn.setText("Play");
    }

    private void installKeys(JComponent root) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("RIGHT"), "stepNext");
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("LEFT"), "stepBack");
        root.getActionMap().put("stepNext", action(() -> {
            if (current.equals(STEP)) {
                goTo(stepIndex + 1);
            }
        }));
        root.getActionMap().put("stepBack", action(() -> {
            if (current.equals(STEP)) {
                goTo(stepIndex - 1);
            }
        }));
    }

    // ==========================================================================================
    //  UNDER ATTACK
    // ==========================================================================================

    private record Attack(int n, long aSteps, double aMs, long bSteps, int bState, double jMs, int jState) {}

    private JComponent buildAttackPage() {
        JTextArea intro = text("Pattern  " + ATTACK_PATTERN + "  against n letters 'a' followed by '!'. "
            + "The input can never match, because the pattern needs a 'b'. A backtracking engine must "
            + "try every way of splitting the a's into groups before it can say no, and the number of "
            + "ways grows exponentially.");
        sizeCombo.setSelectedIndex(2);
        sizeCombo.setBackground(Color.WHITE);
        help(sizeCombo, "How many letters 'a' the attack input contains");
        help(attackRun, "Run all three engines on the chosen size");
        help(sweepRun, "Run every size in the list, one after another, like Part 2 of the text tour");
        help(clearBtn, "Empty the table below");
        attackRun.addActionListener(e -> runAttack(false));
        sweepRun.addActionListener(e -> runAttack(true));
        clearBtn.addActionListener(e -> attackModel.setRowCount(0));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        controls.add(label("Input size"));
        controls.add(sizeCombo);
        controls.add(attackRun);
        controls.add(sweepRun);
        controls.add(clearBtn);
        JPanel head = card(new BorderLayout(0, 10));
        head.add(intro, BorderLayout.CENTER);
        head.add(controls, BorderLayout.SOUTH);

        summary.setFont(summary.getFont().deriveFont(Font.BOLD, 13f));
        JPanel callout = new JPanel(new BorderLayout());
        callout.setBackground(BLUE_BG);
        callout.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 4, 0, 0, BLUE),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        callout.add(summary, BorderLayout.CENTER);

        JPanel results = new JPanel(new GridLayout(1, 3, 12, 0));
        results.setOpaque(false);
        results.add(resultCard("AEGIS", aegisBar, aegisInfo, "Bar = share of the baseline's step limit"));
        results.add(resultCard("Baseline (normal backtracking)", baseBar, baseInfo,
            "Bar = share of the " + fmt(BacktrackingEngine.DEFAULT_STEP_LIMIT) + "-step limit"));
        results.add(resultCard("Java's built-in regex", javaBar, javaInfo,
            "Bar = share of the " + JAVA_LIMIT_MS + " ms time limit"));

        styleTable(attackTable);
        JScrollPane tableScroll = scroll(attackTable);

        JPanel middle = new JPanel(new BorderLayout(0, 12));
        middle.setOpaque(false);
        JPanel stack = new JPanel(new BorderLayout(0, 12));
        stack.setOpaque(false);
        stack.add(callout, BorderLayout.NORTH);
        stack.add(results, BorderLayout.CENTER);
        middle.add(stack, BorderLayout.NORTH);
        middle.add(tableScroll, BorderLayout.CENTER);

        JPanel page = new JPanel(new BorderLayout(0, 12));
        page.setOpaque(false);
        page.add(head, BorderLayout.NORTH);
        page.add(middle, BorderLayout.CENTER);
        return page;
    }

    private JPanel resultCard(String title, Bar bar, JLabel info, String scaleNote) {
        JLabel note = muted(scaleNote);
        note.setFont(note.getFont().deriveFont(11f));
        info.setFont(info.getFont().deriveFont(Font.BOLD, 15f));
        JPanel texts = new JPanel(new GridLayout(0, 1, 0, 3));
        texts.setOpaque(false);
        texts.add(info);
        texts.add(note);
        JPanel p = card(new BorderLayout(0, 10));
        p.add(heading(title), BorderLayout.NORTH);
        JPanel inner = new JPanel(new BorderLayout(0, 8));
        inner.setOpaque(false);
        inner.add(bar, BorderLayout.NORTH);
        inner.add(texts, BorderLayout.CENTER);
        p.add(inner, BorderLayout.CENTER);
        return p;
    }

    private void runAttack(boolean sweep) {
        final int n = SIZES[sizeCombo.getSelectedIndex()];
        setAttackBusy(true);
        strip.work(+1);
        background(() -> {
            try {
                if (!sweep) {
                    long t0 = System.currentTimeMillis();
                    Attack r = attack(n, true, true);
                    pause(Math.max(0, MIN_BUSY_MS - (System.currentTimeMillis() - t0)));
                    SwingUtilities.invokeLater(() -> showAttack(r, true));
                } else {
                    boolean baseDead = false;
                    boolean javaDead = false;
                    int baseAt = -1;
                    int javaAt = -1;
                    Attack lastRun = null;
                    for (int size : SIZES) {
                        long t0 = System.currentTimeMillis();
                        Attack r = attack(size, !baseDead, !javaDead);
                        if (r.bState() == GAVE_UP) {
                            baseDead = true;
                            baseAt = size;
                        }
                        if (r.jState() == GAVE_UP) {
                            javaDead = true;
                            javaAt = size;
                        }
                        lastRun = r;
                        pause(Math.max(0, MIN_BUSY_MS + 150 - (System.currentTimeMillis() - t0)));
                        SwingUtilities.invokeLater(() -> showAttack(r, false));
                    }
                    final String text = "AEGIS handled every size, up to " + fmt(lastRun.n()) + " letters, "
                        + "with about " + Math.round((double) lastRun.aSteps() / lastRun.n())
                        + " steps per letter: a straight line. "
                        + (baseAt > 0 ? "The normal backtracking engine gave up at " + baseAt + " letters. "
                            : "The baseline coped with every size. ")
                        + (javaAt > 0 ? "Java's built-in regex gave up at " + javaAt + " letters."
                            : "Java's built-in regex coped with every size.");
                    SwingUtilities.invokeLater(() -> summary.setText(text));
                }
            } finally {
                SwingUtilities.invokeLater(() -> {
                    strip.work(-1);
                    setAttackBusy(false);
                });
            }
        });
    }

    private static void pause(long ms) {
        if (Anim.enabled && ms > 0) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void setAttackBusy(boolean busy) {
        attackRun.setEnabled(!busy);
        sweepRun.setEnabled(!busy);
        sizeCombo.setEnabled(!busy);
    }

    private Attack attack(int n, boolean tryBase, boolean tryJava) {
        String input = "a".repeat(n) + "!";
        long t0 = System.nanoTime();
        AegisEngine.Result a = attackAegis.matches(input);
        double aMs = (System.nanoTime() - t0) / 1e6;

        long bSteps = 0;
        int bState = SKIPPED;
        if (tryBase) {
            BacktrackingEngine.Result b = attackBase.matches(input);
            bSteps = b.steps();
            bState = b.aborted() ? GAVE_UP : OK;
        }

        double jMs = 0;
        int jState = SKIPPED;
        if (tryJava) {
            long start = System.nanoTime();
            try {
                attackJdk.matcher(new AegisShowcase.DeadlineSeq(input, start + JAVA_LIMIT_MS * 1_000_000L)).matches();
                jMs = (System.nanoTime() - start) / 1e6;
                jState = OK;
            } catch (AegisShowcase.Timeout | StackOverflowError e) {
                jState = GAVE_UP;
            }
        }
        return new Attack(n, a.steps(), aMs, bSteps, bState, jMs, jState);
    }

    private void showAttack(Attack r, boolean explainOne) {
        double limit = BacktrackingEngine.DEFAULT_STEP_LIMIT;
        aegisBar.animateTo(r.aSteps() / limit, BLUE);
        aegisInfo.setText(fmt(r.aSteps()) + " steps, " + ms(r.aMs()));

        String baseShort;
        if (r.bState() == SKIPPED) {
            baseBar.animateTo(0, BLUE);
            baseInfo.setText("(not tried)");
            baseShort = "(not tried)";
        } else if (r.bState() == GAVE_UP) {
            baseBar.animateTo(1, RED);
            baseInfo.setText("GAVE UP");
            baseShort = "GAVE UP";
        } else {
            baseBar.animateTo(r.bSteps() / limit, BLUE);
            baseInfo.setText(fmt(r.bSteps()) + " steps");
            baseShort = fmt(r.bSteps());
        }

        String javaShort;
        if (r.jState() == SKIPPED) {
            javaBar.animateTo(0, BLUE);
            javaInfo.setText("(not tried)");
            javaShort = "(not tried)";
        } else if (r.jState() == GAVE_UP) {
            javaBar.animateTo(1, RED);
            javaInfo.setText("GAVE UP");
            javaShort = "GAVE UP";
        } else {
            javaBar.animateTo(r.jMs() / JAVA_LIMIT_MS, BLUE);
            javaInfo.setText(ms(r.jMs()));
            javaShort = ms(r.jMs());
        }

        if (explainOne) {
            if (r.bState() == GAVE_UP) {
                summary.setText("At " + fmt(r.n()) + " letters the normal backtracking engine gave up after "
                    + fmt(BacktrackingEngine.DEFAULT_STEP_LIMIT) + " steps. AEGIS finished with only "
                    + fmt(r.aSteps()) + " steps.");
            } else if (r.jState() == GAVE_UP) {
                summary.setText("Java's built-in regex gave up at " + fmt(r.n()) + " letters, while AEGIS "
                    + "finished with only " + fmt(r.aSteps()) + " steps.");
            } else {
                summary.setText("All three engines coped with " + fmt(r.n()) + " letters. Try a larger size "
                    + "to see the normal engine struggle.");
            }
        }

        attackModel.addRow(new Object[] {fmt(r.n()), fmt(r.aSteps()), baseShort, javaShort});
        int row = attackModel.getRowCount() - 1;
        attackTable.scrollRectToVisible(attackTable.getCellRect(row, 0, true));
    }

    // ==========================================================================================
    //  SMALL WIDGETS AND HELPERS
    // ==========================================================================================

    /** Shows a hint in the bottom bar while the mouse is over a control, and as a tooltip. */
    private <T extends JComponent> T help(T c, String text) {
        c.setToolTipText(text);
        c.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                helpLabel.setText(text);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                helpLabel.setText(pageHelp);
            }
        });
        return c;
    }

    /** Colors and timing for the slow reveals. */
    private static final class Anim {
        static volatile boolean enabled = true;

        static javax.swing.Timer run(int durationMs, java.util.function.DoubleConsumer frame, Runnable done) {
            if (!enabled || durationMs <= 0) {
                frame.accept(1.0);
                if (done != null) {
                    done.run();
                }
                return null;
            }
            final long start = System.nanoTime();
            final javax.swing.Timer timer = new javax.swing.Timer(16, null);
            timer.addActionListener(e -> {
                double p = Math.min(1.0, (System.nanoTime() - start) / (durationMs * 1e6));
                double q = 1 - p;
                frame.accept(1 - q * q * q); // fast start, gentle finish
                if (p >= 1.0) {
                    timer.stop();
                    if (done != null) {
                        done.run();
                    }
                }
            });
            frame.accept(0.0);
            timer.start();
            return timer;
        }

        static Color mix(Color a, Color b, double t) {
            int r = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t);
            int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
            int bl = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t);
            return new Color(r, g, bl);
        }
    }

    /** A page that fades in when it is shown. */
    private static final class FadePanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private float alpha = 1f;
        private javax.swing.Timer timer;

        FadePanel(LayoutManager layout) {
            super(layout);
            setBackground(BG);
        }

        void fadeIn() {
            if (timer != null) {
                timer.stop();
            }
            timer = Anim.run(280, t -> {
                alpha = (float) t;
                repaint();
            }, null);
        }

        @Override
        public void paint(Graphics g) {
            if (alpha >= 1f) {
                super.paint(g);
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, alpha)));
            super.paint(g2);
            g2.dispose();
        }
    }

    /** The thin loading strip under the header: a light slides along it while AEGIS is working. */
    private static final class Strip extends JComponent {
        private static final long serialVersionUID = 1L;
        private int active;
        private double phase;
        private final javax.swing.Timer timer = new javax.swing.Timer(16, e -> {
            phase = (phase + 0.014) % 1.4;
            repaint();
        });

        Strip() {
            setPreferredSize(new Dimension(10, 4));
        }

        void work(int delta) {
            active = Math.max(0, active + delta);
            if (active > 0) {
                timer.start();
            } else {
                timer.stop();
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setColor(NAVY);
            g.fillRect(0, 0, getWidth(), getHeight());
            if (active > 0) {
                int w = getWidth();
                int seg = (int) (w * 0.3);
                int x = (int) ((phase - 0.3) * w);
                g.setPaint(new GradientPaint(x, 0, new Color(100, 181, 246, 0), x + seg, 0, ACCENT));
                g.fillRect(x, 0, seg, getHeight());
            }
            g.dispose();
        }
    }

    /** A flat progress bar that grows smoothly to its value. */
    private static final class Bar extends JComponent {
        private static final long serialVersionUID = 1L;
        private double fraction;
        private Color color = BLUE;
        private javax.swing.Timer timer;

        Bar() {
            setPreferredSize(new Dimension(200, 18));
        }

        void animateTo(double target, Color newColor) {
            final double from = fraction;
            final double to = Math.max(0, Math.min(1, target));
            if (timer != null) {
                timer.stop();
            }
            color = newColor;
            timer = Anim.run(550, t -> {
                fraction = from + (to - from) * t;
                repaint();
            }, null);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth() - 1;
            int h = getHeight() - 1;
            g.setColor(NEUTRAL_BG);
            g.fillRoundRect(0, 0, w, h, 8, 8);
            if (fraction > 0.0005) {
                g.setColor(color);
                g.fillRoundRect(0, 0, Math.max(6, (int) Math.round(w * fraction)), h, 8, 8);
            }
            g.setColor(LINE);
            g.drawRoundRect(0, 0, w, h, 8, 8);
            g.dispose();
        }
    }

    /** A flat, rounded button (blue when primary). Drawn by hand so it looks the same everywhere. */
    private static final class Btn extends JButton {
        private static final long serialVersionUID = 1L;
        private final boolean primary;
        private boolean hover;
        private boolean down;

        Btn(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setFont(getFont().deriveFont(Font.BOLD, 13f));
            setBorder(BorderFactory.createEmptyBorder(7, 16, 7, 16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    down = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    down = true;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    down = false;
                    repaint();
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            Insets in = getInsets();
            return new Dimension(fm.stringWidth(getText()) + in.left + in.right, fm.getHeight() + in.top + in.bottom);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean on = isEnabled();
            int w = getWidth() - 1;
            int h = getHeight() - 1;
            Color fill;
            Color fg;
            if (primary) {
                fill = !on ? new Color(0xB4C3D1) : down ? new Color(0x0D47A1) : hover ? new Color(0x1976D2) : BLUE;
                fg = Color.WHITE;
            } else {
                fill = !on ? new Color(0xF3F5F7) : down ? new Color(0xD9E4EF) : hover ? new Color(0xEAF1F8) : Color.WHITE;
                fg = on ? TEXT : new Color(0xA0A8B2);
            }
            g.setColor(fill);
            g.fillRoundRect(0, 0, w, h, 10, 10);
            if (!primary) {
                g.setColor(LINE);
                g.drawRoundRect(0, 0, w, h, 10, 10);
            }
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            g.setColor(fg);
            g.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
                (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    /** A page button in the header: plain text with an underline under the current page. */
    private static final class NavBtn extends JButton {
        private static final long serialVersionUID = 1L;
        final String page;
        private boolean active;
        private boolean hover;

        NavBtn(String text, String page) {
            super(text);
            this.page = page;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setFont(getFont().deriveFont(Font.BOLD, 13f));
            setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        void setActive(boolean on) {
            active = on;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            Insets in = getInsets();
            return new Dimension(fm.stringWidth(getText()) + in.left + in.right, fm.getHeight() + in.top + in.bottom);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            g.setColor(active || hover ? Color.WHITE : new Color(0xA9BFD6));
            g.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2, getInsets().top + fm.getAscent());
            if (active) {
                g.setColor(ACCENT);
                g.fillRect(8, getHeight() - 3, getWidth() - 16, 3);
            }
            g.dispose();
        }
    }

    /** A small blue circle with a number in it. */
    private static final class Badge extends JComponent {
        private static final long serialVersionUID = 1L;
        private final int number;

        Badge(int number) {
            this.number = number;
            setPreferredSize(new Dimension(26, 26));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(BLUE);
            g.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
            g.setColor(Color.WHITE);
            g.setFont(getFont().deriveFont(Font.BOLD, 13f));
            FontMetrics fm = g.getFontMetrics();
            String s = String.valueOf(number);
            g.drawString(s, (getWidth() - fm.stringWidth(s)) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    /** Colors the answers in the tables: green for MATCH, red for NO MATCH / GAVE UP. */
    private static final class CellStyle extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus,
                                                       int row, int col) {
            super.getTableCellRendererComponent(t, v, false, false, row, col);
            String s = v == null ? "" : v.toString();
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            setFont(t.getFont());
            setForeground(TEXT);
            if (s.equals("MATCH")) {
                setForeground(GREEN);
                setFont(t.getFont().deriveFont(Font.BOLD));
            } else if (s.equals("NO MATCH") || s.startsWith("GAVE UP")) {
                setForeground(RED);
                setFont(t.getFont().deriveFont(Font.BOLD));
            } else if (s.startsWith("(not tried)")) {
                setForeground(MUTED);
            }
            return this;
        }
    }

    private static void styleTable(JTable table) {
        table.setRowHeight(28);
        table.setEnabled(false);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(new Color(0xE6EAF0));
        table.setBackground(Color.WHITE);
        table.setDefaultRenderer(Object.class, new CellStyle());
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setBackground(NEUTRAL_BG);
        table.getTableHeader().setFont(table.getFont().deriveFont(Font.BOLD));
        if (table.getTableHeader().getDefaultRenderer() instanceof DefaultTableCellRenderer r) {
            r.setHorizontalAlignment(SwingConstants.LEFT);
        }
    }

    private static JScrollPane scroll(JComponent view) {
        JScrollPane s = new JScrollPane(view);
        s.setBorder(BorderFactory.createLineBorder(LINE));
        s.getViewport().setBackground(Color.WHITE);
        if (view instanceof JTable t) {
            s.setColumnHeaderView(t.getTableHeader());
        }
        return s;
    }

    private static JPanel card(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),
            BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        return p;
    }

    private static JLabel heading(String s) {
        JLabel l = new JLabel(s);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 14f));
        return l;
    }

    private static JLabel muted(String s) {
        JLabel l = new JLabel(s);
        l.setForeground(MUTED);
        return l;
    }

    private static JLabel label(String s) {
        JLabel l = new JLabel(s);
        l.setForeground(MUTED);
        return l;
    }

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static JTextArea text(String s) {
        JTextArea a = new JTextArea(s);
        a.setEditable(false);
        a.setFocusable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setOpaque(false);
        a.setForeground(TEXT);
        a.setBorder(null);
        return a;
    }

    private static GridBagConstraints cell(int x, int y, double weightX, int fill) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.weightx = weightX;
        c.fill = fill;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(3, 4, 3, 8);
        return c;
    }

    private static javax.swing.event.DocumentListener onChange(Runnable r) {
        return new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                r.run();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                r.run();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                r.run();
            }
        };
    }

    private static Action action(Runnable r) {
        return new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        };
    }

    private static String indent(String block) {
        StringBuilder sb = new StringBuilder();
        for (String line : block.split("\n")) {
            sb.append("  ").append(line).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private static String fmt(long n) {
        return String.format("%,d", n);
    }

    private static String ms(double ms) {
        return ms < 0.1 ? "under 0.1 ms" : String.format("%.1f ms", ms);
    }
}
