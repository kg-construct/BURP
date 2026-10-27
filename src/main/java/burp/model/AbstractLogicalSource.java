package burp.model;


import burp.model.context.EvaluationContext;
import burp.model.key.AstKey;
import burp.model.lv.FieldParent;
import burp.parse.loader.annotations.RdfId;
import burp.parse.loader.annotations.RdfProperty;
import burp.reporting.BurpException;
import burp.reporting.Origin;
import burp.reporting.RDFPointer;
import burp.reporting.StatementParts;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.Resource;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;

public abstract class AbstractLogicalSource extends AbstractPlanNode implements FormulationIterable, FieldParent, ExportedReferenceScope, LogicalTargetScope {

    @RdfId
    public Resource subject;

    @Override
    public @NonNull Resource getIri() {
        return subject;
    }

    @Override
    public void collectAttributes(AttributeCollector sink) {
        Resource rf = getReferenceFormulation();
        if (rf != null) {
            Origin orig = originOf("referenceFormulation");
            sink.put("referenceFormulation", rf.getLocalName(), orig);
        }
    }

    public Set<Object> nulls = new HashSet<>();

    public Set<Object> getNulls() {
        return nulls;
    }

    @RdfProperty(Rml.logicalTarget)
    private final Set<LogicalTarget> logicalTargets = new HashSet<>();

    @Override
    public Set<LogicalTarget> getLogicalTargets() {
        return logicalTargets;
    }

    public String getSourceIdentifier() {
        if (subject != null && !subject.isAnon() && subject.getURI() != null) {
            return subject.getURI();
        }
        if (this instanceof burp.ls.FileBasedLogicalSource fbls && fbls.file != null) {
            if (fbls.file instanceof burp.ls.SourceFile.Local local) {
                return new java.io.File(local.getPath()).getName();
            } else if (fbls.file instanceof burp.ls.SourceFile.Remote remote) {
                return new java.io.File(remote.getUrl()).getName();
            }
        }
        return PlanNodeIdRegistry.getId(this);
    }

    public abstract Iterable<Iteration> iterator() throws BurpException;

    @Override
    public abstract @Nullable Resource getReferenceFormulation();

    @Override
    public String getAbsoluteFieldName() {
        return "<it>";
    }

    @Override
    public AstKey computeAstKey() {
        String srcFile = "";
        String iterExpr = null;
        if (this instanceof burp.ls.FileBasedLogicalSource fbls && fbls.file != null) {
            if (fbls.file instanceof burp.ls.SourceFile.Local local) {
                srcFile = local.getPath();
            } else if (fbls.file instanceof burp.ls.SourceFile.Remote remote) {
                srcFile = remote.getUrl();
            } else {
                srcFile = fbls.file.toString();
            }
        }
        if (this instanceof burp.ls.JSONSourceRFC js) {
            iterExpr = js.iterator;
        }
        return new AstKey.LogicalSourceKey(srcFile, iterExpr);
    }

    public List<Object> extractValues(Iteration iteration, String reference) {
        Reference ref = buildExportedReference(reference, null);
        if (ref != null) {
            return ref.getValues(iteration);
        }
        return Collections.emptyList();
    }

    @Override
    public List<RmlEvaluationResult> evaluateNode(@Nullable Iteration iter, EvaluationContext context) {
        if (iter == null) {
            return Collections.emptyList();
        }
        return List.of(new RawValue(iter.asString()));
    }

    // TODO: Needs fixing this hack with OriginOf while loading when rationalising LogicalSource and Source loading.
    @Override
    public List<RDFPointer> getSourcePointers() {
        List<RDFPointer> pointers = new ArrayList<>();
        if (subject != null) {
            var statements = subject.listProperties().toList();
            for (var stmt : statements) {
                pointers.add(new StatementParts(stmt, true, true, true));
                if (stmt.getObject().isResource() && stmt.getObject().asResource().isAnon()) {
                    for (var nested : stmt.getObject().asResource().listProperties().toList()) {
                        pointers.add(new StatementParts(nested, true, true, true));
                    }
                }
            }
        }
        return pointers;
    }
}