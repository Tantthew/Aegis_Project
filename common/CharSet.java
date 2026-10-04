package common;

/**
 * A set of characters that one input symbol can be matched against.
 * Stored as inclusive ranges plus an optional negation flag, so '.', [a-z], [^0-9],
 * \d, \w and \s are all the same kind of object.
 */
public final class CharSet {
    private final int[] ranges; // lo0, hi0, lo1, hi1, ...
    private final boolean negated;
    private final String label;

    private CharSet(int[] ranges, boolean negated, String label) {
        this.ranges = ranges;
        this.negated = negated;
        this.label = label;
    }

    public static CharSet single(char c) {
        return new CharSet(new int[] {c, c}, false, quote(c));
    }

    /** '.' : any character except line terminators (same default as java.util.regex). */
    public static CharSet dot() {
        return new CharSet(new int[] {10, 10, 13, 13, 0x85, 0x85, 0x2028, 0x2029}, true, ".");
    }

    public static CharSet digit() {
        return new CharSet(new int[] {'0', '9'}, false, "\\d");
    }

    public static CharSet word() {
        return new CharSet(new int[] {'a', 'z', 'A', 'Z', '0', '9', '_', '_'}, false, "\\w");
    }

    public static CharSet space() {
        return new CharSet(new int[] {' ', ' ', 9, 13}, false, "\\s");
    }

    public static CharSet ofRanges(int[] ranges, boolean negated, String label) {
        return new CharSet(ranges.clone(), negated, label);
    }

    public CharSet negate(String newLabel) {
        return new CharSet(ranges, !negated, newLabel);
    }

    /** The raw ranges, used when \d, \w or \s appear inside a [...] class. */
    public int[] ranges() {
        return ranges.clone();
    }

    public boolean matches(char c) {
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
