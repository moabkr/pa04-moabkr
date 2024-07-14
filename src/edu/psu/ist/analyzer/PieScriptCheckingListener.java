package edu.psu.ist.analyzer;

import antlr4.edu.psu.ist.parser.PiethonBaseListener;
import antlr4.edu.psu.ist.parser.PiethonParser;
import edu.psu.ist.analyzer.entry.SymbolTableEntry;
import edu.psu.ist.analyzer.errors.SemanticError;
import edu.psu.ist.analyzer.utils.Result;
import edu.psu.ist.analyzer.utils.SourceLocation;
import edu.psu.ist.analyzer.utils.TextInput;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PieScriptCheckingListener extends PiethonBaseListener {
    private final Map<String, SymbolTableEntry.ProcDefEntry> procedures = new HashMap<>();
    private Map<String, SymbolTableEntry> currLocalScope;
    private final List<PieErrorMessage> errors = new ArrayList<>();
    private final TextInput source;
    private final PiethonParser.ScriptContext hostContext;

    private final Map<PiethonParser.ExpContext, PieType> expressionTypes = new HashMap<>();

    public PieScriptCheckingListener(TextInput source, PiethonParser.ScriptContext hostContext) {
        this.source = source;
        this.hostContext = hostContext;
        this.currLocalScope = new HashMap<>();
    }

    @Override
    public void enterDef(PiethonParser.DefContext ctx) {
        currLocalScope = new HashMap<>();
    }

    /**
     * Handles the exit from a procedure definition in the Piethon language.
     * This method is responsible for checking if the procedure name already exists in the procedures map.
     * If it does, a semantic error for duplicate symbol is added to the errors list.
     * If not, the procedure is added to the procedures map with its formal parameters, return type, and source location.
     * Additionally, this method checks for return statements and unreachable code within the procedure definition.
     *
     * @param ctx The context of the procedure definition from the parsed Piethon code.
     */
    @Override
    public void exitDef(PiethonParser.DefContext ctx) {
        String procName = ctx.ID().getText();
        if (procedures.containsKey(procName)) {
            errors.add(new SemanticError.DupSymbol(procName, SymbolKind.Procedure, mkSl(ctx)));
        } else {
            PieType returnType = resolvePieType(ctx.ty().getText());
            List<SymbolTableEntry.ParamDefEntry> formalParams = getFormalParams(ctx.paramList());
            procedures.put(procName, new SymbolTableEntry.ProcDefEntry(procName, formalParams, returnType, mkSl(ctx)));
        }
        checkReturnStatement(ctx);
        checkUnreachableCode(ctx);
    }

    private void setExpressionType(PiethonParser.ExpContext ctx, PieType type) {
        expressionTypes.put(ctx, type);
    }

    private PieType getExpressionType(PiethonParser.ExpContext ctx) {
        return expressionTypes.getOrDefault(ctx, PieType.Error);
    }

    private List<SymbolTableEntry.ParamDefEntry> getFormalParams(PiethonParser.ParamListContext paramListCtx) {
        List<SymbolTableEntry.ParamDefEntry> params = new ArrayList<>();
        if (paramListCtx != null) {
            for (PiethonParser.ParamDefContext paramCtx : paramListCtx.paramDef()) {
                String paramName = paramCtx.ID().getText();
                PieType paramType = resolvePieType(paramCtx.ty().getText());
                params.add(new SymbolTableEntry.ParamDefEntry(paramName, paramType, mkSl(paramCtx)));
            }
        }
        return params;
    }

    @Override
    public void enterVarDef(PiethonParser.VarDefContext ctx) {
        String varName = ctx.ID().getText();
        if (currLocalScope.containsKey(varName)) {
            errors.add(new SemanticError.DupSymbol(varName, SymbolKind.Variable, mkSl(ctx)));
        } else {
            PieType type = resolvePieType(ctx.ty().getText());
            boolean isInitialized = ctx.exp() != null;
            if (isInitialized) {
                PiethonParser.ExpContext exp = ctx.exp();
                PieType expType = getExpressionType(exp);
                if (type != expType) {
                    errors.add(new SemanticError.TypeMismatch(type, expType, mkSl(exp)));
                } else {
                    setExpressionType(exp, type);
                }
            }
            currLocalScope.put(varName, new SymbolTableEntry.VarDefEntry(varName, type, mkSl(ctx), isInitialized));
        }
    }

    @Override
    public void enterParamDef(PiethonParser.ParamDefContext ctx) {
        String paramName = ctx.ID().getText();
        if (currLocalScope.containsKey(paramName)) {
            errors.add(new SemanticError.DupSymbol(paramName, SymbolKind.Parameter, mkSl(ctx)));
        } else {
            PieType type = resolvePieType(ctx.ty().getText());
            currLocalScope.put(paramName, new SymbolTableEntry.ParamDefEntry(paramName, type, mkSl(ctx)));
        }
    }

    @Override
    public void enterCallStmt(PiethonParser.CallStmtContext ctx) {
        String procName = ctx.ID().getText();
        if (!currLocalScope.containsKey(procName) && !procedures.containsKey(procName)) {
            errors.add(new SemanticError.NoSuchSymbol(procName, mkSl(ctx)));
        } else if (procedures.containsKey(procName)) {
            SymbolTableEntry.ProcDefEntry procEntry = procedures.get(procName);
            List<PiethonParser.ExpContext> providedArgs = ctx.expList() != null ? ctx.expList().exp() : new ArrayList<>();

            int expectedArgsCount = procEntry.getParameters().size();
            int providedArgsCount = providedArgs.size();

            if (expectedArgsCount != providedArgsCount) {
                errors.add(new SemanticError.ArgCountMismatch(expectedArgsCount, providedArgsCount, mkSl(ctx)));
            } else {
                for (int i = 0; i < providedArgs.size(); i++) {
                    PieType expectedType = procEntry.getParameters().get(i).tpe();
                    PieType actualType = getExpressionType(providedArgs.get(i));

                    if (expectedType != actualType) {
                        errors.add(new SemanticError.TypeMismatch(expectedType, actualType, mkSl(ctx)));
                    }
                }
            }
        }
    }

    @Override
    public void enterVarRefExp(PiethonParser.VarRefExpContext ctx) {
        String varName = ctx.name.getText();
        if (currLocalScope.containsKey(varName)) {
            SymbolTableEntry entry = currLocalScope.get(varName);
            if (entry instanceof SymbolTableEntry.VarDefEntry varEntry) {
                setExpressionType(ctx, varEntry.tpe());
            }
        }
    }

    /**
     * Handles the entry into a boolean expression representing the 'true' literal in the Piethon language.
     * This method sets the expression type of the context to PieType.Bool.
     *
     * @param ctx The context of the 'true' expression from the parsed Piethon code.
     */
    @Override
    public void enterTrueExp(PiethonParser.TrueExpContext ctx) {
        setExpressionType(ctx, PieType.Bool);
    }

    /**
     * Handles the entry into a boolean expression representing the 'false' literal in the Piethon language.
     * This method sets the expression type of the context to PieType.Bool.
     *
     * @param ctx The context of the 'false' expression from the parsed Piethon code.
     */
    @Override
    public void enterFalseExp(PiethonParser.FalseExpContext ctx) {
        setExpressionType(ctx, PieType.Bool);
    }

    /**
     * Handles the entry into an integer expression in the Piethon language.
     * This method sets the expression type of the context to PieType.Int32.
     *
     * @param ctx The context of the integer expression from the parsed Piethon code.
     */
    @Override
    public void enterIntExp(PiethonParser.IntExpContext ctx) {
        setExpressionType(ctx, PieType.Int32);
    }
    
    /**
     * Handles the entry into a string expression in the Piethon language.
     * This method sets the expression type of the context to PieType.String.
     *
     * @param ctx The context of the string expression from the parsed Piethon code.
     */
    private PieType resolvePieType(String typeStr) {
        return switch (typeStr) {
            case "Int32" -> PieType.Int32;
            case "Bool" -> PieType.Bool;
            case "Void" -> PieType.Void;
            case "Error" -> PieType.Error;
            default -> throw new IllegalArgumentException("Unknown type: " + typeStr);
        };
    }

    /**
     * This method resolves a string representation of a type to its corresponding {@link PieType} enum.
     * It supports basic types such as Int32, Bool, Void, and Error. If an unknown type string is provided,
     * it throws an IllegalArgumentException.
     *
     * @param typeStr the string representation of the type.
     * @return the corresponding {@link PieType} enum.
     * @throws IllegalArgumentException if the type string is unknown.
     */

    private void checkReturnStatement(PiethonParser.DefContext ctx) {
        PieType expectedReturnType = resolvePieType(ctx.ty().getText());
        int expectedReturnCount = expectedReturnType == PieType.Void ? 0 : 1;
        int actualReturnCount = 0;

        for (int i = 0; i < ctx.getChildCount(); i++) {
            if (ctx.getChild(i) instanceof PiethonParser.ReturnStmtContext returnStmt) {
                actualReturnCount++;
                PiethonParser.ExpContext returnExp = returnStmt.exp();
                if (returnExp != null) {
                    PieType actualReturnType = getExpressionType(returnExp);
                    if (expectedReturnType != actualReturnType) {
                        errors.add(new SemanticError.TypeMismatch(expectedReturnType, actualReturnType, mkSl(returnStmt)));
                    }
                } else if (expectedReturnType != PieType.Void) {
                    errors.add(new SemanticError.TypeMismatch(expectedReturnType, PieType.Void, mkSl(returnStmt)));
                }
            }
        }

        if (expectedReturnCount != actualReturnCount) {
            errors.add(new SemanticError.TooFewOrTooManyReturns(expectedReturnCount, mkSl(ctx)));
        }
    }

    /**
     * This method checks for unreachable code after a return statement within a function definition.
     * It iterates through all children of the function definition context. If a return statement is found,
     * any subsequent statements are flagged as unreachable. This helps in identifying logical errors in the code
     * where statements that never get executed are present.
     *
     * @param ctx The function definition context to check for unreachable code.
     */
    private void checkUnreachableCode(PiethonParser.DefContext ctx) {
        boolean hasReturnStmt = false;
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            if (child instanceof PiethonParser.ReturnStmtContext) {
                if (hasReturnStmt) {
                    errors.add(new SemanticError.UnreachableCode(mkSl((ParserRuleContext) child), "Additional return statement or code after return."));
                }
                hasReturnStmt = true;
            } else if (hasReturnStmt && child instanceof ParserRuleContext) {
                errors.add(new SemanticError.UnreachableCode(mkSl((ParserRuleContext) child), "Code after return statement."));
            }
        }
    }



    public SourceLocation mkSl(ParserRuleContext ctx) {
        var start = ctx.start;
        var stop = ctx.start;
        return new SourceLocation(source, start.getLine(), start.getCharPositionInLine(), stop.getLine(), stop.getCharPositionInLine());
    }

    public Result<PiethonParser.ScriptContext, List<PieErrorMessage>> getCheckedScript() {
        if (errors.isEmpty()) {
            return Result.ok(hostContext);
        } else {
            return Result.err(errors);
        }
    }
}
