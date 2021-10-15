package plc.project;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope = new Scope(null);

    public Interpreter(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", 1, args -> {
            System.out.println(args.get(0).getValue());
            return Environment.NIL;
        });
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Environment.PlcObject visit(Ast.Source ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Environment.PlcObject visit(Ast.Global ast) {
        /**
         list ::= 'LIST' identifier '=' '[' expression (',' expression)* ']'
         mutable ::= 'VAR' identifier ('=' expression)?
         immutable ::= 'VAL' identifier '=' expression
         */
        
        //How do you determine if it is a VAR or a LIST
        if(ast.getMutable()){
            //This would be for VAL, don't know what to do for LIST
            if(ast.getValue().isPresent()){
                scope.defineVariable(ast.getName(), true, visit(ast.getValue().get()));
            }
            else{
                scope.defineVariable(ast.getName(), true, Environment.NIL);
            }
        }
        else{
            scope.defineVariable(ast.getName(), false, visit(ast.getValue().get()));
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Function ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Expression ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Declaration ast) {
        if(ast.getValue().isPresent()){
            scope.defineVariable(ast.getName(), true, visit(ast.getValue().get()));
        }
        else{
            scope.defineVariable(ast.getName(), true, Environment.NIL);
        }
        
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Assignment ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.If ast) {
        //if true
        if(requireType(Boolean.class, visit(ast.getCondition()))){
            try{
                scope = new Scope(scope);
                ast.getThenStatements().forEach(this::visit);
            } finally {
                scope = scope.getParent();
            }
        }
        //if false
        else{
            try{
                scope = new Scope(scope);
                ast.getElseStatements().forEach(this::visit);
            } finally {
                scope = scope.getParent();
            }
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Switch ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Case ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.While ast) {
        while(requireType(Boolean.class, visit(ast.getCondition()))){
            try{
                scope = new Scope(scope);
                /**
                for (Ast.Statement stmt : ast.getStatements()){
                    visit(stmt);
                }
                or you can do this down below
                 */
                ast.getStatements().forEach(this::visit);

            } finally {
                scope = scope.getParent();
            }
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Return ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Literal ast) {

        //??? should null literal return Environment.NIL
        return Environment.create(ast.getLiteral());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Group ast) {
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Binary ast) {
        String operator = ast.getOperator();

        if (operator.equals("&&") || operator.equals("||")) {

            boolean lhs, rhs, result;
            lhs = requireType(Boolean.class, visit(ast.getLeft()));
            rhs = requireType(Boolean.class, visit(ast.getRight()));
            result = operator.equals("&&") ? lhs && rhs : lhs || rhs;

            return Environment.create(result);
        }
        else if (operator.equals("<") || operator.equals(">")) {

            Environment.PlcObject lhs = visit(ast.getLeft());
            Environment.PlcObject rhs = visit(ast.getRight());

            if (!(lhs.getValue().getClass().equals(rhs.getValue().getClass()))) {
                throw new RuntimeException("Expected type " + Comparable.class.getName() + ", received " + lhs.getValue().getClass().getName() + ".");
            }

            if (!(lhs.getValue() instanceof Comparable)) {

            }

            return lhs.getValue().getClass().compare

            return lhs.getValue().compareTo(rhs.getValue());


            /*
            Environment.PlcObject lhs = visit(ast.getLeft());
            if (!(lhs.getValue() instanceof Comparable)) {
                throw new RuntimeException("Expected type " + Comparable.class.getName() + ", received " + lhs.getValue().getClass().getName() + ".");
            }
            lhs.getValue().getClass() rhs;
             */

        }
        else if () {

        }
        else if () {

        }
        else {

        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Access ast) {

        if (ast.getOffset().isPresent()) { //if there is an offset, accessing a list

            //list stored as a Variable in scope, with value being a PlcObject, whose value is the list of elements
            List<Object> list = (List<Object>)scope.lookupVariable(ast.getName()).getValue().getValue();

            Environment.PlcObject offset = visit(ast.getOffset().get());
            int index = requireType(BigInteger.class, offset).intValue();

            return Environment.create(list.get(index));
        }
        else { //no offset, accessing a variable

            //variable already exists in scope; get Variable instance, retrieve PlcObject value
            return scope.lookupVariable(ast.getName()).getValue();
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Function ast) {

        //invoke requires a list of PlcObject arguments while Ast.Expression.Function only contains a list of Ast.Expression arguments
        //need to convert Ast.Expression -> PlcObject by visiting
        int arity = ast.getArguments().size();
        List<Ast.Expression> exprArguments = ast.getArguments();
        ArrayList<Environment.PlcObject> plcArguments = new ArrayList<Environment.PlcObject>();
        for (int i = 0; i < arity; i++) {
            plcArguments.add(visit(ast.getArguments().get(i)));
        }

        //retrieve the respective function from scope, then invoke it with the visited arguments
        return scope.lookupFunction(ast.getName(), ast.getArguments().size()).invoke(plcArguments);
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.PlcList ast) {
        throw new UnsupportedOperationException(); //TODO
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

        private final Environment.PlcObject value;

        private Return(Environment.PlcObject value) {
            this.value = value;
        }

    }

}
