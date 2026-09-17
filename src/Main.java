import ast.Program;
import lexer.Lexer;
import lexer.Token;
import parser.Parser;
import codegen.CodeGenerator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {

        String source = """
                func int main() {
                    int x = input();
                    output(x);
                    send x;
                }
                """;

        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.tokenize();

        Parser parser = new Parser(tokens);
        Program program = parser.parse();

        CodeGenerator codeGenerator = new CodeGenerator();
        String assembly = codeGenerator.generate(program);

        Files.writeString(
                Path.of("out/program.s"),
                assembly
        );

        System.out.println("Assembly generated: out/program.s");
    }
}