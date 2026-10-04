package aegis;

import common.Node;
import common.RegexSyntaxException;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 3 of AEGIS: AST -> NFA (Thompson's construction).
 *
 * The tree is converted from the back to the front. build(node, next) returns the entry state of an
 * automaton for 'node' whose exit leads to 'next'. This avoids patch lists and keeps each rule to a
 * few lines:
 *
 *   Lit      : one CHAR state pointing at next
 *   Cat      : build the last part first, then each earlier part pointing at the one built after it
 *   Alt      : SPLIT states whose branches each end at next
 *   Opt      : SPLIT -> (body | next)
 *   Star     : SPLIT -> (body | next), and the body loops back to the SPLIT
 *   Plus     : like Star, but the entry is the body, so it must run at least once
 *
 * The NFA has at most a few states per pattern symbol, which is what makes matching O(m * n).
 */
public final class NfaBuilder {
    private static final int MAX_STATES = 500_000;

    private final List<Nfa.State> states = new ArrayList<>();

    private NfaBuilder() {}

    public static Nfa build(Node ast) {
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
            split.out1 = build(s.body(), split); // body loops back to the split
            split.out2 = next;
            return split;
        }
        if (n instanceof Node.Plus p) {
            Nfa.State split = newState(Nfa.SPLIT);
            Nfa.State body = build(p.body(), split); // after the body, choose: again or leave
            split.out1 = body;
            split.out2 = next;
            return body;
        }
        throw new IllegalStateException("Unknown node " + n);
    }
}
