import ast.Program;
import lexer.Lexer;
import lexer.Token;
import parser.Parser;
import semantic.SemanticAnalyzer;
import codegen.CodeGenerator;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        String source = """
                func int main() {

                int x = 10;
                int y = 20;

                int result = x + y;

                output(result);

                send result;
                }
                """;

        // 1. LEXER

        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.tokenize();

      
        // 2. PARSER

        Parser parser = new Parser(tokens);
        Program program = parser.parse();

        
        // 3. SEMANTIC ANALYSIs

        SemanticAnalyzer analyzer =
                new SemanticAnalyzer();

        analyzer.analyze(program);

        
        // 4. CODE GENERATION

        CodeGenerator generator =
                new CodeGenerator();

        String assembly =
                generator.generate(program);

                
        // 5. WRITE ASSEMBLY FILE
        

        String outputFile = "out/program.s";

        try (FileWriter writer =
                     new FileWriter(outputFile)) {

            writer.write(assembly);

            System.out.println(
                    "Assembly generated: "
                            + outputFile
            );

        } catch (IOException e) {

            System.err.println(
                    "Failed to write assembly: "
                            + e.getMessage()
            );
        }
    }
}