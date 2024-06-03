
package burp;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRMLFNMLGrellFunctions {
	private static String base = "./src/test/resources/rml-fnml-grel/";

	@Test public void RMLFNOTC0001() throws IOException { testForOK("string-length"); }
	@Test public void RMLFNOTC0002() throws IOException { testForOK("string-touppercase"); }
	@Test public void RMLFNOTC0003() throws IOException { testForOK("string-tolowercase"); }
	@Test public void RMLFNOTC0004() throws IOException { testForOK("string-totitlecase-a"); }
	@Test public void RMLFNOTC0005() throws IOException { testForOK("string-totitlecase-b"); }
	@Test public void RMLFNOTC0006() throws IOException { testForOK("string-replace"); }
	@Test public void RMLFNOTC0007() throws IOException { testForOK("string-startswith-true"); }
	@Test public void RMLFNOTC0008() throws IOException { testForOK("string-startswith-false"); }
	@Test public void RMLFNOTC0009() throws IOException { testForOK("string-endswith-true"); }
	@Test public void RMLFNOTC0010() throws IOException { testForOK("string-endswith-false"); }

	public void testForOK(String f) throws IOException {
		System.out.println(String.format("Now processing %s", f));
		String m = new File(base + f, "mapping.ttl").getAbsolutePath().toString();
		String r = Files.createTempFile(null, ".nq").toString();
		System.out.println(String.format("Writing output to %s", r));

		System.out.println("This test should generate a graph.");
		String o = new File(base + f, "output.nq").getAbsolutePath().toString();

		int exit = Main.doMain(new String[] { "-m", m, "-o", r, "-b", "http://example.com/base/" });

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

	public void testForNotOK(String f) throws IOException {
		System.out.println(String.format("Now processing %s", f));
		String m = new File(base + f, "mapping.ttl").getAbsolutePath().toString();
		String r = Files.createTempFile(null, ".nq").toString();
		System.out.println(String.format("Writing output to %s", r));

		System.out.println("This test should NOT generate a graph.");
		int exit = Main.doMain(new String[] { "-m", m, "-o", r });
		System.out.println(Files.size(Paths.get(r)) == 0 ? "OK" : "NOK");
		Model actual = RDFDataMgr.loadModel(r);
		actual.write(System.out, "NQ");

		assertTrue(exit > 0);
		assertTrue(Files.size(Paths.get(r)) == 0);

		System.out.println();
	}

}