package edu.psu.ist.analyzer;

import antlr4.edu.psu.ist.parser.PiethonParser;
import edu.psu.ist.TestUtils;
import edu.psu.ist.analyzer.errors.ParseError;
import edu.psu.ist.analyzer.errors.SemanticError;
import edu.psu.ist.analyzer.utils.Result;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

// NOTE: these will fail until you implement the logic in PieScriptCheckingListener;
// this file is by no means complete -- just an example of how you can unit test this
// analyzer.
public class PieAnalyzerTests extends TestUtils {

    // testing return stmts:

    @Test public void testBadReturn01() {
        var input = """
                def moo() : Void is
                    return 1; // type mismatch Int32 != Void
                end
                """;

        var result = check(input);
        expectError(SemanticError.TypeMismatch.class, result,
                1);
    }

    @Test public void testBadReturn02() {
        var input = """
                def moo(x : Bool) : Int32 is
                    return x; // <- x has wrong type...
                end
                """;

        var result = check(input);
        expectError(SemanticError.TypeMismatch.class, result,
                1);
    }

    @Test public void testBadReturn03() {
        var input = """
                def moo() : Int32 is
                    return 0;
                    return 0;
                end
                """;

        var result = check(input);
        expectError(SemanticError.TooFewOrTooManyReturns.class, result,
                1);
    }

    @Test public void testDupVars01() {
        var input = """
                def bar() : Void is
                    var x : Int32 := 0;
                    var x : Bool  := true; // name x is already taken
                    var y : Bool := false;
                    var z : Int32 := 0;
                    var y : Bool := true; // another: y is already taken
                end
                """;

        var result = check(input);
        expectError(SemanticError.DupSymbol.class, result,
                2);
    }



    // todo: MORE

    // vars

    @Test
    public void testDupVars02() {
        var input = """
        def foo() : Int32 is
            return 1;
        end

        def foo() : Int32 is // Duplicate function definition
            return 2;
        end
        """;
        var result = check(input);
        expectError(SemanticError.DupSymbol.class, result, 1);
    }

    @Test
    public void testParseError() {
        String input = """
        def invalid(x : Int32 is // Missing closing parenthesis
            return x;
        end
        """;

        Result<PiethonParser.ScriptContext, List<PieErrorMessage>> result = check(input);
        Assertions.assertTrue(result.isError(), "Expected parse error");
        expectError(ParseError.class, result, 1);
    }


    @Test
    public void testTypeErrorInExpression() {
        String input = """
        def add(a : Int32, b : Bool) : Int32 is
            return a + b; // Type mismatch: Int32 and Bool
        end
        """;

        Result<PiethonParser.ScriptContext, List<PieErrorMessage>> result = check(input);
        Assertions.assertTrue(result.isError(), "Expected semantic error");
        expectError(SemanticError.TypeMismatch.class, result, 1);
    }



    @Test
    public void testVariableTypeMismatch() {
        String input = """
        def process() : Void is
            var flag : Bool := true;
            flag := 10; // Type mismatch, expected Bool got Int32
        end
        """;

        var result = check(input);
        expectError(SemanticError.TypeMismatch.class, result, 1);
    }

    @Test
    public void testProcedureCallWithWrongArgumentCount() {
        String input = """
        def add(a : Int32, b : Int32) : Int32 is
            return a + b;
        end

        def main() : Void is
            add(1); // Missing second argument
        end
        """;

        var result = check(input);
        expectError(SemanticError.ArgCountMismatch.class, result, 1);
    }


    @Test
    public void testNoSuchProcedure() {
        String input = """
        def main() : Void is
            compute(); // No such procedure 'compute'
        end
        """;

        var result = check(input);
        expectError(SemanticError.NoSuchSymbol.class, result, 1);
    }





}
