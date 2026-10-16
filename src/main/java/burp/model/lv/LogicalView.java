package burp.model.lv;

import burp.model.*;
import burp.parse.ValidationMode;
import burp.parse.loader.annotations.RdfProperty;
import burp.parse.loader.annotations.RdfType;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.reporting.RmlExecutionReport;
import burp.vocabularies.BURP;
import burp.vocabularies.RER;
import burp.vocabularies.Rml;
import burp.model.key.AstKey;
import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;

@RdfType(Rml.LogicalView)
public class LogicalView extends AbstractLogicalSource implements ContainsFields, LocalReferenceScope {

    private List<LogicalIteration> iterations = null;

    @RdfProperty(Rml.viewOn)
    public AbstractLogicalSource logicalSource;

    @RdfProperty(Rml.field)
    public List<Field> fields = new ArrayList<>();

    @RdfProperty(Rml.leftJoin)
    public List<ViewJoin> leftJoins = new ArrayList<>();

    @RdfProperty(Rml.innerJoin)
    public List<ViewJoin> innerJoins = new ArrayList<>();

    public List<ViewJoin> joins = new ArrayList<>();

    @Override
    public Iterable<PlanNode> children() {
        List<PlanNode> list = new ArrayList<>();
        if (logicalSource != null) list.add(logicalSource);
        list.addAll(getExpressionFields());
        list.addAll(getIterableFields());
        list.addAll(getJoins());
        return list;
    }

    @Override
    public Iterable<PlanNode> dependencies() {
        return children();
    }

    private boolean evaluating = false;

    @SuppressWarnings("unchecked")
    @Override
    public Iterable<Iteration> iterator() throws BurpException {
        if (iterations == null) {
            if (evaluating) {
                throw new BurpException(new RmlError(
                        "Cycle detected in logical view hierarchy for " + (subject != null ? subject.toString() : "anonymous view"),
                        emitOrigin(),
                        RER.ExecutionError,
                        this
                ));
            }
            evaluating = true;
            try {
                iterations = new ArrayList<>();

                List<LogicalIteration> viewOnIterations = new ArrayList<>();
                int index = 0;
                for (Iteration iteration : logicalSource.iterator()) {
                    OriginLineage lineage = iteration.getLineage();
                    LogicalIteration li = new LogicalIteration(lineage, logicalSource.nulls);
                    li.put("#", index++);
                    li.put("<it>", iteration);
                    viewOnIterations.add(li);
                }

                iterations = Field.expand(viewOnIterations, getExpressionFields(), getIterableFields());

                for (ViewJoin join : getJoins()) {
                    iterations = join.expand(iterations);
                }
            } finally {
                evaluating = false;
            }
        }

        return (List<Iteration>) (List<?>) iterations;
    }

    public void initialize() {
        joins.clear();
        for (ViewJoin vj : leftJoins) {
            vj.joinType = JoinType.LEFT;
            if (!joins.contains(vj)) joins.add(vj);
        }
        for (ViewJoin vj : innerJoins) {
            vj.joinType = JoinType.INNER;
            if (!joins.contains(vj)) joins.add(vj);
        }

        if (this.logicalSource != null) {
            for (Field f : fields) {
                f.wire(this.logicalSource);
            }
        }

        for (ViewJoin join : joins) {
            join.setParent(this);
            FieldParent joinParent = (join.parentLogicalView != null && join.parentLogicalView.logicalSource != null)
                    ? join.parentLogicalView.logicalSource
                    : join.parentLogicalView;
            for (Field f : join.fields) {
                f.wire(joinParent);
            }
        }
    }

    @Override
    public List<ExpressionField> getExpressionFields() {
        List<ExpressionField> list = new ArrayList<>();
        for (Field f : fields) {
            if (f instanceof ExpressionField ef) {
                list.add(ef);
            }
        }
        return list;
    }

    @Override
    public List<IterableField> getIterableFields() {
        List<IterableField> list = new ArrayList<>();
        for (Field f : fields) {
            if (f instanceof IterableField inf) {
                list.add(inf);
            }
        }
        return list;
    }

    public List<ViewJoin> getJoins() {
        return joins;
    }

    public void addJoin(ViewJoin join) {
        joins.add(join);
    }

    @Override
    public Resource getReferenceFormulation() {
        return BURP.LogicalView;
    }

    @Override
    public Reference buildExportedReference(String reference, Origin origin) {
        return new LogicalReference(reference, origin, this);
    }

    @Override
    public Reference buildLocalReference(String reference, Origin origin) {
        return logicalSource.buildExportedReference(reference, origin);
    }

    @Override
    public @Nullable AbstractLogicalSource getLogicalSource() {
        return logicalSource;
    }

    @Override
    public @NonNull Resource getIri() {
        return (subject);
    }

    private static final ThreadLocal<Set<PlanNode>> AST_KEY_VISITING = ThreadLocal.withInitial(() -> Collections.newSetFromMap(new java.util.IdentityHashMap<>()));

    @Override
    public AstKey computeAstKey() {
        Set<PlanNode> visiting = AST_KEY_VISITING.get();
        if (!visiting.add(this)) {
            return new AstKey.LogicalViewKey(List.of());
        }
        try {
            List<AstKey> childKeys = new ArrayList<>();
            for (PlanNode child : children()) {
                childKeys.add(child.getAstKey());
            }
            return new AstKey.LogicalViewKey(childKeys);
        } finally {
            visiting.remove(this);
        }
    }

    public static void validateLogicalViews(
            Collection<LogicalView> logicalViews,
            @Nullable RmlExecutionReport report,
            ValidationMode validationMode
    ) {
        if (logicalViews == null || logicalViews.isEmpty()) return;

        // 1. Direct self-joins and viewOn self-cycles
        for (LogicalView lv : logicalViews) {
            if (lv.logicalSource == lv) {
                failValidation(
                        "Cycle detected in abstract logical source: " + viewName(lv) + " references itself via rml:viewOn",
                        lv, report, validationMode
                );
            }
            for (ViewJoin join : lv.getJoins()) {
                if (join.parentLogicalView == lv) {
                    failValidation(
                            "Cycle detected: Self-join in logical view " + viewName(lv),
                            join, report, validationMode
                    );
                }
            }
        }

        // 2. Transitive cycle detection across logical views (viewOn and joins)
        Map<LogicalView, Integer> state = new IdentityHashMap<>(); // 0: unvisited, 1: visiting, 2: visited
        List<LogicalView> path = new ArrayList<>();

        for (LogicalView lv : logicalViews) {
            if (state.getOrDefault(lv, 0) == 0) {
                dfs(lv, state, path, report, validationMode);
            }
        }

        // 3. Field nesting cycle detection
        for (LogicalView lv : logicalViews) {
            validateFieldCycles(lv.fields, report, validationMode);
            for (ViewJoin join : lv.getJoins()) {
                validateFieldCycles(join.fields, report, validationMode);
            }
        }
    }

    private static void dfs(
            LogicalView current,
            Map<LogicalView, Integer> state,
            List<LogicalView> path,
            @Nullable RmlExecutionReport report,
            ValidationMode validationMode
    ) {
        state.put(current, 1);
        path.add(current);

        for (LogicalView dep : getDependencies(current)) {
            int depState = state.getOrDefault(dep, 0);
            if (depState == 1) {
                int cycleStart = path.indexOf(dep);
                String cycleChain = path.subList(cycleStart, path.size()).stream()
                        .map(LogicalView::viewName)
                        .reduce((a, b) -> a + " -> " + b)
                        .orElse(viewName(dep)) + " -> " + viewName(dep);
                failValidation(
                        "Cycle detected in logical view dependencies: " + cycleChain,
                        current, report, validationMode
                );
            } else if (depState == 0) {
                dfs(dep, state, path, report, validationMode);
            }
        }

        path.removeLast();
        state.put(current, 2);
    }

    private static List<LogicalView> getDependencies(LogicalView lv) {
        List<LogicalView> deps = new ArrayList<>();
        if (lv.logicalSource instanceof LogicalView parentView) {
            deps.add(parentView);
        }
        for (ViewJoin join : lv.getJoins()) {
            if (join.parentLogicalView != null) {
                deps.add(join.parentLogicalView);
            }
        }
        return deps;
    }

    private static void validateFieldCycles(
            List<Field> fields,
            @Nullable RmlExecutionReport report,
            ValidationMode validationMode
    ) {
        if (fields == null || fields.isEmpty()) return;
        Map<Field, Integer> state = new IdentityHashMap<>();
        List<Field> path = new ArrayList<>();
        for (Field f : fields) {
            if (state.getOrDefault(f, 0) == 0) {
                dfsField(f, state, path, report, validationMode);
            }
        }
    }

    private static void dfsField(
            Field current,
            Map<Field, Integer> state,
            List<Field> path,
            @Nullable RmlExecutionReport report,
            ValidationMode validationMode
    ) {
        state.put(current, 1);
        path.add(current);

        for (Field child : current.fields) {
            int childState = state.getOrDefault(child, 0);
            if (childState == 1) {
                failValidation(
                        "Cycle detected in nested fields: field '" + current.fieldName + "'",
                        current, report, validationMode
                );
            } else if (childState == 0) {
                dfsField(child, state, path, report, validationMode);
            }
        }

        path.removeLast();
        state.put(current, 2);
    }

    private static String viewName(LogicalView lv) {
        if (lv.subject != null) {
            return lv.subject.isURIResource() ? lv.subject.getURI() : lv.subject.toString();
        }
        return lv.getId();
    }

    private static void failValidation(
            String message,
            PlanNode node,
            @Nullable RmlExecutionReport report,
            ValidationMode validationMode
    ) {
        RmlError err = new RmlError(message, new Origin(node.getSourcePointers()), RER.MappingError, node);
        if (report != null && !report.getErrors().contains(err)) {
            report.getErrors().add(err);
        }
        if (validationMode == ValidationMode.STRICT) {
            throw new BurpException(err);
        }
    }
}


class LogicalReference extends Reference {
    private final LogicalView logicalView;

    public LogicalReference(String reference, Origin origin, LogicalView logicalView) {
        super(reference, origin);
        this.logicalView = logicalView;
    }

    @Override
    public List<Object> getValues(Iteration i) {
        if (!(i instanceof LogicalIteration li)) {
            throw new IllegalArgumentException("LogicalReference " + reference + " can only be used with LogicalIteration.");
        }

        if (!li.map.containsKey(reference)) {
            throw new BurpException(new RmlError(
                    "Attribute " + reference + " does not exist in " + logicalView.subject + ".",
                    emitOrigin("reference"),
                    RER.ReferenceFormulationExecutionError,
                    null,
                    Collections.singletonMap(RER.reference, reference),
                    this
            ));
        }

        Object o = li.map.get(reference);

        if (o instanceof Iteration) {
            throw new BurpException(new RmlError(
                    "Attribute " + reference + " refers to a record key.",
                    emitOrigin("reference"),
                    RER.ReferenceFormulationExecutionError,
                    null,
                    Collections.singletonMap(RER.reference, reference),
                    this
            ));
        }

        if (li.nulls != null && li.nulls.contains(o)) {
            return Collections.emptyList();
        }
        return Collections.singletonList(o);
    }
}
