package burp.parse;

import burp.reporting.*;
import burp.vocabularies.RER;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDFS;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.topbraid.shacl.validation.*;
import org.topbraid.shacl.vocabulary.SH;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates RML mapping models against W3C RML SHACL shapes
 * using TopBraid SHACL, unpacks sh:or branch constraints for detailed diagnostics,
 * and extracts statement provenance for validation report violations.
 */
@NullMarked
public class RmlShaclValidator {

    public static boolean validate(Model mapping, @Nullable RmlExecutionReport report) {
        return validate(mapping, report, null);
    }

    public static boolean validate(Model mapping, @Nullable RmlExecutionReport report, @Nullable Path mappingPath) {
        try {
            Model shapesModel = ModelFactory.createDefaultModel();
            try (InputStream combinedStream = RmlShaclValidator.class.getResourceAsStream("/shapes/core+cc+fnml+io+io-registry+lv.ttl")) {
                if (combinedStream != null) {
                    shapesModel.read(combinedStream, "urn:dummy", "TURTLE");
                }
            }

            ValidationEngineConfiguration configuration = new ValidationEngineConfiguration().setReportDetails(true);
            ValidationEngine engine = ValidationUtil.createValidationEngine(mapping, shapesModel, configuration);
            engine.applyEntailments();
            engine.updateConforms();
            engine.validateAll();
            ValidationReport shaclReport = engine.getValidationReport();

            if (!shaclReport.conforms()) {
                if (report != null) {
                    List<ValidationResult> allViolations = new ArrayList<>();
                    for (ValidationResult vr : shaclReport.results()) {
                        for (ValidationResult leaf : collectLeafViolations(vr)) {
                            if (!isIgnoredViolation(leaf)) {
                                allViolations.add(leaf);
                            }
                        }
                    }

                    if (allViolations.isEmpty()) {
                        return true;
                    }

                    for (ValidationResult vr : allViolations) {
                        RDFNode focusNode = vr.getFocusNode();
                        Resource pathRes = vr.getPath();
                        RDFNode valueNode = vr.getValue();

                        List<StatementParts> focusOrigins = extractStatements(focusNode, pathRes, valueNode, mapping);

                        StringBuilder msg = new StringBuilder();
                        String baseMessage = cleanMessage(vr.getMessage());
                        if (!baseMessage.isBlank()) {
                            msg.append(baseMessage);
                        } else {
                            msg.append("Constraint violation on ").append(focusNode);
                            if (pathRes != null) {
                                msg.append(" (path: ").append(pathRes).append(")");
                            }
                        }

                        // Unpack sh:or branches for actionable diagnostics
                        if (SH.OrConstraintComponent.equals(vr.getSourceConstraintComponent())) {
                            unpackOrConstraint(vr, mapping, shapesModel, configuration, msg);
                        }

                        Origin origin = !focusOrigins.isEmpty() ? new Origin(focusOrigins) : null;
                        OntClass errorType = extractRerError(vr, shapesModel);
                        report.getErrors().add(
                                new RmlError(
                                        msg.toString(),
                                        origin,
                                        errorType
                                )
                        );
                    }
                }
                return false;
            }
            return true;
        } catch (Exception e) {
            if (report != null) {
                report.getErrors().add(new RmlError("SHACL validation failed with exception: " + e.getMessage(), null, RER.MappingError, e));
            }
            return false;
        }
    }

    private static List<ValidationResult> collectLeafViolations(ValidationResult vr) {
        if (vr instanceof ResourceValidationResult rvr) {
            Resource res = rvr.getResource();
            List<Resource> details = res.listProperties(SH.detail).mapWith(Statement::getResource).toList();
            if (!details.isEmpty()) {
                List<ValidationResult> results = new ArrayList<>();
                for (Resource detail : details) {
                    results.addAll(collectLeafViolations(new ResourceValidationResult(detail)));
                }
                return results;
            }
        }
        return List.of(vr);
    }

    private static boolean isIgnoredViolation(ValidationResult vr) {
        // Pattern rule on template: ignore at SHACL stage because template parsing system
        // generates more precise errors on the segment level
        if (SH.PatternConstraintComponent.equals(vr.getSourceConstraintComponent())) {
            Resource path = vr.getPath();
            if (path != null && path.isURIResource() && path.getURI().equals(burp.vocabularies.RML.template.getURI())) {
                return true;
            }
        }
        return false;
    }

    private static OntClass extractRerError(ValidationResult vr, Model shapesModel) {
        Property rerError = shapesModel.createProperty(RER.NS, "error");

        // 1. Check source shape directly in shapesModel
        Resource shape = vr.getSourceShape() != null ? vr.getSourceShape().inModel(shapesModel) : null;
        if (shape != null) {
            Statement stmt = shape.getProperty(rerError);
            if (stmt != null && stmt.getObject().isURIResource()) {
                OntClass ontClass = findRerClass(stmt.getObject().asResource().getURI());
                if (ontClass != null) return ontClass;
            }
            // Check parent shapes if source shape is a nested property shape
            for (ResIterator it = shapesModel.listResourcesWithProperty(SH.property, shape); it.hasNext(); ) {
                Resource parent = it.nextResource();
                Statement pStmt = parent.getProperty(rerError);
                if (pStmt != null && pStmt.getObject().isURIResource()) {
                    OntClass ontClass = findRerClass(pStmt.getObject().asResource().getURI());
                    if (ontClass != null) return ontClass;
                }
            }
        }

        // 2. Check path property shape if present
        Resource path = vr.getPath();
        if (path != null && path.isURIResource()) {
            for (ResIterator it = shapesModel.listResourcesWithProperty(SH.path, path); it.hasNext(); ) {
                Resource pShape = it.nextResource();
                Statement pStmt = pShape.getProperty(rerError);
                if (pStmt != null && pStmt.getObject().isURIResource()) {
                    OntClass ontClass = findRerClass(pStmt.getObject().asResource().getURI());
                    if (ontClass != null) return ontClass;
                }
            }
        }

        return RER.MappingError;
    }

    private static @Nullable OntClass findRerClass(String uri) {
        try {
            org.apache.jena.ontology.OntModel ontModel = (org.apache.jena.ontology.OntModel) RER.NAMESPACE.getModel();
            OntClass cls = ontModel.getOntClass(uri);
            if (cls != null) return cls;
            return ontModel.createClass(uri);
        } catch (Exception ignored) {
        }
        return null;
    }

    public static String cleanMessage(@Nullable String raw) {
        if (raw == null) return "";
        String trimmed = raw.strip();
        String[] lines = trimmed.split("\r?\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String cleanLine = line.strip();
            if (!cleanLine.isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append(" ");
                }
                sb.append(cleanLine);
            }
        }
        return sb.toString();
    }

    private static void unpackOrConstraint(
            ValidationResult vr,
            Model mapping,
            Model shapesModel,
            ValidationEngineConfiguration configuration,
            StringBuilder msg
    ) {
        Resource shape = vr.getSourceShape() != null ? vr.getSourceShape().inModel(shapesModel) : null;
        if (shape == null || !shape.hasProperty(SH.or)) {
            return;
        }

        RDFList orList = shape.getProperty(SH.or).getResource().as(RDFList.class);
        msg.append("\nRequires satisfying at least one of ").append(orList.size()).append(" options:\n");

        RDFNode targetNode = vr.getValue() != null ? vr.getValue() : vr.getFocusNode();
        if (targetNode == null) return;

        int branchIdx = 1;
        for (var iter = orList.iterator(); iter.hasNext(); ) {
            Resource branch = iter.next().asResource();
            String branchName = cleanMessage(getShapeLabel(branch));
            msg.append("  Option ").append(branchIdx++).append(" (").append(branchName).append("): failed because:\n");

            try {
                ValidationEngine branchEngine = ValidationUtil.createValidationEngine(mapping, shapesModel, configuration);
                branchEngine.validateNodesAgainstShape(List.of(targetNode), branch.asNode());
                branchEngine.updateConforms();
                var bReport = branchEngine.getValidationReport();

                if (bReport.results().isEmpty()) {
                    msg.append("    - Condition not satisfied\n");
                } else {
                    for (ValidationResult bRes : bReport.results()) {
                        List<ValidationResult> leaves = collectLeafViolations(bRes);
                        for (ValidationResult leaf : leaves) {
                            String bMsg = cleanMessage(leaf.getMessage());
                            if (bMsg.isEmpty()) {
                                bMsg = leaf.getSourceConstraintComponent() != null ? leaf.getSourceConstraintComponent().getLocalName() : "Violation";
                            }
                            msg.append("    - ").append(bMsg);
                            if (leaf.getPath() != null) {
                                msg.append(" on path ").append(leaf.getPath());
                            }
                            msg.append("\n");
                        }
                    }
                }
            } catch (Exception ignored) {
                msg.append("    - Could not validate against branch\n");
            }
        }
    }

    private static String getShapeLabel(Resource shape) {
        if (shape.hasProperty(RDFS.label)) {
            return shape.getProperty(RDFS.label).getString();
        }
        if (shape.hasProperty(SH.node)) {
            Resource nodeShape = shape.getProperty(SH.node).getResource();
            return getShapeLabel(nodeShape);
        }
        if (shape.isURIResource()) {
            String uri = shape.getURI();
            int hash = uri.lastIndexOf('#');
            int slash = uri.lastIndexOf('/');
            int split = Math.max(hash, slash);
            return split >= 0 ? uri.substring(split + 1) : uri;
        }
        if (shape.hasProperty(SH.targetClass)) {
            return "TargetClass: " + shape.getProperty(SH.targetClass).getResource().getLocalName();
        }
        return "Shape";
    }

    public static List<StatementParts> extractStatements(
            @Nullable RDFNode focusNode,
            @Nullable Resource pathRes,
            @Nullable RDFNode valueNode,
            Model mapping
    ) {
        List<StatementParts> results = new ArrayList<>();
        if (focusNode == null || !focusNode.isResource()) return results;

        Resource focusResource = focusNode.asResource();

        if (pathRes != null && pathRes.isURIResource()) {
            Property predicate = mapping.getProperty(pathRes.getURI());
            if (valueNode != null) {
                mapping.listStatements(focusResource, predicate, valueNode).forEachRemaining(stmt ->
                        results.add(StatementParts.from(stmt, StatementPart.Subject, StatementPart.Predicate, StatementPart.Object))
                );
            }
            if (results.isEmpty()) {
                mapping.listStatements(focusResource, predicate, (RDFNode) null).forEachRemaining(stmt ->
                        results.add(StatementParts.from(stmt, StatementPart.Subject, StatementPart.Predicate, StatementPart.Object))
                );
            }
        }

        if (results.isEmpty() && valueNode != null) {
            mapping.listStatements(focusResource, null, valueNode).forEachRemaining(stmt ->
                    results.add(StatementParts.from(stmt, StatementPart.Subject, StatementPart.Predicate, StatementPart.Object))
            );
        }

        if (results.isEmpty()) {
            mapping.listStatements(focusResource, null, (RDFNode) null).forEachRemaining(stmt ->
                    results.add(StatementParts.from(stmt, StatementPart.Subject))
            );
            mapping.listStatements(null, null, focusResource).forEachRemaining(stmt -> {
                boolean contains = results.stream().anyMatch(it -> it.stmt().equals(stmt));
                if (!contains) {
                    results.add(StatementParts.from(stmt, StatementPart.Object));
                }
            });
        }

        return results;
    }
}
