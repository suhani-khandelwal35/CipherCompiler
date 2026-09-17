
package codegen;

import ast.Program;
import ast.Expression;
import ast.Statement;
import ast.expressions.*;
import ast.statements.*;

import lexer.TokenType;

import java.util.HashMap;
import java.util.Map;

public class CodeGenerator {

    private final StringBuilder assembly =
            new StringBuilder();

    private final Map<String, Integer> variables =
            new HashMap<>();

    private int stackOffset = 0;
    private int labelCounter = 0;

    public String generate(Program program) {

        assembly.setLength(0);
        variables.clear();
        stackOffset = 0;
        labelCounter = 0;

        generateHeader();

        for (FunctionDeclaration function :
                program.getFunctions()) {

            generateFunction(function);
        }

        return assembly.toString();
    }

    // =========================================================
    // HEADER
    // =========================================================

    private void generateHeader() {

        assembly.append(".text\n");
        assembly.append(".globl main\n");

        // Windows / MinGW read-only data section
        assembly.append("\n.section .rdata\n");

        // Format string for printf
        assembly.append("format_int:\n");
        assembly.append("    .asciz \"%lld\\n\"\n");

        assembly.append("\n.text\n");
    }

    // =========================================================
    // FUNCTION GENERATION
    // =========================================================

    private void generateFunction(
            FunctionDeclaration function
    ) {

        variables.clear();
        stackOffset = 0;

        String name = function.getName();

        assembly.append("\n");
        assembly.append(".globl " + name + "\n");
        assembly.append(name + ":\n");

        // -----------------------------------------------------
        // Function prologue
        // -----------------------------------------------------

        assembly.append(
                "    pushq %rbp\n"
        );

        assembly.append(
                "    movq %rsp, %rbp\n"
        );

        /*
         * Reserve stack space for local variables.
         *
         * 128 bytes is enough for our current
         * simple compiler implementation.
         */
        assembly.append(
                "    subq $128, %rsp\n"
        );

        // -----------------------------------------------------
        // Function body
        // -----------------------------------------------------

        for (Statement statement :
                function.getBody()) {

            generateStatement(statement);
        }

        // -----------------------------------------------------
        // Default return
        // -----------------------------------------------------

        assembly.append(
                "    movq $0, %rax\n"
        );

        generateFunctionEpilogue();
    }

    // =========================================================
    // FUNCTION EPILOGUE
    // =========================================================

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

    private void generateStatement(
            Statement statement
    ) {

        // -----------------------------------------------------
        // Variable declaration
        // -----------------------------------------------------

        if (statement instanceof VariableDeclaration declaration) {

            stackOffset -= 8;

            variables.put(
                    declaration.getName(),
                    stackOffset
            );

            if (declaration.getInitializer() != null) {

                generateExpression(
                        declaration.getInitializer()
                );

                assembly.append(
                        "    movq %rax, "
                                + memoryLocation(stackOffset)
                                + "\n"
                );
            }

        }

        // -----------------------------------------------------
        // Assignment
        // -----------------------------------------------------

        else if (statement instanceof Assignment assignment) {

            generateExpression(
                    assignment.getValue()
            );

            Integer offset =
                    variables.get(
                            assignment.getName()
                    );

            if (offset != null) {

                assembly.append(
                        "    movq %rax, "
                                + memoryLocation(offset)
                                + "\n"
                );
            }
        }

        // -----------------------------------------------------
        // Return / send
        // -----------------------------------------------------

        else if (statement instanceof ReturnStatement returnStatement) {

            if (returnStatement.getValue() != null) {

                generateExpression(
                        returnStatement.getValue()
                );

            }

            else {

                assembly.append(
                        "    movq $0, %rax\n"
                );
            }

            generateFunctionEpilogue();
        }

        // -----------------------------------------------------
        // Expression statement
        // -----------------------------------------------------

        else if (statement instanceof ExpressionStatement expressionStatement) {

            generateExpression(
                    expressionStatement.getExpression()
            );
        }

        // -----------------------------------------------------
        // If statement
        // -----------------------------------------------------

        else if (statement instanceof IfStatement ifStatement) {

            generateIf(ifStatement);
        }

        // -----------------------------------------------------
        // While / loop
        // -----------------------------------------------------

        else if (statement instanceof WhileStatement whileStatement) {

            generateWhile(whileStatement);
        }

        // -----------------------------------------------------
        // Each
        // -----------------------------------------------------

        else if (statement instanceof EachStatement eachStatement) {

            generateEach(eachStatement);
        }

        // -----------------------------------------------------
        // Break
        // -----------------------------------------------------

        else if (statement instanceof BreakStatement) {

            // Will be implemented with loop labels.
        }

        // -----------------------------------------------------
        // Skip
        // -----------------------------------------------------

        else if (statement instanceof SkipStatement) {

            // Will be implemented with loop labels.
        }
    }

    // =========================================================
    // IF
    // =========================================================

    private void generateIf(
            IfStatement statement
    ) {

        String elseLabel =
                newLabel("else");

        String endLabel =
                newLabel("endif");

        // Generate condition
        generateExpression(
                statement.getCondition()
        );

        assembly.append(
                "    cmpq $0, %rax\n"
        );

        assembly.append(
                "    je "
                        + elseLabel
                        + "\n"
        );

        // Then branch
        for (Statement bodyStatement :
                statement.getThenBranch()) {

            generateStatement(bodyStatement);
        }

        assembly.append(
                "    jmp "
                        + endLabel
                        + "\n"
        );

        // Else label
        assembly.append(
                elseLabel
                        + ":\n"
        );

        // Else branch
        if (statement.getElseBranch() != null) {

            for (Statement bodyStatement :
                    statement.getElseBranch()) {

                generateStatement(bodyStatement);
            }
        }

        // End
        assembly.append(
                endLabel
                        + ":\n"
        );
    }

    // =========================================================
    // WHILE / LOOP
    // =========================================================

    private void generateWhile(
            WhileStatement statement
    ) {

        String startLabel =
                newLabel("loop");

        String endLabel =
                newLabel("endloop");

        // Start
        assembly.append(
                startLabel
                        + ":\n"
        );

        // Condition
        generateExpression(
                statement.getCondition()
        );

        assembly.append(
                "    cmpq $0, %rax\n"
        );

        assembly.append(
                "    je "
                        + endLabel
                        + "\n"
        );

        // Body
        for (Statement bodyStatement :
                statement.getBody()) {

            generateStatement(bodyStatement);
        }

        // Jump back
        assembly.append(
                "    jmp "
                        + startLabel
                        + "\n"
        );

        // End
        assembly.append(
                endLabel
                        + ":\n"
        );
    }

    // =========================================================
    // EACH
    // =========================================================

    private void generateEach(
            EachStatement statement
    ) {

        // Initializer
        if (statement.getInitializer() != null) {

            generateStatement(
                    statement.getInitializer()
            );
        }

        String startLabel =
                newLabel("each");

        String endLabel =
                newLabel("endeach");

        // Start
        assembly.append(
                startLabel
                        + ":\n"
        );

        // Condition
        if (statement.getCondition() != null) {

            generateExpression(
                    statement.getCondition()
            );

            assembly.append(
                    "    cmpq $0, %rax\n"
            );

            assembly.append(
                    "    je "
                            + endLabel
                            + "\n"
            );
        }

        // Body
        for (Statement bodyStatement :
                statement.getBody()) {

            generateStatement(bodyStatement);
        }

        // Update
        if (statement.getUpdate() != null) {

            generateStatement(
                    statement.getUpdate()
            );
        }

        // Repeat
        assembly.append(
                "    jmp "
                        + startLabel
                        + "\n"
        );

        // End
        assembly.append(
                endLabel
                        + ":\n"
        );
    }

    // =========================================================
    // EXPRESSIONS
    // =========================================================

    private void generateExpression(
            Expression expression
    ) {

        // -----------------------------------------------------
        // Literal
        // -----------------------------------------------------

        if (expression instanceof LiteralExpression literal) {

            Object value =
                    literal.getValue();

            if (value instanceof Integer) {

                assembly.append(
                        "    movq $"
                                + value
                                + ", %rax\n"
                );
            }

            else if (value instanceof Boolean) {

                boolean booleanValue =
                        (Boolean) value;

                assembly.append(
                        "    movq $"
                                + (booleanValue ? "1" : "0")
                                + ", %rax\n"
                );
            }

            else if (value instanceof Character) {

                char character =
                        (Character) value;

                assembly.append(
                        "    movq $"
                                + (int) character
                                + ", %rax\n"
                );
            }

            else if (value instanceof String) {

                /*
                 * String literals are not yet fully supported
                 * by the backend.
                 */
                assembly.append(
                        "    movq $0, %rax\n"
                );
            }

            else if (value instanceof Double) {

                /*
                 * Decimal floating-point code generation
                 * will be implemented later.
                 */
                assembly.append(
                        "    movq $0, %rax\n"
                );
            }
        }

        // -----------------------------------------------------
        // Variable
        // -----------------------------------------------------

        else if (expression instanceof VariableExpression variable) {

            Integer offset =
                    variables.get(
                            variable.getName()
                    );

            if (offset != null) {

                assembly.append(
                        "    movq "
                                + memoryLocation(offset)
                                + ", %rax\n"
                );
            }

            else {

                assembly.append(
                        "    movq $0, %rax\n"
                );
            }
        }

        // -----------------------------------------------------
        // Binary expression
        // -----------------------------------------------------

        else if (expression instanceof BinaryExpression binary) {

            generateBinaryExpression(binary);
        }

        // -----------------------------------------------------
        // Unary expression
        // -----------------------------------------------------

        else if (expression instanceof UnaryExpression unary) {

            generateExpression(
                    unary.getExpression()
            );

            if (unary.getOperator()
                    == TokenType.MINUS) {

                assembly.append(
                        "    negq %rax\n"
                );
            }

            else if (unary.getOperator()
                    == TokenType.NOT) {

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
        }

        // -----------------------------------------------------
        // Function call
        // -----------------------------------------------------

        else if (expression instanceof CallExpression call) {

            generateCall(call);
        }
    }

    // =========================================================
    // BINARY EXPRESSIONS
    // =========================================================

    private void generateBinaryExpression(
            BinaryExpression expression
    ) {

        // Generate left
        generateExpression(
                expression.getLeft()
        );

        // Save left value
        assembly.append(
                "    pushq %rax\n"
        );

        // Generate right
        generateExpression(
                expression.getRight()
        );

        /*
         * RAX = right
         *
         * R10 = right
         * RAX = left
         *
         * R10 is caller-saved on Windows x64,
         * so it is safer than RBX here.
         */

        assembly.append(
                "    movq %rax, %r10\n"
        );

        assembly.append(
                "    popq %rax\n"
        );

        switch (expression.getOperator()) {

            // -------------------------------------------------
            // Addition
            // -------------------------------------------------

            case PLUS:

                assembly.append(
                        "    addq %r10, %rax\n"
                );

                break;

            // -------------------------------------------------
            // Subtraction
            // -------------------------------------------------

            case MINUS:

                assembly.append(
                        "    subq %r10, %rax\n"
                );

                break;

            // -------------------------------------------------
            // Multiplication
            // -------------------------------------------------

            case STAR:

                assembly.append(
                        "    imulq %r10, %rax\n"
                );

                break;

            // -------------------------------------------------
            // Division
            // -------------------------------------------------

            case SLASH:

                assembly.append(
                        "    cqto\n"
                );

                assembly.append(
                        "    idivq %r10\n"
                );

                break;

            // -------------------------------------------------
            // Modulo
            // -------------------------------------------------

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

            // -------------------------------------------------
            // Equality
            // -------------------------------------------------

            case EQUAL_EQUAL:

                generateComparison("sete");

                break;

            // -------------------------------------------------
            // Not equal
            // -------------------------------------------------

            case NOT_EQUAL:

                generateComparison("setne");

                break;

            // -------------------------------------------------
            // Less
            // -------------------------------------------------

            case LESS:

                generateComparison("setl");

                break;

            // -------------------------------------------------
            // Less or equal
            // -------------------------------------------------

            case LESS_EQUAL:

                generateComparison("setle");

                break;

            // -------------------------------------------------
            // Greater
            // -------------------------------------------------

            case GREATER:

                generateComparison("setg");

                break;

            // -------------------------------------------------
            // Greater or equal
            // -------------------------------------------------

            case GREATER_EQUAL:

                generateComparison("setge");

                break;

            // -------------------------------------------------
            // Logical AND
            // -------------------------------------------------

            case AND_AND:

                assembly.append(
                        "    cmpq $0, %rax\n"
                );

                assembly.append(
                        "    setne %al\n"
                );

                assembly.append(
                        "    movzbq %al, %rax\n"
                );

                assembly.append(
                        "    cmpq $0, %r10\n"
                );

                assembly.append(
                        "    setne %r10b\n"
                );

                assembly.append(
                        "    movzbq %r10b, %r10\n"
                );

                assembly.append(
                        "    andq %r10, %rax\n"
                );

                break;

            // -------------------------------------------------
            // Logical OR
            // -------------------------------------------------

            case OR_OR:

                assembly.append(
                        "    cmpq $0, %rax\n"
                );

                assembly.append(
                        "    setne %al\n"
                );

                assembly.append(
                        "    movzbq %al, %rax\n"
                );

                assembly.append(
                        "    cmpq $0, %r10\n"
                );

                assembly.append(
                        "    setne %r10b\n"
                );

                assembly.append(
                        "    movzbq %r10b, %r10\n"
                );

                assembly.append(
                        "    orq %r10, %rax\n"
                );

                break;

            default:

                break;
        }
    }

    // =========================================================
    // COMPARISON
    // =========================================================

    private void generateComparison(
            String instruction
    ) {

        assembly.append(
                "    cmpq %r10, %rax\n"
        );

        assembly.append(
                "    "
                        + instruction
                        + " %al\n"
        );

        assembly.append(
                "    movzbq %al, %rax\n"
        );
    }

    // =========================================================
    // FUNCTION CALLS
    // =========================================================

    private void generateCall(
            CallExpression call
    ) {

        // -----------------------------------------------------
        // input()
        // -----------------------------------------------------

        if (call.getName().equals("input")) {

            /*
             * Temporary implementation.
             *
             * Real console input will be implemented later.
             */
            assembly.append(
                    "    movq $0, %rax\n"
            );

            return;
        }

        // -----------------------------------------------------
        // output()
        // -----------------------------------------------------

        if (call.getName().equals("output")) {

            if (!call.getArguments().isEmpty()) {

                /*
                 * Generate the expression.
                 *
                 * Result:
                 *     RAX = value to print
                 */

                generateExpression(
                        call.getArguments().get(0)
                );

                /*
                 * Windows x64 calling convention:
                 *
                 * RCX = first argument
                 * RDX = second argument
                 * R8  = third argument
                 * R9  = fourth argument
                 *
                 * printf(format, value)
                 *
                 * RCX = format
                 * RDX = value
                 */

                assembly.append(
                        "    movq %rax, %rdx\n"
                );

                assembly.append(
                        "    leaq format_int(%rip), %rcx\n"
                );

                /*
                 * Windows x64 requires 32 bytes
                 * of shadow/home space for function calls.
                 *
                 * Our stack is already aligned because
                 * the function prologue reserves 128 bytes.
                 */

                assembly.append(
                        "    subq $32, %rsp\n"
                );

                assembly.append(
                        "    call printf\n"
                );

                assembly.append(
                        "    addq $32, %rsp\n"
                );
            }

            /*
             * output() behaves as void.
             *
             * We keep RAX = 0.
             */

            assembly.append(
                    "    movq $0, %rax\n"
            );

            return;
        }

        // -----------------------------------------------------
        // User-defined function
        // -----------------------------------------------------

        assembly.append(
                "    call "
                        + call.getName()
                        + "\n"
        );
    }

    // =========================================================
    // MEMORY LOCATION
    // =========================================================

    private String memoryLocation(
            int offset
    ) {

        return offset
                + "(%rbp)";
    }

    // =========================================================
    // LABEL GENERATOR
    // =========================================================

    private String newLabel(
            String prefix
    ) {

        return prefix
                + "_"
                + labelCounter++;
    }
}

