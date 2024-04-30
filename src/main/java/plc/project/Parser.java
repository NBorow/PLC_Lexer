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
        List<Ast.Global> globals= new ArrayList<>();
        List<Ast.Function> functions= new ArrayList<>();
        while(peek("LIST")||peek("VAR")||peek("VAL")){
            globals.add(parseGlobal());
        }
        while(peek("FUN")){
           functions.add(parseFunction());
        }
        if(peek("LIST")||peek("VAR")||peek("VAL")){
            if(tokens.has(0)){
                throw new ParseException("Global After Functions", tokens.get(0).getIndex());
            }
            else  throw new ParseException("Global After Functions", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
        return new Ast.Source(globals,functions);

    }

    /**
     * Parses the {@code global} rule. This method should only be called if the
     * next tokens start a global, aka {@code LIST|VAL|VAR}.
     */
    public Ast.Global parseGlobal() throws ParseException {
        if (peek("LIST")){
            Ast.Global lis=parseList();
            if(!peek(";")){
                if(tokens.has(0)){
                    throw new ParseException("Expected ; After Global", tokens.get(0).getIndex());
                }
                else  throw new ParseException("Expected ; After Global", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
            else{
                match(";");
                return lis;
            }
        }
        if (peek("VAR")){
            Ast.Global var=parseMutable();
            if(!peek(";")){
                if(tokens.has(0)){
                    throw new ParseException("Expected ; After Global", tokens.get(0).getIndex());
                }
                else  throw new ParseException("Expected ; After Global", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
            else{
                match(";");
                return var;
            }
        }
        if (peek("VAL")){
            Ast.Global val=parseImmutable();
            if(!peek(";")){
                if(tokens.has(0)){
                    throw new ParseException("Expected ; After Global", tokens.get(0).getIndex());
                }
                else  throw new ParseException("Expected ; After Global", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
            else{
                match(";");
                return val;
            }
        }
            throw new ParseException("Unknown Global Error", tokens.get(0).getIndex());
    }


    /**
     * Parses the {@code list} rule. This method should only be called if the
     * next token declares a list, aka {@code LIST}.
     */
    public Ast.Global parseList() throws ParseException {
       if(peek("LIST")) {
           match("LIST");
           if (peek(Token.Type.IDENTIFIER)) {
               if (peek("LIST") || peek("VAR") || peek("VAL") || peek("FUN") || peek("LET") || peek("SWITCH") || peek("CASE") || peek("DEFAULT") || peek("END") || peek("IF") || peek("DO") || peek("ELSE") || peek("WHILE") || peek("RETURN") || peek("NIL") || peek("TRUE") || peek("FALSE") || peek("BOOLEAN")) {

                   if (tokens.has(0)) {
                       throw new ParseException("Expected Nonkeyword Identifier", tokens.get(0).getIndex());
                   } else
                       throw new ParseException("Expected Nonkeyword Identifier", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
               }
               String name = tokens.get(0).getLiteral();
               match(Token.Type.IDENTIFIER);
               if (peek(Token.Type.OPERATOR) && peek(":")) {
                   match(":");
                   if (peek(Token.Type.IDENTIFIER)) {
                       String TypName = tokens.get(0).getLiteral();
                       match(Token.Type.IDENTIFIER);
                       if (peek("=")) {
                           match("=");
                           if (peek("[")) {
                               match("[");
                               ///////////////////////////////////
                               if (peek("LIST") || peek("VAR") || peek("VAL") || peek("FUN") || peek("LET") || peek("SWITCH") || peek("CASE") || peek("DEFAULT") || peek("END") || peek("IF") || peek("DO") || peek("ELSE") || peek("WHILE") || peek("RETURN")) {
                                   if (tokens.has(0)) {
                                       throw new ParseException("Expected Nonkeyword Expression", tokens.get(0).getIndex());
                                   } else
                                       throw new ParseException("Expected Nonkeyword Expression", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());

                               }//keyword

                               if (peek("NIL") || peek("TRUE") || peek("FALSE") || peek(Token.Type.INTEGER) || peek(Token.Type.DECIMAL) || peek(Token.Type.CHARACTER) || peek(Token.Type.STRING) || peek("(") || peek(Token.Type.IDENTIFIER)) {

                                   List<Ast.Expression> firstlist = new ArrayList<>();
                                   Ast.Expression expr = parseExpression();
                                   firstlist.add(expr);
                                   if (peek(",")) {

                                       while (peek(",")) {
                                           match(",");
                                           if (peek("]")) {
                                               throw new ParseException("Trailing Comma In GLOBAL LIST", tokens.get(0).getIndex());
                                           }
                                           if (peek("LIST") || peek("VAR") || peek("VAL") || peek("FUN") || peek("LET") || peek("SWITCH") || peek("CASE") || peek("DEFAULT") || peek("END") || peek("IF") || peek("DO") || peek("ELSE") || peek("WHILE") || peek("RETURN")) {
                                               if (tokens.has(0)) {
                                                   throw new ParseException("Expected Nonkeyword Expression", tokens.get(0).getIndex());
                                               } else
                                                   throw new ParseException("Expected Nonkeyword Expression", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());

                                           }//keyword

                                           if (peek("NIL") || peek("TRUE") || peek("FALSE") || peek(Token.Type.INTEGER) || peek(Token.Type.DECIMAL) || peek(Token.Type.CHARACTER) || peek(Token.Type.STRING) || peek("(") || peek(Token.Type.IDENTIFIER)) {

                                               firstlist.add(parseExpression());

                                           } else {
                                               if (tokens.has(0)) {
                                                   throw new ParseException("Expected  Expression", tokens.get(0).getIndex());
                                               } else
                                                   throw new ParseException("Expected  Expression", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());

                                           }
                                       }

                                   }

                                   if (peek("]")) {
                                       match("]");
                                       Ast.Expression.PlcList seclist = new Ast.Expression.PlcList(firstlist);
                                       return new Ast.Global(name, TypName,true, Optional.of(seclist));
                                   } else {
                                       if (tokens.has(0)) {
                                           throw new ParseException("Expected ]", tokens.get(0).getIndex());
                                       } else
                                           throw new ParseException("Expected ]", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                                   }
                               }
                               //////////////////////////////////////////
                               else {
                                   if (tokens.has(0)) {
                                       throw new ParseException("Expected Expression", tokens.get(0).getIndex());
                                   } else
                                       throw new ParseException("Expected Expression", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                               }/////////////////////////////////////
                           } else {
                               if (tokens.has(0)) {
                                   throw new ParseException("Expected [", tokens.get(0).getIndex());
                               } else
                                   throw new ParseException("Expected [", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                           }
                       } else {
                           throw new ParseException("Expected = After The Identifier After LIST", tokens.get(0).getIndex());
                       }
                   } else {
                       if (tokens.has(0)) {
                           throw new ParseException("Expected Type Identifier After :", tokens.get(0).getIndex());
                       } else
                           throw new ParseException("Expected Type Identifier After :", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                   }
               } else {
                   if (tokens.has(0)) {
                       throw new ParseException("Expected : After The Identifier After LIST", tokens.get(0).getIndex());
                   } else
                       throw new ParseException("Expected : After The Identifier After LIST", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
               }

           } else {
               if (tokens.has(0)) {
                   throw new ParseException("Expected Identifier After Var", tokens.get(0).getIndex());
               } else
                   throw new ParseException("Expected Identifier After Var", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
           }
       }
       else{
           if(tokens.has(0)){
               throw new ParseException("Expected LIST", tokens.get(0).getIndex());
           }
           else  throw new ParseException("Expected LIST", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
       }
    }

    /**
     * Parses the {@code mutable} rule. This method should only be called if the
     * next token declares a mutable global variable, aka {@code VAR}.
     */
    public Ast.Global parseMutable() throws ParseException {
        if(peek("VAR")){
            match("VAR");
            if(peek(Token.Type.IDENTIFIER)) {
                if (peek("LIST") || peek("VAR") || peek("VAL") || peek("FUN") || peek("LET") || peek("SWITCH") || peek("CASE") || peek("DEFAULT") || peek("END") || peek("IF") || peek("DO") || peek("ELSE") || peek("WHILE") || peek("RETURN") || peek("NIL") || peek("TRUE") || peek("FALSE")) {

                    if (tokens.has(0)) {
                        throw new ParseException("Expected Nonkeyword Identifier", tokens.get(0).getIndex());
                    } else
                        throw new ParseException("Expected Nonkeyword Identifier", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }
                String name = tokens.get(0).getLiteral();
                match(Token.Type.IDENTIFIER);
                if (peek(Token.Type.OPERATOR) && peek(":")) {
                    match(":");
                    if (peek(Token.Type.IDENTIFIER)) {
                        String TypName = tokens.get(0).getLiteral();
                        match(Token.Type.IDENTIFIER);
                        if (peek("=")) {
                            match("=");
                            if (peek("NIL") || peek("LET") || peek("SWITCH") || peek("IF") || peek("WHILE") || peek("RETURN") || peek("TRUE") || peek("FALSE") || peek(Token.Type.INTEGER) || peek(Token.Type.DECIMAL) || peek(Token.Type.CHARACTER) || peek(Token.Type.STRING) || peek("(") || peek(Token.Type.IDENTIFIER)) {
                                Ast.Expression expr = parseExpression();
                                return new Ast.Global(name, TypName, true, Optional.of(expr));
                            }
                        } else {
                            return new Ast.Global(name, TypName,true, Optional.empty());
                        }


                    } else {
                        if (tokens.has(0)) {
                            throw new ParseException("Expected Type Identifier After :", tokens.get(0).getIndex());
                        } else
                            throw new ParseException("Expected Type Identifier After :", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                    }

                } else {
                    if (tokens.has(0)) {
                        throw new ParseException("Expected : After The Identifier After VAR", tokens.get(0).getIndex());
                    } else
                        throw new ParseException("Expected : After The Identifier After VAR", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }
            }
            else {
                if(tokens.has(0)){
                    throw new ParseException("Expected Identifier After VAR", tokens.get(0).getIndex());
                }
                else  throw new ParseException("Expected Identifier After VAR", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
        }else{
            if(tokens.has(0)){
                throw new ParseException("Expected VAR", tokens.get(0).getIndex());
            }
            else  throw new ParseException("Expected VAR", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
        if(tokens.has(0)){
            throw new ParseException("Unknown VAR Error", tokens.get(0).getIndex());
        }
        else  throw new ParseException("Unknown VAR Error", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
    }

    /**
     * Parses the {@code immutable} rule. This method should only be called if the
     * next token declares an immutable global variable, aka {@code VAL}.
     */
    public Ast.Global parseImmutable() throws ParseException {
       if(peek("VAL")){
           match("VAL");
           if(peek(Token.Type.IDENTIFIER)){
               if(peek("LIST")||peek("VAR")||peek("VAL")||peek("FUN")||peek("LET")||peek("SWITCH")||peek("CASE")||peek("DEFAULT")||peek("END")||peek("IF")||peek("DO")||peek("ELSE")||peek("WHILE")||peek("RETURN")||peek("NIL")||peek("TRUE")||peek("FALSE")){

                   if(tokens.has(0)){
                       throw new ParseException("Expected Nonkeyword Identifier", tokens.get(0).getIndex());
                   }
                   else  throw new ParseException("Expected Nonkeyword Identifier", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
               }
               String name = tokens.get(0).getLiteral();
               match(Token.Type.IDENTIFIER);
               if (peek(Token.Type.OPERATOR) && peek(":")) {
                   match(":");
                   if (peek(Token.Type.IDENTIFIER)) {
                       String TypName = tokens.get(0).getLiteral();
                       match(Token.Type.IDENTIFIER);
               if(peek("=")){
                   match("=");
                   if(peek("NIL") || peek("LET") || peek("SWITCH") || peek("IF") || peek("WHILE") || peek("RETURN") || peek("TRUE") || peek("FALSE") || peek(Token.Type.INTEGER) || peek(Token.Type.DECIMAL) || peek(Token.Type.CHARACTER) || peek(Token.Type.STRING) || peek("(") || peek(Token.Type.IDENTIFIER)){
                       Ast.Expression expr=parseExpression();
                       return new Ast.Global(name,TypName,false,Optional.of(expr));
                   }
               }
               else{
                   if(tokens.has(0)){
                       throw new ParseException("Expected = After Identifier After VAL", tokens.get(0).getIndex());
                   }
                   else  throw new ParseException("Expected = After Identifier After VAL", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
               }

                   } else {
                       if (tokens.has(0)) {
                           throw new ParseException("Expected Type Identifier After :", tokens.get(0).getIndex());
                       } else
                           throw new ParseException("Expected Type Identifier After :", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                   }

               } else {
                   if (tokens.has(0)) {
                       throw new ParseException("Expected : After The Identifier After VAL", tokens.get(0).getIndex());
                   } else
                       throw new ParseException("Expected : After The Identifier After VAL", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
               }
           }
           else {
               if(tokens.has(0)){
                   throw new ParseException("Expected Identifier After VAL", tokens.get(0).getIndex());
               }
               else  throw new ParseException("Expected Identifier After VAL", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
           }
       }else{
           if(tokens.has(0)){
               throw new ParseException("Expected VAL", tokens.get(0).getIndex());
           }
           else  throw new ParseException("Expected VAL", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
       }
        if(tokens.has(0)){
            throw new ParseException("Expected VAR", tokens.get(0).getIndex());
        }
        else  throw new ParseException("Expected VAR", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
    }

    /**
     * Parses the {@code function} rule. This method should only be called if the
     * next tokens start a method, aka {@code FUN}.
     */
    public Ast.Function parseFunction() throws ParseException {
        if (peek("FUN")) {
            match("FUN");
            if (peek(Token.Type.IDENTIFIER)) {
                if (peek("VAL") || peek("VAR") || peek("LIST")) {
                    if (tokens.has(0)) {
                        throw new ParseException("Global Keyword In Function Name", tokens.get(0).getIndex());
                    } else
                        throw new ParseException("Global Keyword In Function Name", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }
                if (peek("FUN")) {
                    if (tokens.has(0)) {
                        throw new ParseException("Unexpected FUN Keyword", tokens.get(0).getIndex());
                    } else
                        throw new ParseException("Unexpected FUN Keyword", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }
                if (peek("LET") || peek("SWITCH") || peek("CASE") || peek("DEFAULT") || peek("END") || peek("IF") || peek("DO") || peek("ELSE") || peek("WHILE") || peek("RETURN") || peek("NIL") || peek("TRUE") || peek("FALSE")) {
                    if (tokens.has(0)) {
                        throw new ParseException("Expected Nonkeyword Identifier", tokens.get(0).getIndex());
                    } else
                        throw new ParseException("Expected Nonkeyword Identifier", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }
                String name = tokens.get(0).getLiteral();
                match(Token.Type.IDENTIFIER);
                if (peek("(")) {
                    match("(");
                    //  List<Ast.Expression> argumentsb = new ArrayList<>();
                    List<String> arguments = new ArrayList<>();
                    List<String> ParamTypes = new ArrayList<>();
                    while (!peek(")") && peek(Token.Type.IDENTIFIER)) {
                        if (peek(Token.Type.IDENTIFIER)) {
                            if (peek("VAL") || peek("VAR") || peek("LIST")) {
                                if (tokens.has(0)) {
                                    throw new ParseException("Global Keyword In Function Parameter", tokens.get(0).getIndex());
                                } else
                                    throw new ParseException("Global Keyword In Function Parameter", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                            }
                            if (peek("FUN")) {
                                if (tokens.has(0)) {
                                    throw new ParseException("Unexpected FUN Keyword", tokens.get(0).getIndex());
                                } else
                                    throw new ParseException("Unexpected FUN Keyword", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                            }
                            if (peek("LET") || peek("SWITCH") || peek("CASE") || peek("DEFAULT") || peek("END") || peek("IF") || peek("DO") || peek("ELSE") || peek("WHILE") || peek("RETURN") || peek("NIL") || peek("TRUE") || peek("FALSE")) {
                                if (tokens.has(0)) {
                                    throw new ParseException("Expected Nonkeyword Identifier", tokens.get(0).getIndex());
                                } else
                                    throw new ParseException("Expected Nonkeyword Identifier", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                            }
                            arguments.add(tokens.get(0).getLiteral());
                            Ast.Expression first = parseExpression();
                            if (peek(Token.Type.OPERATOR) && peek(":")) {
                                match(":");
                                if (peek(Token.Type.IDENTIFIER)) {
                                    ParamTypes.add(tokens.get(0).getLiteral());
                                    match(Token.Type.IDENTIFIER); //remove? name identifier matched using first=parseExpression. matching this identifier should work?
                                    if (peek(",")) {
                                        match(",");
                                        if (peek(")")) {
                                            throw new ParseException("Trailing Comma In Function Call", tokens.get(0).getIndex());
                                        }
                                    }
                                } else {
                                    if (tokens.has(0)) {
                                        throw new ParseException("Expected Type Identifier After :", tokens.get(0).getIndex());
                                    } else
                                        throw new ParseException("Expected Type Identifier After :", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                                }

                            } else {
                                if (tokens.has(0)) {
                                    throw new ParseException("Expected : After Identifier", tokens.get(0).getIndex());
                                } else
                                    throw new ParseException("Expected : After Identifier", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                            }

                        } else {
                            if (tokens.has(0)) {
                                throw new ParseException("Expected Identifier", tokens.get(0).getIndex());
                            } else
                                throw new ParseException("Expected Identifier", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                        }


                    }


                    if (peek(")")) {
                        match(")");
                        Optional<String> initializer = Optional.empty();
                        if (peek(Token.Type.OPERATOR) && peek(":")) {
                            match(":");
                            if (peek(Token.Type.IDENTIFIER)) {
                                initializer=Optional.of(tokens.get(0).getLiteral());
                                match(Token.Type.IDENTIFIER);
                            }
                            else{
                                if (tokens.has(0)) {
                                    throw new ParseException("Expected Type Identifier After :", tokens.get(0).getIndex());
                                } else
                                    throw new ParseException("Expected Type Identifier After :", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                            }
                        }

                        if (peek("DO")) {
                            match("DO");
                            if ((peek("NIL") || peek("LET") || peek("SWITCH") || peek("IF") || peek("WHILE") || peek("RETURN") || peek("TRUE") || peek("FALSE") || peek(Token.Type.INTEGER) || peek(Token.Type.DECIMAL) || peek(Token.Type.CHARACTER) || peek(Token.Type.STRING) || peek("(") || peek(Token.Type.IDENTIFIER))) {
                                if (peek("VAL") || peek("VAR") || peek("LIST")) {
                                    if (tokens.has(0)) {
                                        throw new ParseException("Global Keyword In Function Block", tokens.get(0).getIndex());
                                    } else
                                        throw new ParseException("Global Keyword In Function Block", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                                }
                                if (peek("FUN")) {
                                    if (tokens.has(0)) {
                                        throw new ParseException("Unexpected FUN Keyword", tokens.get(0).getIndex());
                                    } else
                                        throw new ParseException("Unexpected FUN Keyword", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                                }
                                List<Ast.Statement> bloc = parseBlock(); //add parseexception check?
                                if (peek("END")) {
                                    match("END");
                                    return new Ast.Function(name,arguments,ParamTypes,initializer,bloc);

                                } else {
                                    if (tokens.has(0)) {
                                        throw new ParseException("Expected END", tokens.get(0).getIndex());
                                    } else
                                        throw new ParseException("Expected END", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                                }
                            } else {
                                if (tokens.has(0)) {
                                    throw new ParseException("Expected Block", tokens.get(0).getIndex());
                                } else
                                    throw new ParseException("Expected Block", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                            }
                        } else {
                            if (tokens.has(0)) {
                                throw new ParseException("Expected DO", tokens.get(0).getIndex());
                            } else
                                throw new ParseException("Expected DO", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                        }



                } else {
                    if (tokens.has(0)) {
                        throw new ParseException("Expected )", tokens.get(0).getIndex());
                    } else
                        throw new ParseException("Expected )", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }
            } else {
                if (tokens.has(0)) {
                    throw new ParseException("Expected (", tokens.get(0).getIndex());
                } else
                    throw new ParseException("Expected (FUN)", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
        } else {
            if (tokens.has(0)) {
                throw new ParseException("Expected Identifier After FUN", tokens.get(0).getIndex());
            } else
                throw new ParseException("Expected Identifier After FUN", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
        }
    }
        else{
            if(tokens.has(0)){
                throw new ParseException("Expected FUN", tokens.get(0).getIndex());
            }
            else  throw new ParseException("Expected FUN", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
    }

    /**
     * Parses the {@code block} rule. This method should only be called if the
     * preceding token indicates the opening a block of statements.
     */
    public List<Ast.Statement> parseBlock() throws ParseException {
        List<Ast.Statement> Statements = new ArrayList<>();
        while ((!peek("END") && !peek("ELSE") && !peek("CASE") && !peek("DEFAULT") && !peek("DO") && !peek("LIST") && !peek("VAR") && !peek("VAL")) && ((peek("NIL") || peek("LET") || peek("SWITCH") || peek("IF") || peek("WHILE") || peek("RETURN") || peek("TRUE") || peek("FALSE") || peek(Token.Type.INTEGER) || peek(Token.Type.DECIMAL) || peek(Token.Type.CHARACTER) || peek(Token.Type.STRING) || peek("(") || peek(Token.Type.IDENTIFIER)))){
            Statements.add(parseStatement());
            if(peek("LIST")){
                if(tokens.has(0)){
                    throw new ParseException("Unexpected LIST", tokens.get(0).getIndex());
                }
                else  throw new ParseException("Unexpected LIST", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
            if(peek("VAR")){
                if(tokens.has(0)){
                    throw new ParseException("Unexpected VAR", tokens.get(0).getIndex());
                }
                else  throw new ParseException("Unexpected VAR", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
            if(peek("VAL")) {
                if(tokens.has(0)){
                    throw new ParseException("Unexpected VAL", tokens.get(0).getIndex());
                }
                else  throw new ParseException("Unexpected VAL", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
            if(peek("FUN")){
                if(tokens.has(0)){
                    throw new ParseException("Unexpected FUN Keyword", tokens.get(0).getIndex());
                }
                else  throw new ParseException("Unexpected FUN Keyword", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
    }
//EXTRA EXCEPTION CHECKING NEEDED,
    return Statements;
    }
    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Statement parseStatement() throws ParseException {
            if (peek("LET")) {
                return (parseDeclarationStatement());
            } else if (peek("SWITCH")) {
                return (parseSwitchStatement());
            } else if (peek("IF")) {
                return (parseIfStatement());
            } else if (peek("WHILE")) {
                return (parseWhileStatement());
            } else if (peek("RETURN")) {
                return (parseReturnStatement());
            } else if (peek(Token.Type.IDENTIFIER)) {
                if(peek("VAL")||peek("VAR")||peek("LIST")){
                    if(tokens.has(0)){
                        throw new ParseException("Global Keyword In Statement", tokens.get(0).getIndex());
                    }
                    else  throw new ParseException("Global Keyword In Statement", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                }
                if(peek("FUN")){
                    if(tokens.has(0)){
                        throw new ParseException("Unexpected FUN Keyword", tokens.get(0).getIndex());
                    }
                    else  throw new ParseException("Unexpected FUN Keyword", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                }
                    // tokens.advance();
                    String name = tokens.get(0).getLiteral();
                    System.out.println(name);
                    Ast.Expression first = parseExpression();
                    if (peek("=")) {
                        match("=");
                        // It's an assignment
                        Ast.Expression value = parseExpression(); // Parse the value to be assigned
                        if (!peek(";")) {
                            // System.out.print(tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                            if(tokens.has(0)){
                                throw new ParseException("Expected ; After Assignment", tokens.get(0).getIndex());
                            }
                            else  throw new ParseException("Expected ; After Assignment", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                        }
                        if (peek(";")) {
                            match(";");
                        }
                        // Ast.Expression.Access receiver = new Ast.Expression.Access(Optional.empty(),  name);
                        return new Ast.Statement.Assignment(first, value);
                    }
                 else if (peek(";")) {
                        match(";");
                        return new Ast.Statement.Expression(first);
                    } else {
                        if(tokens.has(0)){
                            throw new ParseException("Expected ; After Expression", tokens.get(0).getIndex());
                        }
                        else  throw new ParseException("Expected ; After Expression", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                    }
            }
        throw new ParseException("Really should not hit here", tokens.get(0).getIndex());
    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Statement.Declaration parseDeclarationStatement() throws ParseException {


        // Expect the current token to be an identifier for the variable name
match("LET");
if(peek(Token.Type.IDENTIFIER)){
        if(peek("LIST")||peek("VAR")||peek("VAL")||peek("FUN")||peek("LET")||peek("SWITCH")||peek("CASE")||peek("DEFAULT")||peek("END")||peek("IF")||peek("DO")||peek("ELSE")||peek("WHILE")||peek("RETURN")||peek("NIL")||peek("TRUE")||peek("FALSE")){

            if(tokens.has(0)){
                throw new ParseException("Expected Nonkeyword Identifier", tokens.get(0).getIndex());
            }
            else  throw new ParseException("Expected Nonkeyword Identifier", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
        String name = tokens.get(0).getLiteral();
        tokens.advance(); // Consume the identifier token

    Optional<String> initializerStr = Optional.empty();
    if (peek(Token.Type.OPERATOR) && peek(":")) {
        match(":");
        if (peek(Token.Type.IDENTIFIER)) {
            initializerStr = Optional.of(tokens.get(0).getLiteral());
            match(Token.Type.IDENTIFIER);
        }
    }
        // Optional initialization expression
        Optional<Ast.Expression> initializer = Optional.empty();
        if (peek("=")) {
            match("=");
            // If there's an '=', parse the following expression as the initializer
            initializer = Optional.of(parseExpression());
        }

        // Ensure the statement ends with a semicolon
        if (!match(";")) {
            if(tokens.has(0)){
                throw new ParseException("Expected ;", tokens.get(0).getIndex());
            }
            else  throw new ParseException("Expected ;", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }

        // Return the declaration statement
    return new Ast.Statement.Declaration(name,initializerStr,initializer);
  }

else{
    if(tokens.has(0)){
        throw new ParseException("Expected Identifier", tokens.get(0).getIndex());
    }
    else  throw new ParseException("Expected Identifier", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());

}
    }


    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Statement.If parseIfStatement() throws ParseException {
        if(peek("IF")) {
            match("IF");
            if (!peek("NIL") && !peek("TRUE") && !peek("FALSE") && !peek(Token.Type.INTEGER) && !peek(Token.Type.DECIMAL) && !peek(Token.Type.CHARACTER) && !peek(Token.Type.STRING) && !peek("(") && !peek(Token.Type.IDENTIFIER)) {
                throw new ParseException("Expected Expression After IF.", tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
            Ast.Expression expr = parseExpression();
            if (peek("DO")) {
                match("DO");
                List<Ast.Statement> bloc = new ArrayList<>();
                List<Ast.Statement> bloc2 = new ArrayList<>();
                if ((peek("NIL") || peek("LET") || peek("SWITCH") || peek("IF") || peek("WHILE") || peek("RETURN") || peek("TRUE") || peek("FALSE") || peek(Token.Type.INTEGER) || peek(Token.Type.DECIMAL) || peek(Token.Type.CHARACTER) || peek(Token.Type.STRING) || peek("(") || peek(Token.Type.IDENTIFIER))) {
                    bloc = parseBlock();//add fuller parseexception check? e.g. can be any identifier includimg FUN, VAL, etc.
                    if (peek("ELSE")) {
                        match("ELSE");

                        if ((peek("NIL") || peek("LET") || peek("SWITCH") || peek("IF") || peek("WHILE") || peek("RETURN") || peek("TRUE") || peek("FALSE") || peek(Token.Type.INTEGER) || peek(Token.Type.DECIMAL) || peek(Token.Type.CHARACTER) || peek(Token.Type.STRING) || peek("(") || peek(Token.Type.IDENTIFIER))) {
                            bloc2 = parseBlock();//add parseexception check? e.g. can be any identifier includimg FUN, VAL, etc.
                            if (peek("END")) {
                                match("END");
                                return new Ast.Statement.If(expr, bloc, bloc2);
                            } else {
                                if(tokens.has(0)){
                                    throw new ParseException("Expected END", tokens.get(0).getIndex());
                                }
                                else  throw new ParseException("Expected END", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                            }

                        }
                    } else {
                        if (peek("END")) {
                            match("END");
                            return new Ast.Statement.If(expr, bloc, bloc2);
                        } else {
                            if(tokens.has(0)){
                                throw new ParseException("Expected END", tokens.get(0).getIndex());
                            }
                            else  throw new ParseException("Expected END", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                        }
                    }
                } else {
                    if(tokens.has(0)){
                        throw new ParseException("Expected Block", tokens.get(0).getIndex());
                    }
                    else  throw new ParseException("Expected Block", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                }
            } else {
                if(tokens.has(0)){
                    throw new ParseException("Expected DO", tokens.get(0).getIndex());
                }
                else  throw new ParseException("Expected DO", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
        }
        else {
            if(tokens.has(0)){
                throw new ParseException("Expected IF", tokens.get(0).getIndex());
            }
            else  throw new ParseException("Expected IF", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
        throw new ParseException("Unexpected IF Error", tokens.get(0).getIndex());
    }

    /**
     * Parses a switch statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a switch statement, aka
     * {@code SWITCH}.
     */
    public Ast.Statement.Switch parseSwitchStatement() throws ParseException {
        match("SWITCH");
        if(!peek("NIL")&&!peek("TRUE")&&!peek("FALSE")&&!peek(Token.Type.INTEGER)&&!peek(Token.Type.DECIMAL)&&!peek(Token.Type.CHARACTER)&&!peek(Token.Type.STRING)&&!peek("(")&&!peek(Token.Type.IDENTIFIER)){
            if(tokens.has(0)){
                throw new ParseException("Expected Expression After SWITCH", tokens.get(0).getIndex());
            }
            else  throw new ParseException("Expected Expression After SWITCH", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
        Ast.Expression expr=parseExpression();
        List<Ast.Statement.Case> Cases=new ArrayList<>();
        while(peek("CASE")){
            Cases.add(parseCaseStatement());

        }
        if(peek("DEFAULT")){
            Cases.add(parseCaseStatement());
        }
        else{
            if(tokens.has(0)){
                throw new ParseException("Expected DEFAULT Case", tokens.get(0).getIndex());
            }
            else  throw new ParseException("Expected DEFAULT Case", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }

        if (peek("END")) {
            match("END");

            return new Ast.Statement.Switch(expr,Cases);


        } else {
            if(tokens.has(0)){
                throw new ParseException("Expected END", tokens.get(0).getIndex());
            }
            else  throw new ParseException("Expected END", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }

    }

    /**
     * Parses a case or default statement block from the {@code switch} rule. 
     * This method should only be called if the next tokens start the case or 
     * default block of a switch statement, aka {@code CASE} or {@code DEFAULT}.
     */
    public Ast.Statement.Case parseCaseStatement() throws ParseException {
    if(peek("CASE")){
        match("CASE");
        if((peek("NIL") || peek("TRUE") || peek("FALSE") || peek(Token.Type.INTEGER) || peek(Token.Type.DECIMAL) || peek(Token.Type.CHARACTER) || peek(Token.Type.STRING) || peek("(") || peek(Token.Type.IDENTIFIER))) {
            Ast.Expression expr=parseExpression();
            if(peek(":")){
                match(":");
                List<Ast.Statement> bloc = parseBlock(); //parseexception check needed?
                 return new Ast.Statement.Case(Optional.of(expr),bloc);
            }
            else{
                if(tokens.has(0)){
                    throw new ParseException("Expected :", tokens.get(0).getIndex());
                }
                else  throw new ParseException("Expected :", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }

        }
        else{
            if(tokens.has(0)){
                throw new ParseException("Expected Expression After Case", tokens.get(0).getIndex());
            }
            else  throw new ParseException("Expected Expression After Case", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }

    }
    if(peek("DEFAULT")){
        match("DEFAULT");
        List<Ast.Statement> bloc = parseBlock(); //add parseexception check?
        return new Ast.Statement.Case(Optional.empty(),bloc);
    }
    else{

        if(tokens.has(0)){
            throw new ParseException("Expected SWITCH Or DEFAULT", tokens.get(0).getIndex());
        }
        else  throw new ParseException("Expected SWITCH Or DEFAULT", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());

    }

    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Statement.While parseWhileStatement() throws ParseException {
        match("WHILE");
        if(!peek("NIL")&&!peek("TRUE")&&!peek("FALSE")&&!peek(Token.Type.INTEGER)&&!peek(Token.Type.DECIMAL)&&!peek(Token.Type.CHARACTER)&&!peek(Token.Type.STRING)&&!peek("(")&&!peek(Token.Type.IDENTIFIER)){

            if(tokens.has(0)){
                throw new ParseException("Expected Expression After While", tokens.get(0).getIndex());
            }
            else  throw new ParseException("Expected Expression After While", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
        Ast.Expression expr=parseExpression();
        if(peek("DO")){
            match("DO");


            if((peek("NIL") || peek("LET") || peek("SWITCH") || peek("IF") || peek("WHILE") || peek("RETURN") || peek("TRUE") || peek("FALSE") || peek(Token.Type.INTEGER) || peek(Token.Type.DECIMAL) || peek(Token.Type.CHARACTER) || peek(Token.Type.STRING) || peek("(") || peek(Token.Type.IDENTIFIER))) {
                List<Ast.Statement> bloc = parseBlock(); //add parseexception check?
                if (peek("END")) {
                    match("END");
                    return new Ast.Statement.While(expr,bloc);
                } else {
                    if(tokens.has(0)){
                        throw new ParseException("Expected END", tokens.get(0).getIndex());
                    }
                    else  throw new ParseException("Expected END", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());

                }
            }else{
                if(tokens.has(0)){
                    throw new ParseException("Expected Block", tokens.get(0).getIndex());
                }
                else  throw new ParseException("Expected Block", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());

            }


        }else{
            if(tokens.has(0)){
                throw new ParseException("Expected DO", tokens.get(0).getIndex());
            }
            else  throw new ParseException("Expected DO", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Statement.Return parseReturnStatement() throws ParseException {
        match("RETURN");
        if(!peek("NIL")&&!peek("TRUE")&&!peek("FALSE")&&!peek(Token.Type.INTEGER)&&!peek(Token.Type.DECIMAL)&&!peek(Token.Type.CHARACTER)&&!peek(Token.Type.STRING)&&!peek("(")&&!peek(Token.Type.IDENTIFIER)){
            if(tokens.has(0)){
                throw new ParseException("Expected Expression After Return", tokens.get(0).getIndex());
            }
            else  throw new ParseException("Expected Expression After Return", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
        Ast.Expression value=parseExpression();
        if (!match(";")) {  // changed to a match statement
            if(tokens.has(0)){
                throw new ParseException("Expected ; After Return Value", tokens.get(0).getIndex());
            }
            else  throw new ParseException("Expected ; After Return Value", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
        return new Ast.Statement.Return(value);
    }

    /**
     * Parses the {@code expression} rule.
     */
    public Ast.Expression parseExpression() throws ParseException {
        if(peek("LIST")||peek("VAR")||peek("VAL")||peek("FUN")||peek("LET")||peek("SWITCH")||peek("CASE")||peek("DEFAULT")||peek("END")||peek("IF")||peek("DO")||peek("ELSE")||peek("WHILE")||peek("RETURN")){

            if(tokens.has(0)){
                throw new ParseException("Unexpected Keyword", tokens.get(0).getIndex());
            }
            else  throw new ParseException("Unexpected Keyword", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
        }
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
            if(peek("LIST")||peek("VAR")||peek("VAL")||peek("FUN")||peek("LET")||peek("SWITCH")||peek("CASE")||peek("DEFAULT")||peek("END")||peek("IF")||peek("DO")||peek("ELSE")||peek("WHILE")||peek("RETURN")){

                if(tokens.has(0)){
                    throw new ParseException("Unexpected Keyword", tokens.get(0).getIndex());
                }
                else  throw new ParseException("Unexpected Keyword", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
            if(!peek("NIL")&&!peek("TRUE")&&!peek("FALSE")&&!peek(Token.Type.INTEGER)&&!peek(Token.Type.DECIMAL)&&!peek(Token.Type.CHARACTER)&&!peek(Token.Type.STRING)&&!peek("(")&&!peek(Token.Type.IDENTIFIER)){
                if(tokens.has(0)){
                    throw new ParseException("Expected Operand", tokens.get(0).getIndex());
                }
                else  throw new ParseException("Expected Operand", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
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
            if(peek("LIST")||peek("VAR")||peek("VAL")||peek("FUN")||peek("LET")||peek("SWITCH")||peek("CASE")||peek("DEFAULT")||peek("END")||peek("IF")||peek("DO")||peek("ELSE")||peek("WHILE")||peek("RETURN")){

                if(tokens.has(0)){
                    throw new ParseException("Unexpected Keyword", tokens.get(0).getIndex());
                }
                else  throw new ParseException("Unexpected Keyword", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
            if(!peek("NIL")&&!peek("TRUE")&&!peek("FALSE")&&!peek(Token.Type.INTEGER)&&!peek(Token.Type.DECIMAL)&&!peek(Token.Type.CHARACTER)&&!peek(Token.Type.STRING)&&!peek("(")&&!peek(Token.Type.IDENTIFIER)){
                if(tokens.has(0)){
                    throw new ParseException("Expected Operand", tokens.get(0).getIndex());
                }
                else  throw new ParseException("Expected Operand", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
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
            if(peek("LIST")||peek("VAR")||peek("VAL")||peek("FUN")||peek("LET")||peek("SWITCH")||peek("CASE")||peek("DEFAULT")||peek("END")||peek("IF")||peek("DO")||peek("ELSE")||peek("WHILE")||peek("RETURN")){

                if(tokens.has(0)){
                    throw new ParseException("Unexpected Keyword", tokens.get(0).getIndex());
                }
                else  throw new ParseException("Unexpected Keyword", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
            if(!peek("NIL")&&!peek("TRUE")&&!peek("FALSE")&&!peek(Token.Type.INTEGER)&&!peek(Token.Type.DECIMAL)&&!peek(Token.Type.CHARACTER)&&!peek(Token.Type.STRING)&&!peek("(")&&!peek(Token.Type.IDENTIFIER)){
                if(tokens.has(0)){
                    throw new ParseException("Expected Operand", tokens.get(0).getIndex());
                }
                else  throw new ParseException("Expected Operand", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
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
            if(peek("LIST")||peek("VAR")||peek("VAL")||peek("FUN")||peek("LET")||peek("SWITCH")||peek("CASE")||peek("DEFAULT")||peek("END")||peek("IF")||peek("DO")||peek("ELSE")||peek("WHILE")||peek("RETURN")){

                if(tokens.has(0)){
                    throw new ParseException("Unexpected Keyword", tokens.get(0).getIndex());
                }
                else  throw new ParseException("Unexpected Keyword", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
            if(!peek("NIL")&&!peek("TRUE")&&!peek("FALSE")&&!peek(Token.Type.INTEGER)&&!peek(Token.Type.DECIMAL)&&!peek(Token.Type.CHARACTER)&&!peek(Token.Type.STRING)&&!peek("(")&&!peek(Token.Type.IDENTIFIER)){
                if(tokens.has(0)){
                    throw new ParseException("Expected Operand", tokens.get(0).getIndex());
                }
                else  throw new ParseException("Expected Operand", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
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
            if(peek("LIST")||peek("VAR")||peek("VAL")||peek("FUN")||peek("LET")||peek("SWITCH")||peek("CASE")||peek("DEFAULT")||peek("END")||peek("IF")||peek("DO")||peek("ELSE")||peek("WHILE")||peek("RETURN")){

                if(tokens.has(0)){
                    throw new ParseException("Unexpected Keyword", tokens.get(0).getIndex());
                }
                else  throw new ParseException("Unexpected Keyword", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
            // First check if java supported identifier
            if (peek("TRUE")) {
                match("TRUE");
                return new Ast.Expression.Literal(true);
            } else if(peek("FALSE")) {
                match("FALSE");
                return new Ast.Expression.Literal(false);}
             else if (peek("NIL")) {
                match("NIL");
                return new Ast.Expression.Literal(null);}
            // else, match arguments
            String name = tokens.get(0).getLiteral();
            match(Token.Type.IDENTIFIER);
            if (peek("(")) {
                match("(");
                List<Ast.Expression> arguments = new ArrayList<>();
                while (!peek(")")) {
                    arguments.add(parseExpression());
                    if (peek(",")) {
                        match(",");
                        if(peek(")")){
                            throw new ParseException("Trailing Comma In Function Call", tokens.get(0).getIndex());
                        }
                    }
                }
                if(peek(")")){
                match(")");
                return new Ast.Expression.Function(name, arguments);} //parse exception missing )}
                     else{
                    if(tokens.has(0)){
                        throw new ParseException("Expected )", tokens.get(0).getIndex());
                    }
                    else  throw new ParseException("Expected )", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
                         }

            }else if (peek("[")) {
                    match("[");
                    Ast.Expression value = parseExpression();
                    match("]");
                    return new Ast.Expression.Access(Optional.of(value), name);
                }
            else {
               // System.out.println("somehow?");
                return new Ast.Expression.Access(Optional.empty(), name);
            }
        } else if (peek("(")) {
            match("(");
            Ast.Expression expression = parseExpression();
            if (!peek(")")) {
                if(tokens.has(0)){
                    throw new ParseException("Expected )", tokens.get(0).getIndex());
                }
                else  throw new ParseException("Expected )", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
            }
            return new Ast.Expression.Group(expression); // Wrap the expression in a Group
        }
        else {
            if(tokens.has(0)){
                throw new ParseException("Expected Primary Expression", tokens.get(0).getIndex());
            }
            else  throw new ParseException("Expected Primary Expression", tokens.get(-1).getIndex()+tokens.get(-1).getLiteral().length());
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
                case 'r': return '\r';
                case 'b': return '\b';
                case '\"': return '\"';
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
                .replace("\\\\", "\\")
                .replace("\\b", "\b")
                .replace("\\r", "\r")
                .replace("\\\'", "\'");
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
           } else if (patterns[i] instanceof String){
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
