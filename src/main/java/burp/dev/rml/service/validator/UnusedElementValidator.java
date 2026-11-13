package burp.dev.rml.service.validator;

import burp.parse.turtleprov.ProvTurtleVisitor;
import burp.reporting.Origin;
import burp.reporting.RmlError;
import burp.reporting.StatementPart;
import burp.reporting.StatementParts;
import burp.vocabularies.RER;
import burp.vocabularies.Rml;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.vocabulary.RDF;
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
    public String getSourceName() {
        return "RML Loader Accessed Statements";
    }

    @Override
    public ValidationExecutionPolicy getExecutionPolicy() {
        return ValidationExecutionPolicy.IMMEDIATE;
    }

    @Override
    public List<RmlError> validate(ValidationContext context) {
        List<RmlError> errors = new ArrayList<>();
        if (context.loaded().model() != null) {
            Set<Statement> accessed = context.loaded().loader().getReflectiveLoader().getAccessedStatements();
            validate(context.loaded().model(), accessed, errors);
        }
        return errors;
    }

    public void validate(
            Model model,
            Set<Statement> accessedStatements,
            List<RmlError> errors
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
                var pointers = Collections.singletonList(predicatePart);
                if (!ProvTurtleVisitor.retrieveTurtleLocation(pointers).isEmpty()) {
                    errors.add(new RmlError(
                            "Unused RML element (" + predUri + ") on <" + stmt.getSubject() + "> is not referenced by any active TriplesMap.",
                            new Origin(pointers),
                            RER.UnusedRmlElement
                    ));
                }
            }
        }
    }
}
