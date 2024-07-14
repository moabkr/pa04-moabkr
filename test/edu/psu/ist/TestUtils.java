package edu.psu.ist;

import antlr4.edu.psu.ist.parser.PiethonParser;
import edu.psu.ist.analyzer.PieAnalyzer;
import edu.psu.ist.analyzer.PieErrorMessage;
import edu.psu.ist.analyzer.utils.Options;
import edu.psu.ist.analyzer.utils.Result;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.stream.Collectors;

public abstract class TestUtils {

    public final Result<PiethonParser.ScriptContext, List<PieErrorMessage>> check(String s) {
        return new PieAnalyzer().setOptions(Options.TestOpts).setScriptCode(
                "<test>", s).check();
    }

    public <T extends PieErrorMessage> void expectError(
            Class<T> expectedErrorClass,
            Result<PiethonParser.ScriptContext, List<PieErrorMessage>> result,
            int howManyExpected) {
        if (result.isOk()) {
            Assertions.fail("Expected failure, but the test passed.");
        } else {
            List<PieErrorMessage> errors = result.getError();
            long count = errors.stream()
                    .filter(error -> expectedErrorClass.isInstance(error))
                    .count();

            if (count != howManyExpected) {
                String errorDetails = errors.stream()
                        .map(PieErrorMessage::message)
                        .collect(Collectors.joining(", "));
                Assertions.fail(String.format("Expected %d errors of type %s, but found %d. Errors: %s",
                        howManyExpected, expectedErrorClass.getSimpleName(), count, errorDetails));
            }
        }
    }

    public void expectErrorMessage(String expectedMessage, Result<PiethonParser.ScriptContext, List<PieErrorMessage>> result) {
        if (result.isOk()) {
            Assertions.fail("Expected failure, but the test passed.");
        } else {
            List<PieErrorMessage> errors = result.getError();
            boolean messageFound = errors.stream()
                    .anyMatch(error -> error.message().contains(expectedMessage));
            Assertions.assertTrue(messageFound, "Expected error message not found: " + expectedMessage);
        }
    }




}
