package burp.dev.rml.service.validator;

import burp.dev.rml.service.ontology.OntologyConformanceChecker;
import burp.dev.rml.service.ontology.OntologyRegistry;
import burp.dev.rml.service.plan.PlanNodeDescriptor;
import burp.model.*;
import burp.model.rdf.IRITerm;
import burp.model.rdf.LiteralTerm;
import burp.parse.turtleprov.ProvTurtleVisitor;
import burp.reporting.Origin;
import burp.reporting.PointRange;
import burp.reporting.StatementPart;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Validates constant predicates, static term types, and datatypes against target ontologies.
@NullMarked
public class OntologyValidator {

    public void validate(
            List<TriplesMap> triplesMaps,
            OntologyRegistry registry,
            Map<String, String> prefixMap,
            List<RmlDiagnostic> diagnostics
    ) {
        for (TriplesMap tm : triplesMaps) {
            if (tm.subjectMap == null || tm.subjectMap.classes.isEmpty()) {
                continue;
            }

            Set<Resource> subjectClasses = new LinkedHashSet<>(tm.subjectMap.classes);

            for (PredicateObjectMap pom : tm.predicateObjectMaps) {
                for (PredicateMap pm : pom.predicateMaps) {
                    if (pm.expression instanceof RDFNodeConstant c && c.constant instanceof IRITerm iri) {
                        Resource predicateRes = ResourceFactory.createResource(iri.uri());
                        Range pmRange = resolveNodeRange(pm, "constant");

                        if (pom.objectMaps.isEmpty()) {
                            // Check predicate domain even without objects
                            List<OntologyConformanceChecker.Issue> issues = OntologyConformanceChecker.check(
                                    subjectClasses,
                                    predicateRes,
                                    null,
                                    null,
                                    null,
                                    null,
                                    registry,
                                    prefixMap
                            );
                            for (var issue : issues) {
                                diagnostics.add(new RmlDiagnostic(
                                        pmRange,
                                        DiagnosticSeverity.Warning,
                                        issue.errorType().getURI(),
                                        issue.message(),
                                        "BURP Ontology Validator"
                                ));
                            }
                        } else {
                            for (BaseObjectMap bom : pom.objectMaps) {
                                Range bomRange = resolveNodeRange(bom, "constant");
                                Resource termType = null;
                                Resource declaredDatatype = null;
                                String lexical = null;
                                Resource litDatatype = null;

                                if (bom instanceof TermMap tmMap) {
                                    termType = tmMap.termType != null ? tmMap.termType : tmMap.getDefaultTermType();
                                    if (tmMap.datatypeMap != null && tmMap.datatypeMap.expression instanceof RDFNodeConstant dc && dc.constant instanceof IRITerm dIri) {
                                        declaredDatatype = ResourceFactory.createResource(dIri.uri());
                                    }
                                    if (tmMap.expression instanceof RDFNodeConstant oc && oc.constant instanceof LiteralTerm lit) {
                                        lexical = lit.value();
                                        if (lit.datatype() != null) {
                                            litDatatype = ResourceFactory.createResource(lit.datatype().uri());
                                        }
                                    }
                                } else if (bom instanceof ReferencingObjectMap) {
                                    termType = burp.vocabularies.RML.IRI;
                                }

                                List<OntologyConformanceChecker.Issue> issues = OntologyConformanceChecker.check(
                                        subjectClasses,
                                        predicateRes,
                                        termType,
                                        declaredDatatype,
                                        lexical,
                                        litDatatype,
                                        registry,
                                        prefixMap
                                );

                                for (var issue : issues) {
                                    Range issueRange = (issue.errorType().equals(burp.vocabularies.RER.OntologyDomainMismatch))
                                            ? pmRange
                                            : (bomRange != null ? bomRange : pmRange);

                                    diagnostics.add(new RmlDiagnostic(
                                            issueRange,
                                            DiagnosticSeverity.Warning,
                                            issue.errorType().getURI(),
                                            issue.message(),
                                            "BURP Ontology Validator"
                                    ));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private @Nullable Range resolveNodeRange(PlanNode node, String propertyName) {
        if (node instanceof AbstractPlanNode apn) {
            Origin origin = apn.emitOrigin(propertyName, StatementPart.Object);
            if (origin.sourceStatements() != null) {
                List<PointRange> ranges = ProvTurtleVisitor.retrieveTurtleLocation(origin.sourceStatements());
                if (!ranges.isEmpty()) {
                    return ranges.getFirst().toLsp();
                }
            }
        }
        return PlanNodeDescriptor.getFirstRangeForNode(node);
    }
}
