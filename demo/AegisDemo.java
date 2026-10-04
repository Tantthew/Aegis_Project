package demo;

import aegis.AegisEngine;
import common.AstPrinter;
import common.Lexer;
import common.RegexSyntaxException;
import common.Token;

import java.util.Arrays;
import java.util.List;

/**
 * Walks through the AEGIS pipeline stage by stage.
 *
 *   java -cp out demo.AegisDemo                       built-in walkthrough
 *   java -cp out demo.AegisDemo "pattern" in1 in2 ... your own pattern and inputs
 */
public final class AegisDemo {
    private AegisDemo() {}

    public static void main(String[] args) {
        if (args.length >= 2) {
            pipeline(args[0], Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        System.out.println("##### AEGIS prototype: regex -> tokens -> AST -> NFA -> linear-time simulation #####\n");
        pipeline("(a|b)*abb", "aabb", "abab");
        pipeline("[a-z]+@\\d{2,3}", "tantan@123", "tantan@1");

        System.out.println("=== Worst-case pattern for backtracking engines ===");
        String evil = "a".repeat(40) + "b";
        AegisEngine e = AegisEngine.compile("(a+)+");
        AegisEngine.Result r = e.matches(evil);
        System.out.println("pattern : (a+)+");
        System.out.println("input   : 40 letters 'a' followed by 'b'  (length " + evil.length() + ")");
        System.out.println("result  : matched=" + r.matched() + ", steps=" + r.steps()
            + ", peak active states=" + r.peakActive() + ", NFA size=" + e.nfa().size());
        System.out.println("A backtracking engine tries about 2^40 ways to split those letters. AEGIS never does:");
        System.out.println("its work is bounded by (NFA size) x (input length) = "
            + e.nfa().size() + " x " + evil.length() + " = " + (e.nfa().size() * evil.length()) + " at most.\n");
    }

    static void pipeline(String pattern, String... inputs) {
        System.out.println("============================================================");
        System.out.println("Pattern: " + pattern);
        System.out.println("============================================================");
        try {
            List<Token> tokens = Lexer.tokenize(pattern);
            System.out.println("[1] Lexer tokens:\n    " + tokens);

            AegisEngine engine = AegisEngine.compile(pattern);
            System.out.println("[2] Parser, abstract syntax tree:");
            System.out.print(indent(AstPrinter.print(engine.ast())));
            System.out.println("[3] NFA (" + engine.nfa().size() + " states, Thompson construction):");
            System.out.print(engine.nfa().describe());

            for (String input : inputs) {
                System.out.println("[4] Simulating input \"" + input + "\":");
                for (String line : engine.trace(input)) {
                    System.out.println("    " + line);
                }
                AegisEngine.Result r = engine.matches(input);
                System.out.println("    => " + (r.matched() ? "MATCH" : "NO MATCH")
                    + "  (steps=" + r.steps() + ", peak active states=" + r.peakActive() + ")\n");
            }
        } catch (RegexSyntaxException ex) {
            System.out.println("Syntax error: " + ex.getMessage() + "\n");
        }
    }

    private static String indent(String s) {
        StringBuilder sb = new StringBuilder();
        for (String line : s.split("\n")) {
            sb.append("    ").append(line).append('\n');
        }
        return sb.toString();
    }
}
