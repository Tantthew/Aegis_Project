package common;

/** Thrown by the lexer or parser when a pattern is malformed or uses an unsupported feature. */
public class RegexSyntaxException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int position;

    public RegexSyntaxException(String message, int position) {
        super(message + " (at index " + position + ")");
        this.position = position;
    }

    public int getPosition() {
        return position;
    }
}
