package burp.parse.turtleprov;

import burp.parse.turtleprov.generated.TurtleLexer;
import burp.parse.turtleprov.generated.TurtleParser;

import java.util.ArrayList;
import java.util.List;

public class TurtleProvParser {
    
    public static ProvStore parseTurtleFromString(String turtleContent) {
        CharStream input = CharStreams.fromString(turtleContent);
        TurtleLexer lexer = new TurtleLexer(input);
        
        List<ProvStore.SyntaxError> syntaxErrors = new ArrayList<>();
        
        BaseErrorListener errorListener = new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                String symbolText = offendingSymbol != null ? offendingSymbol.toString() : "";
                syntaxErrors.add(new ProvStore.SyntaxError(line, charPositionInLine, msg, symbolText));
            }
        };

        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);
        
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        TurtleParser parser = new TurtleParser(tokens);
        
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);
        
        ProvTurtleVisitor visitor = new ProvTurtleVisitor();
        ProvStore store = null;
        try {
            store = (ProvStore) visitor.visitTurtleDoc(parser.turtleDoc());
        } catch (Exception e) {
            // AST traversal failed due to missing nodes or unsupported constructs
            syntaxErrors.add(new ProvStore.SyntaxError(0, 0, e.getMessage(), "AST Traversal"));
            store = visitor.getStore();
        }
        
        if (store == null) {
            store = new ProvStore();
        }
        store.syntaxErrors.addAll(syntaxErrors);
        return store;
    }
}
