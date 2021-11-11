package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * See the specification for information about what the different visit
 * methods should do.
 */
public final class Analyzer implements Ast.Visitor<Void> {

    public Scope scope;
    private Ast.Function function;

    public Analyzer(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Void visit(Ast.Source ast) {
        //Check if main/0 function (name = main, arity = 0) does not exist
        if(scope.lookupFunction("main", 0) == null){
            throw new RuntimeException();
        }

        //Check if main/0 function does not have an Integer return type
        requireAssignable(Environment.Type.INTEGER, scope.lookupFunction("main", 0).getReturnType());

        //visit global first
        for(Ast.Global global : ast.getGlobals()){
            visit(global);
        }

        //visit function second
        for(Ast.Function fun : ast.getFunctions()){
            visit(fun);
        }

        return null;
    }

    //only visit return after visiting function; set function

    @Override
    public Void visit(Ast.Global ast) {
        //The variable's name and jvmName are both the name of the global. DONE
        //The variable's type is the type registered in the Environment with the same name as the one in the AST. DONE
        //The variable's value is Environment.NIL (since it is not used by the analyzer) DONE
        //Additionally, throws a RuntimeException if the value, if present, is not assignable to the global. For a value to be assignable, it's type must be a subtype of the global's type as defined above. DONE
        //The value of the global, if present, must be visited before the variable is defined (otherwise, the global would be used before it was initialized). DONE
        Environment.Type type = Environment.getType(ast.getTypeName());

        if(ast.getValue().isPresent()){
            visit(ast.getValue().get());
            Ast.Expression value = ast.getValue().get();
            requireAssignable(type, value.getType());
        }

        ast.setVariable(scope.defineVariable(ast.getName(), ast.getName(), type, ast.getMutable(), Environment.NIL));

        return null;
    }

    @Override
    public Void visit(Ast.Function ast) {

        List<String> parameterStrings = ast.getParameterTypeNames();
        ArrayList<Environment.Type> parameterTypes = new ArrayList<Environment.Type>();
        Environment.Type returnType = Environment.Type.NIL;

        for (String paramType : parameterStrings) {
            parameterTypes.add(Environment.getType(paramType));
        }
        if (ast.getReturnTypeName().isPresent()) {
            returnType = Environment.getType(ast.getReturnTypeName().get());
        }
        Environment.Function func = scope.defineFunction(ast.getName(), ast.getName(), parameterTypes, returnType, args -> Environment.NIL);
        ast.setFunction(func);

        //what do we put for jvm name?
        try {
            scope = new Scope(scope);
            for (int i = 0; i < ast.getParameters().size(); i++) {
                String param = ast.getParameters().get(i);
                scope.defineVariable(param, param, parameterTypes.get(i), true, Environment.NIL);
            }

            function = ast;
            for (Ast.Statement stmt : ast.getStatements()) {
                visit(stmt);
            }
        }
        finally {
            scope = scope.getParent();
        }

        return null;
    }

    //why return an errors?

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        if (!(ast.getExpression() instanceof Ast.Expression.Function)) {
            throw new RuntimeException("Expression not a function expression");
        }
        visit(ast.getExpression());

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {

        Optional<String> optTypeName = ast.getTypeName();
        Optional<Ast.Expression> optValue = ast.getValue();

        if (!optTypeName.isPresent() && !optValue.isPresent()) {
            throw new RuntimeException("Declaration must have type or value to infer type.");
        }
        Environment.Type type = null;

        if (optTypeName.isPresent()) {
            type = Environment.getType(optTypeName.get());
        }

        if (ast.getValue().isPresent()) {
            visit(ast.getValue().get());

            if (type == null) {
                type = ast.getValue().get().getType();
            }
            requireAssignable(type, ast.getValue().get().getType());
        }

        ast.setVariable(scope.defineVariable(ast.getName(), ast.getName(), type, true, Environment.NIL));

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        //Throws a RuntimeException if the receiver is not an access expression (since any other type is not assignable).
        if (!(ast.getReceiver() instanceof Ast.Expression.Access)) {
            throw new RuntimeException("Only access expressions are assignable.");
        }

        //Throws a RuntimeException if the value is not assignable to the receiver.
        visit(ast.getReceiver());
        visit(ast.getValue());
        requireAssignable(ast.getReceiver().getType(), ast.getValue().getType());

        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        visit(ast.getCondition());
        //make sure the then statement isn't empty
        if(ast.getThenStatements().isEmpty()){
            throw new RuntimeException("The then statement is empty");
        }

        //make sure the condition is a boolean after checking if the then statemtent isn't empty
        requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());

        //visit the then statement
        try {
            scope = new Scope(scope);
            for (Ast.Statement thenStmt : ast.getThenStatements()){
                visit(thenStmt);
            }
        } finally {
            scope = scope.getParent();
        }

        //visit the else statement
        try {
            scope = new Scope(scope);
            for (Ast.Statement elseStmt : ast.getElseStatements()){
                visit(elseStmt);
            }
        } finally {
            scope = scope.getParent();
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Switch ast) {
        visit(ast.getCondition());

        //Throws a RuntimeException if the last case contains a value.  Recall, the final case statement is the default, therefore the value must be empty.
        int size = ast.getCases().size();
        if (ast.getCases().get(size-1).getValue().isPresent()) {
            throw new RuntimeException("Final case statement must be default and cannot have a value.");
        }


        //Throws a RuntimeException if the condition defines the value type for each case. If the type of the condition does not match any of the case value types.
        for (Ast.Statement.Case i : ast.getCases()) {

            //make sure case that is being checked is not the default value
            if (i.getValue().isPresent()) {
                visit(i.getValue().get());
                requireAssignable(ast.getCondition().getType(), i.getValue().get().getType());
            }

            visit(i);
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Case ast) {

        try {
            scope = new Scope(scope);
            ast.getStatements().forEach(this::visit);
        }
        finally {
            scope = scope.getParent();
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast) {

        visit(ast.getCondition());
        requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());
        try {
            scope = new Scope(scope);
            for (Ast.Statement stmt : ast.getStatements()) {
                visit(stmt);
            }
        }
        finally {
            scope = scope.getParent();
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        visit(ast.getValue());
        requireAssignable(function.getFunction().getReturnType(), ast.getValue().getType());

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {

        if (ast.getLiteral() instanceof  Boolean) {
            ast.setType(Environment.Type.BOOLEAN);
        }
        else if (ast.getLiteral() instanceof Character) {
            ast.setType(Environment.Type.CHARACTER);
        }
        else if (ast.getLiteral() instanceof String) {
            ast.setType(Environment.Type.STRING);
        }
        else if (ast.getLiteral() instanceof BigInteger) {  //should I be checking BigInteger or Integer???

            //intValueExact throws an ArithmeticException if BigInt value out of range
            try {
                ((BigInteger) ast.getLiteral()).intValueExact();
            }
            catch(ArithmeticException err) {
                throw new RuntimeException("Integer out of range");
            }

            ast.setType(Environment.Type.INTEGER);
        }
        else if (ast.getLiteral() instanceof BigDecimal) {

            //doubleValue returns NEGATIVE_INFINITY or POSITIVE_INFINITY if BigDecimal value out of range
            Double doub = ((BigDecimal) ast.getLiteral()).doubleValue();
            if (doub.equals(Double.NEGATIVE_INFINITY) || doub.equals(Double.POSITIVE_INFINITY)) {   //need equals()???
                throw new RuntimeException("Decimal out of range");
            }

            ast.setType(Environment.Type.DECIMAL);
        }
        else {  //only other possible Literal is null
            ast.setType(Environment.Type.NIL);
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {

        if (!(ast.getExpression() instanceof  Ast.Expression.Binary)) {
            throw new RuntimeException("Only binary expressions can be grouped");
        }

        visit(ast.getExpression());
        ast.setType(ast.getExpression().getType());

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        String operator = ast.getOperator();
        visit(ast.getLeft());
        visit(ast.getRight());
        Environment.Type lhs = ast.getLeft().getType();
        Environment.Type rhs = ast.getRight().getType();

        //should I be using requireAssignable here?
        //how to check if lhs is a specific Environment.TYPE

        if (operator.equals("&&") || operator.equals("||")) {
            requireAssignable(Environment.Type.BOOLEAN, lhs);
            requireAssignable(lhs, rhs);
            ast.setType(Environment.Type.BOOLEAN);
        }
        else if (operator.equals("<") || operator.equals(">") || operator.equals("==") || operator.equals("!=")) {
            requireAssignable(Environment.Type.COMPARABLE, lhs);
            requireAssignable(lhs, rhs);
            ast.setType(Environment.Type.BOOLEAN);
        }
        else if (operator.equals("+")) {

            if (lhs.equals(Environment.Type.STRING) || rhs.equals(Environment.Type.STRING)) {
                //rhs can be anything
                ast.setType(Environment.Type.STRING);
            }
            else if (lhs.equals(Environment.Type.INTEGER)) {
                requireAssignable(Environment.Type.INTEGER, rhs);
                ast.setType(Environment.Type.INTEGER);
            }
            else if (lhs.equals(Environment.Type.DECIMAL)) {
                requireAssignable(Environment.Type.DECIMAL, rhs);
                ast.setType(Environment.Type.DECIMAL);
            }
            else {
                throw new RuntimeException("Expected String/Integer/Decimal, received " + lhs.getName());
            }
        }
        else if (operator.equals("-") || operator.equals("*") || operator.equals("/")) {

            if (lhs.equals(Environment.Type.INTEGER)) {
                requireAssignable(Environment.Type.INTEGER, rhs);
                ast.setType(Environment.Type.INTEGER);
            }
            else if (lhs.equals(Environment.Type.DECIMAL)) {
                requireAssignable(Environment.Type.DECIMAL, rhs);
                ast.setType(Environment.Type.DECIMAL);
            }
            else {
                throw new RuntimeException("Expected Integer/Decimal, received " + lhs.getName());
            }
        }
        else {  //only other possible operator is '^'

            requireAssignable(Environment.Type.INTEGER, rhs);

            if (lhs.equals(Environment.Type.INTEGER)) {
                ast.setType(Environment.Type.INTEGER);
            }
            else {
                ast.setType(Environment.Type.DECIMAL);
            }
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {

        if (ast.getOffset().isPresent()) {  //if there is an offset, accessing a list

            //verify the offset is an Integer
            //need to visit offset (it's an Expression) to analyze its type before checking
            visit(ast.getOffset().get());
            requireAssignable(Environment.Type.INTEGER, ast.getOffset().get().getType());
        }

        //should we be placing entire list as variable???

        //place variable into tree; annotating AST, so it can help in process of compilation
        ast.setVariable(scope.lookupVariable(ast.getName()));

        return null;
    }

    //setting variable in tree to show which variable is active at this node
    //will still need to lookup in scope to get value
    //AST has variable name/type which is static; but value is dynamic, so that variable's state in scope changes

    @Override
    public Void visit(Ast.Expression.Function ast) {

        List<Ast.Expression> arguments = ast.getArguments();
        Environment.Function func = scope.lookupFunction(ast.getName(), arguments.size());

        //if there are any arguments, make sure the type matches with the type of the corresponding parameter
        if (arguments.size() > 0) {
            for (int i = 0; i < arguments.size(); i++) {
                visit(arguments.get(i));
                requireAssignable(func.getParameterTypes().get(i), arguments.get(i).getType());
            }
        }

        ast.setFunction(scope.lookupFunction(ast.getName(), ast.getArguments().size()));

        return null;
    }

    @Override
    public Void visit(Ast.Expression.PlcList ast) {

        //are we just checking if all elements of the list are the same type?

        //visit the first element in the list to get the type
        List<Ast.Expression> elements = ast.getValues();
        visit(elements.get(0));
        Environment.Type type = elements.get(0).getType();

        //check if all other elements have the same type
        for (int i = 1; i < elements.size(); i++) {
            visit(elements.get(i));
            requireAssignable(type, elements.get(i).getType());
        }
        ast.setType(type);

        return null;
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {

        if (target.equals(Environment.Type.ANY)) {
            return;
        }

        if (target.equals(Environment.Type.COMPARABLE)) {
            if (type.equals(Environment.Type.INTEGER)
                    || type.equals(Environment.Type.DECIMAL)
                    || type.equals(Environment.Type.CHARACTER)
                    || type.equals(Environment.Type.STRING)
            ) {
                return;
            }
        }

        if (!(type.equals(target))) {
            throw new RuntimeException("Expected " + target.getName() + ", received " + type.getName() + ".");
        }
    }

}
