package edu.psu.ist.analyzer;

import antlr4.AntlrErrorReportingListener;
import antlr4.edu.psu.ist.parser.PiethonLexer;
import antlr4.edu.psu.ist.parser.PiethonParser;
import edu.psu.ist.analyzer.errors.ParseError;
import edu.psu.ist.analyzer.utils.Digraph;
import edu.psu.ist.analyzer.utils.Options;
import edu.psu.ist.analyzer.utils.Result;
import edu.psu.ist.analyzer.utils.TextInput;
import guru.nidi.graphviz.attribute.*;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.model.MutableNode;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static guru.nidi.graphviz.model.Factory.mutGraph;
import static guru.nidi.graphviz.model.Factory.mutNode;
import static guru.nidi.graphviz.model.Link.to;

public final class PieAnalyzer {

    /** The current {@code .pie} source to be parsed into a CST. */
    private TextInput currentSource;

    /** Stores current settings/options for the checker (minimal currently). */
    private Options options = Options.TestOpts;

    /** Adds the source code with the given {@code name} and {@code text}. */
    public PieAnalyzer setScriptCode(String name, String text) {
        if (name == null || text == null) {
            throw new IllegalArgumentException("name, text should not be null");
        }
        this.currentSource = new TextInput(name, text);

        return this;
    }

    public PieAnalyzer setOptions(Options o) {
        this.options = o;
        return this;
    }

    /** Removes the current {@code .pie} script with the given {@code name}. */
    public PieAnalyzer removeSourceCode() {
        this.currentSource = null;
        return this;
    }

    /**
     * Returns a {@link Result} instance that is either a {@link Result.Ok}
     * holding the successully parsed syntax tree for the current piethon
     * program or an {@link Result.Err} that encapsulates a list of error
     * messages.
     */
    public Result<PiethonParser.ScriptContext, List<PieErrorMessage>> check() {

        if (currentSource == null) {
            throw new IllegalStateException("Cannot call check until a " +
                    "script is set (call setScriptCode(..))");
        }
        var parseResult = parseRoot(currentSource);
        if (parseResult.isError()) {
            if (!options.runSilent()) {
                reportErrors(parseResult.getError());
            }
            return parseResult;
        }

        PiethonParser.ScriptContext scriptRootNode = parseResult.get();
        PieScriptCheckingListener checkingListener =
                new PieScriptCheckingListener(currentSource, scriptRootNode);
        ParseTreeWalker.DEFAULT.walk(checkingListener, scriptRootNode);

        var result = checkingListener.getCheckedScript();
        if (result.isError() && !options.runSilent()) {
            reportErrors(result.getError());
        }
        return result;
    }

    private void reportErrors(List<PieErrorMessage> errors) {
        for (var err : errors) {
            System.err.println(err);
        }
    }

    public Digraph<ProcNode> buildGraph() {

        var checkResult = check();
        if (!checkResult.isOk()) {
            throw new IllegalArgumentException("Script contains errors " +
                    "(call check first to ensure the script is well formed)");
        }
        PieGraphBuildingListener l = new PieGraphBuildingListener();
        // walk the tree & build the graph
        ParseTreeWalker.DEFAULT.walk(l, checkResult.get());
        return l.getGraph();
    }

    /**
     * Given a call graph {@code g}, exports a png visualizing the graph to
     * the project root directory.
     * <p>
     * Implement this method using
     * <a href="https://github.com/nidi3/graphviz-java">this graphviz library</a>
     */
    public void exportGraph(Digraph<ProcNode> g, String outputImageName, String graphTitle) {
        MutableGraph graph = mutGraph(graphTitle).setDirected(true).graphAttrs().add("rankdir", "LR");

        Map<ProcNode, MutableNode> nodeMap = new HashMap<>();
        for (ProcNode node : g.getVertices()) {
            MutableNode graphNode = mutNode(node.toString()).add(Shape.TRIANGLE, Style.FILLED, Color.BLUE);
            nodeMap.put(node, graphNode);
            graph.add(graphNode);
        }

        for (ProcNode node : g.getVertices()) {
            MutableNode sourceNode = nodeMap.get(node);
            for (ProcNode target : g.neighbors(node)) {
                MutableNode targetNode = nodeMap.get(target);
                sourceNode.addLink(to(targetNode).with(Label.of("calls")));
            }
        }

        Path path = Paths.get(outputImageName);
        try {
            Graphviz.fromGraph(graph).width(2340).render(Format.PNG).toFile(new File(path.toString()));
            System.out.println("Graph has been exported to: " + path.toString());
        } catch (Exception e) {
            System.err.println("Error while exporting graph: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Given a piethon {@code source}, returns an {@link Result} instance that
     * holds either the root of a successfully parsed piethon parse tree, or a
     * List of {@link ParseError} messages.
     */
    private Result<PiethonParser.ScriptContext, List<PieErrorMessage>> parseRoot(TextInput source) {
        var errorListener = new AntlrErrorReportingListener(source);
        var lexer = new PiethonLexer(CharStreams.fromString(source.text(),
                source.name()));

        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        var parser = new PiethonParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        PiethonParser.ScriptContext tree = parser.script();
        // NOTE: we don't want our parser to stop cold on the first
        // syntactic error encountered
        if (!errorListener.errors().isEmpty()) {
            // failure (one or more syntactic errors)
            return Result.err(errorListener.errors());
        }
        return Result.ok(tree);
    }
}
