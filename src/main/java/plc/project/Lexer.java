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
     * <p>
     * The next character should start a valid token since whitespace is handled
     * by {@link #lex()}
     */
    public Token lexToken() { //TODO
        if (peek("@|[A-Za-z]")) {
            return lexIdentifier();
        }
        else if (peek("\'")) {
            return lexCharacter();
        }
        else {
            throw new ParseException("Unsupported character at line: ", chars.index);
        }
    }


    public Token lexIdentifier() {
//        identifier ::= '@'? [A-Za-z] [A-Za-z0-9_-]*
        if (match("@|[A-Za-z]")) {
            while (match("[A-Za-z0-9_-]"));
        }

        return chars.emit(Token.Type.IDENTIFIER);
    }


    public Token lexNumber() {
//        integer ::= '0' | '-'? [1-9] [0-9]*
//        decimal ::= '-'? ('0' | [1-9] [0-9]*) '.' [0-9]+ *Note this is correct unlike the project specifications*

        /*Checking if first number is 0*/
        if (match("0")){
            /*Integer*/
            //Check if Leading Zero, if so break because it's not supposed to fail
            zeroErrorCheck:
            {
                if (peek("[1-9]")) {
//                  throw new ParseException("No leading zeros on integers", chars.index);
                    break zeroErrorCheck;
                }
                /*Decimal*/
                //What do you do if there is a decimal but then there isn't any numbers after (NOTE: DO NOT FAIL)
                else if (peek("[.]")) {
                    //What if next character isn't a number? Should an exception be thrown?
                    if(peek("[.]", "[0-9]")){
                        while (match("[0-9]")){}
                        return chars.emit(Token.Type.DECIMAL);
                    }
                    else{
                        break zeroErrorCheck;
                    }
                }
                //This should only return if the number is just a 0
                return chars.emit(Token.Type.INTEGER);
            }
            System.out.println("This should never be printed. If so then there is an error inside lexToken with lexNumber being called when it shouldn't. Error with FIRST DIGIT ZERO IF STATEMENT");
        }

        /*Check to see if first character is a negative*/
        else if (match("[-]")){

            //If first number is not zero...
            if (match("[1-9]")){
                while (match("0-9")){}

                //Check if decimal
                if (peek("[.]")){
                    match("[.]");
                    do {
                        match("[0-9]");
                    }while(match("[0-9]"));

                    return chars.emit(Token.Type.DECIMAL);
                }
                else {
                    return chars.emit(Token.Type.INTEGER);
                }
            }

            //Therefore, first number (not character since that is the negative sign) must be 0 and a decimal because a negative 0 integer doesn't exist
            else {
                match("0", "[.]");
                do{
                    match("[0-9]");
                }while(match("[0-9]"));

                return chars.emit(Token.Type.DECIMAL);
            }

        }
//        integer ::= '0' | '-'? [1-9] [0-9]*
//        decimal ::= '-'? ('0' | [1-9] [0-9]*) '.' [0-9]+
        /*Positive Integers & Decimals (Should be last case)*/
        else if(match("[1-9]")){
            while(match("[0-9]")){
                //If number is a decimal then record decimal and keep iterating through number
                if(peek("[.]")){
                    match("[.]");
                    do{
                        match("[0-9]");
                    }while(match("[0-9]"));
                    return chars.emit(Token.Type.DECIMAL);
                }
            }
            return chars.emit(Token.Type.INTEGER);
        }

        //This should never get thrown. If it does then there is an error inside lexToken with lexNumber being called when it shouldn't
        throw new ParseException("This should never be printed. If so then there is an error inside lexToken with lexNumber being called when it shouldn't. Error with THE IF STATEMENT IS NOT BEING TRIGGERED: ", chars.index);
    }
    /**
    if peek = "0"
      if peek = "." {
            check for decimal
      else if
          check for integer
    else if peek = "-"
        if peek = 0
            if peek = "."
                check for decimal
*/


    public Token lexCharacter() {
        if (match("\'")) {
            if (match("[^\'\n\r\\\\]")) {
                if (match("\'")) {
                    return chars.emit(Token.Type.CHARACTER);
                }
            }
            else if (peek("\\\\", "[bnrt\'\"\\\\]")) {
                lexEscape();
                if (match("\'")) {
                    return chars.emit(Token.Type.CHARACTER);
                }
            }
        }

        throw new ParseException("Unterminated character at index: ", chars.index);
    }


    public Token lexString() {
        throw new UnsupportedOperationException(); //TODO
    }


    //call inside lexString and lexChar
    //don't call in lexToken
    //matching on \\n
    public void lexEscape() {
        match("\\\\", "[bnrt\'\"\\\\]");
    }

    public Token lexOperator() {
        throw new UnsupportedOperationException(); //TODO
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
