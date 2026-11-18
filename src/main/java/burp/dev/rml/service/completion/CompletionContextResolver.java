package burp.dev.rml.service.completion;

import burp.dev.rml.service.MappingWorkspace;
import burp.dev.rml.service.plan.PlanNodeDescriptor;
import burp.dev.rml.service.selector.CursorSelector;
import burp.model.PlanNode;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/// Resolves the semantic context and replaces ranges for autocompletion.
/// Populates the [CompletionContext] with the resolved AST node, prefix map, document location, and editing offsets.
@NullMarked
public class CompletionContextResolver {

    /// Constructs a fully populated CompletionContext from LSP parameters and the workspace.
    public @Nullable CompletionContext buildContext(
            @Nullable CompletionParams params,
            MappingWorkspace workspace
    ) {
        if (params == null || params.getTextDocument() == null || params.getPosition() == null) {
            return null;
        }

        try {
            String uri = params.getTextDocument().getUri();
            Position pos = params.getPosition();
            CursorSelector cs = new CursorSelector(uri, pos);

            String openDoc = workspace.getOpenDocumentText(uri);
            if (openDoc == null) {
                Path mappingPath = workspace.normalizePath(uri);
                if (!mappingPath.isAbsolute()) {
                    mappingPath = workspace.getCurrentWorkingDirectory().resolve(mappingPath).normalize();
                }
                if (Files.exists(mappingPath)) {
                    openDoc = Files.readString(mappingPath);
                }
            }

            String effectivePrefix = "";
            Range replaceRange = null;
            if (openDoc != null) {
                String[] lines = openDoc.split("\r?\n");
                if (pos.getLine() >= 0 && pos.getLine() < lines.length) {
                    String lineText = lines[pos.getLine()];
                    int charPos = Math.min(pos.getCharacter(), lineText.length());
                    int start = charPos;
                    while (start > 0) {
                        char ch = lineText.charAt(start - 1);
                        if (isTokenDelimiter(ch)) {
                            break;
                        }
                        start--;
                    }
                    effectivePrefix = lineText.substring(start, charPos);
                    int end = charPos;
                    while (end < lineText.length()) {
                        char ch = lineText.charAt(end);
                        if (isTokenDelimiter(ch)) {
                            break;
                        }
                        end++;
                    }
                    replaceRange = new Range(new Position(pos.getLine(), start), new Position(pos.getLine(), end));
                }
            }

            MappingWorkspace.ResolvedNode resolved = workspace.resolveNode(cs);
            PlanNode targetNode = resolved.targetNode();
            if (replaceRange == null) {
                replaceRange = PlanNodeDescriptor.getFirstRangeForNode(targetNode);
            }

            MappingWorkspace.LoadedMapping loaded = workspace.loadMappingDetailed(uri);
            Map<String, String> prefixMap = new HashMap<>(loaded.document().prefixMap);
            if (loaded.model() != null) {
                prefixMap.putAll(loaded.model().getNsPrefixMap());
            }

            return new CompletionContext(
                    uri,
                    pos,
                    effectivePrefix,
                    replaceRange,
                    loaded,
                    targetNode,
                    prefixMap,
                    workspace
            );
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isTokenDelimiter(char ch) {
        return Character.isWhitespace(ch) || ch == '"' || ch == '\'' || ch == '{' || ch == '}'
                || ch == '(' || ch == ')' || ch == ';' || ch == ',' || ch == '[' || ch == ']';
    }
}
