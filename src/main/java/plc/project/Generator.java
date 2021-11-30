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
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Function ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        print("if (");
        print(ast.getCondition());
        print(") {");
        indent += 1;

        //generation of then statements
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
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Statement.Case ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        print("return ");
        print(ast.getValue());
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        throw new UnsupportedOperationException(); //TODO
    }

    @Override
    public Void visit(Ast.Expression.PlcList ast) {
        throw new UnsupportedOperationException(); //TODO
    }

}
