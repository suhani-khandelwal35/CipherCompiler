import ast.Program;
import codegen.CodeGenerator;
import lexer.Lexer;
import lexer.Token;
import parser.Parser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {

        String source = """
                func void main() {
                    string name = "CipherCompiler";
                    output(name);
                }
                """;

        Lexer lexer = new Lexer(source);

        List<Token> tokens = lexer.tokenize();

        Parser parser = new Parser(tokens);

        Program program = parser.parse();

        CodeGenerator codeGenerator = new CodeGenerator();

        String assembly = codeGenerator.generate(program);

        Files.createDirectories(Path.of("out"));

        Files.writeString(
                Path.of("out/program.s"),
                assembly
        );

        System.out.println("Compilation successful.");
        System.out.println("Assembly written to out/program.s");
    }
}