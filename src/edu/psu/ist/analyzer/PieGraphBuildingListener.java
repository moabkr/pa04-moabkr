package edu.psu.ist.analyzer;

import antlr4.edu.psu.ist.parser.PiethonBaseListener;
import antlr4.edu.psu.ist.parser.PiethonParser;
import edu.psu.ist.analyzer.utils.Digraph;

public final class PieGraphBuildingListener extends PiethonBaseListener {
    private final Digraph<ProcNode> graph;
    private ProcNode currentProcedureNode;

    public PieGraphBuildingListener() {
        this.graph = new Digraph<>();
    }
    /**
     * Handles the entry into a procedure definition in the Piethon language.
     * This method is responsible for creating a new procedure node based on the procedure name
     * and adding it to the graph as a vertex.
     *
     * @param ctx The context of the procedure definition from the parsed Piethon code.
     */

    @Override
    public void enterDef(PiethonParser.DefContext ctx) {
        String procedureName = ctx.ID().getText();
        currentProcedureNode = new ProcNode(procedureName);
        graph.add(currentProcedureNode);
    }

    /**
     * Handles the exit from a procedure definition in the Piethon language.
     * This method is responsible for resetting the current procedure node to null
     * indicating that subsequent function calls or operations are outside the scope
     * of the current procedure definition.
     *
     * @param ctx The context of the procedure definition from the parsed Piethon code.
     */

    @Override
    public void exitDef(PiethonParser.DefContext ctx) {
        currentProcedureNode = null;
    }

    /**
     * Handles the entry into a function call statement within the Piethon language.
     * This method is responsible for adding the called procedure as a vertex in the graph
     * if it does not already exist, and then adding an edge from the current procedure
     * to the called procedure to represent the function call dependency.
     *
     * @param ctx The context of the call statement from the parsed Piethon code.
     */
    @Override
    public void enterCallStmt(PiethonParser.CallStmtContext ctx) {
        String calledProcedureName = ctx.ID().getText();
        ProcNode calledProcedureNode = graph.getVertex(calledProcedureName);
        if (calledProcedureNode == null) {
            calledProcedureNode = new ProcNode(calledProcedureName);
            graph.add(calledProcedureNode);
        }
        if (currentProcedureNode != null) {
            graph.add(currentProcedureNode, calledProcedureNode);
        } else {
            System.err.println("Error: Call expression outside of any procedure definition.");
        }
    }

    /**
     * Retrieves the graph for the script built up over the course of the
     * traversal of this script.
     */
    public Digraph<ProcNode> getGraph() {
        return graph;
    }
}
