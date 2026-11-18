package burp.dev.rml.service.validator;

import burp.dev.rml.service.ontology.OntologyConformanceChecker;
import burp.dev.rml.service.ontology.OntologyRegistry;
import burp.model.*;
import burp.model.rdf.IRITerm;
import burp.model.rdf.LiteralTerm;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.jspecify.annotations.NullMarked;

import java.util.*;

/// Validates constant predicates, static term types, and datatypes against target ontologies.
@NullMarked
public class OntologyValidator implements MappingValidator {

    public static final String ID = "ontology-constants";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getSourceName() {
        return "BURP Ontology Validator";
    }

    @Override
    public ValidationExecutionPolicy getExecutionPolicy() {
        return ValidationExecutionPolicy.IMMEDIATE;
    }

    @Override
    public List<RmlError> validate(ValidationContext context) {
        List<RmlError> errors = new ArrayList<>();
        Map<String, String> prefixMap = new HashMap<>(context.loaded().document().prefixMap);
        if (context.loaded().model() != null) {
            prefixMap.putAll(context.loaded().model().getNsPrefixMap());
        }
        var ontRegistry = context.workspace().getOntologyRegistry(context.loaded().model(), context.mappingFileUri());
        validate(context.loaded().document().triplesMaps, ontRegistry, prefixMap, errors);
        return errors;
    }

    public void validate(
            List<TriplesMap> triplesMaps,
            OntologyRegistry registry,
            Map<String, String> prefixMap,
            List<RmlError> errors
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
                        Origin pmOrigin = MappingValidator.resolveNodeOrigin(pm, "constant");

                        if (pom.objectMaps.isEmpty()) {
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
                                errors.add(new RmlError(issue.message(), pmOrigin, issue.errorType()));
                            }
                        } else {
                            for (BaseObjectMap bom : pom.objectMaps) {
                                Resource termType = null;
                                Resource declaredDatatype = null;
                                String lexical = null;
                                Resource litDatatype = null;
                                Origin datatypeOrigin = null;
                                Origin termTypeOrigin = null;

                                if (bom instanceof TermMap tmMap) {
                                    termType = tmMap.termType != null ? tmMap.termType : tmMap.getDefaultTermType();
                                    if (tmMap.originOf("termType") != null) {
                                        termTypeOrigin = MappingValidator.resolveNodeOrigin(tmMap, "termType");
                                    }
                                    if (tmMap.datatypeMap != null && tmMap.datatypeMap.expression instanceof RDFNodeConstant dc && dc.constant instanceof IRITerm dIri) {
                                        declaredDatatype = ResourceFactory.createResource(dIri.uri());
                                        datatypeOrigin = MappingValidator.resolveNodeOrigin(tmMap.datatypeMap, "constant");
                                    }
                                    if (tmMap.expression instanceof RDFNodeConstant oc && oc.constant instanceof LiteralTerm lit) {
                                        lexical = lit.value();
                                        if (lit.datatype() != null) {
                                            litDatatype = ResourceFactory.createResource(lit.datatype().uri());
                                        }
                                    }
                                } else if (bom instanceof ReferencingObjectMap) {
                                    termType = RML.IRI;
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
                                if (issues.isEmpty()) continue;

                                Origin bomOrigin = MappingValidator.resolveNodeOrigin(bom, "constant");
                                for (var issue : issues) {
                                    Origin issueOrigin = bomOrigin != null ? bomOrigin : pmOrigin;
                                    if (issue.errorType().equals(RER.OntologyDomainMismatch)) {
                                        issueOrigin = pmOrigin;
                                    } else if (issue.errorType().equals(RER.OntologyDatatypeMismatch) && datatypeOrigin != null) {
                                        issueOrigin = datatypeOrigin;
                                    } else if (issue.errorType().equals(RER.OntologyTermTypeMismatch) && termTypeOrigin != null) {
                                        issueOrigin = termTypeOrigin;
                                    }
                                    errors.add(new RmlError(issue.message(), issueOrigin, issue.errorType()));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
