package common;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 1: turns the pattern text into a list of tokens.
 *
 * Supported: literals, '.', escapes (\d \D \w \W \s \S \n \t \r \f and escaped punctuation),
 * character classes [abc] [a-z] [^0-9], groups ( ), alternation |, and the quantifiers
 * * + ? {n} {n,} {n,m}.
 *
 * Not supported (rejected with a clear error): anchors, backreferences, look-around,
 * lazy or possessive quantifiers, capture-group extraction.
 */
public final class Lexer {
    public static final int MAX_REPEAT = 1000;

    private Lexer() {}

    public static List<Token> tokenize(String p) {
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
                case '[' -> i = lexClass(p, i, out);
                case '\\' -> i = lexEscape(p, i, out);
                case '{' -> i = lexRepeat(p, i, out);
                default -> { out.add(Token.chars(CharSet.single(c), i)); i++; }
            }
        }
        out.add(Token.of(Token.Type.END, n));
        return out;
    }

    // ---- escapes -------------------------------------------------------------------------

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

    // ---- character classes ---------------------------------------------------------------

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
            // first end of a possible range, or a class escape such as \d
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

    // ---- {n}, {n,}, {n,m} ----------------------------------------------------------------

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

    /** Reads a run of digits; returns {value, indexAfterDigits} or null if there is no digit. */
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
