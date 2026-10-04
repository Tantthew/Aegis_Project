package common;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 2: recursive-descent parser. Turns tokens into a {@link Node} tree.
 *
 * Grammar (lowest to highest precedence):
 *   regex  := cat ( '|' cat )*
 *   cat    := repeat*                 (may be empty)
 *   repeat := atom quantifier?
 *   atom   := CHAR | '(' regex ')'
 *
 * Only one quantifier is allowed per atom. Stacked quantifiers such as a** or a+? (which in many
 * engines mean "lazy" or "possessive") are rejected so the meaning is never silently different.
 */
public final class Parser {
    private final List<Token> toks;
    private int p = 0;

    private Parser(List<Token> toks) {
        this.toks = toks;
    }

    public static Node parse(String pattern) {
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
