package burp.dev.rml.service.ontology;

import burp.dev.rml.service.RmlEvaluationResultFormatter;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.ext.xerces.xs.XSTypeDefinition;
import org.apache.jena.ontapi.model.OntClass;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/// Stateless ontology conformance checker shared identically by static and dynamic analysis.
@NullMarked
public class OntologyConformanceChecker {

    public record Issue(
            OntClass errorType,
            String message
    ) {}

    public static List<Issue> check(
            Set<Resource> subjectClasses,
            Resource predicate,
            @Nullable Resource termType,
            @Nullable Resource declaredDatatype,
            @Nullable String literalLexicalValue,
            @Nullable Resource literalDatatype,
            OntologyRegistry registry,
            Map<String, String> prefixMap
    ) {
        List<Issue> issues = new ArrayList<>();
        PropertyConstraint constraint = registry.getPropertyConstraint(subjectClasses, predicate);

        if (!constraint.isKnownProperty()) {
            return issues;
        }

        String predCompact = RmlEvaluationResultFormatter.compactIri(predicate.getURI(), prefixMap);

        // 1. Domain Check
        if (!constraint.isDomainCompatible()) {
            String domains = constraint.declaredDomains().stream()
                    .map(d -> RmlEvaluationResultFormatter.compactIri(d.getURI(), prefixMap))
                    .collect(Collectors.joining(", "));
            String classes = subjectClasses.stream()
                    .map(c -> RmlEvaluationResultFormatter.compactIri(c.getURI(), prefixMap))
                    .collect(Collectors.joining(", "));

            issues.add(new Issue(
                    RER.OntologyDomainMismatch,
                    "Predicate " + predCompact + " domain is " + domains + "; not declared for subject class(es) " + classes + "."
            ));
        }

        // 2. Term Type Check
        if (constraint.isObjectProperty()) {
            if (termType != null && termType.equals(RML.Literal)) {
                issues.add(new Issue(
                        RER.OntologyTermTypeMismatch,
                        "Term type rml:Literal is incompatible with object property " + predCompact + " (expected IRI or BlankNode)."
                ));
            } else if (literalLexicalValue != null) {
                issues.add(new Issue(
                        RER.OntologyTermTypeMismatch,
                        "Generated literal is incompatible with object property " + predCompact + " (expected IRI or BlankNode)."
                ));
            }
        } else if (constraint.isDatatypeProperty()) {
            if (termType != null && (termType.equals(RML.IRI) || termType.equals(RML.URI) || termType.equals(RML.BlankNode))) {
                String termTypeCompact = RmlEvaluationResultFormatter.compactIri(termType.getURI(), prefixMap);
                issues.add(new Issue(
                        RER.OntologyTermTypeMismatch,
                        "Term type " + termTypeCompact + " is incompatible with datatype property " + predCompact + " (expected Literal)."
                ));
            }
        }

        // 3. Datatype Check
        Resource expectedRange = constraint.expectedRange();
        if (expectedRange != null) {
            String rangeCompact = RmlEvaluationResultFormatter.compactIri(expectedRange.getURI(), prefixMap);

            if (declaredDatatype != null && !isDatatypeCompatible(declaredDatatype, expectedRange, registry.getOntModel())) {
                String declaredCompact = RmlEvaluationResultFormatter.compactIri(declaredDatatype.getURI(), prefixMap);
                issues.add(new Issue(
                        RER.OntologyDatatypeMismatch,
                        "Declared datatype " + declaredCompact + " is incompatible with expected range " + rangeCompact + " for predicate " + predCompact + "."
                ));
            }

            if (literalDatatype != null && !isDatatypeCompatible(literalDatatype, expectedRange, registry.getOntModel())) {
                String litDatatypeCompact = RmlEvaluationResultFormatter.compactIri(literalDatatype.getURI(), prefixMap);
                issues.add(new Issue(
                        RER.OntologyDatatypeMismatch,
                        "Generated literal datatype " + litDatatypeCompact + " is incompatible with expected range " + rangeCompact + " for predicate " + predCompact + "."
                ));
            }

            // 4. Lexical Value Conformance
            if (literalLexicalValue != null) {
                if (!isLexicalValueValid(literalLexicalValue, expectedRange)) {
                    issues.add(new Issue(
                            RER.OntologyDatatypeMismatch,
                            "Lexical value '" + literalLexicalValue + "' is not valid for expected range " + rangeCompact + " for predicate " + predCompact + "."
                    ));
                } else if (literalDatatype != null && !isLexicalValueValid(literalLexicalValue, literalDatatype)) {
                    String dtCompact = RmlEvaluationResultFormatter.compactIri(literalDatatype.getURI(), prefixMap);
                    issues.add(new Issue(
                            RER.OntologyDatatypeMismatch,
                            "Lexical value '" + literalLexicalValue + "' is not valid for declared datatype " + dtCompact + "."
                    ));
                }
            }
        }

        return issues;
    }

    private static boolean isDatatypeCompatible(Resource actual, Resource expected, OntModel ontModel) {
        return isDatatypeCompatible(actual, expected, ontModel, new HashSet<>());
    }

    private static boolean isDatatypeCompatible(Resource actual, Resource expected, OntModel ontModel, Set<Resource> visited) {
        if (actual.equals(expected)) return true;
        if (RDFS.Literal.equals(expected)) return true;
        if (!visited.add(actual)) return false;

        // Check Xerces XSD Jena built-in hierarchy
        TypeMapper tm = TypeMapper.getInstance();
        RDFDatatype actualDt = tm.getTypeByName(actual.getURI());
        RDFDatatype expectedDt = tm.getTypeByName(expected.getURI());

        if (actualDt instanceof XSDDatatype actualXsd && expectedDt instanceof XSDDatatype expectedXsd) {
            Object actualDef = actualXsd.extendedTypeDefinition();
            Object expectedDef = expectedXsd.extendedTypeDefinition();
            if (actualDef instanceof XSTypeDefinition actualXtd && expectedDef instanceof XSTypeDefinition expectedXtd) {
                XSTypeDefinition cur = actualXtd.getBaseType();
                while (cur != null) {
                    if (cur.equals(expectedXtd) ||
                            (Objects.equals(cur.getName(), expectedXtd.getName()) && Objects.equals(cur.getNamespace(), expectedXtd.getNamespace()))) {
                        return true;
                    }
                    cur = cur.getBaseType();
                }
            }
        }

        // Check ontology datatype hierarchy via rdfs:subClassOf
        if (ontModel.contains(actual, RDFS.subClassOf, expected)) {
            return true;
        }

        var stmtIter = ontModel.listStatements(actual, RDFS.subClassOf, (RDFNode) null);
        while (stmtIter.hasNext()) {
            var stmt = stmtIter.nextStatement();
            if (stmt.getObject().isResource() && !stmt.getObject().equals(actual)) {
                if (isDatatypeCompatible(stmt.getObject().asResource(), expected, ontModel, visited)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isLexicalValueValid(String lexicalValue, Resource datatype) {
        RDFDatatype jenaType = TypeMapper.getInstance().getTypeByName(datatype.getURI());
        if (jenaType == null) return true;
        return jenaType.isValid(lexicalValue);
    }
}
