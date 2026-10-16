package burp.parse;

import burp.model.RawReference;
import burp.model.Template;
import burp.model.Template.LiteralSegment;
import burp.model.Template.ReferenceSegment;
import burp.model.Template.Segment;
import burp.parse.turtleprov.NodeInfo;
import burp.parse.turtleprov.OffsetMapper;
import burp.parse.turtleprov.Point;
import burp.parse.turtleprov.RDF12Converter;
import burp.reporting.*;
import burp.vocabularies.RER;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.rdf.model.Statement;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parser for RML template strings.
 * Converts template strings into a list of literal and reference segments,
 * validates escaping rules according to the RML specification,
 * emits {@code ReferenceFormulationSyntaxError} with precise character ranges upon errors,
 * and attaches StatementPart origins for precise Turtle source mapping.
 */
@NullMarked
public class TemplateParser {

    public static Template parse(
            String template,
            @Nullable Statement stmt,
            @Nullable RmlExecutionReport report,
            @Nullable ValidationMode validationMode
    ) {
        return new Template(template, stmt, report, validationMode);
    }

    public static Template parse(String template, @Nullable Statement stmt) {
        return parse(template, stmt, null, ValidationMode.STRICT);
    }

    public static Template parse(String template) {
        return parse(template, null, null, ValidationMode.STRICT);
    }

    public static List<Segment> parseSegments(
            String template,
            Template templateObj,
            @Nullable Statement stmt,
            @Nullable RmlExecutionReport report,
            ValidationMode validationMode
    ) {
        return new Session(template, templateObj, stmt, report, validationMode).parse();
    }

    @NullMarked
    private static class Session {
        private final String template;
        private final Template templateObj;
        private final @Nullable Statement stmt;
        private final @Nullable RmlExecutionReport report;
        private final ValidationMode validationMode;
        private final int len;

        private int i = 0;
        private int literalStart = 0;
        private final List<Segment> result = new ArrayList<>();

        Session(
                String template,
                Template templateObj,
                @Nullable Statement stmt,
                @Nullable RmlExecutionReport report,
                ValidationMode validationMode
        ) {
            this.template = template;
            this.templateObj = templateObj;
            this.stmt = stmt;
            this.report = report;
            this.validationMode = validationMode;
            this.len = template.length();
        }

        List<Segment> parse() {
            StringBuilder literalBuf = new StringBuilder();
            literalStart = 0;

            while (i < len) {
                char c = template.charAt(i);

                if (c == '\\') {
                    if (!appendEscapedCharacter(literalBuf)) {
                        return Collections.emptyList();
                    }
                } else if (c == '}') {
                    emitSyntaxError("\"}\" has non escaped }", i, i + 1);
                    return Collections.emptyList();
                } else if (c == '{') {
                    if (!parseReference(literalBuf)) {
                        return Collections.emptyList();
                    }
                } else {
                    literalBuf.append(c);
                    i++;
                }
            }

            flushLiteral(literalBuf, literalStart);

            if (result.stream().noneMatch(s -> s instanceof ReferenceSegment)) {
                String msg = "Template (\"" + template + "\") must contain at least one reference expression enclosed in unescaped curly braces or should be an rml:constant.";
                emitError(msg, 0, len, RER.TemplateSyntaxError);
            }

            return result;
        }

        private boolean parseReference(StringBuilder literalBuf) {
            flushLiteral(literalBuf, literalStart);

            int refStart = i++; // move past '{'
            int refContentStart = i;
            StringBuilder refBuf = new StringBuilder();
            OffsetMapper.Builder refOffsetBuilder = OffsetMapper.builder();

            while (i < len) {
                char rc = template.charAt(i);

                if (rc == '\\') {
                    int rawStart = i;
                    int decStart = refBuf.length();
                    if (!appendEscapedCharacter(refBuf)) {
                        return false;
                    }
                    refOffsetBuilder.recordEscape(refBuf.length(), i - rawStart, refBuf.length() - decStart);
                } else if (rc == '}') {
                    if (refBuf.isEmpty()) {
                        emitSyntaxError("Empty reference expression '{}' in template: " + template, refStart, i + 1);
                        return false;
                    }
                    addReferenceSegment(refContentStart, refBuf.toString(), refOffsetBuilder.build());
                    i++; // past '}'
                    literalStart = i;
                    return true;
                } else if (rc == '{') {
                    handleNestedOpeningBrace(i);
                    return false;
                } else {
                    refBuf.append(rc);
                    i++;
                }
            }

            handleUnclosedReference(refStart);
            return false;
        }

        private void flushLiteral(StringBuilder literalBuf, int startOffset) {
            if (!literalBuf.isEmpty()) {
                PointRange range = new PointRange(Point.fromOffset(template, startOffset), Point.fromOffset(template, i));
                result.add(new LiteralSegment(startOffset, range, templateObj, literalBuf.toString()));
                literalBuf.setLength(0);
            }
        }

        private void addReferenceSegment(int contentStart, String referenceValue, OffsetMapper innerOffsetMapper) {
            PointRange range = new PointRange(Point.fromOffset(template, contentStart), Point.fromOffset(template, i));
            Origin origin = (stmt == null)
                    ? new Origin()
                    : new Origin(Collections.singletonList(new LiteralPart(stmt, range)));
            RawReference rawRef = new RawReference(referenceValue, origin);

            OffsetMapper outerMapper = null;
            if (stmt != null && stmt.getModel() != null) {
                var tripleInfo = RDF12Converter.fromAnnotations(stmt, stmt.getModel());
                if (tripleInfo.objectInfo() != null) {
                    outerMapper = tripleInfo.objectInfo().offsetMapper();
                }
            }
            OffsetMapper composedMapper = OffsetMapper.compose(innerOffsetMapper, outerMapper, contentStart);

            ReferenceSegment seg = new ReferenceSegment(contentStart, range, templateObj, rawRef, composedMapper, innerOffsetMapper);
            rawRef.setParent(seg);
            result.add(seg);
        }

        private boolean appendEscapedCharacter(StringBuilder buf) {
            if (i + 1 >= len) {
                emitSyntaxError("Trailing unescaped backslash in template \"" + template + "\"", i, len);
                return false;
            }
            char next = template.charAt(i + 1);
            if (next == '{' || next == '}' || next == '\\') {
                buf.append(next);
            } else {
                buf.append('\\').append(next);
            }
            i += 2;
            return true;
        }

        private void handleNestedOpeningBrace(int braceIndex) {
            int nextClosing = findNextUnescaped(braceIndex + 1, '}');
            if (nextClosing != -1) {
                int errEnd = nextClosing + 1;
                String snippet = template.substring(braceIndex, errEnd);
                emitSyntaxError(snippet + " has non escaped {}", braceIndex, errEnd);
            } else {
                int errEnd = len;
                String snippet = template.substring(braceIndex, errEnd);
                emitSyntaxError(snippet + " has non escaped {", braceIndex, errEnd);
            }
        }

        private void handleUnclosedReference(int refStart) {
            String snippet = template.substring(refStart, len);
            emitSyntaxError(snippet + " has non escaped {", refStart, len);
        }

        private int findNextUnescaped(int fromIndex, char target) {
            int j = fromIndex;
            while (j < len) {
                char c = template.charAt(j);
                if (c == '\\') {
                    j += 2;
                } else if (c == target) {
                    return j;
                } else if (c == '{' && target == '}') {
                    return -1;
                } else {
                    j++;
                }
            }
            return -1;
        }

        private void emitSyntaxError(String message, int startOffset, int endOffset) {
            emitError(message, startOffset, endOffset, RER.ReferenceFormulationSyntaxError);
        }

        private void emitError(String message, int startOffset, int endOffset, OntClass errorType) {
            Point startPoint = Point.fromOffset(template, startOffset);
            Point endPoint = Point.fromOffset(template, endOffset);
            PointRange range = new PointRange(startPoint, endPoint);
            Origin origin = (stmt == null)
                    ? new Origin()
                    : new Origin(Collections.singletonList(new LiteralPart(stmt, range)));
            RmlError error = new RmlError(message, origin, errorType, null, Collections.emptyMap(), templateObj);

            if (report != null && !report.getErrors().contains(error)) {
                report.getErrors().add(error);
            }

            if (validationMode == ValidationMode.STRICT) {
                throw new BurpException(error);
            }
        }
    }
}
