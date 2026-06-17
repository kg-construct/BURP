package burp.parse.turtleprov;

import burp.reporting.PointRange;
import org.antlr.v4.runtime.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TurtleProvParser {

    public static ProvStore parseTurtleFromString(String turtleContent) {
        CharStream input = CharStreams.fromString(turtleContent);
        return parse(input);
    }

    public static ProvStore parseTurtleFromPath(Path path) throws IOException {
        CharStream input = CharStreams.fromPath(path);
        return parse(input);
    }

    public static ProvStore parseTurtleFromStream(InputStream stream) throws IOException {
        CharStream input = CharStreams.fromStream(stream);
        return parse(input);
    }

    private static ProvStore parse(CharStream input) {
        TurtleLexer lexer = new TurtleLexer(input);
        List<ProvStore.SyntaxError> syntaxErrors = new ArrayList<>();

        final String fullText = input.size() > 0 ? input.getText(org.antlr.v4.runtime.misc.Interval.of(0, input.size() - 1)) : "";

        BaseErrorListener errorListener = new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                String symbolText = "";
                PointRange range = null;
                boolean hasOffendingSymbol = false;

                if (offendingSymbol instanceof Token token) {
                    symbolText = token.getText();
                    if (symbolText != null && !symbolText.isEmpty() && token.getType() != Token.EOF) {
                        hasOffendingSymbol = true;
                    }
                } else if (offendingSymbol != null) {
                    symbolText = offendingSymbol.toString();
                    if (!symbolText.isEmpty() && !symbolText.equals("<EOF>")) {
                        hasOffendingSymbol = true;
                    }
                }

                if (hasOffendingSymbol) {
                    int len = symbolText.length();
                    int startLine = (offendingSymbol instanceof Token token) ? token.getLine() - 1 : line - 1;
                    int startCol = (offendingSymbol instanceof Token token) ? token.getCharPositionInLine() : charPositionInLine;
                    range = new PointRange(
                        new Point(startLine, startCol),
                        new Point(startLine, startCol + len)
                    );
                } else {
                    // FIXME Improve the detection of the end of the token when having a token recognition error at:'...'
                    //  It should surely be possible to have that token length data from somewhere else than parsing the anltr message.
                    String quotedText = extractQuotedText(msg);
                    if (!quotedText.isEmpty() && !quotedText.equals("<EOF>")) {
                        int len = quotedText.length();
                        range = new PointRange(
                            new Point(line - 1, charPositionInLine),
                            new Point(line - 1, charPositionInLine + len)
                        );
                    } else {
                        int startOffset = (offendingSymbol instanceof Token token) ? token.getStartIndex() : getOffsetOfLineAndColumn(fullText, line, charPositionInLine);
                        int len = getLengthToLineEnd(fullText, startOffset);
                        range = new PointRange(
                            new Point(line - 1, charPositionInLine),
                            new Point(line - 1, charPositionInLine + len)
                        );
                    }
                }
                syntaxErrors.add(new ProvStore.SyntaxError(line, charPositionInLine, msg, symbolText, range));
            }
        };

        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        TurtleParser parser = new TurtleParser(tokens);

        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        ProvTurtleVisitor visitor = new ProvTurtleVisitor();
        ProvStore store;
        try {
            store = visitor.visitTurtleDoc(parser.turtleDoc());
        } catch (TurtleProvException e) {
            int line = e.getRange() != null ? e.getRange().start().line + 1 : 0;
            int col = e.getRange() != null ? e.getRange().start().column : 0;
            syntaxErrors.add(new ProvStore.SyntaxError(line, col, e.getMessage(), "AST Traversal", e.getRange()));
            store = visitor.getStore();
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
    private static int getOffsetOfLineAndColumn(String text, int targetLine, int targetCol) {
        int currentLine = 1;
        int currentCol = 0;
        for (int i = 0; i < text.length(); i++) {
            if (currentLine == targetLine && currentCol == targetCol) {
                return i;
            }
            char c = text.charAt(i);
            if (c == '\n') {
                currentLine++;
                currentCol = 0;
            } else {
                currentCol++;
            }
        }
        return -1;
    }

    private static int getLengthToLineEnd(String text, int startOffset) {
        if (startOffset < 0 || startOffset >= text.length()) {
            return 1;
        }
        int len = 0;
        for (int i = startOffset; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                break;
            }
            len++;
        }
        return len > 0 ? len : 1;
    }
    private static String extractQuotedText(String msg) {
        if (msg == null) return "";
        int firstQuote = msg.indexOf('\'');
        if (firstQuote >= 0) {
            int secondQuote = msg.indexOf('\'', firstQuote + 1);
            if (secondQuote > firstQuote) {
                return msg.substring(firstQuote + 1, secondQuote);
            }
        }
        return "";
    }
}
