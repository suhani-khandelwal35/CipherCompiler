package semantic;

import java.util.HashMap;
import java.util.Map;

public class SymbolTable {

    private final Map<String, Symbol> symbols = new HashMap<>();
    private final SymbolTable parent;

    public SymbolTable() {
        this(null);
    }

    public SymbolTable(SymbolTable parent) {
        this.parent = parent;
    }

    public boolean define(String name, String type) {

        if (symbols.containsKey(name)) {
            return false;
        }

        symbols.put(name, new Symbol(name, type));
        return true;
    }

    public Symbol resolve(String name) {

        Symbol symbol = symbols.get(name);

        if (symbol != null) {
            return symbol;
        }

        if (parent != null) {
            return parent.resolve(name);
        }

        return null;
    }

    public boolean contains(String name) {
        return resolve(name) != null;
    }
}