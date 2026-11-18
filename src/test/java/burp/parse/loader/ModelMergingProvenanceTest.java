package burp.parse.loader;

import burp.Main;
import burp.model.MappingDocument;
import burp.model.TriplesMap;
import burp.parse.RmlLoader;
import burp.parse.ValidationMode;
import burp.processor.RmlProcessor;
import burp.reporting.BurpException;
import burp.reporting.RmlExecutionReport;
import org.apache.jena.riot.Lang;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@NullMarked
public class ModelMergingProvenanceTest {

    @Test
    public void testMergedModelsRetainFileProvenance() throws Exception {
        Path file1 = Path.of("src/test/resources/burp/merge/mapping1.ttl").toAbsolutePath().normalize();
        Path file2 = Path.of("src/test/resources/burp/merge/mapping2.ttl").toAbsolutePath().normalize();
        Path dir = Path.of("src/test/resources/burp/merge").toAbsolutePath().normalize();

        RmlLoader loader = new RmlLoader(List.of(file1, file2))
                .withCurrentDirectory(dir)
                .withValidationMode(ValidationMode.LENIENT);

        MappingDocument doc = loader.loadMappingDocument();
        assertThat(doc.triplesMaps).hasSize(2);

        TriplesMap studentMap = doc.triplesMaps.stream()
                .filter(tm -> tm.subject.getURI().endsWith("StudentMap"))
                .findFirst().orElseThrow();
        TriplesMap courseMap = doc.triplesMaps.stream()
                .filter(tm -> tm.subject.getURI().endsWith("CourseMap"))
                .findFirst().orElseThrow();

        assertThat(studentMap.getSourceFile()).isEqualTo(file1);
        assertThat(courseMap.getSourceFile()).isEqualTo(file2);

        RmlProcessor processor = new RmlProcessor();
        var stmts = processor.executeToStatements(doc);
        assertThat(stmts.get(burp.model.LogicalTarget.DEFAULT)).hasSize(2);
    }

    @Test
    public void testCliExecutionWithMultipleMappingFiles(@TempDir Path tempDir) throws Exception {
        Path csv1 = tempDir.resolve("items1.csv");
        Files.writeString(csv1, "id\n1\n");
        Path csv2 = tempDir.resolve("items2.csv");
        Files.writeString(csv2, "id\n2\n");

        Path file1 = tempDir.resolve("mapA.ttl");
        Files.writeString(file1, """
                PREFIX rml: <http://w3id.org/rml/>
                PREFIX ex: <http://example.com/>

                ex:ItemMapA a rml:TriplesMap ;
                    rml:logicalSource [
                        rml:source [ a rml:RelativePathSource ; rml:path "items1.csv" ] ;
                        rml:referenceFormulation rml:CSV
                    ] ;
                    rml:subjectMap [
                        rml:template "http://example.com/itemA/{id}"
                    ] ;
                    rml:predicateObjectMap [
                        rml:predicate ex:type ;
                        rml:objectMap [
                            rml:constant ex:A
                        ]
                    ] .
                """);

        Path file2 = tempDir.resolve("mapB.ttl");
        Files.writeString(file2, """
                PREFIX rml: <http://w3id.org/rml/>
                PREFIX ex: <http://example.com/>

                ex:ItemMapB a rml:TriplesMap ;
                    rml:logicalSource [
                        rml:source [ a rml:RelativePathSource ; rml:path "items2.csv" ] ;
                        rml:referenceFormulation rml:CSV
                    ] ;
                    rml:subjectMap [
                        rml:template "http://example.com/itemB/{id}"
                    ] ;
                    rml:predicateObjectMap [
                        rml:predicate ex:type ;
                        rml:objectMap [
                            rml:constant ex:B
                        ]
                    ] .
                """);

        Path outputFile = tempDir.resolve("output.nt");

        // Test multiple -m flags
        int exitCode1 = Main.doMain(new String[]{
                "-m", file1.toString(),
                "-m", file2.toString(),
                "-o", outputFile.toString(),
                "-f", "N-TRIPLES"
        }, tempDir);
        assertThat(exitCode1).isEqualTo(0);
        String outputContent1 = Files.readString(outputFile);
        assertThat(outputContent1).contains("itemA/1");
        assertThat(outputContent1).contains("itemB/2");

        // Test multiple --mappingFile flags
        Files.delete(outputFile);
        int exitCode2 = Main.doMain(new String[]{
                "--mappingFile", file1.toString(),
                "--mappingFile", file2.toString(),
                "-o", outputFile.toString(),
                "-f", "N-TRIPLES"
        }, tempDir);
        assertThat(exitCode2).isEqualTo(0);
        String outputContent2 = Files.readString(outputFile);
        assertThat(outputContent2).contains("itemA/1");
        assertThat(outputContent2).contains("itemB/2");
    }

    @Test
    public void testErrorProvenanceInMergedModelsPointsToSpecificFile(@TempDir Path tempDir) throws Exception {
        Path csv1 = tempDir.resolve("data1.csv");
        Files.writeString(csv1, "id\n1\n");

        Path file1 = tempDir.resolve("valid.ttl");
        Files.writeString(file1, """
                PREFIX rml: <http://w3id.org/rml/>
                PREFIX ex: <http://example.com/>

                ex:ValidMap a rml:TriplesMap ;
                    rml:logicalSource [
                        rml:source [ a rml:RelativePathSource ; rml:path "data1.csv" ] ;
                        rml:referenceFormulation rml:CSV
                    ] ;
                    rml:subjectMap [
                        rml:template "http://example.com/valid/{id}"
                    ] .
                """);

        // Unescaped backslash in template causes syntax error during parsing
        Path file2 = tempDir.resolve("invalid.ttl");
        Files.writeString(file2, """
                PREFIX rml: <http://w3id.org/rml/>
                PREFIX ex: <http://example.com/>

                ex:InvalidMap a rml:TriplesMap ;
                    rml:logicalSource [
                        rml:source [ a rml:RelativePathSource ; rml:path "data1.csv" ] ;
                        rml:referenceFormulation rml:CSV
                    ] ;
                    rml:subjectMap [
                        rml:template "http://example.com/invalid/{\\\\id}"
                    ] .
                """);

        RmlExecutionReport report = new RmlExecutionReport();
        RmlLoader loader = new RmlLoader(List.of(file1, file2))
                .withCurrentDirectory(tempDir)
                .withReport(report)
                .withValidationMode(ValidationMode.LENIENT);

        MappingDocument doc = loader.loadMappingDocument();
        TriplesMap invalidMap = doc.triplesMaps.stream()
                .filter(tm -> tm.subject.getURI().endsWith("InvalidMap"))
                .findFirst().orElseThrow();

        assertThat(invalidMap.getSourceFile()).isEqualTo(file2.toAbsolutePath().normalize());
    }
}
