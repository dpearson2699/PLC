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

    @Override
    public Void visit(Ast.Global ast) {
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Function ast) {
        throw new UnsupportedOperationException();  // TODO
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
        throw new UnsupportedOperationException();  // TODO
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        throw new UnsupportedOperationException();  // TODO
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
            requireAssignable(lhs, rhs);
            ast.setType(Environment.Type.BOOLEAN);
        }
        else if (operator.equals("<") || operator.equals(">") || operator.equals("==") || operator.equals("!=")) {
            requireAssignable(Environment.Type.COMPARABLE, lhs);
            requireAssignable(lhs, rhs);
            ast.setType(Environment.Type.BOOLEAN);
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

        /*
        //
        if (ast.getArguments().size() > 0) {

        }

        ast.setFunction();
         */

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

        //do we check for valid type names in here? where do we make Environment.Type
        //better way to do this comparison than by string values?

        //can I use == since Environment.Type are all static objects?

        if (!(type.getName().equals(target.getName()))) {
            throw new RuntimeException("Expected " + target.getName() + ", received " + type.getName() + ".");
        }
    }

}
