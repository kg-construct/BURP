package burp;

import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.exceptions.CsvException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class TestRMLModule {

    public abstract String getBase();

    Stream<TestData> testDataProvider() throws IOException, CsvException {

        Path testCaseDir = Paths.get(getBase());

        List<TestData> testDataList = new ArrayList<TestData>();
        Path csvFilePath = testCaseDir.resolve("./metadata.csv");
        var reader = new CSVReaderHeaderAware(new FileReader(csvFilePath.toFile()));

        Map<String, String> record;
        while ((record = reader.readMap()) != null) {
            TestData td = new TestData(record);
            td.baseIRI = "http://example.com/base/";
            testDataList.add(td);
        }

        return testDataList.stream().sorted(Comparator.comparing(t -> t.ID));
    }

    @ParameterizedTest
    @MethodSource("testDataProvider")
    void testDirectoryBasedCases(TestData testData) throws Exception {
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("Processing test %s: %s%n", testData.ID, testData.title);
        System.out.println("--------------------------------------------------------------------------------");

        System.out.println(testData.mapping);
        System.out.println(testData.output1);
        System.out.println(testData.error);
        System.out.println();

        if (testData.error) testForNotOK(testData);
        else testForOK(testData);
    }

    public void testForOK(TestData testData) throws IOException {
        String m = Path.of(getBase(), testData.ID, testData.mapping).toAbsolutePath().normalize().toString();
        String r = Files.createTempFile(null, ".nq").toString();
        System.out.printf("Writing output to %s%n", r);

        System.out.println("This test should generate a graph.");
        String expectedOutputPath = Path.of(getBase(), testData.ID, testData.output1).toAbsolutePath().normalize().toString();

        Path cwd = Path.of(getBase(), testData.ID).toAbsolutePath().normalize();
        int exit = Main.doMain(new String[]{"-m", m, "-o", r, "-b", "http://example.com/base/"}, cwd);

        Model expected = RDFDataMgr.loadModel(expectedOutputPath);
        Model actual = RDFDataMgr.loadModel(r);

        boolean isIsomorphic = expected.isIsomorphicWith(actual);
        if (!isIsomorphic) {
            expected.write(System.out, "Turtle");
            System.out.println("---");
            actual.write(System.out, "Turtle");
        }

        assertEquals(0, exit);

        System.out.println(isIsomorphic ? "OK" : "NOK");

        assertTrue(isIsomorphic);
    }

    public void testForNotOK(TestData testData) throws IOException {
        String m = new File(getBase() + testData.ID, testData.mapping).getAbsolutePath();
        String r = Files.createTempFile(null, ".nq").toString();
        System.out.printf("Writing output to %s%n", r);

        System.out.println("This test should NOT generate a graph.");
        Path cwd = Path.of(getBase(), testData.ID).toAbsolutePath().normalize();
        int exit = Main.doMain(new String[]{"-m", m, "-o", r}, cwd);
        long outputFileSize = Files.size(Paths.get(r));
        System.out.println(outputFileSize == 0 ? "OK" : "NOK");
        Model actual = RDFDataMgr.loadModel(r);
        actual.write(System.out, "NQ");

        assertTrue(exit > 0);
        assertEquals(0, outputFileSize);

        System.out.println();
    }

}
