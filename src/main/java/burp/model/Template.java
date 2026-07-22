package burp.model;

import burp.parse.turtleprov.Point;
import burp.reporting.LiteralPart;
import burp.reporting.Origin;
import burp.reporting.PointRange;
import burp.util.Util;
import com.google.common.collect.Lists;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.jena.rdf.model.Statement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Template implements Expression {

    private String template;
    private Statement stmt;
    private PlanNode parent = null;
    private List<Segment> segments;

    public Template(String template) {
        this(template, null);
    }

    public Template(String template, Statement stmt) {
        this.template = template;
        this.stmt = stmt;
        this.segments = parseTemplate();
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public Statement getStmt() {
        return stmt;
    }

    public void setStmt(Statement stmt) {
        this.stmt = stmt;
    }

    public List<Segment> getSegments() {
        return segments;
    }

    public void setSegments(List<Segment> segments) {
        this.segments = segments;
    }

    @Override
    public PlanNode getParent() {
        return parent;
    }

    @Override
    public void setParent(PlanNode parent) {
        this.parent = parent;
    }

    @Override
    public Iterable<PlanNode> children() {
        return new ArrayList<>(segments);
    }

    public List<String> values(Iteration i) {
        return values(i, TemplateReferenceSafety.UNSAFE);
    }

    public List<String> values(Iteration i, boolean safe) {
        return values(i, safe ? TemplateReferenceSafety.SAFE_IRI : TemplateReferenceSafety.UNSAFE);
    }

    public List<String> values(Iteration i, TemplateReferenceSafety safety) {
        List<List<String>> evaluatedSegments = new ArrayList<>();
        for (Segment segment : segments) {
            evaluatedSegments.add(switch (segment) {
                case ReferenceSegment refSegment -> {
                    List<String> refVals = refSegment.reference().getStrings(i);
                    yield switch (safety) {
                        case SAFE_IRI -> refVals.stream().map(Util::toIRISafe).collect(Collectors.toList());
                        case SAFE_URI -> refVals.stream().map(Util::toURISafe).collect(Collectors.toList());
                        case UNSAFE -> refVals;
                    };
                }
                case LiteralSegment literalSegment -> Collections.singletonList(literalSegment.literal());
            });
        }

        return Lists.cartesianProduct(evaluatedSegments).stream()
                .map(list -> String.join("", list))
                .collect(Collectors.toList());
    }

    private static final Pattern bracesPattern = Pattern.compile("(?<!\\\\)\\{(.+?)(?<!\\\\)}");
    public List<String> references() {
        List<String> list = new ArrayList<>();
        Matcher m = bracesPattern.matcher(template);
        while (m.find()) {
            String temp = template.substring(m.start(1), m.end(1));
            list.add(StringEscapeUtils.unescapeJava(temp));
        }
        return list;
    }


    public sealed interface Segment extends PlanNode permits LiteralSegment, ReferenceSegment {
        int offset();

        PointRange range();

        PlanNode parent();

        @Override
        default PlanNode getParent() {
            return parent();
        }

        @Override
        default void setParent(PlanNode parent) {
            if (parent != parent()) {
                throw new UnsupportedOperationException("Cannot mutate parent of a template segment: " + this);
            }
        }
    }

    public record LiteralSegment(int offset, PointRange range, Template parent, String literal) implements Segment {
    }

    public record ReferenceSegment(int offset, PointRange range, Template parent, Reference reference)
            implements Segment {
        @Override
        public Iterable<PlanNode> children() {
            return Collections.singletonList(reference);
        }
    }

    public enum SegmentKind {Literal, Reference}

    private record SegmentData(SegmentKind kind, String value, int offset) {
    }

    private List<Segment> parseTemplate() {
        String rest = template;
        int offset = 0;
        List<SegmentData> segments = new ArrayList<>();

        while (!rest.isEmpty()) {
            Matcher m = bracesPattern.matcher(rest);
            if (m.find()) {
                if (m.start() > 0) {
                    String literal = rest.substring(0, m.start());
                    String escapeLiteral = escape(literal);
                    segments.add(new SegmentData(SegmentKind.Literal, escapeLiteral, offset));
                    offset += literal.length();
                }
                String reference = m.group(1);
                String escapeReference = escape(reference);
                segments.add(new SegmentData(SegmentKind.Reference, escapeReference, offset + 1));

                offset += m.end() - m.start(); // Advance offset by the exact matched length (including braces)
                rest = rest.substring(m.end());
            } else {
                segments.add(new SegmentData(SegmentKind.Literal, escape(rest), offset));
                offset += rest.length();
                rest = "";
            }
        }
        return constructSegmentsRangeAndReference(segments);
    }

    private List<Segment> constructSegmentsRangeAndReference(List<SegmentData> segments) {
        if (segments.isEmpty()) return Collections.emptyList();

        List<Point> points = new ArrayList<>();
        for (SegmentData segment : segments) {
            points.add(Point.fromOffset(template, segment.offset()));
        }
        points.add(Point.fromOffset(template, template.length()));

        List<Segment> constructedSegments = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            SegmentData segment = segments.get(i);
            Point startPoint = points.get(i);
            Point endPoint = points.get(i + 1);
            PointRange range = new PointRange(startPoint, endPoint);

            constructedSegments.add(switch (segment.kind()) {
                case Literal -> new LiteralSegment(segment.offset(), range, this, segment.value());
                case Reference -> {
                    Origin origin = (stmt == null) ? new Origin() : new Origin(this, Collections.singletonList(new LiteralPart(stmt, range)));
                    RawReference rawReference = new RawReference(segment.value(), origin);
                    yield new ReferenceSegment(segment.offset(), range, this, rawReference);
                }
            });
        }

        return constructedSegments;
    }

    private static final Pattern unescapePattern = Pattern.compile("\\\\([{}\\\\])");
    /**
     * Unescapes the escaping backslash from \{ and \} and \\.
     */
    private String escape(String s) {
        if (s == null || s.indexOf('\\') < 0) return s;
        return unescapePattern.matcher(s).replaceAll("$1");
    }



}