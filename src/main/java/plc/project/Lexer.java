package plc.project;

import java.util.ArrayList;
import java.util.List;

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
    public List<Token> lex() { //TODO

        ArrayList<Token> tokenList = new ArrayList<Token>();
        while(chars.has(0)) {
            if (match("[ \b\n\r\t]")) {
                chars.skip();
            }
            else {
                tokenList.add(lexToken());
            }
        }

        return tokenList;
    }

    /**
     * This method determines the type of the next token, delegating to the
     * appropriate lex method. As such, it is best for this method to not change
     * the state of the char stream (thus, use peek not match).
     *
     * The next character should start a valid token since whitespace is handled
     * by {@link #lex()}
     */
    public Token lexToken() { //TODO
        if (peek("@|[A-Za-z]")) {
            return lexIdentifier();
        }
        else if (peek("[0-9]") || peek("-", "[1-9]") || peek("-", "0", ".", "[0-9]")) {
            return lexNumber();
        }
        else if (peek("\'")) {
            return lexCharacter();
        }
        else if (peek("\"")) {
            return lexString();
        }
        else {
            return lexOperator();
        }
    }

    public Token lexIdentifier() {
        if (match("[A-Za-z]") || match("@", "[A-Za-z0-9_-]")) {
            while (match("[A-Za-z0-9_-]"));
        }

        return chars.emit(Token.Type.IDENTIFIER);
    }

    public Token lexNumber() {
        if (match("-")) {   //handles - int/dec
            if (match("0", ".")) {  //case where peek("-", "0", ".", "[0-9]") returns T
                while (match("[0-9]"));
                return chars.emit(Token.Type.DECIMAL);
            }
            else if (match("[1-9]")) {  //case where peek("-", "[1-9]") returns T
                while (match("[0-9]"));

                if (match(".", "[0-9]")) {  //need to check if decimal can be valid
                    while (match("[0-9]"));
                    return chars.emit(Token.Type.DECIMAL);
                }
                else {
                    return chars.emit(Token.Type.INTEGER);
                }
            }
        }
        else if (match("[1-9]")) {  //case where peek("[1-9]") returns T; handles + int/dec
            while (match("[0-9]"));

            if (match(".", "[0-9]")) {  //need to check if decimal can be valid
                while (match("[0-9]"));
                return chars.emit(Token.Type.DECIMAL);
            }
            else {
                return chars.emit(Token.Type.INTEGER);
            }
        }
        else {  //case where peek("0") returns T
            if (match("0", ".", "[0-9]")) {  //need to check if decimal can be valid
                while (match("[0-9]"));
                return chars.emit(Token.Type.DECIMAL);
            }
        }

        //last case: just have 0 bc java stupid
        chars.advance();
        return chars.emit(Token.Type.INTEGER);
    }

    public Token lexCharacter() {
        if (match("\'")) {
            if (match("[^\'\n\r\\\\]")) {
                if (match("\'")) {
                    return chars.emit(Token.Type.CHARACTER);
                }
                else {
                    throw new ParseException("Unterminated character at index: ", chars.index);
                }
            }
            else if (peek("\\\\")) {
                lexEscape();
                if (match("\'")) {
                    return chars.emit(Token.Type.CHARACTER);
                }
                else {
                    throw new ParseException("Unterminated character at index: ", chars.index);
                }
            }
        }

        throw new ParseException("Invalid character at index: ", chars.index);
    }

    //problem: length is too big for unterminated strings
    public Token lexString() {
        if (match("\"")) {
            while(match("[^\"\n\r\\\\]") || peek("\\\\")) {
                lexEscape();
            };

            if (match("\"")) {
                return chars.emit(Token.Type.STRING);
            }
        }

        throw new ParseException("Unterminated string at index: ", chars.index);
    }

    //call inside lexString and lexChar
    //don't call in lexToken
    //matching on \\n
    //ex: "\a" //error on index of 'a'
    public void lexEscape() {
        //only peek in else if in lexChar, then call lexEscape
        if (match("\\\\")) {
            if (!match( "[bnrt\'\"\\\\]")) {
                throw new ParseException("Invalid escape sequence at index: ", chars.index);
            }
        }
    }

    public Token lexOperator() {
        if (match("[!=]", "=") || match("&", "&") || match("|", "|")) {
            return chars.emit(Token.Type.OPERATOR);
        }
        else {
            chars.advance();
            return chars.emit(Token.Type.OPERATOR);
        }
    }

    /**
     * Returns true if the next sequence of characters match the given patterns,
     * which should be a regex. For example, {@code peek("a", "b", "c")} would
     * return true if the next characters are {@code 'a', 'b', 'c'}.
     */
    public boolean peek(String... patterns) { //TODO
        for (int i = 0; i < patterns.length; i++) {
            if (!chars.has(i) || !String.valueOf(chars.get(i)).matches(patterns[i])) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns true in the same way as {@link #peek(String...)}, but also
     * advances the character stream past all matched characters if peek returns
     * true. Hint - it's easiest to have this method simply call peek.
     */
    public boolean match(String... patterns) { //TODO
        boolean peek = peek(patterns);
        if (peek) {
            for (int i =0; i < patterns.length; i++) {
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
