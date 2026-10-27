package burp.model;

import burp.model.key.AstKey;
import burp.parse.turtleprov.ProvTurtleVisitor;
import burp.reporting.Origin;
import burp.reporting.PointRange;
import burp.reporting.RDFPointer;
import burp.reporting.RmlError;
import org.apache.jena.ontology.OntClass;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import burp.reporting.StatementPart;
import burp.reporting.StatementParts;
import burp.reporting.LiteralPart;

import java.nio.file.Path;
import java.util.*;

/// Common abstract base class for AST execution plan nodes in BURP.
///
/// Centralizes node parentage management, map-backed source provenance tracking,
/// and lazy O(1) Merkle DAG AST key memoization.
@NullMarked
public abstract class AbstractPlanNode implements PlanNode {
    protected @Nullable PlanNode parent = null;
    protected @Nullable AstKey cachedAstKey = null;

    protected final Map<String, Origin> propertyOrigins = new LinkedHashMap<>();
    protected final List<Origin> extraOrigins = new ArrayList<>();

    public @Nullable Origin originOf(String propertyName) {
        return propertyOrigins.get(propertyName);
    }

    public Origin emitOrigin() {
        return new Origin(getSourcePointers());
    }

    public Origin emitOrigin(String propertyName) {
        Origin o = originOf(propertyName);
        if (o == null) return emitOrigin();
        return o;
    }

    public Origin emitOrigin(String propertyName, StatementPart part) {
        Origin o = originOf(propertyName);
        if (o == null) return emitOrigin();

        if (o.sourceStatements() == null) return o;

        List<RDFPointer> filtered = o.sourceStatements().stream().map(p -> {
            if (p instanceof StatementParts sp) {
                if (part == StatementPart.Object) {
                    LiteralPart lp = sp.toLiteralPart();
                    if (lp != null) return lp;
                    return new StatementParts(sp.stmt(), false, false, true);
                } else if (part == StatementPart.Subject) {
                    return new StatementParts(sp.stmt(), true, false, false);
                } else if (part == StatementPart.Predicate) {
                    return new StatementParts(sp.stmt(), false, true, false);
                }
            }
            return p;
        }).toList();

        return new Origin(filtered);
    }

    public RmlError error(String message, String propertyName, OntClass errorType) {
        return new RmlError(message, emitOrigin(propertyName), errorType, this);
    }

    public RmlError error(String message, OntClass errorType) {
        return new RmlError(message, emitOrigin(), errorType, this);
    }

    public void putOrigin(String propertyName, Origin origin) {
        Origin existing = propertyOrigins.get(propertyName);
        if (existing == null) {
            propertyOrigins.put(propertyName, origin);
        } else {
            LinkedHashSet<RDFPointer> combined = new LinkedHashSet<>();
            if (existing.sourceStatements() != null) combined.addAll(existing.sourceStatements());
            if (origin.sourceStatements() != null) combined.addAll(origin.sourceStatements());
            propertyOrigins.put(
                    propertyName,
                    new Origin(new ArrayList<>(combined))
            );
        }
    }

    public void addExtraOrigin(Origin origin) {
        if (!extraOrigins.contains(origin)) {
            extraOrigins.add(origin);
        }
    }

    public Collection<Origin> getAllOrigins() {
        if (extraOrigins.isEmpty()) return propertyOrigins.values();
        List<Origin> all = new ArrayList<>(propertyOrigins.size() + extraOrigins.size());
        all.addAll(propertyOrigins.values());
        all.addAll(extraOrigins);
        return all;
    }

    @Override
    public List<RDFPointer> getSourcePointers() {
        return getAllOrigins().stream()
                .map(Origin::sourceStatements)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .distinct()
                .map(p -> (RDFPointer) p)
                .toList();
    }

    @Override
    public List<PointRange> nodeRanges() {
        return ProvTurtleVisitor.retrieveTurtleLocation(getSourcePointers());
    }

    public @Nullable Path getSourceFile() {
        for (Origin o : propertyOrigins.values()) {
            if (o.file() != null) return o.file();
        }
        for (Origin o : extraOrigins) {
            if (o.file() != null) return o.file();
        }
        if (parent instanceof AbstractPlanNode apn) return apn.getSourceFile();
        return null;
    }

    @Override
    public @Nullable PlanNode getParent() {
        return parent;
    }

    @Override
    public void setParent(@Nullable PlanNode parent) {
        this.parent = parent;
    }

    @Override
    public AstKey getAstKey() {
        if (cachedAstKey == null) {
            cachedAstKey = computeAstKey();
        }
        return cachedAstKey;
    }
}
