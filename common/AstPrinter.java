package common;

/** Draws an AST as an indented tree, handy for showing the parser stage during a presentation. */
public final class AstPrinter {
    private AstPrinter() {}

    public static String print(Node root) {
        StringBuilder sb = new StringBuilder();
        walk(root, 0, sb);
        return sb.toString();
    }

    private static void walk(Node n, int depth, StringBuilder sb) {
        String pad = "  ".repeat(depth);
        if (n instanceof Node.Empty) {
            sb.append(pad).append("Empty\n");
        } else if (n instanceof Node.Lit l) {
            sb.append(pad).append("Lit ").append(l.set()).append('\n');
        } else if (n instanceof Node.Cat c) {
            sb.append(pad).append("Concat\n");
            for (Node part : c.parts()) {
                walk(part, depth + 1, sb);
            }
        } else if (n instanceof Node.Alt a) {
            sb.append(pad).append("Alternation\n");
            for (Node o : a.options()) {
                walk(o, depth + 1, sb);
            }
        } else if (n instanceof Node.Star s) {
            sb.append(pad).append("Star (zero or more)\n");
            walk(s.body(), depth + 1, sb);
        } else if (n instanceof Node.Plus p) {
            sb.append(pad).append("Plus (one or more)\n");
            walk(p.body(), depth + 1, sb);
        } else if (n instanceof Node.Opt o) {
            sb.append(pad).append("Optional (zero or one)\n");
            walk(o.body(), depth + 1, sb);
        }
    }
}
