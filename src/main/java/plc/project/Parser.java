package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


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
     * Parses the {@code global} rule. This method should only be called if the
     * next tokens start a global, aka {@code LIST|VAL|VAR}.
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
     * Parses the {@code function} rule. This method should only be called if the
     * next tokens start a method, aka {@code FUN}.
     */
    public Ast.Function parseFunction() throws ParseException {
        throw new UnsupportedOperationException(); //TODO
    }

    /**
     * Parses the {@code block} rule. This method should only be called if the
     * preceding token indicates the opening a block of statements.
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
        if (!peek(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier at the beginning of the statement", tokens.get(0).getIndex());
        }

        String name = tokens.get(0).getLiteral(); // Capture the identifier name
        tokens.advance(); // Move past the identifier
        if (peek("(")) {
            // This is the start of a function call
            match("(");
            List<Ast.Expression> arguments = new ArrayList<>();
            while (!peek(")")) {
                arguments.add(parseExpression());
                if (peek(",")) {
                    match(",");
                }
            }
            if(peek(")")){
                match(")");
            }
            else{throw new ParseException("Expected closing ')'", tokens.get(0).getIndex());}
            // Ensure closing parenthesis
            if (peek(";")) {
                match(";");
                // If there's a semicolon, it's a complete expression statement
                return new Ast.Statement.Expression(new Ast.Expression.Function(name, arguments));
            } else {
                throw new ParseException("Expected ';'", tokens.get(0).getIndex());
            }
        } else {
            if (peek("=")) {
                match("=");
                // It's an assignment
                Ast.Expression value = parseExpression(); // Parse the value to be assigned

                if (!peek(";")) {
                    throw new ParseException("Expected ';' after expression", tokens.get(0).getIndex());
                }


                Ast.Expression.Access receiver = new Ast.Expression.Access(Optional.empty(), name);

                return new Ast.Statement.Assignment(receiver, value);
            } else {

                throw new ParseException("Expected ';'", tokens.get(0).getIndex());

            }
        }

    }


    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Statement.Declaration parseDeclarationStatement() throws ParseException {
        // Expect the current token to be an identifier for the variable name
        if (!peek(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier", tokens.get(0).getIndex());
        }
        String name = tokens.get(0).getLiteral();
        tokens.advance(); // Consume the identifier token

        // Optional initialization expression
        Optional<Ast.Expression> initializer = Optional.empty();
        if (peek("=")) {
            match("=");
            // If there's an '=', parse the following expression as the initializer
            initializer = Optional.of(parseExpression());
        }

        // Ensure the statement ends with a semicolon
        if (!peek(";")) {
            throw new ParseException("Expected ';'", tokens.get(0).getIndex());
        }

        // Return the declaration statement
        return new Ast.Statement.Declaration(name, initializer);
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
        Ast.Expression left = parseComparisonExpression();

        while (peek(Token.Type.OPERATOR) && (peek("&&") || peek("||"))) {
            String operator = tokens.get(0).getLiteral();
            tokens.advance(); // Move past the operator

            Ast.Expression right = parseComparisonExpression(); // Parse the right operand

            left = new Ast.Expression.Binary(operator, left, right); // Construct a new binary expression
        }

        return left;
    }
    /**
     * Parses the {@code comparison-expression} rule.
     */
    public Ast.Expression parseComparisonExpression() throws ParseException {
        Ast.Expression result = parseAdditiveExpression(); // Start with the next precedence level
        while (peek("<") || peek(">") || peek("==") || peek("!=")) {
            String operator = tokens.get(0).getLiteral();
            tokens.advance(); // Move past the operator
            Ast.Expression right = parseAdditiveExpression(); // Parse the right side of the binary expression
            result = new Ast.Expression.Binary(operator, result, right); // Form a binary expression
        }
        return result;
    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    public Ast.Expression parseAdditiveExpression() throws ParseException {
        Ast.Expression result = parseMultiplicativeExpression(); // Start with the next precedence level
        while (peek("+") || peek("-")) {
            String operator = tokens.get(0).getLiteral();
            tokens.advance(); // Move past the operator
            Ast.Expression right = parseMultiplicativeExpression();
            result = new Ast.Expression.Binary(operator, result, right); // Form a binary expression
        }
        return result;
    }
    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expression parseMultiplicativeExpression() throws ParseException {
        Ast.Expression result = parsePrimaryExpression(); // Start with the lowest level of expressions
        while (peek("*") || peek("/") || peek("^")) {
            String operator = tokens.get(0).getLiteral();
            tokens.advance(); // Move past the operator
            Ast.Expression right = parsePrimaryExpression();
            result = new Ast.Expression.Binary(operator, result, right); // Form a binary expression
        }
        return result;
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */

    public Ast.Expression parsePrimaryExpression() throws ParseException {
        if (peek(Token.Type.INTEGER)) {
            String value = tokens.get(0).getLiteral();
            match(Token.Type.INTEGER);
            return new Ast.Expression.Literal(new BigInteger(value));
        } else if (peek(Token.Type.DECIMAL)) {
            String value = tokens.get(0).getLiteral();
            match(Token.Type.DECIMAL);
            return new Ast.Expression.Literal(new BigDecimal(value));
        } else if (peek(Token.Type.CHARACTER)) {
            String value = tokens.get(0).getLiteral();
            match(Token.Type.CHARACTER);
            return new Ast.Expression.Literal(processCharLiteral(value));
        } else if (peek(Token.Type.STRING)) {
            String value = tokens.get(0).getLiteral();
            match(Token.Type.STRING);
            return new Ast.Expression.Literal(processStringLiteral(value));
        } else if (peek(Token.Type.IDENTIFIER)) {
            String name = tokens.get(0).getLiteral();

            if (peek("TRUE")) {
                match("TRUE");
                return new Ast.Expression.Literal(true);
            } else if(peek("FALSE")) {
            match("FALSE");
                return new Ast.Expression.Literal(false);}
             else if (peek("NIL")) {
                match("NIL");
                return new Ast.Expression.Literal(null);}

            match(Token.Type.IDENTIFIER);
            if (peek("(")) {
                match("(");
                List<Ast.Expression> arguments = new ArrayList<>();
                while (!peek(")")) {
                    arguments.add(parseExpression());
                    if (peek(",")) {
                        match(",");
                    }
                }
                if(peek(")")){
                match(")");
                return new Ast.Expression.Function(name, arguments);} //parse exception missing )}
                     else{throw new ParseException("Expected closing ')'", tokens.get(0).getIndex());}

            }else if (peek("[")) {
                    match("[");
                    Ast.Expression index = parseExpression(); // This captures the index expression
                    match("]");
                    return new Ast.Expression.Access(Optional.of(index), name);
                }
            else {

                return new Ast.Expression.Access(Optional.empty(), name);
            }
        } else if (peek("(")) {
            match("(");
            Ast.Expression expression = parseExpression();
            if (!peek(")")) {
                throw new ParseException("Expected ')'", tokens.get(0).getIndex());
            }
            return new Ast.Expression.Group(expression); // Wrap the expression in a Group
        }
        else {
            throw new ParseException("Expected a primary expression", tokens.get(0).getIndex());
        }
    }

    private char processCharLiteral(String literal) {
        // Assuming the character is properly escaped and surrounded by single quotes
        if (literal.length() == 3) {
            return literal.charAt(1); // Direct character
        } else if (literal.length() == 4 && literal.charAt(1) == '\\') {
            // Handle simple escape sequences
            switch (literal.charAt(2)) {
                case 'n': return '\n';
                case 't': return '\t';
                case '\\': return '\\';
                case '\'': return '\'';
                // Add more cases as necessary
            }
        }
        throw new IllegalArgumentException("Invalid character literal: " + literal);
    }

    private String processStringLiteral(String literal) {
        // Remove surrounding double quotes and replace escape sequences
        String unquoted = literal.substring(1, literal.length() - 1);
        return unquoted.replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
        // Add more replacements as necessary for other escape sequences
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
        for(int i=0; i<patterns.length;i++){
           if(!tokens.has(i)) {
               return false;
           } else if (patterns[i] instanceof Token.Type){
               if (patterns[i]!=tokens.get(i).getType()) {
                   return false;
               }
           }else if (patterns[i] instanceof String){
               if(!patterns[i].equals(tokens.get(i).getLiteral())){
                   return false;
               }
           }else {
               throw new AssertionError("Invalid pattern object: "+patterns[i].getClass());
           }

        }
        return true;
    }


    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns) {
        boolean peek=peek(patterns);
        if (peek) {
            for (int i=0; i<patterns.length; i++){
                tokens.advance();

            }

        }
        return peek;
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
