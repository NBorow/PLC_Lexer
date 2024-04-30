package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope = new Scope(null);

    public Interpreter(Scope parent) {
        scope = new Scope(parent);  // define new scope
        scope.defineFunction("print", 1, args -> {
            System.out.println(args.get(0).getValue());
            return Environment.NIL;
        }); // defines new function in scope, specifying func name, arity, and definition
    }

    public Scope getScope() {
        return scope;
    }

    // Evaluates globals followed by functions
    // FUN main() DO RETURN 0; END => 0
    // VAR x = 1; VAR y = 10; FUN main() DO x+y; END => NIL
    // ^^The statement x+y is evaluated, but not returned
    // If a function does not exist within source, the evaluation fails
    @Override
    public Environment.PlcObject visit(Ast.Source ast) {
        ast.getGlobals().forEach(this::visit);
        ast.getFunctions().forEach(this::visit);

        Environment.Function mainFunction = scope.lookupFunction("main", 0);
        if (mainFunction != null) {
            return mainFunction.invoke(List.of()); // Call main with no arguments
        }

        return Environment.NIL; // If no main function or other return condition is specified
    }



    // Defines a variable in the current scope
    // Assign value as NIL if no initial value is defined/required
    // Returns NIL
    // VAL name = 1;, scope = {} => NIL, scope = {name = 1}
    // LIST list = [1, 5, 10];, scope = {} => NIL, scope = {list = [1, 5, 10]}
    @Override
    public Environment.PlcObject visit(Ast.Global ast) {
        if(ast.getValue().isEmpty()) scope.defineVariable(ast.getName(), ast.getMutable(), Environment.NIL);
        else scope.defineVariable(ast.getName(), ast.getMutable(), visit(ast.getValue().get()));
        return Environment.NIL;
    }

    // Defines a function in the current scope
    // Callback function (lambda) should implement the behavior of calling this function
    // - Set the scope to be a new child of the scope where the function was defined
    // - Define variables for the incoming arguments, using the parameter name
    // - Evaluate the function's statements
    // -- Return the value in a Return exception or NIL
    // visit(Ast.Function) should itself return NIL
    // FUN square(x) DO RETURN x*x; END => NIL, scope={square=...}
    // ^^evaluating square(10) returns 100
    @Override
    public Environment.PlcObject visit(Ast.Function ast) {


            Scope definingScope = this.scope; // Capture the current scope as the defining scope of the function.

            // Define the function with a lambda that uses the captured defining scope.
            scope.defineFunction(ast.getName(), ast.getParameters().size(), args -> {
                Scope previousScope = this.scope; // Save the current execution scope.
                this.scope = new Scope(definingScope); // Switch to a new scope based on the defining scope.
                try {
                    // Define parameters in the new scope based on the defining scope.
                    for (int i = 0; i < ast.getParameters().size(); i++) {
                        this.scope.defineVariable(ast.getParameters().get(i), true, args.get(i));
                    }
                    // Execute the function body.
                    for (Ast.Statement statement : ast.getStatements()) {
                        visit(statement);
                    }
                } catch (Return e) {
                    return e.value; // Return Lambda Function
                } finally {
                    this.scope = previousScope; // Restore the previous execution scope.
                }
                return Environment.NIL; // Default return if no explicit RETURN statement is encountered.
            });
            return Environment.NIL; // Function definition does not produce a runtime value.
    }

    // Evaluates the expression => Returns NIL
    // print ("Hello, World!"); => NIL, prints Hello, World!
    @Override
    public Environment.PlcObject visit(Ast.Statement.Expression ast) {
        visit(ast.getExpression());
        return Environment.NIL;
    }

    // Defines a local variable in the current scope, defaulting to NIL
    // Returns NIL
    // Similar to Ast.Global, but within different scope and always mutable
    // LET name = 1;, scope={} => NIL, scope={name=1}
    @Override
    public Environment.PlcObject visit(Ast.Statement.Declaration ast) {
        Optional optional=ast.getValue();
        Boolean present=optional.isPresent();
        if(present){
            Ast.Expression expr=(Ast.Expression) optional.get();
            scope.defineVariable(ast.getName(),true,visit(expr));
        }else{
            scope.defineVariable(ast.getName(),true,Environment.NIL);
        }
        return Environment.NIL;
    }

    // VERIFY RECEIVER IS AN Ast.Expression.Access
    // any other type will cause evaluation to fail
    // Lookup and set the variable in the current scope
    // - when modifying list, use offset to determine the index
    // Assignments to immutable variable will fail
    // Returns NIL
    // variable = 1;, scope={variable="variable"} => NIL, scope={variable = 1}
    @Override
    public Environment.PlcObject visit(Ast.Statement.Assignment ast) {
        if (!(ast.getReceiver() instanceof Ast.Expression.Access)) {
            throw new RuntimeException("The left-hand side of an assignment must be a variable access.");
        }

        Ast.Expression.Access access = (Ast.Expression.Access) ast.getReceiver();
        Environment.Variable variable = scope.lookupVariable(access.getName());

        // Directly evaluate the right-hand side (value) of the assignment.
        Environment.PlcObject value = visit(ast.getValue());

        if (!variable.getMutable()) {
            throw new RuntimeException("Cannot assign to an immutable variable: " + access.getName());
        }

        if (access.getOffset().isPresent()) {
            // Handling list assignmen variable.
            List<Object>list=requireType(List.class, variable.getValue());
            // Evaluate the offset to get the index for list access
            int index = requireType(BigInteger.class, visit(access.getOffset().get())).intValue();

            // Ensure the index is within the list bounds
            if (index < 0 || index >= list.size()) {
                throw new RuntimeException("List index out of bounds: " + index);
            }
            list.set(index,value.getValue());
        } else {
            // Regular variable assignment
            variable.setValue(value);
        }

        return Environment.NIL; // The assignment itself does not produce a value.
    }



    // Ensure the condition evaluates to a Boolean [requireType]
    // otherwise... FAIL
    // if inside new scope [parent exists?] => evaluate thenStatements
    // otherwise, evaluate elseStatements
    // Returns NIL
    @Override
    public Environment.PlcObject visit(Ast.Statement.If ast) {

        Environment.PlcObject condition = visit(ast.getCondition());
        Boolean conditionValue = requireType(Boolean.class, condition);
        try {
            scope = new Scope(scope);   // Defining new scope for function
            if (conditionValue) {
                ast.getThenStatements().forEach(this::visit);
            } else {
                ast.getElseStatements().forEach(this::visit);
            }

        }

        finally {
            scope = scope.getParent();  // Update Scope before Exiting
        }
        return Environment.NIL;
    }


    // Inside of a new scope, if the condition is equivalent to a CASE value, evaluate the corresponding statements
    // Otherwise evaluate the statements of the DEFAULT
    // Returns NIL
    @Override
    public Environment.PlcObject visit(Ast.Statement.Switch ast) {

        Environment.PlcObject conditionValue = visit(ast.getCondition());
        boolean matched = false;

        try {
            scope = new Scope(scope);   // Defining new scope for function
            for (Ast.Statement.Case caseStmt : ast.getCases()) {
                // If we've already matched, break out of the loop to prevent executing more than one case
                if (matched) {
                    break;
                }

                if (caseStmt.getValue().isPresent()) {
                    Environment.PlcObject caseValue = visit(caseStmt.getValue().get());

                    // Compare the actual values of the condition and the case
                    if (Objects.equals(conditionValue.getValue(), caseValue.getValue())) {
                        matched = true;
                        for (Ast.Statement statement : caseStmt.getStatements()) {
                            visit(statement);
                        }
                    }
                } else {
                    // This is a default case. Execute it only if no other case matched.
                    if (!matched) {
                        for (Ast.Statement statement : caseStmt.getStatements()) {
                            visit(statement);
                        }
                        matched = true; // Ensure the default case is executed only once
                    }
                }
            }

        }
        finally {
            scope = scope.getParent();  // Update Scope before Exiting
        }
        return Environment.NIL;
    }




    // yeah
    public Environment.PlcObject visit(Ast.Statement.Case ast) {
        for (Ast.Statement statement : ast.getStatements()) {
            visit(statement);
        }
        return Environment.NIL;
    }


    // Ensure the condition evaluates to a Boolean [requireType]
    // otherwise... FAIL
    // if condition is TRUE, evaluate statements and repeat
    // ^^don't forget to re-evaluate the condition itself each iteration
    // Returns NIL
    @Override
    public Environment.PlcObject visit(Ast.Statement.While ast) {
        while(requireType(Boolean.class,visit(ast.getCondition()))) {
            try {
                scope = new Scope(scope);
                ast.getStatements().forEach(this::visit);
            } finally {
                scope = scope.getParent();

            }
        }
       return Environment.NIL;
    }

    // Evaluates the value and throws it inside a Return exception
    // The implementation of Ast.Function will catch any Return exceptions and complete the behavior
    // RETURN 1; => throws Return exception with value = 1
    // ^^Return exception class is private!!! (change for testing)
    @Override
    public Environment.PlcObject visit(Ast.Statement.Return ast) {
        throw new Return(visit(ast.getValue()));   // Throws Return Exception with passed value
    }

    // Returns the literal value as a PlcObject
    // *Hint* use Environment.create as needed
    // NIL => NIL, 1 => 1
    @Override
    public Environment.PlcObject visit(Ast.Expression.Literal ast) {
        if(ast.getLiteral() == null) return Environment.NIL;    // Special Case for null conversion to NIL
        return Environment.create(ast.getLiteral());    // Convert Literal into PlcObject
    }

    // Evaluates the contained expression, returning its value
    // (1) => 1, (1 + 10) => 11
    @Override
    public Environment.PlcObject visit(Ast.Expression.Group ast) {
        // Simply evaluate the expression contained within the group and return its result.
        return visit(ast.getExpression());
    }


    // Check assignment doc (the big one)
    // Evaluates arguments based on the specific binary operator, returning the appropriate result for the operation
    // *Hint* use requireType and Environment.create
    // Whenever something is observed but not permitted, FAIL
    // Follow short circuiting rules!
    // ENSURE PROPER OBJECT TYPES [BINARY, BIGDECIMAL, ETC.]
    // Need to use Comparable, equals, RoundingMode.HALF_EVEN
    // For power operations, result may be outside of integer range, calculate yourself :(
    @Override
    public Environment.PlcObject visit(Ast.Expression.Binary ast) {
        // This whole thing is kind of a mess.
        String operator = ast.getOperator();
        Environment.PlcObject lhs = visit(ast.getLeft());
        Environment.PlcObject rhs;
        Object Ret;
        switch(operator) {  // will fail on certain exceptions!
            case "&&":
                requireType(Boolean.class, lhs);
                if(lhs.getValue().equals(Boolean.FALSE)) return Environment.create(Boolean.FALSE);    // F && T
                rhs = visit(ast.getRight());    // Short Circuiting
                requireType(Boolean.class, rhs);
                if(rhs.getValue().equals(Boolean.FALSE)) return Environment.create(Boolean.FALSE);    // T && F
                return Environment.create(Boolean.TRUE);    // T && T
            case "||":
                requireType(Boolean.class, lhs);
                if(lhs.getValue().equals(Boolean.TRUE)) return Environment.create(Boolean.TRUE);    // T || F
                rhs = visit(ast.getRight());    // Short Circuiting
                requireType(Boolean.class, rhs);
                if(rhs.getValue().equals(Boolean.TRUE)) return Environment.create(Boolean.TRUE);    // F || T
                return Environment.create(Boolean.FALSE);   // F && F
            case "<":
                rhs = visit(ast.getRight());
                if (!lhs.getValue().getClass().equals(rhs.getValue().getClass()) ||
                        !(lhs.getValue() instanceof Comparable) ||
                        !(rhs.getValue() instanceof Comparable)) {
                    throw new RuntimeException("Operands for '" + operator + "' operator must be of the same comparable type.");
                }
                int comparisonResultless = ((Comparable) lhs.getValue()).compareTo(rhs.getValue());
                return Environment.create(comparisonResultless< 0);
            case ">":
                // Check if both lhs and rhs are instances of the same class and are Comparable
                rhs = visit(ast.getRight());
                if (!lhs.getValue().getClass().equals(rhs.getValue().getClass()) ||
                        !(lhs.getValue() instanceof Comparable) ||
                        !(rhs.getValue() instanceof Comparable)) {
                    throw new RuntimeException("Operands for '" + operator + "' operator must be of the same comparable type.");
                }
                int comparisonResultmore = ((Comparable) lhs.getValue()).compareTo(rhs.getValue());
                return Environment.create(comparisonResultmore > 0);
            case "==":
                rhs = visit(ast.getRight());
                if(lhs.equals(rhs)) return Environment.create(Boolean.TRUE);    // 5 == 5
                return Environment.create(Boolean.FALSE);   // 5 == 3
            case "!=":
                rhs = visit(ast.getRight());
                if(lhs.equals(rhs)) return Environment.create(Boolean.FALSE);   // 5 != 5
                return Environment.create(Boolean.TRUE);    // 5 != 3
            case "+":
                rhs = visit(ast.getRight());
                if(lhs.getValue() instanceof String || rhs.getValue() instanceof String) {  // String Concatenation
                    return Environment.create(lhs.getValue().toString() + rhs.getValue().toString());
                }
                requireType(lhs.getValue().getClass(), rhs);
                if(lhs.getValue() instanceof BigInteger) Ret = (BigInteger) ((BigInteger) lhs.getValue()).add((BigInteger) rhs.getValue());
                else if(lhs.getValue() instanceof BigDecimal) Ret = (BigDecimal) ((BigDecimal) lhs.getValue()).add((BigDecimal) rhs.getValue());
                else throw new RuntimeException("Invalid Data Type.");
                return Environment.create(Ret);
            case "-":
                rhs = visit(ast.getRight());
                requireType(lhs.getValue().getClass(), rhs);    // Don't like this formatting, very confusing and messy
                if(lhs.getValue() instanceof BigInteger) Ret = (BigInteger) ((BigInteger) lhs.getValue()).subtract((BigInteger) rhs.getValue());
                else if(lhs.getValue() instanceof BigDecimal) Ret = (BigDecimal) ((BigDecimal) lhs.getValue()).subtract((BigDecimal) rhs.getValue());
                else throw new RuntimeException("Invalid Data Type.");
                return Environment.create(Ret);
            case "*":
                rhs = visit(ast.getRight());
                requireType(lhs.getValue().getClass(), rhs);
                if(lhs.getValue() instanceof BigInteger) Ret = (BigInteger) ((BigInteger) lhs.getValue()).multiply((BigInteger) rhs.getValue());
                else if(lhs.getValue() instanceof BigDecimal) Ret = (BigDecimal) ((BigDecimal) lhs.getValue()).multiply((BigDecimal) rhs.getValue());
                else throw new RuntimeException("Invalid Data Type.");
                return Environment.create(Ret);
            case "/":
                rhs = visit(ast.getRight());
                requireType(lhs.getValue().getClass(), rhs);
                if(rhs.getValue().equals(BigInteger.ZERO) || rhs.getValue().equals(BigDecimal.ZERO)) throw new RuntimeException("Divide by Zero Error.");
                if(lhs.getValue() instanceof BigInteger) Ret = (BigInteger) ((BigInteger) lhs.getValue()).divide((BigInteger) rhs.getValue());
                else if(lhs.getValue() instanceof BigDecimal) Ret = (BigDecimal) ((BigDecimal) lhs.getValue()).divide((BigDecimal) rhs.getValue(), RoundingMode.HALF_EVEN);
                else throw new RuntimeException("Invalid Data Type.");
                return Environment.create(Ret);
            case "^":
                rhs = visit(ast.getRight());
                // Ensure LHS is of type BigInteger for the operation
                BigInteger lhsValue = requireType(BigInteger.class, lhs);
                // Convert RHS to an integer value for the exponent. This step was causing the exception.
                int exponent;
                try {
                    exponent = requireType(BigInteger.class, rhs).intValueExact();
                } catch (ArithmeticException e) {
                    throw new RuntimeException("Exponent is too large for an integer", e);
                }
                return Environment.create(lhsValue.pow(exponent));
            default:
                throw new RuntimeException("Invalid Binary Operator.");
        }
        //return Environment.NIL;
    }

    // Return the value of the appropriate variable in the current scope
    // For lists, evaluate the offset (index)
    // ^^Any access outside of length-1 will FAIL
    @Override
    public Environment.PlcObject visit(Ast.Expression.Access ast) {
        Environment.Variable variable = scope.lookupVariable(ast.getName());

        // If there's no offset, simply return the variable's value
        if (!ast.getOffset().isPresent()) {
            return variable.getValue();
        } else {
            // If there's an offset, it's a list access. First, ensure the variable's value is a List
            List<Environment.PlcObject> list = requireType(List.class, variable.getValue());

            // Evaluate the offset expression to get the index
            int index = requireType(BigInteger.class, visit(ast.getOffset().get())).intValueExact();

            // Check for index out of bounds
            if (index < 0 || index >= list.size()) {
                throw new RuntimeException("List index out of bounds: " + index);
            }

            // Return the value at the calculated index
            return Environment.create(list.get(index));
        }
    }


    // Return the value of invoking the appropriate function in the current scope with the evaluated arguments
    // idk about this one
    @Override
    public Environment.PlcObject visit(Ast.Expression.Function ast) {
        Environment.Function function = scope.lookupFunction(ast.getName(), ast.getArguments().size());
        List<Environment.PlcObject> arguments = new ArrayList<>();
        for(Ast.Expression e : ast.getArguments()) {
            arguments.add(visit(e));   // Converting each expression to a PlcObject to pass to function
        }
        return function.invoke(arguments);
    }





    // Returns the list as a PlcObject
    // [1,5,10], scope={} => [1,5,10]
    @Override
    public Environment.PlcObject visit(Ast.Expression.PlcList ast) {
        List<Object> values = new ArrayList<>();
        for (Ast.Expression expression : ast.getValues()) {
            Environment.PlcObject plcObject = visit(expression);
            values.add(plcObject.getValue()); // Store the raw value, not the PlcObject
        }
        return Environment.create(values); // Adjust Environment.create if necessary to handle lists of raw values
    }




    /**
     * Helper function to ensure an object is of the appropriate type.
     */
    private static <T> T requireType(Class<T> type, Environment.PlcObject object) {
        if (type.isInstance(object.getValue())) {
            return type.cast(object.getValue());
        } else {
            throw new RuntimeException("Expected type " + type.getName() + ", received " + object.getValue().getClass().getName() + ".");
        }
    }

    /**
     * Exception class for returning values.
     */
    private static class Return extends RuntimeException {

        public final Environment.PlcObject value;

        public Return(Environment.PlcObject value) {
            this.value = value;
        }

    }

}