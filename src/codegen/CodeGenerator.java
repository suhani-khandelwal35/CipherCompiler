package codegen;

import ast.Program;
import ast.Expression;
import ast.Statement;
import ast.expressions.*;
import ast.statements.*;

import lexer.TokenType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class CodeGenerator {

    private final StringBuilder assembly = new StringBuilder();

    // Stack variables
    private final Map<String, Integer> variables = new HashMap<>();

    // Whether a variable contains a decimal
    private final Map<String, Boolean> decimalVariables = new HashMap<>();

    // Whether a variable contains a string
    private final Map<String, Boolean> stringVariables = new HashMap<>();

    // Function return types
    private final Map<String, String> functionReturnTypes = new HashMap<>();

    // String literal pool
    private final Map<String, String> stringLabels = new HashMap<>();
    private final StringBuilder stringData = new StringBuilder();

    // Loop labels
    private final Stack<String> loopEndLabels = new Stack<>();
    private final Stack<String> loopContinueLabels = new Stack<>();

    private int stackOffset = 0;
    private int labelCounter = 0;
    private int stringLabelCounter = 0;

    private String currentFunctionReturnType = "void";

    // =========================================================
    // GENERATE
    // =========================================================

    public String generate(Program program) {

        assembly.setLength(0);

        variables.clear();
        decimalVariables.clear();
        stringVariables.clear();

        functionReturnTypes.clear();

        stringLabels.clear();
        stringData.setLength(0);

        loopEndLabels.clear();
        loopContinueLabels.clear();

        stackOffset = 0;
        labelCounter = 0;
        stringLabelCounter = 0;

        // Collect function return types
        for (FunctionDeclaration function : program.getFunctions()) {

            functionReturnTypes.put(
                    function.getName(),
                    function.getReturnType()
            );
        }

        generateHeader();

        for (FunctionDeclaration function : program.getFunctions()) {
            generateFunction(function);
        }

        // Add string literals after all functions.
        if (stringData.length() > 0) {

            assembly.append("\n.section .rdata\n");
            assembly.append(stringData);

            assembly.append("\n.text\n");
        }

        return assembly.toString();
    }

    // =========================================================
    // HEADER
    // =========================================================

    private void generateHeader() {

        assembly.append(".text\n");
        assembly.append(".globl main\n");

        assembly.append("\n.section .rdata\n");

        assembly.append("format_int:\n");
        assembly.append("    .asciz \"%lld\\n\"\n");

        assembly.append("format_decimal:\n");
        assembly.append("    .asciz \"%.6f\\n\"\n");

        assembly.append("format_string:\n");
        assembly.append("    .asciz \"%s\\n\"\n");

        assembly.append("input_format:\n");
        assembly.append("    .asciz \"%lld\"\n");

        assembly.append("\n.text\n");
    }

    // =========================================================
    // FUNCTION GENERATION
    // =========================================================

    private void generateFunction(FunctionDeclaration function) {

        variables.clear();
        decimalVariables.clear();
        stringVariables.clear();

        loopEndLabels.clear();
        loopContinueLabels.clear();

        stackOffset = 0;

        currentFunctionReturnType = function.getReturnType();

        String name = function.getName();

        assembly.append("\n.globl " + name + "\n");
        assembly.append(name + ":\n");

        assembly.append("    pushq %rbp\n");
        assembly.append("    movq %rsp, %rbp\n");
        assembly.append("    subq $512, %rsp\n");

        String[] integerParameterRegisters = {
                "%rcx",
                "%rdx",
                "%r8",
                "%r9"
        };

        String[] decimalParameterRegisters = {
                "%xmm0",
                "%xmm1",
                "%xmm2",
                "%xmm3"
        };

        for (int i = 0; i < function.getParameters().size(); i++) {

            Parameter parameter =
                    function.getParameters().get(i);

            stackOffset -= 8;

            variables.put(
                    parameter.getName(),
                    stackOffset
            );

            String type = parameter.getType();

            boolean decimal = type.equals("decimal");
            boolean string = type.equals("string");

            decimalVariables.put(
                    parameter.getName(),
                    decimal
            );

            stringVariables.put(
                    parameter.getName(),
                    string
            );

            if (i < 4) {

                if (decimal) {

                    assembly.append(
                            "    movsd "
                                    + decimalParameterRegisters[i]
                                    + ", "
                                    + memoryLocation(stackOffset)
                                    + "\n"
                    );

                } else {

                    // int, truth, char and string
                    // are all passed through integer registers.

                    assembly.append(
                            "    movq "
                                    + integerParameterRegisters[i]
                                    + ", "
                                    + memoryLocation(stackOffset)
                                    + "\n"
                    );
                }
            }
        }

        for (Statement statement : function.getBody()) {
            generateStatement(statement);
        }

        // Default return value
        if (function.getReturnType().equals("decimal")) {

            assembly.append(
                    "    pxor %xmm0, %xmm0\n"
            );

        } else {

            assembly.append(
                    "    movq $0, %rax\n"
            );
        }

        generateFunctionEpilogue();
    }

    private void generateFunctionEpilogue() {

        assembly.append(
                "    movq %rbp, %rsp\n"
        );

        assembly.append(
                "    popq %rbp\n"
        );

        assembly.append(
                "    ret\n"
        );
    }

    // =========================================================
    // STATEMENTS
    // =========================================================

    private void generateStatement(Statement statement) {

        // -----------------------------------------------------
        // VARIABLE DECLARATION
        // -----------------------------------------------------

        if (statement instanceof VariableDeclaration declaration) {

            stackOffset -= 8;

            variables.put(
                    declaration.getName(),
                    stackOffset
            );

            String type = declaration.getType();

            decimalVariables.put(
                    declaration.getName(),
                    type.equals("decimal")
            );

            stringVariables.put(
                    declaration.getName(),
                    type.equals("string")
            );

            if (declaration.getInitializer() != null) {

                generateExpression(
                        declaration.getInitializer()
                );

                // decimal
                if (type.equals("decimal")) {

                    ensureDecimalResult(
                            declaration.getInitializer()
                    );

                    assembly.append(
                            "    movsd %xmm0, "
                                    + memoryLocation(stackOffset)
                                    + "\n"
                    );

                }

                // string
                else if (type.equals("string")) {

                    assembly.append(
                            "    movq %rax, "
                                    + memoryLocation(stackOffset)
                                    + "\n"
                    );

                }

                // int / truth / char
                else {

                    assembly.append(
                            "    movq %rax, "
                                    + memoryLocation(stackOffset)
                                    + "\n"
                    );
                }
            }

            return;
        }

        // -----------------------------------------------------
        // ASSIGNMENT
        // -----------------------------------------------------

        if (statement instanceof Assignment assignment) {

            generateExpression(
                    assignment.getValue()
            );

            Integer offset =
                    variables.get(assignment.getName());

            if (offset != null) {

                // decimal
                if (decimalVariables.getOrDefault(
                        assignment.getName(),
                        false
                )) {

                    ensureDecimalResult(
                            assignment.getValue()
                    );

                    assembly.append(
                            "    movsd %xmm0, "
                                    + memoryLocation(offset)
                                    + "\n"
                    );
                }

                // string
                else if (stringVariables.getOrDefault(
                        assignment.getName(),
                        false
                )) {

                    assembly.append(
                            "    movq %rax, "
                                    + memoryLocation(offset)
                                    + "\n"
                    );
                }

                // int / truth / char
                else {

                    assembly.append(
                            "    movq %rax, "
                                    + memoryLocation(offset)
                                    + "\n"
                    );
                }
            }

            return;
        }

        // -----------------------------------------------------
        // RETURN
        // -----------------------------------------------------

        if (statement instanceof ReturnStatement returnStatement) {

            if (returnStatement.getValue() != null) {

                generateExpression(
                        returnStatement.getValue()
                );

                if (currentFunctionReturnType.equals("decimal")) {

                    ensureDecimalResult(
                            returnStatement.getValue()
                    );
                }

                // string / int / truth / char
                // already return in RAX.

            }

            else if (currentFunctionReturnType.equals("decimal")) {

                assembly.append(
                        "    pxor %xmm0, %xmm0\n"
                );

            }

            else {

                assembly.append(
                        "    movq $0, %rax\n"
                );
            }

            generateFunctionEpilogue();
            return;
        }

        // -----------------------------------------------------
        // EXPRESSION STATEMENT
        // -----------------------------------------------------

        if (statement instanceof ExpressionStatement expressionStatement) {

            generateExpression(
                    expressionStatement.getExpression()
            );

            return;
        }

        // -----------------------------------------------------
        // IF
        // -----------------------------------------------------

        if (statement instanceof IfStatement ifStatement) {

            generateIf(ifStatement);
            return;
        }

        // -----------------------------------------------------
        // WHILE
        // -----------------------------------------------------

        if (statement instanceof WhileStatement whileStatement) {

            generateWhile(whileStatement);
            return;
        }

        // -----------------------------------------------------
        // EACH
        // -----------------------------------------------------

        if (statement instanceof EachStatement eachStatement) {

            generateEach(eachStatement);
            return;
        }

        // -----------------------------------------------------
        // BREAK
        // -----------------------------------------------------

        if (statement instanceof BreakStatement) {

            if (!loopEndLabels.isEmpty()) {

                assembly.append(
                        "    jmp "
                                + loopEndLabels.peek()
                                + "\n"
                );
            }

            return;
        }

        // -----------------------------------------------------
        // SKIP
        // -----------------------------------------------------

        if (statement instanceof SkipStatement) {

            if (!loopContinueLabels.isEmpty()) {

                assembly.append(
                        "    jmp "
                                + loopContinueLabels.peek()
                                + "\n"
                );
            }
        }
    }

    // =========================================================
    // IF
    // =========================================================

    private void generateIf(IfStatement statement) {

        String elseLabel =
                newLabel("else");

        String endLabel =
                newLabel("endif");

        generateExpression(
                statement.getCondition()
        );

        assembly.append(
                "    cmpq $0, %rax\n"
        );

        assembly.append(
                "    je " + elseLabel + "\n"
        );

        for (Statement bodyStatement :
                statement.getThenBranch()) {

            generateStatement(bodyStatement);
        }

        assembly.append(
                "    jmp " + endLabel + "\n"
        );

        assembly.append(
                elseLabel + ":\n"
        );

        if (statement.getElseBranch() != null) {

            for (Statement bodyStatement :
                    statement.getElseBranch()) {

                generateStatement(bodyStatement);
            }
        }

        assembly.append(
                endLabel + ":\n"
        );
    }

    // =========================================================
    // WHILE
    // =========================================================

    private void generateWhile(WhileStatement statement) {

        String startLabel =
                newLabel("loop");

        String endLabel =
                newLabel("endloop");

        loopEndLabels.push(endLabel);
        loopContinueLabels.push(startLabel);

        assembly.append(
                startLabel + ":\n"
        );

        generateExpression(
                statement.getCondition()
        );

        assembly.append(
                "    cmpq $0, %rax\n"
        );

        assembly.append(
                "    je " + endLabel + "\n"
        );

        for (Statement bodyStatement :
                statement.getBody()) {

            generateStatement(bodyStatement);
        }

        assembly.append(
                "    jmp " + startLabel + "\n"
        );

        assembly.append(
                endLabel + ":\n"
        );

        loopContinueLabels.pop();
        loopEndLabels.pop();
    }

    // =========================================================
    // EACH
    // =========================================================

    private void generateEach(EachStatement statement) {

        if (statement.getInitializer() != null) {

            generateStatement(
                    statement.getInitializer()
            );
        }

        String startLabel =
                newLabel("each");

        String endLabel =
                newLabel("endeach");

        loopEndLabels.push(endLabel);
        loopContinueLabels.push(startLabel);

        assembly.append(
                startLabel + ":\n"
        );

        if (statement.getCondition() != null) {

            generateExpression(
                    statement.getCondition()
            );

            assembly.append(
                    "    cmpq $0, %rax\n"
            );

            assembly.append(
                    "    je " + endLabel + "\n"
            );
        }

        for (Statement bodyStatement :
                statement.getBody()) {

            generateStatement(bodyStatement);
        }

        if (statement.getUpdate() != null) {

            generateStatement(
                    statement.getUpdate()
            );
        }

        assembly.append(
                "    jmp " + startLabel + "\n"
        );

        assembly.append(
                endLabel + ":\n"
        );

        loopContinueLabels.pop();
        loopEndLabels.pop();
    }

    // =========================================================
    // EXPRESSIONS
    // =========================================================

    private void generateExpression(Expression expression) {

        // -----------------------------------------------------
        // LITERAL
        // -----------------------------------------------------

        if (expression instanceof LiteralExpression literal) {

            Object value =
                    literal.getValue();

            // integer
            if (value instanceof Integer) {

                assembly.append(
                        "    movq $" + value + ", %rax\n"
                );

                return;
            }

            // decimal
            if (value instanceof Double) {

                long bits =
                        Double.doubleToRawLongBits(
                                (Double) value
                        );

                assembly.append(
                        "    movabsq $"
                                + bits
                                + ", %rax\n"
                );

                assembly.append(
                        "    movq %rax, %xmm0\n"
                );

                return;
            }

            // boolean
            if (value instanceof Boolean) {

                assembly.append(
                        "    movq $"
                                + ((Boolean) value
                                ? "1"
                                : "0")
                                + ", %rax\n"
                );

                return;
            }

            // character
            if (value instanceof Character) {

                assembly.append(
                        "    movq $"
                                + (int) ((Character) value)
                                + ", %rax\n"
                );

                return;
            }

            // string
            if (value instanceof String) {

                String label =
                        getStringLabel((String) value);

                assembly.append(
                        "    leaq "
                                + label
                                + "(%rip), %rax\n"
                );

                return;
            }
        }

        // -----------------------------------------------------
        // VARIABLE
        // -----------------------------------------------------

        if (expression instanceof VariableExpression variable) {

            Integer offset =
                    variables.get(variable.getName());

            if (offset == null) {

                assembly.append(
                        "    movq $0, %rax\n"
                );

                return;
            }

            // decimal
            if (decimalVariables.getOrDefault(
                    variable.getName(),
                    false
            )) {

                assembly.append(
                        "    movsd "
                                + memoryLocation(offset)
                                + ", %xmm0\n"
                );

            }

            // string
            else if (stringVariables.getOrDefault(
                    variable.getName(),
                    false
            )) {

                assembly.append(
                        "    movq "
                                + memoryLocation(offset)
                                + ", %rax\n"
                );
            }

            // int / truth / char
            else {

                assembly.append(
                        "    movq "
                                + memoryLocation(offset)
                                + ", %rax\n"
                );
            }

            return;
        }

        // -----------------------------------------------------
        // BINARY
        // -----------------------------------------------------

        if (expression instanceof BinaryExpression binary) {

            generateBinaryExpression(binary);
            return;
        }

        // -----------------------------------------------------
        // UNARY
        // -----------------------------------------------------

        if (expression instanceof UnaryExpression unary) {

            generateExpression(
                    unary.getExpression()
            );

            if (unary.getOperator() == TokenType.MINUS) {

                if (isDecimalExpression(
                        unary.getExpression()
                )) {

                    assembly.append(
                            "    pxor %xmm1, %xmm1\n"
                    );

                    assembly.append(
                            "    subsd %xmm0, %xmm1\n"
                    );

                    assembly.append(
                            "    movsd %xmm1, %xmm0\n"
                    );

                } else {

                    assembly.append(
                            "    negq %rax\n"
                    );
                }

            }

            else if (unary.getOperator() == TokenType.NOT) {

                assembly.append(
                        "    cmpq $0, %rax\n"
                );

                assembly.append(
                        "    sete %al\n"
                );

                assembly.append(
                        "    movzbq %al, %rax\n"
                );
            }

            return;
        }

        // -----------------------------------------------------
        // CALL
        // -----------------------------------------------------

        if (expression instanceof CallExpression call) {

            generateCall(call);
        }
    }

    // =========================================================
    // BINARY EXPRESSIONS
    // =========================================================

    private void generateBinaryExpression(
            BinaryExpression expression
    ) {

        TokenType operator =
                expression.getOperator();

        // -----------------------------------------------------
        // STRING COMPARISON
        // -----------------------------------------------------

        if ((operator == TokenType.EQUAL_EQUAL
                || operator == TokenType.NOT_EQUAL)
                && (isStringExpression(
                        expression.getLeft()
                )
                || isStringExpression(
                        expression.getRight()
                ))) {

            generateStringComparison(expression);
            return;
        }

        // -----------------------------------------------------
        // DECIMAL
        // -----------------------------------------------------

        boolean numericArithmetic =
                operator == TokenType.PLUS
                        || operator == TokenType.MINUS
                        || operator == TokenType.STAR
                        || operator == TokenType.SLASH
                        || operator == TokenType.PERCENT;

        boolean comparison =
                operator == TokenType.EQUAL_EQUAL
                        || operator == TokenType.NOT_EQUAL
                        || operator == TokenType.LESS
                        || operator == TokenType.LESS_EQUAL
                        || operator == TokenType.GREATER
                        || operator == TokenType.GREATER_EQUAL;

        if ((numericArithmetic || comparison)
                && (isDecimalExpression(
                        expression.getLeft()
                )
                || isDecimalExpression(
                        expression.getRight()
                ))) {

            generateDecimalBinaryExpression(
                    expression
            );

            return;
        }

        // -----------------------------------------------------
        // INTEGER / BOOLEAN
        // -----------------------------------------------------

        generateExpression(
                expression.getLeft()
        );

        assembly.append(
                "    pushq %rax\n"
        );

        generateExpression(
                expression.getRight()
        );

        assembly.append(
                "    movq %rax, %r10\n"
        );

        assembly.append(
                "    popq %rax\n"
        );

        switch (operator) {

            case PLUS:

                assembly.append(
                        "    addq %r10, %rax\n"
                );

                break;

            case MINUS:

                assembly.append(
                        "    subq %r10, %rax\n"
                );

                break;

            case STAR:

                assembly.append(
                        "    imulq %r10, %rax\n"
                );

                break;

            case SLASH:

                assembly.append(
                        "    cqto\n"
                );

                assembly.append(
                        "    idivq %r10\n"
                );

                break;

            case PERCENT:

                assembly.append(
                        "    cqto\n"
                );

                assembly.append(
                        "    idivq %r10\n"
                );

                assembly.append(
                        "    movq %rdx, %rax\n"
                );

                break;

            case EQUAL_EQUAL:

                generateComparison("sete");
                break;

            case NOT_EQUAL:

                generateComparison("setne");
                break;

            case LESS:

                generateComparison("setl");
                break;

            case LESS_EQUAL:

                generateComparison("setle");
                break;

            case GREATER:

                generateComparison("setg");
                break;

            case GREATER_EQUAL:

                generateComparison("setge");
                break;

            case AND_AND:

                normalizeBoolean("%rax");
                normalizeBoolean("%r10");

                assembly.append(
                        "    andq %r10, %rax\n"
                );

                break;

            case OR_OR:

                normalizeBoolean("%rax");
                normalizeBoolean("%r10");

                assembly.append(
                        "    orq %r10, %rax\n"
                );

                break;

            default:
                break;
        }
    }

    // =========================================================
    // STRING COMPARISON
    // =========================================================

    private void generateStringComparison(
            BinaryExpression expression
    ) {

        generateExpression(
                expression.getLeft()
        );

        assembly.append(
                "    movq %rax, %rcx\n"
        );

        generateExpression(
                expression.getRight()
        );

        assembly.append(
                "    movq %rax, %rdx\n"
        );

        assembly.append(
                "    call strcmp\n"
        );

        assembly.append(
                "    cmpq $0, %rax\n"
        );

        if (expression.getOperator()
                == TokenType.EQUAL_EQUAL) {

            assembly.append(
                    "    sete %al\n"
            );

        } else {

            assembly.append(
                    "    setne %al\n"
            );
        }

        assembly.append(
                "    movzbq %al, %rax\n"
        );
    }

    // =========================================================
    // DECIMAL BINARY EXPRESSIONS
    // =========================================================

    private void generateDecimalBinaryExpression(
            BinaryExpression expression
    ) {

        TokenType operator =
                expression.getOperator();

        generateDecimalExpression(
                expression.getLeft()
        );

        assembly.append(
                "    subq $16, %rsp\n"
        );

        assembly.append(
                "    movsd %xmm0, 8(%rsp)\n"
        );

        generateDecimalExpression(
                expression.getRight()
        );

        assembly.append(
                "    movsd %xmm0, %xmm1\n"
        );

        assembly.append(
                "    movsd 8(%rsp), %xmm0\n"
        );

        assembly.append(
                "    addq $16, %rsp\n"
        );

        switch (operator) {

            case PLUS:

                assembly.append(
                        "    addsd %xmm1, %xmm0\n"
                );

                break;

            case MINUS:

                assembly.append(
                        "    subsd %xmm1, %xmm0\n"
                );

                break;

            case STAR:

                assembly.append(
                        "    mulsd %xmm1, %xmm0\n"
                );

                break;

            case SLASH:

                assembly.append(
                        "    divsd %xmm1, %xmm0\n"
                );

                break;

            case PERCENT:

                assembly.append(
                        "    call fmod\n"
                );

                break;

            case EQUAL_EQUAL:

                generateDecimalComparison("sete");
                break;

            case NOT_EQUAL:

                generateDecimalComparison("setne");
                break;

            case LESS:

                generateDecimalComparison("setb");
                break;

            case LESS_EQUAL:

                generateDecimalComparison("setbe");
                break;

            case GREATER:

                generateDecimalComparison("seta");
                break;

            case GREATER_EQUAL:

                generateDecimalComparison("setae");
                break;

            default:

                assembly.append(
                        "    pxor %xmm0, %xmm0\n"
                );

                break;
        }
    }

    // =========================================================
    // DECIMAL EXPRESSION
    // =========================================================

    private void generateDecimalExpression(
            Expression expression
    ) {

        generateExpression(expression);

        ensureDecimalResult(expression);
    }

    private void ensureDecimalResult(
            Expression expression
    ) {

        if (!isDecimalExpression(expression)) {

            assembly.append(
                    "    cvtsi2sd %rax, %xmm0\n"
            );
        }
    }

    // =========================================================
    // INTEGER COMPARISON
    // =========================================================

    private void generateComparison(
            String instruction
    ) {

        assembly.append(
                "    cmpq %r10, %rax\n"
        );

        assembly.append(
                "    " + instruction + " %al\n"
        );

        assembly.append(
                "    movzbq %al, %rax\n"
        );
    }

    // =========================================================
    // DECIMAL COMPARISON
    // =========================================================

    private void generateDecimalComparison(
            String instruction
    ) {

        assembly.append(
                "    ucomisd %xmm1, %xmm0\n"
        );

        assembly.append(
                "    " + instruction + " %al\n"
        );

        assembly.append(
                "    movzbq %al, %rax\n"
        );
    }

    // =========================================================
    // BOOLEAN NORMALIZATION
    // =========================================================

    private void normalizeBoolean(
            String register
    ) {

        assembly.append(
                "    cmpq $0, " + register + "\n"
        );

        if (register.equals("%rax")) {

            assembly.append(
                    "    setne %al\n"
            );

            assembly.append(
                    "    movzbq %al, %rax\n"
            );

        } else {

            assembly.append(
                    "    setne %r10b\n"
            );

            assembly.append(
                    "    movzbq %r10b, %r10\n"
            );
        }
    }

    // =========================================================
    // TYPE HELPERS
    // =========================================================

    private boolean isDecimalExpression(
            Expression expression
    ) {

        if (expression instanceof LiteralExpression literal) {

            return literal.getValue()
                    instanceof Double;
        }

        if (expression instanceof VariableExpression variable) {

            return decimalVariables.getOrDefault(
                    variable.getName(),
                    false
            );
        }

        if (expression instanceof UnaryExpression unary) {

            return unary.getOperator()
                    == TokenType.MINUS
                    && isDecimalExpression(
                    unary.getExpression()
            );
        }

        if (expression instanceof BinaryExpression binary) {

            TokenType op =
                    binary.getOperator();

            if (op == TokenType.PLUS
                    || op == TokenType.MINUS
                    || op == TokenType.STAR
                    || op == TokenType.SLASH
                    || op == TokenType.PERCENT) {

                return isDecimalExpression(
                        binary.getLeft()
                )
                        || isDecimalExpression(
                        binary.getRight()
                );
            }

            if (op == TokenType.EQUAL_EQUAL
                    || op == TokenType.NOT_EQUAL
                    || op == TokenType.LESS
                    || op == TokenType.LESS_EQUAL
                    || op == TokenType.GREATER
                    || op == TokenType.GREATER_EQUAL
                    || op == TokenType.AND_AND
                    || op == TokenType.OR_OR) {

                return false;
            }
        }

        if (expression instanceof CallExpression call) {

            return "decimal".equals(
                    functionReturnTypes.get(
                            call.getName()
                    )
            );
        }

        return false;
    }

    // =========================================================
    // STRING TYPE HELPER
    // =========================================================

    private boolean isStringExpression(
            Expression expression
    ) {

        if (expression instanceof LiteralExpression literal) {

            return literal.getValue()
                    instanceof String;
        }

        if (expression instanceof VariableExpression variable) {

            return stringVariables.getOrDefault(
                    variable.getName(),
                    false
            );
        }

        if (expression instanceof CallExpression call) {

            return "string".equals(
                    functionReturnTypes.get(
                            call.getName()
                    )
            );
        }

        return false;
    }

    // =========================================================
    // FUNCTION CALLS
    // =========================================================

    private void generateCall(
            CallExpression call
    ) {

        // -----------------------------------------------------
        // INPUT
        // -----------------------------------------------------

        if (call.getName().equals("input")) {

            assembly.append(
                    "    leaq -120(%rbp), %rdx\n"
            );

            assembly.append(
                    "    leaq input_format(%rip), %rcx\n"
            );

            assembly.append(
                    "    call scanf\n"
            );

            assembly.append(
                    "    movq -120(%rbp), %rax\n"
            );

            return;
        }

        // -----------------------------------------------------
        // OUTPUT
        // -----------------------------------------------------

        if (call.getName().equals("output")) {

            if (!call.getArguments().isEmpty()) {

                Expression argument =
                        call.getArguments().get(0);

                generateExpression(argument);

                // decimal
                if (isDecimalExpression(argument)) {

                    assembly.append(
                            "    movq %xmm0, %rdx\n"
                    );

                    // Required for floating-point
                    // varargs on Windows x64.
                    assembly.append(
                            "    movq %rdx, %xmm1\n"
                    );

                    assembly.append(
                            "    leaq format_decimal(%rip), %rcx\n"
                    );

                    assembly.append(
                            "    call printf\n"
                    );
                }

                // string
                else if (isStringExpression(argument)) {

                    assembly.append(
                            "    movq %rax, %rdx\n"
                    );

                    assembly.append(
                            "    leaq format_string(%rip), %rcx\n"
                    );

                    assembly.append(
                            "    call printf\n"
                    );
                }

                // int / truth / char
                else {

                    assembly.append(
                            "    movq %rax, %rdx\n"
                    );

                    assembly.append(
                            "    leaq format_int(%rip), %rcx\n"
                    );

                    assembly.append(
                            "    call printf\n"
                    );
                }
            }

            assembly.append(
                    "    movq $0, %rax\n"
            );

            return;
        }

        // -----------------------------------------------------
        // USER FUNCTION
        // -----------------------------------------------------

        String[] integerArgumentRegisters = {
                "%rcx",
                "%rdx",
                "%r8",
                "%r9"
        };

        String[] decimalArgumentRegisters = {
                "%xmm0",
                "%xmm1",
                "%xmm2",
                "%xmm3"
        };

        List<Expression> arguments =
                call.getArguments();

        /*
         * Evaluate arguments first.
         *
         * Each argument gets a temporary stack slot so that
         * evaluating later arguments cannot destroy earlier
         * argument values.
         */

        for (int i = 0;
             i < arguments.size() && i < 4;
             i++) {

            Expression argument =
                    arguments.get(i);

            generateExpression(argument);

            int scratchOffset =
                    -480 - (i * 8);

            if (isDecimalExpression(argument)) {

                ensureDecimalResult(argument);

                assembly.append(
                        "    movsd %xmm0, "
                                + scratchOffset
                                + "(%rbp)\n"
                );

            } else {

                // int / truth / char / string
                assembly.append(
                        "    movq %rax, "
                                + scratchOffset
                                + "(%rbp)\n"
                );
            }
        }

        /*
         * Move the saved arguments into the appropriate
         * Windows x64 argument registers.
         */

        for (int i = 0;
             i < arguments.size() && i < 4;
             i++) {

            Expression argument =
                    arguments.get(i);

            int scratchOffset =
                    -480 - (i * 8);

            if (isDecimalExpression(argument)) {

                assembly.append(
                        "    movsd "
                                + scratchOffset
                                + "(%rbp), "
                                + decimalArgumentRegisters[i]
                                + "\n"
                );

            } else {

                assembly.append(
                        "    movq "
                                + scratchOffset
                                + "(%rbp), "
                                + integerArgumentRegisters[i]
                                + "\n"
                );
            }
        }

        assembly.append(
                "    call "
                        + call.getName()
                        + "\n"
        );
    }

    // =========================================================
    // STRING LITERALS
    // =========================================================

    private String getStringLabel(
            String value
    ) {

        String existing =
                stringLabels.get(value);

        if (existing != null) {
            return existing;
        }

        String label =
                "string_" + stringLabelCounter++;

        stringLabels.put(
                value,
                label
        );

        stringData.append(
                label + ":\n"
        );

        stringData.append(
                "    .asciz \""
                        + escapeAssemblyString(value)
                        + "\"\n"
        );

        return label;
    }

    private String escapeAssemblyString(
            String value
    ) {

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // =========================================================
    // MEMORY / LABELS
    // =========================================================

    private String memoryLocation(
            int offset
    ) {

        return offset + "(%rbp)";
    }

    private String newLabel(
            String prefix
    ) {

        return prefix
                + "_"
                + labelCounter++;
    }
}