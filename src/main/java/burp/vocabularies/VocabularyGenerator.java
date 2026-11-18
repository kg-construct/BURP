package burp.vocabularies;

import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.OntSpecification;
import org.apache.jena.ontapi.model.OntAnnotationProperty;
import org.apache.jena.ontapi.model.OntClass;
import org.apache.jena.ontapi.model.OntDataProperty;
import org.apache.jena.ontapi.model.OntIndividual;
import org.apache.jena.ontapi.model.OntList;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.ontapi.model.OntObjectProperty;
import org.apache.jena.ontapi.model.OntObject;
import org.apache.jena.ontapi.model.OntProperty;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDFS;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/// Base generator for BURP RDF vocabulary classes using Jena's modern OntAPI (`org.apache.jena.ontapi`).
///
/// Subclasses specialize the vocabulary namespace, input ontology files, target Java class/file,
/// and generation options without exposing CLI or Maven plugin configuration flags.
@NullMarked
public abstract class VocabularyGenerator {

    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "record", "sealed", "permits", "yield", "var", "null", "true", "false"
    );

    protected abstract String namespace();

    protected abstract String className();

    protected abstract Path outputFile(Path baseDir);

    protected abstract List<Path> ontologyFiles(Path baseDir);

    protected List<Path> importedFiles(Path baseDir) {
        return List.of();
    }

    protected boolean isStringConstantsOnly() {
        return false;
    }

    protected List<String> extraKnownTerms() {
        return Stream.concat(extraKnownProperties().stream(), extraKnownResources().stream()).toList();
    }

    protected List<String> extraKnownProperties() {
        return List.of();
    }

    protected List<String> extraKnownResources() {
        return List.of();
    }

    protected @Nullable String modelResourcePath() {
        return null;
    }

    protected boolean includeTaxonomyMaps() {
        return false;
    }

    protected String ontSpecificationExpression() {
        return "OntSpecification.OWL2_FULL_MEM";
    }

    protected String classJavadoc() {
        return "Auto-generated vocabulary definitions for " + namespace() + ".";
    }

    public final void generate(Path baseDir) throws IOException {
        OntModel model = loadModel(baseDir);
        String content = isStringConstantsOnly()
                ? renderStringConstantsClass(model)
                : renderOntModelClass(model);
        Path target = outputFile(baseDir);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
        System.out.println("Generated " + target);
    }

    private OntModel loadModel(Path baseDir) throws IOException {
        OntModel model = OntModelFactory.createModel(OntSpecification.OWL2_FULL_MEM_RDFS_INF);
        for (Path imported : importedFiles(baseDir)) {
            readTurtleIfPresent(model, imported);
        }
        for (Path file : ontologyFiles(baseDir)) {
            readTurtleIfPresent(model, file);
        }
        return model;
    }

    private static void readTurtleIfPresent(OntModel model, Path file) throws IOException {
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                RDFDataMgr.read(model, in, Lang.TURTLE);
            }
        }
    }

    private String renderStringConstantsClass(OntModel model) {
        String ns = namespace();
        Set<String> terms = new TreeSet<>();
        model.classes().forEach(cls -> addLocalName(terms, cls.getURI(), ns));
        model.properties().forEach(prop -> addLocalName(terms, prop.getURI(), ns));
        model.individuals().forEach(ind -> addLocalName(terms, ind.getURI(), ns));
        terms.addAll(extraKnownTerms());

        Set<String> generatedIdentifiers = new HashSet<>();
        StringBuilder sb = new StringBuilder();
        sb.append("package burp.vocabularies;\n\n");
        sb.append("/**\n * ").append(escapeJavadoc(classJavadoc())).append("\n */\n");
        sb.append("public final class ").append(className()).append(" {\n\n");
        sb.append("    public static final String NS = \"").append(ns).append("\";\n\n");

        for (String name : terms) {
            String identifier = sanitizeIdentifier(name);
            if (generatedIdentifiers.add(identifier)) {
                sb.append("    public static final String ")
                        .append(identifier)
                        .append(" = NS + \"")
                        .append(escapeJavaString(name))
                        .append("\";\n");
            }
        }
        sb.append("}\n");
        return sb.toString();
    }

    private String renderOntModelClass(OntModel model) {
        String ns = namespace();
        StringBuilder sb = new StringBuilder();
        sb.append("package burp.vocabularies;\n\n");
        sb.append("import org.apache.jena.ontapi.OntModelFactory;\n");
        sb.append("import org.apache.jena.ontapi.OntSpecification;\n");
        sb.append("import org.apache.jena.ontapi.model.*;\n");
        sb.append("import org.apache.jena.rdf.model.Property;\n");
        sb.append("import org.apache.jena.rdf.model.Resource;\n");
        if (includeTaxonomyMaps()) {
            sb.append("import burp.reporting.Severity;\n");
            sb.append("import java.util.List;\n");
            sb.append("import java.util.Map;\n");
        }
        sb.append("\n");
        sb.append("/**\n * ").append(escapeJavadoc(classJavadoc())).append("\n */\n");
        sb.append("@SuppressWarnings(\"unused\")\n");
        sb.append("public class ").append(className()).append(" {\n\n");

        sb.append("    private static final OntModel M_MODEL = OntModelFactory.createModel(")
                .append(ontSpecificationExpression())
                .append(");\n\n");
        sb.append("    public static final String NS = \"").append(ns).append("\";\n\n");
        sb.append("    public static String getURI() {\n        return NS;\n    }\n\n");
        sb.append("    public static final Resource NAMESPACE = M_MODEL.createResource(NS);\n\n");

        String versionInfo = model.getID() != null ? model.getID().getVersionInfo() : null;
        if (versionInfo != null && !versionInfo.isBlank()) {
            sb.append("    public static final String VERSION_INFO = \"")
                    .append(escapeJavaString(versionInfo))
                    .append("\";\n\n");
        }

        String resourcePath = modelResourcePath();
        if (resourcePath != null) {
            sb.append("    static {\n");
            sb.append("        try (var in = ").append(className())
                    .append(".class.getClassLoader().getResourceAsStream(\"")
                    .append(escapeJavaString(resourcePath))
                    .append("\")) {\n");
            sb.append("            if (in != null) {\n");
            sb.append("                M_MODEL.read(in, NS, \"TURTLE\");\n");
            sb.append("            }\n");
            sb.append("        } catch (java.io.IOException ignored) {\n");
            sb.append("        }\n");
            sb.append("    }\n\n");
        }

        Set<String> emittedIdentifiers = new HashSet<>();
        Set<String> emittedPropertyUris = new HashSet<>();

        emitResources(sb, model.objectProperties().toList(), ns, emittedIdentifiers, emittedPropertyUris,
                "OntObjectProperty.Named", "createObjectProperty");
        emitResources(sb, model.dataProperties().toList(), ns, emittedIdentifiers, emittedPropertyUris,
                "OntDataProperty", "createDataProperty");
        emitResources(sb, model.annotationProperties().toList(), ns, emittedIdentifiers, emittedPropertyUris,
                "OntAnnotationProperty", "createAnnotationProperty");

        List<OntProperty> remainingProps = model.properties()
                .filter(p -> p.isURIResource() && !emittedPropertyUris.contains(p.getURI()))
                .toList();
        emitResources(sb, remainingProps, ns, emittedIdentifiers, emittedPropertyUris,
                "Property", "createProperty");

        List<OntClass.Named> classes = model.classes()
                .filter(c -> c.isURIResource() && c.getURI().startsWith(ns))
                .sorted(Comparator.comparing(OntClass::getURI))
                .toList();
        emitResources(sb, classes, ns, emittedIdentifiers, new HashSet<>(),
                "OntClass.Named", "createOntClass");

        emitIndividuals(sb, model.individuals().toList(), ns, emittedIdentifiers);

        for (String name : extraKnownProperties()) {
            String identifier = sanitizeIdentifier(name);
            if (emittedIdentifiers.add(identifier)) {
                sb.append("    public static final Property ").append(identifier)
                        .append(" = M_MODEL.createProperty(NS + \"")
                        .append(escapeJavaString(name)).append("\");\n\n");
            }
        }
        for (String name : extraKnownResources()) {
            String identifier = sanitizeIdentifier(name);
            if (emittedIdentifiers.add(identifier)) {
                sb.append("    public static final Resource ").append(identifier)
                        .append(" = M_MODEL.createResource(NS + \"")
                        .append(escapeJavaString(name)).append("\");\n\n");
            }
        }

        if (includeTaxonomyMaps()) {
            emitTaxonomyMaps(sb, classes, ns);
        }

        sb.append("}\n");
        return sb.toString();
    }

    private <T extends OntObject> void emitResources(
            StringBuilder sb,
            List<T> resources,
            String ns,
            Set<String> emittedIdentifiers,
            Set<String> emittedUris,
            String javaType,
            String factoryMethod
    ) {
        resources.stream()
                .filter(r -> r.isURIResource() && r.getURI().startsWith(ns))
                .sorted(Comparator.comparing(Resource::getURI))
                .forEach(r -> {
                    String uri = r.getURI();
                    emittedUris.add(uri);
                    String localName = uri.substring(ns.length());
                    if (localName.isEmpty()) {
                        return;
                    }
                    String identifier = sanitizeIdentifier(localName);
                    if (!emittedIdentifiers.add(identifier)) {
                        return;
                    }
                    appendCommentJavadoc(sb, r);
                    sb.append("    public static final ").append(javaType).append(" ")
                            .append(identifier)
                            .append(" = M_MODEL.").append(factoryMethod).append("(\"")
                            .append(escapeJavaString(uri))
                            .append("\");\n\n");
                });
    }

    private void emitIndividuals(
            StringBuilder sb,
            List<OntIndividual> individuals,
            String ns,
            Set<String> emittedIdentifiers
    ) {
        individuals.stream()
                .filter(ind -> ind.isURIResource() && ind.getURI().startsWith(ns))
                .sorted(Comparator.comparing(OntIndividual::getURI))
                .forEach(ind -> {
                    String uri = ind.getURI();
                    String localName = uri.substring(ns.length());
                    if (localName.isEmpty()) {
                        return;
                    }
                    String identifier = sanitizeIdentifier(localName);
                    if (!emittedIdentifiers.add(identifier)) {
                        return;
                    }
                    appendCommentJavadoc(sb, ind);
                    String typeExpr = ind.classes(true)
                            .filter(c -> c.isURIResource() && c.getURI().startsWith(ns))
                            .map(c -> sanitizeIdentifier(c.getURI().substring(ns.length())))
                            .filter(emittedIdentifiers::contains)
                            .findFirst()
                            .orElse(null);
                    if (typeExpr != null) {
                        sb.append("    public static final OntIndividual.Named ")
                                .append(identifier)
                                .append(" = ")
                                .append(typeExpr)
                                .append(".createIndividual(\"")
                                .append(escapeJavaString(uri))
                                .append("\");\n\n");
                    } else {
                        sb.append("    public static final OntIndividual.Named ")
                                .append(identifier)
                                .append(" = M_MODEL.createIndividual(\"")
                                .append(escapeJavaString(uri))
                                .append("\");\n\n");
                    }
                });
    }

    private void emitTaxonomyMaps(StringBuilder sb, List<? extends OntClass> classes, String ns) {
        Map<String, List<String>> superClassesMap = new TreeMap<>();
        Map<String, String> severityMap = new LinkedHashMap<>();

        String errorUri = ns + "Error";
        String warningUri = ns + "Warning";
        String infoUri = ns + "Information";

        for (OntClass cls : classes) {
            String uri = cls.getURI();
            List<String> supers = cls.superClasses(false)
                    .filter(Resource::isURIResource)
                    .map(Resource::getURI)
                    .filter(u -> !u.equals(uri)
                            && !u.startsWith(OWL2.NS)
                            && !u.startsWith(RDFS.uri))
                    .distinct()
                    .sorted()
                    .toList();
            superClassesMap.put(uri, supers);

            if (uri.equals(errorUri) || supers.contains(errorUri)) {
                severityMap.put(uri, "Severity.ERROR");
            } else if (uri.equals(warningUri) || supers.contains(warningUri)) {
                severityMap.put(uri, "Severity.WARNING");
            } else if (uri.equals(infoUri) || supers.contains(infoUri)) {
                severityMap.put(uri, "Severity.INFORMATION");
            }
        }

        sb.append("    public static final Map<String, List<String>> SUPER_CLASSES = Map.ofEntries(\n");
        int idx = 0;
        for (Map.Entry<String, List<String>> entry : superClassesMap.entrySet()) {
            sb.append("            Map.entry(\"")
                    .append(escapeJavaString(entry.getKey()))
                    .append("\", List.of(");
            List<String> vals = entry.getValue();
            for (int i = 0; i < vals.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append("\"").append(escapeJavaString(vals.get(i))).append("\"");
            }
            sb.append("))");
            if (++idx < superClassesMap.size()) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("    );\n\n");

        sb.append("    public static final Map<String, Severity> SEVERITIES = Map.ofEntries(\n");
        idx = 0;
        for (Map.Entry<String, String> entry : severityMap.entrySet()) {
            sb.append("            Map.entry(\"")
                    .append(escapeJavaString(entry.getKey()))
                    .append("\", ")
                    .append(entry.getValue())
                    .append(")");
            if (++idx < severityMap.size()) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("    );\n\n");

        sb.append("    public static Severity severityOf(OntClass errorType) {\n");
        sb.append("        return SEVERITIES.getOrDefault(errorType.getURI(), Severity.ERROR);\n");
        sb.append("    }\n\n");

        sb.append("    public static List<String> superClassesOf(OntClass errorType) {\n");
        sb.append("        return SUPER_CLASSES.getOrDefault(errorType.getURI(), List.of());\n");
        sb.append("    }\n\n");
    }

    private static void appendCommentJavadoc(StringBuilder sb, OntObject resource) {
        String comment = resource.getComment();
        List<String> domains = formatPropertyTargets(resource, RDFS.domain);
        List<String> ranges = formatPropertyTargets(resource, RDFS.range);
        boolean hasComment = comment != null && !comment.isBlank();
        if (!hasComment && domains.isEmpty() && ranges.isEmpty()) {
            return;
        }
        if (domains.isEmpty() && ranges.isEmpty() && hasComment) {
            sb.append("    /** ").append(escapeJavadoc(comment.trim())).append(" */\n");
            return;
        }
        sb.append("    /**\n");
        if (hasComment) {
            sb.append("     * ").append(escapeJavadoc(comment.trim())).append("\n");
        }
        if (!domains.isEmpty()) {
            sb.append("     * <p>Domain: ").append(escapeJavadoc(String.join(", ", domains))).append("</p>\n");
        }
        if (!ranges.isEmpty()) {
            sb.append("     * <p>Range: ").append(escapeJavadoc(String.join(", ", ranges))).append("</p>\n");
        }
        sb.append("     */\n");
    }

    private static List<String> formatPropertyTargets(OntObject resource, Property predicate) {
        return resource.listProperties(predicate)
                .toList()
                .stream()
                .map(Statement::getObject)
                .map(node -> formatRdfNode(resource.getModel(), node))
                .distinct()
                .toList();
    }

    private static String formatRdfNode(Model model, RDFNode node) {
        if (node.isURIResource()) {
            return model.shortForm(node.asResource().getURI());
        }
        if (node instanceof OntClass.UnionOf union) {
            OntList<? extends OntClass> list = union.getList();
            return list.members()
                    .map(m -> formatRdfNode(model, m))
                    .reduce((a, b) -> a + " | " + b)
                    .orElse("owl:unionOf");
        }
        return node.toString();
    }

    private static void addLocalName(Set<String> terms, @Nullable String uri, String namespace) {
        if (uri != null && uri.startsWith(namespace)) {
            String local = uri.substring(namespace.length());
            if (!local.isEmpty()) {
                terms.add(local);
            }
        }
    }

    private static String sanitizeIdentifier(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isJavaIdentifierPart(c) && c != '-') {
                if (i == 0 && !Character.isJavaIdentifierStart(c)) {
                    sb.append('_');
                }
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String id = sb.toString();
        if (JAVA_KEYWORDS.contains(id)) {
            id = id + "_";
        }
        return id;
    }

    private static String escapeJavaString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String escapeJavadoc(String value) {
        return value.replace("*/", "* /").replace("\r\n", " ").replace("\n", " ");
    }
}
