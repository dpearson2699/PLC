package plc.project;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

        //declare/initialize functions and globals
        for(Ast.Global global : ast.getGlobals()){
            visit(global);
        }
        for(Ast.Function function : ast.getFunctions()){
            visit(function);
        }

        //invoke main, if it doesn't exist throw an exception
        return scope.lookupFunction("main", 0).invoke(new ArrayList<Environment.PlcObject>());
    }

    @Override
    public Environment.PlcObject visit(Ast.Global ast) {

        boolean mutable;
        Environment.PlcObject value;

        //VAR and List are mutable
        if(ast.getMutable()){

            mutable = true;

            if(ast.getValue().isPresent()){

                value = visit(ast.getValue().get());
            }
            else{   //Parser already throws an error if List has no value; so this case only applies to uninitialized VAR

                value = Environment.NIL;
            }
        }
        else{

            mutable = false;
            value = visit(ast.getValue().get());
        }

        scope.defineVariable(ast.getName(), mutable, value);

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Function ast) {
        Scope globalScope = scope;

        //dynamically build a Function object using members of Ast.Function (not the same as manually building Function objects to test Ast.Expression.Function)
        scope.defineFunction(ast.getName(), ast.getParameters().size(), arguments -> {
            Scope previous = scope;
            try{
                scope = new Scope(globalScope);

                //arguments
                for(int i = 0; i < arguments.size(); i++){
                    scope.defineVariable(ast.getParameters().get(i), true, arguments.get(i));
                }

                //statements
                ast.getStatements().forEach(this::visit);

            } catch(Return ret){
                return ret.value;

            } finally {
                scope = previous;
            }
            return Environment.NIL; //no return is listed, nothing returned (for void)
        });
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Expression ast) {
        visit(ast.getExpression());
        return Environment.NIL;
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

        //receiver must be an Access to be assignable
        if (!(ast.getReceiver().getClass().equals(Ast.Expression.Access.class))) {
            throw new RuntimeException("Expected type Access, received " + ast.getReceiver().getClass().getName() + ".");
        }

        //can only assign to a mutable variable in scope
        Ast.Expression.Access receiver = (Ast.Expression.Access) ast.getReceiver();
        Environment.Variable receiverVar = scope.lookupVariable(receiver.getName());
        if (!receiverVar.getMutable()) {
            throw new RuntimeException("Expected mutable receiver.");
        }

        if (receiver.getOffset().isPresent()) { //if an offset is present, we are assigning to a list element
            List<Object> list = (List<Object>)receiverVar.getValue().getValue();

            BigInteger offset = requireType(BigInteger.class, visit(receiver.getOffset().get()));

            //Java is pass by value/reference (list isn't a copy, it's the actual list stored in Env.Var receiver)
            list.set(offset.intValue(),visit(ast.getValue()).getValue());

        }
        else { //otherwise we are assigning to a variable

            //list elements are mutable, but list itself can't be reassigned
            if (receiverVar.getValue().getValue() instanceof List) {
                throw new RuntimeException("List cannot be reassigned.");
            }

            receiverVar.setValue(visit(ast.getValue()));
        }

        return Environment.NIL;
    }

    //need try{}finally{} in the case getStatements() contains a Return statement (throws an error that is only caught in visit Ast.Function)
    //want error to bubble up to Ast.Function, but still need to restore scope from If/While/Switch

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

        Environment.PlcObject condition = visit(ast.getCondition());
        for (Ast.Statement.Case i : ast.getCases()) {

            if (visit(i).getValue().equals(condition.getValue()) || visit(i).equals(Environment.NIL)) { //if case matches condition or case is default
                try {
                    scope = new Scope(scope);
                    i.getStatements().forEach(this::visit);
                }
                finally {
                    scope = scope.getParent();
                }

                return Environment.NIL; //exit switch
            }
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Case ast) {
        if (ast.getValue().isPresent()) {
            return visit(ast.getValue().get());
        }
        else {
            return Environment.NIL;
        }
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

        Environment.PlcObject val = visit(ast.getValue());
        throw new Return(val);
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Literal ast) {

        //null needs to be replaced with NIL
        if (ast.getLiteral() == null) {
            return Environment.NIL;
        }

        return Environment.create(ast.getLiteral());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Group ast) {
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Binary ast) {
        String operator = ast.getOperator();
        Environment.PlcObject left = visit(ast.getLeft());
        //Object leftType = left.getValue().getClass();

        if (operator.equals("&&") || operator.equals("||")) {

            Boolean lhs, rhs;
            lhs = requireType(Boolean.class, left);

            if (!lhs && operator.equals("&&")) {    //short-circuiting: FALSE && ... is always FALSE
                return Environment.create(new Boolean(false));
            }

            if (lhs && operator.equals("||")) { //short-circuiting: TRUE || ... is always TRUE
                return Environment.create(new Boolean(true));
            }

            Environment.PlcObject right = visit(ast.getRight());
            rhs = requireType(Boolean.class, right);
            boolean result = operator.equals("&&") ? lhs && rhs : lhs || rhs;

            //wrap in Boolean object; PlcObject's value is of type Object
            //autoboxing: can just pass boolean and it will be boxed automatically
            return Environment.create(new Boolean(result));
        }

        Environment.PlcObject right = visit(ast.getRight());
        //Object rightType = right.getValue().getClass();

        if (operator.equals("<") || operator.equals(">")) {

            //lhs and rhs must be types that are instances of Comparable
            Comparable lhs = requireType(Comparable.class, left);

            //if rhs is the same type as lhs, then it is also an instance of Comparable
            Comparable rhs = (Comparable)requireType(left.getValue().getClass(), right);

            //alternatively, can us '.getValue().getClass().equals(Type.class)'
            //or '.getValue() instanceof Type' to check if types are the same
            //would require manually throwing a RunTimeException

            Boolean result;
            if (operator.equals("<")) {
                result = new Boolean(lhs.compareTo(rhs) < 0 ? true : false);
            }
            else {
                result = new Boolean(lhs.compareTo(rhs) > 0 ? true : false);
            }

            return Environment.create(result);
        }
        else if (operator.equals("==") || operator.equals("!=")) {

            boolean result = left.getValue().equals(right.getValue()) == operator.equals("==");
            return Environment.create(new Boolean(result));
        }
        else if (operator.equals("+")) {

            //https://stackoverflow.com/questions/4344871/how-can-i-know-if-object-is-string-type-object
            //??? should we wrap String in new String (says it's redundant)
            if (left.getValue() instanceof String) {
                return Environment.create((String)left.getValue() + right.getValue());
            }
            else if (right.getValue() instanceof String) {
                return Environment.create(left.getValue() + (String)right.getValue());
            }
            else if (left.getValue() instanceof BigInteger){

                BigInteger lhs = (BigInteger)left.getValue();
                BigInteger rhs = requireType(BigInteger.class, right);

                return Environment.create(lhs.add(rhs));
            }
            else if (left.getValue() instanceof BigDecimal) {

                BigDecimal lhs = (BigDecimal)left.getValue();
                BigDecimal rhs = requireType(BigDecimal.class, right);

                return Environment.create(lhs.add(rhs));
            }
            else {
                throw new RuntimeException("Expected type String/BigInteger/BigDecimal, received " + left.getValue().getClass().getName() + ".");
            }
        }
        else if (operator.equals("-") || operator.equals("*")) {

            //left.getValue() instanceof  BigInteger;
            //BigInteger.class.isInstance(left.getValue());

            if (left.getValue() instanceof BigInteger) {

                BigInteger lhs = (BigInteger)left.getValue();
                BigInteger rhs = requireType(BigInteger.class, right);

                BigInteger result = operator.equals("-") ? lhs.subtract(rhs) : lhs.multiply(rhs);
                return Environment.create(result);
            }
            else if (left.getValue() instanceof BigDecimal) {

                BigDecimal lhs = (BigDecimal)left.getValue();
                BigDecimal rhs = requireType(BigDecimal.class, right);

                BigDecimal result = operator.equals("-") ? lhs.subtract(rhs) : lhs.multiply(rhs);
                return Environment.create(result);
            }
            else {
                throw new RuntimeException("Expected type BigInteger/BigDecimal, received " + left.getValue().getClass().getName() + ".");
            }
        }
        else if (operator.equals("/")) {

            if (left.getValue() instanceof BigInteger) {

                BigInteger lhs = (BigInteger)left.getValue();
                BigInteger rhs = requireType(BigInteger.class, right);

                if (rhs.equals(BigInteger.ZERO)) {
                    throw new RuntimeException("Can't divide by zero.");
                }

                return Environment.create(lhs.divide(rhs));
            }
            else if (left.getValue() instanceof BigDecimal) {

                BigDecimal lhs = (BigDecimal)left.getValue();
                BigDecimal rhs = requireType(BigDecimal.class, right);

                if (rhs.equals(BigDecimal.ZERO)) {
                    throw new RuntimeException("Can't divide by zero.");
                }

                return Environment.create(lhs.divide(rhs, RoundingMode.HALF_EVEN));
            }
            else {
                throw new RuntimeException("Expected type BigInteger/BigDecimal, received " + left.getValue().getClass().getName() + ".");
            }
        }
        else {  //the last possible operator for a valid BinaryExpression node is ^

            if (left.getValue() instanceof BigInteger) {

                BigInteger lhs = (BigInteger)left.getValue();
                BigInteger rhs = requireType(BigInteger.class, right);
                if (rhs.compareTo(BigInteger.ZERO) > 0) {

                    return Environment.create(lhs.pow(rhs.intValue()));
                }
                else {  //BigInteger.pow() cannot handle negative exponents, but BigDecimal can

                    /*
                        BigDecimal _lhs = new BigDecimal(lhs);
                        return Environment.create(_lhs.pow(rhs.intValue()));
                    */
                    //return Environment.create(BigDecimal.ONE.divide(BigDecimal.valueOf(lhs.pow(rhs.intValue() * -1).doubleValue())));

                    BigDecimal _lhs = new BigDecimal(lhs);
                    BigDecimal _rhs = new BigDecimal(rhs);
                    return Environment.create(BigDecimal.ONE.divide(_lhs.pow(_rhs.abs().intValue())));
                }
            }
            else if (left.getValue() instanceof BigDecimal) {

                BigDecimal lhs = (BigDecimal)left.getValue();
                BigInteger rhs = requireType(BigInteger.class, right);

                //return Environment.create(lhs.pow(rhs.intValue()));

                if (rhs.compareTo(BigInteger.ZERO) > 0) {

                    return Environment.create(lhs.pow(rhs.intValue()));
                }
                else {

                    //handle non-terminating decimal???
                    return Environment.create(BigDecimal.ONE.divide(lhs.pow(rhs.abs().intValue())));
                }
            }
            else {
                throw new RuntimeException("Expected type BigInteger/BigDecimal, received " + left.getValue().getClass().getName() + ".");
            }
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Access ast) {

        if (ast.getOffset().isPresent()) { //if there is an offset, accessing a list

            //list stored as a Variable in scope, with value being a PlcObject, whose value is the list of elements
            //value of PlcObejct is an Object (parent to all types)
            //need to cast to List<0bject>, because Environment.create() only takes types that are subclasses of Object
            //list could be of Boolean, BigInteger, BigDecimal values; Object accepts them all since it's their parent
            List<Object> list = (List<Object>)scope.lookupVariable(ast.getName()).getValue().getValue();

            Environment.PlcObject offset = visit(ast.getOffset().get());
            int index = requireType(BigInteger.class, offset).intValue();
            if (index >= list.size() || index < 0) {
                throw new RuntimeException("List index out of bounds.");
            }

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
            plcArguments.add(visit(exprArguments.get(i)));
        }

        //go through all
        /*
        for () {
            //if Ast.Expression.Access
            if () {
                //check global scope for same var
                while()
            }
        }
        */

        //retrieve the respective function from scope, then invoke it with the visited arguments
        return scope.lookupFunction(ast.getName(), exprArguments.size()).invoke(plcArguments);
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.PlcList ast) {

        List<Ast.Expression> elements = ast.getValues();
        ArrayList<Object> visitedElements = new ArrayList<Object>();
        for (Ast.Expression i : elements) {
            visitedElements.add(visit(i).getValue());
        }

        return Environment.create(visitedElements);
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
    public static class Return extends RuntimeException {

        private final Environment.PlcObject value;

        private Return(Environment.PlcObject value) {
            this.value = value;
        }

    }

}
