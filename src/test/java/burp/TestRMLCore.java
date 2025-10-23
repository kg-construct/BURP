package burp;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRMLCore {

    public static String base = "./src/test/resources/rml-core/";

    static Stream<TestData> testDataProvider() throws IOException, CsvException {

        Path testCaseDir = Paths.get(base);

        List<TestData> testDataList = new ArrayList<>();
        Path csvFilePath = testCaseDir.resolve("./metadata.csv");
        CSVReader reader = new CSVReader(new FileReader(csvFilePath.toFile()));
        List<String[]> records = reader.readAll();
        //skip the header
        for (int i = 1; i < records.size(); i++) {
            String[] record = records.get(i);
            testDataList.add(new TestData(
                    record[0], record[1], record[2], record[3],
                    record[4], record[5], record[6], record[7],
                    record[8], record[9], record[10], record[11],
                    record[12], record[13], record[14], record[15],
                    record[16], record[17], record[18]));
        }
        return testDataList.stream();
    }

    @ParameterizedTest
    @MethodSource("testDataProvider")
    void testDirectoryBasedCases(TestData testData) throws IOException {
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("Processing test %s%n", testData.ID);
        System.out.println("--------------------------------------------------------------------------------");

        System.out.println(testData.mapping);
        System.out.println(testData.output1);
        System.out.println(testData.error);
        System.out.println();

        if(testData.error)
            testForNotOK(testData);
        else
            testForOK(testData);
    }

    public void testForOK(TestData testData) throws IOException {
        String m = new File(base + testData.ID, testData.mapping).getAbsolutePath();
        String r = Files.createTempFile(null, ".nq").toString();
        System.out.printf("Writing output to %s%n", r);

        System.out.println("This test should generate a graph.");
        String o = new File(base + testData.ID, testData.output1).getAbsolutePath();

        int exit = Main.doMain(new String[] { "-m", m, "-o", r, "-b", "http://example.com/" });

        Model expected = RDFDataMgr.loadModel(o);
        Model actual = RDFDataMgr.loadModel(r);

        if (!expected.isIsomorphicWith(actual)) {
            expected.write(System.out, "Turtle");
            System.out.println("---");
            actual.write(System.out, "Turtle");
        }

        assertEquals(0, exit);

        System.out.println(expected.isIsomorphicWith(actual) ? "OK" : "NOK");

        assertTrue(expected.isIsomorphicWith(actual));
    }

    public void testForNotOK(TestData testData) throws IOException {
        String m = new File(base + testData.ID, testData.mapping).getAbsolutePath();
        String r = Files.createTempFile(null, ".nq").toString();
        System.out.printf("Writing output to %s%n", r);

        System.out.println("This test should NOT generate a graph.");
        int exit = Main.doMain(new String[] { "-m", m, "-o", r });
        Path path = Paths.get(r);
        System.out.println(Files.size(path) == 0 ? "OK" : "NOK");
        Model actual = RDFDataMgr.loadModel(r);
        actual.write(System.out, "NQ");

        assertTrue(exit > 0);
        assertEquals(0, Files.size(path));

        System.out.println();
    }
}
