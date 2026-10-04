package demo;

import aegis.AegisEngine;
import baseline.BacktrackingEngine;

import java.util.List;

/**
 * Example security application: a small rule-based input filter (validation rules plus a scan for suspicious text).
 * Every rule is run through BOTH engines so the difference is visible when a hostile input arrives.
 *
 *   java -cp out demo.FilterDemo
 */
public final class FilterDemo {
    private FilterDemo() {}

    /** @param search true = flag if the pattern appears anywhere, false = the whole input must match */
    private record Rule(String name, String regex, boolean search, String meaning) {}

    private static final long BASELINE_LIMIT = 5_000_000L;

    public static void main(String[] args) throws InterruptedException {
        Thread t = new Thread(null, FilterDemo::run, "filter-demo", 1L << 29);
        t.start();
        t.join();
    }

    private static void run() {
        List<Rule> rules = List.of(
            new Rule("username", "[a-zA-Z0-9_]{3,16}", false, "valid when the whole input matches"),
            new Rule("email", "[a-zA-Z0-9._]+@[a-zA-Z0-9]+\\.(com|org|net|ph)", false, "valid when the whole input matches"),
            new Rule("sql-scan", "(DROP|DELETE)\\s+TABLE|UNION\\s+SELECT|'\\s*OR\\s+1\\s*=\\s*1", true, "suspicious when found anywhere"),
            // A rule of the kind that real code often contains: a nested quantifier before a fixed suffix.
            new Rule("legacy-corp-id", "([a-zA-Z0-9]+)*@corp\\.example", false, "valid when the whole input matches (vulnerable shape)"));

        String[][] inputs = {
            {"username", "tantan_01"},
            {"username", "x"},
            {"username", "bad name!"},
            {"email", "student@school.ph"},
            {"email", "student@school"},
            {"sql-scan", "SELECT name FROM users WHERE id = 5"},
            {"sql-scan", "5; DROP   TABLE users"},
            {"sql-scan", "x' OR 1=1 --"},
            {"legacy-corp-id", "admin2024@corp.example"},
            {"legacy-corp-id", "a".repeat(30) + "!"}, // hostile: no '@', long run of letters
        };

        System.out.println("Rule-based input filter, every rule checked with AEGIS and with the baseline backtracker");
        System.out.println("(baseline step limit " + BASELINE_LIMIT + ")\n");
        for (String[] in : inputs) {
            Rule rule = rules.stream().filter(r -> r.name().equals(in[0])).findFirst().orElseThrow();
            String text = in[1];
            AegisEngine a = AegisEngine.compile(rule.regex());
            BacktrackingEngine b = BacktrackingEngine.compile(rule.regex(), BASELINE_LIMIT);

            AegisEngine.Result ar = rule.search() ? a.find(text) : a.matches(text);
            BacktrackingEngine.Result br = rule.search() ? b.find(text) : b.matches(text);

            String shown = text.length() > 36 ? text.substring(0, 33) + "...(" + text.length() + " chars)" : text;
            System.out.println("rule '" + rule.name() + "' (" + rule.meaning() + ")");
            System.out.println("  input    : \"" + shown + "\"");
            System.out.println("  verdict  : " + verdict(rule, ar.matched()));
            System.out.println("  AEGIS    : steps=" + ar.steps());
            System.out.println("  baseline : " + BaselineDemo.describe(br));
            System.out.println();
        }
        System.out.println("Last case: the baseline cannot finish a single validation of a 31 character string,");
        System.out.println("while AEGIS answers in well under a thousand steps. A backtracking validator of this shape");
        System.out.println("would tie up a server thread on one such request.");
    }

    private static String verdict(Rule r, boolean matched) {
        if (r.search()) {
            return matched ? "BLOCK (suspicious text found)" : "allow";
        }
        return matched ? "accept (valid)" : "reject (invalid)";
    }
}
