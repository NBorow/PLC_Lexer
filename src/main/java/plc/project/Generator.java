package plc.project;

import java.io.PrintWriter;

public final class Generator implements Ast.Visitor<Void> {

    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }

    private void print(Object... objects) {
        for (Object object : objects) {
            if (object instanceof Ast) {
                visit((Ast) object);
            } else {
                writer.write(object.toString());
            }
        }
    }

    private void newline(int indent) {
        writer.println();
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    @Override
    public Void visit(Ast.Source ast) {
        print("public class Main {");   // class header
        newline(0); ++indent; // formatting
        if(!ast.getGlobals().isEmpty()) {   // Check if source has globals
            // Printing globals
            for (int i = 0; i < ast.getGlobals().size(); ++i) {
                newline(indent);
                print(ast.getGlobals().get(i));
            }
            newline(0);
        }
        newline(indent);    // Start of Java main
        print("public static void main(String[] args) {");
        newline(++indent);
        // Check if source has functions
        if(ast.getFunctions().isEmpty()) throw new RuntimeException("Error: Source has no functions");
        // Check if main is present
        short flag = 0;
        for(int i = 0; i < ast.getFunctions().size(); ++i) {
            if(ast.getFunctions().get(i).getName().equals("main")) ++flag;
        }
        if(flag == 0) throw new RuntimeException("Error: No main function found.");
        print("System.exit(new Main().main());");
        newline(--indent);
        print("}"); // End of Java main
        // Printing Functions
        for(int i = 0; i < ast.getFunctions().size(); ++i) {
            newline(0);
            newline(indent);
            print(ast.getFunctions().get(i));    // visit each function
        }
        newline(0);
        newline(--indent);
        print("}"); // End of Main class
        return null;
    }

    @Override
    public Void visit(Ast.Global ast) {
        if(!ast.getMutable()) print("final ");  // If immutable, print final
        print(ast.getVariable().getType().getJvmName());    // Type
        if(ast.getValue().isPresent()
                && ast.getValue().get() instanceof Ast.Expression.PlcList) {
            print("[]");   // specify variable is an array
        }
        print(" ", ast.getVariable().getJvmName()); // variable name
        if(ast.getValue().isPresent()) {
            // If value is present, print equal sign and value
            print(" = ", ast.getValue().get());
        }
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Function ast) {
        if(ast.getReturnTypeName().isPresent()) {
            print(ast.getFunction().getReturnType().getJvmName(), " ");
        } else print("Void ");    // I'm inferring here
        print(ast.getFunction().getName(), "(");  // function name
        // ***potential bug with parameters and parameter types being different sizes
        if(!ast.getParameters().isEmpty()) {
            print(ast.getFunction().getParameterTypes().getFirst().getJvmName(),
                    " ", ast.getParameters().getFirst());
            for (int i = 1; i < ast.getParameters().size(); ++i) {
                print(", ", ast.getFunction().getParameterTypes().get(i).getJvmName(),
                        " ", ast.getParameters().get(i));
            }
        }
        print(") {");   // close parameters and start body
        if(!ast.getStatements().isEmpty()) {
            ++indent;
            for (int i = 0; i < ast.getStatements().size(); ++i) {
                newline(indent);
                print(ast.getStatements().get(i));
            }
            newline(--indent);    // exiting statements/body
        }
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        print(ast.getExpression(),";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        print(ast.getVariable().getType().getJvmName(), " ",
                ast.getVariable().getJvmName());
        if (ast.getValue().isPresent()) {
            print(" = ", ast.getValue().get());
        }
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        print(ast.getReceiver()," = ",ast.getValue(),";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        // Print the if statement condition
        print("if (", ast.getCondition(), ") {");   // Visits the condition part of the if statement
        ++indent;

        // Visit each statement in the 'then' block
        for (Ast.Statement statement : ast.getThenStatements()) {
            newline(indent);    // Ensures each statement starts on a new line
            print(statement);
        }

        newline(--indent);
        print("}");

        // Check if there is an else block
        if (!ast.getElseStatements().isEmpty()) {
            print(" else {");
            ++indent;  // Increase indentation for the body of the else block

            // Visit each statement in the 'else' block
            for (Ast.Statement statement : ast.getElseStatements()) {
                newline(indent);
                print(statement);
            }

            newline(--indent); // Decrease indentation back to the previous level
            print("}");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Switch ast) {
        print("switch (", ast.getCondition(), ") {");
        newline(++indent);
        for (Ast.Statement.Case caseStatement : ast.getCases()) {
            visit(caseStatement);
        }
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Case ast) {
        if (ast.getValue().isPresent()) {
            // Handle a case with a specific value
            print("case ", ast.getValue().get(), ":");
        } else {
            // Handle the default case
            print("default:");
        }
        // Increase indentation for the statements in the case
        ++indent;
        for (Ast.Statement statement : ast.getStatements()) {
            newline(indent);
            print(statement);
        }
        if (ast.getValue().isPresent()) {
            // Only add break for non-default cases
            newline(indent);
            print("break;");
            newline(--indent);
        } else newline(indent - 2); // else end of the cases
        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        print("while (", ast.getCondition(), ") {");
        if(!ast.getStatements().isEmpty()) {
            newline(++indent);  // Increase indent for the block of the while loop
            for (int i = 0; i < ast.getStatements().size(); i++) {
                if(i != 0){
                    newline(indent);
                }
                print(ast.getStatements().get(i));  // Recursively visit each statement in the while block
            }
            newline(--indent);  // Decrease indent after ending the block
        }
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        print("return ", ast.getValue(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        if (ast.getType().equals(Environment.Type.BOOLEAN)) {
            print(ast.getLiteral().toString());  // Directly print boolean literals
        } else if (ast.getType().equals(Environment.Type.STRING)) {
            print("\"", ast.getLiteral().toString(), "\"");  // Add quotes around string literals
        } else if (ast.getType().equals(Environment.Type.CHARACTER)) {
            print("'", ast.getLiteral().toString(), "'");  // Add single quotes for characters
        } else if (ast.getType().equals(Environment.Type.NIL)) {
            //print(ast.getType().getJvmName());   // Can't use toString on null literal (DNE)
            print("null");
        } else {
            print(ast.getLiteral().toString());  // Directly print numeric literals
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        print("(", ast.getExpression(), ")");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        // Append the operator
        switch (ast.getOperator()) {
            case "&&":
                print(ast.getLeft(), " && ", ast.getRight()); break;
            case "||":
                print(ast.getLeft(), " || ", ast.getRight()); break;
            case "+":
                print(ast.getLeft(), " + ", ast.getRight()); break;
            case "-":
                print(ast.getLeft(), " - ", ast.getRight()); break;
            case "*":
                print(ast.getLeft(), " * ", ast.getRight()); break;
            case "/":
                print(ast.getLeft(), " / ", ast.getRight()); break;
            case "<":
                print(ast.getLeft(), " < ", ast.getRight()); break;
            case ">":
                print(ast.getLeft(), " > ", ast.getRight()) ; break;
            case "==":
                print(ast.getLeft(), " == ", ast.getRight()); break;
            case "!=":
                print(ast.getLeft(), " != ", ast.getRight()); break;
            case "^":{
                print("Math.pow(", ast.getLeft(), ", ", ast.getRight(), ")");
                break;
            }
            default: throw new RuntimeException("Unsupported operator: " + ast.getOperator());
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {
        // If the expression includes an index, it represents array access
        if (ast.getOffset().isPresent()) {
            print(ast.getVariable().getJvmName(), "[", ast.getOffset().get(), "]");
        } else {
            // Otherwise, it's a simple variable access
            print(ast.getVariable().getJvmName());
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        // Append the function name, which is the JVM name of the function stored in the AST
        print(ast.getFunction().getJvmName(), "(");
        // Process each argument in the function call
        for (int i = 0; i < ast.getArguments().size(); i++) {
            // Visit the argument to generate its Java code
            print(ast.getArguments().get(i));
            // If this is not the last argument, print a comma and space for separation
            if (i < ast.getArguments().size() - 1) {
                print(", ");
            }
        }
        // Close the argument list
        print(")");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.PlcList ast) {
        // Start the list with an open curly brace
        print("{");
        // Iterate through the expressions in the PLC List
        if (!ast.getValues().isEmpty()) {
            // Visit the first expression
            print(ast.getValues().get(0));
            // Visit and comma-separate the rest of the expressions
            for (int i = 1; i < ast.getValues().size(); i++) {
                print(", ");
                print(ast.getValues().get(i));
            }
        }
        // Close the list with a closing curly brace
        print("}");
        return null;
    }
}
