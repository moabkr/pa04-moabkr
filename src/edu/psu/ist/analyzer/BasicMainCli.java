package edu.psu.ist.analyzer;

import edu.psu.ist.analyzer.utils.Digraph;
import edu.psu.ist.analyzer.utils.Options;

public class BasicMainCli {

    public static void main(String[] args) {
        // just a place to generate a sample graph (most of the time
        // when you interact with PieAnalyzer it will be through the tests --
        // see examples in the /test/analyzer directory)
        System.out.println("PIETHON analyzer:");

        // this triple-quoted string is called a "Text block", a newer multiline
        // form of string added to java (they are used in the jUnit tests as well)
        String sampleScript = """
        def g() : Void is
        end
        
        def f() : Void is
            g();
        end
        
        def m() : Int32 is
            f();
            g();
            return 0;
        end
        """;

        var analyzer = new PieAnalyzer()
                .setOptions(new Options(false))
                .setScriptCode("exampleScript", sampleScript);

        var checkResult = analyzer.check();
        if (checkResult.isOk()) {
            Digraph<ProcNode> g = analyzer.buildGraph();

            analyzer.exportGraph(g, "test-graph2.png", "example script graph");
        } else {
            System.err.println("Script contains errors:");
            for (PieErrorMessage error : checkResult.getError()) {
                System.err.println(error);
            }
        }}
}
