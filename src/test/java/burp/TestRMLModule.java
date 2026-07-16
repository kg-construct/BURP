package burp;

import burp.util.Util;
import burp.vocabularies.RER;
import burp.vocabularies.RML;
import com.google.gson.JsonParser;
import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.util.IsoMatcher;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class TestRMLModule {

    public abstract String getBase();

    public Stream<TestData> loadTestData() throws IOException, CsvException {
        Path testCaseDir = Paths.get(getBase()).toAbsolutePath().normalize();

        List<TestData> testDataList = new ArrayList<>();
        Path csvFilePath = testCaseDir.resolve("./metadata.csv").normalize();

        try (CSVReaderHeaderAware reader = new CSVReaderHeaderAware(new FileReader(csvFilePath.toFile()))) {
            Map<String, String> record;
            while ((record = reader.readMap()) != null) {
                testDataList.add(new TestData(record));
            }
        }

        testDataList.sort(Comparator.comparing(td -> td.ID));
        return testDataList.stream();
    }

    protected Stream<TestData> testDataProviderOK() throws IOException, CsvException {
        List<TestData> list = loadTestData().filter(t -> !t.error).toList();
        if (list.isEmpty()) {
            return Stream.of(new TestData(java.util.Map.of("ID", "dummy", "error", "false")));
        }
        return list.stream();
    }

    protected Stream<TestData> testDataProviderNotOK() throws IOException, CsvException {
        List<TestData> list = loadTestData().filter(t -> t.error).toList();
        if (list.isEmpty()) {
            return Stream.of(new TestData(java.util.Map.of("ID", "dummy", "error", "true")));
        }
        return list.stream();
    }

    public Path getPath(TestData testData, String path) {
        return Paths.get(getBase(), testData.ID, path).toAbsolutePath().normalize();
    }

    public Path getPathOptional(TestData testData, String path) {
        if (path == null) return null;
        return Paths.get(getBase(), testData.ID, path).toAbsolutePath().normalize();
    }


    public void printTestHeader(TestData testData) {
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("Processing test %s: %s%n", testData.ID, testData.title);
        System.out.println("--------------------------------------------------------------------------------");

        System.out.println("Mapping\t" + getPath(testData, testData.mapping));
        System.out.println("First Input\t" + getPathOptional(testData, testData.input1));
        System.out.println("First Output\t" + getPathOptional(testData, testData.output1));
        System.out.println("Expects error?\t" + testData.error);
        System.out.println();
    }

    private Resource getCompressionFromFileName(String fileName) {
        if (fileName.endsWith(".tar.xz")) return RML.tarxz;
        if (fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz")) return RML.targz;
        if (fileName.endsWith(".gz")) return RML.gzip;
        if (fileName.endsWith(".zip")) return RML.zip;
        return RML.none;
    }

    public void prepareSource(TestData testData) throws Exception {
    }

    @ParameterizedTest
    @MethodSource("testDataProviderOK")
    public void testForOK(TestData testData) throws Exception {
        if ("dummy".equals(testData.ID)) return;
        printTestHeader(testData);
        prepareSource(testData);

        Path originalCwd = Paths.get(getBase(), testData.ID).toAbsolutePath().normalize();
        Path tempDir = Files.createTempDirectory(testData.ID);

        List<String> outputs = new ArrayList<>();
        if (testData.output1 != null && !testData.output1.isBlank()) outputs.add(testData.output1);
        if (testData.output2 != null && !testData.output2.isBlank()) outputs.add(testData.output2);
        if (testData.output3 != null && !testData.output3.isBlank()) outputs.add(testData.output3);

        try (Stream<Path> stream = Files.walk(originalCwd)) {
            stream.forEach(source -> {
                try {
                    Path relative = originalCwd.relativize(source);
                    Path dest = tempDir.resolve(relative);
                    if (!Files.isDirectory(source) && !outputs.contains(relative.toString())) {
                        Files.createDirectories(dest.getParent());
                        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        String resultPath = tempDir.resolve(testData.output1 != null && !testData.output1.isEmpty() ? testData.output1 : "default.nq").toString();
        String reportPath = Files.createTempFile("report_" + testData.ID, ".nq").toString();

        String tempMappingPath = tempDir.resolve(testData.mapping).toString();

        int exit = Main.doMain(
                new String[]{
                        "-m", tempMappingPath,
                        "-o", resultPath,
                        "--baseIRI", testData.baseIRI,
                        "--reportFile", reportPath
                },
                tempDir
        );
        System.out.println("Exit code: " + exit);

        for (String out : outputs) {
            String expectedOutputPathStr = originalCwd.resolve(out).toString();
            Path expectedOutputPath = Paths.get(expectedOutputPathStr);
            String actualOutputPathStr = tempDir.resolve(out).toString();
            Path actualOutputPath = Paths.get(actualOutputPathStr);

            System.out.println("Checking output file: " + out);
            System.out.println("Expected: " + expectedOutputPath);
            System.out.println("Actual: " + actualOutputPath);

            Resource expectedCompression = getCompressionFromFileName(out);
            String decompressedExpectedPath = expectedOutputPathStr;
            if (expectedCompression != RML.none && Files.exists(expectedOutputPath)) {
                System.out.println("Decompressing expected output: " + expectedOutputPath);
                decompressedExpectedPath = Util.getDecompressedFile(expectedOutputPathStr, expectedCompression);
            }

            Resource actualCompression = getCompressionFromFileName(out);
            String decompressedActualPath = actualOutputPathStr;
            if (actualCompression != RML.none && Files.exists(actualOutputPath)) {
                System.out.println("Decompressing actual output: " + actualOutputPath);
                decompressedActualPath = Util.getDecompressedFile(actualOutputPathStr, actualCompression);
            }

            boolean isNFormat = out.endsWith(".nt") || out.endsWith(".nq")
                    || out.matches(".*\\.(nt|nq)(\\..*)?");
            boolean isJsonFormat = out.endsWith(".json") || out.endsWith(".jsonld") || out.endsWith(".rdfjson");

            try {
                DatasetGraph expected = normalizeDoubles(loadDataset(decompressedExpectedPath));
                DatasetGraph actual = normalizeDoubles(loadDataset(decompressedActualPath));

                boolean isIsomorphic = IsoMatcher.isomorphic(expected, actual);
                if (!isIsomorphic) {
                    System.out.println("--- Expected");
                    RDFDataMgr.write(System.out, expected, Lang.TRIG);
                    System.out.println("--- Actual");
                    RDFDataMgr.write(System.out, actual, Lang.TRIG);
                }

                System.out.println("Isomorphic? " + (isIsomorphic ? "OK" : "NOK"));
                assertTrue(isIsomorphic, "is not isomorphic");
            } catch (Exception e) {
                if (isNFormat) {
                    System.out.println("RDF parsing failed, falling back to line-by-line comparison: " + e.getMessage());

                    String expectedData = Files.readString(Paths.get(decompressedExpectedPath));
                    String actualData = Files.readString(Paths.get(decompressedActualPath));

                    List<String> expectedLines = normalizeAndDeduplicateLines(expectedData);
                    List<String> actualLines = normalizeAndDeduplicateLines(actualData);

                    if (expectedLines.equals(actualLines)) {
                        System.out.println("Line comparison: OK - Matched by normalized line-by-line comparison");
                    } else {
                        System.out.println("--- Expected (normalized)");
                        expectedLines.forEach(System.out::println);
                        System.out.println("--- Actual (normalized)");
                        actualLines.forEach(System.out::println);
                        System.out.println("--- Actual (raw)");
                        System.out.println(actualData);
                        throw new RuntimeException("Expected and actual do not match in line-by-line comparison for " + out, e);
                    }
                } else if (isJsonFormat) {
                    System.out.println("JSON parsing failed, falling back to deep JSON comparison: " + e.getMessage());

                    String expectedData = Files.readString(Paths.get(decompressedExpectedPath));
                    String actualData = Files.readString(Paths.get(decompressedActualPath));

                    var expectedJson = JsonParser.parseString(expectedData);
                    var actualJson = JsonParser.parseString(actualData);

                    if (expectedJson.equals(actualJson)) {
                        System.out.println("JSON comparison: OK - Matched by deep JSON comparison");
                    } else {
                        System.out.println("--- Expected (JSON)");
                        System.out.println(expectedJson);
                        System.out.println("--- Actual (JSON)");
                        System.out.println(actualJson);
                        throw new RuntimeException("Expected and actual do not match in deep JSON comparison for " + out, e);
                    }
                } else {
                    throw e;
                }
            }
        }

        System.out.println("Exit code: " + exit);

        Model report = RDFDataMgr.loadModel(reportPath);
        long countErrors = getCountErrors(report);
        var errorTypes = getErrorTypes(report);
        if (countErrors > 0) {
            String errorTypeNames = mapToLocalNames(errorTypes).collect(Collectors.joining(", "));
            System.out.println("Error types: " + errorTypeNames);
        }
    }

    private static @NonNull Stream<String> mapToLocalNames(List<Resource> errorTypes) {
        return errorTypes.stream().map(Resource::getLocalName);
    }

    @ParameterizedTest
    @MethodSource("testDataProviderNotOK")
    public void testForNotOK(TestData testData) throws Exception {
        if ("dummy".equals(testData.ID)) return;
        printTestHeader(testData);
        prepareSource(testData);
        testForNotOK(testData, getPath(testData, testData.mapping).toAbsolutePath().toString());
    }

    public void testForNotOK(TestData testData, String mappingPath) throws Exception {
        String resultPath = Files.createTempFile(null, ".nq").toString();
        String reportPath = Files.createTempFile("report_" + testData.ID, ".nq").toString();
        System.out.printf("Writing output to %s%n", resultPath);

        Path cwd = Paths.get(getBase(), testData.ID).toAbsolutePath().normalize();
        int exit = Main.doMain(
                new String[]{
                        "-m", mappingPath,
                        "-o", resultPath,
                        "--baseIRI", testData.baseIRI,
                        "--reportFile", reportPath
                },
                cwd
        );

        long outputFileSize = Files.size(Paths.get(resultPath));
        System.out.println(outputFileSize == 0L ? "No output file" : "Output file is not empty");

        if (outputFileSize != 0L) {
            Model actual = RDFDataMgr.loadModel(resultPath);
            System.out.println("--- Actual");
            actual.write(System.out, "NQ");
        }

        Model report = RDFDataMgr.loadModel(reportPath);
        System.out.println("--- Report");
        report.write(System.out, "Turtle");

        Path errorCsv = Paths.get(getBase(), "error.csv");
        if (!Files.exists(errorCsv)) Files.createFile(errorCsv);
        try (CSVWriter writer = new CSVWriter(Files.newBufferedWriter(errorCsv, StandardOpenOption.APPEND))) {
            writer.writeNext(new String[]{testData.ID});
            writer.flush();
        }

        assertTrue(exit > 0);
        assertFalse(report.isEmpty());

        long countErrors = getCountErrors(report);
        var errorTypes = getErrorTypes(report);

        System.out.println("Error types: " + errorTypes);
        assertTrue(countErrors > 0, "Expected at least 1 error, but got " + countErrors);

        checkExpectedErrors(testData, errorTypes);

        System.out.println();

        try (CSVWriter writer = new CSVWriter(Files.newBufferedWriter(errorCsv, StandardOpenOption.APPEND))) {
            List<String> nextLine = new ArrayList<>();
            nextLine.add(testData.ID);
            nextLine.add(testData.title);
            nextLine.add(String.valueOf(countErrors));
            nextLine.addAll(mapToLocalNames(errorTypes).toList());
            writer.writeNext(nextLine.toArray(new String[0]));
            writer.flush();
        }
    }


    private DatasetGraph loadDataset(String path) throws RiotException {
        if (path.endsWith(".rdfjson")) {
            return RDFDataMgr.loadDatasetGraph(path, Lang.RDFJSON);
        }
        if (path.endsWith(".rdfxml")) {
            return RDFDataMgr.loadDatasetGraph(path, Lang.RDFXML);
        }
        return RDFDataMgr.loadDatasetGraph(path);
    }

    private static long getCountErrors(Model report) {
        String countQueryString =
                "PREFIX rer: <" + RER.NS + ">\n" +
                        "SELECT (COUNT(?error) AS ?count) WHERE {\n" +
                        "  ?s rer:hasError ?error .\n" +
                        "}";

        long countErrors = 0;
        try (QueryExecution qexec = QueryExecutionFactory.create(countQueryString, report)) {
            ResultSet results = qexec.execSelect();
            if (results.hasNext()) {
                QuerySolution soln = results.nextSolution();
                countErrors = soln.getLiteral("count").getLong();
            }
        }
        return countErrors;
    }

    private static List<Resource> getErrorTypes(Model report) {
        String typeQueryString =
                "PREFIX rer: <" + RER.NS + ">\n" +
                        "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
                        "SELECT ?type WHERE {\n" +
                        "  ?s rer:hasError ?error .\n" +
                        "  ?error rdf:type ?type .\n" +
                        "}";
        List<Resource> errorTypes = new ArrayList<>();
        try (QueryExecution qexec = QueryExecutionFactory.create(typeQueryString, report)) {
            ResultSet results = qexec.execSelect();
            while (results.hasNext()) {
                QuerySolution soln = results.nextSolution();
                Resource typeInfo = soln.getResource("type");
                if (typeInfo != null) {
                    errorTypes.add(typeInfo);
                }
            }
        }
        return errorTypes;
    }

    private void checkExpectedErrors(TestData testData, List<Resource> errorTypes) throws IOException, CsvException {
        Path expectedErrorsCsv = Paths.get("src/test/resources/burp/errors.csv").toAbsolutePath().normalize();
        if (Files.exists(expectedErrorsCsv)) {
            try (CSVReaderHeaderAware reader = new CSVReaderHeaderAware(new FileReader(expectedErrorsCsv.toFile()))) {
                Map<String, String> record;
                while ((record = reader.readMap()) != null) {
                    if (testData.ID.equals(record.get("TestCaseId"))) {
                        String expectedRerError = record.get("RerError");
                        if (expectedRerError != null && !expectedRerError.isBlank()) {
                            String rerNamespace = "https://w3id.org/dre/rer#";
                            String expectedUri = expectedRerError.contains(":") || expectedRerError.startsWith("http") ? expectedRerError : rerNamespace + expectedRerError;
                            Resource expected = RER_ONTOLOGY.getResource(expectedUri);
                            boolean match = errorTypes.stream()
                                    .anyMatch(actual -> isSubclassOf(actual, expected));
                            assertTrue(match, "Expected error type " + expectedRerError + " (or a subclass) not found in actual error types: " + errorTypes);
                        }
                    }
                }
            }
        }
    }

    private static final Model RER_ONTOLOGY;

    static {
        var model = org.apache.jena.rdf.model.ModelFactory.createDefaultModel();
        try {
            Path path = Paths.get("src/main/resources/vocabularies/rer.ttl").toAbsolutePath().normalize();
            if (Files.exists(path)) {
                RDFDataMgr.read(model, path.toString(), Lang.TURTLE);
            } else {
                try (var is = TestRMLModule.class.getResourceAsStream("/vocabularies/rer.ttl")) {
                    if (is != null) {
                        RDFDataMgr.read(model, is, Lang.TURTLE);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load RER ontology");
            e.printStackTrace();
        }
        RER_ONTOLOGY = model;
    }

    private static boolean isSubclassOf(Resource actual, Resource expected) {
        if (actual.equals(expected)) {
            return true;
        }
        Resource ontologyActual = RER_ONTOLOGY.getResource(actual.getURI());
        if (ontologyActual != null) {
            org.apache.jena.rdf.model.StmtIterator it = ontologyActual.listProperties(org.apache.jena.vocabulary.RDFS.subClassOf);
            while (it.hasNext()) {
                org.apache.jena.rdf.model.Statement s = it.nextStatement();
                if (s.getObject().isResource()) {
                    if (isSubclassOf(s.getObject().asResource(), expected)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static List<String> normalizeAndDeduplicateLines(String data) {
        Set<String> normalizedLines = new HashSet<>();
        String[] lines = data.trim().split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                String normalized = trimmed.replaceAll("\\s+", " ");
                normalizedLines.add(normalized);
            }
        }
        List<String> sorted = new ArrayList<>(normalizedLines);
        Collections.sort(sorted);
        return sorted;
    }


    /**
     * Normalises the lexical forms of xsd:double and xsd:float literal values.
     * <p>
     * Different test suites have conflicting representation expectations for double/float literals:
     * <ul>
     * <li>rml-io-registry test cases (0004o, 0004t, 0005t, 0006t) expect standard decimal lexical representations (like <code>"20.0"^^xsd:double</code>).</li>
     * <li>rml-io test cases (e.g., RMLTTC0002q) expect canonical XML Schema scientific lexical representations (like <code>"3.3E1"^^xsd:double</code>).</li>
     * </ul>
     * <p>
     * To ensure that the test isomorphic check compares literals by their actual numerical values
     * rather than their literal lexical representations, this method parses all double/float
     * literals in the DatasetGraph and normalises them to the standard <code>Double.toString (d)</code> format.
     */
    private static DatasetGraph normalizeDoubles(DatasetGraph dsg) {
        DatasetGraph normalized = DatasetGraphFactory.create();
        Iterator<Quad> it = dsg.find();
        while (it.hasNext()) {
            Quad q = it.next();
            Node obj = q.getObject();
            if (obj.isLiteral()) {
                String dtUri = obj.getLiteralDatatypeURI();
                if (XSDDatatype.XSDdouble.getURI().equals(dtUri) || XSDDatatype.XSDfloat.getURI().equals(dtUri)) {
                    try {
                        double val = Double.parseDouble(obj.getLiteralLexicalForm());
                        String normLex = Double.toString(val);
                        Node normObj = NodeFactory.createLiteralDT(normLex, obj.getLiteralDatatype());
                        q = Quad.create(q.getGraph(), q.getSubject(), q.getPredicate(), normObj);
                    } catch (NumberFormatException e) {
                        // fallback
                    }
                }
            }
            normalized.add(q);
        }
        return normalized;
    }
}
