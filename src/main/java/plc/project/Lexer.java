package plc.project;

import java.util.List;
import java.util.ArrayList;
/**
 * The lexer works through three main functions:
 *
 *  - {@link #lex()}, which repeatedly calls lexToken() and skips whitespace
 *  - {@link #lexToken()}, which lexes the next token
 *  - {@link CharStream}, which manages the state of the lexer and literals
 *
 * If the lexer fails to parse something (such as an unterminated string) you
 * should throw a {@link ParseException} with an index at the character which is
 * invalid.
 *
 * The {@link #peek(String...)} and {@link #match(String...)} functions are * helpers you need to use, they will make the implementation a lot easier. */
public final class Lexer {

    private final CharStream chars;

    public Lexer(String input) {
        chars = new CharStream(input);
    }

    /**
     * Repeatedly lexes the input using {@link #lexToken()}, also skipping over
     * whitespace where appropriate.
     */
    public List<Token> lex() {
        List<Token> tokens = new ArrayList<>();
        while (chars.has( 0)) {
            if (match( "[ \\n\\r\\t]")) chars.skip();
            tokens.add(lexToken());
        }
        return tokens;
    }

    /**
     * This method determines the type of the next token, delegating to the
     * appropriate lex method. As such, it is best for this method to not change
     * the state of the char stream (thus, use peek not match).
     *
     * The next character should start a valid token since whitespace is handled
     * by {@link #lex()}
     */
    public Token lexToken() {
        if (peek("[a-zA-Z@]")) {
            return lexIdentifier();
        } else if (peek("[-0-9]")) {
            return lexNumber();
        } else if (peek("'")) {
            return lexCharacter();
        } else if (peek("\"")) {
            return lexString();
        } else {
            return lexOperator();
        }
    }

    public Token lexIdentifier() {
        if (peek("@")) match("@");
        if (peek("[a-zA-Z]")) {
            while (match("[a-zA-Z0-9_-]"));
            return chars.emit(Token.Type.IDENTIFIER);
        }
        throw new ParseException("Expected identifier", chars.index);
    }

    public Token lexNumber() {
        boolean isDecimal = false;
        // Negatives
        if (match("-")) {
            // Check if Negative Decimal or Non-zero
            // JAVA IS SHORT CIRCUITING MEANING THE REMAINING STATEMENTS WON'T EVALUATE IF THE PRECEDING IS/ARE TRUE
            if (peek("0\\.") || peek("[1-9]") || chars.index == chars.length) {
                // all good!
            }
            else {
                throw new ParseException("invalid", chars.index);
            }
            if (match("0\\.")) isDecimal = true;
        }
        // Leading Zeros
        else if (peek("0")) {
            // Only char that can follow a 0 is a decimal [UNLESS IT'S JUST A ZERO]
            if (peek("[ \\n\\r\\t]") || peek("0\\.") || chars.index == chars.length) {
                // all good
            }
            else {
                throw new ParseException("invalid", chars.index);
            }
            if (match("0\\.")) isDecimal = true;
        }
        // All Other Cases
        if (match("[1-9]") || isDecimal) {
            while (match("[0-9]"));
            // Stopped at end or whitespace
            if (peek("[ \\n\\r\\t]") || chars.index == chars.length) {
                // all good!
            }
            else {
                throw new ParseException("invalid", chars.index);
            }
        }
        // No Valid Input
        else {
            throw new ParseException("invalid", chars.index);
        }
        /*
        match("-"); // Match an optional leading minus
        // Check for leading zero which could lead to a decimal
        if (match("0")) {
            if (match("\\.")) { // It's a decimal if there's a dot following the zero
                if (!peek("[0-9]")) {
                    throw new ParseException("Expected digit after decimal point", chars.index);
                }
                isDecimal = true; // Mark as decimal since we found a dot
            } else if (peek("[0-9]")) {
                // If there's another digit after 0, it's an error
                throw new ParseException("Leading zeros are not allowed", chars.index);
            }
        } else if (match("[1-9]")) {
            while (match("[0-9]")); // Match the rest of any digits
        } else {
            // No valid start for a number
            throw new ParseException("Expected digit", chars.index);
        }

        // Handle the decimal part after ensuring the integer part is correct
        if (peek("\\.")) {
            match("\\.");
            if (!peek("[0-9]")) {
                throw new ParseException("Expected digit after decimal point", chars.index);
            }
            while (peek("[0-9]")) match("[0-9]");
            isDecimal = true; // Confirm it's a decimal after matching the fractional part
        }
        */
        // Emit the appropriate token type based on whether a decimal point was part of the number
        if (isDecimal) {
            return chars.emit(Token.Type.DECIMAL);
        } else {
            return chars.emit(Token.Type.INTEGER);
        }
    }

    public Token lexCharacter() {
        match("'");
        if (peek("\\\\")) { // Start of an escape sequence
            lexEscape(); // Handle the escape sequence
        } else if (!match("[^']")) { // Match any character except a single quote
            throw new ParseException("Invalid character literal", chars.index);
        }
        if (!match("'")) { // Ensure the character literal is properly closed
            throw new ParseException("Unterminated character literal", chars.index);
        }
        return chars.emit(Token.Type.CHARACTER);
    }

    public Token lexString() {
        match("\"");
        while (!peek("\"")) { // Process until the closing double quote
            if (peek("\\\\")) { // Start of an escape sequence
                lexEscape(); // Handle the escape sequence
            } else if (!match("[^\"\\\\]")) { // Match any character except double quote or backslash
                throw new ParseException("Invalid string literal", chars.index);
            }
            if (!chars.has(0)) { // Check if end of input is reached without closing quote
                throw new ParseException("Unterminated string literal", chars.index);
            }
        }
        match("\""); // Consume the closing double quote
        return chars.emit(Token.Type.STRING);
    }

    public void lexEscape() {
        match("\\\\"); // Match the leading backslash of the escape sequence.
        if (!match("[bnrt'\"\\\\]")) { // Match valid escape characters.
            throw new ParseException("Invalid escape sequence", chars.index);
        }
    }

    public Token lexOperator() {
        // First, handle compound operators explicitly to ensure they are matched correctly
        if (peek("!=") || peek("==")) {
            if (peek("!=")) {
                match("!=");
            } else if (peek("==")) {
                match("==");
            }
            return chars.emit(Token.Type.OPERATOR);
        }
        // Include handling for semicolon and single character operators
        if (peek("[;!=><+\\-*/%()]")) {
            match("[;!=><+\\-*/%()]");
            // Special handling for '=' to ensure it's not part of a compound operator missed earlier
            if (peek("=")) {
                // This should not happen due to the initial check for compound operators, but it's here for safety
                match("=");
            }
            return chars.emit(Token.Type.OPERATOR);
        } else {
            throw new ParseException("Expected operator", chars.index);
        }
    }

    /**
     * Returns true if the next sequence of characters match the given patterns,
     * which should be a regex. For example, {@code peek("a", "b", "c")} would
     * return true if the next characters are {@code 'a', 'b', 'c'}.
     */
    public boolean peek(String... patterns) {
        for(int i=0; i<patterns.length; i++){
            if (!chars.has(i) || !String.valueOf(chars.get(i)).matches(patterns[i])){
                return false;
            }
            // System.out.println(chars.get(i));
        }
        return true;
    }

    /**
     * Returns true in the same way as {@link #peek(String...)}, but also
     * advances the character stream past all matched characters if peek returns
     * true. Hint - it's easiest to have this method simply call peek.
     */
    public boolean match(String... patterns) {
       boolean peek = peek(patterns);
       if (peek) {
           for(int i=0; i < patterns.length; i++){
               chars.advance();
           }
       }
       return peek;
    }

    /**
     * A helper class maintaining the input string, current index of the char
     * stream, and the current length of the token being matched.
     *
     * You should rely on peek/match for state management in nearly all cases.
     * The only field you need to access is {@link #index} for any {@link
     * ParseException} which is thrown.
     */
    public static final class CharStream {

        private final String input;
        private int index = 0;
        private int length = 0;

        public CharStream(String input) {
            this.input = input;
        }

        public boolean has(int offset) {
            return index + offset < input.length();
        }

        public char get(int offset) {
         //   System.out.println(input.charAt(index + offset));
            return input.charAt(index + offset);
        }

        public void advance() {
            index++;
            length++;
        }

        public void skip() {
            length = 0;
        }

        public Token emit(Token.Type type) {
            int start = index - length;
            skip();
            return new Token(type, input.substring(start, index), start);
        }

    }

}
