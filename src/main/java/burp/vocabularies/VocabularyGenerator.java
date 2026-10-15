package burp.vocabularies;

import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.OntSpecification;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class VocabularyGenerator {

    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "record", "sealed", "permits", "yield", "var", "null", "true", "false"
    );

    public static void main(String[] args) throws Exception {
        Path baseDir = Path.of("").toAbsolutePath();
        List<Path> ontologyFiles = List.of(
                baseDir.resolve("rml-modules/rml-core/ontology/rml-core.owl"),
                baseDir.resolve("rml-modules/rml-cc/ontology/rml-cc.owl"),
                baseDir.resolve("rml-modules/rml-io/ontology/rml-io.owl"),
                baseDir.resolve("rml-modules/rml-fnml/ontology/rml-fnml.owl"),
                baseDir.resolve("rml-modules/rml-lv/ontology/rml-lv.owl")
        );

        Path outputFile = baseDir.resolve("src/main/java/burp/vocabularies/Rml.java");
        generateRmlVocabulary(ontologyFiles, outputFile);
        System.out.println("Generated " + outputFile);
    }

    public static void generateRmlVocabulary(List<Path> ontologyFiles, Path outputFile) throws Exception {
        String namespace = "http://w3id.org/rml/";
        OntModel model = OntModelFactory.createModel(OntSpecification.OWL2_FULL_MEM);

        for (Path file : ontologyFiles) {
            if (Files.exists(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    RDFDataMgr.read(model, in, Lang.TURTLE);
                }
            }
        }

        // Collect terms from ontologies
        Set<String> terms = new TreeSet<>();
        model.classes().forEach(cls -> addTerm(terms, cls.getURI(), namespace));
        model.properties().forEach(prop -> addTerm(terms, prop.getURI(), namespace));
        model.individuals().forEach(ind -> addTerm(terms, ind.getURI(), namespace));

        // Known terms and shortcuts missing from loaded ontologies
        List<String> knownTerms = List.of(
                // Shortcuts
                "subject", "predicate", "object", "graph", "language", "datatype", "parent", "child",
                "return", "function", "parameter", "inputValue", "condition",

                // RML-Core
                "BlankNode", "IRI", "URI", "UnsafeIRI", "Literal", "defaultGraph", "JoinCondition",

                // RML-CC
                "GatherMap", "gatherMap", "list",

                // RML-IO
                "CSV", "JSONPath", "XPath", "XPathReferenceFormulation", "SQL2008Table", "SQL2008Query",
                "MappingDirectory", "CurrentWorkingDirectory", "Namespace", "RelativePathSource", "FilePath",
                "append", "cartesianProduct", "UTF-8", "UTF-16", "none", "gzip", "zip", "tarxz", "targz",
                "noEmpty",

                // RML-FNML
                "InputValueMap"
        );
        terms.addAll(knownTerms);

        Set<String> generatedIdentifiers = new java.util.HashSet<>();

        StringBuilder sb = new StringBuilder();
        sb.append("package burp.vocabularies;\n\n");
        sb.append("/**\n");
        sb.append(" * Compile-time String constants for RML vocabularies (used in annotations and String-based lookups).\n");
        sb.append(" */\n");
        sb.append("public final class Rml {\n\n");
        sb.append("    public static final String NS = \"").append(namespace).append("\";\n\n");

        for (String name : terms) {
            String identifier = sanitizeIdentifier(name);
            if (generatedIdentifiers.add(identifier)) {
                sb.append("    public static final String ").append(identifier).append(" = NS + \"").append(name).append("\";\n");
            }
        }
        sb.append("}\n");

        Files.writeString(outputFile, sb.toString(), StandardCharsets.UTF_8);
    }

    private static void addTerm(Set<String> terms, String uri, String namespace) {
        if (uri != null && uri.startsWith(namespace)) {
            terms.add(uri.substring(namespace.length()));
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
}
