package plc.project;

import java.lang.reflect.Array;
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
        ArrayList<Ast.Global> globals = new ArrayList<Ast.Global>();
        ArrayList<Ast.Function> functions = new ArrayList<Ast.Function>();

        while (peek("LIST") || peek("VAR") || peek("VAL")) {
            globals.add(parseGlobal());
        }

        //improvement: match instead
        while (peek("FUN")) {
            functions.add(parseFunction());
        }

        //if there are still tokens in the input stream, source grammar is violated
        if (tokens.has(0)) {
            throw new ParseException("Expected 'FUN'", errorIndex());
        }

        return new Ast.Source(globals, functions);
    }

    /**
     * Parses the {@code field} rule. This method should only be called if the
     * next tokens start a field, aka {@code LET}.
     */
    public Ast.Global parseGlobal() throws ParseException {
        Ast.Global global;

        //improvement: match
        if (peek("LIST")) {
            global = parseList();
        }
        else if (peek("VAR")) {
            global = parseMutable();
        }
        else {
            global = parseImmutable();
        }

        if (!match(";")) {
            throw new ParseException("Expected semicolon", errorIndex());
        }

        return global;
    }

    /**
     * Parses the {@code list} rule. This method should only be called if the
     * next token declares a list, aka {@code LIST}.
     */
    public Ast.Global parseList() throws ParseException {
        String name, type;
        ArrayList<Ast.Expression> elements = new ArrayList<Ast.Expression>();

        match("LIST");

        //should we have if statements one-by-one, or match on all 3
        //one-by-one allows for more refined error messages
        if (!match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier", errorIndex());
        }
        name = tokens.get(-1).getLiteral();

        //type is required for global declarations
        type = parseType();

        if (!match("=")) {
            throw new ParseException("Expected assignment operator", errorIndex());
        }
        if (!match("[")) {
            throw new ParseException("Expected opening bracket", errorIndex());
        }

        //handles identifiers in list
        do {
            elements.add(parseExpression());
        }
        while (match(","));

        if (!match("]")) {
            throw new ParseException("Expected closing bracket", errorIndex());
        }

        return new Ast.Global(name, type, true, Optional.of(new Ast.Expression.PlcList(elements)));
    }

    /**
     * Parses the {@code mutable} rule. This method should only be called if the
     * next token declares a mutable global variable, aka {@code VAR}.
     */
    public Ast.Global parseMutable() throws ParseException {
        String name, type;
        Optional<Ast.Expression> value = Optional.empty();

        match("VAR");

        if(!match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier", errorIndex());
        }
        name = tokens.get(-1).getLiteral();

        type = parseType();

        if (match("=")) {
            value = Optional.of(parseExpression());
        }

        return new Ast.Global(name, type, true, value);
    }

    /**
     * Parses the {@code immutable} rule. This method should only be called if the
     * next token declares an immutable global variable, aka {@code VAL}.
     */
    public Ast.Global parseImmutable() throws ParseException {
        String name, type;
        Optional<Ast.Expression> value;

        match("VAL");

        if(!match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier", errorIndex());
        }
        name = tokens.get(-1).getLiteral();

        type = parseType();

        if (!match("=")) {
            throw new ParseException("Expected assignment operator", errorIndex());

        }
        value = Optional.of(parseExpression());

        return new Ast.Global(name, type, false, value);

    }

    /**
     * Parses the {@code method} rule. This method should only be called if the
     * next tokens start a method, aka {@code DEF}.
     */
    public Ast.Function parseFunction() throws ParseException {
        String name;
        ArrayList<String> parameters = new ArrayList<String>();
        ArrayList<String> parameterTypes = new ArrayList<String>();
        Optional<String> returnType = Optional.empty();
        List<Ast.Statement> statements;

        match("FUN");

        if (!match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier", errorIndex());
        }
        name = tokens.get(-1).getLiteral();

        if (!match("(")) {
            throw new ParseException("Expected opening parenthesis'", errorIndex());
        }

        //handles variable amount of parameters
        if (match(Token.Type.IDENTIFIER)) {
            do {
                parameters.add(tokens.get(-1).getLiteral());
                parameterTypes.add(parseType());
            }
            while(match(",", Token.Type.IDENTIFIER));
        }

        if (!match(")")) {
            throw new ParseException("Expected closing parenthesis'", errorIndex());
        }
        //return type, if no match, then it is Optional.empty() (void/NIL return type)
        if (match(":")) {
            if (!match(Token.Type.IDENTIFIER)) {
                throw new ParseException("Expected identifier", errorIndex());
            }
            returnType = Optional.of(tokens.get(-1).getLiteral());
        }
        if (!match("DO")) {
            throw new ParseException("Expected 'DO'", errorIndex());
        }

        //handles parsing statements in function's block
        statements = parseBlock();

        if (!match("END")) {
            throw new ParseException("Expected 'END'", errorIndex());
        }

        return new Ast.Function(name, parameters, parameterTypes, returnType, statements);
    }

    /**
     * Parses the {@code block} rule. This method should only be called if the
     * preceding token indicates the opening a block.
     */
    public List<Ast.Statement> parseBlock() throws ParseException {
        ArrayList<Ast.Statement> statements = new ArrayList<Ast.Statement>();

        //as long as we have statements, continue parsing statements until we reach an identifier signifying the ending of a block
        while (tokens.has(0) && !peek("END") && !peek("ELSE") && !peek("CASE") && !peek("DEFAULT")) {
            statements.add(parseStatement());
        }

        return statements;
    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Statement parseStatement() throws ParseException {

        if (peek("LET")) {
            return parseDeclarationStatement();
        }
        else if (peek("SWITCH")) {
            return parseSwitchStatement();
        }
        else if (peek("IF")) {
            return parseIfStatement();
        }
        else if (peek("WHILE")) {
            return parseWhileStatement();
        }
        else if (peek("RETURN")) {
            return parseReturnStatement();
        }
        else {  //have an expression by itself or assignment

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
    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Statement.Declaration parseDeclarationStatement() throws ParseException {
        String name;
        Optional<String> type = Optional.empty();
        Optional<Ast.Expression> value = Optional.empty();

        match("LET");

        if (!match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier", errorIndex());
        }
        name = tokens.get(-1).getLiteral();

        //in local declaration, type is optional
        if (match(":")) {
            if (!match(Token.Type.IDENTIFIER)) {
                throw new ParseException("Expected identifier that is a name of a type", errorIndex());
            }
            type = Optional.of(tokens.get(-1).getLiteral());
        }

        if (match("=")) {
            value = Optional.of(parseExpression());
        }
        if (!match(";")) {
            throw new ParseException("Expected semicolon", errorIndex());
        }

        return new Ast.Statement.Declaration(name, type, value);
    }

    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Statement.If parseIfStatement() throws ParseException {
        //'IF' expression 'DO' block ('ELSE' block)? 'END'
        Ast.Expression condition;
        List<Ast.Statement> thenStatements;
        List<Ast.Statement> elseStatements;

        match("IF");
        condition = parseExpression();
        if (!match("DO")){
            throw new ParseException("Expected 'DO'", errorIndex());
        }
        else{
            thenStatements = parseBlock();
            if (!match("ELSE")){
                if(!match("END")){
                    throw new ParseException("Expected 'END'", errorIndex());
                }
                else{
                    //instantiate elseStatements as empty list since there is no ELSE attached to the IF statement
                    elseStatements = new ArrayList<Ast.Statement>();
                    return new Ast.Statement.If(condition, thenStatements, elseStatements);
                }
            }
            else{
                elseStatements = parseBlock();
                if(!match("END")){
                    throw new ParseException("Expected 'END'", errorIndex());
                }
                else{
                    return new Ast.Statement.If(condition, thenStatements, elseStatements);
                }
            }
        }
    }

    /**
     * Parses a switch statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a switch statement, aka
     * {@code SWITCH}.
     */
    public Ast.Statement.Switch parseSwitchStatement() throws ParseException {
        Ast.Expression condition;
        ArrayList<Ast.Statement.Case> cases = new ArrayList<Ast.Statement.Case>();

        match("SWITCH");
        condition = parseExpression();

        while(peek("CASE")) {
            cases.add(parseCaseStatement());
        }

        if(!match("DEFAULT")) {
            throw new ParseException("Expected 'DEFAULT'", errorIndex());
        }
        cases.add(parseCaseStatement());

        if(!match("END")) {
            throw new ParseException("Expected 'END'", errorIndex());
        }

        return new Ast.Statement.Switch(condition, cases);
    }

    /**
     * Parses a case or default statement block from the {@code switch} rule. 
     * This method should only be called if the next tokens start the case or 
     * default block of a switch statement, aka {@code CASE} or {@code DEFAULT}.
     */
    public Ast.Statement.Case parseCaseStatement() throws ParseException {
        Optional<Ast.Expression> value = Optional.empty();
        List<Ast.Statement> statements;

        if (match("CASE")) {

            value = Optional.of(parseExpression());
            if (!match(":")) {
                throw new ParseException("Expected colon", errorIndex());
            }
        }

        statements = parseBlock();

        return new Ast.Statement.Case(value, statements);
    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Statement.While parseWhileStatement() throws ParseException {
        Ast.Expression condition;
        List<Ast.Statement> statements;

        match("WHILE");
        condition = parseExpression();

        if (!match("DO")){
            throw new ParseException("Expected 'DO'", errorIndex());
        }
        else{
            statements = parseBlock();
            if(!match("END")){
                throw new ParseException("Expected 'END'", errorIndex());
            }
            else{
                return new Ast.Statement.While(condition, statements);
            }
        }
    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Statement.Return parseReturnStatement() throws ParseException {

        match("RETURN");
        Ast.Expression value = parseExpression();

        if (!match(";")) {
            throw new ParseException("Expected semicolon", errorIndex());
        }

        return new Ast.Statement.Return(value);
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

    private String parseType() {
        if (!match(":")) {
            throw new ParseException("Expected colon", errorIndex());
        }
        if (!match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier", errorIndex());
        }
        return tokens.get(-1).getLiteral();
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
