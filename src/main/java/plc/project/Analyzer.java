package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
        throw new UnsupportedOperationException();  // TODO
    }

    //only visit return after visiting function; set function

    @Override
    public Void visit(Ast.Global ast) {
        throw new UnsupportedOperationException();  // TODO
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

        //where should this be positioned?
        function = ast;

        //what do we put for jvm name?
        try {
            scope = new Scope(scope);
            for (int i = 0; i < ast.getParameters().size(); i++) {
                String param = ast.getParameters().get(i);
                scope.defineVariable(param, param, parameterTypes.get(i), true, Environment.NIL);
            }

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
    public Void visit(Ast.Statement.Expression ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Statement.Switch ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Statement.Case ast) {
        throw new UnsupportedOperationException();  // TODO
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
