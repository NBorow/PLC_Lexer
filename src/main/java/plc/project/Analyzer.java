package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

import static plc.project.Ast.*;

/**
 * See the specification for information about what the different visit
 * methods should do.
 */

public final class Analyzer implements Ast.Visitor<Void> {

    private Environment.Type currentFunctionReturnType=null;
    public Scope scope;
    private Function function;

    public Analyzer(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }


    @Override
    public Void visit(Source ast) {

        boolean mainFunctionExists = false;
        Environment.Type mainFunctionReturnType = null;

        // Visit and define all globals.
        for (Global global : ast.getGlobals()) {
            visit(global);
        }

        // Visit and define all functions.
        for (Function function : ast.getFunctions()) {
            visit(function);
            // Check if there is a main function with the correct signature.
            if ("main".equals(function.getName()) && function.getParameters().isEmpty()) {
                mainFunctionExists = true;
                mainFunctionReturnType = this.scope.lookupFunction("main", 0).getReturnType();
            }
        }

        // Throw an exception if the main function does not exist.
        if (!mainFunctionExists) {
            throw new RuntimeException("A main function with no parameters must be defined.");
        }

        // Additionally, the main function must have an Integer return type.
        if (!Environment.Type.INTEGER.equals(mainFunctionReturnType)) {
            throw new RuntimeException("The main function must return Integer.");
        }

        return null;

    }

    @Override
    public Void visit(Global ast) {
        // Determine the type of the global variable.
        Environment.Type type;

        if(ast.getTypeName().equals("Boolean")){type=Environment.Type.BOOLEAN;}
        else if(ast.getTypeName().equals("Character")){type=Environment.Type.CHARACTER;}
        else if(ast.getTypeName().equals("Decimal")){type=Environment.Type.DECIMAL;}
        else if(ast.getTypeName().equals("Integer")){type=Environment.Type.INTEGER;}
        else if(ast.getTypeName().equals("String")){type=Environment.Type.STRING;}
        else if(ast.getTypeName().equals("Any")){type=Environment.Type.ANY;}
        else if(ast.getTypeName().equals("Comparable")){type=Environment.Type.COMPARABLE;}
        else{throw new RuntimeException("Global Variable Type Could Not Be Determined.");}

Boolean mut=ast.getMutable();
        // If the global has an initial value, visit it to analyze and ensure it's valid.
        if (ast.getValue().isPresent()) {
            visit(ast.getValue().get());
            // After visiting, check if the value's type is assignable to the global's type.
            Environment.Type valueType = ast.getValue().get().getType();
            Analyzer.requireAssignable(type, valueType);
        }

        // Define the variable in the current scope.
        Environment.Variable variable = scope.defineVariable(ast.getName(), ast.getName(), type,mut, Environment.NIL);

        // Set the variable in the Ast.Global node for later reference.
        ast.setVariable(variable);

        return null;
    }


    @Override
    public Void visit(Function ast) {
        // Convert parameter type names and return type name to Environment.Types
        List<Environment.Type> parameterTypes = ast.getParameterTypeNames().stream()
                .map(this::getTypeFromEnvironment)
                .collect(Collectors.toList());

        Environment.Type returnType = ast.getReturnTypeName().map(this::getTypeFromEnvironment)
                .orElse(Environment.Type.NIL);

        currentFunctionReturnType=returnType;

        scope.defineFunction(ast.getName(),ast.getName(), parameterTypes, returnType, args->Environment.NIL);
        Environment.Function temp=scope.lookupFunction(ast.getName(),ast.getParameters().size());
        ast.setFunction(temp);


        // Create a new scope for the function's body
        Scope originalScope = this.scope;
        this.scope = new Scope(this.scope);

        // Define parameters as variables in the new scope
        for (int i = 0; i < ast.getParameters().size(); i++) {

            scope.defineVariable(ast.getName(), ast.getName(), parameterTypes.get(i), true, Environment.NIL);
        }

        // Visit statements within the function's body
        for (Statement statement : ast.getStatements()) {
            visit(statement);
        }
System.out.println(ast);
        // Restore the; original scope
        this.scope = originalScope;
        currentFunctionReturnType = null;
        return null;
    }

    public static class ReturnException extends RuntimeException {
        private final Environment.PlcObject value;

        public ReturnException(Environment.PlcObject value) {
            this.value = value;
        }

        public Environment.PlcObject getValue() {
            return value;
        }
    }

    private Environment.Type getTypeFromEnvironment(String typeName) {
        return switch (typeName) {
            case "Boolean" -> Environment.Type.BOOLEAN;
            case "Integer" -> Environment.Type.INTEGER;
            case "Decimal" -> Environment.Type.DECIMAL;
            case "Character" -> Environment.Type.CHARACTER;
            case "String" -> Environment.Type.STRING;
            case "Nil"->Environment.Type.NIL;
            default -> throw new RuntimeException("Unknown type: " + typeName);
        };
    }

@Override
public Void visit(Statement.Expression ast) {
    // Visit the expression to perform any necessary analysis or validation.
   visit(ast.getExpression());

    // Ensure the expression is a function call.
    if (!(ast.getExpression() instanceof Expression.Function)) {
        throw new RuntimeException("Only function calls are allowed as standalone expressions in statements.");
    }

    // Since the expression is valid and no additional actions are needed, return null.
    return null;
}


    @Override
    public Void visit(Statement.Declaration ast) {
        // Determine the type of the declaration, either from the explicit type name or from the value assigned.
        Environment.Type type = null;
        if (ast.getTypeName().isPresent()) {
            type = getTypeFromEnvironment(ast.getTypeName().get());
        }

        // If a value is present in the declaration, visit the value to perform type checking and infer the type if not explicitly provided.
        if (ast.getValue().isPresent()) {
            visit(ast.getValue().get());
            Environment.Type valueType = ast.getValue().get().getType();

            // If the type was explicitly provided, ensure the value's type is assignable to the declared type.
            if(type!=null){
            requireAssignable(valueType,type);}

            // If no type was explicitly provided, infer the type from the value.
            if (type == null) {
                type = valueType;
            }
        } else if (type == null) {
            // If no type and no value were provided, it's an error.
            throw new RuntimeException("Declaration must have either a type or an initializing value.");
        }

        // Ensure the variable is not already declared in the current scope and add it to the scope.
        try{
            scope.lookupVariable(ast.getName());
        }
        catch(RuntimeException e){
            if(e.getMessage().startsWith("The variable")){
                //System.out.print("test");
                if(ast.getValue().isPresent()){
                    System.out.println("test");
                    scope.defineVariable(ast.getName(), ast.getName(), type, true, Environment.create(ast.getValue()));
                }
                 else{
                scope.defineVariable(ast.getName(), ast.getName(), type, true, Environment.NIL);}
            }
            else throw e;
        }
        /*if (scope.lookupVariable(ast.getName())!=null) {
            throw new RuntimeException("Variable '" + ast.getName() + "' is already declared in this scope.");
        }*/
        ast.setVariable(scope.lookupVariable(ast.getName()));

        return null;
    }

    @Override
    public Void visit(Statement.Assignment ast) {
        // First, visit the receiver to analyze it.
        visit(ast.getReceiver());

        // Ensure the receiver is an Ast.Expression.Access since only variables or array elements can be assigned new values.
        if (!(ast.getReceiver() instanceof Expression.Access)) {
            throw new RuntimeException("The left side of an assignment must be a variable.");
        }

        // Visit the value to be assigned to perform type checking and analysis.
        visit(ast.getValue());

        // Retrieve the variable associated with the receiver access expression.
        Environment.Variable variable = ((Expression.Access) ast.getReceiver()).getVariable();

        // Check if the variable is mutable.
        if (!variable.getMutable()) {
            throw new RuntimeException("Cannot assign a new value to an immutable variable: " + variable.getName());
        }

        // Check if the value's type is assignable to the variable's type.
        Environment.Type valueType = ast.getValue().getType();
        requireAssignable(variable.getType(), valueType);


        return null;
    }

    @Override
    public Void visit(Statement.If ast) {
        // First, visit and analyze the condition.
        visit(ast.getCondition());

        // Ensure the condition evaluates to a Boolean.
        if (!ast.getCondition().getType().equals(Environment.Type.BOOLEAN)) {
            throw new RuntimeException("The condition of an 'if' statement must evaluate to a Boolean.");
        }

        // Check that the 'then' block contains at least one statement.
        if (ast.getThenStatements().isEmpty()) {
            throw new RuntimeException("The 'then' block of an 'if' statement cannot be empty.");
        }

        // Create a new scope and visit the 'then' block.
        scope = new Scope(scope); // Enter a new scope
        for (Statement thenStmt : ast.getThenStatements()) {
            visit(thenStmt);
        }
        scope = scope.getParent(); // Exit to the previous scope

        // If there is an 'else' block, visit it in a new scope as well.
        if (ast.getElseStatements() != null && !ast.getElseStatements().isEmpty()) {
            scope = new Scope(scope); // Enter a new scope for the 'else' block
            for (Statement elseStmt : ast.getElseStatements()) {
                visit(elseStmt);
            }
            scope = scope.getParent(); // Exit to the previous scope
        }

        return null;
    }

    @Override
    public Void visit(Statement.Switch ast) {

        visit(ast.getCondition());
        Environment.Type conditionType = ast.getCondition().getType();
        if(conditionType == null) {
            throw new RuntimeException("Condition type is uninitialized.");
        }

        for (Statement.Case caseStmt : ast.getCases()) {
            // For each case, if there's a value, ensure its type matches the condition's type.
            caseStmt.getValue().ifPresent(value -> {
                visit(value); // This should set the value's type in the AST.
                Environment.Type valueType = value.getType();

                if (!conditionType.equals(valueType)) {
                    throw new RuntimeException("Case value type does not match switch condition type.");
                }
            });

            // Now handle the case's statements.
            scope = new Scope(scope);
            try {
                for (Statement statement : caseStmt.getStatements()) {
                    visit(statement);
                }
            } finally {
                // Ensure the scope is always reverted back to the parent to avoid scope leaks.
                scope = scope.getParent();
            }
        }
        return null;
    }

    @Override
    public Void visit(Statement.Case ast) {
        try {
            scope=new Scope(scope);
            for (Statement statement : ast.getStatements()) {
                // Visit each statement in the case within the new scope.
                visit(statement);
            }
        } finally {
            // Restore the original scope after exiting the case block.
            scope = scope.getParent();
        }

        return null;
    }

    @Override
    public Void visit(Statement.While ast) {
        visit(ast.getCondition());
        requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());
        try  {
            scope = new Scope(scope);

            // Visit each statement in the body of the while loop within the new scope.
            for (Statement statement : ast.getStatements()) {
                visit(statement);
            }
        } finally {
            scope = scope.getParent();
            // Restore the previous scope after the loop body has been executed.
            // This is handled automatically by the try-with-resources statement.
        }

        return null;
    }

    @Override
    public Void visit(Statement.Return ast) {
        // If there's a value to return, validate its type
        if (ast.getValue()!=null) {
            // Visit the return value expression to ensure it's analyzed and its type is set
            visit(ast.getValue());

            // Retrieve the actual type of the return value from the analyzed expression
            Environment.Type actualReturnType = ast.getValue().getType();

            // Use requireAssignable to ensure the actual return type matches the expected return type
          requireAssignable(currentFunctionReturnType, actualReturnType);
        } else {
            // If no value is returned, ensure the function's expected return type is 'Nil' or compatible with 'Nil'
            if (!currentFunctionReturnType.equals(Environment.Type.NIL)) {
                throw new RuntimeException("Missing return value in a function expected to return " + currentFunctionReturnType);
            }
        }

        // No direct return value manipulation here; just type checking and validation
        return null;
    }

    @Override
    public Void visit(Expression.Literal ast) {

        Object value = ast.getLiteral();
        Environment.Type type;

        if (value == null) {
            type = Environment.Type.NIL;
        } else if (value instanceof Boolean) {
            type = Environment.Type.BOOLEAN;
        } else if (value instanceof Character) {
            type = Environment.Type.CHARACTER;
        } else if (value instanceof String) {
            type = Environment.Type.STRING;
        } else if (value instanceof BigInteger) {
            BigInteger bigIntValue = (BigInteger) value;
            // Check if the BigInteger fits within Java's int range for an Integer type
            if (bigIntValue.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0 &&
                    bigIntValue.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0) {
                type = Environment.Type.INTEGER;
            } else {
                throw new RuntimeException("Integer literal out of range: " + bigIntValue);
            }
        } else if (value instanceof BigDecimal) {
            BigDecimal bigDecValue = (BigDecimal) value;
            // Use doubleValue() to check if BigDecimal fits within double's range, with an awareness of the potential for inaccuracy.
            double doubleValue = bigDecValue.doubleValue();
            if (Double.isFinite(doubleValue)) {
                type = Environment.Type.DECIMAL;
            } else {
                throw new RuntimeException("Decimal literal out of range: " + bigDecValue);
            }
        } else {
            throw new RuntimeException("Unrecognized literal type: " + value);
        }

        ast.setType(type);
        return null;

    }


    @Override
    public Void visit(Expression.Group ast) {

        // First, visit the contained expression to ensure it's analyzed.
        visit(ast.getExpression());

        // The type of the group expression is the type of its contained expression.
        Environment.Type innerType = ast.getExpression().getType();

        if (!(ast.getExpression() instanceof Expression.Binary)) {
            throw new RuntimeException("Only binary expressions can be grouped.");
        }

        ast.setType(innerType);

        return null;
    }

    @Override
    public Void visit(Expression.Binary ast) {

        // Visit both sides to ensure they're analyzed.
        visit(ast.getLeft());
        visit(ast.getRight());

        Environment.Type leftType = ast.getLeft().getType();
        Environment.Type rightType = ast.getRight().getType();
        String operator = ast.getOperator();

        switch (operator) {
            case "&&":
                requireAssignable(Environment.Type.BOOLEAN, leftType);
                requireAssignable(Environment.Type.BOOLEAN, rightType);
                ast.setType(Environment.Type.BOOLEAN);
                break;
            case "||":
                requireAssignable(Environment.Type.BOOLEAN, leftType);
                requireAssignable(Environment.Type.BOOLEAN, rightType);
                ast.setType(Environment.Type.BOOLEAN);
                break;
            case "<":
                if( (leftType.equals(Environment.Type.INTEGER)||leftType.equals(Environment.Type.DECIMAL)||leftType.equals(Environment.Type.CHARACTER)||leftType.equals(Environment.Type.STRING))&&leftType.equals(rightType)){
                    ast.setType(Environment.Type.BOOLEAN);
                }
                else {
                    throw new RuntimeException("Invalid operands for '<' operation.");
                }
                break;
            case ">":
                if((leftType.equals(Environment.Type.INTEGER)||leftType.equals(Environment.Type.DECIMAL)||leftType.equals(Environment.Type.CHARACTER)||leftType.equals(Environment.Type.STRING))&&leftType==rightType){
                    ast.setType(Environment.Type.BOOLEAN);
                }
                else {
                    throw new RuntimeException("Invalid operands for '>' operation.");
                }
                break;
            case "==":
                if((leftType.equals(Environment.Type.INTEGER)||leftType.equals(Environment.Type.DECIMAL)||leftType.equals(Environment.Type.CHARACTER)||leftType.equals(Environment.Type.STRING))&&leftType==rightType){
                    ast.setType(Environment.Type.BOOLEAN);
                }
                else {
                    throw new RuntimeException("Invalid operands for '==' operation.");
                }
                break;
            case "!=":
                if((leftType.equals(Environment.Type.INTEGER)||leftType.equals(Environment.Type.DECIMAL)||leftType.equals(Environment.Type.CHARACTER)||leftType.equals(Environment.Type.STRING))&&leftType==rightType){
                    ast.setType(Environment.Type.BOOLEAN);
                }
                else {
                    throw new RuntimeException("Invalid operands for '!=' operation.");
                }
                break;
            case "+":
                if (leftType.equals(Environment.Type.STRING) || rightType.equals(Environment.Type.STRING)) {
                    ast.setType(Environment.Type.STRING);
                } else if (leftType.equals(rightType) && (leftType.equals(Environment.Type.INTEGER) || leftType.equals(Environment.Type.DECIMAL))) {
                    ast.setType(leftType); // or rightType, since they're the same.
                } else {
                    throw new RuntimeException("Invalid operands for '+' operation.");
                }
                break;
            case "-":
                if (leftType.equals(rightType) && (leftType.equals(Environment.Type.INTEGER) || leftType.equals(Environment.Type.DECIMAL))) {
                    ast.setType(leftType); // or rightType, since they're the same.
                } else {
                    throw new RuntimeException("Invalid operands for '-' operation.");
                }
                break;
            case "*":
                if (leftType.equals(rightType) && (leftType.equals(Environment.Type.INTEGER) || leftType.equals(Environment.Type.DECIMAL))) {
                    ast.setType(leftType); // or rightType, since they're the same.
                } else {
                    throw new RuntimeException("Invalid operands for '*' operation.");
                }
                break;
            case "/":
                if (leftType.equals(rightType) && (leftType.equals(Environment.Type.INTEGER) || leftType.equals(Environment.Type.DECIMAL))) {
                    ast.setType(leftType); // or rightType, since they're the same.
                } else {
                    throw new RuntimeException("Invalid operands for '/' operation.");
                }
                break;
            case "^":
                if (leftType.equals(rightType) && (leftType.equals(Environment.Type.INTEGER))){
                ast.setType(leftType);
            }
                else {
                    throw new RuntimeException("Invalid operands for '^' operation.");
                }
                break;
            default:
                throw new RuntimeException("Unsupported binary operator.");
        }

        return null;


    }

    @Override
    public Void visit(Expression.Access ast) {

        // Check if the access is to a list with an index.
        if (ast.getOffset().isPresent()) {
            Expression offsetExpression = ast.getOffset().get();
            // Recursively visit the offset to ensure it's analyzed.
            visit(offsetExpression);
            // Ensure the offset is of type Integer.

            if (!offsetExpression.getType().equals(Environment.Type.INTEGER)) {
                throw new RuntimeException("List index must be of type Integer");
            }
        }

        // Retrieve the variable from the current scope using the name.
        Environment.Variable variable = scope.lookupVariable(ast.getName());
        // Set the variable in the Access expression for later use.
        ast.setVariable(variable);

        // No return value is needed for Void visit methods.
        return null;

    }

    @Override
    public Void visit(Expression.Function ast) {
        Environment.Function function = scope.lookupFunction(ast.getName(), ast.getArguments().size());
        ast.setFunction(function);

        // Ensure that provided arguments match the expected parameter types.
        List<Environment.Type> parameterTypes = function.getParameterTypes();
        for (int i = 0; i < ast.getArguments().size(); i++) {
            Expression argument = ast.getArguments().get(i);
            visit(argument); // Validate and infer types for arguments.

            Environment.Type argumentType = argument.getType();
            Environment.Type expectedType = parameterTypes.get(i);

            // Check if the argument type is assignable to the parameter type.
            requireAssignable(expectedType, argumentType);

        }

        // Set the expression type to the function's return type.
       // ast.setType(function.getReturnType());
        return null;
    }




    @Override
    public Void visit(Expression.PlcList ast) {
        if (ast.getValues().isEmpty()) {
            throw new RuntimeException("List cannot be empty");
        }

        // Initially, we don't know the list's type.
        Environment.Type listType = null;

        for (Expression value : ast.getValues()) {
            visit(value); // Validate each element.
            if (listType == null) {
                // Set the initial list type based on the first element.
                listType = value.getType();
            } else if (!value.getType().equals(listType)) {
                // Ensure all elements are of the same type.
                throw new RuntimeException("All elements in the list must have the same type");
            }
        }

        // Optionally, set the type for the entire list expression.
        // This might involve setting it to a specific list type in your type system.

        ast.setType(listType);

        return null;
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {
       if(target==type){
        }
        else if(target==Environment.Type.ANY&&(type==Environment.Type.BOOLEAN||type==Environment.Type.CHARACTER||type==Environment.Type.DECIMAL||type==Environment.Type.INTEGER||type==Environment.Type.STRING)){}
        else if (target==Environment.Type.COMPARABLE&&(type==Environment.Type.CHARACTER||type==Environment.Type.DECIMAL||type==Environment.Type.INTEGER||type==Environment.Type.STRING)){}
        else{
            throw new RuntimeException("Not Assignable Types");
        }

    }

}
