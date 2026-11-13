package burp.dev.rml.service.validator;

import burp.parse.turtleprov.ProvTurtleVisitor;
import burp.reporting.StatementPart;
import burp.reporting.StatementParts;
import burp.vocabularies.RER;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Detects unused or unreferenced elements and statements in the rml: namespace (Rml.NS)
 * by comparing parsed Model statements against statements accessed during AST construction.
 */
@NullMarked
public class UnusedElementValidator implements MappingValidator {

    public static final String ID = "unused-elements";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public ValidationExecutionPolicy getExecutionPolicy() {
        return ValidationExecutionPolicy.IMMEDIATE;
    }

    @Override
    public List<RmlDiagnostic> validate(ValidationContext context) {
        List<RmlDiagnostic> diagnostics = new ArrayList<>();
        if (context.loaded().model() != null) {
            Set<Statement> accessed = context.loaded().loader().getReflectiveLoader().getAccessedStatements();
            validate(context.loaded().model(), accessed, diagnostics);
        }
        return diagnostics;
    }

    public void validate(
            Model model,
            Set<Statement> accessedStatements,
            List<RmlDiagnostic> diagnostics
    ) {
        StmtIterator it = model.listStatements();
        while (it.hasNext()) {
            Statement stmt = it.nextStatement();
            String predUri = stmt.getPredicate().getURI();
            boolean isRmlPredicate = predUri.startsWith(Rml.NS);
            boolean isRmlType = stmt.getPredicate().equals(RDF.type)
                    && stmt.getObject().isURIResource()
                    && stmt.getObject().asResource().getURI() != null
                    && stmt.getObject().asResource().getURI().startsWith(Rml.NS);

            if ((isRmlPredicate || isRmlType) && !accessedStatements.contains(stmt)) {
                var predicatePart = StatementParts.from(stmt, StatementPart.Predicate);
                var ranges = ProvTurtleVisitor.retrieveTurtleLocation(Collections.singletonList(predicatePart));
                if (!ranges.isEmpty()) {
                    var lspRange = ranges.getFirst().toLsp();
                    diagnostics.add(new RmlDiagnostic(
                            lspRange,
                            DiagnosticSeverity.Warning,
                            RER.UnusedRmlElement.getURI(),
                            "Unused RML element (" + predUri + ") on <" + stmt.getSubject() + "> is not referenced by any active TriplesMap.",
                            "RML Loader Accessed Statements"
                    ));
                }
            }
        }


    }
}

