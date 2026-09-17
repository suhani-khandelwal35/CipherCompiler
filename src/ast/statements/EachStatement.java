package ast.statements;

import ast.Expression;
import ast.Statement;

import java.util.List;

public class EachStatement extends Statement {

    private final Statement initializer;
    private final Expression condition;
    private final Statement update;
    private final List<Statement> body;

    public EachStatement(
            Statement initializer,
            Expression condition,
            Statement update,
            List<Statement> body
    ) {
        this.initializer = initializer;
        this.condition = condition;
        this.update = update;
        this.body = body;
    }

    public Statement getInitializer() {
        return initializer;
    }

    public Expression getCondition() {
        return condition;
    }

    public Statement getUpdate() {
        return update;
    }

    public List<Statement> getBody() {
        return body;
    }
}