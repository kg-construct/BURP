package burp.dev.rml.service.validator;

import burp.dev.rml.service.ontology.OntologyConformanceChecker;
import burp.dev.rml.service.ontology.OntologyRegistry;
import burp.model.RdfPredicateObject;
import burp.model.RmlEvaluationResult;
import burp.model.TriplesMap;
import burp.model.rdf.IRITerm;
import burp.model.rdf.LiteralTerm;
import burp.model.rdf.Term;
import burp.reporting.RmlError;
import burp.vocabularies.RML;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;

/// Performs sampled dynamic ontology checks on evaluated statements.
@NullMarked
public class RuntimeOntologyValidator {

    public List<RmlError> validateResults(
            TriplesMap tm,
            List<? extends RmlEvaluationResult> results,
            OntologyRegistry registry,
            Map<String, String> prefixMap,
            @Nullable Integer sampleLimit
    ) {
        if (results.isEmpty()) return Collections.emptyList();

        Set<Resource> subjectClasses = new LinkedHashSet<>();
        if (tm.subjectMap != null) {
            subjectClasses.addAll(tm.subjectMap.classes);
        }

        List<RmlError> errors = new ArrayList<>();
        String sampleNote = sampleLimit != null ? " [sampled evaluation, limit " + sampleLimit + "]" : " [evaluated]";

        for (RmlEvaluationResult res : results) {
            IRITerm pred;
            Term obj;

            if (res instanceof burp.model.RdfStatement stmt) {
                pred = stmt.getPredicate();
                obj = stmt.getObject();
            } else if (res instanceof RdfPredicateObject po) {
                pred = po.predicate();
                obj = po.object();
            } else {
                continue;
            }

            Resource predicate = ResourceFactory.createResource(pred.uri());

            Resource termType;
            String lexical = null;
            Resource litDatatype = null;

            if (obj instanceof LiteralTerm lit) {
                termType = RML.LITERAL;
                lexical = lit.value();
                if (lit.datatype() != null) {
                    litDatatype = ResourceFactory.createResource(lit.datatype().uri());
                }
            } else if (obj instanceof IRITerm) {
                termType = RML.IRI;
            } else {
                termType = RML.BLANKNODE;
            }

            List<OntologyConformanceChecker.Issue> issues = OntologyConformanceChecker.check(
                    subjectClasses,
                    predicate,
                    termType,
                    null,
                    lexical,
                    litDatatype,
                    registry,
                    prefixMap
            );

            for (var issue : issues) {
                OntClass errorClass = switch (issue.errorType().getURI()) {
                    case "https://w3id.org/dre/rer#OntologyDomainMismatch" -> burp.vocabularies.RER.OntologyDomainMismatch;
                    case "https://w3id.org/dre/rer#OntologyTermTypeMismatch" -> burp.vocabularies.RER.OntologyTermTypeMismatch;
                    case "https://w3id.org/dre/rer#OntologyDatatypeMismatch" -> burp.vocabularies.RER.OntologyDatatypeMismatch;
                    default -> burp.vocabularies.RER.OntologyWarning;
                };
                errors.add(new RmlError(
                        issue.message() + sampleNote,
                        null,
                        errorClass
                ));
            }
        }

        return errors;
    }
}

