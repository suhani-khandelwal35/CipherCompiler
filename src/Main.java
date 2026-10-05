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
                func decimal add(decimal a, decimal b) {
                    send a + b;
                }

                func int main() {
                    decimal x = 12.5;
                    decimal y = 2.5;
                    decimal z = x * y + 1.0;

                    output(x);
                    output(y);
                    output(z);
                    output(add(x, y));

                    if (z > 30.0) {
                        output(1);
                    } else {
                        output(0);
                    }

                    send 0;
                }
                """;

        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.tokenize();

        Parser parser = new Parser(tokens);
        Program program = parser.parse();

        CodeGenerator codeGenerator = new CodeGenerator();
        String assembly = codeGenerator.generate(program);

        Files.writeString(Path.of("out/program.s"), assembly);
        System.out.println("Assembly generated: out/program.s");
    }
}
