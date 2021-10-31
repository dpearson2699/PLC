package plc.project;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

/**
 * These tests should be passed to avoid double jeopardy during grading as other
 * test cases may rely on this functionality.
 */
public final class InterpreterBaselineTests {

    @Test
    void testBooleanLiteral() {
        test(new Ast.Expression.Literal(true), true, new Scope(null));
    }

    @Test
    void testIntegerLiteral() {
        test(new Ast.Expression.Literal(BigInteger.ONE), BigInteger.ONE, new Scope(null));
    }

    @Test
    void testStringLiteral() {
        test(new Ast.Expression.Literal("string"), "string", new Scope(null));
    }

    @Test
    void testBinaryAddition() {
        test(new Ast.Expression.Binary("+",
                new Ast.Expression.Literal(BigInteger.ONE),
                new Ast.Expression.Literal(BigInteger.TEN)
        ), BigInteger.valueOf(11), new Scope(null));
    }

    @Test
    void testVariableAccess() {
        Scope scope = new Scope(null);
        scope.defineVariable("num", true, Environment.create(BigInteger.ONE));
        test(new Ast.Expression.Access(Optional.empty(), "num"), BigInteger.ONE, scope);
    }

    /**
     * Tests that visiting a function expression properly calls the function and
     * returns the result.
     *
     * When the {@code log(obj)} function is called, {@code obj} is appended to
     * the {@link StringBuilder} and then returned by the function. The last
     * assertion checks that the writer contains the correct value.
     */
    @Test
    void testFunctionCall() {
        Scope scope = new Scope(null);
        StringBuilder builder = new StringBuilder();
        scope.defineFunction("log", 1, args -> {
            builder.append(args.get(0).getValue());
            return args.get(0);
        });
        test(new Ast.Expression.Function("log", Arrays.asList(
                new Ast.Expression.Literal(BigInteger.ONE)
        )), BigInteger.ONE, scope);
        Assertions.assertEquals("1", builder.toString());
    }

    /**
     * Tests that visiting an expression statement evaluates the expression and
     * returns {@code NIL}. This tests relies on function calls.
     *
     * See {@link #testFunctionCall()} for an explanation of {@code log(obj)}.
     */
    @Test
    void testExpressionStatement() {
        Scope scope = new Scope(null);
        StringBuilder builder = new StringBuilder();
        scope.defineFunction("log", 1, args -> {
            builder.append(args.get(0).getValue());
            return args.get(0);
        });
        test(new Ast.Statement.Expression(new Ast.Expression.Function("log", Arrays.asList(
                new Ast.Expression.Literal(BigInteger.ONE)
        ))), Environment.NIL.getValue(), scope);
        Assertions.assertEquals("1", builder.toString());
    }

    @Test
    void testLogarithmExpressionStatement() {
        Scope scope = new Scope(null);
        scope.defineFunction("logarithm", 1, args -> {
            if (!(args.get(0).getValue() instanceof BigDecimal)) {
                throw new RuntimeException("Expected type BigDecimal, recieved" + args.get(0).getValue().getClass().getName() + ".");
            }

            BigDecimal bd1 = (BigDecimal) args.get(0).getValue();
            BigDecimal bd2 = requireType(BigDecimal.class, Environment.create(args.get(0).getValue()));
            BigDecimal result = BigDecimal.valueOf(Math.log(bd2.doubleValue()));

            return Environment.create(result);
        });

        test(new Ast.Expression.Function(
                "logarithm",
                Arrays.asList(new Ast.Expression.Literal(BigDecimal.valueOf(Math.E)))
            ),
            BigDecimal.valueOf(1.0),
            scope
        );
    }

    @Test
    void testConverterExpressionStatement() {
        Scope scope = new Scope(null);
        scope.defineFunction("converter", 2, args -> {
            BigInteger decimal = requireType(BigInteger.class, Environment.create(args.get(0).getValue()));
            BigInteger base = requireType(BigInteger.class, Environment.create(args.get(1).getValue()));

            String number = new String();
            int i, n = 0;

            ArrayList<BigInteger> quotients = new ArrayList<>();
            ArrayList<BigInteger> remainders = new ArrayList<>();

            quotients.add(decimal);

            do {
                quotients.add(quotients.get(n).divide(base));
                remainders.add(
                        quotients.get(n).subtract(quotients.get(n+1).multiply(base))
                );
                n++;
            } while (quotients.get(n).compareTo(BigInteger.ZERO) > 0);

            for (i = 0; i < remainders.size(); i++) {
                number = remainders.get(i).toString() + number;
            }

            return Environment.create(number);
        });

        test(new Ast.Expression.Function(
                "converter",
                Arrays.asList(new Ast.Expression.Literal(BigInteger.valueOf(11)),
                        new Ast.Expression.Literal(BigInteger.valueOf(2)))
            ),
            "1011",
            scope
        );
    }

    /**
     * Tests that visiting the source rule invokes the main/0 function and
     * returns the result.
     */
    @Test
    void testSourceInvokeMain() {
        Scope scope = new Scope(null);
        scope.defineFunction("main", 0, args -> Environment.create(BigInteger.ZERO));
        test(new Ast.Source(Arrays.asList(), Arrays.asList()), BigInteger.ZERO, scope);
    }

    private static void test(Ast ast, Object expected, Scope scope) {
        Interpreter interpreter = new Interpreter(scope);
        if (expected != null) {
            Assertions.assertEquals(expected, interpreter.visit(ast).getValue());
        } else {
            Assertions.assertThrows(RuntimeException.class, () -> interpreter.visit(ast));
        }
    }

    private static <T> T requireType(Class<T> type, Environment.PlcObject object) {
        if (type.isInstance(object.getValue())) {
            return type.cast(object.getValue());
        } else {
            throw new RuntimeException("Expected type " + type.getName() + ", received " + object.getValue().getClass().getName() + ".");
        }
    }
}
