package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 *
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 *
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have it's own function, and reference to other rules correspond
 * to calling that functions.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    /**
     * Parses the {@code source} rule.
     */
    public Ast.Source parseSource() throws ParseException {
        throw new UnsupportedOperationException(); //TODO
    }

    /**
     * Parses the {@code field} rule. This method should only be called if the
     * next tokens start a field, aka {@code LET}.
     */
    public Ast.Global parseGlobal() throws ParseException {
        throw new UnsupportedOperationException(); //TODO
    }

    /**
     * Parses the {@code list} rule. This method should only be called if the
     * next token declares a list, aka {@code LIST}.
     */
    public Ast.Global parseList() throws ParseException {
        throw new UnsupportedOperationException(); //TODO
    }

    /**
     * Parses the {@code mutable} rule. This method should only be called if the
     * next token declares a mutable global variable, aka {@code VAR}.
     */
    public Ast.Global parseMutable() throws ParseException {
        throw new UnsupportedOperationException(); //TODO
    }

    /**
     * Parses the {@code immutable} rule. This method should only be called if the
     * next token declares an immutable global variable, aka {@code VAL}.
     */
    public Ast.Global parseImmutable() throws ParseException {
        throw new UnsupportedOperationException(); //TODO
    }

    /**
     * Parses the {@code method} rule. This method should only be called if the
     * next tokens start a method, aka {@code DEF}.
     */
    public Ast.Function parseFunction() throws ParseException {
        throw new UnsupportedOperationException(); //TODO
    }

    /**
     * Parses the {@code block} rule. This method should only be called if the
     * preceding token indicates the opening a block.
     */
    public List<Ast.Statement> parseBlock() throws ParseException {
        throw new UnsupportedOperationException(); //TODO
    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Statement parseStatement() throws ParseException {

        Ast.Expression receiver = parseExpression();
        if (match("=")) {   //assignment

            Ast.Expression value = parseExpression();
            if (!match(";")) {
                throw new ParseException("Expected semicolon", errorIndex());
            }

            return new Ast.Statement.Assignment(receiver, value);
        }
        else {  //function calls
            if (!match(";")) {
                throw new ParseException("Expected semicolon", errorIndex());
            }

            return new Ast.Statement.Expression(receiver);
        }
    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Statement.Declaration parseDeclarationStatement() throws ParseException {
        throw new UnsupportedOperationException(); //TODO
    }

    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Statement.If parseIfStatement() throws ParseException {
        throw new UnsupportedOperationException(); //TODO
    }

    /**
     * Parses a switch statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a switch statement, aka
     * {@code SWITCH}.
     */
    public Ast.Statement.Switch parseSwitchStatement() throws ParseException {
        throw new UnsupportedOperationException(); //TODO
    }

    /**
     * Parses a case or default statement block from the {@code switch} rule. 
     * This method should only be called if the next tokens start the case or 
     * default block of a switch statement, aka {@code CASE} or {@code DEFAULT}.
     */
    public Ast.Statement.Case parseCaseStatement() throws ParseException {
        throw new UnsupportedOperationException(); //TODO
    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Statement.While parseWhileStatement() throws ParseException {
        throw new UnsupportedOperationException(); //TODO
    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Statement.Return parseReturnStatement() throws ParseException {
        throw new UnsupportedOperationException(); //TODO
    }

    /**
     * Parses the {@code expression} rule.
     */
    public Ast.Expression parseExpression() throws ParseException {
        return parseLogicalExpression();
    }

    /**
     * Parses the {@code logical-expression} rule.
     */
    public Ast.Expression parseLogicalExpression() throws ParseException {
        String operator = "";
        Ast.Expression left = parseComparisonExpression();
        Ast.Expression right;

        while (match("&&") || match("||")) {
            operator = tokens.get(-1).getLiteral();
            right = parseComparisonExpression();
            left = new Ast.Expression.Binary(operator, left, right);
        }

        return left;
    }

    //when you have things of the same priority, move right to left

    /**
     * Parses the {@code equality-expression} rule.
     */
    public Ast.Expression parseComparisonExpression() throws ParseException {
        String operator = "";
        Ast.Expression left = parseAdditiveExpression();
        Ast.Expression right;

        while (match("<") || match(">") || match("==") || match("!=")) {
            operator = tokens.get(-1).getLiteral();
            right = parseAdditiveExpression();
            left = new Ast.Expression.Binary(operator, left, right);
        }

        return left;
    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    public Ast.Expression parseAdditiveExpression() throws ParseException {
        String operator = "";
        Ast.Expression left = parseMultiplicativeExpression();
        Ast.Expression right;

        while (match("+") || match("-")) {
            operator = tokens.get(-1).getLiteral();
            right = parseMultiplicativeExpression();
            left = new Ast.Expression.Binary(operator, left, right);
        }

        return left;
    }

    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expression parseMultiplicativeExpression() throws ParseException {
        String operator = "";
        Ast.Expression left = parsePrimaryExpression();
        Ast.Expression right;

        while (match("*") || match("/") || match("^")) {
            operator = tokens.get(-1).getLiteral();
            right = parsePrimaryExpression();
            left = new Ast.Expression.Binary(operator, left, right);
        }

        return left;
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */
    public Ast.Expression parsePrimaryExpression() throws ParseException {

        if (match("NIL")){
            return new Ast.Expression.Literal(null);
        }
        //if there is a boolean literal
        //have a 'TRUE' identifier, produce boolean true value
        else if (match("TRUE")) {
            return new Ast.Expression.Literal(new Boolean(true));
        }
        else if (match("FALSE")) {
            return new Ast.Expression.Literal(new Boolean(false));
        }
        else if (match(Token.Type.INTEGER)){
            return new Ast.Expression.Literal(new BigInteger(tokens.get(-1).getLiteral()));
        }
        else if (match(Token.Type.DECIMAL)){
            return new Ast.Expression.Literal(new BigDecimal(tokens.get(-1).getLiteral()));
        }
        /*
         Character values are represented with the Character class.
         You will need to remove the surrounding single quotes (') from the literal
         returned by the Lexer and replace any escape characters (hint, see String#replace).
         '\' 'n' different from \n
         "'\\n'" -> "'\n'"
         '\n' -> 'newline char'

         "''"
         "'c'" -> "c"
         "'\n'" -> "
          "

          //"'\\n'"

         NonJava: \n
         Java: '\\n'
         */
        else if(match(Token.Type.CHARACTER)){
            String character = tokens.get(-1).getLiteral();
            character = character.substring(1, character.length()-1);
            //CharacterIterator iterator = new StringCharacterIterator(character);
            //'a'
            //escape characters
            if(character.equals("\\b")){
                return new Ast.Expression.Literal(new Character('\b'));
            }
            else if(character.equals("\\n")){
                return new Ast.Expression.Literal(new Character('\n'));
            }
            else if(character.equals("\\r")){
                return new Ast.Expression.Literal(new Character('\r'));
            }
            else if(character.equals("\\t")){
                return new Ast.Expression.Literal(new Character('\t'));
            }
            else if(character.equals("\\\'")){
                return new Ast.Expression.Literal(new Character('\''));
            }
            else if(character.equals("\\\"")){
                return new Ast.Expression.Literal(new Character('\"'));
            }
            else if(character.equals("\\\\")){
                return new Ast.Expression.Literal(new Character('\\'));
            }

            //other characters
            else{
                return new Ast.Expression.Literal(new Character(character.charAt(0)));
            }
        }

        //"Hello,\\nWorld!"
        //replace any escape characters (hint, see String#replace).
        else if (match(Token.Type.STRING)){
            String inputString = tokens.get(-1).getLiteral();
            inputString = inputString.substring(1, inputString.length()-1);
            inputString = inputString.replace("\\b", "\b");
            inputString = inputString.replace("\\n", "\n");
            inputString = inputString.replace("\\r", "\r");
            inputString = inputString.replace("\\t", "\t");
            inputString = inputString.replace("\\\'", "\'");
            inputString = inputString.replace("\\\"", "\"");
            inputString = inputString.replace("\\\\", "\\");

            return new Ast.Expression.Literal(inputString);
        }

        else if (match("(")) {  //grouped expressions
            Ast.Expression expression = parseExpression();
            if (!match(")")) {
                throw new ParseException("Expected closing parenthesis", errorIndex());
            }

            return new Ast.Expression.Group(expression);
        }
        else if (match(Token.Type.IDENTIFIER)) {
            String name = tokens.get(-1).getLiteral();

            if (match("(")) {   //function calls
                ArrayList<Ast.Expression> parameters = new ArrayList<Ast.Expression>();

                //when the parameters are not empty
                if (!peek(")")) {
                    parameters.add(parseExpression());
                    while(match(",")) {
                        parameters.add(parseExpression());
                    }
                }

                if (!match(")")) {
                    throw new ParseException("Expected closing parenthesis", errorIndex());
                }

                return new Ast.Expression.Function(name, parameters);
            }
            else if(match("[")) {   //list index access
                Ast.Expression expression = parseExpression();
                if (!match("]")) {
                    throw new ParseException("Expected closing bracket", errorIndex());
                }

                return new Ast.Expression.Access(Optional.of(expression), name);
            }
            else {
                return new Ast.Expression.Access(Optional.empty(), name);
            }
        }
        else {
            throw new ParseException("Invalid primary expression", errorIndex());
        }
    }

    /**
     * As in the lexer, returns {@code true} if the current sequence of tokens
     * matches the given patterns. Unlike the lexer, the pattern is not a regex;
     * instead it is either a {@link Token.Type}, which matches if the token's
     * type is the same, or a {@link String}, which matches if the token's
     * literal is the same.
     *
     * In other words, {@code Token(IDENTIFIER, "literal")} is matched by both
     * {@code peek(Token.Type.IDENTIFIER)} and {@code peek("literal")}.
     */
    private boolean peek(Object... patterns) {
        for (int i = 0; i < patterns.length; i++) {
            if (!tokens.has(i)) {
                return false;
            }
            else if (patterns[i] instanceof Token.Type) {
                if (patterns[i] != tokens.get(i).getType()) {
                    return false;
                }
            }
            else if (patterns[i] instanceof String) {
                if (!(patterns[i].equals(tokens.get(i).getLiteral()))) {
                    return false;
                }
            }
            else {
                throw new AssertionError("Invalid pattern object: " + patterns[i].getClass());
            }
        }
        return true;
    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns) {
        boolean peek = peek(patterns);
        if (peek) {
            for (int i = 0; i < patterns.length; i++) {
                tokens.advance();
            }
        }
        return peek;
    }

    private int errorIndex() {
        int index;
        if (tokens.has(0)) {
            index = tokens.get(0).getIndex();
        }
        else {
            index = tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length();
        }

        return index;
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at index + offset.
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Gets the token at index + offset.
         */
        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        /**
         * Advances to the next token, incrementing the index.
         */
        public void advance() {
            index++;
        }

    }

}
