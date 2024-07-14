# Piethon Static Typechecker and Control Flow Visualizer

This project implements a static typechecker and control flow visualization tool for Piethon, a statically typed programming language inspired by Python. The main components include:

1. **PieScriptCheckingListener**: Performs semantic analysis on Piethon scripts, including:
   - Building symbol tables for procedures
   - Typechecking expressions
   - Detecting duplicate variable/parameter definitions
   - Verifying correct procedure calls and return statements

2. **Unit Tests**: A suite of tests to verify the analyzer's functionality, including negative tests for invalid Piethon programs.

3. **Call Graph Generator**: Creates and visualizes a call graph for Piethon scripts using the PieGraphBuildingListener class.

This project utilizes the ANTLR4 parser generator to parse Piethon syntax and traverses the resulting parse trees to perform analysis and visualization tasks.

Key features:
- Static type checking
- Symbol table management
- Call graph generation and visualization
- Comprehensive error detection for invalid Piethon programs

The project provides hands-on experience with parsing, semantic analysis, and working with domain-specific languages (DSLs).