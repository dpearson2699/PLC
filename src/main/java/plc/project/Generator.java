package plc.project;

import java.io.PrintWriter;
import java.util.List;

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
        print("public class Main {");
        newline(0);
        indent += 1;
        newline(indent);

        for(Ast.Global global : ast.getGlobals()){
            print(global);
            newline(indent);
        }

        print("public static void main(String[] args) {");
        indent += 1;
        newline(indent);
        print("System.exit(new Main().main());");
        indent -= 1;
        newline(indent);
        print("}");

        newline(0);
        for(Ast.Function function : ast.getFunctions()){
            newline(indent);
            print(function);
        }
        newline(0);

        indent -= 1;
        newline(indent);
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Global ast) {
        String typeName = ast.getVariable().getType().getJvmName();
        String varName = ast.getVariable().getJvmName();

        //don't need to throw any errors; already handled by analyzer
        if (ast.getValue().isPresent() && ast.getValue().get() instanceof Ast.Expression.PlcList) { //list variables
            print(typeName, "[] ", varName, " = ", ast.getValue().get());
        }
        else {  //mutable and immutable variables

            if (!ast.getMutable()) {
                print("final ");
            }

            print(typeName, " ", varName);

            //immutable will always be initialized, but need check for mutable
            if (ast.getValue().isPresent()) {
                print(" = ", ast.getValue().get());
            }
        }

        print (";");
        return null;
    }

    @Override
    public Void visit(Ast.Function ast) {
        print(ast.getFunction().getReturnType().getJvmName(), " ", ast.getFunction().getJvmName());

        //method parameters
        print("(");
        List<String> arguments = ast.getParameters();
        int arity = arguments.size();
        if (arity > 0) {
            for (int i = 0; i < arity; i++) {
                print(ast.getFunction().getParameterTypes().get(i).getJvmName(), " ", arguments.get(i));
                if (i < arity - 1) {
                    print(", ");
                }
            }
        }
        print(")");

        //statement block
        print(" {");
        if(!ast.getStatements().isEmpty()){
            newline(++indent);
            for(int i = 0; i < ast.getStatements().size(); i++){
                if(i != 0){
                    newline(indent);
                }
                print(ast.getStatements().get(i));
            }
            newline(--indent);
        }
        print("}");

        return null;
    }

    //possible helper functions for refactoring:
    //comma-separated list
    //printing statement block

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        print(ast.getExpression(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        print(ast.getVariable().getType().getJvmName(), " ", ast.getVariable().getJvmName());

        if(ast.getValue().isPresent()){
            print(" = ", ast.getValue().get());
        }
        print(";");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        print(ast.getReceiver(), " = ", ast.getValue(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        print("if (", ast.getCondition(), ") {");
        indent += 1;

        //generation of then statements (analyzer does not allow for empty then statement)
        for(Ast.Statement stmt : ast.getThenStatements()){
            newline(indent);
            print(stmt);
        }
        indent -= 1;
        newline(indent);
        print("}");

        //check if there are else statements
        if(!ast.getElseStatements().isEmpty()){
            print(" else {");
            indent += 1;

            for(Ast.Statement stmt : ast.getElseStatements()){
                newline(indent);
                print(stmt);
            }

            indent -= 1;
            newline(indent);
            print("}");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Switch ast) {
        print("switch (", ast.getCondition(), ") {");
        newline(++indent);

        for(int i = 0; i < ast.getCases().size(); ++i) {
            print(ast.getCases().get(i));
            if(i < ast.getCases().size() - 1) {
                newline(--indent);
            }
        }

        indent -= 2;
        newline(indent);
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Case ast) {
        if(ast.getValue().isPresent()){
            print("case ", ast.getValue().get(), ":");
        }
        else{
            print("default:");
        }

        newline(++indent);

        for(int i = 0; i < ast.getStatements().size(); i++){
            print(ast.getStatements().get(i));
            if(i < ast.getStatements().size() - 1){
                newline(indent);
            }
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        print("while (", ast.getCondition(), ") {");

        if(!ast.getStatements().isEmpty()){
            newline(++indent);
            for(int i = 0; i < ast.getStatements().size(); i++){
                if(i != 0){
                    newline(indent);
                }
                print(ast.getStatements().get(i));
            }
            newline(--indent);
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

        if (ast.getLiteral() instanceof String) {
            print("\"", ast.getLiteral(), "\"");
        }
        else if (ast.getLiteral() instanceof Character) {
            print("\'", ast.getLiteral(), "\'");
        }
        else if (ast.getLiteral() == null ) {   //calling toString on null object throws a null pointer exception
            print("null");
        }
        else {  //toString on BigDecimal and BigInteger gives accurate value
            print(ast.getLiteral());
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

        if (ast.getOperator().equals("^")) {
            print("Math.pow(", ast.getLeft(), ", ", ast.getRight(), ")");
        }
        else {
            print(ast.getLeft(), " ", ast.getOperator(), " ", ast.getRight());
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {

        print(ast.getVariable().getJvmName());

        if (ast.getOffset().isPresent()) {  //accessing a list
            print("[", ast.getOffset().get(), "]");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {

        print(ast.getFunction().getJvmName(), "(");

        List<Ast.Expression> arguments = ast.getArguments();
        int arity = arguments.size();
        if (arity > 0) {
            for (int i = 0; i < arity - 1; i++) {
                print(arguments.get(i), ", ");
            }
            print(arguments.get(arity - 1));
        }

        print(")");

        return null;
    }

    @Override
    public Void visit(Ast.Expression.PlcList ast) {
        print("{");

        //can't use for each loop bc need to know when at last value
        for(int i = 0; i < ast.getValues().size(); i++){
            print(ast.getValues().get(i));
            //make sure value is not the last so to print a comma in the list
            if(i < ast.getValues().size() - 1){
                print(", ");
            }
        }

        print("}");

        return null;
    }

}