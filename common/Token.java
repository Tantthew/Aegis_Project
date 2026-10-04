package common;

/** One lexical unit of a pattern. Produced by {@link Lexer}, consumed by {@link Parser}. */
public final class Token {
    public enum Type { CHAR, LPAREN, RPAREN, PIPE, STAR, PLUS, QMARK, REPEAT, END }

    public final Type type;
    public final CharSet set; // only for CHAR
    public final int min;     // only for REPEAT
    public final int max;     // only for REPEAT, -1 means unbounded
    public final int pos;     // index in the pattern, for error messages

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
