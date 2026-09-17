package semantic;

import ast.Program;
import ast.Expression;
import ast.Statement;
import ast.expressions.*;
import ast.statements.*;

import lexer.TokenType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SemanticAnalyzer {

    private SymbolTable currentScope;

    // Stores information about all functions
    private final Map<String, FunctionInfo> functions = new HashMap<>();

    // Current function being analyzed
    private FunctionDeclaration currentFunction;

    // Stores whether semantic errors occurred
    private boolean hasErrors;

    public void analyze(Program program) {

        currentScope = new SymbolTable();
        hasErrors = false;

        // First collect all functions
        collectFunctions(program);

        // Then analyze each function
        for (FunctionDeclaration function : program.getFunctions()) {
            analyzeFunction(function);
        }

        if (hasErrors) {
            System.err.println("Semantic analysis failed.");
        }
    }

    /*
     * Collect all function declarations before analyzing them.
     * This allows functions to call functions declared later.
     */
    private void collectFunctions(Program program) {

        for (FunctionDeclaration function : program.getFunctions()) {

            if (functions.containsKey(function.getName())) {

                error(
                        "Function already declared: "
                                + function.getName()
                );

                continue;
            }

            functions.put(
                    function.getName(),
                    new FunctionInfo(
                            function.getName(),
                            function.getReturnType(),
                            function.getParameters()
                    )
            );
        }
    }

    private void analyzeFunction(FunctionDeclaration function) {

        currentFunction = function;

        SymbolTable functionScope =
                new SymbolTable(currentScope);

        SymbolTable previousScope = currentScope;
        currentScope = functionScope;

        // Add parameters to function scope
        for (Parameter parameter : function.getParameters()) {

            if (!currentScope.define(
                    parameter.getName(),
                    parameter.getType()
            )) {

                error(
                        "Duplicate parameter: "
                                + parameter.getName()
                );
            }
        }

        // Analyze function body
        for (Statement statement : function.getBody()) {
            analyzeStatement(statement);
        }

        currentScope = previousScope;
        currentFunction = null;
    }

    private void analyzeStatement(Statement statement) {

        // Variable declaration
        if (statement instanceof VariableDeclaration declaration) {

            String name = declaration.getName();
            String declaredType = declaration.getType();

            if (!currentScope.define(name, declaredType)) {

                error(
                        "Variable already declared: "
                                + name
                );
            }

            if (declaration.getInitializer() != null) {

                String valueType =
                        analyzeExpression(
                                declaration.getInitializer()
                        );

                if (!isTypeCompatible(
                        declaredType,
                        valueType
                )) {

                    error(
                            "Cannot assign "
                                    + valueType
                                    + " to "
                                    + declaredType
                                    + " variable '"
                                    + name
                                    + "'"
                    );
                }
            }

        // Assignment
        } else if (statement instanceof Assignment assignment) {

            Symbol symbol =
                    currentScope.resolve(
                            assignment.getName()
                    );

            String valueType =
                    analyzeExpression(
                            assignment.getValue()
                    );

            if (symbol == null) {

                error(
                        "Undefined variable: "
                                + assignment.getName()
                );

            } else if (!isTypeCompatible(
                    symbol.getType(),
                    valueType
            )) {

                error(
                        "Cannot assign "
                                + valueType
                                + " to "
                                + symbol.getType()
                                + " variable '"
                                + assignment.getName()
                                + "'"
                );
            }

        // Return / send
        } else if (statement instanceof ReturnStatement returnStatement) {

            analyzeReturnStatement(returnStatement);

        // Expression statement
        } else if (statement instanceof ExpressionStatement expressionStatement) {

            analyzeExpression(
                    expressionStatement.getExpression()
            );

        // If
        } else if (statement instanceof IfStatement ifStatement) {

            String conditionType =
                    analyzeExpression(
                            ifStatement.getCondition()
                    );

            if (!conditionType.equals("truth")) {

                error(
                        "If condition must be of type truth"
                );
            }

            analyzeBlock(
                    ifStatement.getThenBranch()
            );

            if (ifStatement.getElseBranch() != null
                    && !ifStatement.getElseBranch().isEmpty()) {

                analyzeBlock(
                        ifStatement.getElseBranch()
                );
            }

        // Loop
        } else if (statement instanceof WhileStatement whileStatement) {

            String conditionType =
                    analyzeExpression(
                            whileStatement.getCondition()
                    );

            if (!conditionType.equals("truth")) {

                error(
                        "Loop condition must be of type truth"
                );
            }

            analyzeBlock(
                    whileStatement.getBody()
            );

        // Each
        } else if (statement instanceof EachStatement eachStatement) {

            analyzeEach(eachStatement);

        // Break
        } else if (statement instanceof BreakStatement) {

            // Loop-context checking will be added later.

        // Skip
        } else if (statement instanceof SkipStatement) {

            // Loop-context checking will be added later.
        }
    }

    /*
     * Check return/send statement against current function's return type.
     */
    private void analyzeReturnStatement(
            ReturnStatement returnStatement
    ) {

        if (currentFunction == null) {
            error("Return statement outside function.");
            return;
        }

        String expectedType =
                currentFunction.getReturnType();

        Expression value =
                returnStatement.getValue();

        // void function
        if (expectedType.equals("void")) {

            if (value != null) {

                error(
                        "Void function cannot return a value"
                );
            }

            return;
        }

        // Non-void function must return a value
        if (value == null) {

            error(
                    "Function '"
                            + currentFunction.getName()
                            + "' must return "
                            + expectedType
            );

            return;
        }

        String actualType =
                analyzeExpression(value);

        if (!isTypeCompatible(
                expectedType,
                actualType
        )) {

            error(
                    "Function '"
                            + currentFunction.getName()
                            + "' must return "
                            + expectedType
                            + " but returned "
                            + actualType
            );
        }
    }

    private void analyzeEach(EachStatement statement) {

        SymbolTable eachScope =
                new SymbolTable(currentScope);

        SymbolTable previousScope = currentScope;
        currentScope = eachScope;

        if (statement.getInitializer() != null) {

            analyzeStatement(
                    statement.getInitializer()
            );
        }

        if (statement.getCondition() != null) {

            String conditionType =
                    analyzeExpression(
                            statement.getCondition()
                    );

            if (!conditionType.equals("truth")) {

                error(
                        "Each condition must be of type truth"
                );
            }
        }

        if (statement.getUpdate() != null) {

            analyzeStatement(
                    statement.getUpdate()
            );
        }

        analyzeBlock(
                statement.getBody()
        );

        currentScope = previousScope;
    }

    private void analyzeBlock(
            List<Statement> statements
    ) {

        SymbolTable blockScope =
                new SymbolTable(currentScope);

        SymbolTable previousScope = currentScope;
        currentScope = blockScope;

        for (Statement statement : statements) {
            analyzeStatement(statement);
        }

        currentScope = previousScope;
    }

    /*
     * Analyze expression and return its type.
     */
    private String analyzeExpression(
            Expression expression
    ) {

        // Literal
        if (expression instanceof LiteralExpression literal) {

            Object value = literal.getValue();

            if (value instanceof Integer) {
                return "int";
            }

            if (value instanceof Double) {
                return "decimal";
            }

            if (value instanceof Boolean) {
                return "truth";
            }

            if (value instanceof Character) {
                return "char";
            }

            if (value instanceof String) {
                return "string";
            }

            return "void";
        }

        // Variable
        if (expression instanceof VariableExpression variable) {

            Symbol symbol =
                    currentScope.resolve(
                            variable.getName()
                    );

            if (symbol == null) {

                error(
                        "Undefined variable: "
                                + variable.getName()
                );

                return "void";
            }

            return symbol.getType();
        }

        // Unary expression
        if (expression instanceof UnaryExpression unary) {

            String operandType =
                    analyzeExpression(
                            unary.getExpression()
                    );

            TokenType operator =
                    unary.getOperator();

            if (operator == TokenType.NOT) {

                if (!operandType.equals("truth")) {

                    error(
                            "Operator ! requires truth operand"
                    );
                }

                return "truth";
            }

            if (operator == TokenType.MINUS) {

                if (!isNumeric(operandType)) {

                    error(
                            "Unary - requires numeric operand"
                    );
                }

                return operandType;
            }

            return operandType;
        }

        // Binary expression
        if (expression instanceof BinaryExpression binary) {

            String leftType =
                    analyzeExpression(
                            binary.getLeft()
                    );

            String rightType =
                    analyzeExpression(
                            binary.getRight()
                    );

            TokenType operator =
                    binary.getOperator();

            // Logical
            if (operator == TokenType.AND_AND
                    || operator == TokenType.OR_OR) {

                if (!leftType.equals("truth")
                        || !rightType.equals("truth")) {

                    error(
                            "Logical operators require truth operands"
                    );
                }

                return "truth";
            }

            // Comparison
            if (operator == TokenType.LESS
                    || operator == TokenType.GREATER
                    || operator == TokenType.LESS_EQUAL
                    || operator == TokenType.GREATER_EQUAL) {

                if (!isNumeric(leftType)
                        || !isNumeric(rightType)) {

                    error(
                            "Comparison operators require numeric operands"
                    );
                }

                return "truth";
            }

            // Equality
            if (operator == TokenType.EQUAL_EQUAL
                    || operator == TokenType.NOT_EQUAL) {

                if (!isTypeCompatible(
                        leftType,
                        rightType
                )) {

                    error(
                            "Equality comparison requires compatible types"
                    );
                }

                return "truth";
            }

            // Arithmetic
            if (operator == TokenType.PLUS
                    || operator == TokenType.MINUS
                    || operator == TokenType.STAR
                    || operator == TokenType.SLASH
                    || operator == TokenType.PERCENT) {

                if (!isNumeric(leftType)
                        || !isNumeric(rightType)) {

                    error(
                            "Arithmetic operators require numeric operands"
                    );

                    return "void";
                }

                if (leftType.equals("decimal")
                        || rightType.equals("decimal")) {

                    return "decimal";
                }

                return "int";
            }

            return "void";
        }

        // Function call
        if (expression instanceof CallExpression call) {

            return analyzeFunctionCall(call);
        }

        return "void";
    }

    /*
     * Check function calls.
     */
    private String analyzeFunctionCall(
            CallExpression call
    ) {

        String functionName = call.getName();

        // Built-in input()
        if (functionName.equals("input")) {

            if (!call.getArguments().isEmpty()) {

                error(
                        "input() does not accept arguments"
                );
            }

            return "int";
        }

        // Built-in output()
        if (functionName.equals("output")) {

            if (call.getArguments().isEmpty()) {

                error(
                        "output() requires at least one argument"
                );

            } else {

                for (Expression argument : call.getArguments()) {
                    analyzeExpression(argument);
                }
            }

            return "void";
        }

        // Find user-defined function
        FunctionInfo function =
                functions.get(functionName);

        if (function == null) {

            error(
                    "Undefined function: "
                            + functionName
            );

            // Still analyze arguments
            for (Expression argument : call.getArguments()) {
                analyzeExpression(argument);
            }

            return "void";
        }

        List<Expression> arguments =
                call.getArguments();

        List<Parameter> parameters =
                function.getParameters();

        // Check number of arguments
        if (arguments.size() != parameters.size()) {

            error(
                    "Function '"
                            + functionName
                            + "' expects "
                            + parameters.size()
                            + " argument(s), but got "
                            + arguments.size()
            );
        }

        // Check argument types
        int count =
                Math.min(
                        arguments.size(),
                        parameters.size()
                );

        for (int i = 0; i < count; i++) {

            String actualType =
                    analyzeExpression(
                            arguments.get(i)
                    );

            String expectedType =
                    parameters.get(i).getType();

            if (!isTypeCompatible(
                    expectedType,
                    actualType
            )) {

                error(
                        "Argument "
                                + (i + 1)
                                + " of function '"
                                + functionName
                                + "' expects "
                                + expectedType
                                + " but got "
                                + actualType
                );
            }
        }

        return function.getReturnType();
    }

    private boolean isNumeric(String type) {

        return type.equals("int")
                || type.equals("decimal");
    }

    private boolean isTypeCompatible(
            String expected,
            String actual
    ) {

        if (expected.equals(actual)) {
            return true;
        }

        // Allow int -> decimal conversion
        if (expected.equals("decimal")
                && actual.equals("int")) {

            return true;
        }

        return false;
    }

    private void error(String message) {

        hasErrors = true;

        System.err.println(
                "Semantic error: "
                        + message
        );
    }

    /*
     * Stores information about a function.
     */
    private static class FunctionInfo {

        private final String name;
        private final String returnType;
        private final List<Parameter> parameters;

        public FunctionInfo(
                String name,
                String returnType,
                List<Parameter> parameters
        ) {
            this.name = name;
            this.returnType = returnType;
            this.parameters = parameters;
        }

        public String getName() {
            return name;
        }

        public String getReturnType() {
            return returnType;
        }

        public List<Parameter> getParameters() {
            return parameters;
        }
    }
}