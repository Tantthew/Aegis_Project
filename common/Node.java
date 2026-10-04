package common;

import java.util.List;

/**
 * Abstract syntax tree of a regular expression. Immutable, so the same node can be shared
 * (for example when {n,m} is expanded into repeated copies of its operand).
 *
 * Both engines in this prototype start from exactly this tree:
 *  - AEGIS turns it into an NFA and simulates the NFA.
 *  - The baseline engine walks it directly and backtracks.
 */
public sealed interface Node permits Node.Empty, Node.Lit, Node.Cat, Node.Alt, Node.Star, Node.Plus, Node.Opt {

    /** Matches the empty string. */
    record Empty() implements Node {}

    /** Matches exactly one character from a set. */
    record Lit(CharSet set) implements Node {}

    /** Concatenation: parts in order. */
    record Cat(List<Node> parts) implements Node {}

    /** Alternation: any one of the options. */
    record Alt(List<Node> options) implements Node {}

    /** Zero or more. */
    record Star(Node body) implements Node {}

    /** One or more. */
    record Plus(Node body) implements Node {}

    /** Zero or one. */
    record Opt(Node body) implements Node {}
}
