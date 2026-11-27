package burp.model;

import burp.model.context.EvaluationContext;
import burp.model.key.AstKey;
import burp.model.rdf.LiteralTerm;
import burp.parse.TemplateParser;
import burp.parse.ValidationMode;
import burp.parse.turtleprov.OffsetMapper;
import burp.parse.turtleprov.ProvTurtleVisitor;
import burp.reporting.LiteralPart;
import burp.reporting.PointRange;
import burp.reporting.RmlExecutionReport;
import burp.util.Util;
import com.google.common.collect.Lists;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Template extends AbstractPlanNode implements Expression {

    @Override
    public @Nullable Resource getIri() {
        return null;
    }

    @Override
    public void collectAttributes(AttributeCollector sink) {
        sink.put("template", template, emitOrigin());
    }

    private final String template;
    private Statement stmt;
    private List<Segment> segments;

    public Template(String template, @Nullable Statement stmt) {
        this(template, stmt, null, ValidationMode.STRICT);
    }

    public Template(String template) {
        this(template, null, null, ValidationMode.STRICT);
    }

    public Template(
            String template,
            @Nullable Statement stmt,
            @Nullable RmlExecutionReport report,
            @Nullable ValidationMode validationMode
    ) {
        this.template = template;
        this.stmt = stmt;
        this.segments = TemplateParser.parseSegments(
                template,
                this,
                stmt,
                report,
                validationMode != null ? validationMode : ValidationMode.STRICT
        );
    }

    public boolean hasReferenceSegments() {
        if (segments == null) return false;
        return segments.stream().anyMatch(s -> s instanceof ReferenceSegment);
    }

    public String getTemplate() {
        return template;
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
    public AstKey computeAstKey() {
        List<AstKey> childKeys = new ArrayList<>();
        for (PlanNode child : children()) {
            childKeys.add(child.getAstKey());
        }
        return new AstKey.TemplateKey(template != null ? template : "", childKeys);
    }

    @Override
    public List<PlanNode> children() {
        return new ArrayList<>(segments);
    }

    @Override
    public List<PlanNode> dependencies() {
        return new ArrayList<>(segments);
    }

    @Override
    public @NonNull List<PointRange> nodeRanges() {
        if (stmt != null) {
            return ProvTurtleVisitor.retrieveTurtleLocation(Collections.singletonList(new LiteralPart(stmt, new PointRange())));
        }
        return Collections.emptyList();
    }

    @Override
    public List<RmlEvaluationResult> evaluateNode(@Nullable Iteration iter, @NonNull EvaluationContext context) {
        var tplCtx = context.withTemplateSafety(TemplateReferenceSafety.UNSAFE);
        List<RmlEvaluationResult> resList = new ArrayList<>();
        for (String str : values(iter, tplCtx)) {
            resList.add(new RawValue(str, Collections.emptySet(), iter != null ? iter.getLineage() : null));
        }
        return resList;
    }

    public List<String> values(@Nullable Iteration i, EvaluationContext context) {
        if (segments == null || segments.isEmpty()) {
            return Collections.emptyList();
        }
        TemplateReferenceSafety safety = context.templateSafety();
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

    public List<String> values(Iteration i) {
        return values(i, EvaluationContext.createDefault().withTemplateSafety(TemplateReferenceSafety.UNSAFE));
    }

    public List<String> values(Iteration i, boolean safe) {
        return values(i, EvaluationContext.createDefault().withTemplateSafety(safe ? TemplateReferenceSafety.SAFE_IRI : TemplateReferenceSafety.UNSAFE));
    }

    public List<String> values(Iteration i, TemplateReferenceSafety safety) {
        return values(i, EvaluationContext.createDefault().withTemplateSafety(safety));
    }

    public List<String> getReferenceNames() {
        if (segments == null) return Collections.emptyList();
        List<String> list = new ArrayList<>();
        for (Segment segment : segments) {
            if (segment instanceof ReferenceSegment refSegment) {
                list.add(refSegment.reference().getReference());
            }
        }
        return list;
    }


    public sealed interface Segment extends PlanNode permits LiteralSegment, ReferenceSegment {
        int offset();

        PointRange range();

        PlanNode parent();

        @Override
        default @Nullable Resource getIri() {
            return null;
        }

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

        @Override
        public AstKey computeAstKey() {
            return new AstKey.TermMapKey(literal != null ? literal : "", "LiteralSegment", String.valueOf(offset));
        }
    }

    public record ReferenceSegment(
            int offset,
            PointRange range,
            Template parent,
            Reference reference,
            OffsetMapper offsetMapper,
            OffsetMapper innerOffsetMapper
    ) implements Segment {

        @Override
        public Iterable<PlanNode> children() {
            return Collections.singletonList(reference);
        }

        @Override
        public Iterable<PlanNode> dependencies() {
            LocalReferenceScope scope = ancestor(LocalReferenceScope.class);
            if (scope != null && scope.getLogicalSource() != null) {
                return List.of(scope.getLogicalSource());
            }
            return reference != null ? Collections.singletonList(reference) : Collections.emptyList();
        }

        @Override
        public List<PointRange> nodeRanges() {
            if (parent != null && parent.stmt != null) {
                return ProvTurtleVisitor.retrieveTurtleLocation(Collections.singletonList(new LiteralPart(parent.stmt, range)));
            }
            return Collections.emptyList();
        }

        @Override
        public AstKey computeAstKey() {
            List<AstKey> childKeys = reference != null ? List.of(reference.getAstKey()) : List.of();
            return new AstKey.TermMapKey(reference != null ? reference.getReference() : "", "ReferenceSegment", String.valueOf(offset), childKeys);
        }

        @Override
        public List<RmlEvaluationResult> evaluateNode(@Nullable Iteration iter, EvaluationContext context) {
            if (reference == null) return Collections.emptyList();
            List<RmlEvaluationResult> resList = new ArrayList<>();
            for (String str : reference.getStrings(iter)) {
                resList.add(new LiteralTerm(str));
            }
            return resList;
        }
    }

    @Override
    public String toString() {
        return template;
    }
}