package lexer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lexer {

    private final String source;
    private final List<Token> tokens = new ArrayList<>();

    private int current = 0;
    private int start = 0;

    private int line = 1;
    private int column = 1;

    private int startLine = 1;
    private int startColumn = 1;

    private static final Map<String, TokenType> keywords = new HashMap<>();

    static {
        // Functions and control flow
        keywords.put("func", TokenType.FUNC);
        keywords.put("send", TokenType.SEND);

        keywords.put("if", TokenType.IF);
        keywords.put("else", TokenType.ELSE);

        // Arc supports "while" and "loop" as loop keywords
        keywords.put("while", TokenType.LOOP);
        keywords.put("loop", TokenType.LOOP);

        keywords.put("each", TokenType.EACH);

        keywords.put("break", TokenType.BREAK);
        keywords.put("skip", TokenType.SKIP);

        // Built-in functions
        keywords.put("output", TokenType.OUTPUT);
        keywords.put("input", TokenType.INPUT);

        // Data types
        keywords.put("int", TokenType.INT);
        keywords.put("decimal", TokenType.DECIMAL);
        keywords.put("truth", TokenType.TRUTH);
        keywords.put("char", TokenType.CHAR);
        keywords.put("string", TokenType.STRING);
        keywords.put("void", TokenType.VOID);

        // Boolean literals
        keywords.put("yes", TokenType.YES);
        keywords.put("no", TokenType.NO);
    }

    public Lexer(String source) {
        this.source = source;
    }

    public List<Token> tokenize() {
        while (!isAtEnd()) {
            start = current;
            startLine = line;
            startColumn = column;

            scanToken();
        }

        tokens.add(new Token(
                TokenType.EOF,
                "",
                line,
                column
        ));

        return tokens;
    }

    private void scanToken() {
        char c = advance();

        switch (c) {

            // Parentheses
            case '(':
                addToken(TokenType.LEFT_PAREN);
                break;

            case ')':
                addToken(TokenType.RIGHT_PAREN);
                break;

            // Braces
            case '{':
                addToken(TokenType.LEFT_BRACE);
                break;

            case '}':
                addToken(TokenType.RIGHT_BRACE);
                break;

            // Brackets
            case '[':
                addToken(TokenType.LEFT_BRACKET);
                break;

            case ']':
                addToken(TokenType.RIGHT_BRACKET);
                break;

            // Separators
            case ',':
                addToken(TokenType.COMMA);
                break;

            case ';':
                addToken(TokenType.SEMICOLON);
                break;

            // Arithmetic
            case '+':
                addToken(TokenType.PLUS);
                break;

            case '-':
                addToken(TokenType.MINUS);
                break;

            case '*':
                addToken(TokenType.STAR);
                break;

            case '%':
                addToken(TokenType.PERCENT);
                break;

            // Assignment / equality
            case '=':
                if (match('=')) {
                    addToken(TokenType.EQUAL_EQUAL);
                } else {
                    addToken(TokenType.ASSIGN);
                }
                break;

            // Not / not equal
            case '!':
                if (match('=')) {
                    addToken(TokenType.NOT_EQUAL);
                } else {
                    addToken(TokenType.NOT);
                }
                break;

            // Less than / less than or equal
            case '<':
                if (match('=')) {
                    addToken(TokenType.LESS_EQUAL);
                } else {
                    addToken(TokenType.LESS);
                }
                break;

            // Greater than / greater than or equal
            case '>':
                if (match('=')) {
                    addToken(TokenType.GREATER_EQUAL);
                } else {
                    addToken(TokenType.GREATER);
                }
                break;

            // Logical AND
            case '&':
                if (match('&')) {
                    addToken(TokenType.AND_AND);
                } else {
                    error("Expected '&' after '&'.");
                }
                break;

            // Logical OR
            case '|':
                if (match('|')) {
                    addToken(TokenType.OR_OR);
                } else {
                    error("Expected '|' after '|'.");
                }
                break;

            // Division / comments
            case '/':
                if (match('/')) {
                    skipLineComment();
                } else if (match('*')) {
                    skipBlockComment();
                } else {
                    addToken(TokenType.SLASH);
                }
                break;

            // Whitespace
            case ' ':
            case '\r':
            case '\t':
                break;

            // New line
            case '\n':
                line++;
                column = 1;
                break;

            // Character literal
            case '\'':
                charLiteral();
                break;

            // String literal
            case '"':
                stringLiteral();
                break;

            // Identifiers and numbers
            default:
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    error("Unexpected character: '" + c + "'");
                }
                break;
        }
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) {
            advance();
        }

        String text = source.substring(start, current);

        TokenType type = keywords.get(text);

        if (type == null) {
            type = TokenType.IDENTIFIER;
        }

        addToken(type);
    }

    private void number() {
        while (isDigit(peek())) {
            advance();
        }

        // Decimal number
        if (peek() == '.' && isDigit(peekNext())) {
            advance();

            while (isDigit(peek())) {
                advance();
            }

            addToken(TokenType.DECIMAL_LITERAL);
        } else {
            addToken(TokenType.INTEGER_LITERAL);
        }
    }

    private void charLiteral() {
        if (isAtEnd()) {
            error("Unterminated character literal.");
            return;
        }

        char value = advance();

        if (value == '\n') {
            error("Unterminated character literal.");
            return;
        }

        if (!match('\'')) {
            error("Character literal must contain exactly one character.");
            return;
        }

        addToken(TokenType.CHAR_LITERAL);
    }

    private void stringLiteral() {
        while (peek() != '"' && !isAtEnd()) {

            if (peek() == '\n') {
                line++;
                column = 1;
                advance();
            } else {
                advance();
            }
        }

        if (isAtEnd()) {
            error("Unterminated string literal.");
            return;
        }

        // Closing "
        advance();

        addToken(TokenType.STRING_LITERAL);
    }

    private void skipLineComment() {
        while (peek() != '\n' && !isAtEnd()) {
            advance();
        }
    }

    private void skipBlockComment() {
        while (!isAtEnd()) {

            if (peek() == '*' && peekNext() == '/') {
                advance();
                advance();
                return;
            }

            if (peek() == '\n') {
                line++;
                column = 1;
                advance();
            } else {
                advance();
            }
        }

        error("Unterminated block comment.");
    }

    private char advance() {
        char c = source.charAt(current);

        current++;
        column++;

        return c;
    }

    private boolean match(char expected) {
        if (isAtEnd()) {
            return false;
        }

        if (source.charAt(current) != expected) {
            return false;
        }

        current++;
        column++;

        return true;
    }

    private char peek() {
        if (isAtEnd()) {
            return '\0';
        }

        return source.charAt(current);
    }

    private char peekNext() {
        if (current + 1 >= source.length()) {
            return '\0';
        }

        return source.charAt(current + 1);
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private void addToken(TokenType type) {
        String text = source.substring(start, current);

        tokens.add(new Token(
                type,
                text,
                startLine,
                startColumn
        ));
    }

    private void error(String message) {
        System.err.println(
                "Lexer error at line "
                        + line
                        + ", column "
                        + column
                        + ": "
                        + message
        );
    }
}