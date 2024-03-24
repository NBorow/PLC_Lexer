package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
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
        Environment.PlcObject ret = Environment.NIL;    // Default Case
        ast.getGlobals().forEach(this::visit);  // Evaluate Globals
        for(Ast.Function f : ast.getFunctions()) {  // Evaluate Functions
            ret = visit(f); // ISSUE: FUNCTION PASSES NOTHING TO args SO FUNCTION ISN'T DEFINED => NO EVALUATION/RETURN
        }
        scope.lookupFunction("main", 0);    // Fail if no main
        return ret; // Default Case
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
        String name = ast.getName();
        int arity = ast.getParameters().size();
        // testSource and testMain pass the same exact function, but one fails and one doesn't. WTF
        scope.defineFunction(name, arity, args -> { // ERROR WITH NO ARGS PASSED SO LAMBDA ISN'T ENTERED
            try {
                scope = new Scope(scope);   // Defining new scope for function

                List<String> names = ast.getParameters();
                for (int i = 0; i < arity; ++i) {
                    scope.defineVariable(names.get(i), true,
                            Environment.create(args.get(i).getValue()));    // Defining Variables with Parameter Names and Passed Values
                }

                List<Ast.Statement> statements = ast.getStatements();
                Ast.Statement s;
                for (int i = 0; i < statements.size(); ++i) {
                    visit(statements.get(i));   // Evaluating Function Statements
                }
            } catch (Return e) {
                return e.value; // Return Lambda Function
            } finally {
                scope = scope.getParent();  // Update Scope before Exiting
            }
            return Environment.NIL; // shouldn't reach this statement, here for syntax reasons
        });
        return Environment.NIL; // This Function always returns NIL
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
        requireType(Ast.Expression.Access.class, Environment.create(ast.getReceiver()));
        Environment.Variable variable = scope.lookupVariable(visit(ast.getReceiver()).getValue().toString());
        Environment.PlcObject value = visit(ast.getValue());
        if(variable.getMutable()) {
            Ast.Expression.Access access = (Ast.Expression.Access) ast.getReceiver();
            if(access.getOffset().isPresent()) {
                List<Environment.PlcObject> list = requireType(List.class, variable.getValue());
                int index = requireType(BigInteger.class, visit(access.getOffset().get())).intValue();
                if(index < 0 || index >= list.size()) throw new RuntimeException("List index out of bounds: " + index);
                list.set(index, value);
            } else variable.setValue(value);
        }
        else throw new RuntimeException("Immutable Variable");
        return Environment.NIL;
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

        if (conditionValue) {
            ast.getThenStatements().forEach(this::visit);
        } else {
            ast.getElseStatements().forEach(this::visit);
        }

        return Environment.NIL;
    }

    // Inside of a new scope, if the condition is equivalent to a CASE value, evaluate the corresponding statements
    // Otherwise evaluate the statements of the DEFAULT
    // Returns NIL
    @Override
    public Environment.PlcObject visit(Ast.Statement.Switch ast) {
        Environment.PlcObject conditionValue = visit(ast.getCondition());
        boolean matchedCase = false;

        for (Ast.Statement.Case caseStatement : ast.getCases()) {
            if (!matchedCase && caseStatement.getValue().isPresent()) {
                Environment.PlcObject caseValue = visit(caseStatement.getValue().get());
                if (conditionValue.equals(caseValue)) {
                    matchedCase = true;
                    visit(caseStatement); // Use the conceptual visit method for Case
                    break;
                }
            } else if (!matchedCase && !caseStatement.getValue().isPresent()) {
                visit(caseStatement); // Use it for the default case as well
                matchedCase = true;
                break;
            }
        }
        return Environment.NIL;
    }


    // yeah
    @Override
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
        return Environment.create(ast.getExpression()); // TODO
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
                requireType(BigInteger.class, lhs); // Has to be BigInteger
                requireType(lhs.getValue().getClass(), rhs);
                // The value of a BigInteger might be larger than the range of an Integer
                // , requiring you to perform calculations yourself
                Ret = (BigInteger) ((BigInteger)lhs.getValue()).pow((int) rhs.getValue());
                return Environment.create(Ret);
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
        List<Environment.PlcObject> evaluatedValues = new ArrayList<>();
        for (Ast.Expression expression : ast.getValues()) {
            evaluatedValues.add(visit(expression)); // This assumes visit() evaluates the expression and returns a PlcObject
        }
        return Environment.create(evaluatedValues); // Create a PlcObject with the list of evaluated values
        /*
        Object Ret;
       // requireType(Ast.Expression.PlcList.class,ast);
       // List<Ast.Expression>temmp=ast.getValues();
      //  temmp.toString()
        Ast.Expression.PlcList temp;
        for(int i=0;i<temmp.size();i++){
            temp.  temmp[i].getLiteral();
        }
        //Ret=(Ast.Expression.PlcList) ast.getValues();
        return Environment.create(ast.getValues().toString()); // TODO
         */
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